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
 * The shape of Go.dot's addresses: an object's rows live at `/godot/<owner>/<id>/<row>`,
 * the id eight characters of Crockford's base 32 (digits and capitals without I, L, O, U).
 * `/godot/cue/F7HR8TVD/level` is the row `level` of the cue `F7HR8TVD`; `/godot/list/focus`
 * is no object's - `focus` is not an id - and is a row of its own.
 */
object Addresses {
    const val ROOT = "/godot/"
    const val TICK = "/godot/engine/tick"
    const val LAST_ERROR = "/godot/engine/lastError"
    const val REMOTES = "/godot/network/remotes"

    /** An address read as an object's row; [row] is empty for the object's own node. */
    data class Owned(val owner: String, val id: String, val row: String) {
        val objectPath: String get() = "$ROOT$owner/$id"
    }

    fun isId(text: CharSequence, start: Int = 0, end: Int = text.length): Boolean {
        if (end - start != 8) return false
        for (i in start until end) {
            val c = text[i]
            val ok = (c in '0'..'9') || (c in 'A'..'Z' && c != 'I' && c != 'L' && c != 'O' && c != 'U')
            if (!ok) return false
        }
        return true
    }

    /** The object an address belongs to, or null when it is no object's row. */
    fun owned(address: String): Owned? {
        if (!address.startsWith(ROOT)) return null
        val ownerEnd = address.indexOf('/', ROOT.length)
        if (ownerEnd <= ROOT.length) return null
        val idStart = ownerEnd + 1
        val idEnd = address.indexOf('/', idStart).let { if (it < 0) address.length else it }
        if (!isId(address, idStart, idEnd)) return null
        val row = if (idEnd >= address.length) "" else address.substring(idEnd + 1)
        return Owned(address.substring(ROOT.length, ownerEnd), address.substring(idStart, idEnd), row)
    }

    /**
     * The key an address's metadata is kept under: its id replaced by `*`, since the rows
     * of one owner share their metadata (remote protocol §4) - `/godot/run/<a>/position`
     * describes `/godot/run/<b>/position`. An address that is no object's is its own key.
     */
    fun metaKey(address: String): String {
        val owned = owned(address) ?: return address
        return if (owned.row.isEmpty()) "$ROOT${owned.owner}/*" else "$ROOT${owned.owner}/*/${owned.row}"
    }

    /** What to GET to learn what an address is: its object, or the address itself. */
    fun objectOf(address: String): String = owned(address)?.objectPath ?: address
}
