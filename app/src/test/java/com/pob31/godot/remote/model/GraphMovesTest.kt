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

/** How a finger moves the graphs and the bars: axes, pinches, a bar's drag, a bar's box. */
class GraphMovesTest {

    @Test
    fun anAxisGoesBothWays_upIsUp() {
        val up = DbAxis(-24.0, 24.0, upward = true)
        assertEquals(0f, up.pixelOf(24.0, 400f), 0f)
        assertEquals(200f, up.pixelOf(0.0, 400f), 0f)
        assertEquals(-12.0, up.dbAt(300f, 400f), 1e-9)
        val across = DbAxis(-60.0, 0.0, upward = false)
        assertEquals(-30.0, across.dbAt(across.pixelOf(-30.0, 600f), 600f), 1e-6)
        assertEquals("kept inside", 0.0, across.dbAt(900f, 600f), 0.0)
    }

    @Test
    fun aFrequencyAcrossTheGraphIsInOctaves() {
        val w = 1000f
        assertEquals(0f, GraphMoves.xOfFrequency(20.0, w), 1e-3f)
        assertEquals(w, GraphMoves.xOfFrequency(20000.0, w), 1e-3f)
        assertEquals(1000.0, GraphMoves.frequencyAtX(GraphMoves.xOfFrequency(1000.0, w), w), 1e-3)
    }

    @Test
    fun aPinchWidensABand_aSpreadOpensTheKnee() {
        assertEquals(0.7, GraphMoves.pinchedQ(1.4, 100f, 200f), 1e-9)
        assertEquals(2.8, GraphMoves.pinchedQ(1.4, 200f, 100f), 1e-9)
        assertEquals(10.0, GraphMoves.pinchedQ(5.0, 400f, 10f), 0.0)
        assertEquals(0.1, GraphMoves.pinchedQ(0.2, 10f, 400f), 0.0)
        assertEquals(18.0, GraphMoves.pinchedKnee(6.0, 100f, 300f, 400f), 1e-9)
        assertEquals(0.0, GraphMoves.pinchedKnee(6.0, 300f, 100f, 200f), 0.0)
        assertEquals(24.0, GraphMoves.pinchedKnee(20.0, 0f, 1000f, 200f), 0.0)
    }

    @Test
    fun aBarMovesFromWhereItStands_finerAwayFromIt() {
        val near = BarDrag(0.5, 400f, 48f)
        assertEquals(0.75, near.move(100f, 0f), 1e-9)
        val far = BarDrag(0.5, 400f, 48f)
        assertTrue(far.move(100f, 96f) < 0.75)
        assertEquals(1.0, BarDrag(0.9, 400f, 48f).move(400f, 0f), 0.0)
    }

    @Test
    fun aBarsBoxSaysHertzOrKilohertz_andReadsATypedNumberBack() {
        assertEquals(BarNumber("82.5", "Hz", 1, 1.0), BarNumber.of(EqLaw.FREQUENCY, 82.5))
        assertEquals(BarNumber("500", "Hz", 1, 1.0), BarNumber.of(EqLaw.FREQUENCY, 500.0))
        assertEquals(BarNumber("2.500", "kHz", 3, 1000.0), BarNumber.of(EqLaw.FREQUENCY, 2500.0))
        assertEquals(BarNumber("12.00", "kHz", 3, 1000.0), BarNumber.of(EqLaw.FREQUENCY, 12000.0))
        assertEquals(BarNumber("-3.5", "dB", 1, 1.0), BarNumber.of(EqLaw.GAIN, -3.5))
        assertEquals(BarNumber("4.0", ":1", 1, 1.0), BarNumber.of(EqLaw.RATIO, 4.0))
        assertEquals(BarNumber("150", "ms", 2, 1.0), BarNumber.of(EqLaw.TIME, 150.0))
        assertEquals(BarNumber("0.70", null, 2, 1.0), BarNumber.of(EqLaw.WIDTH, 0.7))
    }
}
