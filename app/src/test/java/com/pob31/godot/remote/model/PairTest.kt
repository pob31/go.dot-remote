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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

/** A machine's seat in its pair, from its own rows: the words and the buttons pair.js gives. */
class PairTest {

    private fun mirror(role: String, state: String, linked: Boolean, alarm: String = ""): Mirror {
        val result = SnapshotReader.read(StringReader(Fixtures.snapshotSmall()))
        val mirror = Mirror().also { it.install(result.state, result.tick) }
        mirror.putValues("/godot/pair/role", "s", listOf(OscValue.Str(role)))
        mirror.putValues("/godot/pair/state", "s", listOf(OscValue.Str(state)))
        mirror.putValues("/godot/pair/linked", if (linked) "T" else "F", listOf(OscValue.of(linked)))
        mirror.putValues("/godot/pair/alarm", "s", listOf(OscValue.Str(alarm)))
        return mirror
    }

    private fun offered(pair: PairReading) = PairCommand.entries.filter { pair.offers(it) }

    @Test
    fun aMainLeadingItsBackupSaysSendingAndMayHandOver() {
        val pair = readPair(mirror("main", "leading", linked = true))
        assertEquals(Said("remote.pair.seat.mainSending"), pair.seat())
        assertTrue(pair.sends)
        assertEquals(listOf(PairCommand.HAND_OVER), offered(pair))
        assertEquals("not-holding", pair.refusal(PairCommand.TAKE_OVER))
    }

    @Test
    fun aBackupFollowingMayOnlyTakeOver() {
        val pair = readPair(mirror("backup", "following", linked = true))
        assertEquals(Said("remote.pair.seat.backupFollowing"), pair.seat())
        assertFalse(pair.sends)
        assertEquals(listOf(PairCommand.TAKE_OVER), offered(pair))
        assertEquals("not-sending", pair.refusal(PairCommand.HAND_OVER))
    }

    @Test
    fun aMainAloneHasNobodyToHandOverTo() {
        val pair = readPair(mirror("main", "alone", linked = false))
        assertEquals(Said("remote.pair.seat.mainAlone"), pair.seat())
        assertEquals(emptyList<PairCommand>(), offered(pair))
        assertEquals("not-linked", pair.refusal(PairCommand.HAND_OVER))
    }

    @Test
    fun twoMainsAreAnAlarmAnsweredByMakeThisTheMain() {
        val pair = readPair(mirror("backup", "bothSending", linked = true, alarm = "Both computers are sending."))
        assertEquals(Said("remote.pair.seat.twoMains"), pair.seat())
        assertTrue(pair.alarming)
        assertEquals(PairCommand.KEEP, pair.answer())
        assertEquals(listOf(PairCommand.HAND_OVER, PairCommand.KEEP), offered(pair))
    }

    @Test
    fun aMachineWaitingForItsPartnerIsAnsweredByTakeOver() {
        val pair = readPair(mirror("backup", "waiting", linked = false, alarm = "The main is not there."))
        assertEquals(Said("remote.pair.seat.backupWaiting"), pair.seat())
        assertEquals(PairCommand.TAKE_OVER, pair.answer())
        assertEquals(listOf(PairCommand.TAKE_OVER), offered(pair))
        assertFalse(pair.sends)
    }

    @Test
    fun aMachineNotPairedSaysNothingAndOffersNothing() {
        val pair = readPair(mirror("none", "alone", linked = false, alarm = "ignored"))
        assertNull(pair.seat())
        assertFalse(pair.paired)
        assertFalse(pair.alarming)
        assertTrue(pair.sends)
        for (command in PairCommand.entries) assertEquals("not-paired", pair.refusal(command))
    }

    @Test
    fun theCommandsAreTheEnginesNames() {
        assertEquals(listOf("pair.takeOver", "pair.handOver", "pair.keep"), PairCommand.entries.map { it.command })
    }
}
