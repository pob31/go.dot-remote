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

import com.pob31.godot.remote.osc.OscMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * At most one write per address every [intervalMs] (20 ms: the engine's 50 Hz tick),
 * keeping the LATEST value - WFS-DIY's OscThrottleManager, one per link instead of one
 * for the app.
 *
 * A value offered inside the interval waits as the address's pending value, replaced by
 * any newer one, and goes when the interval is up. [flush] is the gesture's end: the
 * pending value goes NOW, unthrottled, and nothing older can follow it, because the timer
 * that would have sent it is cancelled under the same lock (WFS-DIY's "final value lands
 * unthrottled", without its 60 ms wait: one ordered WebSocket has no send in flight to
 * overtake).
 */
class WriteThrottle(
    private val intervalMs: Long,
    private val scope: CoroutineScope,
    private val clockMs: () -> Long,
    private val send: (OscMessage) -> Boolean,
) {
    private class Slot {
        var lastSentAt = Long.MIN_VALUE / 2
        var pending: OscMessage? = null
        var timer: Job? = null
    }

    private val slots = HashMap<String, Slot>()
    private val lock = Any()

    /** Throttled: sends now if the address's interval is up, else keeps [message] as its pending value. */
    fun offer(key: String, message: OscMessage) {
        synchronized(lock) {
            val slot = slots.getOrPut(key) { Slot() }
            val now = clockMs()
            val wait = slot.lastSentAt + intervalMs - now
            if (wait <= 0 && slot.timer == null) {
                slot.pending = null
                slot.lastSentAt = now
                send(message)
                return
            }
            slot.pending = message
            if (slot.timer == null) {
                slot.timer = scope.launch {
                    delay(wait.coerceAtLeast(0))
                    timerFired(key)
                }
            }
        }
    }

    private fun timerFired(key: String) {
        synchronized(lock) {
            val slot = slots[key] ?: return
            slot.timer = null
            val message = slot.pending ?: return
            slot.pending = null
            slot.lastSentAt = clockMs()
            send(message)
        }
    }

    /** The gesture's end: the pending value, if any, goes now. True when something was sent. */
    fun flush(key: String): Boolean {
        synchronized(lock) {
            val slot = slots[key] ?: return false
            slot.timer?.cancel()
            slot.timer = null
            val message = slot.pending ?: return false
            slot.pending = null
            slot.lastSentAt = clockMs()
            return send(message)
        }
    }

    /** Unthrottled, replacing whatever was pending: a typed value, a switch. */
    fun sendNow(key: String, message: OscMessage): Boolean {
        synchronized(lock) {
            val slot = slots.getOrPut(key) { Slot() }
            slot.timer?.cancel()
            slot.timer = null
            slot.pending = null
            slot.lastSentAt = clockMs()
            return send(message)
        }
    }

    /** Forget everything pending: the connection it was for is gone. */
    fun clear() {
        synchronized(lock) {
            for (slot in slots.values) slot.timer?.cancel()
            slots.clear()
        }
    }
}
