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

import com.pob31.godot.remote.osc.OscValue

/** One argument's bounds: a MIN and a MAX, or a closed set of words (OSCQuery's RANGE entry). */
data class ArgRange(val min: Double? = null, val max: Double? = null, val vals: List<String>? = null)

/**
 * What a node is, as opposed to what it holds: OSCQuery's ACCESS, RANGE, UNIT and
 * DESCRIPTION. Shared by every row of one owner with the same name, and kept once per
 * [Addresses.metaKey], so 500 cues carry one copy of each description.
 */
data class NodeMeta(
    val access: Int,
    val range: List<ArgRange>,
    val unit: String?,
    val description: String?,
) {
    companion object {
        /** Before HTTP has said: nothing known, nothing promised. */
        val UNKNOWN = NodeMeta(ACCESS_UNKNOWN, emptyList(), null, null)
    }
}

/** ACCESS as OSCQuery spells it, plus "not known yet". */
const val ACCESS_UNKNOWN = -1
const val ACCESS_NONE = 0
const val ACCESS_READ = 1
const val ACCESS_WRITE = 2
const val ACCESS_READ_WRITE = 3

/**
 * One node of the engine's tree as the mirror holds it. Immutable: a value that changes
 * replaces the node whole, so a reader on the UI thread never sees half an update.
 * [typeTags] is the tag string of the values held now (a list row's grows with its value).
 */
data class Node(
    val address: String,
    val typeTags: String,
    val access: Int,
    val values: List<OscValue>,
    val range: List<ArgRange>,
    val unit: String?,
    val description: String?,
) {
    val value: OscValue? get() = values.firstOrNull()

    /** The first value as text, or null when the node holds none (a container, an event). */
    val text: String? get() = values.firstOrNull()?.text()

    val writable: Boolean get() = access == ACCESS_WRITE || access == ACCESS_READ_WRITE

    fun withMeta(meta: NodeMeta) = copy(access = meta.access, range = meta.range, unit = meta.unit, description = meta.description)

    companion object {
        fun of(address: String, typeTags: String, values: List<OscValue>, meta: NodeMeta) =
            Node(address, typeTags, meta.access, values, meta.range, meta.unit, meta.description)
    }
}
