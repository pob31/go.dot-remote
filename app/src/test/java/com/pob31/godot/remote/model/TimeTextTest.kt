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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*  TIMES IN SECONDS OR AS A CLOCK (Go.dot's namespace draft §151, BKM), as the
    window and the page write them: the hours only past an hour, tenths kept,
    typed either way; seconds the look there was. */
class TimeTextTest {
    @After fun seconds() = ShownTime.follow("seconds")

    @Test fun aClockIsMinutesAndSecondsTheHoursPastAnHour() {
        assertEquals("1:23.5", clockTime(83.46))
        assertEquals("1:00.0", clockTime(59.96))
        assertEquals("1:02:03.5", clockTime(3723.5))
        assertEquals("-0:04.0", clockTime(-4.0))
        assertEquals("1:04.25", clockTime(64.25, 3, trim = true))
        assertEquals("0:07", clockTime(7.0, 3, trim = true))
    }

    @Test fun theShowsFormatSaysWhich() {
        ShownTime.follow(null)
        assertEquals("83.5", readingTime(83.5))
        assertEquals("12:04.0", clockText(724.0))           // the header's look, as it was
        assertEquals("62:04.0", clockText(3724.0))

        ShownTime.follow("clock")
        assertEquals("1:23.5", readingTime(83.5))
        assertEquals("1:02:04.0", clockText(3724.0))
        assertEquals("1:23.5", FieldText.shown(83.5, 3, blankZero = true, time = true))
        assertEquals("", FieldText.shown(0.0, 3, blankZero = true, time = true))
        assertEquals("83.5", FieldText.shown(83.5, 3, blankZero = true, time = false))
    }

    @Test fun typedEitherWay() {
        assertEquals(83.5, typedTime("83.5")!!, 1e-9)
        assertEquals(83.5, typedTime("83,5")!!, 1e-9)
        assertEquals(83.5, typedTime("1:23.5")!!, 1e-9)
        assertEquals(3723.5, typedTime("1:02:03.5")!!, 1e-9)
        assertNull(typedTime("1:75"))
        assertNull(typedTime("1.5:20"))
        assertNull(typedTime("soon"))

        assertEquals(FieldText.Commit.Send("83.5"), FieldText.commit("1:23.5", 3, blankZero = true, time = true))
        assertEquals(FieldText.Commit.NotANumber, FieldText.commit("1:75", 3, blankZero = true, time = true))
        assertEquals("1:23.5", TypedNumber.filterTyping("1:23,5", allowDecimal = true, allowColon = true))
        assertEquals("123.5", TypedNumber.filterTyping("1:23,5", allowDecimal = true))
    }
}
