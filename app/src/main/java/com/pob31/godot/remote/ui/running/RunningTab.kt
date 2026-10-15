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

package com.pob31.godot.remote.ui.running

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.link.LinkedMachine
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.FaderCurve
import com.pob31.godot.remote.model.LinkWrites
import com.pob31.godot.remote.model.Pill
import com.pob31.godot.remote.model.PillState
import com.pob31.godot.remote.model.RunActions
import com.pob31.godot.remote.model.RunRow
import com.pob31.godot.remote.model.Scrub
import com.pob31.godot.remote.model.Sent
import com.pob31.godot.remote.model.TabWrites
import com.pob31.godot.remote.model.clockText
import com.pob31.godot.remote.model.readRuns
import com.pob31.godot.remote.ui.GodotIconMark
import com.pob31.godot.remote.ui.LinkMark
import com.pob31.godot.remote.ui.rememberReading
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotMetrics
import com.pob31.godot.remote.ui.theme.GodotType
import com.pob31.godot.remote.ui.widgets.DbFader
import com.pob31.godot.remote.ui.widgets.Mark
import com.pob31.godot.remote.ui.widgets.MarkIcon
import com.pob31.godot.remote.ui.widgets.MeterBar
import com.pob31.godot.remote.ui.widgets.markFor
import com.pob31.godot.remote.ui.widgets.rememberSurfaceTables
import com.pob31.godot.remote.ui.widgets.stateColour
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val offState: StateFlow<LinkState> = MutableStateFlow(LinkState.OFF)

/**
 * THE RUNNING TAB (A.5, PRD §3.17's "running pane"): what the show is doing now, from the
 * machine on screen's mirror at 25 Hz - kill, stop, advance, prune a round's pills, drag a
 * playhead, ride a sound's trim. Every action is a run's, never a cue's: a cue list row is
 * a cue, and the thing killed is one of its instances.
 */
@Composable
fun RunningTab(onScreen: LinkedMachine?) {
    val link = onScreen?.link
    val rows = rememberReading(link, emptyList<RunRow>()) { readRuns(it) }
    val state by (link?.state ?: offState).collectAsState()
    val writes = remember(link) { link?.let { LinkWrites(it) } ?: TabWrites.NONE }
    RunningView(rows, state, writes, rememberSurfaceTables().fader, hasMachine = onScreen != null)
}

/** The tab drawn from rows - no link of its own, so a test draws and presses it from a fixture. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningView(
    rows: List<RunRow>,
    state: LinkState,
    writes: TabWrites,
    curve: FaderCurve,
    hasMachine: Boolean = true,
    sheetFor: String? = null,
) {
    var sheet by remember { mutableStateOf(sheetFor) }
    var notSent by remember { mutableIntStateOf(0) }
    val send: (Sent?) -> Unit = { sent -> if (sent != null && !writes.send(sent)) notSent++ }
    val live = state == LinkState.LIVE

    BoxWithConstraints(Modifier.fillMaxSize().background(GodotColors.panelRuns)) {
        val wide = maxWidth >= 760.dp
        Column(Modifier.fillMaxSize()) {
            // The tab's line: how many, and the link when it is not live - greyed rows say why in words.
            Row(
                Modifier.fillMaxWidth().background(GodotColors.panel).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    if (rows.isEmpty()) loc("running.title") else loc("running.count", "count" to rows.count { !it.finished }.toString()),
                    color = GodotColors.ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp
                )
                if (hasMachine && !live) LinkMark(state)
                if (notSent > 0) Text(loc("running.notSent"), color = GodotColors.failed, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (rows.isNotEmpty() && wide) {
                    Text(loc("running.hint"), color = GodotColors.inkOff, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                }
            }
            if (rows.isNotEmpty() && !wide) {
                Text(
                    loc("running.hint"), color = GodotColors.inkOff, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().background(GodotColors.panel).padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
            }
            if (rows.isEmpty()) {
                EmptyRuns(hasMachine)
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().alpha(if (live) 1f else 0.45f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(rows, key = { it.id }) { row ->
                        RunRowItem(row, curve, writes, wide, send = send, onOpen = { sheet = row.id })
                    }
                }
            }
        }
    }

    val open = sheet?.let { id -> rows.firstOrNull { it.id == id } }
    if (open != null) {
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = GodotColors.panelHigh) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                RunSheet(open, onSend = { send(it); sheet = null }, onClose = { sheet = null })
            }
        }
    }
}

/** §4.7: the empty state is where the show's own words may be - the page's, from the play. */
@Composable
private fun EmptyRuns(hasMachine: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasMachine) {
            Text(loc("running.emptyLine"), color = GodotColors.inkDim, fontSize = 20.sp)
            Text(loc("running.empty"), color = GodotColors.inkFaint)
        } else {
            Text(loc("remote.tabs.noMachine"), color = GodotColors.inkFaint)
        }
    }
}

/**
 * ONE RUN'S ROW: its state in a mark and a word, what it is, where it is, its strip, its
 * round's pills and - for a sound - its trim. Tap opens its sheet; a LONG swipe either way
 * kills it (PRD §3.17: a deliberate gesture beats a 9 mm cross in the dark), the sheet's
 * Kill being the same without the gesture.
 */
@Composable
fun RunRowItem(row: RunRow, curve: FaderCurve, writes: TabWrites, wide: Boolean, send: (Sent?) -> Unit, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember(row.id) { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    val threshold = width * 0.6f
    val killable = RunActions.kill(row) != null
    val current by rememberUpdatedState(row)

    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
        if (offset.value != 0f) {
            val past = abs(offset.value) >= threshold
            Row(
                Modifier.matchParentSize().background(if (past) GodotColors.failed else GodotColors.failed.copy(alpha = 0.45f)).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (offset.value > 0) Arrangement.Start else Arrangement.End
            ) {
                MarkIcon(Mark.CROSS, Color.Black, size = 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(loc(if (past) "running.swipeLetGo" else "running.swipeKill"), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .fillMaxWidth()
                .background(if (row.depth > 0) GodotColors.panelIn else GodotColors.panel)
                .pointerInput(row.id, killable) {
                    if (!killable) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (abs(offset.value) >= threshold) {
                                    send(RunActions.kill(current))
                                    offset.animateTo(if (offset.value > 0) width.toFloat() else -width.toFloat(), tween(120))
                                    kotlinx.coroutines.delay(1500)
                                    offset.snapTo(0f) // still here: the engine has not ended it yet, or refused
                                } else {
                                    offset.animateTo(0f, tween(180))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, tween(180)) } },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + amount) }
                    }
                }
                .pointerInput(row.id) { detectTapGestures(onTap = { onOpen() }) }
        ) {
            RunRowContent(row, curve, writes, wide, send)
        }
    }
}

@Composable
private fun RunRowContent(row: RunRow, curve: FaderCurve, writes: TabWrites, wide: Boolean, send: (Sent?) -> Unit) {
    val indent = (row.depth * 28).dp
    Column(Modifier.fillMaxWidth().padding(start = 12.dp + indent, end = 12.dp, top = 8.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TitleLine(row, wide)
                WordsLine(row, wide)
                RunStrip(row, writes, send)
                if (row.pills.isNotEmpty()) Pills(row, send)
            }
            if (wide) {
                // The trim's column is kept on every row, so the clocks stand in one column down the tab.
                Spacer(Modifier.width(20.dp))
                Box(Modifier.width(TRIM_WIDTH)) { if (row.hasTrim) TrimBlock(row, curve, writes, Modifier.fillMaxWidth()) }
            }
        }
        if (!wide && row.hasTrim) {
            Spacer(Modifier.height(8.dp))
            TrimBlock(row, curve, writes, Modifier.fillMaxWidth())
        }
    }
}

private val TRIM_WIDTH = 440.dp

/** The state's mark and word, the kind's picture in its accent, the number, the name, and where it is. */
@Composable
private fun TitleLine(row: RunRow, wide: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val colour = stateColour(row.state)
        if (wide) {
            Row(Modifier.widthIn(min = 112.dp), verticalAlignment = Alignment.CenterVertically) {
                MarkIcon(markFor(row.state), colour, size = 20.dp)
                Spacer(Modifier.width(6.dp))
                Text(row.stateKey?.let { loc(it) } ?: row.state, color = colour, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        } else {
            // On a phone the state's word is the first of the line under: the name keeps the room.
            MarkIcon(markFor(row.state), colour, size = 20.dp)
        }
        Spacer(Modifier.width(8.dp))
        val accent = GodotColors.kindAccent(row.kind, row.mode)
        // What it is takes what room there is; the clock keeps its place at the end of the line.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            GodotIconMark(row.icon, accent, size = 24.dp)
            Spacer(Modifier.width(8.dp))
            if (row.number.isNotEmpty()) {
                Text(row.number, color = GodotColors.ink, style = GodotType.number.copy(fontSize = 18.sp), maxLines = 1)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                row.name.ifEmpty { row.cue }, color = GodotColors.ink, fontSize = 19.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
            )
            if (wide) {
                Spacer(Modifier.width(10.dp))
                Text(row.kindKey?.let { loc(it) } ?: row.kind, color = accent, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        clockOf(row)?.let { Text(it, color = colour, style = GodotType.numberLarge.copy(fontSize = 22.sp), maxLines = 1) }
    }
}

/** Where a run is: how far in once let go, how long left in a wait; nothing before either. */
@Composable
private fun clockOf(row: RunRow): String? = when {
    row.counting -> loc("remote.header.left", "time" to clockText(row.remaining))
    row.launched -> clockText(row.position)
    else -> null
}

/** The holder, the part a group is in, its round, its speed, its level, why it failed - each in words. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordsLine(row: RunRow, wide: Boolean) {
    val words = ArrayList<Pair<String, Color>>()
    if (!wide) {
        words += (row.stateKey?.let { loc(it) } ?: row.state) to stateColour(row.state)
        words += (row.kindKey?.let { loc(it) } ?: row.kind) to GodotColors.kindAccent(row.kind, row.mode)
    }
    if (row.parentLabel.isNotEmpty()) words += loc("running.inGroup", "group" to row.parentLabel) to GodotColors.inkFaint
    if (row.phase.isNotEmpty() && row.kind == "group") words += (loc("running.phase.${row.phase}").takeUnless { it.startsWith("running.") } ?: row.phase) to GodotColors.inkDim
    row.roundCount?.let { (at, of) ->
        words += loc("running.round", "at" to at.toString(), "of" to if (of > 0) of.toString() else "∞") to GodotColors.inkDim
    }
    if (row.kind == "media" && row.rate != 1.0) words += "×" + com.pob31.godot.remote.link.WireText.number(row.rate, 3) to GodotColors.inkDim
    if (row.kind == "media" && row.level != null) words += loc("running.level", "level" to com.pob31.godot.remote.model.FieldText.levelText(row.level)) to GodotColors.inkDim
    if (row.held) words += loc("running.held") to GodotColors.inkDim
    if (row.solo) words += loc("running.soloed") to GodotColors.standby
    if (row.error.isNotEmpty()) words += row.error to GodotColors.failed
    if (words.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for ((text, colour) in words) Text(text, color = colour, maxLines = 1)
    }
}

/**
 * THE STRIP UNDER A RUN (the desktop's bar, PRD §3.30): a sound's playhead over its length,
 * the played part in the colour of what sounds there now (`run/timbre`), grey until the file
 * is analysed - said in words too; a wait's bar emptying right to left, a countdown. A strip
 * the engine says a seek would move is DRAGGED (Scrub: relative from touch-down, 1:1 along
 * it, finer above and below, sliding on at the screen's edge); the ghost head and its clock
 * show where a release would land, and the real head keeps drawing where the sound is.
 */
@Composable
private fun RunStrip(row: RunRow, writes: TabWrites, send: (Sent?) -> Unit) {
    val through = row.through()
    val waiting = row.counting && row.waitTotal > 0.0
    val sound = row.kind == "media" || row.kind == "mic"
    // A sound's strip; a wait's countdown; a scene's only when it can be dragged.
    if (!waiting && !(sound && (through != null || row.timbreUnsaid)) && !(row.kind == "group" && row.scrubbable)) return
    val current by rememberUpdatedState(row)
    var ghost by remember(row.id) { mutableStateOf<Double?>(null) }
    var ghostRate by remember(row.id) { mutableStateOf(1.0) }
    var stripX by remember { mutableStateOf(0f) }
    val windowWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    val density = LocalDensity.current
    val unitPx = with(density) { 40.dp.toPx() }
    val edgePx = with(density) { 28.dp.toPx() }
    val stripHeight = if (row.scrubbable) 40.dp else 14.dp

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ghost?.let { at ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(loc("running.seekTo", "time" to clockText(at)), color = GodotColors.picked, style = GodotType.number, maxLines = 1)
                Scrub.rateText(ghostRate).takeIf { it.isNotEmpty() }?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(loc("running.finer", "rate" to it), color = GodotColors.picked, maxLines = 1)
                }
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(stripHeight)
                .onGloballyPositioned { stripX = it.positionInWindow().x }
                .pointerInput(row.id, row.scrubbable) {
                    if (!row.scrubbable) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val (from, to) = current.span() ?: return@awaitEachGesture
                        val scrub = Scrub()
                        scrub.begin(
                            Scrub.Setup(
                                position = current.position, from = from, to = to,
                                secondsPerPixel = (to - from) / size.width.coerceAtLeast(1),
                                unit = unitPx.toDouble(), x = down.position.x.toDouble(),
                                onRelease = current.kind == "group",
                            )
                        )
                        ghost = scrub.target
                        var last = down.position
                        var pushedAt = SystemClock.uptimeMillis()
                        fun distance(at: Offset) = when {
                            at.y < 0f -> -at.y
                            at.y > size.height -> at.y - size.height
                            else -> 0f
                        }.toDouble()
                        while (true) {
                            val event = withTimeoutOrNull(50) { awaitPointerEvent() }
                            val now = SystemClock.uptimeMillis()
                            if (event == null) {
                                val windowX = stripX + last.x
                                val direction = when {
                                    windowX < edgePx -> -1
                                    windowWidth > 0f && windowX > windowWidth - edgePx -> 1
                                    else -> 0
                                }
                                scrub.push(direction, (now - pushedAt) / 1000.0, distance(last))
                            } else {
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                                last = change.position
                                scrub.moveTo(last.x.toDouble(), distance(last))
                            }
                            pushedAt = now
                            ghost = scrub.target
                            ghostRate = scrub.rate
                            if (scrub.due(now.toDouble())) send(RunActions.seek(current, scrub.target))
                        }
                        if (scrub.settle()) send(RunActions.seek(current, scrub.target))
                        scrub.end()
                        ghost = null
                    }
                }
        ) {
            drawRect(GodotColors.panelIn)
            if (waiting) {
                val left = (row.remaining / row.waitTotal).coerceIn(0.0, 1.0).toFloat()
                drawRect(GodotColors.waiting.copy(alpha = 0.7f), size = Size(size.width * left, size.height))
                return@Canvas
            }
            val at = (through ?: 0.0).toFloat()
            val tint = row.timbre?.let { Color.hsl(((it.hue % 360.0 + 360.0) % 360.0).toFloat(), it.saturation.coerceIn(0.0, 1.0).toFloat(), it.lightness.coerceIn(0.0, 1.0).toFloat()) }
                ?: GodotColors.inkOff
            drawRect(tint.copy(alpha = 0.85f), size = Size(size.width * at, size.height))
            drawRect(Color.White, Offset(size.width * at - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height))
            ghost?.let { g ->
                val (from, to) = row.span() ?: return@let
                val x = (((g - from) / (to - from)).coerceIn(0.0, 1.0) * size.width).toFloat()
                drawRect(GodotColors.picked, Offset(x - 2.dp.toPx(), 0f), Size(4.dp.toPx(), size.height))
            }
        }
        if (sound && row.timbreUnsaid && row.launched) Text(loc("running.notAnalysed"), color = GodotColors.inkOff, fontSize = 12.sp)
        if (row.scrubbable && ghost == null) Text(loc("running.dragToSeek"), color = GodotColors.inkOff, fontSize = 12.sp)
    }
}

/**
 * A GROUP'S ROUND AS PILLS (PRD §3.6): the members in the order they will play - pending
 * plain, sounding marked, played dimmed - then those out of this round dimmed, then those
 * pruned until the group ends dimmed with a cross. Tap: skip it this round (or put back one
 * pruned for good); hold: skip it until the group ends. Run-local: the show is not edited.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun Pills(row: RunRow, send: (Sent?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (pill in row.pills) PillChip(pill, onTap = { send(RunActions.pillTap(row, pill)) }, onHold = { send(RunActions.pillHold(row, pill)) })
        }
        Text(loc("running.pillsHint"), color = GodotColors.inkOff, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PillChip(pill: Pill, onTap: () -> Unit, onHold: () -> Unit) {
    val dim = pill.state == PillState.PLAYED || pill.state == PillState.OUT_THIS_ROUND || pill.state == PillState.PRUNED
    val border = when (pill.state) {
        PillState.PLAYING -> GodotColors.live
        PillState.PENDING -> GodotColors.rule
        else -> GodotColors.panelHigh
    }
    Row(
        Modifier
            .heightIn(min = GodotMetrics.touch)
            .alpha(if (dim) 0.5f else 1f)
            .background(GodotColors.panelHigh, RoundedCornerShape(24.dp))
            .border(2.dp, border, RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onTap, onLongClick = onHold)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (pill.state) {
            PillState.PLAYING -> MarkIcon(Mark.PLAYING, GodotColors.live, size = 16.dp)
            PillState.PRUNED -> MarkIcon(Mark.CROSS, GodotColors.failed, size = 16.dp)
            PillState.PLAYED -> MarkIcon(Mark.DONE, GodotColors.inkFaint, size = 16.dp)
            else -> Unit
        }
        if (pill.state != PillState.PENDING && pill.state != PillState.OUT_THIS_ROUND) Spacer(Modifier.width(6.dp))
        if (pill.number.isNotEmpty()) {
            Text(pill.number, color = GodotColors.ink, style = GodotType.number, maxLines = 1)
            Spacer(Modifier.width(6.dp))
        }
        Text(pill.name, color = GodotColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
        val word = when (pill.state) {
            PillState.PLAYED -> loc("running.pill.played")
            PillState.OUT_THIS_ROUND -> loc("running.pill.out")
            PillState.PRUNED -> loc("running.pill.pruned")
            else -> null
        }
        word?.let {
            Spacer(Modifier.width(6.dp))
            Text(it, color = GodotColors.inkFaint, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** A sound's trim, tonight's and never stored: big, precise, the meter after the fader under it. */
@Composable
private fun TrimBlock(row: RunRow, curve: FaderCurve, writes: TabWrites, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(loc("running.trim"), color = GodotColors.inkFaint, fontSize = 13.sp, maxLines = 1)
        DbFader(
            value = row.trim ?: 0.0, address = row.trimAddress, writes = writes, curve = curve,
            colour = GodotColors.live, label = loc("running.trim"), thickness = 60.dp,
        )
        MeterBar(row.meter, curve, Modifier.fillMaxWidth().padding(end = 110.dp))
    }
}

/**
 * A RUN'S SHEET: every action the row has, as buttons with words - the swipe's kill among
 * them, so no action is a gesture only (PRD §3.2). Stop's verbs are the engine's, said: a
 * group's can wait for the end of its member or its round.
 */
@Composable
fun RunSheet(row: RunRow, onSend: (Sent?) -> Unit, onClose: () -> Unit) {
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GodotIconMark(row.icon, GodotColors.kindAccent(row.kind, row.mode), size = 28.dp)
            Spacer(Modifier.width(10.dp))
            if (row.number.isNotEmpty()) {
                Text(row.number, color = GodotColors.ink, style = GodotType.number.copy(fontSize = 20.sp))
                Spacer(Modifier.width(10.dp))
            }
            Text(row.name.ifEmpty { row.cue }, color = GodotColors.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(12.dp))
            MarkIcon(markFor(row.state), stateColour(row.state))
            Spacer(Modifier.width(6.dp))
            Text(row.stateKey?.let { loc(it) } ?: row.state, color = stateColour(row.state), fontWeight = FontWeight.SemiBold)
        }
        if (row.finished) Text(loc("running.over"), color = GodotColors.inkFaint)
        for (verb in RunActions.stopVerbs(row)) {
            SheetButton(loc(verb.key), GodotColors.stopping, Color.Black) { onSend(RunActions.stop(row, verb)) }
        }
        RunActions.advance(row)?.let { sent -> SheetButton(loc("running.advance"), GodotColors.panel, GodotColors.ink) { onSend(sent) } }
        RunActions.solo(row)?.let { sent -> SheetButton(loc(if (row.solo) "running.unsolo" else "running.solo"), GodotColors.panel, GodotColors.ink) { onSend(sent) } }
        RunActions.kill(row)?.let { sent ->
            Spacer(Modifier.height(8.dp)) // a row's height of air before the one that cuts
            val words = if (row.descendants > 0) loc("running.killWithMembers", "count" to row.descendants.toString()) else loc("running.kill")
            SheetButton(words, GodotColors.failed, Color.Black) { onSend(sent) }
        }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = GodotMetrics.touch)) {
            Text(loc("running.close"), color = GodotColors.inkDim)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SheetButton(text: String, ground: Color, ink: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = ground, contentColor = ink),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
}
