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

package com.pob31.godot.remote.mapping

import kotlin.math.abs
import kotlin.math.hypot

/**
 * A MESH POINT UNDER A FINGER (PRD §3.17's touch rules, for a point that moves in two
 * dimensions):
 *
 * - RELATIVE FROM TOUCH-DOWN, never absolute: the finger lands near the point, not on it,
 *   and the point does not jump to it. The finger's travel moves the point by as much, at
 *   the scale the display is drawn - so zooming in makes the same travel finer.
 * - THE FADER'S RULE ON THE LINE THE FINGER STARTED ALONG. For its first [slopPx] the point
 *   follows the finger freely. Past that, the direction travelled so far becomes the drag's
 *   axis: travel along it moves the point along it, and the finger's distance ACROSS it -
 *   from the line through the touch-down point - divides the speed: 1 / (1 + across /
 *   [precisionPx]). Slide the finger sideways and keep going: the point creeps. Lift and
 *   touch again for another direction.
 * - A CONTINUOUS RATIO, no step: the gain applies to each move as it comes, so moving
 *   sideways alone never moves the point, and coming back towards the line speeds it up
 *   again smoothly. The point never moves backwards because the finger left the line.
 *
 * Pure arithmetic, in the finger's pixels and the display's units (0..1), so a test runs
 * it without a screen. [unitsPerPxX] and [unitsPerPxY] are one over the drawn display's
 * width and height in pixels. The point is kept within -0.5..1.5, as the desktop's editor
 * keeps it: a warp taking only part of a canvas puts points outside the display.
 */
class PointDrag(
    startX: Double,
    startY: Double,
    private val unitsPerPxX: Double,
    private val unitsPerPxY: Double,
    private val slopPx: Double,
    private val precisionPx: Double,
) {
    var x: Double = startX
        private set
    var y: Double = startY
        private set

    /** The axis, a unit vector in pixels, once the finger has travelled past the slop. */
    var axis: Pair<Double, Double>? = null
        private set

    /** Whether the point has moved at all: a drag that never moved is a tap. */
    var moved: Boolean = false
        private set

    private var lastDx = 0.0
    private var lastDy = 0.0

    /** The gain now, for a finger at ([dx], [dy]) from where it touched down. */
    fun gainAt(dx: Double, dy: Double): Double {
        val (ax, ay) = axis ?: return 1.0
        val across = abs(dx * ay - dy * ax)
        return 1.0 / (1.0 + across / precisionPx)
    }

    /**
     * The finger is at ([dx], [dy]) pixels from where it touched down: moves the point and
     * answers whether it moved.
     */
    fun update(dx: Double, dy: Double): Boolean {
        val stepX = dx - lastDx
        val stepY = dy - lastDy
        lastDx = dx
        lastDy = dy
        if (stepX == 0.0 && stepY == 0.0) return false
        val beforeX = x
        val beforeY = y
        val current = axis
        if (current == null) {
            // Free, at one to one, until the direction is known.
            moveBy(stepX, stepY)
            val travel = hypot(dx, dy)
            if (travel >= slopPx && travel > 0.0) axis = (dx / travel) to (dy / travel)
        } else {
            val (ax, ay) = current
            val along = stepX * ax + stepY * ay
            val gain = gainAt(dx, dy)
            moveBy(ax * along * gain, ay * along * gain)
        }
        val did = x != beforeX || y != beforeY
        if (did) moved = true
        return did
    }

    private fun moveBy(px: Double, py: Double) {
        x = (x + px * unitsPerPxX).coerceIn(MIN, MAX)
        y = (y + py * unitsPerPxY).coerceIn(MIN, MAX)
    }

    companion object {
        const val MIN = -0.5
        const val MAX = 1.5
    }
}

/**
 * THE NUDGE PAD, the drag's non-gestural equivalent (PRD §3.17, "every gesture has a
 * non-gestural equivalent"): a thousandth of the display a press, or a hundredth - the
 * desktop editor's arrow keys and Shift (§40.5). Up is towards the top of the display, y
 * smaller. The point kept within the drag's bounds, its numbers rounded to the four
 * decimals the mesh row is written with, so a run of presses does not gather dust.
 */
enum class NudgeStep(val size: Double) { FINE(0.001), COARSE(0.01) }

enum class NudgeDirection(val dx: Int, val dy: Int) { LEFT(-1, 0), RIGHT(1, 0), UP(0, -1), DOWN(0, 1) }

fun nudged(warp: Warp, index: Int, direction: NudgeDirection, step: NudgeStep): Warp {
    if (index !in 0 until warp.size) return warp
    val x = round4(warp.x[index] + direction.dx * step.size).coerceIn(PointDrag.MIN, PointDrag.MAX)
    val y = round4(warp.y[index] + direction.dy * step.size).coerceIn(PointDrag.MIN, PointDrag.MAX)
    return warp.moved(index, x, y)
}

private fun round4(value: Double): Double = Math.round(value * 10000.0) / 10000.0

/**
 * The point a finger at ([atX], [atY]) means: the nearest of [points] (screen pixels)
 * within [radius] pixels, or null.
 */
fun nearestPoint(points: List<Pair<Double, Double>>, atX: Double, atY: Double, radius: Double): Int? {
    var best: Int? = null
    var bestDistance = radius
    for ((n, point) in points.withIndex()) {
        val distance = hypot(point.first - atX, point.second - atY)
        if (distance <= bestDistance) {
            bestDistance = distance
            best = n
        }
    }
    return best
}
