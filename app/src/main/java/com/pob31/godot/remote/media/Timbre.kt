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

import com.pob31.godot.remote.json.JsonPull
import java.io.StringReader
import kotlin.math.max

/*  WHAT A SOUND IS MADE OF, AS GO.DOT SERVES IT (PRD §3.30; engine oscquery/TimbreRoute.h,
    audio/Timbre.h): a pyramid of levels, level 0 one frame a hop (1024 samples), each level
    above half the one below, down to 64 frames or fewer. A frame is four bytes - hue (256
    steps of a turn), saturation, lightness and peak (255 steps of the unit each); lightness
    nought is silence. The engine has already answered what a file looks like: nothing
    here analyses anything, it picks a level and buckets it into columns. */

/** One level's size, as `?INFO` says it. */
data class TimbreLevel(val frames: Int, val bytes: Int)

/**
 * `GET /media/<sha256>/timbre?INFO`:
 * `{"sha256":..,"formatVersion":..,"seconds":..,"sampleRate":..,"window":..,"hop":..,"levels":[{"frames":..,"bytes":..},...]}`.
 * [formatVersion] is the analysis that built the frames - the half of what the bytes depend on
 * that the hash does not carry - so a copy kept from another version is thrown away.
 */
data class TimbreInfo(
    val sha256: String,
    val formatVersion: Int,
    val seconds: Double,
    val sampleRate: Int,
    val hop: Int,
    val levels: List<TimbreLevel>,
) {
    companion object {
        /** The header, or null for anything that is not one - never an exception. */
        fun parse(text: String): TimbreInfo? = try {
            var sha = ""
            var version: Int? = null
            var seconds = 0.0
            var rate = 0
            var hop = 0
            val levels = ArrayList<TimbreLevel>()
            JsonPull(StringReader(text)).use { json ->
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "sha256" -> sha = json.nextString()
                        "formatVersion" -> version = json.nextNumber().toIntOrNull()
                        "seconds" -> seconds = json.nextNumber().toDoubleOrNull() ?: 0.0
                        "sampleRate" -> rate = json.nextNumber().toIntOrNull() ?: 0
                        "hop" -> hop = json.nextNumber().toIntOrNull() ?: 0
                        "levels" -> {
                            json.beginArray()
                            while (json.hasNext()) {
                                var frames = -1
                                var bytes = -1
                                json.beginObject()
                                while (json.hasNext()) {
                                    when (json.nextName()) {
                                        "frames" -> frames = json.nextNumber().toIntOrNull() ?: -1
                                        "bytes" -> bytes = json.nextNumber().toIntOrNull() ?: -1
                                        else -> json.skipValue()
                                    }
                                }
                                json.endObject()
                                levels += TimbreLevel(frames, bytes)
                            }
                            json.endArray()
                        }
                        else -> json.skipValue()
                    }
                }
                json.endObject()
            }
            val ok = isContentHash(sha) && version != null && levels.isNotEmpty() &&
                levels.all { it.frames >= 0 && it.bytes == it.frames * BYTES_PER_FRAME }
            if (ok) TimbreInfo(sha, version!!, seconds, rate, hop, levels) else null
        } catch (e: Exception) {
            null
        }
    }
}

const val BYTES_PER_FRAME = 4

/** Sixty-four lower-case hex digits: how the engine publishes a hash (`/godot/cue/<id>/hash`), and the only key the route takes. */
fun isContentHash(text: String): Boolean = text.length == 64 && text.all { it in '0'..'9' || it in 'a'..'f' }

/** One level's frames, as the route sends them. */
class TimbreFrames private constructor(val bytes: ByteArray) {
    val count: Int get() = bytes.size / BYTES_PER_FRAME

    fun hue(i: Int): Double = (bytes[4 * i].toInt() and 0xff) * 360.0 / 256.0
    fun saturation(i: Int): Double = (bytes[4 * i + 1].toInt() and 0xff) / 255.0
    fun lightness(i: Int): Double = (bytes[4 * i + 2].toInt() and 0xff) / 255.0
    fun peak(i: Int): Double = (bytes[4 * i + 3].toInt() and 0xff) / 255.0
    fun silent(i: Int): Boolean = bytes[4 * i + 2].toInt() == 0

    companion object {
        /**
         * The bytes read as [frames] frames - or null when they are not exactly that many:
         * a short read, a level of another analysis, a file gone bad. Nothing is drawn
         * from bytes that do not fit; nothing crashes on them.
         */
        fun of(bytes: ByteArray, frames: Int): TimbreFrames? =
            if (frames >= 0 && bytes.size == frames * BYTES_PER_FRAME) TimbreFrames(bytes) else null
    }
}

/**
 * WHICH LEVEL A STRIP THIS WIDE READS (client/model/Waveform.cpp `levelFor`): the coarsest
 * with at least a frame per column, so a column takes a handful of frames rather than
 * thousands; the finest when even that is not enough.
 */
fun levelFor(levels: List<TimbreLevel>, width: Int): Int {
    if (levels.isEmpty() || width <= 0) return 0
    for (level in levels.indices.reversed()) if (levels[level].frames >= width) return level
    return 0
}

/** One column of a strip: what that slice sounds like and how loud. */
data class WaveColumn(val hue: Float, val saturation: Float, val lightness: Float, val peak: Float)

/**
 * THE STRIP, one column per pixel across the whole file (Waveform.cpp `waveform`): each
 * column the LOUDEST frame of its span - a transient survives to the screen - and a strip
 * wider than the level repeats frames rather than leaving gaps. Empty when there is
 * nothing to draw.
 */
fun columnsOf(frames: TimbreFrames, width: Int): List<WaveColumn> {
    val count = frames.count
    if (width <= 0 || count == 0) return emptyList()
    val out = ArrayList<WaveColumn>(width)
    for (at in 0 until width) {
        val from = (at.toLong() * count / width).toInt()
        val to = max(from + 1, ((at + 1).toLong() * count / width).toInt())
        var loudest = from
        for (i in from until minOf(to, count)) if (frames.peak(i) > frames.peak(loudest)) loudest = i
        out += WaveColumn(
            frames.hue(loudest).toFloat(), frames.saturation(loudest).toFloat(),
            frames.lightness(loudest).toFloat(), frames.peak(loudest).toFloat(),
        )
    }
    return out
}

/** A strip's makings: the header, the level picked, its frames. */
class TimbreStrip(val info: TimbreInfo, val level: Int, val frames: TimbreFrames)
