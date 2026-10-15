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

package com.pob31.godot.remote.ui.mapping

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.mapping.Warp
import com.pob31.godot.remote.mapping.nearestPoint
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotMetrics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Where the display is drawn: its top-left on screen and its size, both in pixels. */
private data class Placed(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun toScreen(u: Double, v: Double) = Offset(left + (u * width).toFloat(), top + (v * height).toFloat())
}

/** The view's pinch and pan, the screen's own (never sent): 1 is the display fitted, with room round it. */
private data class ViewPort(val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f)

/** One warp as the view draws it: its points, its name on screen, and whether it is the one picked. */
data class DrawnWarp(val warp: Warp, val label: String)

/** A point being dragged: moved by the finger's travel from touch-down, in pixels, and let go. */
interface PointGrab {
    fun move(dx: Double, dy: Double)
    fun end()
}

/**
 * THE OUTPUT AND ITS WARPS, drawn as the renderer bends them (§40.5): a dark rectangle the
 * shape of the output's picture, each warp's grid lines through the curve the renderer
 * draws, its own first, then each zone. The picked warp is bright with its points; the
 * others are outlines; each is named on screen. No picture of the canvas: the wall is the
 * picture (§40.2, XI).
 *
 * ONE FINGER on a point picks it and drags it (relative from touch-down, the fader's rule
 * along the line it started - [com.pob31.godot.remote.mapping.PointDrag]); anywhere else
 * it pans; two fingers pinch and pan. [grab] answers null when the point may not move -
 * the lock, a stale link - and the finger pans instead. "Fit" puts the view back.
 */
@Composable
fun MeshView(
    warps: List<DrawnWarp>,
    picked: Int,
    pickedPoint: Int?,
    aspect: Double,
    viewKey: Any,
    onPick: (layer: Int, point: Int?) -> Unit,
    grab: (layer: Int, point: Int, unitsPerPxX: Double, unitsPerPxY: Double) -> PointGrab?,
    modifier: Modifier = Modifier,
) {
    var port by remember(viewKey) { mutableStateOf(ViewPort()) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val hitRadius = with(density) { 30.dp.toPx() }
    val currentWarps by rememberUpdatedState(warps)
    val currentPicked by rememberUpdatedState(picked)
    val pickHandler by rememberUpdatedState(onPick)
    val grabHandler by rememberUpdatedState(grab)

    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val fitted = remember(widthPx, heightPx, aspect) { fit(widthPx, heightPx, aspect) }
        val placed = Placed(
            left = fitted.left * port.zoom + port.panX,
            top = fitted.top * port.zoom + port.panY,
            width = fitted.width * port.zoom,
            height = fitted.height * port.zoom,
        )
        val currentPlaced by rememberUpdatedState(placed)
        val currentFitted by rememberUpdatedState(fitted)
        val currentSize by rememberUpdatedState(Size(widthPx, heightPx))

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(viewKey) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val at = currentPlaced
                        val hit = pickAt(currentWarps, currentPicked, at, down.position, hitRadius)
                        val grabbed = hit?.let {
                            pickHandler(it.first, it.second)
                            grabHandler(it.first, it.second, 1.0 / at.width, 1.0 / at.height)
                        }
                        if (grabbed != null) {
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val d = change.position - down.position
                                grabbed.move(d.x.toDouble(), d.y.toDouble())
                                change.consume()
                            }
                            grabbed.end()
                            return@awaitEachGesture
                        }
                        // Pan, and pinch with two fingers; a tap on nothing lets the point go.
                        var travelled = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            travelled += abs(pan.x) + abs(pan.y) + abs(zoom - 1f) * 100f
                            if (zoom != 1f || pan != Offset.Zero) {
                                port = zoomed(port, currentFitted, zoom, pan, centroid, currentSize.width, currentSize.height)
                                event.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                            }
                        }
                        if (hit == null && travelled < viewConfiguration.touchSlop) pickHandler(currentPicked, null)
                    }
                }
        ) {
            drawRect(GodotColors.panelIn)
            val rect = Rect(placed.left, placed.top, placed.left + placed.width, placed.top + placed.height)
            drawRect(Color.Black, rect.topLeft, rect.size)
            drawRect(GodotColors.rule, rect.topLeft, rect.size, style = Stroke(1.dp.toPx()))
            clipRect {
                for ((n, drawn) in warps.withIndex()) if (n != picked) drawWarp(drawn.warp, placed, false)
                warps.getOrNull(picked)?.let { drawWarp(it.warp, placed, true) }
                for ((n, drawn) in warps.withIndex()) {
                    // In the middle of its top-left cell: never on a point, and inside its own outline.
                    val centre = drawn.warp.at(0.5 / (drawn.warp.columns - 1), 0.5 / (drawn.warp.rows - 1))
                    val place = placed.toScreen(centre.first, centre.second)
                    val style = TextStyle(
                        color = if (n == picked) GodotColors.picked else GodotColors.inkFaint,
                        fontSize = 14.sp,
                        fontWeight = if (n == picked) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    val layout = measurer.measure(drawn.label, style)
                    val topLeft = Offset(place.x - layout.size.width / 2f, place.y - layout.size.height / 2f)
                    drawRect(Color.Black.copy(alpha = 0.6f), topLeft - Offset(4f, 2f), Size(layout.size.width + 8f, layout.size.height + 4f))
                    drawText(layout, topLeft = topLeft)
                }
                warps.getOrNull(picked)?.let { drawPoints(it.warp, placed, pickedPoint) }
            }
        }

        if (port != ViewPort()) {
            OutlinedButton(
                onClick = { port = ViewPort() },
                colors = ButtonDefaults.outlinedButtonColors(containerColor = GodotColors.panelHigh),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .heightIn(min = GodotMetrics.touch)
            ) { Text(loc("mapping.view.fit"), color = GodotColors.ink) }
        }
    }
}

/** The display fitted in the view at zoom 1, with a margin round it for points just outside. */
private fun fit(width: Float, height: Float, aspect: Double): Placed {
    val margin = 0.08f
    val roomW = width * (1 - 2 * margin)
    val roomH = height * (1 - 2 * margin)
    val a = aspect.toFloat().takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
    val w: Float
    val h: Float
    if (roomW / roomH > a) {
        h = roomH
        w = h * a
    } else {
        w = roomW
        h = w / a
    }
    return Placed((width - w) / 2f, (height - h) / 2f, w, h)
}

/** Pinch about the fingers' centre and pan with them; the zoom kept to 1..8 and the display never wholly off the view. */
private fun zoomed(port: ViewPort, fitted: Placed, zoom: Float, pan: Offset, centroid: Offset, width: Float, height: Float): ViewPort {
    val newZoom = (port.zoom * zoom).coerceIn(1f, 8f)
    val ratio = newZoom / port.zoom
    // The screen point under the fingers stays under them: origin' = c - (c - origin) * ratio.
    val originX = fitted.left * port.zoom + port.panX
    val originY = fitted.top * port.zoom + port.panY
    var newOriginX = centroid.x - (centroid.x - originX) * ratio + pan.x
    var newOriginY = centroid.y - (centroid.y - originY) * ratio + pan.y
    val w = fitted.width * newZoom
    val h = fitted.height * newZoom
    val keep = min(width, height) * 0.25f
    newOriginX = newOriginX.coerceIn(keep - w, width - keep)
    newOriginY = newOriginY.coerceIn(keep - h, height - keep)
    if (newZoom == 1f && abs(newOriginX - fitted.left) < 1f && abs(newOriginY - fitted.top) < 1f) return ViewPort()
    return ViewPort(newZoom, newOriginX - fitted.left * newZoom, newOriginY - fitted.top * newZoom)
}

/**
 * The point a finger means: the picked warp's nearest within reach first; failing that,
 * another warp's, which picks that warp. Null for none.
 */
private fun pickAt(warps: List<DrawnWarp>, picked: Int, placed: Placed, at: Offset, radius: Float): Pair<Int, Int>? {
    fun pointsOf(warp: Warp) = warp.x.indices.map { placed.toScreen(warp.x[it], warp.y[it]).let { o -> o.x.toDouble() to o.y.toDouble() } }
    warps.getOrNull(picked)?.let { mine ->
        nearestPoint(pointsOf(mine.warp), at.x.toDouble(), at.y.toDouble(), radius.toDouble())?.let { return picked to it }
    }
    for ((n, drawn) in warps.withIndex()) {
        if (n == picked) continue
        nearestPoint(pointsOf(drawn.warp), at.x.toDouble(), at.y.toDouble(), radius.toDouble())?.let { return n to it }
    }
    return null
}

/** The grid as the renderer bends it: each line through the surface, sixteen steps a cell. */
private fun DrawScope.drawWarp(warp: Warp, placed: Placed, bright: Boolean) {
    val colour = if (bright) GodotColors.picked else GodotColors.inkOff
    val width = (if (bright) 2.dp else 1.dp).toPx()
    val steps = 16 * max(warp.columns - 1, warp.rows - 1)
    for (column in 0 until warp.columns) {
        val s = column.toDouble() / (warp.columns - 1)
        val path = Path()
        for (step in 0..steps) {
            val (x, y) = warp.at(s, step.toDouble() / steps)
            val p = placed.toScreen(x, y)
            if (step == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, colour, style = Stroke(width))
    }
    for (row in 0 until warp.rows) {
        val t = row.toDouble() / (warp.rows - 1)
        val path = Path()
        for (step in 0..steps) {
            val (x, y) = warp.at(step.toDouble() / steps, t)
            val p = placed.toScreen(x, y)
            if (step == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, colour, style = Stroke(width))
    }
}

/** The picked warp's points: a dot each, and the picked one ringed - a shape, not only a colour. */
private fun DrawScope.drawPoints(warp: Warp, placed: Placed, pickedPoint: Int?) {
    val dot = 6.dp.toPx()
    for (n in warp.x.indices) {
        val p = placed.toScreen(warp.x[n], warp.y[n])
        drawCircle(Color.Black, dot + 1.5.dp.toPx(), p)
        drawCircle(GodotColors.ink, dot, p)
    }
    if (pickedPoint != null && pickedPoint in warp.x.indices) {
        val p = placed.toScreen(warp.x[pickedPoint], warp.y[pickedPoint])
        val ring = 15.dp.toPx()
        drawCircle(Color.Black, ring, p, style = Stroke(5.dp.toPx()))
        drawCircle(GodotColors.standby, ring, p, style = Stroke(2.5.dp.toPx()))
        val arm = 26.dp.toPx()
        val gap = ring + 3.dp.toPx()
        for ((a, b) in listOf(Offset(-arm, 0f) to Offset(-gap, 0f), Offset(gap, 0f) to Offset(arm, 0f), Offset(0f, -arm) to Offset(0f, -gap), Offset(0f, gap) to Offset(0f, arm))) {
            drawLine(GodotColors.standby, p + a, p + b, strokeWidth = 2.dp.toPx())
        }
    }
}
