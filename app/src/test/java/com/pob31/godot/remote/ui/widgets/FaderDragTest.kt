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

import com.pob31.godot.remote.model.ShowFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A finger on a fader, in numbers: relative from touch-down, finer across, a tenth of a dB, silence exact. */
class FaderDragTest {

    private val curve = ShowFixture.tables().fader

    /** A fader 1000 px long, the precision halving every 50 px away from it. */
    private fun drag(from: Double) = FaderDrag(curve, from, lengthPx = 1000f, unitPx = 50f)

    @Test
    fun theCapDoesNotJumpToTheFinger() {
        val drag = drag(-6.0)
        assertEquals(-6.0, drag.level(), 0.0)
        assertEquals("a touch is not a move", -6.0, drag.move(0f, 0f), 0.0)
    }

    @Test
    fun alongTheFaderIsTheValue() {
        // 0 dB sits at 0.85 of the travel; 150 px up is the top, +12 dB.
        assertEquals(12.0, drag(0.0).move(150f, 0f), 1e-9)
        // From 0.85 down 325 px to 0.525: -30 dB.
        assertEquals(-30.0, drag(0.0).move(-325f, 0f), 1e-9)
    }

    @Test
    fun acrossTheFaderIsTheDivisor() {
        val near = drag(0.0).move(-100f, 0f)
        val far = drag(0.0).move(-100f, 50f) // one unit off the band: half as far
        val farther = drag(0.0).move(-100f, 100f)
        assertTrue(near < far && far < farther && farther < 0.0)
        assertEquals(dbAfter(0.85 - 0.05), far, 0.05)
        assertEquals(dbAfter(0.85 - 0.025), farther, 0.05)
    }

    @Test
    fun keptToATenth_andSilenceAtTheBottom() {
        val level = drag(-6.0).move(3f, 0f)
        assertEquals(level, Math.round(level * 10) / 10.0, 1e-12)
        assertEquals(curve.silenceDb, drag(-6.0).move(-5000f, 0f), 0.0)
        assertEquals(curve.loudestDb, drag(-6.0).move(5000f, 0f), 0.0)
    }

    private fun dbAfter(place: Double) = curve.dbForFraction(place)
}
