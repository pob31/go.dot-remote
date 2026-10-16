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

package com.pob31.godot.remote.buzzer

import java.util.UUID

/**
 * CHOUFLEUR'S WRIST BUZZER, AS ITS CONTRACT SAYS (choufleur_prompt, buzzer/README.md, "The
 * contract", contract 1): a BLE wearable named CHF-XXXX advertising one service; a "vibe"
 * characteristic written one or two bytes at a time, `[op]` or `[op, param]`; an "info"
 * characteristic read as `[contract, fw_major, fw_minor, calibrated, driver_present]`; the
 * standard Battery Service. Choufleur's page computes the warnings - the server sends none -
 * so the remote only carries the page's bytes, and keeps the link alive with a heartbeat.
 *
 * Pure: no Android, so the framing is tested on the JVM.
 */
object BuzzerContract {
    val SERVICE: UUID = UUID.fromString("c0f1e000-a7d3-4b8f-96f1-2b73d3c5a001")
    val VIBE: UUID = UUID.fromString("c0f1e001-a7d3-4b8f-96f1-2b73d3c5a001")
    val INFO: UUID = UUID.fromString("c0f1e002-a7d3-4b8f-96f1-2b73d3c5a001")
    val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    /** The descriptor a notification is switched on through. */
    val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** The contract this remote was written against. */
    const val CONTRACT = 1

    const val OP_HEARTBEAT = 0x00
    const val OP_STANDBY = 0x01
    const val OP_FINAL = 0x02
    const val OP_LOST_NEAR = 0x03
    const val OP_CANCEL = 0x04
    const val OP_TEST = 0x05
    const val OP_IDENTIFY = 0x06
    const val OP_EFFECT = 0x07
    const val OP_CALIBRATE = 0x08
    const val OP_RATED = 0x09

    /** The wearable's watchdog: 90 s without a heartbeat and it says the link is stale. */
    const val WATCHDOG_MS = 90_000L

    /** One frame: `[op]`, or `[op, param]` when a param is given. */
    fun frame(op: Int, param: Int? = null): ByteArray {
        require(op in 0..0xff) { "an opcode is a byte" }
        return if (param == null) byteArrayOf(op.toByte()) else byteArrayOf(op.toByte(), (param and 0xff).toByte())
    }

    /**
     * The page's bytes through the bridge: two or four hex digits ("05", "0500"; case and
     * spaces do not matter). Null for anything else - an empty string, an odd digit, three
     * bytes - which is never written.
     */
    fun parseHex(hex: String): ByteArray? {
        val digits = hex.filterNot { it.isWhitespace() }
        if (digits.length != 2 && digits.length != 4) return null
        if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return ByteArray(digits.length / 2) { digits.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun hexOf(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/** The info characteristic, read: bytes four and five are later additions and may be missing. */
data class BuzzerInfo(
    val contract: Int,
    val firmwareMajor: Int?,
    val firmwareMinor: Int?,
    /** The driver's last auto-calibration passed; null when the wearable does not say. */
    val calibrated: Boolean?,
    /** The driver answered its last I2C write: false is a broken lead, not a bad calibration. */
    val driverPresent: Boolean?,
) {
    val firmware: String? get() = if (firmwareMajor != null && firmwareMinor != null) "$firmwareMajor.$firmwareMinor" else null

    companion object {
        fun parse(bytes: ByteArray?): BuzzerInfo? {
            if (bytes == null || bytes.isEmpty()) return null
            fun at(i: Int) = if (bytes.size > i) bytes[i].toInt() and 0xff else null
            return BuzzerInfo(at(0)!!, at(1), at(2), at(3)?.let { it != 0 }, at(4)?.let { it != 0 })
        }
    }
}

/** How the link to the buzzer stands, in the words the Connections tab says. */
enum class BuzzerPhase(val key: String) {
    /** No buzzer remembered. */
    NONE("buzzer.phase.none"),

    /** Android has not been allowed Bluetooth yet. */
    NO_PERMISSION("buzzer.phase.noPermission"),
    BLUETOOTH_OFF("buzzer.phase.bluetoothOff"),

    /** Remembered, not linked yet since the remote started: looking for it. */
    SEARCHING("buzzer.phase.searching"),
    LINKED("buzzer.phase.linked"),

    /** It was linked and the link fell: looking for it again. */
    LOST("buzzer.phase.lost"),
}

/** What happens to the link; [BuzzerPhase] follows from them alone ([next]). */
enum class BuzzerEvent { REMEMBERED, NO_PERMISSION, BLUETOOTH_OFF, CONNECTING, LINKED, DROPPED, FORGOTTEN }

/** The phase after [event]: a drop from linked is LOST - the wrist has already felt it - from anything else still SEARCHING. */
fun BuzzerPhase.next(event: BuzzerEvent): BuzzerPhase = when (event) {
    BuzzerEvent.FORGOTTEN -> BuzzerPhase.NONE
    BuzzerEvent.NO_PERMISSION -> BuzzerPhase.NO_PERMISSION
    BuzzerEvent.BLUETOOTH_OFF -> BuzzerPhase.BLUETOOTH_OFF
    BuzzerEvent.LINKED -> BuzzerPhase.LINKED
    BuzzerEvent.REMEMBERED -> BuzzerPhase.SEARCHING
    BuzzerEvent.CONNECTING -> if (this == BuzzerPhase.LOST) BuzzerPhase.LOST else BuzzerPhase.SEARCHING
    BuzzerEvent.DROPPED -> when (this) {
        BuzzerPhase.LINKED, BuzzerPhase.LOST -> BuzzerPhase.LOST
        BuzzerPhase.NONE -> BuzzerPhase.NONE
        else -> BuzzerPhase.SEARCHING
    }
}

/**
 * THE HEARTBEAT: a 0x00 every [periodMs] while linked, well inside the wearable's 90 s
 * watchdog - a phone that sleeps a timer by a few seconds still lands one in time. The page
 * sends its own when it runs; the remote's covers a page that is not open.
 */
class Heartbeat(private val periodMs: Long = 30_000L) {
    init {
        require(periodMs * 2 < BuzzerContract.WATCHDOG_MS) { "two heartbeats must fit in the watchdog" }
    }

    private var lastAt: Long? = null

    /** Linked at [now]: the first heartbeat is due a period later (the link itself fed the watchdog). */
    fun linked(now: Long) {
        lastAt = now
    }

    fun dropped() {
        lastAt = null
    }

    /** Any heartbeat sent at [now], the page's included. */
    fun sent(now: Long) {
        if (lastAt != null) lastAt = now
    }

    fun due(now: Long): Boolean = lastAt?.let { now - it >= periodMs } ?: false

    /** How long until the next is due; null while not linked. */
    fun wait(now: Long): Long? = lastAt?.let { (it + periodMs - now).coerceAtLeast(0L) }
}

/**
 * THE WAY BACK AFTER A DROP: Android's own autoConnect reconnects a wearable that walks back
 * into range, but a link closed with an error must be opened again. Tries are spaced 1, 2, 5,
 * 10 then every 30 s, for as long as the buzzer is remembered; a link resets the spacing.
 */
class ReconnectPlan(private val steps: List<Long> = listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)) {
    private var tries = 0

    fun nextDelay(): Long = steps[tries.coerceAtMost(steps.size - 1)].also { tries++ }

    fun linked() {
        tries = 0
    }

    val attempts: Int get() = tries
}
