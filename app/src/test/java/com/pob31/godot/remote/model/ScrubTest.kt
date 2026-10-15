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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A finger dragging a playhead, with numbers (Go.dot's ScrubTests, ported). */
class ScrubTest {

    /** A 100 s file on a 400 px strip: a quarter of a second a pixel. */
    private fun grabbed(position: Double = 50.0, onRelease: Boolean = false) = Scrub().apply {
        begin(Scrub.Setup(position = position, to = 100.0, secondsPerPixel = 0.25, unit = 40.0, x = 200.0, onRelease = onRelease))
    }

    @Test
    fun relativeFromTouchDown_noJump() {
        val scrub = grabbed()
        assertEquals(50.0, scrub.target, 0.0)
        assertEquals(50.0, scrub.moveTo(200.0, 0.0), 0.0) // a touch is not a move
        assertEquals(55.0, scrub.moveTo(220.0, 0.0), 1e-12) // 20 px inside: 1:1
    }

    @Test
    fun finerAboveAndBelow_continuously() {
        assertEquals(1.0, Scrub.rateFor(0.0, 40.0), 0.0)
        assertEquals(0.5, Scrub.rateFor(40.0, 40.0), 1e-12)
        assertEquals(0.25, Scrub.rateFor(80.0, 40.0), 1e-12)
        assertEquals(1.0 / 256.0, Scrub.rateFor(10_000.0, 40.0), 0.0)
        // Between the thresholds the rate slides, it does not step.
        val between = Scrub.rateFor(20.0, 40.0)
        assertTrue(between < 1.0 && between > 0.5)

        val scrub = grabbed()
        scrub.moveTo(240.0, 40.0) // 40 px along, a unit above: half the rate
        assertEquals(55.0, scrub.target, 1e-12)
        assertEquals("1/2", Scrub.rateText(scrub.rate))
        scrub.moveTo(280.0, 0.0) // back inside: 1:1 from where it got to, no jump
        assertEquals(65.0, scrub.target, 1e-12)
        assertEquals("", Scrub.rateText(scrub.rate))
    }

    @Test
    fun heldInsideTheMaterial() {
        val scrub = grabbed()
        scrub.moveTo(-10_000.0, 0.0)
        assertEquals(0.0, scrub.target, 0.0)
        scrub.moveTo(10_000.0, 0.0)
        assertEquals(100.0, scrub.target, 0.0)
        // A scene's round: from 30 to 60.
        val round = Scrub().apply { begin(Scrub.Setup(position = 70.0, from = 30.0, to = 60.0, secondsPerPixel = 0.1)) }
        assertEquals(60.0, round.target, 0.0)
    }

    @Test
    fun pushedAgainstAnEdgeItSlidesOn() {
        val scrub = grabbed()
        // Two seconds at 90 px/s, a unit above the strip: 180 px at half rate, a quarter second a pixel.
        assertEquals(50.0 + 180 * 0.25 * 0.5, scrub.push(1, 2.0, 40.0), 1e-9)
        assertEquals(scrub.target, scrub.push(0, 2.0, 40.0), 0.0)
    }

    @Test
    fun aSeekAtMostEach200ms_andOnlyWhenTheHeadMoved() {
        val scrub = grabbed()
        assertFalse("a grab is not a seek", scrub.due(1000.0))
        scrub.moveTo(204.0, 0.0)
        assertTrue(scrub.due(1000.0))
        scrub.moveTo(208.0, 0.0)
        assertFalse(scrub.due(1150.0))
        assertTrue(scrub.due(1201.0))
        assertFalse("nothing new", scrub.due(1500.0))
        scrub.moveTo(212.0, 0.0)
        assertTrue("the release sends what was not sent", scrub.settle())
        assertFalse(scrub.settle())
    }

    @Test
    fun aSceneIsSentOnceWhenTheHandLetsGo() {
        val scrub = grabbed(onRelease = true)
        scrub.moveTo(260.0, 0.0)
        assertFalse(scrub.due(1000.0))
        assertFalse(scrub.due(5000.0))
        assertTrue(scrub.settle())
        assertEquals(65.0, scrub.target, 1e-12)
    }
}
