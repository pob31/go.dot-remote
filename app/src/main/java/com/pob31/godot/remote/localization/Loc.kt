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

package com.pob31.godot.remote.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/*  WFS-DIY's remote's localisation, carried over whole: the words live in
    assets/lang/<code>.json, a key is a dotted path into it, and English stands in for
    a key a language lacks. LanguageParityTest keeps the two files' keys in step. */

val LocalLanguage = compositionLocalOf { "en" }

@Composable
fun loc(key: String): String {
    LocalLanguage.current // read so that a change of language recomposes
    return LocalizationManager.get(key)
}

@Composable
fun loc(key: String, vararg params: Pair<String, String>): String {
    LocalLanguage.current
    return LocalizationManager.get(key, *params)
}

/** For places outside composition: the link service's notification. */
fun locStatic(key: String): String = LocalizationManager.get(key)

fun locStatic(key: String, vararg params: Pair<String, String>): String =
    LocalizationManager.get(key, *params)
