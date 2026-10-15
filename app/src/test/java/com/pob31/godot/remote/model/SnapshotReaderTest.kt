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

package com.pob31.godot.remote.model

import com.pob31.godot.remote.Fixtures
import com.pob31.godot.remote.json.JsonException
import com.pob31.godot.remote.osc.OscValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Reader
import java.io.StringReader

class SnapshotReaderTest {

    private fun mirrorOf(json: String): Mirror {
        val result = SnapshotReader.read(StringReader(json))
        return Mirror().also { it.install(result.state, result.tick) }
    }

    @Test
    fun theFixtureIsReadWhole() {
        val result = SnapshotReader.read(StringReader(Fixtures.snapshotSmall()))
        assertEquals(Fixtures.SNAPSHOT_TICK, result.tick)
        val mirror = Mirror().also { it.install(result.state, result.tick) }
        assertEquals(100L, mirror.tick.value)

        // Values typed by TYPE.
        assertEquals(OscValue.Float64(0.0), mirror.value("/godot/cue/F7HR8TVD/level"))
        assertEquals(OscValue.Float64(-6.5), mirror.value("/godot/cue/K2M4P6R8/level"))
        assertEquals(OscValue.Str("Announce"), mirror.value("/godot/cue/F7HR8TVD/name"))
        assertEquals(OscValue.True, mirror.value("/godot/cue/F7HR8TVD/enabled"))
        assertEquals(OscValue.False, mirror.value("/godot/cue/K2M4P6R8/enabled"))
        assertEquals(OscValue.Int64(100), mirror.value("/godot/engine/tick"))
        assertEquals(OscValue.Int32(2), mirror.value("/godot/videoOutput/V1D30000/meshColumns"))

        // A list row: one value per element, its TYPE as long.
        val mesh = mirror["/godot/videoOutput/V1D30000/mesh"]!!
        assertEquals("dddddddd", mesh.typeTags)
        assertEquals(listOf(0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 1.0), mesh.values.map { it.asDouble() })

        // Containers are nodes with nothing in them.
        val container = mirror["/godot/cue/F7HR8TVD"]!!
        assertTrue(container.values.isEmpty())
        assertEquals("", container.typeTags)
        assertEquals(ACCESS_NONE, container.access)
    }

    @Test
    fun metadataIsReadAndSharedByRow() {
        val mirror = mirrorOf(Fixtures.snapshotSmall())
        val a = mirror["/godot/cue/F7HR8TVD/level"]!!
        val b = mirror["/godot/cue/K2M4P6R8/level"]!!
        assertEquals("The cue's level, in decibels — \"0\" is unity.", a.description)
        assertEquals("dB", a.unit)
        assertEquals(listOf(ArgRange(min = -144.0, max = 12.0)), a.range)
        assertEquals(ACCESS_READ_WRITE, a.access)
        assertTrue(a.writable)
        // One description for the row, not one per cue.
        assertSame(a.description, b.description)
        assertSame(a.range, b.range)

        assertEquals(listOf(ArgRange(vals = listOf("memo", "group", "media", "fade"))), mirror["/godot/cue/K2M4P6R8/kind"]!!.range)
        assertEquals(listOf(ArgRange(min = 0.0)), mirror["/godot/cue/F7HR8TVD/postWait"]!!.range)
        assertEquals(ACCESS_READ, mirror["/godot/cue/F7HR8TVD/kind"]!!.access)
        assertNull(mirror["/godot/videoOutput/V1D30000/meshColumns"]!!.description)
        assertNotNull(mirror.metaOf("/godot/cue/ZZZZZZZZ/level"))
    }

    @Test
    fun theOwnerIndexIsBuilt() {
        val mirror = mirrorOf(Fixtures.snapshotSmall())
        assertEquals(listOf("F7HR8TVD", "K2M4P6R8"), mirror.ids("cue"))
        assertEquals(listOf("D9FH2JKA"), mirror.ids("list"))
        assertEquals(listOf("V1D30000"), mirror.ids("videoOutput"))
        assertEquals(emptyList<String>(), mirror.ids("run"))
        assertEquals(setOf("", "autoName", "enabled", "kind", "level", "name", "notes", "number", "postWait"), mirror.rows("cue", "F7HR8TVD"))
    }

    @Test
    fun theStandbyCueIsReadByAddress() {
        val mirror = mirrorOf(Fixtures.snapshotSmall())
        assertEquals(CueLabel("F7HR8TVD", "2.2", "Announce"), standbyCue(mirror))
        // A cue with no name reads by the name Go.dot works out for it.
        assertEquals(CueLabel("K2M4P6R8", "3", "Fade Announce"), cueLabel(mirror, "K2M4P6R8"))
    }

    /**
     * The engine's own golden for one cue (go.dot tests/fixtures/tree/cue-F7HR8TVD.json at
     * 2e498dfb), as `GET /godot/cue/F7HR8TVD` answers it: the real shape, with the real
     * descriptions, escapes and RANGE spellings.
     */
    @Test
    fun theEnginesOwnCueGoldenIsRead() {
        val result = SnapshotReader.read(StringReader(Fixtures.text("fixtures/engine-cue-F7HR8TVD.json")))
        val mirror = Mirror().also { it.install(result.state, result.tick) }
        assertEquals(19, mirror.size)
        assertEquals(-1L, result.tick) // an object, not the tree: no tick in it
        assertEquals("Announce", mirror.text("/godot/cue/F7HR8TVD/name"))
        assertEquals("2.2", mirror.text("/godot/cue/F7HR8TVD/number"))
        assertEquals(OscValue.Float64(1.5), mirror.value("/godot/cue/F7HR8TVD/postWait"))
        assertEquals(OscValue.True, mirror.value("/godot/cue/F7HR8TVD/enabled"))
        assertEquals(OscValue.Int32(1), mirror.value("/godot/cue/F7HR8TVD/index"))
        assertEquals("s", mirror["/godot/cue/F7HR8TVD/postWait"]!!.unit)
        assertEquals(listOf(ArgRange(vals = listOf("file", "on", "off"))), mirror["/godot/cue/F7HR8TVD/override"]!!.range)
        assertTrue(mirror["/godot/cue/F7HR8TVD/flagged"]!!.description!!.startsWith("Whether somebody has marked"))
        assertEquals(listOf("F7HR8TVD"), mirror.ids("cue"))
    }

    @Test(expected = JsonException::class)
    fun malformedJsonIsAnException() {
        SnapshotReader.read(StringReader("""{"FULL_PATH": "/godot", "CONTENTS": {"a": {"FULL_PATH": "/godot/a", "VALUE": [1, }"""))
    }

    /** A synthetic show of [cues] cues with [rows] rows each, written as it is read: never whole in memory. */
    private class SyntheticShow(private val cues: Int, private val rows: Int) : Reader() {
        private var cue = -1
        private var chunk = StringBuilder()
        private var at = 0
        private var finished = false

        private fun id(n: Int): String {
            val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
            var v = n
            val out = CharArray(8)
            for (i in 7 downTo 0) {
                out[i] = alphabet[v % 32]
                v /= 32
            }
            return String(out)
        }

        private fun next(): Boolean {
            chunk.setLength(0)
            at = 0
            if (finished) return false
            if (cue == -1) {
                chunk.append("""{"FULL_PATH": "/godot", "ACCESS": 0, "CONTENTS": {"engine": {"FULL_PATH": "/godot/engine", "ACCESS": 0, "CONTENTS": {"tick": {"FULL_PATH": "/godot/engine/tick", "TYPE": "h", "ACCESS": 1, "VALUE": [4242]}}}, "cue": {"FULL_PATH": "/godot/cue", "ACCESS": 0, "CONTENTS": {""")
            } else if (cue < cues) {
                val cueId = id(cue)
                if (cue > 0) chunk.append(",")
                chunk.append("\"$cueId\": {\"FULL_PATH\": \"/godot/cue/$cueId\", \"ACCESS\": 0, \"GODOT\": {\"KIND\": \"container\"}, \"CONTENTS\": {")
                for (r in 0 until rows) {
                    if (r > 0) chunk.append(",")
                    chunk.append("\"row$r\": {\"FULL_PATH\": \"/godot/cue/$cueId/row$r\", \"DESCRIPTION\": \"")
                    chunk.append("A description long enough to be most of the bytes, as Go.dot's are. ".repeat(3))
                    chunk.append("\", \"TYPE\": \"d\", \"ACCESS\": 3, \"VALUE\": [$r.5], \"RANGE\": [{\"MIN\": 0, \"MAX\": 100}], \"UNIT\": [\"s\"], ")
                    chunk.append("\"GODOT\": {\"KIND\": \"state\", \"RATE_CAP\": 50, \"ANTICIPATABLE\": false, \"PANIC\": \"park\"}}")
                }
                chunk.append("}}")
            } else {
                chunk.append("}}}}")
                finished = true
            }
            cue++
            bytes += chunk.length
            return true
        }

        var bytes = 0L

        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            while (at >= chunk.length) if (!next()) return -1
            val n = minOf(len, chunk.length - at)
            chunk.getChars(at, at + n, cbuf, off)
            at += n
            return n
        }

        override fun close() {}
    }

    @Test
    fun aShowOfFiveHundredCuesStreams() {
        val show = SyntheticShow(cues = 500, rows = 40)
        val started = System.nanoTime()
        val result = SnapshotReader.read(show)
        val ms = (System.nanoTime() - started) / 1_000_000
        println("synthetic snapshot: ${show.bytes / 1024} KiB, ${result.state.nodes.size} nodes, read in $ms ms")
        assertTrue("several megabytes", show.bytes > 5_000_000)
        assertEquals(4242L, result.tick)
        assertEquals(500 * 41 + 4, result.state.nodes.size)
        val mirror = Mirror().also { it.install(result.state, result.tick) }
        assertEquals(500, mirror.ids("cue").size)
        assertSame(mirror["/godot/cue/00000000/row7"]!!.description, mirror["/godot/cue/0000000F/row7"]!!.description)
        assertEquals(OscValue.Float64(7.5), mirror.value("/godot/cue/0000000F/row7"))
    }
}
