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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.DbAxis
import com.pob31.godot.remote.model.DynCurve
import com.pob31.godot.remote.model.DynReading
import com.pob31.godot.remote.model.EqKnob
import com.pob31.godot.remote.model.EqLaws
import com.pob31.godot.remote.model.GraphMoves
import com.pob31.godot.remote.model.TabWrites
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotType
import kotlin.math.hypot

/*  A SOUND'S OWN DYNAMICS AS TWO GRAPHS (the desktop's Dynamics panel, 388845f1): what comes
    out for what goes in, in decibels both ways, by the voice's own maths (model DynCurve) -
    the compressor's beside the gate's where they fit, one over the other where not, each
    under its own switch, which says its name and whether it is in. On each a round handle
    on the threshold, taken where it is and moved across; the compressor's ratio by a square
    handle at the right, where the loudest sound comes out, moved up and down; the gate's
    range by a square handle under its threshold, moved up and down. Two fingers spread on
    the compressor open its knee. Every number beside its graph on a bar; while the cue
    plays, how far the stage turns it down now beside the compressor. */

private enum class Half { COMPRESSOR, GATE }

private val COMPRESSOR_ROWS = listOf("dynThreshold", "dynRatio", "dynKnee", "dynMakeup", "dynAttack", "dynRelease")
private val GATE_ROWS = listOf("dynGateThreshold", "dynGateRange", "dynGateAttack", "dynGateHold", "dynGateRelease")
private val KEY_ROWS = listOf("dynKeyLowCut", "dynKeyHighCut")

/** A gate's number under the gate's own heading needs no "Gate" before it: "Gate thresh" is the Threshold. */
private fun barLabel(knob: EqKnob): String {
    val label = knob.control.label
    if (!knob.control.row.startsWith("dynGate")) return label
    val bare = label.removePrefix("Gate ").replaceFirstChar { it.uppercase() }
    return if (bare == "Thresh") "Threshold" else bare
}

/**
 * THE DYNAMICS EDITOR, as the Surface's Dynamics page and the inspector both draw it: the
 * stage's switch and what it listens to, the two graphs with their numbers, the key filter
 * and the detector.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DynEditor(dyn: DynReading, writes: TabWrites, graphHeight: Dp, enabled: Boolean = true, key: Any? = null) {
    val held = rememberHeldNumbers(writes, key)
    val eq = dyn.eq
    val byRow = eq.knobs.associateBy { it.control.row }
    val switches = eq.switches.associateBy { it.row }
    val stageOn = switches["dynOn"]?.on != false

    @Composable
    fun bars(rows: List<String>, colour: Color, halfOn: Boolean) {
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (row in rows) {
                val knob = byRow[row] ?: continue
                NumberBar(knob, barLabel(knob), held, colour, dimmed = !stageOn || !halfOn, enabled = enabled)
            }
        }
    }

    @Composable
    fun half(which: Half, modifier: Modifier) {
        val switchRow = if (which == Half.COMPRESSOR) "dynCompOn" else "dynGateOn"
        val halfOn = switches[switchRow]?.on != false
        val colour = GodotColors.eq[if (which == Half.COMPRESSOR) 1 else 3]
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            switches[switchRow]?.let { EqBandSwitch(it, writes, enabled) }
            Row(Modifier.fillMaxWidth().height(graphHeight)) {
                DynGraph(which, byRow, held, colour, lit = stageOn && halfOn, enabled = enabled, modifier = Modifier.weight(1f).fillMaxHeight())
                if (which == Half.COMPRESSOR && dyn.reduction != null) {
                    Spacer(Modifier.width(8.dp))
                    ReductionBar(dyn.reduction, Modifier.width(40.dp).fillMaxHeight())
                }
            }
            bars(if (which == Half.COMPRESSOR) COMPRESSOR_ROWS else GATE_ROWS, colour, halfOn)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            switches["dynOn"]?.let { EqBandSwitch(it, writes, enabled) }
            DynKeyMenu(dyn, writes, enabled)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 840.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    half(Half.COMPRESSOR, Modifier.weight(1f))
                    half(Half.GATE, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    half(Half.COMPRESSOR, Modifier.fillMaxWidth())
                    half(Half.GATE, Modifier.fillMaxWidth())
                }
            }
        }
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (row in KEY_ROWS) byRow[row]?.let { NumberBar(it, it.control.label, held, GodotColors.eq[0], dimmed = !stageOn, enabled = enabled) }
            byRow["dynDetector"]?.let { ShapeChoice(it, it.control.label, writes, enabled) }
        }
    }
}

@Composable
private fun DynGraph(
    which: Half,
    byRow: Map<String, EqKnob>,
    held: HeldNumbers,
    colour: Color,
    lit: Boolean,
    enabled: Boolean,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    val rows by rememberUpdatedState(byRow)
    val slop = LocalViewConfiguration.current.touchSlop
    val compressor = which == Half.COMPRESSOR
    val across = if (compressor) DbAxis(DynCurve.COMP_IN_LOW, DynCurve.COMP_IN_HIGH, upward = false) else DbAxis(DynCurve.GATE_LOW, DynCurve.GATE_HIGH, upward = false)
    val up = if (compressor) DbAxis(DynCurve.COMP_OUT_LOW, DynCurve.COMP_OUT_HIGH, upward = true) else DbAxis(DynCurve.GATE_LOW, DynCurve.GATE_HIGH, upward = true)
    val thresholdRow = if (compressor) "dynThreshold" else "dynGateThreshold"
    val otherRow = if (compressor) "dynRatio" else "dynGateRange"
    val description = loc(if (compressor) "dyn.switch.compressor" else "dyn.switch.gate")

    fun number(row: String, otherwise: Double): Double = rows[row]?.let { held.valueOf(it) } ?: otherwise
    fun out(input: Double): Double =
        if (compressor) DynCurve.compressorOut(input, number("dynThreshold", -20.0), number("dynRatio", 4.0), number("dynKnee", 6.0), number("dynMakeup", 0.0))
        else DynCurve.gateOut(input, number("dynGateThreshold", -50.0), number("dynGateRange", -40.0))

    /** Where the threshold's handle (0) and the other (1) stand, kept inside the graph. */
    fun handleAt(handle: Int, w: Float, h: Float): Offset {
        val threshold = number(thresholdRow, -20.0)
        val (x, y) = when {
            handle == 0 -> across.pixelOf(threshold, w) to up.pixelOf(out(threshold), h)
            compressor -> across.pixelOf(0.0, w) to up.pixelOf(out(0.0), h)
            else -> across.pixelOf(threshold, w) to up.pixelOf(threshold + number("dynGateRange", -40.0), h)
        }
        return Offset(x.coerceIn(0f, w), y.coerceIn(0f, h))
    }

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
                    val grabbed = (0..1).map { it to (handleAt(it, w, h) - down.position).getDistance() }
                        .filter { it.second <= reach }.minByOrNull { it.second }?.first
                    if (grabbed != null) down.consume()
                    val start = grabbed?.let { handleAt(it, w, h) }
                    var dragging = false
                    var pinch: Pair<Float, Double>? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2 && !dragging && compressor) {
                            val knee = rows["dynKnee"] ?: continue
                            val a = pressed[0].position
                            val b = pressed[1].position
                            val distance = hypot(a.x - b.x, a.y - b.y)
                            val begun = pinch ?: (distance to held.valueOf(knee)).also {
                                pinch = it
                                held.begin(knee.address)
                            }
                            held.move(knee.address, GraphMoves.pinchedKnee(begun.second, begun.first, distance, w))
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        if (grabbed == null || start == null || pinch != null) continue
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                        val moved = change.position - down.position
                        if (!dragging && moved.getDistance() <= slop) continue
                        change.consume()
                        val row = if (grabbed == 0) thresholdRow else otherRow
                        val knob = rows[row] ?: continue
                        if (!dragging) {
                            dragging = true
                            held.begin(knob.address)
                        }
                        val c = knob.control
                        val value = when {
                            grabbed == 0 -> across.dbAt(start.x + moved.x, w)
                            compressor -> DynCurve.ratioFromHandle(up.dbAt(start.y + moved.y, h), number("dynThreshold", -20.0), number("dynMakeup", 0.0)) ?: continue
                            else -> up.dbAt(start.y + moved.y, h) - number("dynGateThreshold", -50.0)
                        }
                        held.move(knob.address, EqLaws.grained(c.law, value, c.min, c.max))
                    }
                    if (dragging || pinch != null) held.end()
                }
            }
    ) {
        val w = size.width
        val h = size.height
        drawRoundRect(GodotColors.panelIn, cornerRadius = CornerRadius(6.dp.toPx()))
        val words = TextStyle(color = GodotColors.inkFaint, fontSize = 10.sp)
        val step = if (compressor) 12.0 else 15.0
        var db = across.low
        while (db <= across.high + 1e-9) {
            val x = across.pixelOf(db, w)
            drawLine(GodotColors.rule, Offset(x, 0f), Offset(x, h), 1.dp.toPx())
            if (db < across.high) {
                val text = measurer.measure(db.toInt().toString(), words)
                drawText(text, topLeft = Offset(x + 3.dp.toPx(), h - text.size.height - 2.dp.toPx()))
            }
            db += step
        }
        db = up.low
        while (db <= up.high + 1e-9) {
            val y = up.pixelOf(db, h)
            drawLine(if (db == 0.0) GodotColors.inkOff else GodotColors.rule, Offset(0f, y), Offset(w, y), 1.dp.toPx())
            if (db > up.low && db < up.high) {
                val text = measurer.measure((if (db > 0) "+" else "") + db.toInt().toString(), words)
                drawText(text, topLeft = Offset(4.dp.toPx(), y - text.size.height - 1.dp.toPx()))
            }
            db += step
        }
        // Unity: what would come out with nothing in the way.
        drawLine(GodotColors.inkOff.copy(alpha = 0.6f), Offset(across.pixelOf(across.low, w), up.pixelOf(across.low, h)), Offset(across.pixelOf(across.high, w), up.pixelOf(across.high, h)), 1.dp.toPx())

        val line = Path()
        val points = w.toInt().coerceAtLeast(2)
        for (i in 0..points) {
            val x = w * i / points
            val y = up.pixelOf(out(across.dbAt(x, w)), h)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val ink = if (lit) colour else GodotColors.inkOff
        drawPath(line, ink, style = Stroke(2.5.dp.toPx()))

        val radius = 12.dp.toPx()
        val threshold = handleAt(0, w, h)
        drawCircle(ink, radius, threshold)
        drawCircle(Color.White, radius, threshold, style = Stroke(2.dp.toPx()))
        val other = handleAt(1, w, h)
        val side = radius * 1.8f
        drawRoundRect(ink, Offset(other.x - side / 2, other.y - side / 2), Size(side, side), CornerRadius(3.dp.toPx()))
        drawRoundRect(Color.White, Offset(other.x - side / 2, other.y - side / 2), Size(side, side), CornerRadius(3.dp.toPx()), style = Stroke(2.dp.toPx()))
    }
}

/** How far the stage turns the sound down now: a bar from the top, 24 dB its foot, its number under it. */
@Composable
private fun ReductionBar(reduction: Double, modifier: Modifier) {
    val depth = (reduction / DynCurve.REDUCTION_FLOOR).coerceIn(0.0, 1.0).toFloat()
    val words = EqLaws.fixed(kotlin.math.abs(reduction), 1)
    val description = loc("dyn.reduction")
    Column(
        modifier.semantics {
            contentDescription = description
            stateDescription = "$words dB"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.width(14.dp).weight(1f)) {
            drawRect(GodotColors.panelIn)
            drawRect(GodotColors.waiting, size = Size(size.width, size.height * depth))
        }
        Text(words, color = GodotColors.inkFaint, style = GodotType.number.copy(fontSize = 12.sp), maxLines = 1)
    }
}
