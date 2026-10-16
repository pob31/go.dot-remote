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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pob31.godot.remote.buzzer.BuzzerAccess
import com.pob31.godot.remote.buzzer.BuzzerPhase
import com.pob31.godot.remote.buzzer.BuzzerStatus
import com.pob31.godot.remote.link.EngineLinkService
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.link.LinkedMachine
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.Said
import com.pob31.godot.remote.prompt.PageState
import com.pob31.godot.remote.prompt.PromptPage
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * How a connection stands, in four: each has its colour AND its shape (§4.8) - a filled dot
 * when it is good, a ring in between, a square with a cross when it is bad, a dash when off.
 */
enum class ConnectionHealth { GOOD, BETWEEN, BAD, OFF }

/** One connection the Connections tab manages, as the tab bar shows it: its short name, how it stands, its state in words. */
data class ConnectionMark(val name: Said, val health: ConnectionHealth, val state: Said)

fun linkHealth(state: LinkState): ConnectionHealth = when (state) {
    LinkState.LIVE -> ConnectionHealth.GOOD
    LinkState.CONNECTING, LinkState.STALE, LinkState.CLOCK_STOPPED -> ConnectionHealth.BETWEEN
    LinkState.GONE, LinkState.TOO_OLD -> ConnectionHealth.BAD
    LinkState.OFF -> ConnectionHealth.OFF
}

fun pageHealth(state: PageState): ConnectionHealth = when (state) {
    is PageState.Shown -> ConnectionHealth.GOOD
    is PageState.Loading -> ConnectionHealth.BETWEEN
    is PageState.Failed -> ConnectionHealth.BAD
    PageState.Empty -> ConnectionHealth.OFF
}

private fun pageWordKey(state: PageState): String = when (state) {
    is PageState.Shown -> "remote.connections.page.loaded"
    is PageState.Loading -> "remote.connections.page.loading"
    is PageState.Failed -> "remote.connections.page.failed"
    PageState.Empty -> "remote.connections.page.notOpened"
}

fun buzzerHealth(phase: BuzzerPhase): ConnectionHealth = when (phase) {
    BuzzerPhase.LINKED -> ConnectionHealth.GOOD
    BuzzerPhase.SEARCHING -> ConnectionHealth.BETWEEN
    BuzzerPhase.LOST, BuzzerPhase.NO_PERMISSION, BuzzerPhase.BLUETOOTH_OFF -> ConnectionHealth.BAD
    BuzzerPhase.NONE -> ConnectionHealth.OFF
}

/**
 * THE CONNECTIONS THE TAB BAR SHOWS, in order: each linked Go.dot machine, the one on screen
 * first; Choufleur's page while an address is set; the wrist buzzer while one is remembered.
 * With no machine linked, one "Go.dot - not linked" mark, so the bar still says so.
 */
fun connectionMarks(
    machines: List<Pair<String, LinkState>>,
    onScreenIndex: Int?,
    page: PageState?,
    buzzer: BuzzerStatus?,
): List<ConnectionMark> {
    val marks = mutableListOf<ConnectionMark>()
    val ordered = if (onScreenIndex != null && onScreenIndex in machines.indices) {
        listOf(machines[onScreenIndex]) + machines.filterIndexed { i, _ -> i != onScreenIndex }
    } else machines
    for ((name, state) in ordered) {
        marks += ConnectionMark(Said.verbatim(name), linkHealth(state), Said(EngineLinkService.linkWordKey(state)))
    }
    if (ordered.isEmpty()) {
        marks += ConnectionMark(Said.verbatim("Go.dot"), ConnectionHealth.OFF, Said(EngineLinkService.linkWordKey(LinkState.OFF)))
    }
    if (page != null) marks += ConnectionMark(Said("remote.connections.script"), pageHealth(page), Said(pageWordKey(page)))
    if (buzzer != null && buzzer.remembered != null && buzzer.phase != BuzzerPhase.NONE) {
        marks += ConnectionMark(Said("remote.connections.buzzer"), buzzerHealth(buzzer.phase), Said(buzzer.phase.key))
    }
    return marks
}

private val noPage: StateFlow<PageState> = MutableStateFlow(PageState.Empty)

/** The marks of the service's links, the Script page (while [promptAddress] is set) and the buzzer, kept up to date. */
@Composable
fun rememberConnectionMarks(
    links: List<LinkedMachine>,
    onScreen: LinkedMachine?,
    promptAddress: String?,
    page: PromptPage?,
    buzzer: BuzzerAccess?,
): List<ConnectionMark> {
    val states = links.map { linked -> key(linked.machine.key) { linked.link.state.collectAsState().value } }
    val pageState by (page?.state ?: noPage).collectAsState()
    val buzzerStatus = buzzer?.status?.collectAsState()?.value
    val onScreenIndex = links.indexOfFirst { it.machine.key == onScreen?.machine?.key }.takeIf { it >= 0 }
    return connectionMarks(
        links.mapIndexed { i, linked -> linked.machine.label to states[i] },
        onScreenIndex,
        if (promptAddress != null) pageState else null,
        buzzerStatus,
    )
}

private fun healthColour(health: ConnectionHealth): Color = when (health) {
    ConnectionHealth.GOOD -> GodotColors.live
    ConnectionHealth.BETWEEN -> GodotColors.stopping
    ConnectionHealth.BAD -> GodotColors.failed
    ConnectionHealth.OFF -> GodotColors.inkOff
}

/** The state's shape in its colour: a filled dot, a ring, a square with a cross, a dash. */
@Composable
fun HealthShape(health: ConnectionHealth, size: Dp = 14.dp) {
    val colour = healthColour(health)
    Canvas(Modifier.size(size)) {
        val w = this.size.minDimension
        val stroke = w * 0.18f
        when (health) {
            ConnectionHealth.GOOD -> drawCircle(colour, radius = w / 2)
            ConnectionHealth.BETWEEN -> drawCircle(colour, radius = (w - stroke) / 2, style = Stroke(stroke))
            ConnectionHealth.BAD -> {
                val inset = stroke / 2
                drawRect(colour, Offset(inset, inset), Size(w - stroke, w - stroke), style = Stroke(stroke))
                val a = w * 0.28f
                val b = w - a
                drawLine(colour, Offset(a, a), Offset(b, b), stroke, StrokeCap.Round)
                drawLine(colour, Offset(a, b), Offset(b, a), stroke, StrokeCap.Round)
            }
            ConnectionHealth.OFF -> drawLine(colour, Offset(w * 0.1f, w / 2), Offset(w * 0.9f, w / 2), stroke * 1.3f, StrokeCap.Round)
        }
    }
}

/** The width the marks need with their names; under it, the shapes alone. */
private val NAMED_MARK = 76.dp

/**
 * THE CONNECTIONS AT THE END OF THE TAB BAR (the author, 2026-10-16: "To gain a little
 * space, can the connection statuses show as red/orange/green dots at the end of the tab
 * bar? Clicking on one opens the connections tab"): one shape per connection, its short name
 * beside it where the bar has room - the shapes alone on a phone, two to a column. The whole
 * group is one button, at least a finger wide, that opens the Connections tab; each mark
 * says its name and state in words to a screen reader.
 */
@Composable
fun ConnectionStrip(marks: List<ConnectionMark>, compact: Boolean, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val openWords = loc("remote.connections.open")
    BoxWithConstraints(
        modifier
            .widthIn(min = GodotMetrics.touch)
            .clickable(onClickLabel = openWords, role = Role.Button, onClick = onOpen)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        val named = !compact && maxWidth >= NAMED_MARK * marks.size
        if (named) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End)) {
                for (mark in marks) NamedMark(mark, Modifier.weight(1f, fill = false))
            }
        } else {
            // The shapes alone, two to a column: a phone's bar, or a tight one, keeps its width for the tabs.
            val rows = if (marks.size > 2) 2 else 1
            val columns = marks.chunked(rows)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                for (column in columns) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (mark in column) ShapeOnly(mark)
                    }
                }
            }
        }
    }
}

@Composable
private fun markWords(mark: ConnectionMark): String =
    loc("remote.connections.mark", "name" to say(mark.name), "state" to say(mark.state))

@Composable
private fun NamedMark(mark: ConnectionMark, modifier: Modifier) {
    val words = markWords(mark)
    Row(modifier.clearAndSetSemantics { contentDescription = words }, verticalAlignment = Alignment.CenterVertically) {
        HealthShape(mark.health)
        Spacer(Modifier.width(6.dp))
        Text(
            say(mark.name), color = if (mark.health == ConnectionHealth.OFF) GodotColors.inkFaint else GodotColors.inkDim,
            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false
        )
    }
}

@Composable
private fun ShapeOnly(mark: ConnectionMark) {
    val words = markWords(mark)
    Box(Modifier.clearAndSetSemantics { contentDescription = words }) { HealthShape(mark.health) }
}
