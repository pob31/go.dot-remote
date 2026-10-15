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

import com.pob31.godot.remote.model.FieldText.Commit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The shown value and the commit text agree (the page's `shownValue` / `commitText` trap). */
class FieldTextTest {

    @Test
    fun aBlankNoughtBoxShowsNoughtAsNothing_andClearedSendsNought() {
        assertEquals("", FieldText.shown(0.0, 3, blankZero = true))
        assertEquals("1.5", FieldText.shown(1.5, 3, blankZero = true))
        assertEquals(Commit.Send("0"), FieldText.commit("", 3, blankZero = true))
        assertEquals(Commit.Send("0"), FieldText.commit("   ", 3, blankZero = true))
    }

    @Test
    fun anyOtherBoxClearedSendsNothing_neverAnEmptyString() {
        assertEquals("0", FieldText.shown(0.0, 3))
        assertEquals(Commit.Revert, FieldText.commit("", 3))
        assertEquals(Commit.NotANumber, FieldText.commit("abc", 3))
        assertEquals(Commit.NotANumber, FieldText.commit("-", 3))
    }

    @Test
    fun aCommaIsReadAndAPointIsWritten_inEveryLanguage() {
        val was = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertEquals(Commit.Send("1.25"), FieldText.commit("1,25", 3))
            assertEquals(Commit.Send("-6.5"), FieldText.commit("−6,5 dB", 1))
            assertEquals("0.6", FieldText.shown(0.6, 3))
            assertEquals("1.333", FieldText.shown(4.0 / 3.0, 3))
        } finally {
            Locale.setDefault(was)
        }
    }

    @Test
    fun whatIsShownCommitsBackToItself() {
        for (value in listOf(0.0, 0.6, 1.5, 2.25, 10.0, 0.001)) {
            val shown = FieldText.shown(value, 3)
            assertEquals(Commit.Send(shown), FieldText.commit(shown, 3))
        }
    }

    @Test
    fun aLevelSaysSilenceInAWord() {
        assertEquals("-inf", FieldText.levelText(-120.0))
        assertEquals("-6.0", FieldText.levelText(-6.0))
        assertEquals("12.0", FieldText.levelText(12.0))
    }

    @Test
    fun aSentValueIsShownUntilTheEngineAgrees_orForASecondAndAHalf() {
        val held = SentValue()
        held.sent("-6", 1000)
        assertEquals("-6", held.shown("0", 1100))
        assertTrue(held.waiting)
        assertEquals("-6", held.shown("-6", 1200))
        assertFalse(held.waiting)
        assertEquals("-3", held.shown("-3", 1300))

        held.sent("-9", 2000)
        assertEquals("-9", held.shown("0", 3400))
        assertEquals("refused: the engine's value again", "0", held.shown("0", 3600))
    }
}
