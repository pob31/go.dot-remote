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

import com.pob31.godot.remote.json.JsonException
import com.pob31.godot.remote.json.JsonPull
import com.pob31.godot.remote.osc.OscValue
import java.io.Reader

/**
 * Go.dot's OSCQuery JSON (`GET /godot`, engine OscQueryJson.cpp) read into a [MirrorState],
 * a token at a time: the whole tree is 8.6 MB at 500 cues and never becomes a DOM.
 *
 * Each node is an object with FULL_PATH, then where they apply DESCRIPTION, TYPE, ACCESS,
 * VALUE (an array), RANGE (an array of `{MIN, MAX}` or `{VALS}`), UNIT (an array), GODOT,
 * and CONTENTS (the children by their last segment). VALUE's JSON scalars are typed by
 * TYPE, a tag per value; JSON null is a value the engine cannot say (a blob, a time tag)
 * and reads as nil.
 *
 * ONE DESCRIPTION PER ROW, NOT PER NODE: once a row's metadata is known (from the first
 * cue that has it), the same row of every other cue skips its DESCRIPTION, RANGE and UNIT
 * unread - most of the 8.6 MB.
 */
object SnapshotReader {

    class Result(val state: MirrorState, val tick: Long)

    /** Reads the tree at [input] whole. Throws [JsonException] (an IOException) on malformed text. */
    fun read(input: Reader): Result {
        val state = MirrorState()
        JsonPull(input).use { json -> readNode(json, state) }
        val tick = state.nodes[Addresses.TICK]?.value?.asLong() ?: -1L
        return Result(state, tick)
    }

    private sealed class Raw {
        class Str(val text: String) : Raw()
        class Num(val text: String) : Raw()
        class Bool(val value: Boolean) : Raw()
        data object Null : Raw()
    }

    private fun readNode(json: JsonPull, state: MirrorState) {
        json.beginObject()
        var address: String? = null
        var known: NodeMeta? = null
        var type = ""
        var access = ACCESS_UNKNOWN
        var raw: List<Raw>? = null
        var range: List<ArgRange>? = null
        var unit: String? = null
        var description: String? = null

        while (json.hasNext()) {
            when (json.nextName()) {
                "FULL_PATH" -> {
                    val path = json.nextString()
                    address = path
                    known = state.meta[Addresses.metaKey(path)]
                }
                "TYPE" -> type = json.nextString()
                "ACCESS" -> access = json.nextNumber().toDoubleOrNull()?.toInt() ?: ACCESS_UNKNOWN
                "VALUE" -> raw = readScalars(json)
                "RANGE" -> if (known != null) json.skipValue() else range = readRange(json)
                "UNIT" -> if (known != null) json.skipValue() else unit = readScalars(json).firstOrNull()?.let(::textOf)
                "DESCRIPTION" -> if (known != null) json.skipValue() else description = json.nextString()
                "CONTENTS" -> {
                    json.beginObject()
                    while (json.hasNext()) {
                        json.nextName()
                        readNode(json, state)
                    }
                    json.endObject()
                }
                else -> json.skipValue() // GODOT, and whatever a later engine adds
            }
        }
        json.endObject()

        val path = address ?: throw JsonException("a node without FULL_PATH")
        val meta = known ?: NodeMeta(access, range ?: emptyList(), unit, description).also {
            state.meta[Addresses.metaKey(path)] = it
        }
        val values = raw?.let { typed(it, type) } ?: emptyList()
        state.put(Node(path, if (values.isEmpty()) "" else type, access, values, meta.range, meta.unit, meta.description))
    }

    private fun readScalars(json: JsonPull): List<Raw> {
        val out = ArrayList<Raw>(1)
        json.beginArray()
        while (json.hasNext()) out.add(readScalar(json))
        json.endArray()
        return out
    }

    private fun readScalar(json: JsonPull): Raw = when (json.peek()) {
        JsonPull.Token.STRING -> Raw.Str(json.nextString())
        JsonPull.Token.NUMBER -> Raw.Num(json.nextNumber())
        JsonPull.Token.BOOLEAN -> Raw.Bool(json.nextBoolean())
        JsonPull.Token.NULL -> { json.nextNull(); Raw.Null }
        else -> { json.skipValue(); Raw.Null }
    }

    private fun textOf(raw: Raw): String = when (raw) {
        is Raw.Str -> raw.text
        is Raw.Num -> raw.text
        is Raw.Bool -> raw.value.toString()
        Raw.Null -> ""
    }

    private fun readRange(json: JsonPull): List<ArgRange> {
        val out = ArrayList<ArgRange>(1)
        json.beginArray()
        while (json.hasNext()) {
            var min: Double? = null
            var max: Double? = null
            var vals: List<String>? = null
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "MIN" -> min = (readScalar(json) as? Raw.Num)?.text?.toDoubleOrNull()
                    "MAX" -> max = (readScalar(json) as? Raw.Num)?.text?.toDoubleOrNull()
                    "VALS" -> vals = readScalars(json).map(::textOf)
                    else -> json.skipValue()
                }
            }
            json.endObject()
            out.add(ArgRange(min, max, vals))
        }
        json.endArray()
        return out
    }

    private fun typed(raw: List<Raw>, type: String): List<OscValue> =
        raw.mapIndexed { i, one -> typedOne(one, type.getOrNull(i)) }

    /** One JSON scalar as the OSC value its tag says. */
    private fun typedOne(raw: Raw, tag: Char?): OscValue = when (raw) {
        Raw.Null -> OscValue.Nil
        is Raw.Bool -> OscValue.of(raw.value)
        is Raw.Str -> OscValue.Str(raw.text)
        is Raw.Num -> number(raw.text, tag)
    }

    private fun number(text: String, tag: Char?): OscValue = try {
        when (tag) {
            'i' -> OscValue.Int32(text.toLongOrNull()?.toInt() ?: text.toDouble().toInt())
            'h' -> OscValue.Int64(text.toLongOrNull() ?: text.toDouble().toLong())
            'f' -> OscValue.Float32(text.toFloat())
            'd' -> OscValue.Float64(text.toDouble())
            's' -> OscValue.Str(text)
            'T', 'F' -> OscValue.of(text.toDouble() != 0.0)
            else -> text.toLongOrNull()?.let { OscValue.Int64(it) } ?: OscValue.Float64(text.toDouble())
        }
    } catch (bad: NumberFormatException) {
        OscValue.Nil
    }
}
