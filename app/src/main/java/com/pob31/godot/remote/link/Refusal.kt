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

/**
 * A write or a command the engine refused, read from `/godot/engine/lastError`:
 * `<tick> <seq> <origin> <reason> [<command>] [<detail>]` (engine Engine.cpp, record()).
 * The detail is where a `node.setMany` was refused - the rest of the line, since an
 * address may hold a space.
 */
data class Refusal(
    val tick: Long,
    val seq: Long,
    val origin: String,
    val reason: String,
    val command: String?,
    val detail: String?,
    val raw: String,
) {
    companion object {
        /** The record in [text], or null when it is not one (empty, or fewer than four fields). */
        fun parse(text: String): Refusal? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            val fields = trimmed.split(' ', limit = 6).filter { it.isNotEmpty() }
            if (fields.size < 4) return null
            val tick = fields[0].toLongOrNull() ?: return null
            val seq = fields[1].toLongOrNull() ?: return null
            return Refusal(
                tick = tick,
                seq = seq,
                origin = fields[2],
                reason = fields[3],
                command = fields.getOrNull(4),
                detail = fields.getOrNull(5),
                raw = text,
            )
        }
    }
}

/** One line of `/godot/network/remotes`: `<origin>\t<id>\t<name>` (remote protocol §5). */
data class RemoteLine(val origin: String, val id: String, val name: String) {
    companion object {
        fun parseAll(text: String): List<RemoteLine> =
            text.split('\n').mapNotNull { line ->
                val parts = line.trimEnd('\r').split('\t', limit = 3)
                if (parts.size < 2 || parts[0].isEmpty()) null
                else RemoteLine(parts[0], parts[1], parts.getOrElse(2) { "" })
            }

        /** The origin the engine knows this tablet by, from its install [id]; null until it says. */
        fun originOf(text: String, id: String): String? = parseAll(text).firstOrNull { it.id == id }?.origin
    }
}

/** Go.dot asking this tablet to show itself, for [seconds] (30 when it did not say). */
data class FindRequest(val seconds: Int) {
    companion object {
        /** When FIND says no time, or a time that is no time: the engine's own default. */
        const val DEFAULT_SECONDS = 30

        /** Five minutes at most, as `remote.find` (namespace draft §68): never an alarm without end. */
        const val MOST_SECONDS = 300

        /**
         * FIND's DATA (`"<seconds>"`, §5) as a request: whole seconds, a fraction rounded up;
         * nothing, nought, a negative or a word read as [DEFAULT_SECONDS]; more than
         * [MOST_SECONDS] held to it.
         */
        fun parse(data: String): FindRequest {
            val asked = data.trim().replace(',', '.').toDoubleOrNull()
            if (asked == null || !asked.isFinite() || asked <= 0.0) return FindRequest(DEFAULT_SECONDS)
            return FindRequest(kotlin.math.ceil(minOf(asked, MOST_SECONDS.toDouble())).toInt().coerceIn(1, MOST_SECONDS))
        }
    }
}
