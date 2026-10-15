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

import com.pob31.godot.remote.model.ShowFixture.ACT_ONE
import com.pob31.godot.remote.model.ShowFixture.AFTERMATH
import com.pob31.godot.remote.model.ShowFixture.CRICKETS
import com.pob31.godot.remote.model.ShowFixture.CUT
import com.pob31.godot.remote.model.ShowFixture.DRIPS
import com.pob31.godot.remote.model.ShowFixture.FOYER
import com.pob31.godot.remote.model.ShowFixture.INTERVAL
import com.pob31.godot.remote.model.ShowFixture.NIGHT_BED
import com.pob31.godot.remote.model.ShowFixture.PRE_ARM
import com.pob31.godot.remote.model.ShowFixture.RAIN
import com.pob31.godot.remote.model.ShowFixture.RAIN_DOWN
import com.pob31.godot.remote.model.ShowFixture.RELEASE
import com.pob31.godot.remote.model.ShowFixture.ROAD
import com.pob31.godot.remote.model.ShowFixture.SIREN
import com.pob31.godot.remote.model.ShowFixture.STORM
import com.pob31.godot.remote.model.ShowFixture.THUNDER
import com.pob31.godot.remote.model.ShowFixture.THUNDER_AND_RAIN
import com.pob31.godot.remote.model.ShowFixture.WIND
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cue list's rows: sections, folds kept on the tablet, the standby gutter, the time columns. */
class CueTreeTest {

    private val mirror = ShowFixture.mirror()
    private val folds = Folds()

    private fun rows() = buildCueTree(mirror, ACT_ONE, folds)

    private fun shape(rows: List<CueRow>) = rows.map { if (it.band) "[${it.name}]" else it.id }

    @Test
    fun aGroupsHeaderMembersAndFooter_eachSectionFramed() {
        assertEquals(
            listOf(
                THUNDER_AND_RAIN,
                STORM, "[header]", PRE_ARM, THUNDER, RAIN, AFTERMATH, DRIPS, SIREN, "[footer]", RELEASE,
                NIGHT_BED, CRICKETS, ROAD, WIND,
                RAIN_DOWN, INTERVAL, CUT,
            ),
            shape(rows())
        )
        val byId = rows().associateBy { it.id }
        assertEquals(0, byId.getValue(STORM).depth)
        assertEquals("a section's rows sit one in from its head", 2, byId.getValue(PRE_ARM).depth)
        assertEquals(1, byId.getValue("$STORM/header").depth)
        assertEquals(1, byId.getValue(THUNDER).depth)
        assertEquals(Section.HEADER, byId.getValue(PRE_ARM).section)
        assertEquals(Section.MEMBER, byId.getValue(THUNDER).section)
        assertEquals(2, byId.getValue(DRIPS).depth)
        assertEquals(Section.FOOTER, byId.getValue(RELEASE).section)
        assertTrue(byId.getValue(STORM).isGroup)
        assertEquals(3, byId.getValue(STORM).count)
        assertEquals("2.h", byId.getValue(PRE_ARM).number)
        assertEquals("Pre-arm the rig", byId.getValue(PRE_ARM).name)
        assertEquals(GodotIcon.TIMELINE, byId.getValue(AFTERMATH).icon)
        assertEquals(GodotIcon.FADE, byId.getValue(RAIN_DOWN).icon)
        assertFalse(byId.getValue(CUT).enabled)
        assertEquals("#3498db", byId.getValue(THUNDER_AND_RAIN).colour)
    }

    @Test
    fun theGutterParksOnTheCue_aHeadersLineOnItsGroup() {
        val byId = rows().associateBy { it.id }
        assertEquals(THUNDER, byId.getValue(THUNDER).parksOn)
        assertEquals(STORM, byId.getValue(STORM).parksOn)
        assertEquals("a header's line parks on its group", STORM, byId.getValue(PRE_ARM).parksOn)
        assertEquals(STORM, byId.getValue(RELEASE).parksOn)
        assertEquals("the head too", STORM, byId.getValue("$STORM/header").parksOn)
        assertFalse(byId.getValue(PRE_ARM).mayPark)

        // A persistent bed has no group to stand for it.
        mirror.put("/godot/list/$ACT_ONE/persistentOrder", INTERVAL)
        val bed = rows()
        assertEquals("[persistent]", shape(bed).first())
        assertEquals("", bed.first { it.id == INTERVAL }.parksOn)
        assertEquals("", bed.first().parksOn)
    }

    @Test
    fun foldsAreTheTablets() {
        folds.toggle(STORM)
        assertEquals(
            listOf(THUNDER_AND_RAIN, STORM, NIGHT_BED, CRICKETS, ROAD, WIND, RAIN_DOWN, INTERVAL, CUT),
            shape(rows())
        )
        assertTrue(rows().first { it.id == STORM }.shut)
        folds.toggle(STORM)
        folds.toggle("$STORM/header")
        val headerShut = shape(rows())
        assertTrue("[header]" in headerShut)
        assertFalse(PRE_ARM in headerShut)
        assertTrue(rows().first { it.id == "$STORM/header" }.shut)
    }

    @Test
    fun theShowsFoldIsTheStart_aToggleHereIsNotUndoneByIt() {
        mirror.put("/godot/cue/$NIGHT_BED/folded", true)
        assertFalse("folded by the show", CRICKETS in shape(rows()))
        folds.toggle(NIGHT_BED)
        assertTrue("opened here, the show's flag unmoved", CRICKETS in shape(rows()))
        mirror.put("/godot/cue/$NIGHT_BED/folded", false)
        mirror.put("/godot/cue/$NIGHT_BED/folded", true)
        // A flag that flipped and came back between two readings never moved.
        assertTrue(CRICKETS in shape(rows()))
        mirror.put("/godot/cue/$NIGHT_BED/folded", false)
        rows()
        mirror.put("/godot/cue/$NIGHT_BED/folded", true)
        // Now it moved - another client folded it - and is taken.
        assertFalse(CRICKETS in shape(rows()))
    }

    @Test
    fun theTimeColumns_oneDecimal_aReadOnlyNoughtBlank() {
        val byId = rows().associateBy { it.id }
        assertEquals("a media cue whose length is unknown says nothing", "", byId.getValue(THUNDER_AND_RAIN).duration)
        assertEquals("1.5", byId.getValue(THUNDER_AND_RAIN).postWait)
        assertEquals("a writable nought is a decision, and is shown", "0.0", byId.getValue(THUNDER_AND_RAIN).preWait)
        assertEquals("0.6", byId.getValue(RAIN).preWait)
        assertEquals("3.0", byId.getValue(RAIN_DOWN).duration)
        assertEquals("a memo has no duration", "", byId.getValue(INTERVAL).duration)

        mirror.put("/godot/cue/$THUNDER/duration", 40.0)
        mirror.put("/godot/cue/$THUNDER/rate", 2.0)
        assertEquals("a file at twice its speed lasts half as long", "20.0", rows().first { it.id == THUNDER }.duration)
        assertEquals("12.3", oneDecimal(12.34))
        assertEquals("-0.5", oneDecimal(-0.45))
    }

    @Test
    fun theMarksReadAtTickRate() {
        val marks = readMarks(mirror, ACT_ONE, rows())
        assertEquals(RAIN_DOWN, marks.standby)
        assertEquals(setOf(RAIN), marks.flagged)
        assertTrue(marks.overrides.isEmpty())
        mirror.put("/godot/cue/$CUT/override", "on")
        assertEquals(mapOf(CUT to "on"), readMarks(mirror, ACT_ONE, rows()).overrides)
    }

    @Test
    fun theListsAndTheOtherOne() {
        assertEquals(listOf(ListLine(ACT_ONE, "Act One"), ListLine(FOYER, "Foyer")), readLists(mirror))
        assertEquals(1, buildCueTree(mirror, FOYER, Folds()).size)
        assertTrue(buildCueTree(mirror, "NOSUCHLS", Folds()).isEmpty())
    }

    @Test
    fun theCacheRebuildsOnlyWhenTheShowMoves() {
        val cache = CueTreeCache()
        val first = cache.read(mirror, ACT_ONE, folds)
        assertSame("a GO, a flag, a tick: the same rows", first, cache.read(mirror, ACT_ONE, folds))
        mirror.put("/godot/cue/$THUNDER/name", "Big thunder")
        assertSame("a value without the revision moving is not an edit", first, cache.read(mirror, ACT_ONE, folds))
        mirror.put("/godot/document/revision", 3L)
        assertEquals("Big thunder", cache.read(mirror, ACT_ONE, folds).first { it.id == THUNDER }.name)
        folds.toggle(STORM)
        assertFalse(THUNDER in cache.read(mirror, ACT_ONE, folds).map { it.id })
    }

    @Test
    fun theListOnScreen_pickedHere_elseTheGoList_elseTheFirst() {
        val tables = ShowFixture.tables()
        fun read(viewed: String?, picked: String? = null) = readCues(mirror, viewed, picked, Folds(), CueTreeCache(), tables)
        assertEquals("no GO list said: the first", ACT_ONE, read(null).listId)
        mirror.put("/godot/list/focus", FOYER)
        assertEquals(FOYER, read(null).listId)
        assertEquals(FOYER, read(null).focus)
        assertEquals("looking is not focusing", ACT_ONE, read(ACT_ONE).listId)
        assertEquals("a list that went is not shown", FOYER, read("GONELIST").listId)
        assertEquals(THUNDER, read(ACT_ONE, THUNDER).inspection?.cue?.id)
        assertEquals(null, read(ACT_ONE).inspection)
        assertEquals("picking a cue moves no standby", RAIN_DOWN, read(ACT_ONE, THUNDER).marks.standby)
    }
}
