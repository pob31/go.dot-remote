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

package com.pob31.godot.remote.ui.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.link.LinkedMachine
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.AimChoice
import com.pob31.godot.remote.model.EqReading
import com.pob31.godot.remote.model.FaderCurve
import com.pob31.godot.remote.model.LinkWrites
import com.pob31.godot.remote.model.Sent
import com.pob31.godot.remote.model.StripActions
import com.pob31.godot.remote.model.StripLine
import com.pob31.godot.remote.model.SurfaceLine
import com.pob31.godot.remote.model.SurfacePage
import com.pob31.godot.remote.model.SurfaceReading
import com.pob31.godot.remote.model.SurfaceState
import com.pob31.godot.remote.model.SurfaceTables
import com.pob31.godot.remote.model.TabWrites
import com.pob31.godot.remote.model.readSurface
import com.pob31.godot.remote.model.placeKnobs
import com.pob31.godot.remote.model.sendKnobs
import com.pob31.godot.remote.ui.GodotIconMark
import com.pob31.godot.remote.ui.rememberReading
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotMetrics
import com.pob31.godot.remote.ui.theme.GodotType
import com.pob31.godot.remote.ui.widgets.DbFader
import com.pob31.godot.remote.ui.widgets.EqBandSwitch
import com.pob31.godot.remote.ui.widgets.EqKnobView
import com.pob31.godot.remote.ui.widgets.LiveWord
import com.pob31.godot.remote.ui.widgets.LockedWord
import com.pob31.godot.remote.ui.widgets.Mark
import com.pob31.godot.remote.ui.widgets.MarkIcon
import com.pob31.godot.remote.ui.widgets.MeterBar
import com.pob31.godot.remote.ui.widgets.SwapSwitch
import com.pob31.godot.remote.ui.widgets.markFor
import com.pob31.godot.remote.ui.widgets.rememberSurfaceTables
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val offState: StateFlow<LinkState> = MutableStateFlow(LinkState.OFF)

/**
 * THE SURFACE TAB (A.7): the show's surfaces on the tablet - a D700 left in the régie, a
 * Mackie that did not come, or no surface at all - so a hand walking the house has the
 * faders. The strips of the surface picked here, their faders on what each rides now,
 * a pad for a pad's strip, SELECT; and three pages a D700 has, the aimed cue's EQ, its
 * sends and their places. Which surface and which page are this tablet's own; the aim is
 * the engine's.
 */
@Composable
fun SurfaceTab(onScreen: LinkedMachine?) {
    val link = onScreen?.link
    val state by (link?.state ?: offState).collectAsState()
    val writes = remember(link) { link?.let { LinkWrites(it) } ?: TabWrites.NONE }
    val tables = rememberSurfaceTables()
    var asked by rememberSaveable { mutableStateOf<String?>(null) }
    var page by rememberSaveable { mutableStateOf(SurfacePage.FADERS) }
    // Read again at once when another surface is picked, not only at the next tick.
    val reading = key(asked) { rememberReading(link, SurfaceReading()) { readSurface(it, asked, tables) } }
    SurfaceView(
        reading, state, writes, tables, page,
        hasMachine = onScreen != null,
        onSurface = { asked = it },
        onPage = { page = it },
    )
}

/** The tab drawn from a reading - no link of its own, so a test draws and presses it from a fixture. */
@Composable
fun SurfaceView(
    reading: SurfaceReading,
    state: LinkState,
    writes: TabWrites,
    tables: SurfaceTables,
    page: SurfacePage,
    hasMachine: Boolean = true,
    onSurface: (String) -> Unit = {},
    onPage: (SurfacePage) -> Unit = {},
) {
    var notSent by remember { mutableIntStateOf(0) }
    val send: (Sent?) -> Unit = { sent -> if (sent != null && !writes.send(sent)) notSent++ }
    val live = state == LinkState.LIVE

    Column(Modifier.fillMaxSize().background(GodotColors.ground)) {
        SurfaceBar(reading, state, hasMachine, page, notSent > 0, onSurface, onPage)
        if (!hasMachine) {
            Text(loc("remote.tabs.noMachine"), color = GodotColors.inkFaint, modifier = Modifier.padding(24.dp))
            return@Column
        }
        Box(Modifier.fillMaxWidth().weight(1f).alpha(if (live) 1f else 0.45f)) {
            when (page) {
                SurfacePage.FADERS -> StripsPage(reading, writes, tables.fader, send)
                SurfacePage.EQ -> EqPage(reading, writes, tables, send)
                SurfacePage.SENDS -> SendsPage(reading, writes, tables, send)
                SurfacePage.PAN -> PanPage(reading, writes, tables, send)
            }
        }
    }
}

// ------------------------------------------------------------------------- the bar

/** The surfaces by name, each saying how it stands; the pages; the lock and the link in words. */
@Composable
private fun SurfaceBar(
    reading: SurfaceReading,
    state: LinkState,
    hasMachine: Boolean,
    page: SurfacePage,
    notSent: Boolean,
    onSurface: (String) -> Unit,
    onPage: (SurfacePage) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().background(GodotColors.panel)) {
    // Wide, the pages share the surfaces' row: the room under it is the faders'.
    val wide = maxWidth >= 900.dp
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(reading.surfaces, key = { it.id }) { surface ->
                    SurfaceChip(surface, surface.id == reading.shown.id) { onSurface(surface.id) }
                }
            }
            if (wide) for (each in SurfacePage.entries) PageChip(each, each == page) { onPage(each) }
        }
        if (!wide) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (each in SurfacePage.entries) PageChip(each, each == page) { onPage(each) }
            }
        }
        if (reading.shown.state == SurfaceState.NOT_CONNECTED) {
            Text(loc("surface.standsIn", "surface" to surfaceName(reading.shown)), color = GodotColors.inkDim, fontSize = 14.sp)
        }
        if (reading.locked) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LockedWord()
                Text(loc("surface.lockedNote"), color = GodotColors.inkDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
            }
        }
        if (notSent) Text(loc("running.notSent"), color = GodotColors.failed)
    }
    }
}

@Composable
private fun surfaceName(surface: SurfaceLine): String = when {
    surface.isTablet -> loc("surface.thisTablet")
    surface.name.isNotEmpty() -> surface.name
    else -> surface.profileKey?.let { loc(it) } ?: surface.profile.ifEmpty { surface.id }
}

/** The colour a surface's state wears beside its word - never instead of it (§4.8). */
private fun stateColour(state: SurfaceState): Color = when (state) {
    SurfaceState.TABLET -> GodotColors.picked
    SurfaceState.CONNECTED -> GodotColors.live
    SurfaceState.OFF -> GodotColors.inkOff
    SurfaceState.PROBLEM -> GodotColors.failed
    SurfaceState.NOT_CONNECTED -> GodotColors.stopping
}

@Composable
private fun stateWords(surface: SurfaceLine): String = when (surface.state) {
    SurfaceState.TABLET -> loc("surface.state.tablet")
    SurfaceState.CONNECTED -> loc("surface.state.connected")
    SurfaceState.OFF -> loc("surface.state.off")
    SurfaceState.PROBLEM -> surface.problem
    SurfaceState.NOT_CONNECTED -> loc("surface.state.notConnected")
}

/** A surface: its name, then its profile and how it stands, in words with a dot of the state's colour. */
@Composable
private fun SurfaceChip(surface: SurfaceLine, on: Boolean, onPick: () -> Unit) {
    Column(
        Modifier
            .heightIn(min = 56.dp)
            .background(if (on) GodotColors.panelHigh else GodotColors.panelIn, RoundedCornerShape(6.dp))
            .border(2.dp, if (on) GodotColors.picked else GodotColors.rule, RoundedCornerShape(6.dp))
            .clickable(role = Role.Tab) { onPick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (surface.isTablet) {
                GodotIconMark(com.pob31.godot.remote.model.GodotIcon.SURFACE, if (on) GodotColors.picked else GodotColors.inkFaint, size = 18.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(surfaceName(surface), color = if (on) GodotColors.ink else GodotColors.inkDim, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(stateColour(surface.state), CircleShape))
            Spacer(Modifier.width(6.dp))
            val profile = if (surface.isTablet || surface.name.isEmpty()) null else surface.profileKey?.let { loc(it) }
            Text(
                listOfNotNull(profile, stateWords(surface)).joinToString(" · "),
                color = GodotColors.inkFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 320.dp)
            )
        }
    }
}

/** A page: its word, lit and ticked when it is the one up (the tick says what the colour does). */
@Composable
private fun PageChip(page: SurfacePage, on: Boolean, onPick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = GodotMetrics.touch)
            .widthIn(min = 88.dp)
            .background(if (on) GodotColors.picked else GodotColors.panelHigh, RoundedCornerShape(6.dp))
            .clickable(role = Role.Tab) { onPick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (on) {
            MarkIcon(Mark.DONE, Color.Black, size = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(loc(page.key), color = if (on) Color.Black else GodotColors.ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ------------------------------------------------------------------------- the faders

/** The strips side by side, as many as fit, the rest a swipe sideways away; an empty surface says so. */
@Composable
private fun StripsPage(reading: SurfaceReading, writes: TabWrites, curve: FaderCurve, send: (Sent?) -> Unit) {
    val strips = reading.strips
    if (strips.isEmpty()) {
        Text(
            loc(if (reading.shown.isTablet) "surface.noDca" else "surface.noStrips"),
            color = GodotColors.inkFaint, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp)
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gap = 8.dp
        val width = ((maxWidth - 24.dp - gap * (strips.size - 1)) / strips.size).coerceIn(124.dp, 168.dp)
        LazyRow(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            items(strips, key = { it.id }) { strip ->
                StripView(strip, curve, writes, send, width)
            }
        }
    }
}

/** A strip's word in the engine's colour for it, beside the word itself. */
private fun wordColour(word: String): Color = when (word) {
    "playing", "held" -> GodotColors.live
    "armed" -> GodotColors.standby
    "pending" -> GodotColors.waiting
    "stopping", "closing" -> GodotColors.stopping
    "recording", "rec" -> GodotColors.failed
    "lane" -> GodotColors.fade
    else -> GodotColors.inkFaint
}

/**
 * ONE STRIP: its place on the surface and what it is doing in a word, what is on it, the
 * fader on what it rides (relative from touch-down, finer as the finger drifts sideways,
 * one undo step a drag - and a trim, never stored) with the meter of the run holding it,
 * or a pad for a pad's strip, and SELECT under a sound's.
 */
@Composable
private fun StripView(strip: StripLine, curve: FaderCurve, writes: TabWrites, send: (Sent?) -> Unit, width: Dp) {
    val inside = width - 16.dp
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(if (strip.aimed) GodotColors.panelHigh else GodotColors.panel, RoundedCornerShape(8.dp))
            .border(if (strip.aimed) 2.dp else 1.dp, if (strip.aimed) GodotColors.picked else GodotColors.rule, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text((strip.index + 1).toString(), color = GodotColors.inkFaint, style = GodotType.number.copy(fontSize = 14.sp), maxLines = 1)
            Spacer(Modifier.weight(1f))
            val word = strip.wordKey?.let { loc(it) } ?: strip.word
            if (word.isNotEmpty()) {
                if (strip.word == "playing" || strip.word == "armed" || strip.word == "stopping") {
                    MarkIcon(markFor(strip.word), wordColour(strip.word), size = 12.dp)
                    Spacer(Modifier.width(4.dp))
                }
                Text(word, color = wordColour(strip.word), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // What is on it - its number and its name, on two lines at most - or that nothing is.
        Box(Modifier.fillMaxWidth().height(52.dp)) {
            if (strip.empty) {
                Text(loc(if (strip.isDca) "surface.noDcaOnStrip" else "surface.nothingOnStrip"), color = GodotColors.inkOff, fontSize = 14.sp, maxLines = 2)
            } else {
                Text(
                    buildAnnotatedString {
                        if (strip.number.isNotEmpty()) {
                            withStyle(SpanStyle(fontFamily = GodotType.number.fontFamily, fontWeight = FontWeight.Normal)) { append(strip.number) }
                            append("  ")
                        }
                        append(strip.name)
                    },
                    color = if (strip.isDca) GodotColors.standby else GodotColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp
                )
            }
        }
        if (strip.isPad) {
            PadView(strip, writes, send, Modifier.fillMaxWidth().weight(1f))
        } else {
            // The track as wide as the strip allows beside its meter: a finger's width at least.
            val meter = if (strip.isDca) 0.dp else 46.dp
            DbFader(
                value = strip.level ?: curve.silenceDb,
                address = strip.target,
                writes = writes,
                curve = curve,
                modifier = Modifier.fillMaxWidth().weight(1f),
                vertical = true,
                colour = if (strip.isDca) GodotColors.standby else GodotColors.live,
                enabled = strip.rides,
                label = "",
                thickness = (inside - meter).coerceIn(44.dp, 72.dp),
                boxWidth = (inside - 36.dp).coerceAtLeast(60.dp),
                beside = if (strip.isDca) null else ({ MeterBar(strip.meter, curve, Modifier.fillMaxHeight(), vertical = true) }),
            )
        }
        if (strip.isDca) {
            // A DCA's trim is tonight's: said where a sound's strip has its SELECT.
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                if (strip.rides) Text(loc("surface.dcaTonight"), color = GodotColors.inkFaint, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2)
            }
        } else {
            SelectButton(strip) { send(StripActions.select(strip)) }
        }
    }
}

/**
 * A PAD: lit while a hand is on it - this one, or another the engine says is holding the
 * clip (`run/held`) - and saying so. Where it is struck is the velocity, the top a hard hit
 * and the bottom a soft one, said on it. A DCA strip's pad puts its trim back at nought.
 */
@Composable
private fun PadView(strip: StripLine, writes: TabWrites, send: (Sent?) -> Unit, modifier: Modifier) {
    var down by remember(strip.id) { mutableStateOf(false) }
    val current by rememberUpdatedState(strip)
    val lit = down || strip.held
    Box(
        modifier
            .background(if (lit) GodotColors.picked else if (strip.empty) GodotColors.panelIn else GodotColors.panelHigh, RoundedCornerShape(8.dp))
            .border(2.dp, if (lit) GodotColors.ink else GodotColors.rule, RoundedCornerShape(8.dp))
            .semantics {
                role = Role.Button
                contentDescription = "pad ${strip.index + 1}"
            }
            .pointerInput(strip.id, strip.empty) {
                if (strip.empty) return@pointerInput
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    first.consume()
                    val now = current
                    val unity = StripActions.dcaUnity(now)
                    if (unity != null) {
                        writes.setNow(unity.first, unity.second)
                    } else {
                        val height = if (size.height > 0) 1.0 - first.position.y / size.height else 1.0
                        send(StripActions.press(now, StripActions.velocityAt(height)))
                        down = true
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                    }
                    if (unity == null) {
                        send(StripActions.release(now))
                        down = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val ink = if (lit) Color.Black else GodotColors.inkFaint
        if (strip.empty) {
            // Nothing to strike: the words above say so, and the pad stays bare.
        } else if (strip.isDca) {
            Text(loc("surface.padUnity"), color = ink, fontWeight = FontWeight.SemiBold)
        } else {
            Text(loc("surface.padHard"), color = ink, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            if (lit) Text(loc("surface.word.held"), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(loc("surface.padSoft"), color = ink, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
    }
}

/** SELECT: aims the EQ and Send pages at this strip's sound; lit, and saying so, while they edit it. */
@Composable
private fun SelectButton(strip: StripLine, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = strip.selectable,
        color = if (strip.aimed) GodotColors.picked else GodotColors.panelHigh,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "SELECT", fontWeight = FontWeight.Bold, maxLines = 1,
                color = when {
                    strip.aimed -> Color.Black
                    strip.selectable -> GodotColors.ink
                    else -> GodotColors.inkOff
                }
            )
            if (strip.aimed) Text(loc("surface.selected"), color = Color.Black, fontSize = 12.sp, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------------- the pages

/** The cue the pages edit, and how to aim them elsewhere; with nothing aimed, how to aim them at all. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AimHeader(reading: SurfaceReading, send: (Sent?) -> Unit) {
    var choosing by remember(reading.aim) { mutableStateOf(false) }
    val cue = reading.aimCue
    if (cue == null) {
        Text(loc("surface.noAim"), color = GodotColors.ink, fontSize = 18.sp)
        AimChoices(reading.aimChoices, send)
        return
    }
    val accent = GodotColors.kindAccent(cue.kind, cue.mode)
    val name: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(loc("surface.editing"), color = GodotColors.inkFaint, maxLines = 1)
            Spacer(Modifier.width(10.dp))
            GodotIconMark(cue.icon, accent, size = 26.dp)
            Spacer(Modifier.width(8.dp))
            if (cue.number.isNotEmpty()) {
                Text(cue.number, color = GodotColors.ink, style = GodotType.number.copy(fontSize = 20.sp), maxLines = 1, softWrap = false)
                Spacer(Modifier.width(10.dp))
            }
            Text(cue.name, color = GodotColors.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
    }
    val button: @Composable () -> Unit = {
        Surface(
            onClick = { choosing = !choosing },
            color = GodotColors.panelHigh,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.heightIn(min = GodotMetrics.touch)
        ) {
            Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(loc(if (choosing) "surface.aimKeep" else "surface.aimElsewhere"), color = GodotColors.ink, maxLines = 1, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
    // The name keeps its room: on a phone the button goes under it.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                name(Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(12.dp))
                button()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                name(Modifier)
                button()
            }
        }
    }
    if (choosing) AimChoices(reading.aimChoices.filter { it.cue != reading.aim }, send)
}

/** The sounds playing now, each a button that aims the pages at it; none, said. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AimChoices(choices: List<AimChoice>, send: (Sent?) -> Unit) {
    if (choices.isEmpty()) {
        Text(loc("surface.noneToAim"), color = GodotColors.inkFaint)
        return
    }
    Text(loc("surface.aimAt"), color = GodotColors.inkDim)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (choice in choices) {
            Row(
                Modifier
                    .heightIn(min = GodotMetrics.touch)
                    .background(GodotColors.panelHigh, RoundedCornerShape(6.dp))
                    .border(1.dp, GodotColors.rule, RoundedCornerShape(6.dp))
                    .clickable { send(StripActions.aim(choice.cue)) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MarkIcon(markFor(choice.state), com.pob31.godot.remote.ui.widgets.stateColour(choice.state), size = 14.dp)
                Spacer(Modifier.width(8.dp))
                if (choice.number.isNotEmpty()) {
                    Text(choice.number, color = GodotColors.ink, style = GodotType.number, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                }
                Text(choice.name, color = GodotColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp))
            }
        }
    }
}

/** How big a rotary is, and how many stand in a row, for the room there is: eight a row when they fit (a D700's bank). */
private fun knobLayout(width: Dp, count: Int): Pair<Int, Dp> {
    val perRow = when {
        width >= 116.dp * 8 -> 8
        width >= 100.dp * 4 -> 4
        else -> 3
    }.coerceAtMost(count.coerceAtLeast(1))
    val size = ((width - 12.dp * perRow) / perRow).coerceIn(76.dp, 108.dp)
    return perRow to size
}

@Composable
private fun KnobRows(knobs: EqReading, writes: TabWrites, tables: SurfaceTables, colour: Color? = null) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val (perRow, size) = knobLayout(maxWidth, knobs.knobs.size)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for (row in knobs.knobs.chunked(perRow)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (knob in row) EqKnobView(knob, knobs, writes, tables, size = size, colour = colour)
                }
            }
        }
    }
}

/** The page's own words under the lock: what it moves rides live, heard and not saved. */
@Composable
private fun LiveNote(key: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LiveWord()
        Text(loc(key), color = GodotColors.waiting, modifier = Modifier.weight(1f))
    }
}

/**
 * THE EQ PAGE (namespace draft §17.14): the aimed cue's sixteen controls in the author's
 * order, two rows of eight where they fit - a D700's two banks - each in its band's colour;
 * the switches above them, in words. A gain's press switches its band, a shape's flips
 * peak and shelf.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqPage(reading: SurfaceReading, writes: TabWrites, tables: SurfaceTables, send: (Sent?) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AimHeader(reading, send)
        if (reading.aimCue == null) return@Column
        val eq = reading.eq
        if (eq == null) {
            Text(loc("surface.noEq"), color = GodotColors.inkFaint)
            return@Column
        }
        if (reading.locked) LiveNote("surface.eqLive")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            eq.on?.let { EqBandSwitch(it, writes) }
            for (switch in eq.switches) EqBandSwitch(switch, writes)
        }
        KnobRows(eq, writes, tables)
        Text(loc("surface.eqHint"), color = GodotColors.inkOff, fontSize = 13.sp)
    }
}

/**
 * THE SEND PAGE: the aimed cue's sends, one rotary each on the fader's law, named by
 * its mix channel; a press switches the send in or out, which keeps its level.
 */
@Composable
private fun SendsPage(reading: SurfaceReading, writes: TabWrites, tables: SurfaceTables, send: (Sent?) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AimHeader(reading, send)
        if (reading.aimCue == null) return@Column
        if (reading.sends.isEmpty()) {
            Text(loc("cues.noSends"), color = GodotColors.inkFaint)
            return@Column
        }
        if (reading.locked) LiveNote("surface.sendsLive")
        KnobRows(sendKnobs(reading.sends, tables.fader), writes, tables, colour = GodotColors.fade)
        Text(loc("surface.sendsHint"), color = GodotColors.inkOff, fontSize = 13.sp)
    }
}

/**
 * THE PAN PAGE (namespace draft §113, the D700's Pan pages on one): where the aimed cue's
 * sends sit in their mixes - a pan or a balance a send, pressed back to the centre; under
 * them the widths of the stereo sends, a press swapping the sides, with each one's swap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanPage(reading: SurfaceReading, writes: TabWrites, tables: SurfaceTables, send: (Sent?) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AimHeader(reading, send)
        if (reading.aimCue == null) return@Column
        if (reading.sends.isEmpty()) {
            Text(loc("cues.noSends"), color = GodotColors.inkFaint)
            return@Column
        }
        val place = placeKnobs(reading.sends)
        if (place.isEmpty) {
            Text(loc("surface.noPlace"), color = GodotColors.inkFaint)
            return@Column
        }
        if (reading.locked) LiveNote("surface.sendsLive")
        if (place.leans.knobs.isNotEmpty()) {
            Text(loc("surface.leans"), color = GodotColors.inkDim)
            KnobRows(place.leans, writes, tables, colour = GodotColors.fade)
        }
        if (place.widths.knobs.isNotEmpty()) {
            Text(loc("surface.widths"), color = GodotColors.inkDim)
            KnobRows(place.widths, writes, tables, colour = GodotColors.fade)
            val names = reading.sends.associate { "swap:${it.id}" to it.busName }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (swap in place.widths.switches) SwapSwitch(swap, names[swap.row], writes)
            }
        }
        Text(loc("surface.panHint"), color = GodotColors.inkOff, fontSize = 13.sp)
    }
}
