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

import com.pob31.godot.remote.link.WireText

/*  WHAT A NUMBER BOX SHOWS, AND WHAT IT SENDS BACK - the page's `shownValue` and
    `commitText` (clients/console/views/values.js), a pair that must agree:

    - A BLANK-NOUGHT BOX shows nought as nothing (a time column: a hundred rows of "0"
      would be the loudest thing in the list), so clearing it is the one gesture it
      teaches, and clearing it sends "0" - never "", which the engine refuses as
      `type-mismatch`.
    - ANY OTHER BOX cleared sends nothing: an empty box is not a value anybody typed, and
      the box goes back to what the engine holds.
    - A box holding something that is not a number sends nothing either, and says so.
    - A number is read with a comma or a point (TypedNumber: a French keyboard types a
      comma) and written with a point (WireText, Locale.ROOT), whatever the language. */
object FieldText {

    /** A number as its box shows it: at most [decimals] places, no trailing zeros; blank for nought in a blank-nought box. */
    fun shown(value: Double?, decimals: Int, blankZero: Boolean = false): String {
        if (value == null || !value.isFinite()) return ""
        val text = WireText.number(value, decimals)
        return if (blankZero && text == "0") "" else text
    }

    sealed class Commit {
        /** Send this text as the node's value. */
        data class Send(val text: String) : Commit()
        /** Send nothing; the box shows the engine's value again. */
        data object Revert : Commit()
        /** Send nothing, and say the box did not hold a number. */
        data object NotANumber : Commit()
    }

    /** What a box committed with [typed] sends (the page's `commitText`). */
    fun commit(typed: String, decimals: Int, blankZero: Boolean = false): Commit {
        if (typed.isBlank()) return if (blankZero) Commit.Send("0") else Commit.Revert
        val number = TypedNumber.parse(typed) ?: return Commit.NotANumber
        return Commit.Send(WireText.number(number, decimals))
    }

    /** A level as the fader's box writes it: "-6.5", and the word for silence at the bottom (Fader.cpp `faderText`). */
    fun levelText(decibels: Double, silenceDb: Double = -120.0): String =
        if (decibels <= silenceDb) "-inf" else EqLaws.fixed(decibels, 1)
}

/**
 * A VALUE SENT AND NOT YET HEARD BACK (values.js `refreshFields`): a write takes a tick or
 * two to come back in the stream, and the push in flight when it was made still says the
 * old value - without this a box or a fader would flick back for a frame, which reads
 * exactly like an edit that did not take. The sent value is shown until the engine
 * agrees, or for [holdMs] at most: a refusal never changes the tree, so after that the
 * engine's value is the honest answer, and the header's refusal line says why.
 */
class SentValue(private val holdMs: Long = 1500) {
    private var sent: String? = null
    private var sentAt = 0L

    fun sent(text: String, nowMs: Long) {
        sent = text
        sentAt = nowMs
    }

    /** What to show: the sent value while it waits, else [engine]. */
    fun shown(engine: String, nowMs: Long, agrees: (String, String) -> Boolean = { a, b -> a == b }): String {
        val waiting = sent ?: return engine
        if (agrees(waiting, engine) || nowMs - sentAt > holdMs) {
            sent = null
            return engine
        }
        return waiting
    }

    val waiting: Boolean get() = sent != null
}
