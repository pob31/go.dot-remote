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

package com.pob31.godot.remote.mapping

import com.pob31.godot.remote.link.Link
import com.pob31.godot.remote.link.WireText

/**
 * WHAT THE MAPPING TAB SENDS, and nothing else: values, a drag's brackets, a regrid as one
 * `node.setMany`, and Identify. No `zone.create`, no `object.delete` - the tablet changes
 * no structure (§68.2, ANO). A seam, so a test reads the writes a gesture makes.
 */
interface MappingWrites {
    /** A drag begins: the document folds its run of writes into one undo step. */
    fun touch(address: String): Boolean

    /** A value during a drag, throttled per address, the latest kept. */
    fun set(address: String, text: String)

    /** The drag ends: its last value lands, then `node.release`. */
    fun release(address: String): Boolean

    /** A value now: a nudge, a typed number, a blend. */
    fun setNow(address: String, text: String): Boolean

    /** Several values as one command, one undo step: a regrid. */
    fun setMany(writes: List<Pair<String, String>>): Boolean

    /** `videoOutput.identify <output> <on>`: the test pattern on that projector (machine-local). */
    fun identify(output: String, on: Boolean): Boolean
}

/** The writes, to the machine on screen. */
class LinkMappingWrites(private val link: Link) : MappingWrites {
    override fun touch(address: String) = link.touch(address)
    override fun set(address: String, text: String) = link.set(address, text)
    override fun release(address: String) = link.release(address)
    override fun setNow(address: String, text: String) = link.setNow(address, text)
    override fun setMany(writes: List<Pair<String, String>>): Boolean =
        link.command("node.setMany", *writes.flatMap { listOf(it.first, it.second) }.toTypedArray())
    override fun identify(output: String, on: Boolean) = link.command("videoOutput.identify", output, WireText.bool(on))
}

/**
 * ONE POINT DRAGGED, as writes: `node.touch` on the mesh row at the first move - a tap
 * writes nothing - then the whole mesh string at each move, throttled, and `node.release`
 * when the finger lifts. [warp] is what the screen draws meanwhile.
 */
class WarpDragSession(
    val base: String,
    start: Warp,
    val index: Int,
    private val drag: PointDrag,
    private val writes: MappingWrites,
) {
    var warp: Warp = start
        private set

    var touched: Boolean = false
        private set

    val meshAddress: String get() = base + "mesh"

    /** The finger at ([dx], [dy]) pixels from touch-down; true when the warp moved. */
    fun update(dx: Double, dy: Double): Boolean {
        if (!drag.update(dx, dy)) return false
        if (!touched) {
            writes.touch(meshAddress)
            touched = true
        }
        warp = warp.moved(index, drag.x, drag.y)
        writes.set(meshAddress, warp.text())
        return true
    }

    fun end() {
        if (touched) writes.release(meshAddress)
    }
}

/**
 * WHAT THE SCREEN DRAWS WHILE THE ENGINE CATCHES UP. A warp being dragged is the
 * finger's, whatever the stream says (the contract's "the app ignores pushes for a control
 * under the operator's finger"); once let go, or after a nudge or a regrid, it stays the
 * tablet's until the mirror holds the same text or [graceMs] pass - so the point does not
 * flick back for the tick the write takes to come round.
 */
class HeldWarps(private val graceMs: Long = 1500) {
    private data class Held(val warp: Warp, val text: String, val dragging: Boolean, val atMs: Long)

    private val held = HashMap<String, Held>()

    fun hold(base: String, warp: Warp, dragging: Boolean, nowMs: Long) {
        held[base] = Held(warp, warp.text(), dragging, nowMs)
    }

    fun letGo(base: String, nowMs: Long) {
        held[base]?.let { held[base] = it.copy(dragging = false, atMs = nowMs) }
    }

    /** The warp to draw for [base], whose mirror holds [fromMirror]. */
    fun shown(base: String, fromMirror: Warp, nowMs: Long): Warp {
        val mine = held[base] ?: return fromMirror
        if (mine.dragging) return mine.warp
        val caughtUp = fromMirror.text() == mine.text && fromMirror.columns == mine.warp.columns
        if (caughtUp || nowMs - mine.atMs > graceMs) {
            held.remove(base)
            return fromMirror
        }
        return mine.warp
    }

    fun isEmpty(): Boolean = held.isEmpty()
}
