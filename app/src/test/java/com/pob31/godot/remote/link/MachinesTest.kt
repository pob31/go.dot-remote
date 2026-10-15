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

package com.pob31.godot.remote.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The remembered machines: kept and read back, edited, and which one is on screen. */
class MachinesTest {

    private val main = Machine("Main", "192.168.1.10", 5010, linked = true)
    private val backup = Machine("Backup", "192.168.1.11", 5010, linked = true)
    private val spare = Machine("", "10.0.0.7", 6010, linked = false)

    @Test
    fun machinesRoundTripThroughTheStore() {
        val list = listOf(main, backup, spare)
        assertEquals(list, MachineStore.decode(MachineStore.encode(list)))
        assertEquals(emptyList<Machine>(), MachineStore.decode(""))
    }

    @Test
    fun aTabOrALineBreakInANameCannotSplitARecord() {
        val awkward = Machine("Stage\tleft\nwing", "192.168.1.12", 5010, linked = true)
        val back = MachineStore.decode(MachineStore.encode(listOf(awkward, main)))
        assertEquals(listOf(awkward.copy(name = "Stage left wing"), main), back)
    }

    @Test
    fun aRecordWithoutAPortReadsTheDefault() {
        assertEquals(listOf(Machine("Old", "10.0.0.1", Machine.DEFAULT_PORT, false)), MachineStore.decode("Old\t10.0.0.1\tnot-a-port"))
    }

    @Test
    fun aMachineWithoutANameIsLabelledByItsAddress() {
        assertEquals("10.0.0.7", spare.label)
        assertEquals("10.0.0.7:6010", spare.key)
    }

    @Test
    fun putAddsAtTheEndAndRenamesInPlace() {
        val list = MachineList.put(listOf(main, backup), spare)!!
        assertEquals(listOf(main, backup, spare), list)
        val renamed = MachineList.put(list, backup.copy(name = "Backup FOH"))!!
        assertEquals(listOf(main, backup.copy(name = "Backup FOH"), spare), renamed)
    }

    @Test
    fun anEditMayMoveAMachineToAFreeAddressButNotOntoAnother() {
        val list = listOf(main, backup)
        val moved = MachineList.put(list, backup.copy(host = "192.168.1.20"), replacing = backup.key)!!
        assertEquals(listOf(main, backup.copy(host = "192.168.1.20")), moved)
        assertNull(MachineList.put(list, backup.copy(host = main.host), replacing = backup.key))
    }

    @Test
    fun removeAndLink() {
        val list = listOf(main, backup, spare)
        assertEquals(listOf(main, spare), MachineList.remove(list, backup.key))
        assertEquals(listOf(main, backup, spare.copy(linked = true)), MachineList.setLinked(list, spare.key, true))
    }

    // ------------------------------------------------------------------ on screen

    private val keys = listOf(main.key, backup.key)

    @Test
    fun theOperatorsChoiceHoldsWhileItIsLinked() {
        assertEquals(backup.key, chooseOnScreen(backup.key, main.key, keys) { it == main.key })
    }

    @Test
    fun byDefaultTheMachineThatSendsIsOnScreen() {
        // The backup leads (it took over); the main follows.
        assertEquals(backup.key, chooseOnScreen(null, null, keys) { it == backup.key })
        assertEquals(main.key, chooseOnScreen(null, null, keys) { it == main.key })
    }

    @Test
    fun beforeAnyPairRowIsReadTheFirstLinkedMachineIsOnScreen() {
        assertEquals(main.key, chooseOnScreen(null, null, keys) { null })
    }

    @Test
    fun aDefaultIsKeptWhileItStillSends() {
        // Both send (two mains): the one on screen stays.
        assertEquals(backup.key, chooseOnScreen(null, backup.key, keys) { true })
        // It stopped sending: the one that sends now.
        assertEquals(main.key, chooseOnScreen(null, backup.key, keys) { it == main.key })
    }

    @Test
    fun aChoiceNoLongerLinkedFallsBackToTheDefault() {
        assertEquals(main.key, chooseOnScreen(spare.key, null, keys) { null })
        assertNull(chooseOnScreen(main.key, main.key, emptyList()) { true })
    }

    // ------------------------------------------------------------------ find

    @Test
    fun aFindAsksForWholeSecondsWithinFiveMinutes() {
        assertEquals(FindRequest(12), FindRequest.parse("12"))
        assertEquals(FindRequest(3), FindRequest.parse(" 2.5 "))
        assertEquals(FindRequest(3), FindRequest.parse("2,5"))
        assertEquals(FindRequest(300), FindRequest.parse("3600"))
        assertEquals(FindRequest(1), FindRequest.parse("0.01"))
    }

    @Test
    fun aFindWithNoTimeOrNoSenseLastsThirtySeconds() {
        for (data in listOf("", "0", "-5", "soon", "NaN", "Infinity")) {
            assertEquals("FIND \"$data\"", FindRequest(30), FindRequest.parse(data))
        }
    }
}
