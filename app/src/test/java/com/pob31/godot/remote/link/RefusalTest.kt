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

class RefusalTest {

    @Test
    fun theEnginesRecordIsRead() {
        val r = Refusal.parse("1234 17 ws:192.168.1.20:51234 not-a-stop standby.set")!!
        assertEquals(1234L, r.tick)
        assertEquals(17L, r.seq)
        assertEquals("ws:192.168.1.20:51234", r.origin)
        assertEquals("not-a-stop", r.reason)
        assertEquals("standby.set", r.command)
        assertNull(r.detail)
    }

    @Test
    fun theDetailIsTheRestOfTheLine() {
        val r = Refusal.parse("9 2 ws:10.0.0.2:4000 locked node.setMany /godot/cue/F7HR8TVD/my notes")!!
        assertEquals("node.setMany", r.command)
        assertEquals("/godot/cue/F7HR8TVD/my notes", r.detail)
    }

    @Test
    fun aRecordWithoutACommandHasNone() {
        val r = Refusal.parse("9 2 udp:10.0.0.2:4000 malformed")!!
        assertEquals("malformed", r.reason)
        assertNull(r.command)
    }

    @Test
    fun whatIsNoRecordIsNull() {
        assertNull(Refusal.parse(""))
        assertNull(Refusal.parse("refused"))
        assertNull(Refusal.parse("x 2 ws:a reason"))
    }

    @Test
    fun theRemotesRowNamesOurOrigin() {
        val text = "ws:10.0.0.5:5000\tabc\tFront of house\nws:10.0.0.6:6000\tme\tStage left\n"
        assertEquals(
            listOf(RemoteLine("ws:10.0.0.5:5000", "abc", "Front of house"), RemoteLine("ws:10.0.0.6:6000", "me", "Stage left")),
            RemoteLine.parseAll(text)
        )
        assertEquals("ws:10.0.0.6:6000", RemoteLine.originOf(text, "me"))
        assertNull(RemoteLine.originOf(text, "nobody"))
        assertNull(RemoteLine.originOf("", "me"))
    }
}
