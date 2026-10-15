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

/** The three pair commands a tablet offers, each sent to the machine whose button it is (§8). */
enum class PairCommand(val command: String, val key: String) {
    TAKE_OVER("pair.takeOver", "remote.pair.takeOver"),
    HAND_OVER("pair.handOver", "remote.pair.handOver"),
    KEEP("pair.keep", "remote.pair.keep"),
}

/**
 * WHAT A MACHINE IS IN ITS PAIR (namespace draft §58), read from that machine's own
 * `/godot/pair/{role,state,linked,alarm,inStep}` - Go.dot's page's `model/pair.js`, ported:
 * the same seat words, and the same rule for which button the engine would take. A
 * reading, never a decision: the engine still judges every press, and says no in
 * `lastError` like any other refusal.
 *
 * The words "Main - sending", "Main - alone", "Backup - following", "Take over", "Hand
 * over", "Two mains!" and "Make this the main" are the author's (AGM-AGO); the other seats
 * are Go.dot's implementer's, in their shape.
 */
data class PairReading(
    val role: String,
    val state: String,
    val linked: Boolean,
    val alarm: String,
    val inStep: Boolean,
) {
    val paired: Boolean get() = role == "main" || role == "backup"

    private val isMain: Boolean get() = role == "main"

    /** Whether this machine sends to the devices: not paired, or paired and not holding. */
    val sends: Boolean get() = !paired || (state != "following" && state != "waiting")

    /** The words beside the machine's name - "Backup - following" - or null when it is not paired. */
    fun seat(): Said? {
        if (!paired || state.isEmpty()) return null
        return Said(
            when (state) {
                "following" -> if (isMain) "remote.pair.seat.mainFollowing" else "remote.pair.seat.backupFollowing"
                "waiting" -> if (isMain) "remote.pair.seat.mainWaiting" else "remote.pair.seat.backupWaiting"
                "alone" -> if (isMain) "remote.pair.seat.mainAlone" else "remote.pair.seat.backupSending"
                "bothSending" -> "remote.pair.seat.twoMains"
                else -> if (isMain) "remote.pair.seat.mainSending" else "remote.pair.seat.backupSending"
            }
        )
    }

    /**
     * Whether the engine would take [command] now (pair.js `offer`, the engine's
     * pair/Leadership.cpp rule written out from the rows): null when it would, else the
     * engine's reason word.
     */
    fun refusal(command: PairCommand): String? {
        val holding = state == "following" || state == "waiting"
        if (!paired) return "not-paired"
        return when (command) {
            PairCommand.TAKE_OVER -> if (holding) null else "not-holding"
            PairCommand.HAND_OVER -> when {
                holding -> "not-sending"
                (state == "leading" || state == "bothSending") && linked -> null
                else -> "not-linked"
            }
            PairCommand.KEEP -> if (state == "bothSending") null else "not-both-sending"
        }
    }

    fun offers(command: PairCommand): Boolean = refusal(command) == null

    /** The engine's alarm, said while it has one: Two mains!, a machine waiting alone. */
    val alarming: Boolean get() = paired && alarm.isNotEmpty()

    /** The one button that answers the alarm (pair.js `bannerCommand`), when it is one of the tablet's. */
    fun answer(): PairCommand? = when (state) {
        "bothSending" -> PairCommand.KEEP
        "waiting" -> PairCommand.TAKE_OVER
        else -> null
    }

    companion object {
        val UNPAIRED = PairReading("none", "alone", false, "", false)
    }
}

fun readPair(mirror: Mirror): PairReading = PairReading(
    role = mirror.text("/godot/pair/role") ?: "",
    state = mirror.text("/godot/pair/state") ?: "",
    linked = mirror.value("/godot/pair/linked")?.asBoolean() ?: false,
    alarm = mirror.text("/godot/pair/alarm") ?: "",
    inStep = mirror.value("/godot/pair/inStep")?.asBoolean() ?: false,
)
