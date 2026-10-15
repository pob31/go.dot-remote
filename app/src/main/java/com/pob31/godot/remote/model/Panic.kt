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

/**
 * PANIC, AND PANIC AGAIN: which of PRD §4.4's two levels a press means - Go.dot's
 * client/model/Panic.h, ported. One press is the graceful abort (`run.stopAll`), a
 * second within [DOUBLE_PRESS_MS] the immediate one (`run.killAll`). Only a client can
 * tell them apart - "twice" is a fact about a hand, not about the show - so the reading
 * is made here and each reading is ONE named command: the engine's log records which
 * level was reached.
 *
 * A press is measured from the press before it, as the desktop's: three presses in a
 * second are a stop, a kill, and a kill again - what a hand hammering the button means,
 * applied harmlessly to a show already stopping. A press long after the last is a first
 * press again. Nothing here reads a clock; the caller says what time it is.
 */
class Panic {
    private var armed = false
    private var lastMs = 0L

    /** Records a press at [nowMs]; true when it is the SECOND level. */
    fun press(nowMs: Long): Boolean {
        val isDouble = nextIsDouble(nowMs)
        armed = true
        lastMs = nowMs
        return isDouble
    }

    /** What a press at [nowMs] would be, for the word under the button: true is KILL ALL. */
    fun nextIsDouble(nowMs: Long): Boolean = armed && nowMs - lastMs <= DOUBLE_PRESS_MS && nowMs >= lastMs

    /** When a press stops being the second level - the word goes back - or null before any press. */
    fun armedUntil(): Long? = if (armed) lastMs + DOUBLE_PRESS_MS else null

    companion object {
        /** §4.4's "double Esc", the desktop's number, and a surface's STOP's (`surface::doubleStopTicks`). */
        const val DOUBLE_PRESS_MS = 750L

        fun command(isDouble: Boolean): String = if (isDouble) "run.killAll" else "run.stopAll"
    }
}
