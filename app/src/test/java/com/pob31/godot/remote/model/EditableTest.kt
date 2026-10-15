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
import com.pob31.godot.remote.model.ShowFixture.INTERVAL
import com.pob31.godot.remote.model.ShowFixture.RAIN
import com.pob31.godot.remote.model.ShowFixture.RAIN_DOWN
import com.pob31.godot.remote.model.ShowFixture.RUN_THUNDER
import com.pob31.godot.remote.model.ShowFixture.STORM
import com.pob31.godot.remote.model.ShowFixture.THUNDER
import com.pob31.godot.remote.model.ShowFixture.THUNDER_AND_RAIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The allow-list held to Go.dot's parameter table and command list, and the inspector read off a real tree. */
class EditableTest {

    private val tables = ShowFixture.tables()
    private val mirror = ShowFixture.mirror()

    /** owner -> row -> (access, persist), from the parameter table's rows the remote reads. */
    private val parameters: Map<String, Map<String, Pair<String, String>>> by lazy {
        val out = HashMap<String, HashMap<String, Pair<String, String>>>()
        Fixtures.text("fixtures/parameter-rows.csv").lines()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("owner,") }
            .forEach { line ->
                val (owner, address, _, access, persist) = line.split(',')
                out.getOrPut(owner) { HashMap() }[address] = access to persist
            }
        out
    }

    /** The owners whose rows sit under `/godot/cue/<id>/`. */
    private val cueOwners = listOf("cue", "sound", "media", "fade", "group", "video", "mic")

    private fun rowOf(owner: String, row: String): Pair<String, String>? =
        if (owner == "cue") cueOwners.firstNotNullOfOrNull { parameters[it]?.get(row) } else parameters[owner]?.get(row)

    @Test
    fun everyRowTheTabletWritesIsAWritableRowOfGoDots() {
        val rows = Editables.writableRows(tables)
        assertTrue(rows.size > 30)
        for ((owner, row) in rows) {
            val found = rowOf(owner, row)
            assertNotNull("$owner/$row is no row of Go.dot's", found)
            assertTrue("$owner/$row is read-only in Go.dot", found!!.first.contains('w'))
        }
    }

    @Test
    fun neverTheShapeOfTheCueList() {
        val rows = Editables.writableRows(tables).map { it.second }.toSet()
        for (structure in listOf("name", "number", "shortName", "order", "parent", "index", "kind", "headerOrder", "footerOrder", "mode")) {
            assertFalse("the tablet would write $structure", structure in rows)
        }
    }

    @Test
    fun theLockRuleIsTheTables_storedRowsRefused_ridesAndTonightsLetThrough() {
        for (each in Editables.ALL) {
            val persist = when (each.control) {
                EditControl.RUN_TRIM -> rowOf("run", "trim")!!.second
                EditControl.SENDS -> rowOf("send", "level")!!.second
                EditControl.EQ -> rowOf("cue", "eqOn")!!.second
                else -> rowOf("cue", each.row)!!.second
            }
            when (each.underLock) {
                UnderLock.REFUSED -> assertEquals("${each.row} is refused under the lock: the show records it", "show", persist)
                UnderLock.NOT_STORED -> assertTrue("${each.row} is let through: never in show.xml", persist != "show")
                UnderLock.RIDES_LIVE -> assertEquals("${each.row} rides live over a show row (LiveEdits.h)", "show", persist)
            }
        }
        assertEquals("none", rowOf("dca", Editables.DCA_TRIM)!!.second)
    }

    @Test
    fun everyCommandTheTabsSendIsGoDots_withItsArguments() {
        val signatures = Fixtures.text("fixtures/commands.txt").lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line -> line.substringBefore(' ') to line }
        fun holds(sent: Sent) {
            val signature = signatures[sent.command]
            assertNotNull("${sent.command} is no command of Go.dot's", signature)
            val required = Regex("<[^>]+>").findAll(signature!!).count()
            val optional = Regex("\\[[^]]+]").findAll(signature).count()
            val variadic = signature.contains("...]")
            assertTrue("${sent.command} takes at least $required arguments: $sent", sent.args.size >= required)
            assertTrue("${sent.command} takes at most ${required + optional}: $sent", variadic || sent.args.size <= required + optional)
        }
        val row = readRuns(mirror).first { it.id == RUN_THUNDER }
        val bed = readRuns(mirror).first { it.kind == "group" && it.pills.isNotEmpty() }
        StopVerb.entries.forEach { holds(RunActions.stop(bed, it)) }
        holds(RunActions.kill(row)!!)
        holds(RunActions.seek(row, 1.5))
        holds(RunActions.pillTap(bed, bed.pills[1])!!)
        holds(RunActions.pillTap(bed, bed.pills[2])!!)
        holds(RunActions.pillHold(bed, bed.pills[0])!!)
        mirror.put("/godot/run/$RUN_THUNDER/range", 0.0)
        mirror.put("/godot/run/$RUN_THUNDER/strip", "STRP0001")
        val ranged = readRuns(mirror).first { it.id == RUN_THUNDER }
        holds(RunActions.advance(ranged)!!)
        holds(RunActions.solo(ranged)!!)
        for (command in Editables.COMMANDS) assertNotNull(command, signatures[command])
        holds(CueActions.park("X"))
        holds(CueActions.fire("X"))
        holds(CueActions.focus("X"))
        for (structure in listOf("cue.create", "object.delete", "object.move", "cue.renumber")) {
            assertFalse("$structure is sent from the tablet", structure in Editables.COMMANDS)
        }
    }

    @Test
    fun theInspectorsRowsInTheAllowListsOrder() {
        val inspection = readInspection(mirror, THUNDER_AND_RAIN, tables)!!
        assertEquals(
            listOf("level", "trim", "sends", "eq", "dca", "preWait", "postWait", "rate", "rateMode", "enabled", "flagged", "notes"),
            inspection.lines.map { it.editable.row }
        )
        assertFalse(inspection.locked)
        val level = inspection.lines[0] as InspectorLine.Field
        assertEquals(-6.0, level.field.number!!, 0.0)
        assertEquals(-120.0, level.field.min!!, 0.0)
        assertEquals(12.0, level.field.max!!, 0.0)
        assertEquals("dB", level.field.unit)
        assertTrue(level.field.editable)

        val sends = (inspection.lines[2] as InspectorLine.Sends).sends
        assertEquals(listOf("Reverb" to -12.0, "Foldback" to -20.0), sends.map { it.busName to it.level })
        assertEquals(listOf(true, false), sends.map { it.on })
        assertEquals("/godot/send/S1000001/level", sends[0].levelAddress)

        val eq = (inspection.lines[3] as InspectorLine.Eq).eq
        assertEquals(true, eq.on?.on)
        assertEquals(16, eq.knobs.size)
        val hp = eq.knobs.first { it.control.row == "eqHpfFreq" }
        assertEquals(120.0, hp.value, 0.0)
        assertTrue("the high-pass is in: its switch is on", hp.bandOn)
        assertEquals(4.0, eq.knobs.first { it.control.row == "eqB3Gain" }.value, 0.0)
        assertEquals("peak", eq.knobs.first { it.control.row == "eqB1Shape" }.shape)
        assertFalse("the low-pass is out", eq.knobs.first { it.control.row == "eqLpfFreq" }.bandOn)

        val dca = inspection.lines[4] as InspectorLine.Dca
        assertEquals("D1000001", dca.current)
        assertEquals(listOf(DcaChoice("D1000001", "Band"), DcaChoice("D2000001", "Effects")), dca.choices)
        assertEquals(-3.0, dca.trim!!, 0.0)
        assertEquals("/godot/dca/D1000001/trim", dca.trimAddress)

        val notes = inspection.lines.last() as InspectorLine.Field
        assertEquals("Wait for the actor to reach the door", notes.field.text)
        assertEquals(listOf("varispeed", "timestretch"), (inspection.lines[8] as InspectorLine.Field).field.choices)
    }

    @Test
    fun aPlayingCueHasItsRunsTrim_aMemoHasNoLevel_aFadeNoSpeed() {
        val thunder = readInspection(mirror, THUNDER, tables)!!
        val trim = thunder.lines.first { it is InspectorLine.RunTrim } as InspectorLine.RunTrim
        assertEquals(RUN_THUNDER, trim.runId)
        assertEquals(-4.5, trim.trim, 0.0)
        assertEquals("/godot/run/$RUN_THUNDER/trim", trim.address)

        assertEquals(
            listOf("preWait", "postWait", "enabled", "flagged", "notes"),
            readInspection(mirror, INTERVAL, tables)!!.lines.map { it.editable.row }
        )
        val fade = readInspection(mirror, RAIN_DOWN, tables)!!.lines.map { it.editable.row }
        assertTrue("a fade's target level is a level", "level" in fade)
        assertFalse("a fade's rate is not a speed to ride here", "rate" in fade)
        assertTrue("a group's DCA", "dca" in readInspection(mirror, STORM, tables)!!.lines.map { it.editable.row })
        assertNull(readInspection(mirror, "NOSUCHCU", tables))
        // A run the horizon only made ready, not let go, has no trim to ride yet.
        mirror.put("/godot/run/$RUN_THUNDER/started", 0L)
        assertFalse(readInspection(mirror, THUNDER, tables)!!.lines.any { it is InspectorLine.RunTrim })
    }

    @Test
    fun underTheLock_theShowsRowsGrey_EqSendsFlagsAndTrimsDoNot() {
        mirror.put("/godot/document/locked", true)
        mirror.put("/godot/document/live", 2L)
        mirror.put("/godot/cue/$THUNDER_AND_RAIN/live", "eqB2Gain")
        mirror.put("/godot/send/S1000001/live", true)
        val inspection = readInspection(mirror, THUNDER_AND_RAIN, tables)!!
        assertTrue(inspection.locked)
        assertEquals(2, inspection.liveCount)
        val byRow = inspection.lines.associateBy { it.editable.row }
        fun field(row: String) = when (val line = byRow.getValue(row)) {
            is InspectorLine.Field -> line.field
            is InspectorLine.Enabled -> line.field
            is InspectorLine.Dca -> line.field
            else -> error("no field: $row")
        }
        for (row in listOf("level", "preWait", "postWait", "rate", "rateMode", "enabled", "notes", "dca")) {
            assertTrue("$row is greyed under the lock", field(row).lockedOut)
            assertFalse(field(row).editable)
        }
        assertFalse("a flag is state, let through", field("flagged").lockedOut)
        assertFalse((byRow.getValue("sends") as InspectorLine.Sends).lockedOut)
        assertFalse((byRow.getValue("eq") as InspectorLine.Eq).lockedOut)
        val eq = (byRow.getValue("eq") as InspectorLine.Eq).eq
        assertTrue(eq.knobs.first { it.control.row == "eqB2Gain" }.live)
        assertFalse(eq.knobs.first { it.control.row == "eqB3Gain" }.live)
        assertTrue((byRow.getValue("sends") as InspectorLine.Sends).sends[0].live)
        assertNotNull("the DCA's trim is tonight's, beside the greyed mark", (byRow.getValue("dca") as InspectorLine.Dca).trimAddress)
        // A run's trim is tonight's.
        assertTrue(readInspection(mirror, THUNDER, tables)!!.lines.any { it is InspectorLine.RunTrim })
        assertEquals(RAIN, readMarks(mirror, ShowFixture.ACT_ONE, buildCueTree(mirror, ShowFixture.ACT_ONE, Folds())).flagged.single())
    }

    @Test
    fun theOverrideIsShown_neverWritten() {
        mirror.put("/godot/cue/$INTERVAL/override", "off")
        val enabled = readInspection(mirror, INTERVAL, tables)!!.lines.first { it is InspectorLine.Enabled } as InspectorLine.Enabled
        assertEquals("off", enabled.override)
        assertFalse("override" in Editables.writableRows(tables).map { it.second })
    }
}
