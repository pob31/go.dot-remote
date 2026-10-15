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

import java.util.Locale

/**
 * Values as the engine reads a write (remote protocol §6): ONE string, whatever the
 * node's type. Numbers in no locale's way - a decimal point, never a comma - whatever the
 * tablet's language: every number here goes through [Locale.ROOT], never the default.
 */
object WireText {

    /** [value] with at most [decimals] places, trailing zeros dropped: -6.5, 0, 0.1234. */
    fun number(value: Double, decimals: Int = 4): String {
        require(value.isFinite()) { "not a number the engine can take: $value" }
        var text = String.format(Locale.ROOT, "%.${decimals}f", value)
        if (text.contains('.')) text = text.trimEnd('0').trimEnd('.')
        if (text == "-0") text = "0"
        return text
    }

    fun number(value: Float, decimals: Int = 4): String = number(value.toDouble(), decimals)

    fun integer(value: Long): String = value.toString()

    fun bool(value: Boolean): String = if (value) "true" else "false"

    /** A list (a mesh, points) is one string, its items separated by spaces. */
    fun list(items: List<String>): String = items.joinToString(" ")

    fun numbers(values: List<Double>, decimals: Int = 4): String = list(values.map { number(it, decimals) })
}
