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

package com.pob31.godot.remote.ui.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.link.LinkedMachine
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.CartActions
import com.pob31.godot.remote.model.CartPad
import com.pob31.godot.remote.model.CartReading
import com.pob31.godot.remote.model.LinkWrites
import com.pob31.godot.remote.model.Sent
import com.pob31.godot.remote.model.StripActions
import com.pob31.godot.remote.model.TabWrites
import com.pob31.godot.remote.model.iconFor
import com.pob31.godot.remote.model.readCarts
import com.pob31.godot.remote.ui.GodotIconMark
import com.pob31.godot.remote.ui.rememberReading
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.widgets.LockedWord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val offState: StateFlow<LinkState> = MutableStateFlow(LinkState.OFF)

/**
 * THE CART TAB (Go.dot's namespace draft §74, AQQ): the show's carts - cue lists GO never
 * meets - each cue a pad, fired by touch out of order: a door slam asked for again, a tracker's
 * backup identity sent now, a group that is a macro. Held, a hold member plays while the finger
 * is down. Nothing here moves the standby; under the lock the pads still play.
 */
@Composable
fun CartTab(onScreen: LinkedMachine?) {
    val link = onScreen?.link
    val state by (link?.state ?: offState).collectAsState()
    val writes = remember(link) { link?.let { LinkWrites(it) } ?: TabWrites.NONE }
    val reading = rememberReading(link, CartReading()) { readCarts(it) }
    CartView(reading, state, writes, hasMachine = onScreen != null)
}

/** The tab drawn from a reading - no link of its own, so a test draws and presses it from a fixture. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartView(reading: CartReading, state: LinkState, writes: TabWrites, hasMachine: Boolean = true) {
    var notSent by remember { mutableIntStateOf(0) }
    val send: (Sent) -> Unit = { sent -> if (!writes.send(sent)) notSent++ }
    val live = state == LinkState.LIVE

    Column(Modifier.fillMaxSize().background(GodotColors.ground)) {
        Column(
            Modifier.fillMaxWidth().background(GodotColors.panel).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(loc("cart.about"), color = GodotColors.inkDim, fontSize = 14.sp)
            if (reading.locked) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LockedWord()
                    Text(loc("cart.lockedNote"), color = GodotColors.inkDim, fontSize = 14.sp)
                }
            }
            if (notSent > 0) Text(loc("running.notSent"), color = GodotColors.failed)
        }

        if (!hasMachine) {
            Text(loc("remote.tabs.noMachine"), color = GodotColors.inkFaint, modifier = Modifier.padding(24.dp))
            return@Column
        }

        if (reading.carts.isEmpty()) {
            Text(loc("cart.none"), color = GodotColors.inkFaint, modifier = Modifier.padding(24.dp))
            return@Column
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).alpha(if (live) 1f else 0.45f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            for (cart in reading.carts) {
                Text(cart.name.ifEmpty { cart.id }, color = GodotColors.ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)

                if (cart.pads.isEmpty()) {
                    Text(loc("cart.empty"), color = GodotColors.inkFaint, fontSize = 14.sp)
                    continue
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (pad in cart.pads) CartPadView(pad, send, Modifier.size(150.dp, 112.dp))
                }
            }
        }
    }
}

/**
 * A PAD: the cue's number, its name and its kind in words and a picture, its own colour as a
 * tab at the left - never the only telling - and lit while a finger is on it or the cue is
 * going. Where it is struck is the velocity, the top a hard hit, as on the Surface tab's pads.
 */
@Composable
private fun CartPadView(pad: CartPad, send: (Sent) -> Unit, modifier: Modifier) {
    var down by remember(pad.cue) { mutableStateOf(false) }
    val current by rememberUpdatedState(pad)
    val lit = down || pad.going
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier
            .background(if (lit) GodotColors.picked else GodotColors.panelHigh, shape)
            .border(2.dp, if (lit) GodotColors.ink else GodotColors.rule, shape)
            .semantics {
                role = Role.Button
                contentDescription = "pad ${pad.number} ${pad.name}".trim()
            }
            .pointerInput(pad.cue) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    first.consume()
                    val now = current
                    val height = if (size.height > 0) 1.0 - first.position.y / size.height else 1.0
                    send(CartActions.press(now, StripActions.velocityAt(height)))
                    down = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                    }
                    send(CartActions.release(now))
                    down = false
                }
            }
    ) {
        padColour(pad.colour)?.let { Box(Modifier.fillMaxHeight().width(6.dp).background(it, shape)) }

        val ink = if (lit) Color.Black else GodotColors.ink
        val faint = if (lit) Color.Black else GodotColors.inkFaint

        Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GodotIconMark(iconFor(pad.kind, pad.mode), faint, size = 18.dp)
                Text(pad.number, color = faint, fontSize = 13.sp, maxLines = 1)
            }
            Text(
                pad.name, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(top = 4.dp)
            )
            if (pad.bank.isNotEmpty()) Text(pad.bank, color = faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (lit) Text(loc("cart.going"), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

/** A cue's own colour (`#rrggbb`), drawn as a tab and never as the only telling. */
private fun padColour(text: String): Color? {
    if (text.length != 7 || text[0] != '#') return null
    val rgb = text.substring(1).toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}
