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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.Reader
import java.io.StringReader

class JsonPullTest {

    @Test
    fun readsEveryKindOfValue() {
        val json = JsonPull(StringReader("""{"s": "a\"b\\c\/d\n\té", "n": -1.5e3, "i": 42, "t": true, "f": false, "z": null, "a": [1, [2], {}]}"""))
        json.beginObject()
        assertEquals("s", json.nextName()); assertEquals("a\"b\\c/d\n\t\u00e9", json.nextString())
        assertEquals("n", json.nextName()); assertEquals("-1.5e3", json.nextNumber())
        assertEquals("i", json.nextName()); assertEquals("42", json.nextNumber())
        assertEquals("t", json.nextName()); assertTrue(json.nextBoolean())
        assertEquals("f", json.nextName()); assertFalse(json.nextBoolean())
        assertEquals("z", json.nextName()); assertEquals(JsonPull.Token.NULL, json.peek()); json.nextNull()
        assertEquals("a", json.nextName())
        json.beginArray()
        assertEquals("1", json.nextNumber())
        json.skipValue()
        json.skipValue()
        assertFalse(json.hasNext())
        json.endArray()
        assertFalse(json.hasNext())
        json.endObject()
        assertEquals(JsonPull.Token.END_DOCUMENT, json.peek())
    }

    @Test
    fun skipsNestedValuesWithBracketsInsideStrings() {
        val json = JsonPull(StringReader("""{"skip": {"x": ["]", "}", "\"{"], "y": {"z": [[]]}}, "keep": "yes"}"""))
        json.beginObject()
        assertEquals("skip", json.nextName())
        json.skipValue()
        assertEquals("keep", json.nextName())
        assertEquals("yes", json.nextString())
        json.endObject()
    }

    /** A reader that hands out one character at a time, so every token crosses a buffer edge. */
    private class Trickle(private val text: String) : Reader() {
        private var at = 0
        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            if (at >= text.length) return -1
            cbuf[off] = text[at++]
            return 1
        }
        override fun close() {}
    }

    @Test
    fun readsAcrossBufferEdges() {
        val long = "x".repeat(40_000) + "\\u00e9" + "y".repeat(20_000)
        val json = JsonPull(Trickle("""["$long", 123.25, {"k": "\ud83d\ude00"}]"""))
        json.beginArray()
        assertEquals("x".repeat(40_000) + "\u00e9" + "y".repeat(20_000), json.nextString())
        assertEquals("123.25", json.nextNumber())
        json.beginObject()
        assertEquals("k", json.nextName())
        assertEquals("\ud83d\ude00", json.nextString())
        json.endObject()
        json.endArray()
    }

    @Test
    fun malformedInputIsAnException() {
        for (bad in listOf("{\"a\": \"unterminated", "[1, 2", "{\"a\": @}", "[\"\\q\"]")) {
            try {
                val json = JsonPull(StringReader(bad))
                json.skipValue()
                fail("accepted $bad")
            } catch (expected: JsonException) {
                // as it should
            }
        }
    }

    @Test
    fun quotingRoundTrips() {
        val original = "id\tname \"quoted\" \\ \n end"
        val json = JsonPull(StringReader(jsonQuote(original)))
        assertEquals(original, json.nextString())
        assertEquals("\"a\\tb\"", jsonQuote("a\tb"))
    }
}
