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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The drag's arithmetic - relative, the fader's rule along the line it started - and the nudge pad's. */
class PointDragTest {

    /** A display drawn 1000 px wide and 500 high: a pixel is a thousandth across and two thousandths down. */
    private fun drag(x: Double = 0.5, y: Double = 0.5) =
        PointDrag(x, y, unitsPerPxX = 0.001, unitsPerPxY = 0.002, slopPx = 10.0, precisionPx = 50.0)

    @Test
    fun theFingerTouchingDownMovesNothing() {
        val drag = drag(0.25, 0.75)
        assertFalse(drag.update(0.0, 0.0))
        assertEquals(0.25, drag.x, 0.0)
        assertEquals(0.75, drag.y, 0.0)
        assertFalse(drag.moved)
    }

    @Test
    fun withinTheSlopThePointFollowsFreelyAndRelatively() {
        val drag = drag(0.25, 0.75)
        assertTrue(drag.update(3.0, -4.0))
        assertEquals(0.253, drag.x, 1e-12)
        assertEquals(0.742, drag.y, 1e-12)
        assertNull(drag.axis)
        assertTrue(drag.moved)
    }

    @Test
    fun alongTheAxisThePointMovesOneToOne() {
        val drag = drag()
        drag.update(20.0, 0.0) // past the slop, travelling right: the axis is x
        val axis = drag.axis!!
        assertEquals(1.0, axis.first, 1e-12)
        assertEquals(0.0, axis.second, 1e-12)
        drag.update(120.0, 0.0)
        assertEquals(0.5 + 0.120, drag.x, 1e-12)
        assertEquals(0.5, drag.y, 1e-12)
    }

    @Test
    fun movingAcrossTheAxisAloneMovesNothing() {
        val drag = drag()
        drag.update(20.0, 0.0)
        val x = drag.x
        assertFalse(drag.update(20.0, 80.0))
        assertEquals(x, drag.x, 0.0)
        assertEquals(0.5, drag.y, 0.0)
    }

    @Test
    fun theDistanceAcrossDividesTheSpeed() {
        val drag = drag()
        drag.update(20.0, 0.0)
        // Fifty pixels off the line: half speed. 100 px along moves 50.
        drag.update(20.0, 50.0)
        assertEquals(0.5, drag.gainAt(20.0, 50.0), 1e-12)
        val before = drag.x
        drag.update(120.0, 50.0)
        assertEquals(before + 0.050, drag.x, 1e-12)
        // A hundred and fifty off: a quarter. 100 px along moves 25.
        drag.update(120.0, -150.0)
        val again = drag.x
        drag.update(220.0, -150.0)
        assertEquals(again + 0.025, drag.x, 1e-12)
        // The axis stays where it was set; y is never touched along x.
        assertEquals(0.5, drag.y, 0.0)
    }

    @Test
    fun theRatioIsContinuousAndNeverRunsTheMoveBackwards() {
        val drag = drag()
        drag.update(20.0, 0.0)
        drag.update(220.0, 0.0)
        val reached = drag.x
        // Sliding away from the line, and back: the point stays where it is.
        for (across in listOf(10.0, 40.0, 90.0, 200.0, 90.0, 0.0)) {
            drag.update(220.0, across)
            assertEquals(reached, drag.x, 0.0)
        }
        // Small steps at a growing distance: each step moves less than the one before, none backwards.
        var last = drag.x
        var lastStep = Double.MAX_VALUE
        for (k in 1..20) {
            drag.update(220.0 + k, 10.0 * k)
            val step = drag.x - last
            assertTrue(step > 0)
            assertTrue(step < lastStep)
            lastStep = step
            last = drag.x
        }
    }

    @Test
    fun aDiagonalStartIsADiagonalAxis() {
        val drag = drag()
        drag.update(-30.0, -40.0) // 50 px up and left: the axis is (-0.6, -0.8)
        val (ax, ay) = drag.axis!!
        assertEquals(-0.6, ax, 1e-12)
        assertEquals(-0.8, ay, 1e-12)
        val x = drag.x
        val y = drag.y
        drag.update(-60.0, -80.0) // 50 px more along it
        assertEquals(x - 0.030, drag.x, 1e-12)
        assertEquals(y - 0.080, drag.y, 1e-12)
    }

    @Test
    fun thePointStaysWithinHalfADisplayOfTheEdges() {
        val drag = drag(0.9, 0.5)
        drag.update(5000.0, 0.0)
        assertEquals(PointDrag.MAX, drag.x, 0.0)
        val back = drag(0.1, 0.1)
        back.update(0.0, -5000.0)
        assertEquals(PointDrag.MIN, back.y, 0.0)
    }

    @Test
    fun theNudgePadMovesAThousandthOrAHundredth() {
        val warp = Warp.whole(2, 2).moved(3, 0.9, 0.8)
        assertEquals(0.901, nudged(warp, 3, NudgeDirection.RIGHT, NudgeStep.FINE).x[3], 0.0)
        assertEquals(0.89, nudged(warp, 3, NudgeDirection.LEFT, NudgeStep.COARSE).x[3], 0.0)
        assertEquals(0.79, nudged(warp, 3, NudgeDirection.UP, NudgeStep.COARSE).y[3], 0.0)
        assertEquals(0.801, nudged(warp, 3, NudgeDirection.DOWN, NudgeStep.FINE).y[3], 0.0)
        // Only that point.
        assertEquals(warp.x.take(3), nudged(warp, 3, NudgeDirection.RIGHT, NudgeStep.FINE).x.take(3))
    }

    @Test
    fun aRunOfNudgesGathersNoDust() {
        var warp = Warp.whole(2, 2)
        repeat(7) { warp = nudged(warp, 0, NudgeDirection.RIGHT, NudgeStep.FINE) }
        assertEquals(0.007, warp.x[0], 0.0)
        assertEquals("0.007 0 1 0 0 1 1 1", warp.text())
        repeat(3) { warp = nudged(warp, 0, NudgeDirection.LEFT, NudgeStep.COARSE) }
        assertEquals(-0.023, warp.x[0], 0.0)
    }

    @Test
    fun aNudgeStopsAtTheDragsBoundsAndIgnoresAPointThatIsNotThere() {
        val warp = Warp.whole(2, 2).moved(0, PointDrag.MIN, 0.0)
        assertEquals(PointDrag.MIN, nudged(warp, 0, NudgeDirection.LEFT, NudgeStep.COARSE).x[0], 0.0)
        assertEquals(warp, nudged(warp, 4, NudgeDirection.LEFT, NudgeStep.COARSE))
    }

    @Test
    fun theFingerMeansTheNearestPointWithinReach() {
        val points = listOf(0.0 to 0.0, 100.0 to 0.0, 0.0 to 100.0, 100.0 to 100.0)
        assertEquals(1, nearestPoint(points, 90.0, 12.0, 30.0))
        assertEquals(3, nearestPoint(points, 80.0, 80.0, 30.0))
        assertNull(nearestPoint(points, 50.0, 50.0, 30.0))
    }
}
