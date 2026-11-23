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

import com.pob31.godot.remote.model.ShowFixture.THUNDER_AND_RAIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A SOUND'S OWN DYNAMICS ON THE TABLET (Go.dot's namespace draft §141): the Dynamics page's
 * fourteen controls as the tables carry them, the three laws they add as SurfacePages.cpp
 * turns and says them, the inspector's Dynamics block and its Key list, the Surface tab's
 * Dynamics page.
 */
class DynTest {

    private val tables = ShowFixture.tables()
    private val mirror = ShowFixture.mirror()

    // ------------------------------------------------------------------------- the tables

    @Test
    fun fourteenControlsInTheEnginesOrder_eachWithItsLawAndItsSwitch() {
        assertEquals(
            listOf(
                "dynThreshold", "dynRatio", "dynAttack", "dynRelease", "dynKnee", "dynMakeup",
                "dynGateThreshold", "dynGateRange", "dynGateAttack", "dynGateHold", "dynGateRelease",
                "dynKeyLowCut", "dynKeyHighCut", "dynDetector",
            ),
            tables.dynControls.map { it.row }
        )
        val threshold = tables.control("dynThreshold")!!
        assertEquals(EqLaw.DECIBELS, threshold.law)
        assertEquals(-60.0, threshold.min, 0.0)
        assertEquals("dynCompOn", threshold.switchRow)
        assertEquals(EqPress.TOGGLE_SWITCH, threshold.press)
        assertEquals(EqLaw.RATIO, tables.control("dynRatio")!!.law)
        assertEquals(EqLaw.TIME, tables.control("dynGateHold")!!.law)
        assertEquals(0.0, tables.control("dynGateHold")!!.min, 0.0)
        assertEquals(EqLaw.GAIN, tables.control("dynMakeup")!!.law)
        assertEquals("dynGateOn", tables.control("dynGateRange")!!.switchRow)
        assertEquals("dynOn", tables.control("dynKeyLowCut")!!.switchRow)
        val detector = tables.control("dynDetector")!!
        assertEquals(EqLaw.SHAPE, detector.law)
        assertEquals("rms", detector.shelf)
        assertEquals(EqPress.TOGGLE_SHAPE, detector.press)
        assertEquals(0.125, tables.detent.timeDoublingsPerDetent, 0.0)
        assertEquals(0.125, tables.detent.ratioDoublingsPerDetent, 0.0)
    }

    // ------------------------------------------------------------------------- the laws

    @Test
    fun aThresholdIsLinear_inTenths_saidWithNoPlusSign() {
        assertEquals(0.0, EqLaws.place(EqLaw.DECIBELS, -60.0, -60.0, 0.0), 0.0)
        assertEquals(0.5, EqLaws.place(EqLaw.DECIBELS, -30.0, -60.0, 0.0), 1e-12)
        assertEquals(-15.0, EqLaws.valueAt(EqLaw.DECIBELS, 0.75, -60.0, 0.0), 1e-12)
        assertEquals(-19.5, EqLaws.grained(EqLaw.DECIBELS, -19.45, -60.0, 0.0), 1e-12)
        // SurfacePages.cpp valueText(Law::decibels): "6.0 dB", "-20.0 dB" - no sign of its own.
        assertEquals("6.0 dB", EqLaws.valueText(EqLaw.DECIBELS, 6.0))
        assertEquals("-20.0 dB", EqLaws.valueText(EqLaw.DECIBELS, -20.0))
    }

    @Test
    fun aRatioIsLogarithmic_aTenthUnderTen_wholeAbove() {
        assertEquals(10.0, EqLaws.valueAt(EqLaw.RATIO, 0.5, 1.0, 100.0), 1e-9)
        assertEquals(0.5, EqLaws.place(EqLaw.RATIO, 10.0, 1.0, 100.0), 1e-12)
        assertEquals(4.1, EqLaws.grained(EqLaw.RATIO, 4.06, 1.0, 100.0), 1e-12)
        assertEquals(21.0, EqLaws.grained(EqLaw.RATIO, 20.6, 1.0, 100.0), 1e-12)
        assertEquals("4.0:1", EqLaws.valueText(EqLaw.RATIO, 4.0))
        assertEquals("20:1", EqLaws.valueText(EqLaw.RATIO, 20.0))
    }

    @Test
    fun aTimeIsLogarithmic_fromAMillisecondWhereItsRangeStartsAtNought() {
        // An attack, 0.05..200 ms: its middle the geometric mean.
        assertEquals(Math.sqrt(0.05 * 200.0), EqLaws.valueAt(EqLaw.TIME, 0.5, 0.05, 200.0), 1e-9)
        // A hold, 0..500 ms: nought at the very start, a millisecond just after, 500 at the end.
        assertEquals(0.0, EqLaws.valueAt(EqLaw.TIME, 0.0, 0.0, 500.0), 0.0)
        assertEquals(1.0, EqLaws.valueAt(EqLaw.TIME, 1e-12, 0.0, 500.0), 1e-6)
        assertEquals(500.0, EqLaws.valueAt(EqLaw.TIME, 1.0, 0.0, 500.0), 1e-9)
        assertEquals(0.0, EqLaws.place(EqLaw.TIME, 0.0, 0.0, 500.0), 0.0)
        // Under the floor, nought where the range allows it.
        assertEquals(0.0, EqLaws.grained(EqLaw.TIME, 0.6, 0.0, 500.0), 0.0)
        // The grain: a hundredth under one, a tenth under a hundred, whole above.
        assertEquals(0.37, EqLaws.grained(EqLaw.TIME, 0.374, 0.05, 200.0), 1e-12)
        assertEquals(12.3, EqLaws.grained(EqLaw.TIME, 12.34, 0.05, 200.0), 1e-12)
        assertEquals(151.0, EqLaws.grained(EqLaw.TIME, 150.6, 5.0, 2000.0), 1e-12)
        // SurfacePages.cpp valueText(Law::time).
        assertEquals("10.0 ms", EqLaws.valueText(EqLaw.TIME, 10.0))
        assertEquals("150 ms", EqLaws.valueText(EqLaw.TIME, 150.0))
        assertEquals("1.20 s", EqLaws.valueText(EqLaw.TIME, 1200.0))
        assertEquals("0.50 ms", EqLaws.valueText(EqLaw.TIME, 0.5))
    }

    @Test
    fun theDetectorIsAChoiceOfTwo_peakAndRms() {
        assertEquals("rms", EqLaws.pressedShape("peak", "rms"))
        assertEquals("peak", EqLaws.pressedShape("rms", "rms"))
        assertEquals("RMS", EqLaws.shapeText("rms"))
        assertEquals("Peak", EqLaws.shapeText("peak"))
    }

    // ------------------------------------------------------------------------- the reading

    private fun giveTheCueItsStage(cue: String = THUNDER_AND_RAIN) {
        val base = "/godot/cue/$cue/"
        mirror.put(base + "dynOn", true)
        mirror.put(base + "dynCompOn", true)
        mirror.put(base + "dynGateOn", false)
        mirror.put(base + "dynDetector", "rms")
        mirror.put(base + "dynKey", "")
        for (control in tables.dynControls) {
            if (control.law == EqLaw.SHAPE) continue
            mirror.put(base + control.row, if (control.row == "dynThreshold") -30.0 else control.min)
        }
        mirror.put("/godot/input/KN000001/name", "Talkback")
        mirror.put("/godot/input/KN000001/firstChannel", 0L)
        mirror.put("/godot/slot/AX000001/kind", "rackChannel")
        mirror.put("/godot/slot/AX000001/access", "shared")
        mirror.put("/godot/slot/AX000001/name", "Duck bus")
        mirror.put("/godot/bus/BS000001/name", "Main L/R")
        mirror.put("/godot/bus/BS000001/firstChannel", 0L)
    }

    @Test
    fun theStageIsTheInspectorsRowAfterTheEq_itsSwitchesKnobsAndKey() {
        giveTheCueItsStage()
        val inspection = readInspection(mirror, THUNDER_AND_RAIN, tables)!!
        val rows = inspection.lines.map { it.editable.row }
        assertEquals(rows.indexOf("eq") + 1, rows.indexOf("dyn"))

        val dyn = (inspection.lines.first { it is InspectorLine.Dyn } as InspectorLine.Dyn).dyn
        assertEquals(listOf("dynOn", "dynCompOn", "dynGateOn"), dyn.eq.switches.map { it.row })
        assertEquals(true, dyn.eq.on?.on)
        assertEquals(14, dyn.eq.knobs.size)
        assertEquals(-30.0, dyn.eq.knobs.first { it.control.row == "dynThreshold" }.value, 0.0)
        assertEquals("rms", dyn.eq.knobs.first { it.control.row == "dynDetector" }.shape)
        assertTrue("the compressor is in", dyn.eq.knobs.first { it.control.row == "dynRatio" }.bandOn)
        assertFalse("the gate is out", dyn.eq.knobs.first { it.control.row == "dynGateRange" }.bandOn)
        assertEquals("/godot/cue/$THUNDER_AND_RAIN/dynKey", dyn.keyAddress)

        // The Key list as Go.dot's menu has it: before, after, the input, the aux, the output.
        assertEquals(
            listOf(KeyChoice.Kind.BEFORE, KeyChoice.Kind.AFTER, KeyChoice.Kind.INPUT, KeyChoice.Kind.AUX, KeyChoice.Kind.OUTPUT),
            dyn.keyChoices.map { it.kind }.distinct().take(5)
        )
        assertTrue(dyn.keyChoices.any { it.value == "KN000001" && it.name == "Talkback" })
        assertTrue(dyn.keyChoices.any { it.value == "AX000001" && it.name == "Duck bus" })
        assertTrue(dyn.keyChoices.any { it.value == "BS000001" && it.kind == KeyChoice.Kind.OUTPUT })
        assertEquals(KeyChoice.Kind.BEFORE, dyn.keyChoice.kind)

        // A key naming what the show has not got is kept and said, never dropped.
        mirror.put("/godot/cue/$THUNDER_AND_RAIN/dynKey", "GONE0001")
        val gone = (readInspection(mirror, THUNDER_AND_RAIN, tables)!!.lines.first { it is InspectorLine.Dyn } as InspectorLine.Dyn).dyn
        assertEquals(KeyChoice.Kind.GONE, gone.keyChoice.kind)
        assertEquals("GONE0001", gone.keyChoice.value)
    }

    @Test
    fun anAuxIsNotOnItsOwnKeyList() {
        giveTheCueItsStage()
        assertFalse(keyChoices(mirror, "AX000001").any { it.value == "AX000001" })
    }

    @Test
    fun underTheLockTheStageRidesLive() {
        giveTheCueItsStage()
        mirror.put("/godot/document/locked", true)
        mirror.put("/godot/cue/$THUNDER_AND_RAIN/live", "dynThreshold dynKey eqB2Gain")
        val line = readInspection(mirror, THUNDER_AND_RAIN, tables)!!.lines.first { it is InspectorLine.Dyn } as InspectorLine.Dyn
        assertFalse("the stage rides live, never greyed (BIE)", line.lockedOut)
        assertTrue(line.dyn.eq.knobs.first { it.control.row == "dynThreshold" }.live)
        assertFalse(line.dyn.eq.knobs.first { it.control.row == "dynRatio" }.live)
        assertTrue(line.dyn.keyLive)
    }

    @Test
    fun everyDynamicsRowTheTabletWritesIsInTheAllowList() {
        val rows = Editables.writableRows(tables).filter { it.first == "cue" }.map { it.second }.toSet()
        for (row in Editables.DYN_SWITCHES + Editables.DYN_KEY + tables.dynControls.map { it.row }) {
            assertTrue("$row is written by the tablet", row in rows)
        }
    }

    @Test
    fun aCueWithNoStageHasNoDynamicsRow() {
        assertNull(readInspection(mirror, ShowFixture.INTERVAL, tables)!!.lines.firstOrNull { it is InspectorLine.Dyn })
    }

    // ------------------------------------------------------------------------- the Surface tab

    @Test
    fun theSurfaceTabsDynamicsPageReadsTheAimedCuesStage() {
        giveTheCueItsStage()
        mirror.put("/godot/surface/aim", THUNDER_AND_RAIN)
        val reading = readSurface(mirror, null, tables)
        val dyn = reading.dyn
        assertNotNull(dyn)
        assertEquals(14, dyn!!.eq.knobs.size)
        assertEquals(listOf(SurfacePage.FADERS, SurfacePage.EQ, SurfacePage.DYN, SurfacePage.SENDS), SurfacePage.entries.toList())
        mirror.put("/godot/surface/aim", ShowFixture.INTERVAL)
        assertNull(readSurface(mirror, null, tables).dyn)
    }
}
