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
import org.junit.Assert.assertNull
import org.junit.Test

/** The Dynamics graphs' laws: the desktop's (DynPanelComponent), spatcore's DynamicsModule to the letter. */
class DynCurveTest {

    @Test
    fun underTheThresholdNothingChanges_overIt_oneDecibelInRatio() {
        assertEquals(-40.0, DynCurve.compressorOut(-40.0, -20.0, 4.0, 0.0, 0.0), 1e-12)
        assertEquals(-15.0, DynCurve.compressorOut(0.0, -20.0, 4.0, 0.0, 0.0), 1e-12)
        assertEquals(-13.0, DynCurve.compressorOut(0.0, -20.0, 4.0, 0.0, 2.0), 1e-12)
    }

    @Test
    fun aKneeOfNoughtIsTheHardKnee_aKneeRoundsTheThreshold() {
        for (input in listOf(-60.0, -21.0, -20.0, -19.0, 0.0)) {
            val hard = if (input > -20.0) -20.0 + (input + 20.0) / 4.0 else input
            assertEquals(hard, DynCurve.compressorOut(input, -20.0, 4.0, 0.0, 0.0), 1e-12)
        }
        // At the threshold, a knee k is (1/R - 1) * k / 8 down.
        assertEquals(-20.0 + (0.25 - 1.0) * 6.0 / 8.0, DynCurve.compressorOut(-20.0, -20.0, 4.0, 6.0, 0.0), 1e-12)
        // Past the knee, the line again.
        assertEquals(-15.0, DynCurve.compressorOut(0.0, -20.0, 4.0, 6.0, 0.0), 1e-12)
    }

    @Test
    fun theGateIsUnityOverItsThreshold_flooredByItsRangeUnder() {
        assertEquals(-30.0, DynCurve.gateOut(-30.0, -50.0, -40.0), 1e-12)
        assertEquals(-50.5 - 49.5, DynCurve.gateOut(-50.5, -50.0, -80.0), 1e-9)
        assertEquals(-80.0 - 40.0, DynCurve.gateOut(-80.0, -50.0, -40.0), 1e-12)
    }

    @Test
    fun theRatioHandleGivesTheRatioBack() {
        for (ratio in listOf(1.0, 2.0, 4.0, 10.0, 40.0)) {
            val out = DynCurve.compressorOut(0.0, -30.0, ratio, 0.0, 3.0)
            assertEquals(ratio, DynCurve.ratioFromHandle(out, -30.0, 3.0)!!, 1e-9)
        }
        assertEquals("flat at the top is a hundred to one", 100.0, DynCurve.ratioFromHandle(-30.0, -30.0, 0.0)!!, 0.0)
        assertEquals("above the line is one to one", 1.0, DynCurve.ratioFromHandle(10.0, -30.0, 0.0)!!, 0.0)
        assertNull("a threshold at nought says nothing of a ratio", DynCurve.ratioFromHandle(-3.0, 0.0, 0.0))
    }

    @Test
    fun theReductionIsReadOffTheRunThatPlays() {
        val tables = ShowFixture.tables()
        val mirror = ShowFixture.mirror()
        mirror.put("/godot/run/${ShowFixture.RUN_THUNDER_AND_RAIN}/reduction", -4.5)
        val playing = readDyn(mirror, ShowFixture.THUNDER_AND_RAIN, tables, emptySet())
        assertEquals(-4.5, playing.reduction!!, 0.0)
    }
}
