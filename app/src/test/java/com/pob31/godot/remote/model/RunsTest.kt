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

import com.pob31.godot.remote.model.ShowFixture.CRICKETS
import com.pob31.godot.remote.model.ShowFixture.NIGHT_BED
import com.pob31.godot.remote.model.ShowFixture.ROAD
import com.pob31.godot.remote.model.ShowFixture.RUN_CRICKETS
import com.pob31.godot.remote.model.ShowFixture.RUN_NIGHT_BED
import com.pob31.godot.remote.model.ShowFixture.RUN_STORM
import com.pob31.godot.remote.model.ShowFixture.RUN_THUNDER
import com.pob31.godot.remote.model.ShowFixture.RUN_THUNDER_AND_RAIN
import com.pob31.godot.remote.model.ShowFixture.STORM
import com.pob31.godot.remote.model.ShowFixture.THUNDER
import com.pob31.godot.remote.model.ShowFixture.WIND
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The running tab's rows, from a real engine's runs, and what each gesture sends. */
class RunsTest {

    private val mirror = ShowFixture.mirror()

    private fun row(id: String) = readRuns(mirror).first { it.id == id }

    @Test
    fun rowsInTheOrderTheShowHappened_membersUnderTheirGroup() {
        val rows = readRuns(mirror)
        // Created 2Y5 60N 1CG P82 Q2B; started 1336, 2218, 2223 (under Storm), 2275, 2277 (under Night bed).
        assertEquals(
            listOf(RUN_THUNDER_AND_RAIN, RUN_STORM, RUN_THUNDER, RUN_NIGHT_BED, RUN_CRICKETS),
            rows.map { it.id }
        )
        assertEquals(listOf(0, 0, 1, 0, 1), rows.map { it.depth })
    }

    @Test
    fun aRowSaysWhatItIs() {
        val storm = row(RUN_STORM)
        assertEquals(STORM, storm.cue)
        assertEquals("2", storm.number)
        assertEquals("Storm", storm.name)
        assertEquals("group", storm.kind)
        assertEquals(GodotIcon.SEQUENCE, storm.icon)
        assertEquals("playing", storm.state)
        assertEquals("members", storm.phase)
        assertEquals("remote.runState.playing", storm.stateKey)
        assertEquals(1, storm.descendants) // Thunder's run: the header's memo ended and went

        val thunder = row(RUN_THUNDER)
        assertEquals(THUNDER, thunder.cue)
        assertEquals(RUN_STORM, thunder.parentRun)
        assertEquals("2 Storm", thunder.parentLabel)
        assertEquals(-4.5, thunder.trim!!, 0.0)
        assertEquals(-120.0, thunder.meter!!, 0.0)
        assertTrue(thunder.hasTrim)
        assertEquals("/godot/run/$RUN_THUNDER/trim", thunder.trimAddress)
        assertNull("not analysed yet", thunder.timbre)
        assertTrue(thunder.timbreUnsaid)
    }

    @Test
    fun theRoundsPills_inTheRoundsOrder_thenOutOfIt_thenPruned() {
        val bed = row(RUN_NIGHT_BED)
        assertEquals(
            listOf(CRICKETS to PillState.PLAYING, ROAD to PillState.PENDING, WIND to PillState.PRUNED),
            bed.pills.map { it.cue to it.state }
        )
        assertEquals("3.1", bed.pills[0].number)
        assertEquals("Crickets", bed.pills[0].name)
        assertEquals(1 to 0, bed.roundCount) // round 1 of for ever

        // A member pruned for this round only is no longer in it: dimmed, as played.
        mirror.put("/godot/run/$RUN_NIGHT_BED/round", CRICKETS)
        assertEquals(
            listOf(CRICKETS to PillState.PLAYING, ROAD to PillState.OUT_THIS_ROUND, WIND to PillState.PRUNED),
            row(RUN_NIGHT_BED).pills.map { it.cue to it.state }
        )
        mirror.put("/godot/run/$RUN_CRICKETS/state", "done")
        assertEquals(PillState.PLAYED, row(RUN_NIGHT_BED).pills[0].state)
        assertTrue("a sound's run has no pills", row(RUN_THUNDER).pills.isEmpty())
    }

    @Test
    fun aPillTappedOrHeld() {
        val bed = row(RUN_NIGHT_BED)
        val (playing, pending, pruned) = bed.pills
        assertEquals(Sent("run.prune", RUN_NIGHT_BED, ROAD, "round"), RunActions.pillTap(bed, pending))
        assertEquals(Sent("run.prune", RUN_NIGHT_BED, ROAD, "group"), RunActions.pillHold(bed, pending))
        assertEquals(Sent("run.unprune", RUN_NIGHT_BED, WIND), RunActions.pillTap(bed, pruned))
        assertNull("a member playing is past pruning for this round", RunActions.pillTap(bed, playing))
        assertEquals(Sent("run.prune", RUN_NIGHT_BED, CRICKETS, "group"), RunActions.pillHold(bed, playing))
    }

    @Test
    fun whatTheSheetOffers_byKind() {
        val bed = row(RUN_NIGHT_BED)
        assertEquals(listOf(StopVerb.HARD, StopVerb.AFTER_MEMBER, StopVerb.AFTER_ROUND), RunActions.stopVerbs(bed))
        assertEquals(Sent("run.stop", RUN_NIGHT_BED, "afterIteration"), RunActions.stop(bed, StopVerb.AFTER_ROUND))
        assertEquals(Sent("run.kill", RUN_NIGHT_BED), RunActions.kill(bed))

        val thunder = row(RUN_THUNDER)
        assertEquals("a sound has no boundary to wait for", listOf(StopVerb.HARD), RunActions.stopVerbs(thunder))
        assertEquals(Sent("run.stop", RUN_THUNDER, "hard"), RunActions.stop(thunder, StopVerb.HARD))
        assertNull("in no range: nowhere to advance to", RunActions.advance(thunder))
        assertNull("on no strip: no solo", RunActions.solo(thunder))

        mirror.put("/godot/run/$RUN_THUNDER/range", 1.0)
        mirror.put("/godot/run/$RUN_THUNDER/strip", "STRP0001")
        assertEquals(Sent("run.advance", RUN_THUNDER), RunActions.advance(row(RUN_THUNDER)))
        assertEquals(Sent("run.solo", RUN_THUNDER, "true"), RunActions.solo(row(RUN_THUNDER)))
        mirror.put("/godot/run/$RUN_THUNDER/solo", true)
        assertEquals(Sent("run.solo", RUN_THUNDER, "false"), RunActions.solo(row(RUN_THUNDER)))

        mirror.put("/godot/run/$RUN_THUNDER/state", "done")
        val over = row(RUN_THUNDER)
        assertTrue(RunActions.stopVerbs(over).isEmpty())
        assertNull(RunActions.kill(over))
        assertFalse(over.hasTrim)
    }

    @Test
    fun aSeekIsASecondInNoLocalesWay() {
        mirror.put("/godot/cue/$THUNDER/duration", 30.0)
        mirror.put("/godot/run/$RUN_THUNDER/position", 12.0)
        val thunder = row(RUN_THUNDER)
        assertTrue(thunder.scrubbable)
        assertEquals(0.4, thunder.through()!!, 1e-12)
        assertEquals(Sent("run.seek", RUN_THUNDER, "12.25"), RunActions.seek(thunder, 12.25))

        // A sound whose length nobody measured has no strip to drag.
        mirror.put("/godot/cue/$THUNDER/duration", 0.0)
        assertFalse(row(RUN_THUNDER).scrubbable)
        // A scene scrubs over its solved round.
        mirror.put("/godot/run/$RUN_STORM/roundLength", 20.0)
        mirror.put("/godot/run/$RUN_STORM/roundFrom", 5.0)
        assertEquals(5.0 to 25.0, row(RUN_STORM).span())
    }

    @Test
    fun aWaitCountsDown_andATimbreIsThreeNumbers() {
        mirror.put("/godot/run/$RUN_THUNDER/state", "waiting")
        mirror.put("/godot/run/$RUN_THUNDER/remaining", 1.25)
        val waiting = row(RUN_THUNDER)
        assertTrue(waiting.counting)
        assertFalse(waiting.launched)
        mirror.put("/godot/run/$RUN_THUNDER/timbre", "210 0.6 0.45")
        assertEquals(Timbre(210.0, 0.6, 0.45), row(RUN_THUNDER).timbre)
        assertNull(Timbre.parse("210 0.6"))
        assertNull(Timbre.parse("a b c"))
    }

    @Test
    fun aMemberWhoseHolderIsGoneIsStillARow() {
        val rows = listOf(
            RunRow("A", "", "", "", "media", state = "playing", started = 20, parentRun = "GONE"),
            RunRow("B", "", "", "", "media", state = "armed", started = 0),
            RunRow("C", "", "", "", "media", state = "playing", started = 10),
        )
        assertEquals(listOf("C", "A", "B"), inShowOrder(rows).map { it.id })
    }

    @Test
    fun nothingRunning_noRows() {
        mirror.put("/godot/run/order", "")
        assertTrue(readRuns(mirror).isEmpty())
        assertTrue(NIGHT_BED.isNotEmpty())
    }
}
