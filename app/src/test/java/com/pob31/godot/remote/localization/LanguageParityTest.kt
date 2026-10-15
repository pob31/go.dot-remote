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

import com.pob31.godot.remote.json.JsonPull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Both languages in step: every key English has, French has, and nothing more. */
class LanguageParityTest {

    private fun langFile(code: String): File {
        // Gradle runs unit tests from the module's directory; an IDE may run them from the root.
        val inModule = File("src/main/assets/lang/$code.json")
        return if (inModule.exists()) inModule else File("app/src/main/assets/lang/$code.json")
    }

    private fun keysOf(code: String): Map<String, String> {
        val keys = linkedMapOf<String, String>()
        JsonPull(langFile(code).bufferedReader(Charsets.UTF_8)).use { json ->
            fun walk(prefix: String) {
                when (json.peek()) {
                    JsonPull.Token.BEGIN_OBJECT -> {
                        json.beginObject()
                        while (json.hasNext()) {
                            val name = json.nextName()
                            walk(if (prefix.isEmpty()) name else "$prefix.$name")
                        }
                        json.endObject()
                    }
                    JsonPull.Token.STRING -> keys[prefix] = json.nextString()
                    else -> throw AssertionError("$code.json: $prefix is neither a word nor a group")
                }
            }
            walk("")
        }
        return keys
    }

    @Test
    fun englishAndFrenchHaveTheSameKeys() {
        val en = keysOf("en")
        val fr = keysOf("fr")
        assertTrue("en.json has words", en.size > 2)
        assertEquals("keys only in en.json", emptySet<String>(), en.keys - fr.keys)
        assertEquals("keys only in fr.json", emptySet<String>(), fr.keys - en.keys)
    }

    @Test
    fun noWordIsEmpty() {
        for (code in listOf("en", "fr")) {
            for ((key, word) in keysOf(code)) {
                assertTrue("$code.json: $key is empty", word.isNotBlank())
            }
        }
    }

    @Test
    fun placeholdersMatchAcrossLanguages() {
        val en = keysOf("en")
        val fr = keysOf("fr")
        val placeholder = Regex("\\{[a-zA-Z]+\\}")
        for ((key, word) in en) {
            val inEnglish = placeholder.findAll(word).map { it.value }.toSet()
            val inFrench = placeholder.findAll(fr[key] ?: "").map { it.value }.toSet()
            assertEquals("placeholders of $key", inEnglish, inFrench)
        }
    }
}
