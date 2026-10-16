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

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Script tab is in the row only while a Choufleur address is set, between Mapping and Connections. */
class TabsTest {

    @Test
    fun withNoAddressThereIsNoScriptTab() {
        assertEquals(
            listOf(RemoteTab.CUES, RemoteTab.RUNNING, RemoteTab.SURFACE, RemoteTab.MAPPING, RemoteTab.MACHINES),
            shownTabs(script = false)
        )
    }

    @Test
    fun withAnAddressScriptSitsBeforeConnections() {
        assertEquals(
            listOf(RemoteTab.CUES, RemoteTab.RUNNING, RemoteTab.SURFACE, RemoteTab.MAPPING, RemoteTab.SCRIPT, RemoteTab.MACHINES),
            shownTabs(script = true)
        )
    }

    @Test
    fun theScriptTabClearedFallsBackToConnections() {
        assertEquals(RemoteTab.SCRIPT, tabToShow(RemoteTab.SCRIPT, script = true))
        assertEquals(RemoteTab.MACHINES, tabToShow(RemoteTab.SCRIPT, script = false))
        assertEquals(RemoteTab.CUES, tabToShow(RemoteTab.CUES, script = false))
    }
}
