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

package com.pob31.godot.remote.media

import com.pob31.godot.remote.Fixtures
import com.pob31.godot.remote.json.JsonPull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.util.Base64

/** A real sweep's timbre as `wfg serve` answered it (fixtures/timbre-sweep.json), and the format's edges. */
class TimbreTest {

    /** The fixture: the hash, the `?INFO` text, and each level's bytes. */
    class Sweep(val hash: String, val info: String, val levels: List<ByteArray>)

    companion object {
        fun sweep(): Sweep {
            var hash = ""
            var info = ""
            val levels = ArrayList<ByteArray>()
            JsonPull(StringReader(Fixtures.text("fixtures/timbre-sweep.json"))).use { json ->
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "hash" -> hash = json.nextString()
                        "info" -> info = json.nextString()
                        "levels" -> {
                            json.beginArray()
                            while (json.hasNext()) levels += Base64.getDecoder().decode(json.nextString())
                            json.endArray()
                        }
                        else -> json.skipValue()
                    }
                }
            }
            return Sweep(hash, info, levels)
        }
    }

    @Test
    fun theHeaderIsReadAsTheRouteWritesIt() {
        val sweep = sweep()
        val info = TimbreInfo.parse(sweep.info)!!
        assertEquals(sweep.hash, info.sha256)
        assertEquals(4, info.formatVersion)
        assertEquals(6.0, info.seconds, 0.0)
        assertEquals(48000, info.sampleRate)
        assertEquals(1024, info.hop)
        assertEquals(listOf(282, 141, 71, 36), info.levels.map { it.frames })
        // 6 s at 48 kHz is 288000 samples: ceil(288000 / 1024) frames at level 0.
        assertEquals((288000 + 1023) / 1024, info.levels[0].frames)
        for ((n, level) in info.levels.withIndex()) assertEquals(level.bytes, sweep.levels[n].size)
    }

    @Test
    fun anythingThatIsNotAHeaderIsNone() {
        val good = sweep().info
        assertNull(TimbreInfo.parse(""))
        assertNull(TimbreInfo.parse("no timbre has been analysed for 0000"))
        assertNull(TimbreInfo.parse(good.dropLast(5)))
        assertNull(TimbreInfo.parse(good.replace("\"levels\":[", "\"levels\":[{\"frames\":3,\"bytes\":11},")))
        assertNull(TimbreInfo.parse(good.replace(Regex("\"levels\":\\[.*]"), "\"levels\":[]")))
        assertNull(TimbreInfo.parse(good.replace("bb791f4d", "BB791F4D")))
        assertNull(TimbreInfo.parse(good.replace("\"formatVersion\":4,", "")))
        assertNull(TimbreInfo.parse("[1, 2, 3]"))
    }

    @Test
    fun aLevelIsFourBytesAFrameAndAnyOtherLengthIsNothing() {
        val sweep = sweep()
        val frames = TimbreFrames.of(sweep.levels[3], 36)!!
        assertEquals(36, frames.count)
        assertNull(TimbreFrames.of(sweep.levels[3], 35))
        assertNull(TimbreFrames.of(sweep.levels[3].copyOf(143), 36))
        assertNull(TimbreFrames.of(ByteArray(0), -1))
        assertEquals(0, TimbreFrames.of(ByteArray(0), 0)!!.count)
    }

    @Test
    fun aFramesBytesAreHueSaturationLightnessAndPeak() {
        val frames = TimbreFrames.of(byteArrayOf(128.toByte(), 255.toByte(), 51, 0, 0, 0, 0, 0), 2)!!
        assertEquals(180.0, frames.hue(0), 1e-12) // 128 / 256 of a turn
        assertEquals(1.0, frames.saturation(0), 1e-12)
        assertEquals(0.2, frames.lightness(0), 1e-12)
        assertEquals(0.0, frames.peak(0), 1e-12)
        assertFalse(frames.silent(0))
        assertTrue(frames.silent(1))
    }

    @Test
    fun theSweepsGapIsSilentAndItsSoundIsNot() {
        val sweep = sweep()
        val finest = TimbreFrames.of(sweep.levels[0], 282)!!
        // Silent from 2.6 to 3.0 s: frames 122 to 140 hold nothing but the edges of the windows.
        assertTrue(finest.silent(131))
        assertEquals(0.0, finest.peak(131), 0.0)
        // At 1 s the swell is near its top and the sweep sounds.
        val oneSecond = 48000 / 1024
        assertFalse(finest.silent(oneSecond))
        assertTrue(finest.peak(oneSecond) > 0.5)
    }

    @Test
    fun aStripReadsTheCoarsestLevelWithAFramePerColumn() {
        val levels = listOf(282, 141, 71, 36).map { TimbreLevel(it, it * 4) }
        assertEquals(3, levelFor(levels, 30))
        assertEquals(3, levelFor(levels, 36))
        assertEquals(2, levelFor(levels, 40)) // the Gogo bar's forty pixels
        assertEquals(1, levelFor(levels, 141))
        assertEquals(0, levelFor(levels, 200))
        assertEquals(0, levelFor(levels, 1840)) // wider than the file has frames: the finest
        assertEquals(0, levelFor(levels, 0))
        assertEquals(0, levelFor(emptyList(), 100))
    }

    @Test
    fun aColumnIsTheLoudestFrameOfItsSpan() {
        // Four frames, peaks 10, 200, 30, 40 (of 255), into two columns: the second and the fourth.
        val bytes = byteArrayOf(0, 0, 50, 10, 64, 0, 60, 200.toByte(), 0, 0, 70, 30, 128.toByte(), 0, 80, 40)
        val frames = TimbreFrames.of(bytes, 4)!!
        val columns = columnsOf(frames, 2)
        assertEquals(2, columns.size)
        assertEquals(200 / 255f, columns[0].peak, 1e-6f)
        assertEquals(90f, columns[0].hue, 1e-6f)
        assertEquals(40 / 255f, columns[1].peak, 1e-6f)
        assertEquals(180f, columns[1].hue, 1e-6f)
        // Wider than the level: frames repeat, no gaps.
        val wide = columnsOf(frames, 8)
        assertEquals(8, wide.size)
        assertEquals(wide[0], wide[1])
        assertEquals(10 / 255f, wide[0].peak, 1e-6f)
        // Nothing to draw: nothing.
        assertTrue(columnsOf(frames, 0).isEmpty())
        assertTrue(columnsOf(TimbreFrames.of(ByteArray(0), 0)!!, 10).isEmpty())
    }

    @Test
    fun aRealStripIsAsWideAsAsked() {
        val sweep = sweep()
        val info = TimbreInfo.parse(sweep.info)!!
        val level = levelFor(info.levels, 120)
        assertEquals(1, level)
        val columns = columnsOf(TimbreFrames.of(sweep.levels[level], info.levels[level].frames)!!, 120)
        assertEquals(120, columns.size)
        assertTrue(columns.all { it.peak in 0f..1f && it.hue in 0f..360f })
    }

    @Test
    fun aContentHashIsSixtyFourLowerCaseHexDigits() {
        assertTrue(isContentHash(sweep().hash))
        assertFalse(isContentHash(sweep().hash.uppercase()))
        assertFalse(isContentHash("abc"))
        assertFalse(isContentHash("../" + "0".repeat(61)))
    }
}
