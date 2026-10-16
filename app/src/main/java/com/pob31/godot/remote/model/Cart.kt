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

/*  THE CART TAB'S READING (Go.dot's namespace draft §74, AQQ): every cart of the show - a
    cue list GO never meets, its cues fired out of order - and each of its cues as a pad. A
    cue at a cart's top level is a pad of its own: a sound, an OSC message, a script, a group
    that is a macro. A sampler group in a cart is its members, each a pad, under the group's
    name. A finger down presses the pad (`cue.press`, at the velocity where it landed), up lets
    it go (`cue.release`), so a hold member plays while it is held; anything else is fired by
    the press and lets go of nothing. The standby never moves. */

/** A pad: one cue of a cart. */
data class CartPad(
    val cue: String,
    val number: String,
    val name: String,
    val kind: String,
    val mode: String = "",
    /** The cue's own colour, `#rrggbb`, or empty. */
    val colour: String = "",
    /** Whether a run of it is going now - playing, waiting out its pre-wait, or coming down. */
    val going: Boolean = false,
    /** The sampler group it is a member of, by name; empty for a pad at the cart's top level. */
    val bank: String = "",
)

/** A cart and its pads, in its order. */
data class CartLine(val id: String, val name: String, val pads: List<CartPad>)

data class CartReading(
    val carts: List<CartLine> = emptyList(),
    val locked: Boolean = false,
)

/** Whether the show has a cart at all: the tab is in the row only while it has. */
fun hasCart(mirror: Mirror): Boolean =
    cartWords(mirror.text("/godot/list/order")).any { mirror.text("/godot/list/$it/kind") == "cart" }

fun readCarts(mirror: Mirror): CartReading {
    val going = goingCues(mirror)

    fun pad(cue: String, bank: String = ""): CartPad {
        val label = cueLabel(mirror, cue)
        val base = "/godot/cue/$cue/"
        return CartPad(
            cue = cue,
            number = label.number,
            name = label.name,
            kind = mirror.text(base + "kind") ?: "",
            mode = mirror.text(base + "mode") ?: "",
            colour = mirror.text(base + "colour") ?: "",
            going = cue in going,
            bank = bank,
        )
    }

    val carts = cartWords(mirror.text("/godot/list/order"))
        .filter { mirror.text("/godot/list/$it/kind") == "cart" }
        .map { id ->
            val pads = mutableListOf<CartPad>()

            for (cue in cartWords(mirror.text("/godot/list/$id/order"))) {
                val base = "/godot/cue/$cue/"

                if (mirror.text(base + "mode") == "sampler") {
                    val bank = cueLabel(mirror, cue).name
                    for (member in cartWords(mirror.text(base + "order"))) pads += pad(member, bank)
                } else {
                    pads += pad(cue)
                }
            }

            CartLine(id, mirror.text("/godot/list/$id/name") ?: "", pads)
        }

    return CartReading(
        carts = carts,
        locked = mirror.value("/godot/document/locked")?.asBoolean() == true,
    )
}

/** The cues a run of is going now: not finished, and not a sampler member only armed and waiting. */
private fun goingCues(mirror: Mirror): Set<String> {
    val out = mutableSetOf<String>()

    for (run in cartWords(mirror.text("/godot/run/order"))) {
        val base = "/godot/run/$run/"
        val state = mirror.text(base + "state") ?: ""

        if (state == "playing" || state == "waiting" || state == "stopping" || state == "postWait")
            mirror.text(base + "cue")?.let { out += it }
    }

    return out
}

private fun cartWords(text: String?): List<String> = text?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList()

/** What a hand on the Cart tab sends: one table, read by a test with no screen. */
object CartActions {
    /** A finger down on a pad, at the velocity where it landed (the Surface tab's 1..127). */
    fun press(pad: CartPad, velocity: Int) = Sent("cue.press", pad.cue, velocity.coerceIn(1, 127).toString())

    /** The finger up: a hold member stops; anything else lets go of nothing. */
    fun release(pad: CartPad) = Sent("cue.release", pad.cue)
}
