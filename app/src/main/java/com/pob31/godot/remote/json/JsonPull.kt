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

package com.pob31.godot.remote.json

import java.io.Closeable
import java.io.IOException
import java.io.Reader

class JsonException(message: String) : IOException(message)

/**
 * A pull reader over JSON text, one token at a time from a [Reader], never a tree.
 *
 * WHY NOT android.util.JsonReader: Go.dot's whole tree is 8.6 MB at 500 cues
 * (remote protocol §2) and must be read as it arrives; android.util.JsonReader does that
 * on a tablet but is a stub in the JVM unit tests, which would leave the snapshot path
 * tested nowhere. This one is plain Kotlin, so the code the tablet runs is the code the
 * tests run.
 *
 * Its shape is android.util.JsonReader's - [peek], [beginObject], [hasNext], [nextName],
 * [skipValue] - with one simplification: commas and colons are read as separators
 * wherever they stand, so it does not check them. The text it reads is Go.dot's own.
 * Anything else malformed (an unknown character, an unterminated string, the end of
 * input inside a value) is a [JsonException].
 */
class JsonPull(private val input: Reader) : Closeable {

    enum class Token { BEGIN_OBJECT, END_OBJECT, BEGIN_ARRAY, END_ARRAY, STRING, NUMBER, BOOLEAN, NULL, END_DOCUMENT }

    private val buffer = CharArray(16 * 1024)
    private var pos = 0
    private var limit = 0
    private val text = StringBuilder()

    private fun fill(): Boolean {
        if (pos < limit) return true
        val read = input.read(buffer, 0, buffer.size)
        pos = 0
        limit = if (read > 0) read else 0
        return limit > 0
    }

    /** The next significant character, not consumed; -1 at the end of the input. */
    private fun peekChar(): Int {
        while (fill()) {
            val c = buffer[pos]
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == ',' || c == ':') {
                pos++
                continue
            }
            return c.code
        }
        return -1
    }

    private fun take(expected: Char) {
        val c = peekChar()
        if (c != expected.code) throw JsonException("expected '$expected', found ${describe(c)}")
        pos++
    }

    private fun describe(c: Int) = if (c < 0) "the end of the input" else "'${c.toChar()}'"

    fun peek(): Token = when (val c = peekChar()) {
        -1 -> Token.END_DOCUMENT
        '{'.code -> Token.BEGIN_OBJECT
        '}'.code -> Token.END_OBJECT
        '['.code -> Token.BEGIN_ARRAY
        ']'.code -> Token.END_ARRAY
        '"'.code -> Token.STRING
        't'.code, 'f'.code -> Token.BOOLEAN
        'n'.code -> Token.NULL
        else -> if (c == '-'.code || (c >= '0'.code && c <= '9'.code)) Token.NUMBER
                else throw JsonException("unexpected ${describe(c)}")
    }

    fun beginObject() = take('{')
    fun endObject() = take('}')
    fun beginArray() = take('[')
    fun endArray() = take(']')

    /** Whether the object or array being read has another member. */
    fun hasNext(): Boolean {
        val c = peekChar()
        if (c == -1) throw JsonException("unexpected end of the input")
        return c != '}'.code && c != ']'.code
    }

    fun nextName(): String = nextString()

    fun nextString(): String {
        take('"')
        text.setLength(0)
        while (true) {
            if (!fill()) throw JsonException("unterminated string")
            // The common case: a run with no escape, appended in one piece.
            var end = pos
            while (end < limit && buffer[end] != '"' && buffer[end] != '\\') end++
            text.appendRange(buffer, pos, end)
            pos = end
            if (pos == limit) continue
            if (buffer[pos] == '"') {
                pos++
                return text.toString()
            }
            pos++ // the backslash
            text.append(readEscape())
        }
    }

    private fun nextChar(): Char {
        if (!fill()) throw JsonException("unterminated string")
        return buffer[pos++]
    }

    private fun readEscape(): Char = when (val c = nextChar()) {
        '"' -> '"'
        '\\' -> '\\'
        '/' -> '/'
        'b' -> '\b'
        'f' -> '\u000C'
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'u' -> {
            var code = 0
            repeat(4) {
                val h = nextChar()
                val digit = Character.digit(h, 16)
                if (digit < 0) throw JsonException("bad \\u escape")
                code = code * 16 + digit
            }
            code.toChar()
        }
        else -> throw JsonException("bad escape '\\$c'")
    }

    /** A number as the text it was written in: the caller knows whether it wants a Long or a Double. */
    fun nextNumber(): String {
        if (peek() != Token.NUMBER) throw JsonException("expected a number")
        text.setLength(0)
        while (fill()) {
            val c = buffer[pos]
            if ((c in '0'..'9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                text.append(c)
                pos++
            } else {
                break
            }
        }
        return text.toString()
    }

    fun nextBoolean(): Boolean = when (val word = nextWord()) {
        "true" -> true
        "false" -> false
        else -> throw JsonException("expected a boolean, found '$word'")
    }

    fun nextNull() {
        val word = nextWord()
        if (word != "null") throw JsonException("expected null, found '$word'")
    }

    private fun nextWord(): String {
        peekChar()
        text.setLength(0)
        while (fill()) {
            val c = buffer[pos]
            if (c in 'a'..'z') {
                text.append(c)
                pos++
            } else {
                break
            }
        }
        return text.toString()
    }

    /** Past the next value, whatever it is, without keeping any of it. */
    fun skipValue() {
        when (peek()) {
            Token.BEGIN_OBJECT, Token.BEGIN_ARRAY -> skipNested()
            Token.STRING -> skipString()
            Token.NUMBER -> nextNumber()
            Token.BOOLEAN -> nextBoolean()
            Token.NULL -> nextNull()
            Token.END_OBJECT, Token.END_ARRAY, Token.END_DOCUMENT -> throw JsonException("no value to skip")
        }
    }

    private fun skipString() {
        take('"')
        while (true) {
            val c = nextChar()
            if (c == '"') return
            if (c == '\\') readEscape()
        }
    }

    private fun skipNested() {
        var depth = 0
        do {
            when (val c = peekChar()) {
                '{'.code, '['.code -> { pos++; depth++ }
                '}'.code, ']'.code -> { pos++; depth-- }
                '"'.code -> skipString()
                -1 -> throw JsonException("unexpected end of the input")
                else -> {
                    // A number or a literal, a character at a time.
                    val ch = c.toChar()
                    if (!(ch.isLetterOrDigit() || ch == '-' || ch == '+' || ch == '.'))
                        throw JsonException("unexpected '$ch'")
                    pos++
                }
            }
        } while (depth > 0)
    }

    override fun close() = input.close()
}

/** A string as a JSON literal, quotes included: for the text frames the app sends. */
fun jsonQuote(value: String): String {
    val out = StringBuilder(value.length + 2)
    out.append('"')
    for (c in value) {
        when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            else -> if (c < ' ') out.append(String.format(java.util.Locale.ROOT, "\\u%04x", c.code)) else out.append(c)
        }
    }
    out.append('"')
    return out.toString()
}
