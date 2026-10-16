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
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.model.Mirror
import com.pob31.godot.remote.model.SnapshotReader
import com.pob31.godot.remote.osc.OscValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

/**
 * The Mapping tab's reading of a real tree: `wfg serve` of the video fixture with two
 * zones made by `zone.create` and real meshes written over UDP (fixtures/mapping-snapshot.json,
 * trimmed to the rows this reads).
 */
class MappingReadingTest {

    companion object {
        const val OUTPUT = "VD000021"
        const val CANVAS = "VD000011"
        const val LOGO = "512QZPQT"
        const val FLOOR = "6AP1QZPX"

        fun mirror(): Mirror {
            val result = SnapshotReader.read(StringReader(Fixtures.text("fixtures/mapping-snapshot.json")))
            return Mirror().also { it.install(result.state, result.tick) }
        }
    }

    @Test
    fun theOutputItsCanvasAndItsWords() {
        val reading = readMapping(mirror())
        assertEquals(1, reading.outputs.size)
        val output = reading.outputs[0]
        assertEquals(OUTPUT, output.id)
        assertEquals("Face", output.label)
        assertEquals(CANVAS, output.canvas)
        assertEquals("display", output.kind)
        assertFalse(output.sends)
        assertTrue(output.enabled)
        assertFalse(output.bound)
        assertEquals("the renderer has no window (--no-window)", output.problem)
        assertFalse(output.testPattern)
        assertEquals("Stage", reading.canvasLabel(CANVAS))
        assertEquals(16.0 / 9.0, reading.aspectOf(output), 1e-12)
        assertFalse(reading.locked)
    }

    @Test
    fun itsOwnWarpFirstThenEachZoneBottomFirst() {
        val output = readMapping(mirror()).outputs[0]
        assertEquals(listOf(null, LOGO, FLOOR), output.layers.map { it.zoneId })
        val own = output.layers[0]
        assertEquals("/godot/videoOutput/$OUTPUT/", own.base)
        assertEquals("/godot/videoOutput/$OUTPUT/mesh", own.meshAddress)
        assertEquals(4, own.warp.columns)
        assertEquals(3, own.warp.rows)
        assertEquals(0.04, own.warp.x[0], 1e-12)
        assertEquals(0.0695, own.warp.y[1], 1e-12)
        assertFalse(own.isZone)

        val logo = output.layers[1]
        assertEquals("Logo", logo.name)
        assertEquals("screen", logo.blend)
        assertEquals(60.0, logo.opacity, 0.0)
        assertEquals(0.0, logo.opacityMin, 0.0)
        assertEquals(100.0, logo.opacityMax, 0.0)
        // The blend's closed set, as the engine says it (RANGE VALS), in its order.
        assertEquals(listOf("normal", "add", "screen", "multiply"), logo.blends)
        assertEquals("0.62 0.1 0.9 0.12 0.6 0.38 0.92 0.4", logo.warp.text())

        val floor = output.layers[2]
        assertEquals(3, floor.warp.columns)
        assertEquals(2, floor.warp.rows)
        assertEquals("add", floor.blend)
        assertEquals("/godot/zone/$FLOOR/opacity", floor.opacityAddress)
    }

    @Test
    fun aMeshFromTheStreamIsReadAsTheSnapshotsWas() {
        val mirror = mirror()
        val mesh = "/godot/zone/$LOGO/mesh"
        mirror.putValues(mesh, "dddddddd", listOf(0.6, 0.1, 0.9, 0.1, 0.6, 0.4, 0.9, 0.4).map { OscValue.Float64(it) })
        assertEquals("0.6 0.1 0.9 0.1 0.6 0.4 0.9 0.4", readMapping(mirror).outputs[0].layers[1].warp.text())
    }

    @Test
    fun aZoneNotInTheListIsNotDrawnAndTheOrderIsTheEngines() {
        val mirror = mirror()
        mirror.putValues("/godot/videoOutput/$OUTPUT/zones", "s", listOf(OscValue.Str("$FLOOR $LOGO")))
        assertEquals(listOf(null, FLOOR, LOGO), readMapping(mirror).outputs[0].layers.map { it.zoneId })
        mirror.putValues("/godot/videoOutput/$OUTPUT/zones", "s", listOf(OscValue.Str(LOGO)))
        assertEquals(listOf(null, LOGO), readMapping(mirror).outputs[0].layers.map { it.zoneId })
    }

    @Test
    fun theGridIsReadAndAbsentOnAnOlderGoDot() {
        val mirror = mirror()
        // The fixture is from a Go.dot before the grid: no row, so no switch.
        assertNull(readMapping(mirror).outputs[0].grid)
        mirror.putValues("/godot/videoOutput/$OUTPUT/grid", "F", listOf(OscValue.False))
        assertEquals(false, readMapping(mirror).outputs[0].grid)
        mirror.putValues("/godot/videoOutput/$OUTPUT/grid", "T", listOf(OscValue.True))
        assertEquals(true, readMapping(mirror).outputs[0].grid)
    }

    @Test
    fun anOutputSentToACardIsNamedByTheCardAndItsMaker() {
        val mirror = mirror()
        val base = "/godot/videoOutput/$OUTPUT/"
        // The fixture is from a Go.dot before the cards: no rows, no card.
        assertEquals("", readMapping(mirror).outputs[0].cardLabel)
        mirror.putValues(base + "kind", "s", listOf(OscValue.Str("card")))
        mirror.putValues(base + "card", "s", listOf(OscValue.Str("dl-0")))
        val unnamed = readMapping(mirror).outputs[0]
        assertTrue(unnamed.sends)
        // No name kept: the plain kind word is the fallback.
        assertEquals("", unnamed.cardLabel)
        mirror.putValues(base + "cardName", "s", listOf(OscValue.Str("Duo 2 (1)")))
        assertEquals("DeckLink Duo 2 (1)", readMapping(mirror).outputs[0].cardLabel)
        // The rows on an output that is not a card name nothing.
        mirror.putValues(base + "kind", "s", listOf(OscValue.Str("ndi")))
        assertEquals("", readMapping(mirror).outputs[0].cardLabel)
    }

    @Test
    fun theMakerComesFromThePrefixAndIsNotSaidTwice() {
        assertEquals("DeckLink Duo 2 (1)", cardLabel("dl-1", "DeckLink Duo 2 (1)"))
        assertEquals("DeckLink Duo 2 (1)", cardLabel("dlt-1", "Duo 2 (1)"))
        assertEquals("AJA Kona 5", cardLabel("aja-0", "Kona 5"))
        assertEquals("AJA Kona 5", cardLabel("aja-0", "AJA Kona 5"))
        assertEquals("Go.dot test card", cardLabel("fake-0", "Go.dot test card"))
        assertEquals("Mystery", cardLabel("other-0", "Mystery"))
        assertEquals("", cardLabel("dl-0", " "))
    }

    @Test
    fun theLockIsRead() {
        val mirror = mirror()
        mirror.putValues("/godot/document/locked", "T", listOf(OscValue.True))
        assertTrue(readMapping(mirror).locked)
    }

    @Test
    fun underTheLockTheWarpsAreGreyedWithTheWordAndAStaleLinkWithItsOwn() {
        assertEquals(MappingBlock.NONE, mappingBlock(locked = false, link = LinkState.LIVE))
        assertEquals(MappingBlock.LOCKED, mappingBlock(locked = true, link = LinkState.LIVE))
        // The link's word wins: nothing goes anywhere until it is live, locked or not.
        for (state in LinkState.entries.filter { it != LinkState.LIVE }) {
            assertEquals(MappingBlock.LINK, mappingBlock(locked = true, link = state))
            assertEquals(MappingBlock.LINK, mappingBlock(locked = false, link = state))
        }
    }

    @Test
    fun anEmptyMirrorHasNoOutputs() {
        val reading = readMapping(Mirror())
        assertTrue(reading.outputs.isEmpty())
        assertEquals(null, reading.output(null))
    }
}
