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
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * A SCRUB IN PROGRESS: the arithmetic of a finger dragging a playhead - Go.dot's
 * client/model/Scrub.{h,cpp}, ported. The author's design (2026-09-18): "Scrubbing within
 * the bounds of the strip is 1:1 but dragging with the cursor going above or below
 * increases the precision of the increments for fine tuning. [...] Pushing against the
 * window edge in precision mode will keep sliding the cursor in the given direction."
 *
 * - RELATIVE FROM TOUCH-DOWN: the head does not jump to the finger; it moves by the
 *   finger's travel (PRD §3.17).
 * - 1:1 INSIDE THE STRIP: a pixel of travel is a pixel of the strip.
 * - FINER ABOVE AND BELOW: every [Setup.unit] pixels away from the strip halves what a
 *   pixel moves, continuously, down to 1/256; the travel is integrated step by step at the
 *   rate each step was made at, so the head never jumps as the rate changes.
 * - PUSHED AGAINST AN EDGE it slides on at [PUSH_PIXELS_PER_SECOND], at the rate the
 *   distance sets, until the finger comes back.
 *
 * The target is a second, held inside the stretch the strip covers. A media run's seek is
 * sent as the hand settles, at most once each 200 ms ([due]); a scene's once, when the hand
 * lets go ([Setup.onRelease], the author's RB): each seek of a scene re-seats its members.
 */
class Scrub {
    data class Setup(
        /** Where the head is when the finger takes it, in seconds. */
        val position: Double,
        /** Where the material ends; nought means unbounded. */
        val to: Double,
        /** What a pixel of travel is worth inside the strip. */
        val secondsPerPixel: Double,
        /** How many pixels above or below the strip halve the rate. */
        val unit: Double = 40.0,
        /** The finger's x when it took the head. */
        val x: Double = 0.0,
        /** Where the material begins: the head goes no earlier. */
        val from: Double = 0.0,
        /** Sent only when the hand lets go, never while it moves (a scene, RB). */
        val onRelease: Boolean = false,
    )

    var active = false
        private set
    var sendsOnRelease = false
        private set

    /** Where the head is now, in seconds. */
    var target = 0.0
        private set

    /** The rate the last move was made at: 1, 1/2 ... 1/256. */
    var rate = 1.0
        private set

    private var earliest = 0.0
    private var latest = 0.0
    private var secondsPerPixel = 0.0
    private var unit = 40.0
    private var lastX = 0.0
    private var sentAt = 0.0
    private var sentMs = -1.0e9

    fun begin(setup: Setup) {
        active = true
        sendsOnRelease = setup.onRelease
        earliest = maxOf(0.0, setup.from)
        latest = setup.to
        secondsPerPixel = setup.secondsPerPixel
        unit = if (setup.unit > 0.0) setup.unit else 40.0
        lastX = setup.x
        rate = 1.0
        target = clamp(setup.position)
        // Where the finger took it counts as sent: a grab is not a seek.
        sentAt = target
        sentMs = -1.0e9
    }

    fun end() {
        active = false
    }

    /** The finger moved to [x], [distance] pixels outside the strip (nought inside). Where the head now is. */
    fun moveTo(x: Double, distance: Double): Double {
        if (!active) return target
        rate = rateFor(distance, unit)
        target = clamp(target + (x - lastX) * secondsPerPixel * rate)
        lastX = x
        return target
    }

    /** Pushed against an edge: slides [direction] (-1, +1) for [seconds] of wall time at the rate [distance] sets. */
    fun push(direction: Int, seconds: Double, distance: Double): Double {
        if (!active || direction == 0 || !(seconds > 0.0)) return target
        rate = rateFor(distance, unit)
        target = clamp(target + direction * PUSH_PIXELS_PER_SECOND * seconds * secondsPerPixel * rate)
        return target
    }

    /**
     * Whether a seek is due: the head moved since the last one and [intervalMs] has
     * passed; marks it sent when it is. Never for a scrub sent on release alone.
     */
    fun due(nowMs: Double, intervalMs: Double = 200.0): Boolean {
        if (!active || sendsOnRelease || !unsent() || nowMs - sentMs < intervalMs) return false
        sentMs = nowMs
        sentAt = target
        return true
    }

    /** The hand let go: true when the head is somewhere not yet sent. */
    fun settle(): Boolean {
        if (!active) return false
        val moved = unsent()
        sentAt = target
        return moved
    }

    private fun unsent() = abs(target - sentAt) > 1.0e-9

    private fun clamp(seconds: Double): Double {
        val held = maxOf(earliest, seconds)
        return if (latest > earliest) minOf(held, latest) else held
    }

    companion object {
        const val PUSH_PIXELS_PER_SECOND = 90.0

        /** The rate [distance] pixels from the strip sets: halved per [unit], continuously, floored at 1/256. */
        fun rateFor(distance: Double, unit: Double): Double {
            if (!(distance > 0.0) || !(unit > 0.0)) return 1.0
            return maxOf(1.0 / 256.0, 0.5.pow(distance / unit))
        }

        /** The rate as a word beside the clock: empty at 1:1, else "1/8". */
        fun rateText(rate: Double): String = if (!(rate < 1.0)) "" else "1/" + (1.0 / rate).roundToLong()
    }
}
