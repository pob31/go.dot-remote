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

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

/*  HOW AN EQ CONTROL MOVES AND READS, the engine's surface/SurfacePages.cpp ported: a
    frequency and a width are logarithmic - a turn is a ratio - and a gain is linear, in
    decibels; a shape is a choice of two. A rotary on the tablet is turned by a finger
    rather than by detents, so the travel here is a place from 0 to 1 along the control's
    ring, mapped by the law, and the value kept to the grain a turn keeps it to (a tenth
    of a hertz under a kilohertz and a hertz above, a tenth of a decibel, a hundredth of a
    width). Everything is written digit by digit: no locale moves a point. */
object EqLaws {

    /** Where [value] sits on the ring, 0 at the start of the travel to 1 at its end. */
    fun place(law: EqLaw, value: Double, min: Double, max: Double, fader: FaderCurve? = null): Double = when (law) {
        EqLaw.FREQUENCY, EqLaw.WIDTH ->
            if (min > 0.0 && max > min) (ln(value.coerceIn(min, max) / min) / ln(max / min)).coerceIn(0.0, 1.0) else 0.0
        EqLaw.GAIN -> if (max > min) ((value - min) / (max - min)).coerceIn(0.0, 1.0) else 0.0
        EqLaw.LEVEL -> fader?.fractionForDb(value) ?: 0.0
        EqLaw.SHAPE -> 0.0
    }

    /** And back: the value at [place] on the ring, unrounded. */
    fun valueAt(law: EqLaw, place: Double, min: Double, max: Double, fader: FaderCurve? = null): Double {
        val at = place.coerceIn(0.0, 1.0)
        return when (law) {
            EqLaw.FREQUENCY, EqLaw.WIDTH -> if (min > 0.0 && max > min) min * (max / min).pow(at) else min
            EqLaw.GAIN -> min + (max - min) * at
            EqLaw.LEVEL -> fader?.dbForFraction(at) ?: min
            EqLaw.SHAPE -> min
        }
    }

    /** [value] kept to the grain a hand can tell apart, inside the range (SurfacePages.cpp `turned`). */
    fun grained(law: EqLaw, value: Double, min: Double, max: Double, silenceDb: Double = -120.0): Double = when (law) {
        EqLaw.FREQUENCY -> roundedTo(value.coerceIn(min, max), if (value < 1000.0) 0.1 else 1.0).coerceIn(min, max)
        EqLaw.GAIN -> roundedTo(value, 0.1).coerceIn(min, max)
        EqLaw.WIDTH -> roundedTo(value.coerceIn(min, max), 0.01).coerceIn(min, max)
        EqLaw.LEVEL -> if (value <= silenceDb) silenceDb.coerceIn(min, max) else roundedTo(value, 0.1).coerceIn(min, max)
        EqLaw.SHAPE -> value
    }

    /** A step that is a whole fraction of one rounded by its count, so 0.1 stays exact in binary arithmetic. */
    fun roundedTo(value: Double, step: Double): Double {
        if (step > 0.0 && step < 1.0) {
            val per = Math.round(1.0 / step).toDouble()
            if (per > 0.0 && abs(per * step - 1.0) < 1.0e-9) return halfAway(value * per) / per
        }
        return halfAway(value / step) * step
    }

    /** std::round's rounding: a half goes away from nought, so -3.45 dB keeps to -3.5 as the engine's does. */
    fun halfAway(value: Double): Double = if (value < 0.0) -floor(-value + 0.5) else floor(value + 0.5)

    /** A shape pressed: the peak becomes the shelf and anything else the peak (`pressedShape`). */
    fun pressedShape(current: String, shelf: String): String = if (current == "peak" || current.isEmpty()) shelf else "peak"

    /** A shape turned: clockwise the shelf, anticlockwise the peak (`turnedShape`). */
    fun turnedShape(current: String, steps: Int, shelf: String): String = when {
        steps > 0 -> shelf
        steps < 0 -> "peak"
        else -> current
    }

    /** A shape as a screen says it: "Peak", "Lo shelf", "Hi shelf" (`shapeText`). */
    fun shapeText(shape: String): String = when (shape) {
        "lowShelf" -> "Lo shelf"
        "highShelf" -> "Hi shelf"
        else -> "Peak"
    }

    /**
     * What a control's screen says its value is (`valueText`): "80 Hz", "82.5 Hz",
     * "2.00 kHz", "12.5 kHz"; "+3.5 dB"; "Q 0.70"; "-inf dB" at silence.
     */
    fun valueText(law: EqLaw, value: Double, silenceDb: Double = -120.0): String = when (law) {
        EqLaw.FREQUENCY ->
            if (value >= 999.95) {
                val kilohertz = value / 1000.0
                fixed(kilohertz, if (kilohertz >= 9.995) 1 else 2) + " kHz"
            } else {
                val tenths = (value * 10.0).roundToLong()
                fixed(value, if (value < 99.95 && tenths % 10 != 0L) 1 else 0) + " Hz"
            }
        EqLaw.GAIN -> (if ((value * 10.0).roundToLong() > 0) "+" else "") + fixed(value, 1) + " dB"
        EqLaw.WIDTH -> "Q " + fixed(value, if (value >= 9.995) 1 else 2)
        EqLaw.LEVEL -> if (value <= silenceDb) "-inf dB" else fixed(value, 1) + " dB"
        EqLaw.SHAPE -> ""
    }

    /** [value] with [decimals] places, rounded half away from nought, digit by digit (`fixed`). */
    fun fixed(value: Double, decimals: Int): String {
        var scale = 1L
        repeat(decimals) { scale *= 10 }
        val scaled = (abs(value) * scale).roundToLong()
        val out = StringBuilder()
        if (value < 0.0 && scaled != 0L) out.append('-')
        out.append(scaled / scale)
        if (decimals > 0) {
            out.append('.')
            val fraction = (scaled % scale).toString()
            repeat(decimals - fraction.length) { out.append('0') }
            out.append(fraction)
        }
        return out.toString()
    }
}
