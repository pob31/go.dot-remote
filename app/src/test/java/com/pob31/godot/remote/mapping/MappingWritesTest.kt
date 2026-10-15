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

package com.pob31.godot.remote.mapping

import com.pob31.godot.remote.Fixtures
import com.pob31.godot.remote.await
import com.pob31.godot.remote.link.FakeEngine
import com.pob31.godot.remote.link.Identity
import com.pob31.godot.remote.link.Link
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.link.LinkTiming
import com.pob31.godot.remote.osc.OscMessage
import com.pob31.godot.remote.osc.OscValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a gesture on the Mapping tab sends, and what the screen draws while the engine catches up. */
class MappingWritesTest {

    /** Every write, in order, as words. */
    private class Recorder : MappingWrites {
        val said = ArrayList<String>()
        override fun touch(address: String) = true.also { said += "touch $address" }
        override fun set(address: String, text: String) { said += "set $address $text" }
        override fun release(address: String) = true.also { said += "release $address" }
        override fun setNow(address: String, text: String) = true.also { said += "now $address $text" }
        override fun setMany(writes: List<Pair<String, String>>) = true.also { said += "many " + writes.joinToString(" ") { "${it.first}=${it.second}" } }
        override fun identify(output: String, on: Boolean) = true.also { said += "identify $output $on" }
    }

    private val base = "/godot/zone/512QZPQT/"
    private val mesh = base + "mesh"

    private fun session(writes: MappingWrites, warp: Warp = Warp.whole(2, 2), index: Int = 3) =
        WarpDragSession(base, warp, index, PointDrag(warp.x[index], warp.y[index], 0.001, 0.001, 10.0, 50.0), writes)

    @Test
    fun aDragIsTouchTheWholeMeshAtEachMoveAndRelease() {
        val writes = Recorder()
        val drag = session(writes)
        drag.update(-5.0, 0.0)
        drag.update(-20.0, 0.0)
        drag.update(-20.0, 30.0) // across only: nothing moves, nothing sent
        drag.update(-120.0, 30.0)
        drag.end()
        assertEquals(
            listOf(
                "touch $mesh",
                "set $mesh 0 0 1 0 0 1 0.995 1",
                "set $mesh 0 0 1 0 0 1 0.98 1",
                // 100 px along at 30 px across: 1 / (1 + 30 / 50) of it.
                "set $mesh 0 0 1 0 0 1 0.9175 1",
                "release $mesh",
            ),
            writes.said
        )
    }

    @Test
    fun aTapSendsNothing() {
        val writes = Recorder()
        val drag = session(writes)
        drag.update(0.0, 0.0)
        drag.end()
        assertTrue(writes.said.isEmpty())
    }

    @Test
    fun aDraggedWarpIsTheFingersWhateverTheStreamSays() {
        val held = HeldWarps(graceMs = 1000)
        val mine = Warp.whole().moved(0, 0.2, 0.1)
        val engine = Warp.whole()
        held.hold(base, mine, dragging = true, nowMs = 0)
        assertEquals(mine, held.shown(base, engine, 5000))
        // Let go: still the tablet's until the engine holds the same, or the grace passes.
        held.letGo(base, 6000)
        assertEquals(mine, held.shown(base, engine, 6500))
        assertEquals(mine, held.shown(base, mine.copy(), 6600).also { assertTrue(held.isEmpty()) })
    }

    @Test
    fun aWriteTheEngineNeverTakesIsLetGoAfterTheGrace() {
        val held = HeldWarps(graceMs = 1000)
        val mine = Warp.whole().moved(0, 0.2, 0.1)
        val engine = Warp.whole()
        held.hold(base, mine, dragging = false, nowMs = 0)
        assertEquals(mine, held.shown(base, engine, 999))
        assertEquals(engine, held.shown(base, engine, 1001))
        assertTrue(held.isEmpty())
        // Another warp's row is never held.
        assertEquals(engine, held.shown("/godot/zone/6AP1QZPX/", engine, 0))
    }

    // ------------------------------------------------------------------ through a link

    private val engine = FakeEngine().also { it.snapshot = Fixtures.text("fixtures/mapping-snapshot.json") }
    private val links = mutableListOf<Link>()

    @After
    fun close() {
        links.forEach { it.close() }
        engine.close()
    }

    private fun live(): Link {
        val timing = LinkTiming()
        val link = Link("127.0.0.1", engine.port, Identity("tablet-id", "Wall"), Link.httpClient(timing), timing).also { links += it }
        link.start()
        await("the link is live") { link.state.value == LinkState.LIVE }
        return link
    }

    private fun messages() = engine.writes.filterIsInstance<OscMessage>()

    private fun str(text: String) = OscValue.Str(text)

    @Test
    fun aRegridIsOneSetManyAndIdentifyIsItsCommand() {
        val writes = LinkMappingWrites(live())
        assertTrue(writes.setMany(regridWrites(base, regridded(Warp.whole(), 3, 2))))
        assertTrue(writes.identify(MappingReadingTest.OUTPUT, true))
        await("two writes") { engine.writes.size == 2 }
        assertEquals(
            OscMessage(
                "/godot/cmd/node/setMany",
                str(base + "meshColumns"), str("3"),
                str(base + "meshRows"), str("2"),
                str(base + "mesh"), str("0 0 0.5 0 1 0 0 1 0.5 1 1 1"),
            ),
            messages()[0]
        )
        assertEquals(OscMessage("/godot/cmd/videoOutput/identify", str(MappingReadingTest.OUTPUT), str("true")), messages()[1])
    }

    @Test
    fun aDragThroughTheLinkIsTouchItsValuesAndItsLastValueBeforeRelease() {
        val writes = LinkMappingWrites(live())
        val drag = session(writes)
        for (k in 1..30) drag.update(-k * 4.0, 0.0)
        drag.end()
        await("the release") { messages().any { it.address == "/godot/cmd/node/release" } }
        val m = messages()
        assertEquals(OscMessage("/godot/cmd/node/touch", str(mesh)), m.first())
        assertEquals(OscMessage("/godot/cmd/node/release", str(mesh)), m.last())
        val values = m.drop(1).dropLast(1)
        assertTrue(values.isNotEmpty())
        assertTrue(values.all { it.address == mesh })
        // Throttled - fewer than the thirty moves - and the last one is where the finger ended.
        assertTrue(values.size < 30)
        assertEquals(str("0 0 1 0 0 1 0.88 1"), values.last().values.single())
    }

    @Test
    fun aNudgeIsTheWholeMeshNow() {
        val writes = LinkMappingWrites(live())
        val warp = readMapping(links[0].mirror).outputs[0].layers[1].warp
        val next = nudged(warp, 0, NudgeDirection.RIGHT, NudgeStep.FINE)
        assertTrue(writes.setNow(mesh, next.text()))
        await("the nudge") { engine.writes.size == 1 }
        assertEquals(OscMessage(mesh, str("0.621 0.1 0.9 0.12 0.6 0.38 0.92 0.4")), messages()[0])
    }
}
