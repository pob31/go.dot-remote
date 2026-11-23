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

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*  WHAT AN EQ DOES TO A SOUND, AS GO.DOT WORKS IT OUT (engine audio/EqMath.h, ported line
    for line): the cookbook's second-order sections - a peak, a low and a high shelf, a
    Butterworth high-pass and low-pass - and the whole EQ's response, the sum in decibels of
    every section that is in. The curve the EQ graph draws is this, sampled in octaves from
    20 Hz to 20 kHz at a 48 kHz picture rate, as the desktop's EQ panel draws it
    (client/model/Eq.cpp `eqCurve`). Nothing here sounds: it is the picture's maths. */

/** One section's coefficients, a0 normalised to one. */
data class Biquad(val b0: Double = 1.0, val b1: Double = 0.0, val b2: Double = 0.0, val a1: Double = 0.0, val a2: Double = 0.0)

/** One of the four bands: [shape] is peak, lowShelf or highShelf. */
data class EqBand(val on: Boolean, val shape: String, val freq: Double, val gain: Double, val q: Double) {
    /** A band switched out, or with a gain of exactly nought, is out of the signal (EqSettings.h `bandIsActive`). */
    val active: Boolean get() = on && gain != 0.0
}

/** The whole EQ as its rows say it (EqSettings.h). */
data class EqShape(
    val on: Boolean = true,
    val hpf: Boolean = false,
    val hpfFreq: Double = 80.0,
    val lpf: Boolean = false,
    val lpfFreq: Double = 12000.0,
    val bands: List<EqBand> = listOf(
        EqBand(true, "peak", 100.0, 0.0, 0.7),
        EqBand(true, "peak", 500.0, 0.0, 0.7),
        EqBand(true, "peak", 2000.0, 0.0, 0.7),
        EqBand(true, "peak", 8000.0, 0.0, 0.7),
    ),
)

object EqCurve {
    const val PICTURE_RATE = 48000.0
    const val LOWEST_HZ = 20.0
    const val HIGHEST_HZ = 20000.0
    private const val BUTTERWORTH_Q = 0.70710678118654752440

    fun clampFrequency(frequency: Double, sampleRate: Double): Double = frequency.coerceIn(10.0, maxOf(100.0, sampleRate * 0.45))

    fun clampQ(q: Double): Double = q.coerceIn(0.05, 20.0)

    private class Angles(val sinW: Double, val cosW: Double, val alpha: Double)

    private fun angles(sampleRate: Double, frequency: Double, q: Double): Angles {
        val rate = if (sampleRate > 0.0) sampleRate else PICTURE_RATE
        val w = 2.0 * PI * clampFrequency(frequency, rate) / rate
        val s = sin(w)
        return Angles(s, cos(w), s / (2.0 * clampQ(q)))
    }

    private fun normalised(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double): Biquad {
        val inv = 1.0 / a0
        return Biquad(b0 * inv, b1 * inv, b2 * inv, a1 * inv, a2 * inv)
    }

    fun peak(sampleRate: Double, frequency: Double, q: Double, gainDb: Double): Biquad {
        val a = 10.0.pow(gainDb / 40.0)
        val w = angles(sampleRate, frequency, q)
        return normalised(1.0 + w.alpha * a, -2.0 * w.cosW, 1.0 - w.alpha * a, 1.0 + w.alpha / a, -2.0 * w.cosW, 1.0 - w.alpha / a)
    }

    fun lowShelf(sampleRate: Double, frequency: Double, q: Double, gainDb: Double): Biquad {
        val a = 10.0.pow(gainDb / 40.0)
        val w = angles(sampleRate, frequency, q)
        val c = w.cosW
        val twoRootA = 2.0 * sqrt(a) * w.alpha
        return normalised(
            a * ((a + 1.0) - (a - 1.0) * c + twoRootA),
            2.0 * a * ((a - 1.0) - (a + 1.0) * c),
            a * ((a + 1.0) - (a - 1.0) * c - twoRootA),
            (a + 1.0) + (a - 1.0) * c + twoRootA,
            -2.0 * ((a - 1.0) + (a + 1.0) * c),
            (a + 1.0) + (a - 1.0) * c - twoRootA,
        )
    }

    fun highShelf(sampleRate: Double, frequency: Double, q: Double, gainDb: Double): Biquad {
        val a = 10.0.pow(gainDb / 40.0)
        val w = angles(sampleRate, frequency, q)
        val c = w.cosW
        val twoRootA = 2.0 * sqrt(a) * w.alpha
        return normalised(
            a * ((a + 1.0) + (a - 1.0) * c + twoRootA),
            -2.0 * a * ((a - 1.0) + (a + 1.0) * c),
            a * ((a + 1.0) + (a - 1.0) * c - twoRootA),
            (a + 1.0) - (a - 1.0) * c + twoRootA,
            2.0 * ((a - 1.0) - (a + 1.0) * c),
            (a + 1.0) - (a - 1.0) * c - twoRootA,
        )
    }

    /** -3 dB at the frequency, 12 dB an octave below. */
    fun highPass(sampleRate: Double, frequency: Double): Biquad {
        val w = angles(sampleRate, frequency, BUTTERWORTH_Q)
        return normalised((1.0 + w.cosW) * 0.5, -(1.0 + w.cosW), (1.0 + w.cosW) * 0.5, 1.0 + w.alpha, -2.0 * w.cosW, 1.0 - w.alpha)
    }

    /** -3 dB at the frequency, 12 dB an octave above. */
    fun lowPass(sampleRate: Double, frequency: Double): Biquad {
        val w = angles(sampleRate, frequency, BUTTERWORTH_Q)
        return normalised((1.0 - w.cosW) * 0.5, 1.0 - w.cosW, (1.0 - w.cosW) * 0.5, 1.0 + w.alpha, -2.0 * w.cosW, 1.0 - w.alpha)
    }

    fun forBand(band: EqBand, sampleRate: Double): Biquad = when (band.shape) {
        "lowShelf" -> lowShelf(sampleRate, band.freq, band.q, band.gain)
        "highShelf" -> highShelf(sampleRate, band.freq, band.q, band.gain)
        else -> peak(sampleRate, band.freq, band.q, band.gain)
    }

    /** |H| of one section at [frequency], in decibels; -200 where the maths has nothing to say. */
    fun magnitudeDb(c: Biquad, frequency: Double, sampleRate: Double = PICTURE_RATE): Double {
        val rate = if (sampleRate > 0.0) sampleRate else PICTURE_RATE
        val w = 2.0 * PI * frequency.coerceIn(0.0, rate * 0.5) / rate
        val cosW = cos(w)
        val cos2W = cos(2.0 * w)
        val numerator = c.b0 * c.b0 + c.b1 * c.b1 + c.b2 * c.b2 + 2.0 * (c.b0 * c.b1 + c.b1 * c.b2) * cosW + 2.0 * c.b0 * c.b2 * cos2W
        val denominator = 1.0 + c.a1 * c.a1 + c.a2 * c.a2 + 2.0 * (c.a1 + c.a1 * c.a2) * cosW + 2.0 * c.a2 * cos2W
        if (denominator <= 0.0 || numerator <= 0.0) return -200.0
        return 10.0 * log10(numerator / denominator)
    }

    /** The whole EQ at [frequency]: nought when it is out, else every section that is in, summed. */
    fun responseDb(eq: EqShape, frequency: Double, sampleRate: Double = PICTURE_RATE): Double {
        if (!eq.on) return 0.0
        var total = 0.0
        if (eq.hpf) total += magnitudeDb(highPass(sampleRate, eq.hpfFreq), frequency, sampleRate)
        if (eq.lpf) total += magnitudeDb(lowPass(sampleRate, eq.lpfFreq), frequency, sampleRate)
        for (band in eq.bands) if (band.active) total += magnitudeDb(forBand(band, sampleRate), frequency, sampleRate)
        return total
    }

    /** The frequency [at] of the way along the graph, 0..1, evenly in octaves from 20 Hz to 20 kHz. */
    fun frequencyAt(at: Double): Double = LOWEST_HZ * (HIGHEST_HZ / LOWEST_HZ).pow(at.coerceIn(0.0, 1.0))

    /** And back: how far along the graph [frequency] sits. */
    fun placeOf(frequency: Double): Double =
        (kotlin.math.ln(frequency.coerceIn(LOWEST_HZ, HIGHEST_HZ) / LOWEST_HZ) / kotlin.math.ln(HIGHEST_HZ / LOWEST_HZ))

    /** The curve the graph draws: [points] (place 0..1, dB) evenly in octaves. */
    fun curve(eq: EqShape, points: Int = 240): List<Pair<Double, Double>> =
        (0 until points).map { i ->
            val at = i.toDouble() / (points - 1)
            at to responseDb(eq, frequencyAt(at))
        }
}

/**
 * The EQ as a reading of its rows says it, with [valueOf] giving any number row - the
 * engine's, or the one a finger is moving and has not heard back yet.
 */
fun EqReading.shape(valueOf: (EqKnob) -> Double = { it.value }): EqShape {
    val byRow = knobs.associateBy { it.control.row }
    fun number(row: String, otherwise: Double) = byRow[row]?.let(valueOf) ?: otherwise
    fun switched(row: String, otherwise: Boolean) = switches.firstOrNull { it.row == row }?.on ?: otherwise
    val rest = EqShape()
    return EqShape(
        on = on?.on ?: true,
        hpf = switched("eqHpf", false),
        hpfFreq = number("eqHpfFreq", rest.hpfFreq),
        lpf = switched("eqLpf", false),
        lpfFreq = number("eqLpfFreq", rest.lpfFreq),
        bands = (1..4).map { n ->
            val band = rest.bands[n - 1]
            EqBand(
                on = switched("eqB${n}On", true),
                shape = byRow["eqB${n}Shape"]?.shape?.takeIf { it.isNotEmpty() } ?: "peak",
                freq = number("eqB${n}Freq", band.freq),
                gain = number("eqB${n}Gain", band.gain),
                q = number("eqB${n}Q", band.q),
            )
        },
    )
}
