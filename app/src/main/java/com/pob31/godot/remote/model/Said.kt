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
 * Something to be said, not yet in a language: a key of the language files
 * (`assets/lang/<code>.json`) and what fills its `{placeholders}`. The model decides WHAT
 * is said - the desktop's sentences, ported - and the screen says it in the tablet's
 * language, so a test asserts the meaning with no language loaded.
 */
data class Said(val key: String, val params: List<Pair<String, String>> = emptyList()) {
    constructor(key: String, vararg params: Pair<String, String>) : this(key, params.toList())

    /** Words that are already words - the engine's own sentence, a raw record - said as they came. */
    companion object {
        const val VERBATIM = "remote.verbatim"

        fun verbatim(text: String) = Said(VERBATIM, "text" to text)
    }
}
