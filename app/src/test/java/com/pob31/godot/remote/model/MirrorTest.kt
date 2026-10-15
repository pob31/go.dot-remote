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
import com.pob31.godot.remote.osc.OscValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.StringReader

class MirrorTest {

    private lateinit var mirror: Mirror

    @Before
    fun read() {
        val result = SnapshotReader.read(StringReader(Fixtures.snapshotSmall()))
        mirror = Mirror().also { it.install(result.state, result.tick) }
    }

    @Test
    fun aStreamedValueReplacesTheNodeAndKeepsItsMetadata() {
        val before = mirror["/godot/cue/F7HR8TVD/level"]!!
        mirror.putValues("/godot/cue/F7HR8TVD/level", "d", listOf(OscValue.Float64(-3.0)))
        val after = mirror["/godot/cue/F7HR8TVD/level"]!!
        assertEquals(OscValue.Float64(-3.0), after.value)
        assertSame(before.description, after.description)
        assertEquals(OscValue.Float64(0.0), before.value) // the old node is untouched: nodes are immutable
    }

    @Test
    fun aNewObjectOfAKnownShapeIsIndexedAndDescribedAtOnce() {
        val structure = mirror.structure.value
        mirror.added("/godot/cue/N3WC0E01")
        mirror.putValues("/godot/cue/N3WC0E01/level", "d", listOf(OscValue.Float64(-1.0)))
        assertTrue(mirror.structure.value > structure)
        assertEquals(listOf("F7HR8TVD", "K2M4P6R8", "N3WC0E01"), mirror.ids("cue"))
        assertEquals("dB", mirror["/godot/cue/N3WC0E01/level"]!!.unit)
        assertTrue(mirror.hasMeta("/godot/cue/N3WC0E01/level"))
    }

    @Test
    fun aRowOfAnUnknownShapeWaitsForItsMetadata() {
        mirror.putValues("/godot/run/R1234567/state", "s", listOf(OscValue.Str("running")))
        mirror.putValues("/godot/run/R7654321/state", "s", listOf(OscValue.Str("waiting")))
        assertEquals(ACCESS_UNKNOWN, mirror["/godot/run/R1234567/state"]!!.access)
        assertTrue(!mirror.hasMeta("/godot/run/R7654321/state"))

        // What one run's GET says describes the other's row too.
        mirror.learnMeta("/godot/run/R1234567/state", NodeMeta(ACCESS_READ, emptyList(), null, "What the run is doing."))
        assertEquals("What the run is doing.", mirror["/godot/run/R1234567/state"]!!.description)
        assertEquals("What the run is doing.", mirror["/godot/run/R7654321/state"]!!.description)
        assertEquals(OscValue.Str("waiting"), mirror.value("/godot/run/R7654321/state"))
        assertEquals(ACCESS_READ, mirror["/godot/run/R7654321/state"]!!.access)
    }

    @Test
    fun learningMetadataMakesNoNode() {
        mirror.learnMeta("/godot/run/R1234567/state", NodeMeta(ACCESS_READ, emptyList(), null, "x"))
        assertNull(mirror["/godot/run/R1234567/state"])
        assertTrue(mirror.hasMeta("/godot/run/ZZZZZZZZ/state"))
    }

    @Test
    fun removingEveryRowOfAnObjectDropsItFromTheIndex() {
        for (row in mirror.rows("cue", "K2M4P6R8")) {
            val address = if (row.isEmpty()) "/godot/cue/K2M4P6R8" else "/godot/cue/K2M4P6R8/$row"
            mirror.removed(address)
        }
        assertEquals(listOf("F7HR8TVD"), mirror.ids("cue"))
        assertNull(mirror["/godot/cue/K2M4P6R8/level"])
        // Partly removed, an object stays listed.
        mirror.removed("/godot/cue/F7HR8TVD/notes")
        assertEquals(listOf("F7HR8TVD"), mirror.ids("cue"))
        assertTrue("notes" !in mirror.rows("cue", "F7HR8TVD"))
    }
}
