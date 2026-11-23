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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pob31.godot.remote.link.WireText
import com.pob31.godot.remote.model.BarDrag
import com.pob31.godot.remote.model.BarNumber
import com.pob31.godot.remote.model.EqKnob
import com.pob31.godot.remote.model.EqLaw
import com.pob31.godot.remote.model.EqLaws
import com.pob31.godot.remote.model.TabWrites
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotMetrics

/*  THE EQ'S AND THE DYNAMICS' NUMBERS BESIDE THEIR GRAPHS (the desktop's number bars, 388845f1):
    a bar laid flat that a finger drags, finer away from it, and the number in a box that can
    be typed in - never the number said twice. Every hand on them - a graph's handle, a
    pinch, a bar, a box - goes through one [HeldNumbers], so what was sent is what is drawn
    until the engine says it back. */

/**
 * VALUES SENT AND NOT YET HEARD BACK, by address: drawn instead of the engine's for a second
 * and a half at most, as [com.pob31.godot.remote.model.SentValue] does for a box - so a curve
 * never flicks back to where it was while a write is on its way. A drag is one undo step:
 * [begin] touches its rows, [move] writes them throttled, [end] lands the last and releases.
 */
class HeldNumbers(private val writes: TabWrites, private val holdMs: Long = 1500) {
    private val held = mutableStateMapOf<String, Pair<Double, Long>>()
    private val touched = LinkedHashSet<String>()

    /** What to draw for [knob]: the value sent while it waits, else the engine's. */
    fun valueOf(knob: EqKnob): Double {
        val (sent, at) = held[knob.address] ?: return knob.value
        if (kotlin.math.abs(sent - knob.value) < 1e-6 || SystemClock.uptimeMillis() - at > holdMs) return knob.value
        return sent
    }

    fun begin(vararg addresses: String) {
        for (address in addresses) if (touched.add(address)) writes.touch(address)
    }

    fun move(address: String, value: Double) {
        held[address] = value to SystemClock.uptimeMillis()
        writes.set(address, WireText.number(value, 2))
    }

    fun end() {
        for (address in touched) {
            held[address]?.let { (value, _) ->
                writes.set(address, WireText.number(value, 2))
                held[address] = value to SystemClock.uptimeMillis()
            }
            writes.release(address)
        }
        touched.clear()
    }

    /** A value now, not dragged: a typed number, a double tap. */
    fun setNow(address: String, value: Double) {
        held[address] = value to SystemClock.uptimeMillis()
        writes.setNow(address, WireText.number(value, 2))
    }
}

@Composable
fun rememberHeldNumbers(writes: TabWrites, key: Any?): HeldNumbers = remember(writes, key) { HeldNumbers(writes) }

/**
 * ONE NUMBER ON A BAR: its [label], the bar (dragged along, finer away from it), and its
 * number in a box to type into, with its unit. A gain fills from the centre, everything
 * else from the start, each by its law. [dimmed] while its half is out - the switch above
 * says so in words. The live mark beside the label under the lock (§4.8).
 */
@Composable
fun NumberBar(
    knob: EqKnob,
    label: String,
    held: HeldNumbers,
    colour: Color,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    enabled: Boolean = true,
    labelWidth: Dp = 104.dp,
) {
    val control = knob.control
    val value = held.valueOf(knob)
    val place = EqLaws.place(control.law, value, control.min, control.max)
    val shownPlace by rememberUpdatedState(place)
    var dragging by remember(knob.address) { mutableStateOf<Double?>(null) }
    val drawn = dragging ?: place
    val number = BarNumber.of(control.law, value)
    val unitPx = with(LocalDensity.current) { 48.dp.toPx() }
    val ink = if (dimmed || !enabled) GodotColors.inkOff else colour

    fun valueAt(at: Double) = EqLaws.grained(control.law, EqLaws.valueAt(control.law, at, control.min, control.max), control.min, control.max)

    Row(
        modifier.alpha(if (enabled) 1f else 0.55f).heightIn(min = GodotMetrics.touch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(labelWidth), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (dimmed) GodotColors.inkFaint else GodotColors.inkDim, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (knob.live) {
                Spacer(Modifier.width(4.dp))
                MarkIcon(Mark.LIVE, GodotColors.waiting, size = 12.dp)
            }
        }
        Canvas(
            Modifier
                .weight(1f)
                .height(GodotMetrics.touch)
                .semantics {
                    contentDescription = label
                    stateDescription = number.text + (number.unit?.let { " $it" } ?: "")
                }
                .pointerInput(knob.address, enabled) {
                    if (!enabled) return@pointerInput
                    var drag: BarDrag? = null
                    var last = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { at ->
                            drag = BarDrag(shownPlace, size.width.toFloat(), unitPx)
                            last = at.x
                            held.begin(knob.address)
                        },
                        onDragEnd = {
                            held.end()
                            dragging = null
                            drag = null
                        },
                        onDragCancel = {
                            held.end()
                            dragging = null
                            drag = null
                        },
                    ) { change, _ ->
                        val at = change.position
                        val across = when {
                            at.y < 0f -> -at.y
                            at.y > size.height -> at.y - size.height
                            else -> 0f
                        }
                        val moved = drag?.move(at.x - last, across) ?: return@detectHorizontalDragGestures
                        last = at.x
                        change.consume()
                        val next = valueAt(moved)
                        dragging = moved
                        held.move(knob.address, next)
                    }
                }
        ) {
            val track = size.height * 0.42f
            val top = (size.height - track) / 2f
            val radius = CornerRadius(4.dp.toPx())
            drawRoundRect(ink.copy(alpha = 0.22f), Offset(0f, top), Size(size.width, track), radius)
            val at = (drawn.toFloat() * size.width).coerceIn(0f, size.width)
            if (control.law == EqLaw.GAIN) {
                val centre = size.width / 2f
                drawRect(ink.copy(alpha = 0.75f), Offset(minOf(centre, at), top), Size(kotlin.math.abs(at - centre), track))
            } else {
                drawRoundRect(ink.copy(alpha = 0.75f), Offset(0f, top), Size(at, track), radius)
            }
            val cap = 4.dp.toPx()
            drawRect(Color.White.copy(alpha = if (dimmed) 0.5f else 1f), Offset((at - cap / 2).coerceIn(0f, size.width - cap), top - 4.dp.toPx()), Size(cap, track + 8.dp.toPx()))
        }
        Spacer(Modifier.width(10.dp))
        NumberBox(
            shown = number.text,
            onSend = { text ->
                val typed = text.toDoubleOrNull() ?: return@NumberBox
                held.setNow(knob.address, EqLaws.grained(control.law, typed * number.scale, control.min, control.max))
            },
            decimals = number.decimals,
            signed = control.min < 0.0,
            unit = number.unit,
            enabled = enabled,
            width = 92.dp,
        )
    }
}

/**
 * A CHOICE OF TWO: a band's peak or shelf, the detector's peak or RMS - each word a button,
 * the one in force lit and ticked (the tick says what the colour does).
 */
@Composable
fun ShapeChoice(knob: EqKnob, label: String, writes: TabWrites, enabled: Boolean = true, labelWidth: Dp = 104.dp) {
    val shelf = knob.control.shelf
    Row(Modifier.heightIn(min = GodotMetrics.touch), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(labelWidth), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = GodotColors.inkDim, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (knob.live) {
                Spacer(Modifier.width(4.dp))
                MarkIcon(Mark.LIVE, GodotColors.waiting, size = 12.dp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (shape in listOf("peak", shelf)) {
                val chosen = (knob.shape.ifEmpty { "peak" }) == shape
                Row(
                    Modifier
                        .heightIn(min = GodotMetrics.touch)
                        .background(if (chosen) GodotColors.picked else GodotColors.panelHigh, RoundedCornerShape(6.dp))
                        .clickable(enabled = enabled && !chosen) { writes.setNow(knob.address, shape) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (chosen) {
                        MarkIcon(Mark.DONE, Color.Black, size = 14.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(EqLaws.shapeText(shape), color = if (chosen) Color.Black else GodotColors.ink)
                }
            }
        }
    }
}
