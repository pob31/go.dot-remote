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

import android.content.Context
import android.provider.Settings
import com.pob31.godot.remote.RemotePrefs
import java.util.UUID

/** A Go.dot machine the tablet remembers: typed by the operator, never discovered (§9, ANN). */
data class Machine(
    val name: String,
    val host: String,
    val port: Int = DEFAULT_PORT,
    /** Whether the operator left it linked: it is linked again when the service starts. */
    val linked: Boolean = false,
) {
    val key: String get() = "$host:$port"

    /** What the operator reads: the name, or the address when it has none. */
    val label: String get() = name.ifBlank { host }

    companion object {
        /** Go.dot's HTTP and WebSocket port (`wfg serve`'s default). */
        const val DEFAULT_PORT = 5010
    }
}

/**
 * What the app keeps between launches, in its one SharedPreferences file - READ ONCE, when
 * the service starts, and written when it changes: never read per send.
 */
class MachineStore(context: Context) {
    private val prefs = context.getSharedPreferences(RemotePrefs.NAME, Context.MODE_PRIVATE)

    /** Drawn once at install and kept, so a tablet is the same tablet across reconnects (HELLO, §5). */
    val installId: String = prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_INSTALL_ID, it).apply()
    }

    /** What the operator calls this tablet; the device's own name until they say otherwise. */
    var tabletName: String = prefs.getString(KEY_TABLET_NAME, null)
        ?: (Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?: android.os.Build.MODEL ?: "tablet")
        set(value) {
            field = value
            prefs.edit().putString(KEY_TABLET_NAME, value).apply()
        }

    var machines: List<Machine> = decode(prefs.getString(KEY_MACHINES, null) ?: "")
        set(value) {
            field = value
            prefs.edit().putString(KEY_MACHINES, encode(value)).apply()
        }

    /** The machine on screen, by [Machine.key]. */
    var onScreen: String? = prefs.getString(KEY_ON_SCREEN, null)
        set(value) {
            field = value
            prefs.edit().putString(KEY_ON_SCREEN, value).apply()
        }

    companion object {
        private const val KEY_INSTALL_ID = "installId"
        private const val KEY_TABLET_NAME = "tabletName"
        private const val KEY_MACHINES = "machines"
        private const val KEY_ON_SCREEN = "onScreen"

        private fun clean(text: String) = text.replace('\t', ' ').replace('\n', ' ')

        /** One machine a line: name, host, port, linked, by tabs. */
        internal fun encode(machines: List<Machine>): String =
            machines.joinToString("\n") { "${clean(it.name)}\t${clean(it.host)}\t${it.port}\t${it.linked}" }

        internal fun decode(text: String): List<Machine> =
            text.split('\n').mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 3 || parts[1].isBlank()) null
                else Machine(parts[0], parts[1], parts[2].toIntOrNull() ?: Machine.DEFAULT_PORT, parts.getOrNull(3) == "true")
            }
    }
}

/**
 * The remembered machines as the Machines tab edits them: pure, so a test reads the
 * rules. A machine is known by its address and port ([Machine.key]); two entries never
 * share one.
 */
object MachineList {

    /**
     * [machine] put in the list: in the place of the entry keyed [replacing] (an edit, which
     * may change the address), else in the place of the entry with its own key, else at the
     * end. Null when the edit would give it the address of ANOTHER entry.
     */
    fun put(list: List<Machine>, machine: Machine, replacing: String? = null): List<Machine>? {
        val at = replacing ?: machine.key
        if (list.any { it.key == machine.key && it.key != at }) return null
        val index = list.indexOfFirst { it.key == at }
        return if (index < 0) list + machine else list.toMutableList().also { it[index] = machine }
    }

    fun remove(list: List<Machine>, key: String): List<Machine> = list.filter { it.key != key }

    fun setLinked(list: List<Machine>, key: String, linked: Boolean): List<Machine> =
        list.map { if (it.key == key) it.copy(linked = linked) else it }
}

/**
 * WHICH MACHINE IS ON SCREEN (A.3). The one the operator put there ([chosen]) while it is
 * linked. Otherwise a default: the one on screen now ([current]) while it is linked and
 * sends to the devices; else the first linked one that sends - a main leading, a machine
 * alone, one not paired - so a main and its backup open on the one the show is coming
 * from; else the first linked one. [sends] answers null while a machine's link has not
 * read its pair rows yet. Null when nothing is linked.
 */
fun chooseOnScreen(chosen: String?, current: String?, linked: List<String>, sends: (String) -> Boolean?): String? {
    if (chosen != null && chosen in linked) return chosen
    if (current != null && current in linked && sends(current) == true) return current
    return linked.firstOrNull { sends(it) == true } ?: linked.firstOrNull()
}
