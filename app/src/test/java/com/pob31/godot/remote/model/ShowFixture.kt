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

import com.pob31.godot.remote.Fixtures
import com.pob31.godot.remote.osc.OscValue
import java.io.File
import java.io.StringReader

/**
 * A REAL ENGINE'S TREE WITH RUNS IN IT: `fixtures/show-runs.json` is `GET /godot` from a
 * `wfg serve` (no device, no window) of a scratch show - a media cue with two sends, EQ, a
 * DCA and notes; a group "Storm" with a header, two media members, a timeline group and a
 * footer; a shuffled endless "Night bed"; a fade; two memos, one disabled; a second list -
 * after three GOs, a prune until the group ends, a run trim of -4.5, a DCA trim of -3 and
 * a flag. Pruned to the owners the tabs read, descriptions dropped. With no audio the
 * media runs never launch, so a test that wants a playing one says so with [put].
 */
object ShowFixture {
    const val ACT_ONE = "A1000001"
    const val FOYER = "A2000001"
    const val THUNDER_AND_RAIN = "C1000001"
    const val STORM = "G1000001"
    const val PRE_ARM = "C2000001"
    const val THUNDER = "C3000001"
    const val RAIN = "C4000001"
    const val AFTERMATH = "G2000001"
    const val DRIPS = "C5000001"
    const val SIREN = "C6000001"
    const val RELEASE = "C7000001"
    const val NIGHT_BED = "G3000001"
    const val CRICKETS = "C8000001"
    const val ROAD = "C9000001"
    const val WIND = "CA000001"
    const val RAIN_DOWN = "CB000001"
    const val INTERVAL = "CC000001"
    const val CUT = "CD000001"

    /** The runs, in the engine's creation order. */
    const val RUN_THUNDER_AND_RAIN = "2Y5TTKBT"
    const val RUN_STORM = "60N33CQZ"
    const val RUN_THUNDER = "1CGNZEJS"
    const val RUN_NIGHT_BED = "P829E8C8"
    const val RUN_CRICKETS = "Q2BDJAQM"

    fun mirror(): Mirror {
        val result = SnapshotReader.read(StringReader(Fixtures.text("fixtures/show-runs.json")))
        return Mirror().also { it.install(result.state, result.tick) }
    }

    fun tables(): SurfaceTables = assetFile("surface-tables.json").bufferedReader(Charsets.UTF_8).use { SurfaceTables.parse(it) }

    /** A file of the app's assets, from the module directory (Gradle) or the root (an IDE). */
    fun assetFile(name: String): File {
        val inModule = File("src/main/assets/$name")
        return if (inModule.exists()) inModule else File("app/src/main/assets/$name")
    }
}

fun Mirror.put(address: String, text: String) = putValues(address, "s", listOf(OscValue.Str(text)))
fun Mirror.put(address: String, value: Double) = putValues(address, "d", listOf(OscValue.Float64(value)))
fun Mirror.put(address: String, value: Boolean) = putValues(address, if (value) "T" else "F", listOf(OscValue.of(value)))
fun Mirror.put(address: String, value: Long) = putValues(address, "h", listOf(OscValue.Int64(value)))

/** Every write a screen made, in order, as text a test can compare. */
class RecordingWrites : TabWrites {
    val log = ArrayList<String>()
    override fun send(sent: Sent): Boolean { log += (listOf(sent.command) + sent.args).joinToString(" "); return true }
    override fun set(address: String, text: String) { log += "set $address $text" }
    override fun setNow(address: String, text: String): Boolean { log += "setNow $address $text"; return true }
    override fun touch(address: String): Boolean { log += "touch $address"; return true }
    override fun release(address: String): Boolean { log += "release $address"; return true }
}
