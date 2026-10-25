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
import com.pob31.godot.remote.model.ShowFixture.CRICKETS
import com.pob31.godot.remote.model.ShowFixture.INTERVAL
import com.pob31.godot.remote.model.ShowFixture.RUN_CRICKETS
import com.pob31.godot.remote.model.ShowFixture.RUN_THUNDER_AND_RAIN
import com.pob31.godot.remote.model.ShowFixture.THUNDER_AND_RAIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

/**
 * THE SURFACE TAB'S READING, from a real engine's tree: `fixtures/surfaces-snapshot.json`
 * is `wfg tree tests/fixtures/bundles/surfaces` (Go.dot's own fixture: a virtual panel
 * "Panel" and a Mackie "Desk" that is not connected, eight strips each, the first of each
 * on the DCA "Band"), pruned to the owners the tab reads, descriptions dropped. With no
 * audio nothing is armed, so a test that wants a strip in use says so with [put].
 */
class SurfacesTest {

    private val tables = ShowFixture.tables()

    private fun surfaces(): Mirror {
        val result = SnapshotReader.read(StringReader(Fixtures.text("fixtures/surfaces-snapshot.json")))
        return Mirror().also { it.install(result.state, result.tick) }
    }

    @Test
    fun theShowsSurfacesInItsOrder_thenThisTablet_eachSayingHowItStands() {
        val lines = readSurfaceLines(surfaces())
        assertEquals(listOf("SRFS0001", "SRFS0002", THIS_TABLET), lines.map { it.id })
        val (panel, desk, tablet) = lines
        assertEquals("Panel", panel.name)
        assertEquals("virtual", panel.profile)
        assertEquals("a virtual panel is always reached", SurfaceState.CONNECTED, panel.state)
        assertEquals("mcu", desk.profile)
        assertEquals(SurfaceState.NOT_CONNECTED, desk.state)
        assertEquals("surface.profile.mcu", desk.profileKey)
        assertEquals(8, desk.stripCount)
        assertEquals(SurfaceState.TABLET, tablet.state)
        // Off first: a surface switched off is off whatever its cables do (Surfaces.cpp stateWord).
        assertEquals(SurfaceState.OFF, desk.copy(enabled = false, connected = true).state)
        assertEquals(SurfaceState.PROBLEM, desk.copy(problem = "port P1 is missing").state)
    }

    @Test
    fun aSurfacesStripsInIndexOrder_theDcaStripRidingItsTrim_theRestFree() {
        val mirror = surfaces()
        val strips = readStrips(mirror, "SRFS0002", "")
        assertEquals((0..7).toList(), strips.map { it.index })
        val band = strips[0]
        assertTrue(band.isDca)
        assertEquals("Band", band.name)
        assertEquals("/godot/dca/SRFD0001/trim", band.target)
        assertEquals(0.0, band.level!!, 0.0)
        assertEquals("dca", band.word)
        assertFalse("a DCA strip aims nothing", band.selectable)
        val free = strips[1]
        assertTrue(free.empty)
        assertFalse("a free strip rides nothing: its fader cannot be taken", free.rides)
        assertNull(free.level)
        assertEquals("surface.word.free", free.wordKey)
        assertEquals("the other surface's strips are not these", 8, readStrips(mirror, "SRFS0001", "").size)
    }

    @Test
    fun whatIsOnAStripIsTheEnginesAnswer_theCueTheHolderItsMeterAndHeld() {
        val mirror = surfaces()
        mirror.put("/godot/slot/SRFT000B/cue", "SRF00003")
        mirror.put("/godot/slot/SRFT000B/holder", "RUNAAAAA")
        mirror.put("/godot/slot/SRFT000B/target", "/godot/run/RUNAAAAA/trim")
        mirror.put("/godot/slot/SRFT000B/word", "held")
        mirror.put("/godot/run/RUNAAAAA/trim", -4.5)
        mirror.put("/godot/run/RUNAAAAA/meter", -18.0)
        mirror.put("/godot/run/RUNAAAAA/held", true)
        val strip = readStrips(mirror, "SRFS0002", "SRF00003").first { it.id == "SRFT000B" }
        assertEquals("1.1", strip.number)
        assertEquals("Knock", strip.name)
        assertEquals(-4.5, strip.level!!, 0.0)
        assertEquals(-18.0, strip.meter!!, 0.0)
        assertTrue(strip.held)
        assertTrue("its cue is the one aimed at", strip.aimed)
        assertTrue("a sound's strip offers SELECT", strip.selectable)
        // An authored short name is what a strip shows (PRD §3.16).
        mirror.put("/godot/cue/SRF00003/shortName", "Knk")
        assertEquals("Knk", readStrips(mirror, "SRFS0002", "").first { it.id == "SRFT000B" }.name)
    }

    @Test
    fun thisTablet_aFaderForEveryDca_ridingItsTrim() {
        val mirror = surfaces()
        val strips = tabletStrips(mirror)
        assertEquals(1, strips.size)
        assertEquals("Band", strips[0].name)
        assertEquals("/godot/dca/SRFD0001/trim", strips[0].target)
        assertTrue(strips[0].isDca && strips[0].rides)
        val two = tabletStrips(ShowFixture.mirror())
        assertEquals(listOf("Band", "Effects"), two.map { it.name })
        assertEquals(-3.0, two[0].level!!, 0.0)
    }

    @Test
    fun theSurfaceShown_theOneAskedFor_elseTheFirstDeclared_elseTheTablet() {
        val mirror = surfaces()
        assertEquals("SRFS0001", readSurface(mirror, null, tables).shown.id)
        assertEquals("SRFS0002", readSurface(mirror, "SRFS0002", tables).shown.id)
        assertEquals("SRFS0001", readSurface(mirror, "SGONE001", tables).shown.id)
        val tablet = readSurface(mirror, THIS_TABLET, tables)
        assertTrue(tablet.shown.isTablet)
        assertEquals(listOf("$THIS_TABLET:SRFD0001"), tablet.strips.map { it.id })
        // A show that declares none still has the tablet's.
        val none = readSurface(ShowFixture.mirror(), null, tables)
        assertEquals(listOf(THIS_TABLET), none.surfaces.map { it.id })
        assertEquals(2, none.strips.size)
    }

    @Test
    fun theAimedCuesEqAndSends_andTheSoundsToAimAt() {
        val mirror = ShowFixture.mirror()
        assertNull(readSurface(mirror, null, tables).aimCue)
        mirror.put("/godot/run/$RUN_THUNDER_AND_RAIN/state", "playing")
        mirror.put("/godot/run/$RUN_CRICKETS/state", "playing")
        val choices = readSurface(mirror, null, tables).aimChoices.map { it.cue }
        assertTrue(THUNDER_AND_RAIN in choices && CRICKETS in choices)
        assertFalse("a memo is never aimed at", INTERVAL in choices)

        mirror.put("/godot/surface/aim", THUNDER_AND_RAIN)
        val reading = readSurface(mirror, null, tables)
        assertEquals("Thunder and rain", reading.aimCue!!.name)
        assertEquals(16, reading.eq!!.knobs.size)
        assertEquals(tables.eqControls.map { it.row }, reading.eq!!.knobs.map { it.control.row })
        assertEquals(listOf("Reverb", "Foldback"), reading.sends.map { it.busName })
    }

    @Test
    fun theSendPagesKnobs_onTheFadersLaw_aPressSwitchingTheSend() {
        val sends = readSends(ShowFixture.mirror(), THUNDER_AND_RAIN)
        val page = sendKnobs(sends, tables.fader)
        assertNull("a send page has no EQ switch to dim it all", page.on)
        assertEquals(sends.map { it.levelAddress }, page.knobs.map { it.address })
        for ((knob, send) in page.knobs.zip(sends)) {
            assertEquals(EqLaw.LEVEL, knob.control.law)
            assertEquals(EqPress.TOGGLE_SWITCH, knob.control.press)
            assertEquals(send.on, knob.bandOn)
            val switch = page.switches.first { it.row == knob.control.switchRow }
            assertEquals(send.onAddress, switch.address)
        }
        // Foldback is switched off in the fixture: its knob is dimmed and says so.
        assertFalse(page.knobs[1].bandOn)
        assertEquals("-12.0 dB", EqLaws.valueText(EqLaw.LEVEL, page.knobs[0].value, tables.fader.silenceDb))
    }

    @Test
    fun aSendsPlace_theRowsItsPlacementSaysAreHeard() {
        val mirror = ShowFixture.mirror()
        val (reverb, foldback) = readSends(mirror, THUNDER_AND_RAIN)
        mirror.put(reverb.address("placement"), "stereo")
        mirror.put(reverb.address("balance"), -0.3)
        mirror.put(reverb.address("width"), 0.5)
        mirror.put(reverb.address("swap"), true)
        mirror.put(foldback.address("placement"), "pan")
        mirror.put(foldback.address("pan"), 1.0)
        val place = placeKnobs(readSends(mirror, THUNDER_AND_RAIN))

        // A lean a send: a stereo send's balance, a mono one's pan, pressed back to the centre.
        assertEquals(listOf(reverb.address("balance"), foldback.address("pan")), place.leans.knobs.map { it.address })
        for (knob in place.leans.knobs) {
            assertEquals(EqLaw.PAN, knob.control.law)
            assertEquals(EqPress.CENTRE, knob.control.press)
        }
        assertEquals(listOf("Reverb", "Foldback"), place.leans.knobs.map { it.control.label })
        assertEquals("L 30", EqLaws.valueText(EqLaw.PAN, place.leans.knobs[0].value))
        assertEquals("R 100", EqLaws.valueText(EqLaw.PAN, place.leans.knobs[1].value))

        // A width for the stereo send alone, its press the swap.
        val width = place.widths.knobs.single()
        assertEquals(reverb.address("width"), width.address)
        assertEquals(EqLaw.STEREO_WIDTH, width.control.law)
        assertEquals(EqPress.TOGGLE_SWITCH, width.control.press)
        val swap = place.widths.switches.single { it.row == width.control.switchRow }
        assertEquals(reverb.address("swap"), swap.address)
        assertTrue(swap.on)
        assertEquals("50 %", EqLaws.valueText(EqLaw.STEREO_WIDTH, width.value))

        // Folded to mono: a balance and no width. No place: nothing.
        mirror.put(reverb.address("placement"), "fold")
        mirror.put(foldback.address("placement"), "none")
        val folded = placeKnobs(readSends(mirror, THUNDER_AND_RAIN))
        assertEquals(listOf(reverb.address("balance")), folded.leans.knobs.map { it.address })
        assertTrue(folded.widths.knobs.isEmpty())
        mirror.put(reverb.address("placement"), "none")
        assertTrue(placeKnobs(readSends(mirror, THUNDER_AND_RAIN)).isEmpty)

        // One send drawn alone is named by its rows.
        mirror.put(reverb.address("placement"), "stereo")
        val alone = placeKnobs(readSends(mirror, THUNDER_AND_RAIN).take(1), mapOf("balance" to "Balance", "width" to "Width"))
        assertEquals("Balance", alone.leans.knobs.single().control.label)
        assertEquals("Width", alone.widths.knobs.single().control.label)
    }

    @Test
    fun aSendIntoAnAux_isNamedByTheAuxsSlot() {
        val mirror = ShowFixture.mirror()
        val reverb = readSends(mirror, THUNDER_AND_RAIN).first()
        mirror.put(reverb.address("bus"), "AUX00001")
        mirror.put("/godot/slot/AUX00001/name", "Plate")
        assertEquals("Plate", readSends(mirror, THUNDER_AND_RAIN).first().busName)
        mirror.put("/godot/slot/AUX00001/name", "")
        assertEquals("AUX00001", readSends(mirror, THUNDER_AND_RAIN).first().busName)
    }

    @Test
    fun whatAHandOnTheTabSends_everyCommandGoDots() {
        val signatures = Fixtures.text("fixtures/commands.txt").lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line -> line.substringBefore(' ') to line }
        val sound = StripLine("SRFT000B", 1, "sampler", cue = "SRF00003", cueKind = "media", target = "/godot/run/R/trim")
        val dca = StripLine("SRFT0001", 0, "dca", dca = "SRFD0001", target = "/godot/dca/SRFD0001/trim")
        val free = StripLine("SRFT0002", 1, "sampler")
        val video = sound.copy(cueKind = "video")

        assertEquals(Sent("surface.aim", "SRF00003"), StripActions.select(sound))
        assertNull("surface.aim refuses anything but a sound", StripActions.select(video))
        assertNull(StripActions.select(dca))
        assertNull(StripActions.select(free))
        assertEquals(Sent("strip.press", "SRFT000B", "127"), StripActions.press(sound, 127))
        assertEquals(Sent("strip.press", "SRFT000B", "1"), StripActions.press(sound, 0))
        assertEquals(Sent("strip.release", "SRFT000B"), StripActions.release(sound))
        assertNull("the engine refuses strip.press on a DCA strip", StripActions.press(dca, 100))
        assertNull(StripActions.press(free, 100))
        assertEquals("/godot/dca/SRFD0001/trim" to "0", StripActions.dcaUnity(dca))
        assertNull(StripActions.dcaUnity(sound))
        assertEquals(127, StripActions.velocityAt(1.0))
        assertEquals(1, StripActions.velocityAt(0.0))
        assertEquals(64, StripActions.velocityAt(0.5))

        for (sent in listOf(StripActions.select(sound)!!, StripActions.press(sound, 90)!!, StripActions.release(sound)!!, StripActions.aim("X"))) {
            assertNotNull("${sent.command} is no command of Go.dot's", signatures[sent.command])
        }
        for (structure in listOf("surface.create", "strip.create")) {
            assertFalse(listOf(StripActions.select(sound), StripActions.press(sound, 1), StripActions.release(sound)).any { it?.command == structure })
        }
    }
}
