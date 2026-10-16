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

package com.pob31.godot.remote.ui

import com.pob31.godot.remote.buzzer.BuzzerPhase
import com.pob31.godot.remote.buzzer.BuzzerStatus
import com.pob31.godot.remote.buzzer.RememberedBuzzer
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.model.Said
import com.pob31.godot.remote.prompt.PageState
import org.junit.Assert.assertEquals
import org.junit.Test

/** The tab bar's connections: which are shown, in what order, and how each stands. */
class ConnectionMarksTest {

    private val buzzer = RememberedBuzzer("AA:BB", "CHF-1234")

    @Test
    fun theMachineOnScreenComesFirstThenTheScriptPageThenTheBuzzer() {
        val marks = connectionMarks(
            listOf("Backup FOH" to LinkState.STALE, "Main FOH" to LinkState.LIVE),
            onScreenIndex = 1,
            page = PageState.Shown("http://x"),
            buzzer = BuzzerStatus(buzzer, BuzzerPhase.LOST),
        )
        assertEquals(
            listOf(Said.verbatim("Main FOH"), Said.verbatim("Backup FOH"), Said("remote.connections.script"), Said("remote.connections.buzzer")),
            marks.map { it.name }
        )
        assertEquals(
            listOf(ConnectionHealth.GOOD, ConnectionHealth.BETWEEN, ConnectionHealth.GOOD, ConnectionHealth.BAD),
            marks.map { it.health }
        )
        assertEquals(listOf("remote.link.live", "remote.link.stale", "remote.connections.page.loaded", "buzzer.phase.lost"), marks.map { it.state.key })
    }

    @Test
    fun noAddressAndNoRememberedBuzzerShowNeither() {
        val marks = connectionMarks(listOf("Main" to LinkState.LIVE), 0, page = null, buzzer = BuzzerStatus())
        assertEquals(1, marks.size)
    }

    @Test
    fun withNoMachineLinkedTheBarStillSaysSo() {
        val marks = connectionMarks(emptyList(), null, page = null, buzzer = null)
        assertEquals(listOf(ConnectionMark(Said.verbatim("Go.dot"), ConnectionHealth.OFF, Said("remote.link.off"))), marks)
    }

    @Test
    fun everyStateHasItsHealth() {
        assertEquals(ConnectionHealth.BETWEEN, linkHealth(LinkState.CONNECTING))
        assertEquals(ConnectionHealth.BETWEEN, linkHealth(LinkState.CLOCK_STOPPED))
        assertEquals(ConnectionHealth.BAD, linkHealth(LinkState.GONE))
        assertEquals(ConnectionHealth.BAD, linkHealth(LinkState.TOO_OLD))
        assertEquals(ConnectionHealth.OFF, linkHealth(LinkState.OFF))
        assertEquals(ConnectionHealth.BETWEEN, pageHealth(PageState.Loading("a")))
        assertEquals(ConnectionHealth.BAD, pageHealth(PageState.Failed("a", "connect")))
        assertEquals(ConnectionHealth.OFF, pageHealth(PageState.Empty))
        assertEquals(ConnectionHealth.BETWEEN, buzzerHealth(BuzzerPhase.SEARCHING))
        assertEquals(ConnectionHealth.BAD, buzzerHealth(BuzzerPhase.BLUETOOTH_OFF))
        assertEquals(ConnectionHealth.GOOD, buzzerHealth(BuzzerPhase.LINKED))
    }
}
