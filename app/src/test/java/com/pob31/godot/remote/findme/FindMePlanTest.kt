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

package com.pob31.godot.remote.findme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The find-me alarm's rules: how fast it flashes, and when it turns the alarm up. */
class FindMePlanTest {

    @Test
    fun theScreenAndTheLightFlashUnderThreeTimesASecond() {
        assertEquals(2.0, FindMePlan.flashesPerSecond(FindMePlan.FLASH_MS), 1e-9)
        assertTrue(FindMePlan.flashesPerSecond(FindMePlan.FLASH_MS) < 3.0)
    }

    @Test
    fun anAlarmLeftAtNothingIsTurnedToItsLoudest() {
        assertEquals(7, FindMePlan.raisedVolume(0, 7))
        assertEquals(15, FindMePlan.raisedVolume(0, 15))
    }

    @Test
    fun anAlarmTheOperatorCanHearIsLeftAtTheirVolume() {
        assertNull(FindMePlan.raisedVolume(1, 7))
        assertNull(FindMePlan.raisedVolume(7, 7))
        assertNull("a stream with no volume to give is left alone", FindMePlan.raisedVolume(0, 0))
    }
}
