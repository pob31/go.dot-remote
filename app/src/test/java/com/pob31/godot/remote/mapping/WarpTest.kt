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

import com.pob31.godot.remote.osc.OscValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The mesh row both ways, the renderer's curve and `regridded`, against the desktop's own cases (ClientTests §40). */
class WarpTest {

    private fun d(vararg numbers: Double) = numbers.map { OscValue.Float64(it) }

    @Test
    fun theMeshRowsTextRoundTrips() {
        // ClientTests: the zone's mesh "0.1 0.1 0.5 0 0 1 0.5 0.9" is read and written back unchanged.
        val warp = Warp.read(2.0, 2.0, d(0.1, 0.1, 0.5, 0.0, 0.0, 1.0, 0.5, 0.9))
        assertEquals("0.1 0.1 0.5 0 0 1 0.5 0.9", warp.text())
        assertFalse(warp.isWhole)
        assertEquals(warp, Warp.parse(warp.text(), 2, 2))
    }

    @Test
    fun theTextIsFourDecimalsInNoLocalesWay() {
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.FRANCE)
        try {
            val warp = Warp(2, 2, listOf(0.123456, 1.0, -0.00001, 0.33336), listOf(0.5, 0.0, 1.0, 0.99999))
            assertEquals("0.1235 0.5 1 0 0 1 0.3334 1", warp.text())
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun aMeshTheWrongSizeForItsGridIsTheWholeOutput() {
        // ClientTests: three numbers for a two by two grid read as the whole output, as the renderer reads it.
        assertTrue(Warp.read(2.0, 2.0, d(0.0, 0.0, 1.0)).isWhole)
        assertTrue(Warp.read(3.0, 2.0, d(0.1, 0.1, 0.5, 0.0, 0.0, 1.0, 0.5, 0.9)).isWhole)
        assertEquals(3, Warp.read(3.0, 2.0, null).columns)
        // A grid out of bounds is clamped to 2..16, as readWarp does.
        assertEquals(16, Warp.read(40.0, 1.0, null).columns)
        assertEquals(2, Warp.read(40.0, 1.0, null).rows)
        // Nothing that is not a number is a mesh.
        assertTrue(Warp.read(2.0, 2.0, listOf(OscValue.Float64(0.1), OscValue.Nil, OscValue.Float64(0.2), OscValue.Float64(0.3), OscValue.Float64(0.1), OscValue.Float64(0.1), OscValue.Float64(0.1), OscValue.Float64(0.1))).isWhole)
        assertTrue(Warp.read(2.0, 2.0, d(0.0, 0.0, 1.0, 0.0, 0.0, Double.NaN, 1.0, 1.0)).isWhole)
    }

    @Test
    fun aMeshWrittenAsOneStringReadsTheSame() {
        val warp = Warp.read(2.0, 2.0, listOf(OscValue.Str("0.1 0.1 0.5 0 0 1 0.5 0.9")))
        assertEquals(0.1, warp.x[0], 0.0)
        assertEquals(0.9, warp.y[3], 0.0)
        assertNull(Warp.parse("0.1 0.1 nope 0 0 1 0.5 0.9", 2, 2))
        assertNull(Warp.parse("0.1 0.1", 2, 2))
    }

    @Test
    fun theWholeOutputIsTheIdentity() {
        val whole = Warp.whole(4, 3)
        assertTrue(whole.isWhole)
        assertEquals(12, whole.size)
        assertEquals(1.0 / 3, whole.x[whole.index(1, 0)], 1e-12)
        assertEquals(0.5, whole.y[whole.index(3, 1)], 1e-12)
        for (s in listOf(0.0, 0.2, 0.5, 0.77, 1.0)) {
            for (t in listOf(0.0, 0.31, 1.0)) {
                val (x, y) = whole.at(s, t)
                assertEquals(s, x, 1e-6)
                assertEquals(t, y, 1e-6)
            }
        }
    }

    @Test
    fun aSplitAddedKeepsTheCornersAndPutsTheNewPointsOnTheWarp() {
        // ClientTests §40, the zone regridded to 3 x 2.
        val warp = Warp.read(2.0, 2.0, d(0.1, 0.1, 0.5, 0.0, 0.0, 1.0, 0.5, 0.9))
        val finer = regridded(warp, 3, 2)
        assertEquals(6, finer.size)
        assertEquals(0.1, finer.x[finer.index(0, 0)], 1e-6)
        assertEquals(0.5, finer.x[finer.index(2, 0)], 1e-6)
        assertEquals(0.9, finer.y[finer.index(2, 1)], 1e-6)
        assertTrue(finer.x[finer.index(1, 0)] > 0.1)
        assertTrue(finer.x[finer.index(1, 0)] < 0.5)
        // Two by two is a corner pin: Catmull-Rom through straight-extrapolated phantoms is bilinear,
        // so the new middle points are the edges' midpoints exactly.
        assertEquals(0.3, finer.x[finer.index(1, 0)], 1e-6)
        assertEquals(0.05, finer.y[finer.index(1, 0)], 1e-6)
        assertEquals(0.25, finer.x[finer.index(1, 1)], 1e-6)
        assertEquals(0.95, finer.y[finer.index(1, 1)], 1e-6)
    }

    @Test
    fun theWholeOutputRegriddedIsStillTheWholeOutput() {
        assertTrue(regridded(Warp.whole(), 4, 3).isWhole)
        assertTrue(regridded(Warp.whole(5, 4), 2, 2).isWhole)
    }

    @Test
    fun aFinerGridThroughEveryOldPointLeavesThemWhereTheyWere() {
        // A 3 x 3 bent warp to 5 x 5: every other new point is an old one - the curve passes through its points.
        val bent = Warp(3, 3,
            listOf(0.05, 0.5, 0.95, 0.0, 0.52, 1.0, 0.04, 0.5, 0.97),
            listOf(0.02, 0.06, 0.0, 0.5, 0.48, 0.55, 0.96, 1.0, 0.99))
        val finer = regridded(bent, 5, 5)
        for (row in 0 until 3) {
            for (column in 0 until 3) {
                val old = bent.index(column, row)
                val new = finer.index(2 * column, 2 * row)
                assertEquals(bent.x[old], finer.x[new], 1e-6)
                assertEquals(bent.y[old], finer.y[new], 1e-6)
            }
        }
        // And back down: the original grid again.
        val back = regridded(finer, 3, 3)
        for (n in 0 until bent.size) {
            assertEquals(bent.x[n], back.x[n], 1e-5)
            assertEquals(bent.y[n], back.y[n], 1e-5)
        }
    }

    @Test
    fun aRegridIsOneSetManyOfTheGridAndItsPoints() {
        val writes = regridWrites("/godot/zone/512QZPQT/", regridded(Warp.whole(), 3, 2))
        assertEquals(
            listOf(
                "/godot/zone/512QZPQT/meshColumns" to "3",
                "/godot/zone/512QZPQT/meshRows" to "2",
                "/godot/zone/512QZPQT/mesh" to "0 0 0.5 0 1 0 0 1 0.5 1 1 1",
            ),
            writes
        )
        // The grid clamped to the renderer's 2..16.
        assertEquals(16, regridded(Warp.whole(), 17, 2).columns)
        assertEquals(2, regridded(Warp.whole(), 1, 2).columns)
    }
}
