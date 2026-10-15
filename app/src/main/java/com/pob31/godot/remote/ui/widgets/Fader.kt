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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pob31.godot.remote.link.WireText
import com.pob31.godot.remote.model.EqLaw
import com.pob31.godot.remote.model.EqLaws
import com.pob31.godot.remote.model.FaderCurve
import com.pob31.godot.remote.model.FieldText
import com.pob31.godot.remote.model.Scrub
import com.pob31.godot.remote.model.SentValue
import com.pob31.godot.remote.model.TabWrites
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotType

/**
 * HOW A FINGER MOVES A FADER (PRD §3.17): relative from touch-down - the cap does not jump
 * to the finger - with displacement ALONG the fader the value and displacement ACROSS it
 * the divisor: every [unit] away from the fader's band halves what a pixel moves, smoothly
 * (the scrub's own [Scrub.rateFor], down to 1/256), integrated step by step so the cap never
 * jumps as the hand drifts. A level is kept to a tenth of a decibel, and the bottom of the
 * travel is silence exactly.
 */
class FaderDrag(private val curve: FaderCurve, startDb: Double, private val lengthPx: Float, private val unitPx: Float) {
    private var place = curve.fractionForDb(startDb)

    /** The finger moved [along] pixels toward the top, [across] pixels outside the fader's band. The level now. */
    fun move(along: Float, across: Float): Double {
        if (lengthPx > 0f) place = (place + along / lengthPx * Scrub.rateFor(across.toDouble(), unitPx.toDouble())).coerceIn(0.0, 1.0)
        return level()
    }

    fun level(): Double =
        if (place <= 0.0) curve.silenceDb else EqLaws.grained(EqLaw.LEVEL, curve.dbForFraction(place), curve.silenceDb, curve.loudestDb, curve.silenceDb)
}

/**
 * A LEVEL ON A FADER, in decibels along the generic curve (the surface tables' `fader`),
 * horizontal or vertical, with its number always beside it (§4.8: a fader's position is a
 * colour-like fact) in a box that can be typed in. The look is WFS-DIY's StandardSlider: a
 * dim track, the part below the level brighter, a white line for the cap - with the marks
 * of a desk's scale under it.
 *
 * A DRAG IS ONE UNDO STEP: `node.touch` when the finger lands, the values throttled while
 * it moves, the last one and `node.release` when it lifts ([TabWrites]). The engine's
 * pushes are ignored under the finger, and the value sent shown until the engine agrees
 * ([SentValue]). Disabled, it is drawn greyed and takes no touch.
 */
@Composable
fun DbFader(
    value: Double,
    address: String,
    writes: TabWrites,
    curve: FaderCurve,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    colour: Color = GodotColors.live,
    enabled: Boolean = true,
    label: String = "",
    thickness: Dp = 64.dp,
    boxWidth: Dp = 92.dp,
) {
    val sent = remember(address) { SentValue() }
    var dragging by remember(address) { mutableStateOf<Double?>(null) }
    // Read again at every reading of the mirror (25 a second while the engine ticks), so a held value lets go in time.
    val engine = FieldText.levelText(value, curve.silenceDb)
    val heldText = sent.shown(engine, SystemClock.uptimeMillis())
    val held = dragging ?: if (heldText == engine) value else heldText.toDoubleOrNull() ?: curve.silenceDb

    val shownNow by rememberUpdatedState(held)
    val density = LocalDensity.current
    val unitPx = with(density) { 48.dp.toPx() }

    fun sendText(db: Double) = if (db <= curve.silenceDb) WireText.number(curve.silenceDb, 1) else WireText.number(db, 1)

    val track: @Composable (Modifier) -> Unit = { trackModifier ->
        Canvas(
            trackModifier
                .semantics {
                    contentDescription = label
                    stateDescription = FieldText.levelText(shownNow, curve.silenceDb) + " dB"
                }
                .pointerInput(address, enabled, vertical) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val length = if (vertical) size.height.toFloat() else size.width.toFloat()
                        val drag = FaderDrag(curve, shownNow, length, unitPx)
                        writes.touch(address)
                        dragging = drag.level()
                        var last = down.position
                        var final = drag.level()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val at = change.position
                            val along = if (vertical) last.y - at.y else at.x - last.x
                            val across = if (vertical) {
                                when {
                                    at.x < 0f -> -at.x
                                    at.x > size.width -> at.x - size.width
                                    else -> 0f
                                }
                            } else {
                                when {
                                    at.y < 0f -> -at.y
                                    at.y > size.height -> at.y - size.height
                                    else -> 0f
                                }
                            }
                            last = at
                            change.consume()
                            if (!change.pressed) break
                            val level = drag.move(along, across)
                            if (level != final) {
                                final = level
                                dragging = level
                                writes.set(address, sendText(level))
                            }
                        }
                        writes.set(address, sendText(final))
                        writes.release(address)
                        sent.sent(FieldText.levelText(final, curve.silenceDb), SystemClock.uptimeMillis())
                        dragging = null
                    }
                }
        ) {
            val place = curve.fractionForDb(shownNow).toFloat()
            val radius = CornerRadius(6.dp.toPx())
            val ink = if (enabled) colour else GodotColors.inkOff
            drawRoundRect(ink.copy(alpha = 0.22f), cornerRadius = radius)
            if (vertical) {
                val top = size.height * (1f - place)
                drawRoundRect(ink.copy(alpha = 0.7f), Offset(0f, top), Size(size.width, size.height - top), radius)
            } else {
                drawRoundRect(ink.copy(alpha = 0.7f), Offset.Zero, Size(size.width * place, size.height), radius)
            }
            // The scale's marks: silence, -60, -30, -12, -6, 0 (longer), +6, +12.
            for (db in listOf(-60.0, -30.0, -12.0, -6.0, 0.0, 6.0)) {
                val at = curve.fractionForDb(db).toFloat()
                val long = if (db == 0.0) 0.5f else 0.25f
                if (vertical) {
                    val y = size.height * (1f - at)
                    drawLine(GodotColors.ink.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width * long, y), 1.5.dp.toPx())
                } else {
                    val x = size.width * at
                    drawLine(GodotColors.ink.copy(alpha = 0.55f), Offset(x, size.height * (1f - long)), Offset(x, size.height), 1.5.dp.toPx())
                }
            }
            // The cap: a white line across the track.
            val cap = 4.dp.toPx()
            if (vertical) {
                val y = (size.height * (1f - place)).coerceIn(cap / 2, size.height - cap / 2)
                drawRect(Color.White.copy(alpha = if (enabled) 1f else 0.4f), Offset(0f, y - cap / 2), Size(size.width, cap))
            } else {
                val x = (size.width * place).coerceIn(cap / 2, size.width - cap / 2)
                drawRect(Color.White.copy(alpha = if (enabled) 1f else 0.4f), Offset(x - cap / 2, 0f), Size(cap, size.height))
            }
        }
    }

    val box: @Composable () -> Unit = {
        NumberBox(
            shown = FieldText.levelText(held, curve.silenceDb),
            onSend = { text ->
                writes.setNow(address, text)
                sent.sent(FieldText.levelText(text.toDoubleOrNull() ?: held, curve.silenceDb), SystemClock.uptimeMillis())
            },
            decimals = 1,
            signed = true,
            unit = "dB",
            enabled = enabled,
            width = boxWidth,
        )
    }

    if (vertical) {
        Column(modifier.alpha(if (enabled) 1f else 0.6f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (label.isNotEmpty()) Text(label, color = GodotColors.inkDim, maxLines = 1)
            box()
            track(Modifier.width(thickness).weight(1f).fillMaxHeight())
        }
    } else {
        Row(modifier.alpha(if (enabled) 1f else 0.6f), verticalAlignment = Alignment.CenterVertically) {
            track(Modifier.weight(1f).height(thickness))
            Spacer(Modifier.width(10.dp))
            box()
        }
    }
}

/** A run's level leaving its track: a thin bar along the same curve, its number beside it. */
@Composable
fun MeterBar(decibels: Double?, curve: FaderCurve, modifier: Modifier = Modifier) {
    val db = decibels ?: curve.silenceDb
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).height(8.dp)) {
            drawRect(GodotColors.panelIn)
            val place = curve.fractionForDb(db).toFloat()
            val colour = when {
                db > 0.0 -> GodotColors.failed
                db > -6.0 -> GodotColors.stopping
                else -> GodotColors.live
            }
            drawRect(colour, size = Size(size.width * place, size.height))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (db <= curve.silenceDb) "-inf" else EqLaws.fixed(db, 0),
            color = GodotColors.inkFaint, style = GodotType.number, modifier = Modifier.width(44.dp), maxLines = 1
        )
    }
}
