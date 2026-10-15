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

import com.pob31.godot.remote.link.WireText
import com.pob31.godot.remote.osc.OscValue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A WARP (namespace draft §40): its grid of control points - the four corners and the
 * splits between them - each where it lands on the display, x right and y down in 0..1,
 * row by row from the top-left. An output's own mesh and every zone's are one of these.
 * A port of the desktop's `WarpPoints` (client/model/Video.h). Immutable.
 */
data class Warp(val columns: Int, val rows: Int, val x: List<Double>, val y: List<Double>) {

    val size: Int get() = x.size

    fun index(column: Int, row: Int): Int = row * columns + column

    fun columnOf(index: Int): Int = index % columns
    fun rowOf(index: Int): Int = index / columns

    /** The grid nobody moved: the canvas fills the display. */
    val isWhole: Boolean
        get() {
            val plain = whole(columns, rows)
            if (x.size != plain.x.size) return false
            for (n in x.indices) {
                if (abs(x[n] - plain.x[n]) > 1e-6 || abs(y[n] - plain.y[n]) > 1e-6) return false
            }
            return true
        }

    /** The same warp with one point somewhere else. */
    fun moved(index: Int, toX: Double, toY: Double): Warp {
        if (index !in x.indices) return this
        val nx = x.toMutableList().also { it[index] = toX }
        val ny = y.toMutableList().also { it[index] = toY }
        return copy(x = nx, y = ny)
    }

    /** The mesh row's text: "x y x y ...", four decimals at most, '.' always (Video.cpp `warpText`). */
    fun text(): String = buildString {
        for (n in 0 until min(x.size, y.size)) {
            if (n > 0) append(' ')
            append(WireText.number(x[n], DECIMALS))
            append(' ')
            append(WireText.number(y[n], DECIMALS))
        }
    }

    /**
     * WHERE THE CANVAS'S POINT (s, t) LANDS on the display - s right and t down, 0..1 of
     * the canvas - as the renderer bends it (engine video/Mapping.h `meshAt`): a bicubic
     * Catmull-Rom patch through the points, a phantom row and column beyond each edge
     * extrapolated in a straight line, so a grid nobody moved is exactly the identity and
     * a two-by-two grid is a corner pin. The points are read as the renderer's floats.
     */
    fun at(s: Double, t: Double): Pair<Double, Double> {
        if (!valid) return s to t
        val gx = s.coerceIn(0.0, 1.0) * (columns - 1)
        val gy = t.coerceIn(0.0, 1.0) * (rows - 1)
        val column = min(gx.toInt(), columns - 2)
        val row = min(gy.toInt(), rows - 2)
        val wx = weights(gx - column)
        val wy = weights(gy - row)
        var ox = 0.0
        var oy = 0.0
        for (j in 0 until 4) {
            for (i in 0 until 4) {
                val (px, py) = control(column - 1 + i, row - 1 + j)
                ox += wx[i] * wy[j] * px
                oy += wx[i] * wy[j] * py
            }
        }
        return ox to oy
    }

    private val valid: Boolean
        get() = columns in 2..MAX_POINTS && rows in 2..MAX_POINTS && x.size == columns * rows && y.size == x.size

    /** A control point, the grid extrapolated straight past its edges (Mapping.h `controlAt`). */
    private fun control(column: Int, row: Int): Pair<Double, Double> {
        val c = column.coerceIn(0, columns - 1)
        val r = row.coerceIn(0, rows - 1)
        if (c == column && r == row) {
            val at = r * columns + c
            return x[at].toFloat().toDouble() to y[at].toFloat().toDouble()
        }
        val (ex, ey) = control(c, r)
        var px = ex
        var py = ey
        if (column != c) {
            val inner = c + if (column < c) 1 else -1
            val (ix, iy) = control(inner, r)
            val steps = abs(column - c).toDouble()
            px += (ex - ix) * steps
            py += (ey - iy) * steps
        }
        if (row != r) {
            val inner = r + if (row < r) 1 else -1
            val (ix, iy) = control(c, inner)
            val steps = abs(row - r).toDouble()
            px += (ex - ix) * steps
            py += (ey - iy) * steps
        }
        return px to py
    }

    companion object {
        /** The most points a side, as the renderer takes them (Mapping.h `maxPoints`). */
        const val MAX_POINTS = 16
        const val DECIMALS = 4

        fun whole(columns: Int = 2, rows: Int = 2): Warp {
            val c = max(2, min(MAX_POINTS, columns))
            val r = max(2, min(MAX_POINTS, rows))
            val xs = ArrayList<Double>(c * r)
            val ys = ArrayList<Double>(c * r)
            for (row in 0 until r) {
                for (column in 0 until c) {
                    xs += column.toDouble() / (c - 1)
                    ys += row.toDouble() / (r - 1)
                }
            }
            return Warp(c, r, xs, ys)
        }

        /**
         * The warp a row holds, as the renderer reads it (Video.cpp `readWarp`): the grid
         * clamped to 2..16 a side, and the whole output when the mesh is empty or the wrong
         * size for its grid. [mesh] is the node's values - the tree publishes `d*` as numbers
         * - or one string of them, as a write carries it.
         */
        fun read(columns: Double?, rows: Double?, mesh: List<OscValue>?): Warp {
            val c = columns?.takeIf { it.isFinite() }?.toInt()?.coerceIn(2, MAX_POINTS) ?: 2
            val r = rows?.takeIf { it.isFinite() }?.toInt()?.coerceIn(2, MAX_POINTS) ?: 2
            val plain = whole(c, r)
            val numbers = numbersOf(mesh ?: return plain)
            if (numbers.size != 2 * plain.size) return plain
            return Warp(c, r, List(plain.size) { numbers[2 * it] }, List(plain.size) { numbers[2 * it + 1] })
        }

        /** The numbers of a mesh row: its values, or the words of its one string. Anything else is none. */
        fun numbersOf(values: List<OscValue>): List<Double> {
            if (values.size == 1 && values[0] is OscValue.Str) return numbersOf((values[0] as OscValue.Str).value)
            val out = ArrayList<Double>(values.size)
            for (value in values) {
                if (value is OscValue.Str) return emptyList()
                val number = value.asDouble() ?: return emptyList()
                if (!number.isFinite()) return emptyList()
                out += number
            }
            return out
        }

        /** "x y x y ..." read back; anything that is not a finite number makes the whole of it none. */
        fun numbersOf(text: String): List<Double> {
            val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val out = ArrayList<Double>(words.size)
            for (word in words) {
                val number = word.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return emptyList()
                out += number
            }
            return out
        }

        /** The text read back as a warp of that grid, or null when it does not fit it. */
        fun parse(text: String, columns: Int, rows: Int): Warp? {
            val numbers = numbersOf(text)
            if (columns !in 2..MAX_POINTS || rows !in 2..MAX_POINTS || numbers.size != 2 * columns * rows) return null
            val n = columns * rows
            return Warp(columns, rows, List(n) { numbers[2 * it] }, List(n) { numbers[2 * it + 1] })
        }

        private fun weights(t: Double): DoubleArray {
            val t2 = t * t
            val t3 = t2 * t
            return doubleArrayOf(
                -0.5 * t3 + t2 - 0.5 * t,
                1.5 * t3 - 2.5 * t2 + 1.0,
                -1.5 * t3 + 2.0 * t2 + 0.5 * t,
                0.5 * t3 - 0.5 * t2,
            )
        }
    }
}

/**
 * THE SAME SHAPE ON ANOTHER GRID (Video.cpp `regridded`): a split added or taken away
 * keeps where the picture lands, each new point read off the warp as the renderer draws
 * it, so adding splits moves nothing until a point is dragged. The grid clamped to 2..16.
 */
fun regridded(warp: Warp, columns: Int, rows: Int): Warp {
    val out = Warp.whole(columns, rows)
    if (warp.columns !in 2..Warp.MAX_POINTS || warp.rows !in 2..Warp.MAX_POINTS || warp.x.size != warp.columns * warp.rows) return out
    val xs = out.x.toMutableList()
    val ys = out.y.toMutableList()
    for (row in 0 until out.rows) {
        for (column in 0 until out.columns) {
            val (px, py) = warp.at(column.toDouble() / (out.columns - 1), row.toDouble() / (out.rows - 1))
            xs[out.index(column, row)] = px
            ys[out.index(column, row)] = py
        }
    }
    return out.copy(x = xs, y = ys)
}

/**
 * THE WRITES A REGRID IS (Video.cpp's `warpCopyWrites`, the editor's split): the grid and
 * its points together, as ONE `node.setMany` - one undo step, and the renderer never sees
 * a grid without its points. [base] is "/godot/videoOutput/<id>/" or "/godot/zone/<id>/".
 */
fun regridWrites(base: String, warp: Warp): List<Pair<String, String>> = listOf(
    base + "meshColumns" to warp.columns.toString(),
    base + "meshRows" to warp.rows.toString(),
    base + "mesh" to warp.text(),
)
