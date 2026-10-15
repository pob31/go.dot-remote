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

/**
 * The one reader for a number typed into a box - WFS-DIY's remote's, carried over.
 *
 * Trimmed; "−" and "," read as "-" and "." (a French keyboard types a comma, and the
 * engine refuses one, so it is read here and never sent); a trailing unit (dB, s, Hz, %...)
 * ignored; a number only when it is finite.
 */
object TypedNumber {

    fun parse(text: String): Double? {
        val cleaned = text.trim()
            .replace('−', '-')
            .replace(',', '.')
            .trimEnd { !(it.isDigit() || it == '.') }
            .trim()
        if (cleaned.isEmpty()) return null
        return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** What a number box may hold while it is typed: a comma becomes a point instead of vanishing. */
    fun filterTyping(text: String, allowDecimal: Boolean): String =
        text.map {
            when (it) {
                ',' -> '.'
                '−' -> '-'
                else -> it
            }
        }.filter { it.isDigit() || it == '-' || (allowDecimal && it == '.') }
            .joinToString("")
}
