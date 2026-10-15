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

package com.pob31.godot.remote

/** The test resources, and a wait that does not sleep longer than it must. */
object Fixtures {
    /**
     * A small snapshot written by hand in the shape engine OscQueryJson.cpp writes (remote
     * protocol §4): a list in focus with a cue in standby, two cues, the engine's tick (100)
     * and lastError, the remotes row, a video output with a list-valued mesh.
     */
    fun snapshotSmall(): String = text("fixtures/snapshot-small.json")

    fun text(path: String): String =
        (Fixtures::class.java.classLoader!!.getResourceAsStream(path) ?: error("no resource $path"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    const val CUE_A = "F7HR8TVD"
    const val CUE_B = "K2M4P6R8"
    const val LIST = "D9FH2JKA"
    const val SNAPSHOT_TICK = 100L
}

/** Waits up to [timeoutMs] for [condition]; fails the test with [what] if it never holds. */
fun await(what: String, timeoutMs: Long = 5000, condition: () -> Boolean) {
    val until = System.nanoTime() + timeoutMs * 1_000_000
    while (System.nanoTime() < until) {
        if (condition()) return
        Thread.sleep(5)
    }
    if (!condition()) throw AssertionError("timed out waiting for: $what")
}
