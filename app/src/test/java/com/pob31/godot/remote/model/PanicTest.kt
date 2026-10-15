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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PANIC and PANIC again: the desktop's Panic.h, read with no clock but the one the test says. */
class PanicTest {

    @Test
    fun aFirstPressStopsAll() {
        val panic = Panic()
        assertFalse(panic.nextIsDouble(10_000))
        assertFalse(panic.press(10_000))
        assertEquals("run.stopAll", Panic.command(false))
    }

    @Test
    fun aSecondPressWithin750MsKillsAll() {
        val panic = Panic()
        panic.press(1000)
        assertTrue(panic.nextIsDouble(1400))
        assertTrue(panic.press(1400))
        assertEquals("run.killAll", Panic.command(true))
    }

    @Test
    fun theWindowIs750MsExactlyCountedFromThePressBefore() {
        val atEdge = Panic().apply { press(0) }
        assertTrue(atEdge.press(750))
        val pastIt = Panic().apply { press(0) }
        assertFalse(pastIt.press(751))
    }

    @Test
    fun aHandHammeringIsAStopThenKills() {
        val panic = Panic()
        assertFalse(panic.press(0))
        assertTrue(panic.press(400))
        assertTrue(panic.press(800))
        assertTrue(panic.press(1200))
    }

    @Test
    fun aPressLongAfterIsAFirstPressAgain() {
        val panic = Panic()
        panic.press(0)
        assertTrue(panic.press(500))
        assertFalse(panic.press(5000))
        assertTrue(panic.press(5100))
    }

    @Test
    fun aClockGoingBackwardsIsNeverASecondPress() {
        val panic = Panic()
        panic.press(5000)
        assertFalse(panic.press(4900))
    }

    @Test
    fun theWordGoesBackWhenTheWindowCloses() {
        val panic = Panic()
        assertNull(panic.armedUntil())
        panic.press(2000)
        assertEquals(2750L, panic.armedUntil())
        assertTrue(panic.nextIsDouble(2750))
        assertFalse(panic.nextIsDouble(2751))
    }
}
