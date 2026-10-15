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

package com.pob31.godot.remote.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A finger turning a rotary: relative, by the angle swept, finer by the distance from the centre (PRD §3.17). */
class RotaryTurnTest {

    /** The finger at [degrees] (clockwise from three o'clock, y down) and [r] from the centre. */
    private fun RotaryTurn.at(degrees: Double, r: Float): Double {
        val a = degrees / 180.0 * PI
        return moveTo((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
    }

    @Test
    fun theRingDoesNotJumpToTheFinger() {
        val turn = RotaryTurn(radiusPx = 100f, startPlace = 0.3)
        assertEquals(0.3, turn.at(200.0, 80f), 0.0) // the touch: wherever it lands
    }

    @Test
    fun aSweepInsideTheKnobIsTheRingsOwn() {
        val turn = RotaryTurn(radiusPx = 100f, startPlace = 0.0)
        turn.at(0.0, 80f)
        // A quarter turn clockwise of the ring's 300 degrees is 0.3 of the travel.
        assertEquals(90.0 / 300.0, turn.at(90.0, 80f), 1e-9)
        assertEquals("anticlockwise comes back", 0.0, turn.at(0.0, 80f), 1e-9)
    }

    @Test
    fun outsideItTheDistanceIsTheDivisor_continuously() {
        assertEquals(1.0, RotaryTurn(100f, 0.0).gainAt(50f), 0.0)
        assertEquals(1.0, RotaryTurn(100f, 0.0).gainAt(100f), 0.0)
        assertEquals(0.5, RotaryTurn(100f, 0.0).gainAt(200f), 1e-12)
        assertEquals(0.25, RotaryTurn(100f, 0.0).gainAt(400f), 1e-12)

        val far = RotaryTurn(radiusPx = 100f, startPlace = 0.0)
        far.at(0.0, 400f)
        assertEquals("a quarter turn at four radii moves a quarter as far", 90.0 / 300.0 / 4.0, far.at(90.0, 400f), 1e-9)
    }

    @Test
    fun theCentreMeansNothing_andTheTravelHasEnds() {
        val turn = RotaryTurn(radiusPx = 100f, startPlace = 0.5)
        turn.at(0.0, 80f)
        turn.at(170.0, 5f) // through the middle: not read
        assertEquals(0.5, turn.place, 0.0)
        val full = RotaryTurn(radiusPx = 100f, startPlace = 0.9)
        full.at(0.0, 80f)
        for (step in 1..36) full.at(step * 10.0, 80f)
        assertEquals(1.0, full.place, 0.0)
        assertTrue(RotaryTurn.TRAVEL > PI)
    }
}
