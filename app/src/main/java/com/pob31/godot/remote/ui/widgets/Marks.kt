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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pob31.godot.remote.ui.theme.GodotColors

/*  THE SMALL MARKS THE TWO TABS WEAR, drawn on the icons' 24-unit grid as lines and
    shapes, never a font's glyph or an emoji. Each differs in SHAPE before hue (§4.8): a
    filled triangle for a run that sounds, a ring for one that is ready and not let go, a
    square for one stopping, a cross for one that failed - and a word stands beside every
    one of them on screen. */

enum class Mark { PLAYING, ARMED, WAITING, STOPPING, FAILED, DONE, STANDBY, FLAG, TWIST_SHUT, TWIST_OPEN, CROSS, LOCK, LIVE }

/** The mark a run's state wears beside its word. */
fun markFor(state: String): Mark = when (state) {
    "playing" -> Mark.PLAYING
    "armed", "preparing" -> Mark.ARMED
    "waiting", "postWait" -> Mark.WAITING
    "stopping" -> Mark.STOPPING
    "failed" -> Mark.FAILED
    else -> Mark.DONE
}

/** The colour a run's state wears, beside its word and its mark - never instead of them. */
fun stateColour(state: String): Color = when (state) {
    "playing" -> GodotColors.live
    "armed", "preparing" -> GodotColors.standby
    "waiting", "postWait" -> GodotColors.waiting
    "stopping" -> GodotColors.stopping
    "failed" -> GodotColors.failed
    else -> GodotColors.inkOff
}

@Composable
fun MarkIcon(mark: Mark, colour: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Canvas(modifier.size(size)) { drawMark(mark, colour) }
}

fun DrawScope.drawMark(mark: Mark, colour: Color) {
    val u = size.minDimension / 24f
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    val line = Stroke(width = 2f * u, cap = StrokeCap.Round)
    fun path(vararg points: Pair<Float, Float>, close: Boolean = true) = Path().apply {
        moveTo(points[0].first * u, points[0].second * u)
        for (at in 1 until points.size) lineTo(points[at].first * u, points[at].second * u)
        if (close) close()
    }
    when (mark) {
        Mark.PLAYING -> drawPath(path(6f to 4f, 20f to 12f, 6f to 20f), colour)
        Mark.ARMED -> drawCircle(colour, 7f * u, p(12f, 12f), style = Stroke(width = 2.5f * u))
        Mark.WAITING -> {
            // An hourglass: a wait counting down.
            drawPath(path(6f to 4f, 18f to 4f, 6f to 20f, 18f to 20f), colour, style = line)
        }
        Mark.STOPPING -> drawRect(colour, p(6f, 6f), androidx.compose.ui.geometry.Size(12f * u, 12f * u))
        Mark.FAILED, Mark.CROSS -> {
            drawLine(colour, p(6f, 6f), p(18f, 18f), strokeWidth = 2.5f * u, cap = StrokeCap.Round)
            drawLine(colour, p(18f, 6f), p(6f, 18f), strokeWidth = 2.5f * u, cap = StrokeCap.Round)
        }
        Mark.DONE -> drawPath(path(5f to 13f, 10f to 18f, 19f to 7f, close = false), colour, style = line)
        // The standby's mark in the gutter: an arrow pointing at the row GO will fire.
        Mark.STANDBY -> drawPath(path(3f to 5f, 13f to 5f, 21f to 12f, 13f to 19f, 3f to 19f, 9f to 12f), colour)
        Mark.FLAG -> {
            drawLine(colour, p(6f, 3f), p(6f, 21f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawPath(path(6f to 4f, 19f to 7.5f, 6f to 12f), colour)
        }
        Mark.TWIST_SHUT -> drawPath(path(8f to 5f, 17f to 12f, 8f to 19f), colour)
        Mark.TWIST_OPEN -> drawPath(path(5f to 8f, 19f to 8f, 12f to 17f), colour)
        Mark.LOCK -> {
            drawRect(colour, p(5f, 11f), androidx.compose.ui.geometry.Size(14f * u, 10f * u))
            drawArc(colour, 180f, 180f, false, p(7.5f, 4f), androidx.compose.ui.geometry.Size(9f * u, 12f * u), style = line)
        }
        // Riding live under the lock: a dot with a ring, as an "on air" light.
        Mark.LIVE -> {
            drawCircle(colour, 4f * u, p(12f, 12f))
            drawCircle(colour, 8.5f * u, p(12f, 12f), style = Stroke(width = 1.75f * u))
        }
    }
}
