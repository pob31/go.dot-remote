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

import android.content.Context
import com.pob31.godot.remote.RemotePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.Locale

internal const val KEY_LANGUAGE = "language"

object LocalizationManager {
    private var translations: JSONObject = JSONObject()
    private var fallback: JSONObject = JSONObject()
    private val _currentLanguage = MutableStateFlow("en")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()
    var availableLanguages: List<Pair<String, String>> = listOf("en" to "English")
        private set

    @Volatile private var initialised = false

    /** Once per process (RemoteApplication); a second call changes nothing. */
    fun init(context: Context) {
        if (initialised) return
        initialised = true
        discoverLanguages(context)
        // The saved choice, else the tablet's language, else English.
        val saved = context.getSharedPreferences(RemotePrefs.NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        val target = saved ?: Locale.getDefault().language
        fallback = loadJson(context, "en")
        val available = availableLanguages.any { it.first == target }
        loadLanguage(context, if (available) target else "en", remember = saved != null)
    }

    fun loadLanguage(context: Context, code: String, remember: Boolean = true) {
        translations = loadJson(context, code)
        _currentLanguage.value = code
        if (remember) {
            context.getSharedPreferences(RemotePrefs.NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANGUAGE, code).apply()
        }
    }

    fun get(key: String): String =
        resolve(translations, key)
            ?: resolve(fallback, key)
            ?: key

    fun get(key: String, vararg params: Pair<String, String>): String {
        var result = get(key)
        for ((name, value) in params) result = result.replace("{$name}", value)
        return result
    }

    private fun resolve(json: JSONObject, key: String): String? {
        var current: Any = json
        for (part in key.split(".")) {
            current = when (current) {
                is JSONObject -> current.opt(part) ?: return null
                else -> return null
            }
        }
        return current as? String ?: current.toString()
    }

    private fun loadJson(context: Context, code: String): JSONObject =
        try {
            context.assets.open("lang/$code.json").bufferedReader().use { JSONObject(it.readText()) }
        } catch (e: Exception) {
            JSONObject()
        }

    private fun discoverLanguages(context: Context) {
        val languages = mutableListOf<Pair<String, String>>()
        try {
            for (file in (context.assets.list("lang") ?: emptyArray()).sorted()) {
                if (file.endsWith(".json")) {
                    val code = file.removeSuffix(".json")
                    val name = loadJson(context, code).optJSONObject("meta")?.optString("language", code) ?: code
                    languages.add(code to name)
                }
            }
        } catch (e: Exception) {
            // English alone, below.
        }
        if (languages.isEmpty()) languages.add("en" to "English")
        availableLanguages = languages
    }
}
