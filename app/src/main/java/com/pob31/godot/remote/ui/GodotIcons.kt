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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pob31.godot.remote.model.GodotIcon
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*  GO.DOT'S ICONS, DRAWN (src/wfg/client/ui/Icons.cpp, ported shape by shape): lines to
    stroke and shapes to fill on a 24-unit grid, the line 1.75 units with round ends, in
    the colour they are handed. JUCE measures an arc's angles clockwise from twelve
    o'clock; Compose from three - so every arc here is the desktop's angle less ninety. */

private const val GRID = 24f
private const val WEIGHT = 1.75f
private const val PI = Math.PI.toFloat()

private class Shape {
    val lines = Path()
    val solid = Path()
}

private fun Path.line(x1: Float, y1: Float, x2: Float, y2: Float) {
    moveTo(x1, y1)
    lineTo(x2, y2)
}

private fun Path.ring(cx: Float, cy: Float, r: Float) = addOval(Rect(cx - r, cy - r, cx + r, cy + r))

private fun Path.box(x: Float, y: Float, w: Float, h: Float) = addRect(Rect(x, y, x + w, y + h))

private fun Path.roundBox(x: Float, y: Float, w: Float, h: Float, r: Float) =
    addRoundRect(RoundRect(Rect(x, y, x + w, y + h), CornerRadius(r, r)))

private fun Path.triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
    moveTo(x1, y1)
    lineTo(x2, y2)
    lineTo(x3, y3)
    close()
}

/** An arc by the desktop's angles: degrees clockwise from twelve o'clock, as a new sub-path. */
private fun Path.arc(cx: Float, cy: Float, r: Float, fromDegrees: Float, toDegrees: Float) =
    arcTo(Rect(cx - r, cy - r, cx + r, cy + r), fromDegrees - 90f, toDegrees - fromDegrees, true)

/** An arrowhead whose tip is at (x, y), pointing along (dx, dy). */
private fun Path.head(x: Float, y: Float, dx: Float, dy: Float, size: Float = 3.2f) {
    val length = sqrt(dx * dx + dy * dy)
    if (!(length > 0f)) return
    val ux = dx / length
    val uy = dy / length
    val bx = x - ux * size
    val by = y - uy * size
    val half = size * 0.72f
    triangle(x, y, bx - uy * half, by + ux * half, bx + uy * half, by - ux * half)
}

/** The head at the end of an arc drawn clockwise, pointing the way it travels. */
private fun Path.headOnArc(cx: Float, cy: Float, r: Float, atDegrees: Float) {
    val a = atDegrees * PI / 180f
    val x = cx + r * sin(a)
    val y = cy - r * cos(a)
    val dx = cos(a)
    val dy = sin(a)
    head(x + dx * 1.6f, y + dy * 1.6f, dx, dy)
}

private fun build(icon: GodotIcon): Shape {
    val s = Shape()
    val l = s.lines
    val f = s.solid
    when (icon) {
        GodotIcon.NONE -> Unit

        // WHAT A CUE IS
        GodotIcon.MEMO -> {
            l.roundBox(5f, 3f, 14f, 18f, 2f)
            l.line(8.5f, 8.5f, 15.5f, 8.5f)
            l.line(8.5f, 12f, 15.5f, 12f)
            l.line(8.5f, 15.5f, 13f, 15.5f)
        }
        GodotIcon.MEDIA -> {
            l.line(4f, 10f, 4f, 14f)
            l.line(8f, 7f, 8f, 17f)
            l.line(12f, 4f, 12f, 20f)
            l.line(16f, 8f, 16f, 16f)
            l.line(20f, 10.5f, 20f, 13.5f)
        }
        GodotIcon.MIC -> {
            l.roundBox(9f, 3f, 6f, 11f, 3f)
            l.moveTo(6f, 11f)
            l.cubicTo(6f, 15f, 8.5f, 17.5f, 12f, 17.5f)
            l.cubicTo(15.5f, 17.5f, 18f, 15f, 18f, 11f)
            l.line(12f, 17.5f, 12f, 21f)
            l.line(8.5f, 21f, 15.5f, 21f)
        }
        GodotIcon.VIDEO -> {
            l.roundBox(3f, 5f, 18f, 14f, 1.5f)
            l.moveTo(5f, 17f)
            l.lineTo(10f, 11f)
            l.lineTo(13.5f, 14.5f)
            l.lineTo(15.5f, 12.5f)
            l.lineTo(19f, 17f)
            f.ring(16f, 8.5f, 1.6f)
        }
        GodotIcon.FADE -> {
            l.moveTo(4f, 6f)
            l.cubicTo(10f, 6f, 13f, 18f, 20f, 18f)
            f.ring(4f, 6f, 2f)
            f.ring(20f, 18f, 2f)
        }
        GodotIcon.START -> {
            l.moveTo(7f, 5f)
            l.lineTo(19f, 12f)
            l.lineTo(7f, 19f)
            l.close()
        }
        GodotIcon.OSC -> {
            f.ring(6f, 12f, 2.2f)
            l.arc(6f, 12f, 7f, 45f, 135f)
            l.arc(6f, 12f, 12.5f, 55f, 125f)
        }
        GodotIcon.MIDI -> {
            l.ring(12f, 12f, 9f)
            for (degrees in listOf(180f, 225f, 270f, 315f, 360f)) {
                val a = degrees * PI / 180f
                f.ring(12f + 5.2f * cos(a), 12.5f + 5.2f * sin(a), 1.35f)
            }
            f.roundBox(10.8f, 17.2f, 2.4f, 2.2f, 0.6f)
        }
        GodotIcon.PROCESS -> {
            l.box(3f, 3.5f, 11f, 6.5f)
            l.box(10f, 14f, 11f, 6.5f)
            f.box(3f, 8.5f, 3.5f, 1.5f)
            f.box(10f, 14f, 3.5f, 1.5f)
            l.line(4.75f, 10f, 11.75f, 14f)
        }
        GodotIcon.SCRIPT -> {
            l.moveTo(7f, 3.5f)
            l.quadraticTo(4.5f, 3.5f, 4.5f, 6.5f)
            l.lineTo(4.5f, 10f)
            l.quadraticTo(4.5f, 12f, 2.5f, 12f)
            l.quadraticTo(4.5f, 12f, 4.5f, 14f)
            l.lineTo(4.5f, 17.5f)
            l.quadraticTo(4.5f, 20.5f, 7f, 20.5f)
            l.moveTo(17f, 3.5f)
            l.quadraticTo(19.5f, 3.5f, 19.5f, 6.5f)
            l.lineTo(19.5f, 10f)
            l.quadraticTo(19.5f, 12f, 21.5f, 12f)
            l.quadraticTo(19.5f, 12f, 19.5f, 14f)
            l.lineTo(19.5f, 17.5f)
            l.quadraticTo(19.5f, 20.5f, 17f, 20.5f)
            l.line(8f, 8f, 14f, 8f)
            l.line(10f, 12f, 16f, 12f)
            l.line(8f, 16f, 13f, 16f)
        }
        GodotIcon.TIMECODE -> {
            l.moveTo(2f, 14f)
            for ((x, y) in listOf(5f to 14f, 5f to 5f, 8f to 5f, 8f to 14f, 10.5f to 14f, 10.5f to 5f, 16f to 5f,
                16f to 14f, 19f to 14f, 19f to 5f, 22f to 5f)) l.lineTo(x, y)
            for (x in listOf(3f, 9f, 15f, 21f)) f.roundBox(x - 0.9f, 17.5f, 1.8f, 3.5f, 0.6f)
        }
        GodotIcon.GROUP -> {
            l.roundBox(3f, 8f, 13f, 13f, 2f)
            l.moveTo(8f, 8f)
            l.lineTo(8f, 3.5f)
            l.lineTo(21f, 3.5f)
            l.lineTo(21f, 16f)
            l.lineTo(16f, 16f)
        }
        GodotIcon.RANGE -> {
            l.moveTo(7f, 4f); l.lineTo(3.5f, 4f); l.lineTo(3.5f, 20f); l.lineTo(7f, 20f)
            l.moveTo(17f, 4f); l.lineTo(20.5f, 4f); l.lineTo(20.5f, 20f); l.lineTo(17f, 20f)
            f.roundBox(7.5f, 10.5f, 9f, 3f, 1f)
        }
        GodotIcon.TRIGGER -> {
            l.moveTo(13.5f, 2.5f)
            l.lineTo(5f, 13.5f)
            l.lineTo(11f, 13.5f)
            l.lineTo(10f, 21.5f)
            l.lineTo(19f, 10f)
            l.lineTo(13f, 10f)
            l.close()
        }

        // WHAT A TRANSPORT CUE DOES
        GodotIcon.STOP -> f.roundBox(6f, 6f, 12f, 12f, 2f)
        GodotIcon.STOP_FADE -> {
            l.moveTo(2.5f, 6f)
            l.cubicTo(6f, 6f, 7f, 15.5f, 10.5f, 15.5f)
            f.roundBox(12.5f, 7.5f, 9.5f, 9.5f, 1.8f)
        }
        GodotIcon.STOP_AFTER -> {
            l.line(2.5f, 12f, 8.5f, 12f)
            l.line(10f, 7f, 10f, 17f)
            f.roundBox(12.5f, 7.5f, 9.5f, 9.5f, 1.8f)
        }
        GodotIcon.ADVANCE -> {
            f.triangle(5f, 6f, 15f, 12f, 5f, 18f)
            f.roundBox(16.5f, 6f, 3f, 12f, 1f)
        }
        GodotIcon.RECORD -> f.ring(12f, 12f, 6.5f)
        GodotIcon.LOOP -> {
            l.arc(12f, 12f, 7.5f, 50f, 320f)
            f.headOnArc(12f, 12f, 7.5f, 320f)
        }
        GodotIcon.OVERDUB -> {
            l.ring(9f, 12f, 5.5f)
            f.ring(15f, 12f, 5.5f)
        }
        GodotIcon.CLEAR -> {
            l.ring(12f, 12f, 8f)
            l.line(9f, 9f, 15f, 15f)
            l.line(15f, 9f, 9f, 15f)
        }
        GodotIcon.ENABLE -> {
            l.ring(12f, 12f, 8.5f)
            l.line(8f, 12.5f, 11f, 15.5f)
            l.line(11f, 15.5f, 16.5f, 9f)
        }
        GodotIcon.JUMP -> {
            f.ring(4.5f, 15f, 1.9f)
            l.arc(12f, 15f, 7.5f, -90f, 90f)
            f.headOnArc(12f, 15f, 7.5f, 90f)
        }

        // HOW A GROUP RUNS
        GodotIcon.SEQUENCE -> {
            l.roundBox(1.5f, 8f, 5.5f, 8f, 1.3f)
            l.roundBox(9.25f, 8f, 5.5f, 8f, 1.3f)
            f.roundBox(17f, 8f, 5.5f, 8f, 1.3f)
            l.line(7f, 12f, 9.25f, 12f)
            l.line(14.75f, 12f, 17f, 12f)
        }
        GodotIcon.TIMELINE -> {
            f.roundBox(3f, 5f, 9f, 3.2f, 1.6f)
            f.roundBox(8f, 10.4f, 11f, 3.2f, 1.6f)
            f.roundBox(5f, 15.8f, 16f, 3.2f, 1.6f)
        }
        GodotIcon.SAMPLER -> {
            l.roundBox(4f, 4f, 7f, 7f, 1.5f)
            l.roundBox(13f, 4f, 7f, 7f, 1.5f)
            l.roundBox(4f, 13f, 7f, 7f, 1.5f)
            f.roundBox(13f, 13f, 7f, 7f, 1.5f)
        }
        GodotIcon.TIME_GROUP -> {
            l.ring(9.5f, 14.5f, 6.5f)
            l.moveTo(8f, 8f); l.lineTo(8f, 3.5f); l.lineTo(21f, 3.5f); l.lineTo(21f, 16f); l.lineTo(16f, 16f)
            l.moveTo(9.5f, 10.5f); l.lineTo(9.5f, 14.5f); l.lineTo(12.5f, 16.25f)
        }

        // MARKS AND PANELS
        GodotIcon.DISABLED -> {
            l.ring(12f, 12f, 8.5f)
            l.line(6f, 6f, 18f, 18f)
        }
        GodotIcon.LIST -> {
            f.ring(5f, 7f, 1.5f)
            f.ring(5f, 12f, 1.5f)
            f.ring(5f, 17f, 1.5f)
            l.line(9f, 7f, 20.5f, 7f)
            l.line(9f, 12f, 20.5f, 12f)
            l.line(9f, 17f, 20.5f, 17f)
        }
        GodotIcon.SURFACE -> {
            l.roundBox(3f, 4f, 18f, 16f, 1.5f)
            l.line(7.5f, 7.5f, 7.5f, 16.5f)
            l.line(12f, 7.5f, 12f, 16.5f)
            f.box(6f, 10f, 3f, 2.2f)
            f.box(10.5f, 12.5f, 3f, 2.2f)
            f.roundBox(15.5f, 7.5f, 3f, 3f, 0.6f)
            f.roundBox(15.5f, 13f, 3f, 3f, 0.6f)
        }
        GodotIcon.WARNING -> {
            l.moveTo(12f, 3.5f); l.lineTo(21f, 19.5f); l.lineTo(3f, 19.5f); l.close()
            l.line(12f, 9f, 12f, 13.5f)
            f.ring(12f, 16.6f, 1.2f)
        }

        // THE REMOTE'S OWN, on the same grid
        GodotIcon.PREVIOUS -> {
            l.moveTo(15f, 5f); l.lineTo(8f, 12f); l.lineTo(15f, 19f)
        }
        GodotIcon.NEXT -> {
            l.moveTo(9f, 5f); l.lineTo(16f, 12f); l.lineTo(9f, 19f)
        }
        GodotIcon.MACHINE -> {
            // A screen on its stand: a machine running Go.dot.
            l.roundBox(3f, 4f, 18f, 12f, 1.5f)
            l.line(12f, 16f, 12f, 19.5f)
            l.line(8f, 20f, 16f, 20f)
        }
        GodotIcon.PROMPT -> {
            // A page of script with the follower's mark at the line being spoken: Choufleur.
            l.roundBox(7f, 3f, 14f, 18f, 2f)
            l.line(10f, 7.5f, 18f, 7.5f)
            l.line(10f, 12f, 18f, 12f)
            l.line(10f, 16.5f, 15f, 16.5f)
            f.triangle(2f, 9.5f, 5.5f, 12f, 2f, 14.5f)
        }
        GodotIcon.BUZZER -> {
            // A wearable on its strap, shaking: Choufleur's wrist buzzer.
            l.roundBox(8f, 7f, 8f, 10f, 2f)
            l.line(10f, 3f, 10f, 7f)
            l.line(14f, 3f, 14f, 7f)
            l.line(10f, 17f, 10f, 21f)
            l.line(14f, 17f, 14f, 21f)
            l.line(4.5f, 9f, 4.5f, 15f)
            l.line(19.5f, 9f, 19.5f, 15f)
            l.line(2f, 10.5f, 2f, 13.5f)
            l.line(22f, 10.5f, 22f, 13.5f)
        }
    }
    return s
}

/** Built once per icon and kept: a list of rows paints the same thirty shapes. Drawn on the UI thread only. */
private val shapes = HashMap<GodotIcon, Shape>()

private fun shapeOf(icon: GodotIcon): Shape = shapes.getOrPut(icon) { build(icon) }

/** Draws [icon] into the square of side [side] at [topLeft], in [colour]. */
fun DrawScope.drawGodotIcon(icon: GodotIcon, colour: Color, topLeft: Offset = Offset.Zero, side: Float = min(size.width, size.height)) {
    if (icon == GodotIcon.NONE || side <= 0f) return
    val shape = shapeOf(icon)
    val unit = side / GRID
    translate(topLeft.x, topLeft.y) {
        scale(unit, unit, pivot = Offset.Zero) {
            drawPath(shape.solid, colour)
            // Never thinner than a pixel: under that a line is a grey smear.
            val width = max(WEIGHT, 1f / unit)
            drawPath(shape.lines, colour, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** [icon] in a square of [size], in [colour]: a picture, never the only telling - a word goes beside it. */
@Composable
fun GodotIconMark(icon: GodotIcon, colour: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val side = min(this.size.width, this.size.height)
        drawGodotIcon(icon, colour, Offset((this.size.width - side) / 2f, (this.size.height - side) / 2f), side)
    }
}
