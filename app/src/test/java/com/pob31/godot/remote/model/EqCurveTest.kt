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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The EQ graph's maths: Go.dot's own (engine audio/EqMath.h), so the tablet's curve is the desktop's. */
class EqCurveTest {

    private fun flat() = EqShape()

    private fun withBand(n: Int, band: EqBand) = flat().copy(bands = flat().bands.toMutableList().also { it[n - 1] = band })

    @Test
    fun aFlatEqIsNoughtEverywhere_andAnEqOutIsToo() {
        for (hz in listOf(20.0, 100.0, 1000.0, 10000.0, 20000.0)) assertEquals(0.0, EqCurve.responseDb(flat(), hz), 1e-9)
        val loud = withBand(2, EqBand(true, "peak", 500.0, 12.0, 1.0)).copy(on = false, hpf = true)
        for (hz in listOf(20.0, 500.0, 20000.0)) assertEquals(0.0, EqCurve.responseDb(loud, hz), 1e-9)
    }

    @Test
    fun aPeakIsItsGainAtItsFrequency_andNothingFarFromIt() {
        val eq = withBand(3, EqBand(true, "peak", 2000.0, 6.0, 0.7))
        assertEquals(6.0, EqCurve.responseDb(eq, 2000.0), 0.01)
        assertEquals(0.0, EqCurve.responseDb(eq, 20.0), 0.1)
        val cut = withBand(3, EqBand(true, "peak", 2000.0, -9.0, 2.0))
        assertEquals(-9.0, EqCurve.responseDb(cut, 2000.0), 0.01)
    }

    @Test
    fun aBandOutOrAtNoughtAddsNothing() {
        val out = withBand(1, EqBand(false, "peak", 100.0, 12.0, 1.0))
        assertEquals(0.0, EqCurve.responseDb(out, 100.0), 1e-9)
        val nought = withBand(1, EqBand(true, "lowShelf", 100.0, 0.0, 1.0))
        assertEquals(0.0, EqCurve.responseDb(nought, 30.0), 1e-9)
    }

    @Test
    fun aShelfReachesItsGainFarPastItsCorner() {
        val low = withBand(1, EqBand(true, "lowShelf", 200.0, 8.0, 0.7))
        assertEquals(8.0, EqCurve.responseDb(low, 20.0), 0.2)
        assertEquals(0.0, EqCurve.responseDb(low, 10000.0), 0.1)
        val high = withBand(4, EqBand(true, "highShelf", 4000.0, -6.0, 0.7))
        assertEquals(-6.0, EqCurve.responseDb(high, 19000.0), 0.3)
        assertEquals(0.0, EqCurve.responseDb(high, 50.0), 0.1)
    }

    @Test
    fun theFiltersAreThreeDecibelsDownAtTheirFrequency() {
        assertEquals(-3.01, EqCurve.responseDb(flat().copy(hpf = true, hpfFreq = 120.0), 120.0), 0.05)
        assertEquals(-3.01, EqCurve.responseDb(flat().copy(lpf = true, lpfFreq = 8000.0), 8000.0), 0.05)
        assertTrue("twelve decibels an octave below", EqCurve.responseDb(flat().copy(hpf = true, hpfFreq = 400.0), 100.0) < -20.0)
    }

    @Test
    fun theCurveRunsInOctavesFromTwentyHertzToTwentyKilohertz() {
        val curve = EqCurve.curve(flat())
        assertEquals(240, curve.size)
        assertEquals(20.0, EqCurve.frequencyAt(curve.first().first), 1e-9)
        assertEquals(20000.0, EqCurve.frequencyAt(curve.last().first), 1e-6)
        assertEquals(0.5, EqCurve.placeOf(EqCurve.frequencyAt(0.5)), 1e-12)
        assertEquals(632.46, EqCurve.frequencyAt(0.5), 0.01)
    }

    @Test
    fun aReadingIsTheShapeItsRowsSay() {
        val tables = ShowFixture.tables()
        val mirror = ShowFixture.mirror()
        val eq = readEq(mirror, ShowFixture.THUNDER_AND_RAIN, tables, emptySet())
        val shape = eq.shape()
        assertEquals(eq.knobs.first { it.control.row == "eqB2Gain" }.value, shape.bands[1].gain, 0.0)
        assertEquals(eq.switches.first { it.row == "eqLpf" }.on, shape.lpf)
        // A value a finger holds is drawn in place of the engine's.
        assertEquals(9.0, eq.shape { if (it.control.row == "eqB2Gain") 9.0 else it.value }.bands[1].gain, 0.0)
    }
}
