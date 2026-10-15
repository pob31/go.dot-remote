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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pob31.godot.remote.ui.theme.GodotColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * HOW A FINGER TURNS A ROTARY (PRD §3.17, the Soundcraft pattern): relative from
 * touch-down - the ring does not jump to the finger - by the ANGLE the finger sweeps
 * around the centre, and the DISTANCE from the centre multiplies the precision: inside
 * the knob a sweep is the ring's own (300° the whole travel); out at twice the radius it
 * moves half as far, at four times a quarter - a continuous ratio, so nothing jumps as the
 * hand drifts out mid-gesture. Near the centre the angle means nothing and is not read.
 */
class RotaryTurn(private val radiusPx: Float, startPlace: Double, private val deadPx: Float = radiusPx * 0.25f) {
    var place = startPlace
        private set
    private var lastAngle: Double? = null

    /** The finger is at ([dx], [dy]) from the centre, y down. Where the ring now stands, 0..1. */
    fun moveTo(dx: Float, dy: Float): Double {
        val r = hypot(dx, dy)
        val angle = atan2(dy.toDouble(), dx.toDouble())
        val before = lastAngle
        lastAngle = if (r < deadPx) null else angle
        if (before == null || r < deadPx) return place
        var swept = angle - before
        while (swept > PI) swept -= 2 * PI
        while (swept < -PI) swept += 2 * PI
        place = (place + swept / TRAVEL * gainAt(r)).coerceIn(0.0, 1.0)
        return place
    }

    /** One inside the knob, the radius over the distance outside it. */
    fun gainAt(r: Float): Double = if (r <= radiusPx || radiusPx <= 0f) 1.0 else (radiusPx / r).toDouble()

    companion object {
        /** The ring's whole travel: 300°, a dead zone of 60° at the bottom, as WFS-DIY's RotaryKnob. */
        const val TRAVEL = 300.0 / 180.0 * PI
    }
}

/**
 * A ROTARY, as WFS-DIY's RotaryKnob draws one - a ring of 300° with its gap at the bottom,
 * the travel dim and the part turned bright, a dot where it stands - in its band's colour,
 * its label above and its value in words inside (§4.8). A gain fills from the centre
 * ([bipolar]); everything else from the start. [dimmed] with its [word] for a band switched
 * out. A press (a touch that does not turn) does what the surface tables say a press does.
 *
 * It reports where the ring stands, 0..1, as the finger turns it ([onTurn]), between
 * [onStart] and [onEnd]: the caller maps that to a value by the control's law and writes it.
 */
@Composable
fun Rotary(
    place: Double,
    label: String,
    valueText: String,
    colour: Color,
    modifier: Modifier = Modifier,
    bipolar: Boolean = false,
    dimmed: Boolean = false,
    word: String? = null,
    live: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 92.dp,
    onStart: () -> Unit = {},
    onTurn: (Double) -> Unit = {},
    onEnd: (Double) -> Unit = {},
    onPress: (() -> Unit)? = null,
) {
    var turning by remember { mutableStateOf<Double?>(null) }
    val shown = turning ?: place
    val at by rememberUpdatedState(place)
    val slop = LocalViewConfiguration.current.touchSlop
    val ink = if (dimmed || !enabled) GodotColors.inkOff else colour

    Column(
        modifier
            .width(size + 8.dp)
            .alpha(if (enabled) 1f else 0.55f)
            .semantics {
                contentDescription = label
                stateDescription = valueText + (word?.let { ", $it" } ?: "")
                if (onPress != null) role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = if (dimmed) GodotColors.inkFaint else GodotColors.inkDim, fontSize = 13.sp, maxLines = 1, fontWeight = FontWeight.Medium)
        // Ridden live under the lock: said, with the live mark (§4.8).
        Box(Modifier.size(width = size, height = 16.dp), contentAlignment = Alignment.Center) {
            if (live) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    MarkIcon(Mark.LIVE, GodotColors.waiting, size = 12.dp)
                    Text(" " + com.pob31.godot.remote.localization.loc("widgets.live"), color = GodotColors.waiting, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(enabled, onPress != null) {
                        if (!enabled) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val centre = Offset(this.size.width / 2f, this.size.height / 2f)
                            val radius = min(this.size.width, this.size.height) / 2f
                            val turn = RotaryTurn(radius, at)
                            turn.moveTo(down.position.x - centre.x, down.position.y - centre.y)
                            var moved = false
                            var started = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                                if (!moved && (change.position - down.position).getDistance() > slop) moved = true
                                val place = turn.moveTo(change.position.x - centre.x, change.position.y - centre.y)
                                if (moved) {
                                    if (!started) {
                                        started = true
                                        onStart()
                                    }
                                    turning = place
                                    onTurn(place)
                                }
                            }
                            if (started) onEnd(turn.place)
                            else if (!moved) onPress?.invoke()
                            turning = null
                        }
                    }
            ) {
                val stroke = this.size.minDimension * 0.09f
                val inset = stroke / 2f + 2f
                val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                // Compose's angles start at three o'clock and run clockwise: the travel starts at 120°.
                drawArc(ink.copy(alpha = 0.28f), 120f, 300f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                val sweep = (shown * 300.0).toFloat()
                if (bipolar) {
                    drawArc(ink.copy(alpha = 0.85f), 270f, sweep - 150f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                } else if (sweep > 0f) {
                    drawArc(ink.copy(alpha = 0.85f), 120f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                val angle = Math.toRadians(120.0 + sweep)
                val r = arcSize.width / 2f
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                drawCircle(Color.White.copy(alpha = if (dimmed) 0.5f else 1f), stroke * 0.75f, Offset(c.x + (cos(angle) * r).toFloat(), c.y + (sin(angle) * r).toFloat()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(valueText, color = if (dimmed) GodotColors.inkFaint else GodotColors.ink, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1)
                word?.let { Text(it, color = GodotColors.inkFaint, fontSize = 11.sp, maxLines = 1) }
            }
        }
    }
}
