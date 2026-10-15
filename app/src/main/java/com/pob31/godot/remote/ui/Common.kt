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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pob31.godot.remote.link.EngineLinkService
import com.pob31.godot.remote.link.Link
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.Mirror
import com.pob31.godot.remote.model.Said
import com.pob31.godot.remote.ui.theme.GodotColors
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.sample

/** How often the screen reads a mirror: 25 times a second, at most, whatever the tick. */
const val READ_MS = 40L

/**
 * A READING OF ONE MACHINE'S MIRROR, taken again at no more than 25 Hz while its tick or
 * its structure moves - by address, as the page reads, never a scan of the whole mirror.
 * A reading equal to the last one recomposes nothing. [empty] while there is no link.
 */
@OptIn(FlowPreview::class)
@Composable
fun <T> rememberReading(link: Link?, empty: T, read: (Mirror) -> T): T {
    val reader by rememberUpdatedState(read)
    var value by remember(link) { mutableStateOf(link?.mirror?.let { reader(it) } ?: empty) }
    LaunchedEffect(link) {
        val mirror = link?.mirror ?: return@LaunchedEffect
        merge(mirror.tick, mirror.structure).sample(READ_MS).collect { value = reader(mirror) }
    }
    return value
}

/** Words the model chose, said in the tablet's language. */
@Composable
fun say(said: Said): String = loc(said.key, *said.params.toTypedArray())

/** The colour a link's state wears beside its word - never instead of it (§4.8). */
fun linkColour(state: LinkState): Color = when (state) {
    LinkState.OFF -> GodotColors.inkOff
    LinkState.CONNECTING -> GodotColors.waiting
    LinkState.LIVE -> GodotColors.live
    LinkState.STALE, LinkState.CLOCK_STOPPED -> GodotColors.stopping
    LinkState.GONE, LinkState.TOO_OLD -> GodotColors.failed
}

/** A link's state: a dot in its colour AND its word. */
@Composable
fun LinkMark(state: LinkState, modifier: Modifier = Modifier, style: TextStyle = TextStyle.Default) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .background(linkColour(state), CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            loc(EngineLinkService.linkWordKey(state)), color = GodotColors.ink, style = style,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
