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

/** A cue as a line reads it: its number and its name. */
data class CueLabel(val id: String, val number: String, val name: String)

/**
 * The cue in standby on the list in focus, read by address as the desktop's transport does
 * (client/model/Transport.cpp): `/godot/list/focus` names the list, its `standby` the cue.
 * The name is the cue's own, or the one Go.dot works out while it has none (`autoName`).
 * Null when no list is in focus or nothing stands by.
 */
fun standbyCue(mirror: Mirror): CueLabel? {
    val list = mirror.text("/godot/list/focus")?.takeIf { it.isNotEmpty() } ?: return null
    val cue = mirror.text("/godot/list/$list/standby")?.takeIf { it.isNotEmpty() } ?: return null
    return cueLabel(mirror, cue)
}

fun cueLabel(mirror: Mirror, cue: String): CueLabel {
    val number = mirror.text("/godot/cue/$cue/number") ?: ""
    val name = mirror.text("/godot/cue/$cue/name")?.takeIf { it.isNotEmpty() }
        ?: mirror.text("/godot/cue/$cue/autoName")
        ?: ""
    return CueLabel(cue, number, name)
}
