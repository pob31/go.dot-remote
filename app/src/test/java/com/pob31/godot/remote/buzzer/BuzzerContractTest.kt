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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The buzzer's frames, the bridge's hex, the info bytes, and how the link's phase, heartbeat and retries go. */
class BuzzerContractTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun framesAreOneOrTwoBytes() {
        assertArrayEquals(bytes(0x00), BuzzerContract.frame(BuzzerContract.OP_HEARTBEAT))
        assertArrayEquals(bytes(0x06), BuzzerContract.frame(BuzzerContract.OP_IDENTIFY))
        assertArrayEquals(bytes(0x05, 0x00), BuzzerContract.frame(BuzzerContract.OP_TEST, 0))
        assertArrayEquals(bytes(0x07, 123), BuzzerContract.frame(BuzzerContract.OP_EFFECT, 123))
        // A param is a byte: the page's `param & 0xff`, here too.
        assertArrayEquals(bytes(0x08, 0x2c), BuzzerContract.frame(BuzzerContract.OP_CALIBRATE, 300))
    }

    @Test(expected = IllegalArgumentException::class)
    fun anOpcodeIsAByte() {
        BuzzerContract.frame(256)
    }

    @Test
    fun theBridgesHexIsTwoOrFourDigits() {
        assertArrayEquals(bytes(0x02), BuzzerContract.parseHex("02"))
        assertArrayEquals(bytes(0x05, 0x00), BuzzerContract.parseHex("0500"))
        assertArrayEquals(bytes(0x07, 0x7b), BuzzerContract.parseHex("07 7B"))
        assertArrayEquals(bytes(0xff), BuzzerContract.parseHex("ff"))
        for (bad in listOf("", "0", "000", "000000", "zz", "0x02", "-1", "05 00 01", "g0")) {
            assertNull("'$bad' is not a frame", BuzzerContract.parseHex(bad))
        }
        assertEquals("0500", BuzzerContract.hexOf(bytes(5, 0)))
        assertEquals("ff7b", BuzzerContract.hexOf(bytes(0xff, 0x7b)))
    }

    @Test
    fun theInfoBytesAreReadAsFarAsTheyGo() {
        val full = BuzzerInfo.parse(bytes(1, 0, 3, 1, 0))!!
        assertEquals(1, full.contract)
        assertEquals("0.3", full.firmware)
        assertEquals(true, full.calibrated)
        assertEquals(false, full.driverPresent)
        // An older wearable says three bytes: none the wiser, and nothing invented.
        val old = BuzzerInfo.parse(bytes(1, 0, 2))!!
        assertNull(old.calibrated)
        assertNull(old.driverPresent)
        assertEquals("0.2", old.firmware)
        assertNull(BuzzerInfo.parse(ByteArray(0)))
        assertNull(BuzzerInfo.parse(null))
    }

    @Test
    fun aDropFromLinkedIsLostAndFromSearchingStillSearching() {
        assertEquals(BuzzerPhase.LOST, BuzzerPhase.LINKED.next(BuzzerEvent.DROPPED))
        assertEquals(BuzzerPhase.LOST, BuzzerPhase.LOST.next(BuzzerEvent.DROPPED))
        assertEquals(BuzzerPhase.SEARCHING, BuzzerPhase.SEARCHING.next(BuzzerEvent.DROPPED))
        // Trying again while lost is still lost: the operator was told it fell.
        assertEquals(BuzzerPhase.LOST, BuzzerPhase.LOST.next(BuzzerEvent.CONNECTING))
        assertEquals(BuzzerPhase.SEARCHING, BuzzerPhase.BLUETOOTH_OFF.next(BuzzerEvent.CONNECTING))
        assertEquals(BuzzerPhase.LINKED, BuzzerPhase.LOST.next(BuzzerEvent.LINKED))
        assertEquals(BuzzerPhase.NONE, BuzzerPhase.LINKED.next(BuzzerEvent.FORGOTTEN))
        assertEquals(BuzzerPhase.NONE, BuzzerPhase.NONE.next(BuzzerEvent.DROPPED))
        assertEquals(BuzzerPhase.NO_PERMISSION, BuzzerPhase.SEARCHING.next(BuzzerEvent.NO_PERMISSION))
    }

    @Test
    fun theHeartbeatIsDueEveryPeriodWhileLinked() {
        val beat = Heartbeat(30_000L)
        assertFalse("not linked: never due", beat.due(1_000_000L))
        assertNull(beat.wait(0L))
        beat.linked(0L)
        assertFalse(beat.due(29_999L))
        assertTrue(beat.due(30_000L))
        assertEquals(10_000L, beat.wait(20_000L))
        beat.sent(30_000L)
        assertFalse(beat.due(45_000L))
        // The page's own heartbeat counts.
        beat.sent(50_000L)
        assertFalse(beat.due(70_000L))
        assertTrue(beat.due(80_000L))
        beat.dropped()
        assertFalse(beat.due(1_000_000L))
        beat.sent(1_000_000L)
        assertFalse("a heartbeat while dropped does not link it", beat.due(2_000_000L))
    }

    @Test
    fun theDefaultHeartbeatLandsTwiceInsideTheWatchdog() {
        val beat = Heartbeat()
        beat.linked(0L)
        val period = beat.wait(0L)!!
        assertTrue(period * 2 < BuzzerContract.WATCHDOG_MS)
        assertTrue("at least every 60 s", period <= 60_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aHeartbeatSlowerThanHalfTheWatchdogIsRefused() {
        Heartbeat(45_000L)
    }

    @Test
    fun retriesSpreadOutAndALinkResetsThem() {
        val plan = ReconnectPlan()
        assertEquals(listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L, 30_000L, 30_000L), List(7) { plan.nextDelay() })
        assertEquals(7, plan.attempts)
        plan.linked()
        assertEquals(1_000L, plan.nextDelay())
    }

    @Test
    fun theBridgesDetailSaysTheLink() {
        val linked = BuzzerStatus(
            remembered = RememberedBuzzer("D1:23:45:67:89:AB", "CHF-89AB"),
            phase = BuzzerPhase.LINKED,
            battery = 82,
            info = BuzzerInfo(1, 0, 3, true, true),
        )
        assertEquals(
            """{"paired":true,"linked":true,"name":"CHF-89AB","battery":82,"contract":1,"calibrated":true,"driver":true}""",
            bridgeDetail(linked)
        )
        assertEquals(
            """{"paired":true,"linked":false,"name":"CHF-89AB","battery":null,"contract":null,"calibrated":null,"driver":null}""",
            bridgeDetail(BuzzerStatus(linked.remembered, BuzzerPhase.LOST))
        )
        assertEquals(
            """{"paired":false,"linked":false,"name":null,"battery":null,"contract":null,"calibrated":null,"driver":null}""",
            bridgeDetail(BuzzerStatus())
        )
    }

    @Test
    fun aNameCannotBreakOutOfTheScript() {
        assertEquals("\"a\\\"b\\\\c\\u003c/script>\\u000a\\u2028\"", jsonString("a\"b\\c</script>\n "))
    }
}
