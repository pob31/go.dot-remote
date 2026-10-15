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
import org.junit.Test
import java.util.Locale
import kotlin.math.sqrt

/** The rotary laws: frequency and width logarithmic, gain linear; the grain and the words, as SurfacePages.cpp. */
class EqLawsTest {

    private val tables = ShowFixture.tables()

    @Test
    fun aFrequencyIsLogarithmic_itsMiddleIsTheGeometricMean() {
        val band = tables.control("eqB2Freq")!!
        assertEquals(sqrt(20.0 * 20000.0), EqLaws.valueAt(band.law, 0.5, band.min, band.max), 1e-9)
        assertEquals(0.5, EqLaws.place(band.law, sqrt(20.0 * 20000.0), band.min, band.max), 1e-12)
        // Every decade is the same length of ring: 20-200, 200-2k, 2k-20k a third each.
        assertEquals(1.0 / 3.0, EqLaws.place(band.law, 200.0, band.min, band.max), 1e-12)
        assertEquals(2.0 / 3.0, EqLaws.place(band.law, 2000.0, band.min, band.max), 1e-12)
        assertEquals(0.0, EqLaws.place(band.law, 5.0, band.min, band.max), 0.0)
        assertEquals(1.0, EqLaws.place(band.law, 50000.0, band.min, band.max), 0.0)
    }

    @Test
    fun aWidthIsLogarithmic() {
        val q = tables.control("eqB3Q")!!
        assertEquals(1.0, EqLaws.valueAt(q.law, 0.5, q.min, q.max), 1e-12)
        assertEquals(0.25, EqLaws.place(q.law, sqrt(0.1), q.min, q.max), 1e-12)
    }

    @Test
    fun aGainIsLinear_noughtInTheMiddle() {
        val gain = tables.control("eqB1Gain")!!
        assertEquals(0.5, EqLaws.place(gain.law, 0.0, gain.min, gain.max), 0.0)
        assertEquals(12.0, EqLaws.valueAt(gain.law, 0.75, gain.min, gain.max), 1e-12)
        assertEquals(-24.0, EqLaws.valueAt(gain.law, -1.0, gain.min, gain.max), 0.0)
    }

    @Test
    fun theGrainATurnKeepsTo() {
        assertEquals(632.5, EqLaws.grained(EqLaw.FREQUENCY, 632.4555, 20.0, 20000.0), 1e-9)
        assertEquals(1234.0, EqLaws.grained(EqLaw.FREQUENCY, 1234.4, 20.0, 20000.0), 1e-9)
        assertEquals(-3.5, EqLaws.grained(EqLaw.GAIN, -3.45, -24.0, 24.0), 1e-12) // half away from nought, as std::round
        assertEquals(3.5, EqLaws.grained(EqLaw.GAIN, 3.45, -24.0, 24.0), 1e-12)
        assertEquals(24.0, EqLaws.grained(EqLaw.GAIN, 30.0, -24.0, 24.0), 0.0)
        assertEquals(0.71, EqLaws.grained(EqLaw.WIDTH, 0.7071, 0.1, 10.0), 1e-12)
    }

    @Test
    fun whatTheScreenSays() {
        assertEquals("80 Hz", EqLaws.valueText(EqLaw.FREQUENCY, 80.0))
        assertEquals("82.5 Hz", EqLaws.valueText(EqLaw.FREQUENCY, 82.5))
        assertEquals("125 Hz", EqLaws.valueText(EqLaw.FREQUENCY, 125.0))
        assertEquals("2.00 kHz", EqLaws.valueText(EqLaw.FREQUENCY, 2000.0))
        assertEquals("12.5 kHz", EqLaws.valueText(EqLaw.FREQUENCY, 12500.0))
        assertEquals("+3.5 dB", EqLaws.valueText(EqLaw.GAIN, 3.5))
        assertEquals("-3.5 dB", EqLaws.valueText(EqLaw.GAIN, -3.5))
        assertEquals("0.0 dB", EqLaws.valueText(EqLaw.GAIN, 0.0))
        assertEquals("Q 0.70", EqLaws.valueText(EqLaw.WIDTH, 0.7))
        assertEquals("Q 10.0", EqLaws.valueText(EqLaw.WIDTH, 10.0))
        assertEquals("-inf dB", EqLaws.valueText(EqLaw.LEVEL, -120.0))
        assertEquals("Lo shelf", EqLaws.shapeText("lowShelf"))
        assertEquals("Hi shelf", EqLaws.shapeText("highShelf"))
        assertEquals("Peak", EqLaws.shapeText("peak"))
    }

    @Test
    fun noLocaleMovesThePoint() {
        val was = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertEquals("2.00 kHz", EqLaws.valueText(EqLaw.FREQUENCY, 2000.0))
            assertEquals("-6.5", FieldText.levelText(-6.5))
        } finally {
            Locale.setDefault(was)
        }
    }

    @Test
    fun aShapePressedAndTurned() {
        assertEquals("lowShelf", EqLaws.pressedShape("peak", "lowShelf"))
        assertEquals("peak", EqLaws.pressedShape("lowShelf", "lowShelf"))
        assertEquals("highShelf", EqLaws.pressedShape("", "highShelf"))
        assertEquals("highShelf", EqLaws.turnedShape("peak", 1, "highShelf"))
        assertEquals("peak", EqLaws.turnedShape("highShelf", -2, "highShelf"))
        assertEquals("highShelf", EqLaws.turnedShape("highShelf", 0, "highShelf"))
    }
}
