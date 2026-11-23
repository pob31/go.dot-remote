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

/*  HOW A FINGER MOVES THE EQ'S AND THE DYNAMICS' GRAPHS AND BARS, kept apart from the
    drawing so a test reads it. A graph is a picture of the sound, so a handle moves WITH
    the finger - its place is where it was plus how far the finger went, never where the
    finger landed, so nothing jumps on touch-down. A bar is a fader laid flat: relative,
    finer the further the finger strays from it, as a fader is (the scrub's rate). A
    pinch is the desktop's: the width of a band by q0·d0/d (client/model/Eq.cpp), the
    compressor's knee by how far the fingers spread across the graph. */

/** A decibel axis from [low] to [high], drawn along [length] pixels; [upward] puts [high] at nought pixels (a y axis). */
class DbAxis(val low: Double, val high: Double, private val upward: Boolean) {
    fun pixelOf(db: Double, length: Float): Float {
        val at = ((db - low) / (high - low)).coerceIn(0.0, 1.0)
        return (if (upward) (1.0 - at) else at).toFloat() * length
    }

    fun dbAt(pixel: Float, length: Float): Double {
        if (length <= 0f) return low
        val at = (pixel / length).toDouble().coerceIn(0.0, 1.0)
        return low + (high - low) * (if (upward) 1.0 - at else at)
    }
}

object GraphMoves {
    /** The EQ graph's gain axis: ±24 dB, as the desktop's (`rangeDb`). */
    const val EQ_RANGE_DB = 24.0

    val eqGain = DbAxis(-EQ_RANGE_DB, EQ_RANGE_DB, upward = true)

    /** Where a frequency sits across a graph [width] pixels wide, in octaves from 20 Hz. */
    fun xOfFrequency(frequency: Double, width: Float): Float = (EqCurve.placeOf(frequency) * width).toFloat()

    fun frequencyAtX(x: Float, width: Float): Double = if (width <= 0f) EqCurve.LOWEST_HZ else EqCurve.frequencyAt((x / width).toDouble())

    /** The narrowest and widest a band's Q is taken to by a hand (the desktop's `eqQLowest`/`eqQHighest`). */
    const val Q_LOWEST = 0.1
    const val Q_HIGHEST = 10.0

    /** Fingers [startDistance] apart at the pinch's start, [distance] now: the band's Q from [startQ]. Spread is wider. */
    fun pinchedQ(startQ: Double, startDistance: Float, distance: Float): Double =
        if (distance <= 0f || startDistance <= 0f) startQ
        else EqLaws.roundedTo((startQ * startDistance / distance).coerceIn(Q_LOWEST, Q_HIGHEST), 0.01).coerceIn(Q_LOWEST, Q_HIGHEST)

    /** The knee's whole range, 0..24 dB: spreading the fingers by the graph's [width] opens it all. */
    const val KNEE_HIGHEST = 24.0

    fun pinchedKnee(startKnee: Double, startDistance: Float, distance: Float, width: Float): Double =
        if (width <= 0f) startKnee
        else EqLaws.roundedTo((startKnee + (distance - startDistance) / width * KNEE_HIGHEST).coerceIn(0.0, KNEE_HIGHEST), 0.1)
}

/**
 * A BAR TURNED BY A FINGER: [move] by [along] pixels along a bar [lengthPx] long, [across]
 * pixels away from it; the place, 0..1, comes back - every [unitPx] away halves what a
 * pixel moves, as a fader's (FaderDrag).
 */
class BarDrag(startPlace: Double, private val lengthPx: Float, private val unitPx: Float) {
    var place = startPlace.coerceIn(0.0, 1.0)
        private set

    fun move(along: Float, across: Float): Double {
        if (lengthPx > 0f) place = (place + along / lengthPx * Scrub.rateFor(across.toDouble(), unitPx.toDouble())).coerceIn(0.0, 1.0)
        return place
    }
}

/**
 * WHAT A BAR'S BOX SAYS AND HOW A TYPED NUMBER IS READ BACK: the number in [unit] - a
 * frequency in hertz, or in kilohertz from a thousand, as the desktop says it - with the
 * places it is typed to, and the [scale] a typed number is multiplied by to be the row's.
 */
data class BarNumber(val text: String, val unit: String?, val decimals: Int, val scale: Double) {
    companion object {
        fun of(law: EqLaw, value: Double): BarNumber = when (law) {
            EqLaw.FREQUENCY ->
                if (value >= 999.95) BarNumber(EqLaws.fixed(value / 1000.0, if (value >= 9995.0) 2 else 3), "kHz", 3, 1000.0)
                else BarNumber(EqLaws.fixed(value, if (value < 99.95 && (value * 10.0).toLong() % 10 != 0L) 1 else 0), "Hz", 1, 1.0)
            EqLaw.GAIN, EqLaw.DECIBELS, EqLaw.LEVEL -> BarNumber(EqLaws.fixed(value, 1), "dB", 1, 1.0)
            EqLaw.WIDTH -> BarNumber(EqLaws.fixed(value, 2), null, 2, 1.0)
            EqLaw.RATIO -> BarNumber(EqLaws.fixed(value, if (value < 9.95) 1 else 0), ":1", 1, 1.0)
            EqLaw.TIME -> BarNumber(EqLaws.fixed(value, if (value < 1.0) 2 else if (value < 100.0) 1 else 0), "ms", 2, 1.0)
            EqLaw.PAN, EqLaw.STEREO_WIDTH -> BarNumber(EqLaws.fixed(value, 2), null, 2, 1.0)
            EqLaw.SHAPE -> BarNumber("", null, 0, 1.0)
        }
    }
}
