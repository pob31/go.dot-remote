/*
    This file is part of Go.dot remote — https://github.com/pob31/go.dot-remote
    Copyright (C) 2026 Pierre-Olivier Boulant
    Go.dot remote is free software: you can redistribute it and/or modify it under the
    terms of the GNU General Public License as published by the Free Software
    Foundation, either version 3 of the License, or (at your option) any later
    version. Go.dot remote is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
    or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
    (LICENSE, at the repository root) for more details.
    SPDX-License-Identifier: GPL-3.0-or-later
*/

package com.pob31.godot.remote.link

import com.pob31.godot.remote.Fixtures
import com.pob31.godot.remote.await
import com.pob31.godot.remote.json.JsonPull
import com.pob31.godot.remote.model.Addresses
import com.pob31.godot.remote.osc.OscMessage
import com.pob31.godot.remote.osc.OscValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.StringReader
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/** The link against a fake Go.dot: the contract's sequence, structure, staleness, reconnecting and writes. */
class LinkTest {

    private lateinit var engine: FakeEngine
    private val links = mutableListOf<Link>()
    private val identity = Identity("tablet-id-1", "Stage left")

    @Before
    fun up() {
        engine = FakeEngine()
    }

    @After
    fun down() {
        links.forEach { it.close() }
        engine.close()
    }

    private fun link(timing: LinkTiming = LinkTiming()): Link =
        Link("127.0.0.1", engine.port, identity, Link.httpClient(timing), timing).also { links += it }

    private fun live(timing: LinkTiming = LinkTiming()): Link {
        val link = link(timing)
        link.start()
        await("the link is live") { link.state.value == LinkState.LIVE }
        return link
    }

    private fun commandOf(frame: String): Pair<String, String> {
        var command = ""
        var data = ""
        JsonPull(StringReader(frame)).use { json ->
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "COMMAND" -> command = json.nextString()
                    "DATA" -> data = json.nextString()
                    else -> json.skipValue()
                }
            }
        }
        return command to data
    }

    private fun str(text: String) = OscValue.Str(text)

    // ------------------------------------------------------------------ connecting

    @Test
    fun streamThenHelloAreTheFirstFramesAndComeBeforeTheSnapshot() {
        val link = live()
        val before = engine.textsBeforeSnapshot!!
        assertEquals(2, before.size)
        assertEquals("STREAM" to "/godot", commandOf(before[0]))
        assertEquals("HELLO" to "tablet-id-1\tStage left", commandOf(before[1]))
        assertEquals(1, engine.getsOf("/godot"))
        assertEquals(Fixtures.SNAPSHOT_TICK, link.mirror.tick.value)
        assertEquals("Announce", link.mirror.text("/godot/cue/F7HR8TVD/name"))
    }

    @Test
    fun bundlesAtOrBeforeTheSnapshotsTickAreDroppedAndLaterOnesApplied() {
        val notes = "/godot/cue/F7HR8TVD/notes"
        val level = "/godot/cue/F7HR8TVD/level"
        val nameB = "/godot/cue/K2M4P6R8/name"
        // Sent on the socket while the snapshot (tick 100) is being asked for, before it is answered.
        engine.beforeSnapshot = {
            engine.bundle(98, OscMessage(notes, str("from 98")))
            engine.bundle(100, OscMessage(notes, str("from 100")))
            engine.bundle(101, OscMessage(level, OscValue.Float64(-3.0)))
            engine.bundle(102, OscMessage(nameB, str("Later")))
        }
        val link = live()
        await("tick 102 applied") { link.mirror.tick.value == 102L }
        assertEquals("", link.mirror.text(notes))
        assertEquals(OscValue.Float64(-3.0), link.mirror.value(level))
        assertEquals("Later", link.mirror.text(nameB))
        assertEquals(OscValue.Int64(102), link.mirror.value(Addresses.TICK))

        // An old bundle arriving late is dropped too; a newer one is applied.
        engine.bundle(101, OscMessage(notes, str("late")))
        engine.bundle(103, OscMessage(level, OscValue.Float64(-4.0)))
        await("tick 103 applied") { link.mirror.tick.value == 103L }
        assertEquals("", link.mirror.text(notes))
        assertEquals(OscValue.Float64(-4.0), link.mirror.value(level))
    }

    @Test
    fun aGoDotWithoutStreamIsSaidTooOldAndNeverAskedForItsTree() {
        engine.streams = false
        val link = link()
        link.start()
        await("too old") { link.state.value == LinkState.TOO_OLD }
        assertEquals(0, engine.getsOf("/godot"))
        assertNull(link.problem.value)
    }

    @Test
    fun hostInfoIsReadOncePerConnectionBeforeTheSnapshot() {
        live()
        assertEquals(1, engine.getsOf("/"))
        assertEquals(listOf("/", "/godot"), engine.gets.toList().take(2))
    }

    @Test
    fun streamsReadsOnlyATrueStreamInExtensions() {
        assertTrue(Link.streams("{\"EXTENSIONS\": {\"LISTEN\": true, \"STREAM\": true}}"))
        assertFalse(Link.streams("{\"EXTENSIONS\": {\"LISTEN\": true}}"))
        assertFalse(Link.streams("{\"EXTENSIONS\": {\"STREAM\": false}}"))
        assertFalse(Link.streams("{\"STREAM\": true}"))
        assertFalse(Link.streams("not json"))
    }

    @Test
    fun aNewNameConnectsAgainAndSaysItInHello() {
        val link = live()
        val first = engine.upgrades.get()
        link.reintroduce(Identity("tablet-id-1", "Front of house"))
        await("a second connection said the new name") {
            engine.upgrades.get() > first && engine.texts.any { commandOf(it) == ("HELLO" to "tablet-id-1	Front of house") }
        }
        await("live again") { link.state.value == LinkState.LIVE }
    }

    @Test
    fun aPingTakesOneAndAHalfSeconds() {
        assertEquals(1500L, LinkTiming().pingMs)
    }

    // ------------------------------------------------------------------ structure

    private fun runJson(id: String, state: String) = """
        {"FULL_PATH": "/godot/run/$id", "ACCESS": 0, "GODOT": {"KIND": "container"}, "CONTENTS": {
          "state": {"FULL_PATH": "/godot/run/$id/state", "DESCRIPTION": "What the run is doing.", "TYPE": "s", "ACCESS": 1, "VALUE": ["$state"]},
          "position": {"FULL_PATH": "/godot/run/$id/position", "DESCRIPTION": "Where the run is, in seconds.", "TYPE": "d", "ACCESS": 1, "VALUE": [99], "UNIT": ["s"]}
        }}
    """.trimIndent()

    @Test
    fun pathAddedFetchesAnUndescribedObjectOncePerBurstAndTakesNoValuesFromIt() {
        engine.objects["/godot/run/R1234567"] = runJson("R1234567", "FROM_HTTP")
        engine.objects["/godot/run/R7654321"] = runJson("R7654321", "FROM_HTTP")
        // The burst ends at the bundle: a watchdog beat that falls among the four frames on a
        // slow runner would end it early and fetch twice (seen on CI), so the beat is slowed.
        val link = live(LinkTiming(watchMs = 2000))

        // A new run: rows nobody has described yet. Twice over in one burst, as an object's rows come.
        for (address in listOf("/godot/run/R1234567", "/godot/run/R1234567/state", "/godot/run/R1234567/position", "/godot/run/R1234567/state")) {
            engine.text("PATH_ADDED", address)
        }
        // A new cue: its rows' shape is known from the snapshot, so no GET.
        engine.text("PATH_ADDED", "/godot/cue/N3WC0E01/name")
        engine.text("PATH_ADDED", "/godot/cue/N3WC0E01/level")
        engine.bundle(
            101,
            OscMessage("/godot/run/R1234567/state", str("running")),
            OscMessage("/godot/run/R1234567/position", OscValue.Float64(1.5)),
            OscMessage("/godot/cue/N3WC0E01/name", str("Fresh")),
            OscMessage("/godot/cue/N3WC0E01/level", OscValue.Float64(-2.0)),
        )
        await("the run is described") { link.mirror["/godot/run/R1234567/state"]?.description != null }
        assertEquals(1, engine.getsOf("/godot/run/R1234567"))
        assertEquals(0, engine.getsOf("/godot/cue/N3WC0E01"))
        assertEquals("running", link.mirror.text("/godot/run/R1234567/state"))
        assertEquals(OscValue.Float64(1.5), link.mirror.value("/godot/run/R1234567/position"))
        assertEquals("s", link.mirror["/godot/run/R1234567/position"]!!.unit)
        assertEquals("Fresh", link.mirror.text("/godot/cue/N3WC0E01/name"))
        assertEquals("dB", link.mirror["/godot/cue/N3WC0E01/level"]!!.unit)
        assertEquals(listOf("R1234567"), link.mirror.ids("run"))
        assertTrue("N3WC0E01" in link.mirror.ids("cue"))

        // A second run: its shape is known now, so no GET, and it is described at once.
        engine.text("PATH_ADDED", "/godot/run/R7654321/state")
        engine.bundle(102, OscMessage("/godot/run/R7654321/state", str("waiting")))
        await("tick 102") { link.mirror.tick.value == 102L }
        Thread.sleep(150) // a watchdog beat or more: nothing else may be fetched
        assertEquals(0, engine.getsOf("/godot/run/R7654321"))
        assertEquals("What the run is doing.", link.mirror["/godot/run/R7654321/state"]!!.description)
        assertEquals("waiting", link.mirror.text("/godot/run/R7654321/state"))
    }

    @Test
    fun twoNewObjectsOfOneShapeInOneBurstCostOneGet() {
        engine.objects["/godot/run/R1111111"] = runJson("R1111111", "x")
        engine.objects["/godot/run/R2222222"] = runJson("R2222222", "x")
        val link = live()
        engine.text("PATH_ADDED", "/godot/run/R1111111/state")
        engine.text("PATH_ADDED", "/godot/run/R2222222/state")
        engine.bundle(
            101,
            OscMessage("/godot/run/R1111111/state", str("one")),
            OscMessage("/godot/run/R2222222/state", str("two")),
        )
        await("both described") {
            link.mirror["/godot/run/R1111111/state"]?.description != null &&
                link.mirror["/godot/run/R2222222/state"]?.description != null
        }
        assertEquals(1, engine.getsOf("/godot/run/R1111111") + engine.getsOf("/godot/run/R2222222"))
        assertEquals("one", link.mirror.text("/godot/run/R1111111/state"))
        assertEquals("two", link.mirror.text("/godot/run/R2222222/state"))
    }

    @Test
    fun pathRemovedDropsTheNode() {
        val link = live()
        val rows = link.mirror.rows("cue", Fixtures.CUE_B)
        for (row in rows) {
            engine.text("PATH_REMOVED", if (row.isEmpty()) "/godot/cue/${Fixtures.CUE_B}" else "/godot/cue/${Fixtures.CUE_B}/$row")
        }
        engine.bundle(101)
        await("tick 101") { link.mirror.tick.value == 101L }
        assertNull(link.mirror["/godot/cue/${Fixtures.CUE_B}/level"])
        assertEquals(listOf(Fixtures.CUE_A), link.mirror.ids("cue"))
        assertNotNull(link.mirror["/godot/cue/${Fixtures.CUE_A}/level"])
    }

    @Test
    fun aBigCueCapturedAddsRowsTheAppNeitherKeepsNorFetches() {
        // Go.dot's namespace draft §88: a WFS-DIY snapshot of seventy inputs is thousands of
        // messages, six rows each, added in one tick. The app reads none of them, nor a
        // device's own tree: nothing kept, nothing fetched, the bundle's values passed over.
        val link = live()
        val getsBefore = engine.gets.size
        for (n in 1..2000) engine.text("PATH_ADDED", "/godot/message/M${n.toString().padStart(7, '0')}/value")
        engine.text("PATH_ADDED", "/godot/message")
        engine.text("PATH_ADDED", "/godot/curve/C0RVE001/points")
        engine.text("PATH_ADDED", "/wfs/input/1/positionX")
        engine.bundle(
            101,
            OscMessage("/godot/message/M0000001/value", str("f:0.5")),
            OscMessage("/wfs/input/1/positionX", OscValue.Float32(1.5f)),
        )
        await("tick 101") { link.mirror.tick.value == 101L }
        Thread.sleep(150) // a watchdog beat or more: nothing may be fetched
        assertNull(link.mirror["/godot/message/M0000001/value"])
        assertNull(link.mirror["/godot/message"])
        assertNull(link.mirror["/godot/curve/C0RVE001/points"])
        assertNull(link.mirror["/wfs/input/1/positionX"])
        assertEquals(getsBefore, engine.gets.size)
        assertNotNull(link.mirror["/godot/cue/${Fixtures.CUE_A}/level"])
    }

    // ------------------------------------------------------------------ staleness and coming back

    @Test
    fun staleAfterHalfASecondOfSilenceAndLiveAgainWithABundle() {
        val link = live(LinkTiming(clockStoppedMs = 1500))
        engine.bundle(101)
        await("tick 101") { link.mirror.tick.value == 101L }
        val lastBundle = System.nanoTime()
        await("stale", timeoutMs = 2000) { link.state.value == LinkState.STALE }
        val ms = (System.nanoTime() - lastBundle) / 1_000_000
        assertTrue("stale after $ms ms", ms in 450..900)

        // Still answering pings, still no tick: Go.dot's clock has stopped.
        await("clock stopped", timeoutMs = 3000) { link.state.value == LinkState.CLOCK_STOPPED }

        engine.bundle(102)
        await("live again") { link.state.value == LinkState.LIVE }
    }

    @Test
    fun reconnectsAfterTheMachineGoesAway() {
        val timing = LinkTiming(backoffMs = listOf(50, 100))
        val link = live(timing)
        val seen = CopyOnWriteArrayList<LinkState>()
        val watcher = CoroutineScope(Dispatchers.Default)
        watcher.launch(start = CoroutineStart.UNDISPATCHED) { link.state.collect { seen.add(it) } }
        try {
            val first = engine.awaitSocket()
            engine.socket = null
            first.close(1001, "going away")
            await("a second connection") { engine.upgrades.get() == 2 }
            await("live again") { link.state.value == LinkState.LIVE && engine.socket != null }
            assertTrue("went through gone: $seen", LinkState.GONE in seen)
            assertEquals(2, engine.getsOf("/godot"))
            assertEquals(2, engine.texts.count { commandOf(it).first == "STREAM" })

            // And after an abrupt loss, not a goodbye: the machine dies and comes back.
            engine.restart()
            await("a third connection", timeoutMs = 8000) { engine.upgrades.get() == 3 }
            await("live a third time") { link.state.value == LinkState.LIVE && engine.socket != null }
            engine.bundle(101, OscMessage("/godot/cue/F7HR8TVD/name", str("after")))
            await("streaming again") { link.mirror.text("/godot/cue/F7HR8TVD/name") == "after" }
        } finally {
            watcher.cancel()
        }
    }

    @Test
    fun stopUnlinksAndSaysOff() {
        val link = live()
        link.stop()
        await("off") { link.state.value == LinkState.OFF }
        assertFalse(link.command("go"))
    }

    // ------------------------------------------------------------------ writes

    @Test
    fun writesAreTextInNoLocalesWay() {
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.FRANCE)
        try {
            val link = live()
            assertTrue(link.command("go"))
            link.set("/godot/cue/F7HR8TVD/level", -6.5)
            link.command("run.seek", "R1234567", WireText.number(12.25))
            link.setNow("/godot/videoOutput/V1D30000/mesh", WireText.numbers(listOf(0.0, 0.5, 1.0, 0.25)))
            link.setNow("/godot/cue/F7HR8TVD/enabled", false)
            await("five writes") { engine.writes.size == 5 }
            val m = engine.messages()
            assertEquals(OscMessage("/godot/cmd/go"), m[0])
            assertEquals(OscMessage("/godot/cue/F7HR8TVD/level", str("-6.5")), m[1])
            assertEquals(OscMessage("/godot/cmd/run/seek", str("R1234567"), str("12.25")), m[2])
            assertEquals(OscMessage("/godot/videoOutput/V1D30000/mesh", str("0 0.5 1 0.25")), m[3])
            assertEquals(OscMessage("/godot/cue/F7HR8TVD/enabled", str("false")), m[4])
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun aDragSendsTouchTheLatestValuesAndItsLastValueBeforeRelease() {
        val link = live()
        val level = "/godot/cue/F7HR8TVD/level"
        link.touch(level)
        for (i in 1..40) link.set(level, -i.toDouble())
        link.release(level)
        await("release") { engine.messages().any { it.address == "/godot/cmd/node/release" } }
        Thread.sleep(100) // a timer that should have been cancelled would fire in here
        val m = engine.messages()
        assertEquals(OscMessage("/godot/cmd/node/touch", str(level)), m.first())
        assertEquals(OscMessage("/godot/cmd/node/release", str(level)), m.last())
        val values = m.filter { it.address == level }.map { (it.values.single() as OscValue.Str).value }
        assertEquals("-1", values.first())
        assertEquals("the last value lands, just before release", "-40", values.last())
        assertTrue("throttled: ${values.size} of 40 sent", values.size < 40)
    }

    // ------------------------------------------------------------------ what Go.dot says to this tablet

    @Test
    fun findBecomesAnEvent() {
        val link = live()
        runBlocking {
            // Subscribed before FIND is sent: the flow keeps nothing for a late collector.
            val found = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(3000) { link.finds.first() } }
            engine.text("FIND", "12")
            assertEquals(FindRequest(12), found.await())
        }
    }

    @Test
    fun onlyThisTabletsRefusalsAreSurfaced() {
        val link = live()
        val received = CopyOnWriteArrayList<Refusal>()
        val watcher = CoroutineScope(Dispatchers.Default)
        watcher.launch(start = CoroutineStart.UNDISPATCHED) { link.refusals.collect { received.add(it) } }
        try {
            // The snapshot's lastError is old news and nobody's here.
            assertNull(link.lastRefusal.value)
            engine.bundle(
                101,
                OscMessage(Addresses.REMOTES, str("ws:10.0.0.5:5000\tother\tFront of house\nws:10.0.0.6:6000\ttablet-id-1\tStage left"))
            )
            await("origin learned") { link.origin.value == "ws:10.0.0.6:6000" }
            engine.bundle(102, OscMessage(Addresses.LAST_ERROR, str("102 7 ws:10.0.0.5:5000 locked node.set")))
            engine.bundle(103, OscMessage(Addresses.LAST_ERROR, str("103 8 ws:10.0.0.6:6000 not-a-stop standby.set")))
            await("a refusal") { received.isNotEmpty() }
            Thread.sleep(50)
            assertEquals(1, received.size)
            assertEquals("not-a-stop", received[0].reason)
            assertEquals("standby.set", received[0].command)
            assertEquals(received[0], link.lastRefusal.value)
        } finally {
            watcher.cancel()
        }
    }
}
