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

package com.pob31.godot.remote.ui.widgets

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.EqCurve
import com.pob31.godot.remote.model.EqKnob
import com.pob31.godot.remote.model.EqLaw
import com.pob31.godot.remote.model.EqLaws
import com.pob31.godot.remote.model.EqReading
import com.pob31.godot.remote.model.GraphMoves
import com.pob31.godot.remote.model.TabWrites
import com.pob31.godot.remote.model.shape
import com.pob31.godot.remote.ui.theme.GodotColors
import kotlin.math.hypot

/*  THE EQ AS A GRAPH (the desktop's EQ panel, EqPanelComponent): what the EQ does to the
    sound, 20 Hz to 20 kHz across in octaves and ±24 dB up, worked out as the engine works
    it (model EqCurve) - and on it a handle a band, each in its band's colour AND named
    (1 to 4, HP, LP: §4.8), hollow while its band is out. A finger takes a handle where it
    is and moves it with itself: across is the frequency, up the gain (a filter's only its
    frequency). Two fingers anywhere on the graph pinch the picked band's width. A tap picks
    a handle - its numbers are the bars under the band switches - and a second tap on it
    puts its gain back to nought. A touch on the graph's empty ground is the page's own: it
    scrolls. A drag is one undo step for both its rows. */

/** A handle on the graph: a band (1..4) or a filter (0 the high-pass, 5 the low-pass). */
private data class EqHandle(val index: Int, val freq: EqKnob, val gain: EqKnob?, val q: EqKnob?, val shape: EqKnob?, val on: Boolean) {
    val isBand: Boolean get() = index in 1..4
    val word: String get() = when (index) { 0 -> "HP"; 5 -> "LP"; else -> index.toString() }
}

private fun handlesOf(eq: EqReading): List<EqHandle> {
    val byRow = eq.knobs.associateBy { it.control.row }
    val on = eq.switches.associate { it.row to it.on }
    val out = ArrayList<EqHandle>()
    byRow["eqHpfFreq"]?.let { out += EqHandle(0, it, null, null, null, on["eqHpf"] == true) }
    for (n in 1..4) {
        val freq = byRow["eqB${n}Freq"] ?: continue
        out += EqHandle(n, freq, byRow["eqB${n}Gain"], byRow["eqB${n}Q"], byRow["eqB${n}Shape"], on["eqB${n}On"] != false)
    }
    byRow["eqLpfFreq"]?.let { out += EqHandle(5, it, null, null, null, on["eqLpf"] == true) }
    return out
}

/** The gain a filter's handle sits at: where its section is 3 dB down. */
private const val FILTER_HANDLE_DB = -3.0

/**
 * THE EQ EDITOR, as the Surface's EQ page and the inspector both draw it: the graph, the
 * switches under it (the whole EQ, the filters, the bands - each in words), and the picked
 * handle's numbers on bars.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqEditor(eq: EqReading, writes: TabWrites, graphHeight: Dp, enabled: Boolean = true, key: Any? = null) {
    val held = rememberHeldNumbers(writes, key)
    var picked by rememberSaveable { mutableIntStateOf(2) }
    val handles = handlesOf(eq)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EqGraph(eq, handles, held, picked, { picked = it }, Modifier.fillMaxWidth().height(graphHeight), enabled)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            eq.on?.let { EqBandSwitch(it, writes, enabled) }
            for (switch in eq.switches) EqBandSwitch(switch, writes, enabled)
        }
        val handle = handles.firstOrNull { it.index == picked } ?: handles.firstOrNull()
        if (handle != null) {
            val colour = GodotColors.eq[handle.index.coerceIn(0, GodotColors.eq.lastIndex)]
            val dimmed = !handle.on || eq.on?.on == false
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                handle.shape?.let { ShapeChoice(it, it.control.label, writes, enabled) }
                NumberBar(handle.freq, handle.freq.control.label, held, colour, dimmed = dimmed, enabled = enabled)
                handle.gain?.let { NumberBar(it, it.control.label, held, colour, dimmed = dimmed, enabled = enabled) }
                handle.q?.let { NumberBar(it, it.control.label, held, colour, dimmed = dimmed, enabled = enabled) }
            }
        }
    }
}

@Composable
private fun EqGraph(
    eq: EqReading,
    handles: List<EqHandle>,
    held: HeldNumbers,
    picked: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    val measurer = rememberTextMeasurer()
    val now by rememberUpdatedState(handles)
    val pickedNow by rememberUpdatedState(picked)
    val slop = LocalViewConfiguration.current.touchSlop
    val doubleTapMs = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val lastTap = remember { longArrayOf(0L, -1L) }   // when, which
    val shape = eq.shape { held.valueOf(it) }
    val curve = EqCurve.curve(shape)
    val out = eq.on?.on == false
    val description = loc("widgets.eqGraph")

    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(enabled, held) {
                if (!enabled) return@pointerInput
                val reach = 28.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    fun placeOf(handle: EqHandle): Offset {
                        val gain = handle.gain?.let { held.valueOf(it) } ?: FILTER_HANDLE_DB
                        return Offset(
                            GraphMoves.xOfFrequency(held.valueOf(handle.freq), w),
                            GraphMoves.eqGain.pixelOf(gain, h),
                        )
                    }
                    val grabbed = now
                        .map { it to (placeOf(it) - down.position).getDistance() }
                        .filter { it.second <= reach }
                        .minByOrNull { it.second }?.first
                    if (grabbed != null) {
                        down.consume()
                        onPick(grabbed.index)
                    }
                    val start = grabbed?.let { placeOf(it) }
                    var dragging = false
                    var pinch: Pair<Float, Double>? = null   // start distance, start Q
                    var pinchQ: EqKnob? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2 && !dragging) {
                            val a = pressed[0].position
                            val b = pressed[1].position
                            val distance = hypot(a.x - b.x, a.y - b.y)
                            val q = pinchQ ?: now.firstOrNull { it.index == (grabbed?.index ?: pickedNow) }?.q
                            if (q != null) {
                                pinchQ = q
                                val begun = pinch ?: (distance to held.valueOf(q)).also {
                                    pinch = it
                                    held.begin(q.address)
                                }
                                held.move(q.address, GraphMoves.pinchedQ(begun.second, begun.first, distance))
                                event.changes.forEach { it.consume() }
                            }
                            continue
                        }
                        if (grabbed == null || start == null || pinch != null) continue
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                        val moved = change.position - down.position
                        if (!dragging && moved.getDistance() <= slop) continue
                        change.consume()
                        if (!dragging) {
                            dragging = true
                            grabbed.gain?.let { held.begin(grabbed.freq.address, it.address) } ?: held.begin(grabbed.freq.address)
                        }
                        val f = grabbed.freq.control
                        held.move(grabbed.freq.address, EqLaws.grained(EqLaw.FREQUENCY, GraphMoves.frequencyAtX(start.x + moved.x, w), f.min, f.max))
                        grabbed.gain?.let { gain ->
                            val g = gain.control
                            held.move(gain.address, EqLaws.grained(EqLaw.GAIN, GraphMoves.eqGain.dbAt(start.y + moved.y, h), g.min, g.max))
                        }
                    }
                    if (dragging || pinch != null) {
                        held.end()
                        lastTap[0] = 0L
                    } else if (grabbed != null) {
                        // A second tap on the same band's handle: its gain back to nought (the desktop's double click).
                        val at = SystemClock.uptimeMillis()
                        if (grabbed.isBand && lastTap[1] == grabbed.index.toLong() && at - lastTap[0] <= doubleTapMs) {
                            grabbed.gain?.let { held.setNow(it.address, 0.0) }
                            lastTap[0] = 0L
                        } else {
                            lastTap[0] = at
                            lastTap[1] = grabbed.index.toLong()
                        }
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        drawRoundRect(GodotColors.panelIn, cornerRadius = CornerRadius(6.dp.toPx()))
        eqGrid(measurer)

        // The curve, with the ground between it and nought washed.
        val zero = GraphMoves.eqGain.pixelOf(0.0, h)
        val line = Path()
        val wash = Path().apply { moveTo(0f, zero) }
        curve.forEachIndexed { i, (at, db) ->
            val x = (at * w).toFloat()
            val y = GraphMoves.eqGain.pixelOf(db, h)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            wash.lineTo(x, y)
        }
        wash.lineTo(w, zero)
        wash.close()
        val ink = if (out) GodotColors.inkOff else GodotColors.ink
        drawPath(wash, ink.copy(alpha = 0.10f))
        drawPath(line, ink.copy(alpha = if (out) 0.6f else 0.95f), style = Stroke(2.5.dp.toPx()))

        // The handles, the picked one ringed.
        val radius = 13.dp.toPx()
        for (handle in handles) {
            val gain = handle.gain?.let { held.valueOf(it) } ?: FILTER_HANDLE_DB
            val c = Offset(GraphMoves.xOfFrequency(held.valueOf(handle.freq), w), GraphMoves.eqGain.pixelOf(gain, h))
            val colour = GodotColors.eq[handle.index.coerceIn(0, GodotColors.eq.lastIndex)].let { if (out) it.copy(alpha = 0.5f) else it }
            if (handle.index == picked) drawCircle(Color.White, radius + 6.dp.toPx(), c, style = Stroke(2.dp.toPx()))
            if (handle.isBand) {
                if (handle.on) drawCircle(colour, radius, c) else drawCircle(colour, radius - 1.5.dp.toPx(), c, style = Stroke(3.dp.toPx()))
            } else {
                val side = radius * 1.8f
                val topLeft = Offset(c.x - side / 2, c.y - side / 2)
                if (handle.on) drawRoundRect(colour, topLeft, Size(side, side), CornerRadius(3.dp.toPx()))
                else drawRoundRect(colour, topLeft, Size(side, side), CornerRadius(3.dp.toPx()), style = Stroke(3.dp.toPx()))
            }
            val word = measurer.measure(handle.word, TextStyle(color = if (handle.on) Color.Black else colour, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            drawText(word, topLeft = Offset(c.x - word.size.width / 2f, c.y - word.size.height / 2f))
        }
    }
}

private val gridHz = listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)

private fun DrawScope.eqGrid(measurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val rule = GodotColors.rule
    val words = TextStyle(color = GodotColors.inkFaint, fontSize = 10.sp)
    for (hz in gridHz) {
        val x = GraphMoves.xOfFrequency(hz, w)
        drawLine(rule, Offset(x, 0f), Offset(x, h), 1.dp.toPx())
        val text = measurer.measure(if (hz >= 1000.0) "${(hz / 1000).toInt()}k" else hz.toInt().toString(), words)
        drawText(text, topLeft = Offset(x + 3.dp.toPx(), h - text.size.height - 2.dp.toPx()))
    }
    for (db in listOf(-24.0, -12.0, 0.0, 12.0, 24.0)) {
        val y = GraphMoves.eqGain.pixelOf(db, h)
        drawLine(if (db == 0.0) GodotColors.inkOff else rule, Offset(0f, y), Offset(w, y), (if (db == 0.0) 1.5 else 1.0).dp.toPx())
        if (db != 24.0 && db != -24.0) {
            val text = measurer.measure((if (db > 0) "+" else "") + db.toInt().toString(), words)
            drawText(text, topLeft = Offset(4.dp.toPx(), y - text.size.height - 1.dp.toPx()))
        }
    }
}
