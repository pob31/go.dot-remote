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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressesTest {

    @Test
    fun anObjectsRowIsRead() {
        assertEquals(Addresses.Owned("cue", "F7HR8TVD", "level"), Addresses.owned("/godot/cue/F7HR8TVD/level"))
        assertEquals(Addresses.Owned("cue", "F7HR8TVD", ""), Addresses.owned("/godot/cue/F7HR8TVD"))
        assertEquals(Addresses.Owned("run", "R1234567", "eq/b1"), Addresses.owned("/godot/run/R1234567/eq/b1"))
    }

    @Test
    fun aSingletonIsNoObjectsRow() {
        assertNull(Addresses.owned("/godot/list/focus"))
        assertNull(Addresses.owned("/godot/engine/tick"))
        assertNull(Addresses.owned("/godot/cue"))
        assertNull(Addresses.owned("/godot"))
        assertNull(Addresses.owned("/adm/obj/1/xyz"))
    }

    @Test
    fun idsAreCrockfordBase32() {
        assertTrue(Addresses.isId("0123ABCZ"))
        assertTrue(Addresses.isId("V1D30000"))
        for (bad in listOf("F7HR8TV", "F7HR8TVDX", "f7hr8tvd", "ABCDEFGI", "ABCDEFGL", "ABCDEFGO", "ABCDEFGU", "focus123")) {
            assertFalse(bad, Addresses.isId(bad))
        }
    }

    @Test
    fun metadataIsKeyedByTheRowWithTheIdStarred() {
        assertEquals("/godot/cue/*/level", Addresses.metaKey("/godot/cue/F7HR8TVD/level"))
        assertEquals("/godot/cue/*", Addresses.metaKey("/godot/cue/F7HR8TVD"))
        assertEquals("/godot/list/focus", Addresses.metaKey("/godot/list/focus"))
        assertEquals("/godot/run/R1234567", Addresses.objectOf("/godot/run/R1234567/state"))
        assertEquals("/godot/list/focus", Addresses.objectOf("/godot/list/focus"))
    }
}
