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

package com.pob31.godot.remote.link

import com.pob31.godot.remote.model.TypedNumber
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class WireTextTest {

    private lateinit var saved: Locale

    @Before
    fun french() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.FRANCE)
    }

    @After
    fun restore() {
        Locale.setDefault(saved)
    }

    @Test
    fun theDefaultLocaleWouldWriteACommaWhichIsTheTrap() {
        assertEquals("-6,5", String.format("%.1f", -6.5))
    }

    @Test
    fun numbersAreWrittenWithAPointWhateverTheLocale() {
        assertEquals("-6.5", WireText.number(-6.5))
        assertEquals("0", WireText.number(0.0))
        assertEquals("0", WireText.number(-0.0))
        assertEquals("0", WireText.number(-0.00001))
        assertEquals("12", WireText.number(12.0))
        assertEquals("0.1235", WireText.number(0.123456))
        assertEquals("1234567.25", WireText.number(1234567.25))
        assertEquals("0.33", WireText.number(1.0 / 3, decimals = 2))
        assertEquals("-144", WireText.number(-144f))
    }

    @Test
    fun aListIsOneStringOfSpaceSeparatedItems() {
        assertEquals("0 0.5 1 0.25", WireText.numbers(listOf(0.0, 0.5, 1.0, 0.25)))
        assertEquals("true", WireText.bool(true))
        assertEquals("false", WireText.bool(false))
    }

    @Test
    fun aTypedCommaIsReadAsAPoint() {
        assertEquals(-2.5, TypedNumber.parse("-2,5")!!, 0.0)
        assertEquals(-6.0, TypedNumber.parse(" −6 dB ")!!, 0.0)
        assertEquals(1.5, TypedNumber.parse("1.5s")!!, 0.0)
        assertNull(TypedNumber.parse("NaN"))
        assertNull(TypedNumber.parse(""))
        assertEquals("-2.5", TypedNumber.filterTyping("-2,5", allowDecimal = true))
        assertEquals("-25", TypedNumber.filterTyping("-2,5", allowDecimal = false))
    }
}
