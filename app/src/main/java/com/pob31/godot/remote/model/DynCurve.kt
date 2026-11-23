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

/*  WHAT A SOUND'S OWN DYNAMICS DO TO A LEVEL, AS GO.DOT DRAWS IT (client/ui/
    DynPanelComponent.cpp, spatcore's DynamicsModule to the letter): what comes out for what
    goes in, in decibels both ways - the compressor's (1/R - 1) dB a decibel over its
    threshold with a quadratic knee, then the make-up; the gate's 100:1 under its threshold,
    floored by its range. The attack, release and hold are time and are not drawn. */
object DynCurve {
    /** The ratio the gate's expander works at (CueDyn, SC.6). */
    const val GATE_RATIO = 100.0

    /** The ratio the ratio handle stops at, as the row does. */
    const val HIGHEST_RATIO = 100.0

    /** The compressor's graph: in -60..0 dB across, out -60..+12 dB up. */
    const val COMP_IN_LOW = -60.0
    const val COMP_IN_HIGH = 0.0
    const val COMP_OUT_LOW = -60.0
    const val COMP_OUT_HIGH = 12.0

    /** The gate's: -90..0 dB both ways. */
    const val GATE_LOW = -90.0
    const val GATE_HIGH = 0.0

    /** The reduction bar's foot (DynPanelComponent `meterFloorDb`). */
    const val REDUCTION_FLOOR = -24.0

    fun compressorOut(input: Double, threshold: Double, ratio: Double, knee: Double, makeup: Double): Double {
        val slope = 1.0 / maxOf(ratio, 1.0) - 1.0
        val over = input - threshold
        val half = 0.5 * knee
        val gain = when {
            over >= half -> slope * over
            over > -half -> {
                val t = over + half
                slope * t * t / (2.0 * knee)
            }
            else -> 0.0
        }
        return input + gain + makeup
    }

    fun gateOut(input: Double, threshold: Double, range: Double): Double {
        val over = input - threshold
        return if (over >= 0.0) input else input + maxOf(range, (GATE_RATIO - 1.0) * over)
    }

    /**
     * THE RATIO FROM WHERE THE LOUDEST SOUND COMES OUT: at nought in, T + (0 - T)/R + make-up
     * out, turned round for R; null where the threshold is too near nought to tell.
     */
    fun ratioFromHandle(outDb: Double, threshold: Double, makeup: Double): Double? {
        if (threshold >= -0.5) return null
        val above = outDb - makeup - threshold
        return if (above <= -threshold / HIGHEST_RATIO) HIGHEST_RATIO else (-threshold / above).coerceAtLeast(1.0)
    }
}
