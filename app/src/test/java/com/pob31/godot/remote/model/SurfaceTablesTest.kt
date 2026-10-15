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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** The vendored tables: read whole, held to Go.dot's own copy, and the fader curve both ways. */
class SurfaceTablesTest {

    private val tables = ShowFixture.tables()

    /**
     * THE COPY IS GO.DOT'S FILE, BYTE FOR BYTE, whenever Go.dot's checkout is beside this
     * one (`../go.dot`, or `GODOT_SOURCE`). Skipped - not failed - where it is not, on CI.
     */
    @Test
    fun theVendoredCopyIsGoDotsFile() {
        val root = System.getenv("GODOT_SOURCE")?.let(::File)
            ?: listOf(File("../../go.dot"), File("../go.dot")).firstOrNull { File(it, "tests/fixtures/remote").isDirectory }
        val source = root?.let { File(it, "tests/fixtures/remote/surface-tables.json") }
        assumeTrue("Go.dot's checkout is not beside this one", source != null && source.isFile)
        assertArrayEquals(
            "app/src/main/assets/surface-tables.json differs from ${source!!.path}: copy it again",
            source.readBytes(), ShowFixture.assetFile("surface-tables.json").readBytes()
        )
    }

    @Test
    fun sixteenControlsInTheAuthorsOrder() {
        val rows = tables.eqControls.map { it.row }
        assertEquals(
            listOf(
                "eqHpfFreq", "eqB1Shape", "eqB1Freq", "eqB1Gain", "eqB1Q", "eqB2Freq", "eqB2Gain", "eqB2Q",
                "eqB3Freq", "eqB3Gain", "eqB3Q", "eqB4Shape", "eqB4Freq", "eqB4Gain", "eqB4Q", "eqLpfFreq",
            ),
            rows
        )
        val hp = tables.control("eqHpfFreq")!!
        assertEquals(EqLaw.FREQUENCY, hp.law)
        assertEquals(20.0, hp.min, 0.0)
        assertEquals(2000.0, hp.max, 0.0)
        assertEquals(0, hp.colour)
        assertEquals("eqHpf", hp.switchRow)
        assertEquals(EqPress.TOGGLE_SWITCH, hp.press)

        val shape = tables.control("eqB4Shape")!!
        assertEquals(EqLaw.SHAPE, shape.law)
        assertEquals(EqPress.TOGGLE_SHAPE, shape.press)
        assertEquals("highShelf", shape.shelf)
        assertEquals(EqPress.NONE, tables.control("eqB2Q")!!.press)
        assertEquals(EqLaw.WIDTH, tables.control("eqB2Q")!!.law)
        assertEquals(EqLaw.GAIN, tables.control("eqB3Gain")!!.law)
        assertEquals(5, tables.control("eqLpfFreq")!!.colour)
        assertEquals(0.5, tables.detent.gainStepDb, 0.0)
    }

    @Test
    fun theFaderCurveLandsOnItsPoints() {
        val fader = tables.fader
        assertEquals(-120.0, fader.silenceDb, 0.0)
        assertEquals(12.0, fader.loudestDb, 0.0)
        for ((fraction, db) in fader.points) {
            assertEquals(fraction, fader.fractionForDb(db), 1e-12)
            assertEquals(db, fader.dbForFraction(fraction), 1e-12)
        }
        assertEquals(0.85, fader.fractionForDb(0.0), 1e-12)
        assertEquals(0.0, fader.fractionForDb(-500.0), 0.0)
        assertEquals(1.0, fader.fractionForDb(40.0), 0.0)
        assertEquals(0.0, fader.fractionForDb(Double.NaN), 0.0)
        // Straight between the points: half way from -60 to 0 is half way from 0.2 to 0.85.
        assertEquals(0.525, fader.fractionForDb(-30.0), 1e-12)
    }

    /** Both ways at every half decibel, as Go.dot's SurfaceBridgeTests holds the engine's copy to the client's. */
    @Test
    fun theCurveIsItsOwnInverseAtEveryHalfDecibel() {
        val fader = tables.fader
        var db = -120.0
        while (db <= 12.0) {
            assertEquals("at $db dB", db, fader.dbForFraction(fader.fractionForDb(db)), 1e-9)
            db += 0.5
        }
        var at = 0.0
        while (at <= 1.0) {
            assertEquals("at $at", at, fader.fractionForDb(fader.dbForFraction(at)), 1e-9)
            at += 0.01
        }
    }
}
