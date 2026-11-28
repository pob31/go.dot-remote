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
import kotlin.math.roundToLong

/*  HOW A TIME IS WRITTEN (Go.dot's namespace draft §151, BKM), as the window
    writes it (client/model/TimeText) and the page (clients/console/model/time.js):
    the show's `/godot/list/timeFormat`, seconds - `83.5` - or the clock - `1:23.5`,
    the hours only past an hour, `1:02:03.5` - tenths kept. Typed either way
    whatever is shown. Seconds is the look there was. Digit by digit: no locale
    puts a comma in it. */
object ShownTime {
    const val ADDRESS = "/godot/list/timeFormat"

    /** Whether the show asks for the clock; written by the mirror as the row arrives. */
    @Volatile var clock: Boolean = false

    fun follow(word: String?) {
        clock = word == "clock"
    }
}

/** Minutes and seconds, the hours past an hour, [decimals] places; [trim] takes off trailing noughts. */
fun clockTime(seconds: Double, decimals: Int = 1, trim: Boolean = false): String {
    val places = decimals.coerceIn(0, 3)
    var scale = 1L
    repeat(places) { scale *= 10 }

    //  Rounded once, in units of the last place: 59.96 at a tenth is 1:00.0, never 0:60.0.
    val units = (abs(seconds) * scale).roundToLong()
    val whole = units / scale
    val fraction = units % scale
    val hours = whole / 3600
    val minutes = (whole / 60) % 60
    val secs = whole % 60

    val two = { n: Long -> if (n < 10) "0$n" else "$n" }
    val out = StringBuilder(if (seconds < 0 && units > 0) "-" else "")
    out.append(if (hours > 0) "$hours:${two(minutes)}:${two(secs)}" else "$minutes:${two(secs)}")

    if (places > 0) {
        var digits = fraction.toString().padStart(places, '0')
        if (trim) digits = digits.trimEnd('0')
        if (digits.isNotEmpty()) out.append('.').append(digits)
    }
    return out.toString()
}

/** A reading to a tenth in the show's format: `12.3`, or `0:12.3`. */
fun readingTime(seconds: Double): String = if (ShownTime.clock) clockTime(seconds, 1) else oneDecimal(seconds)

/** A time as typed: `83.5`, `83,5`, `1:23.5`, `1:02:03.5`; null for what is not one. */
fun typedTime(text: String): Double? {
    val parts = text.trim().split(':')
    if (parts.size > 3 || parts.any { it.isBlank() }) return null

    var total = 0.0
    for ((at, part) in parts.withIndex()) {
        val value = part.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        if (value < 0) return null
        if (at > 0 && value >= 60) return null
        if (at + 1 < parts.size && floor(value) != value) return null
        total = total * 60 + value
    }
    return total
}
