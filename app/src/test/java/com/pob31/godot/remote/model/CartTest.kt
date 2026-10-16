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
import com.pob31.godot.remote.model.ShowFixture.FOYER
import com.pob31.godot.remote.model.ShowFixture.RUN_THUNDER
import com.pob31.godot.remote.model.ShowFixture.STORM
import com.pob31.godot.remote.model.ShowFixture.THUNDER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** THE CART TAB'S READING (Go.dot's namespace draft §74, AQQ): a cart's cues as pads. */
class CartTest {
    private val mirror = ShowFixture.mirror()
    private val door = "CE000001"

    private fun words(text: String?) = text?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList()

    @Test
    fun aShowWithNoCartHasNoTabAndNoPads() {
        assertFalse(hasCart(mirror))
        assertTrue(readCarts(mirror).carts.isEmpty())
    }

    @Test
    fun aCartsCuesArePads_andABanksMembersArePadsUnderItsName() {
        mirror.put("/godot/list/$FOYER/kind", "cart")
        mirror.put("/godot/list/$FOYER/order", "$door $STORM")
        mirror.put("/godot/cue/$STORM/mode", "sampler")

        assertTrue(hasCart(mirror))
        val reading = readCarts(mirror)
        assertEquals(1, reading.carts.size)

        val cart = reading.carts[0]
        assertEquals(FOYER, cart.id)

        val members = words(mirror.text("/godot/cue/$STORM/order"))
        assertTrue(members.isNotEmpty())
        assertEquals(listOf(door) + members, cart.pads.map { it.cue })
        assertEquals("", cart.pads[0].bank)
        assertTrue(cart.pads.drop(1).all { it.bank == cueLabel(mirror, STORM).name })
        assertEquals("memo", cart.pads[0].kind)

        // GO never meets a cart, and the Cues tab's lists no longer offer it.
        assertEquals(listOf(ACT_ONE), readLists(mirror).map { it.id })
    }

    @Test
    fun aPadIsGoingWhileARunOfItIs_andNotWhileItIsOnlyArmed() {
        mirror.put("/godot/list/$FOYER/kind", "cart")
        mirror.put("/godot/list/$FOYER/order", THUNDER)

        mirror.put("/godot/run/$RUN_THUNDER/state", "playing")
        assertTrue(readCarts(mirror).carts[0].pads[0].going)

        mirror.put("/godot/run/$RUN_THUNDER/state", "armed")
        assertFalse(readCarts(mirror).carts[0].pads[0].going)
    }

    @Test
    fun aFingerDownPressesAtItsVelocity_andUpLetsGo() {
        val pad = CartPad(door, "1", "Door", "memo")
        assertEquals(Sent("cue.press", door, "127"), CartActions.press(pad, 127))
        assertEquals(Sent("cue.press", door, "1"), CartActions.press(pad, 0))
        assertEquals(Sent("cue.release", door), CartActions.release(pad))
    }
}
