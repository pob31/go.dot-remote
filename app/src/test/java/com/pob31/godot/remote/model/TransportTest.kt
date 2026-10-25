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
import com.pob31.godot.remote.Fixtures.CUE_A
import com.pob31.godot.remote.Fixtures.CUE_B
import com.pob31.godot.remote.Fixtures.LIST
import com.pob31.godot.remote.link.Refusal
import com.pob31.godot.remote.osc.OscValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.StringReader
import java.util.Locale

/** The header read from a fixture mirror: current from history, next from standby, the run, Doh! off the tick. */
class TransportTest {

    private lateinit var mirror: Mirror

    @Before
    fun read() {
        val result = SnapshotReader.read(StringReader(Fixtures.snapshotSmall()))
        mirror = Mirror().also { it.install(result.state, result.tick) }
    }

    private fun put(address: String, text: String) = mirror.putValues(address, "s", listOf(OscValue.Str(text)))
    private fun put(address: String, value: Double) = mirror.putValues(address, "d", listOf(OscValue.Float64(value)))
    private fun tick(tick: Long) = mirror.putValues("/godot/engine/tick", "h", listOf(OscValue.Int64(tick)))

    // ------------------------------------------------------------------ the cues

    @Test
    fun nextIsTheFocusedListsStandby() {
        val header = readHeader(mirror)
        assertEquals(LIST, header.listId)
        val next = header.next!!
        assertEquals(CUE_A, next.id)
        assertEquals("2.2", next.number)
        assertEquals("Announce", next.name)
        assertEquals("media", next.kind)
        assertEquals(GodotIcon.MEDIA, next.icon)
        assertEquals("remote.kind.media", next.kindKey)
    }

    @Test
    fun withNoFocusTheFirstListIsRead() {
        put("/godot/list/focus", "")
        put("/godot/list/order", "$LIST OTHERLST")
        assertEquals(LIST, readHeader(mirror).listId)
        assertEquals(CUE_A, readHeader(mirror).next?.id)
    }

    @Test
    fun aClearedStandbyIsNoNext() {
        put("/godot/list/$LIST/standby", "")
        assertNull(readHeader(mirror).next)
    }

    @Test
    fun currentIsTheNewestStepThatFired_aDohIsNotAFiring() {
        put("/godot/list/$LIST/history", "160:$CUE_A:d 150:$CUE_B:g 120:$CUE_A:t")
        val header = readHeader(mirror)
        assertEquals(CUE_B, header.current?.id)
        assertEquals("3", header.current?.number)
        assertEquals('g', header.currentOrigin)
        assertEquals("remote.origin.go", originKey('g'))
    }

    @Test
    fun anEmptyHistoryHasNoCurrent() {
        put("/godot/list/$LIST/history", "")
        assertNull(readHeader(mirror).current)
    }

    @Test
    fun aHistoryWordOfAnotherShapeIsSkipped() {
        assertEquals(
            listOf(HistoryStep(150, CUE_B, 'g'), HistoryStep(90, CUE_A, 'p')),
            readHistory("junk 150:$CUE_B:g :: 12:: 90:$CUE_A:p")
        )
    }

    @Test
    fun theCurrentCuesNewestLiveRunIsShownWithItsStateAndPosition() {
        put("/godot/list/$LIST/history", "150:$CUE_B:g")
        put("/godot/run/order", "RUNAAAAA RUNBBBBB RUNCCCCC")
        put("/godot/run/RUNAAAAA/cue", CUE_B)
        put("/godot/run/RUNAAAAA/state", "playing")
        put("/godot/run/RUNBBBBB/cue", CUE_B)
        put("/godot/run/RUNBBBBB/state", "playing")
        put("/godot/run/RUNBBBBB/position", 12.34)
        put("/godot/run/RUNCCCCC/cue", CUE_A)
        put("/godot/run/RUNCCCCC/state", "playing")
        val run = readHeader(mirror).currentRun!!
        assertEquals("RUNBBBBB", run.id)
        assertEquals("playing", run.state)
        assertEquals(12.34, run.position, 1e-9)
        assertEquals("remote.runState.playing", run.stateKey)
        assertEquals("0:12.3", clockText(run.position))
    }

    @Test
    fun aFinishedRunIsNotShown_aWaitShowsItsTimeLeft() {
        put("/godot/list/$LIST/history", "150:$CUE_B:g")
        put("/godot/run/order", "RUNAAAAA")
        put("/godot/run/RUNAAAAA/cue", CUE_B)
        put("/godot/run/RUNAAAAA/state", "done")
        assertNull(readHeader(mirror).currentRun)
        put("/godot/run/RUNAAAAA/state", "waiting")
        put("/godot/run/RUNAAAAA/remaining", 2.5)
        val run = readHeader(mirror).currentRun!!
        assertTrue(run.counting)
        assertEquals("0:02.5", clockText(run.remaining))
    }

    @Test
    fun clockTextIsMinutesSecondsAndTenthsInEveryLocale() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertEquals("12:04.0", clockText(724.0))
            assertEquals("0:00.0", clockText(-3.0))
            assertEquals("1:00.0", clockText(59.96))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun theLockAndTheAudioAreRead() {
        mirror.putValues("/godot/document/locked", "T", listOf(OscValue.True))
        put("/godot/audio/status", "running")
        val header = readHeader(mirror)
        assertEquals(true, header.locked)
        assertTrue(header.audioRunning)
        assertNull(header.goLine())
        assertEquals(Said("remote.header.noClock"), header.copy(audioStatus = "noClock").goLine())
        assertEquals(Said("remote.header.noAudio"), header.copy(audioStatus = "stopped").goLine())
    }

    @Test
    fun theNextGoCarryingTheStandbyOnIsSaid() {
        put("/godot/list/$LIST/resume", "$CUE_A 68.7")
        assertEquals(Said("remote.header.resumesAt", "time" to "1:08"), readHeader(mirror).resumeWords())
        put("/godot/list/$LIST/resume", CUE_A)
        assertEquals(Said("remote.header.resumes"), readHeader(mirror).resumeWords())
        put("/godot/list/$LIST/resume", "$CUE_B 3")
        assertNull(readHeader(mirror).resumeWords())
    }

    @Test
    fun aLoadToTimeSaysTheSecondGoPlaysTheStandbyFrom() {
        assertNull(readHeader(mirror).loadedWords())
        put("/godot/list/$LIST/loaded", "$CUE_A 72.34")
        assertEquals(Said("remote.header.loadedAt", "time" to "1:12.3"), readHeader(mirror).loadedWords())
        // Before the cue nothing is held: GO plays it from the top.
        put("/godot/list/$LIST/loaded", "$CUE_A -1")
        assertEquals(Said("remote.header.loadedBefore"), readHeader(mirror).loadedWords())
        // Another cue's hold is not the standby's; empty is nothing held.
        put("/godot/list/$LIST/loaded", "$CUE_B 5")
        assertNull(readHeader(mirror).loadedWords())
        put("/godot/list/$LIST/loaded", "")
        assertNull(readHeader(mirror).loadedWords())
    }

    // ------------------------------------------------------------------ Doh!

    private fun doh(goTick: Long, now: Long, window: Double = 10.0): HeaderReading {
        put("/godot/list/doh", "$LIST $CUE_B $goTick")
        put("/godot/list/dohWindow", window)
        tick(now)
        return readHeader(mirror)
    }

    @Test
    fun dohIsOpenWithTheCueItWouldTakeBackAndTheSecondsLeft() {
        val header = doh(goTick = 1000, now = 1100) // two seconds in, eight left
        val look = header.dohLook()
        assertEquals(DohPhase.OPEN, look.phase)
        assertEquals(1.0, look.strength, 0.0)
        assertEquals(8, look.secondsLeft)
        assertEquals("Doh! 3", header.dohCaption())
        assertTrue(header.dohClickable())
    }

    @Test
    fun dohFadesOverTheWindowsLastTwoSeconds() {
        val look = doh(goTick = 1000, now = 1450).dohLook() // 50 ticks left of 500, the fade is 100
        assertEquals(DohPhase.FADING, look.phase)
        assertEquals(0.5, look.strength, 1e-9)
        assertEquals(1, look.secondsLeft)
        assertEquals(DohPhase.OPEN, doh(goTick = 1000, now = 1400).dohLook().phase) // 100 left: not yet
    }

    @Test
    fun dohIsOverOnTheTickAPressWouldBeRefused() {
        assertEquals(DohPhase.FADING, doh(goTick = 1000, now = 1499).dohLook().phase)
        val over = doh(goTick = 1000, now = 1500)
        assertEquals(DohPhase.OVER, over.dohLook().phase)
        assertEquals("Doh!", over.dohCaption())
        assertFalse(over.dohClickable())
    }

    @Test
    fun aShortWindowFadesOverItsSecondHalf() {
        val look = doh(goTick = 1000, now = 1060, window = 2.0).dohLook() // 100 ticks, fade 50, 40 left
        assertEquals(DohPhase.FADING, look.phase)
        assertEquals(0.8, look.strength, 1e-9)
    }

    @Test
    fun dohOffOrNothingToTakeBackIsOver() {
        assertEquals(DohPhase.OVER, doh(goTick = 1000, now = 1000, window = 0.0).dohLook().phase)
        put("/godot/list/doh", "")
        assertEquals(DohPhase.OVER, readHeader(mirror).dohLook().phase)
        // A tick before the GO's, or a cue nobody can name: over, never a guess.
        assertEquals(DohPhase.OVER, doh(goTick = 1000, now = 990).dohLook().phase)
        put("/godot/list/doh", "$LIST UNKNOWN0 1000")
        tick(1010)
        assertEquals(DohPhase.OVER, readHeader(mirror).dohLook().phase)
    }

    @Test
    fun theWindowTheShowHasNotSaidIsTenSeconds() {
        assertEquals(10.0, HeaderReading(dohWindow = "").dohWindowSeconds, 0.0)
        assertEquals(0.0, HeaderReading(dohWindow = "-2").dohWindowSeconds, 0.0)
    }

    @Test
    fun aResumeToForgetKeepsDohPressable() {
        put("/godot/list/dohForget", "$LIST $CUE_A")
        val header = doh(goTick = 1000, now = 5000)
        assertEquals(DohPhase.OVER, header.dohLook().phase)
        assertTrue(header.dohClickable())
    }

    // ------------------------------------------------------------------ refusals in words

    private fun refused(record: String) = refusalLine(Refusal.parse(record), 10.0)

    @Test
    fun dohsRefusalsAreSentences() {
        assertEquals(Said("remote.refusal.dohTooLate"), refused("900 4 ws:t too-late go.doh"))
        assertEquals(Said("remote.refusal.dohOff"), refusalLine(Refusal.parse("900 4 ws:t too-late go.doh"), 0.0))
        assertEquals(Said("remote.refusal.dohNothing"), refused("900 4 ws:t nothing-to-take-back go.doh"))
        assertEquals(Said("remote.refusal.dohTrigger"), refused("900 4 ws:t trigger-after-go go.doh"))
        assertEquals(Said("remote.refusal.dohTooSoon"), refused("900 4 ws:t too-soon go.doh"))
    }

    @Test
    fun aBouncedGoIsASentence() {
        assertEquals(Said("remote.refusal.goTooSoon"), refused("900 4 ws:t too-soon go"))
    }

    @Test
    fun anyOtherRefusalSaysTheCommandAndTheEnginesReason() {
        assertEquals(
            Said("remote.refusal.withCommand", "command" to "standby.set", "reason" to "not-a-stop"),
            refused("900 4 ws:t not-a-stop standby.set")
        )
    }

    @Test
    fun aSetManyRefusalSaysWhere() {
        assertEquals(
            Said("remote.refusal.setMany", "reason" to "locked", "where" to "/godot/cue/$CUE_A/name with space"),
            refused("900 4 ws:t locked node.setMany /godot/cue/$CUE_A/name with space")
        )
    }

    @Test
    fun aRecordOfAnotherShapeIsShownWhole() {
        assertEquals(Said.verbatim("900 4 ws:t locked"), refused("900 4 ws:t locked"))
        assertEquals(Said.verbatim("900 4 ws:t odd go extra"), refused("900 4 ws:t odd go extra"))
    }

    @Test
    fun noClockTakesTheLineAndNothingRefusedSaysNothing() {
        assertEquals(Said("remote.refusal.noClock"), refusalLine(null, 10.0, "noClock"))
        assertNull(refusalLine(null, 10.0, "running"))
    }
}
