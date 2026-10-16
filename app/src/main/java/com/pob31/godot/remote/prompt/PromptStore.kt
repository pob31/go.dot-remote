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

package com.pob31.godot.remote.prompt

import android.content.Context
import com.pob31.godot.remote.RemotePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the Connections tab's Script prompt section needs: a seam for the screenshot test. */
interface PromptSettings {
    /** The Choufleur page's address, normalised ([PromptAddress]); null when none is set. */
    val address: StateFlow<String?>

    /** Keeps [address] (already normalised). */
    fun save(address: String)

    fun clear()
}

/**
 * THE CHOUFLEUR ADDRESS, KEPT: one entry in the app's SharedPreferences file, read once per
 * process. Not the link service's - Go.dot knows nothing of it - so the screen holds it.
 */
class PromptStore private constructor(context: Context) : PromptSettings {
    private val prefs = context.getSharedPreferences(RemotePrefs.NAME, Context.MODE_PRIVATE)

    private val _address = MutableStateFlow(prefs.getString(KEY, null)?.let(PromptAddress::normalise))
    override val address: StateFlow<String?> = _address.asStateFlow()

    override fun save(address: String) {
        prefs.edit().putString(KEY, address).apply()
        _address.value = address
    }

    override fun clear() {
        prefs.edit().remove(KEY).apply()
        _address.value = null
    }

    companion object {
        private const val KEY = "choufleurAddress"

        @Volatile
        private var shared: PromptStore? = null

        fun shared(context: Context): PromptStore =
            shared ?: synchronized(this) {
                shared ?: PromptStore(context.applicationContext).also { shared = it }
            }
    }
}
