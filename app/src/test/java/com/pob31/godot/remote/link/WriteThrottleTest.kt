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
import com.pob31.godot.remote.osc.OscValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

/** The throttle on virtual time: what goes out, and when, to the millisecond. */
@OptIn(ExperimentalCoroutinesApi::class)
class WriteThrottleTest {

    private val scope = TestScope()
    private val sent = mutableListOf<Pair<Long, String>>()
    private val throttle = WriteThrottle(20, scope, { scope.testScheduler.currentTime }) { message ->
        sent += scope.testScheduler.currentTime to (message.values.first() as OscValue.Str).value
        true
    }

    private fun value(text: String) = OscMessage("/godot/cue/F7HR8TVD/level", OscValue.Str(text))

    private fun at(ms: Long) {
        scope.advanceTimeBy(ms - scope.testScheduler.currentTime)
        scope.runCurrent()
    }

    @Test
    fun theFirstValueGoesAtOnceAndOnlyTheLatestOfABurstFollows() {
        throttle.offer("a", value("1"))
        for (i in 2..9) {
            at(i.toLong())
            throttle.offer("a", value("$i"))
        }
        assertEquals(listOf(0L to "1"), sent)
        at(25)
        assertEquals(listOf(0L to "1", 20L to "9"), sent)
        at(100)
        assertEquals("nothing more", 2, sent.size)
    }

    @Test
    fun aValueAfterTheIntervalGoesAtOnce() {
        throttle.offer("a", value("1"))
        at(30)
        throttle.offer("a", value("2"))
        assertEquals(listOf(0L to "1", 30L to "2"), sent)
    }

    @Test
    fun addressesAreThrottledApart() {
        throttle.offer("a", value("a1"))
        throttle.offer("b", value("b1"))
        throttle.offer("a", value("a2"))
        assertEquals(listOf(0L to "a1", 0L to "b1"), sent)
        at(20)
        assertEquals(listOf(0L to "a1", 0L to "b1", 20L to "a2"), sent)
    }

    @Test
    fun releaseSendsThePendingValueNowAndNothingOlderFollows() {
        throttle.offer("a", value("1"))
        at(5)
        throttle.offer("a", value("2"))
        at(6)
        throttle.offer("a", value("3"))
        at(7)
        throttle.flush("a")
        assertEquals(listOf(0L to "1", 7L to "3"), sent)
        at(100)
        assertEquals("the cancelled timer sends nothing", 2, sent.size)
        // Nothing pending: a release sends nothing.
        throttle.flush("a")
        assertEquals(2, sent.size)
    }

    @Test
    fun sendNowReplacesThePendingValue() {
        throttle.offer("a", value("1"))
        at(5)
        throttle.offer("a", value("2"))
        throttle.sendNow("a", value("typed"))
        at(100)
        assertEquals(listOf(0L to "1", 5L to "typed"), sent)
    }
}
