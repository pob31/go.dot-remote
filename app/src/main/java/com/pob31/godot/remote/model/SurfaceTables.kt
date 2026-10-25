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

package com.pob31.godot.remote.model

import android.content.Context
import com.pob31.godot.remote.json.JsonException
import com.pob31.godot.remote.json.JsonPull
import java.io.Reader

/*  THE SURFACE'S TABLES, AS GO.DOT WRITES THEM (tests/fixtures/remote/surface-tables.json,
    namespace draft §68, A.7): the EQ page's sixteen controls (engine SurfacePages.h), how
    far a detent turns each law (SurfaceProfile.h) and the fader the virtual panel draws
    (FaderCurve.h, the generic law). The app carries a copy in its assets,
    app/src/main/assets/surface-tables.json, and a test holds the copy to Go.dot's file
    whenever both are on the machine - change the headers, Go.dot's file and this copy
    together. Nothing here restates a number: every law reads the file. */

/**
 * How a control moves and reads (SurfacePages.h `Law`). PAN and STEREO_WIDTH are the Pan
 * page's (namespace draft §113): a send's lean in -1..1, drawn from the centre, and a stereo
 * send's width in 0..1 - never among the EQ's sixteen, and not WIDTH, which is the EQ's Q.
 */
enum class EqLaw { FREQUENCY, GAIN, WIDTH, SHAPE, LEVEL, PAN, STEREO_WIDTH }

/** What a press on a control does (SurfacePages.h `Press`); CENTRE puts a pan or a balance back at nought. */
enum class EqPress { NONE, TOGGLE_SWITCH, TOGGLE_SHAPE, CENTRE }

/** One of the EQ page's sixteen controls, in the author's order (SurfacePages.h `eqControls`). */
data class EqControl(
    /** The cue's row it turns: `eqB2Gain`. */
    val row: String,
    val label: String,
    val short: String,
    val law: EqLaw,
    val min: Double,
    val max: Double,
    /** 0 the high-pass, 1..4 the bands, 5 the low-pass: an index into the theme's EQ colours. */
    val colour: Int,
    /** The switch that takes this control's band out when it is false. */
    val switchRow: String,
    val press: EqPress,
    /** A shape's shelf - `lowShelf`, `highShelf` - or empty. */
    val shelf: String,
)

/** How far a detent moves each law (SurfaceProfile.h's `page` numbers). */
data class Detents(
    val octavesPerDetent: Double,
    val gainStepDb: Double,
    val widthDoublingsPerDetent: Double,
    val levelTravelPerDetent: Double,
    /** A pan's, a balance's and a width's (namespace draft §113, `pagePlacePerDetent`). */
    val placePerDetent: Double,
)

/**
 * WHERE A FADER SITS FOR A LEVEL (FaderCurve.h, the generic law; the desktop's
 * client/model/Fader.h): points of (fraction, dB), bottom to top, straight between them -
 * which is what makes the inverse exact: a place dragged to, read as decibels and typed
 * back, lands on the same place. Silence is the bottom exactly.
 */
data class FaderCurve(val silenceDb: Double, val loudestDb: Double, val points: List<Pair<Double, Double>>) {

    init {
        require(points.size >= 2) { "a fader curve needs two points at least" }
    }

    /** Where on the travel, 0 at the bottom to 1 at the top, a level sits. Not a number sits at the bottom. */
    fun fractionForDb(decibels: Double): Double {
        if (!decibels.isFinite()) return if (decibels > 0) 1.0 else 0.0
        val level = decibels.coerceIn(points.first().second, points.last().second)
        for (at in 1 until points.size) {
            val below = points[at - 1]
            val above = points[at]
            if (level > above.second) continue
            val span = above.second - below.second
            if (!(span > 0.0)) return below.first
            return below.first + (above.first - below.first) * ((level - below.second) / span)
        }
        return 1.0
    }

    /** And back: what a place on the travel means, clamped into the range. */
    fun dbForFraction(fraction: Double): Double {
        if (!fraction.isFinite()) return silenceDb
        val place = fraction.coerceIn(0.0, 1.0)
        for (at in 1 until points.size) {
            val below = points[at - 1]
            val above = points[at]
            if (place > above.first) continue
            val span = above.first - below.first
            if (!(span > 0.0)) return below.second
            return below.second + (above.second - below.second) * ((place - below.first) / span)
        }
        return points.last().second
    }
}

class SurfaceTables(val eqControls: List<EqControl>, val detent: Detents, val fader: FaderCurve) {

    /** The control that turns [row], or null. */
    fun control(row: String): EqControl? = eqControls.firstOrNull { it.row == row }

    companion object {
        const val ASSET = "surface-tables.json"

        @Volatile private var loaded: SurfaceTables? = null

        /** The app's copy, read once a process. */
        fun load(context: Context): SurfaceTables =
            loaded ?: synchronized(this) {
                loaded ?: context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { parse(it) }.also { loaded = it }
            }

        /** The file read whole. Throws [JsonException] on a file that is not the tables. */
        fun parse(input: Reader): SurfaceTables {
            var controls: List<EqControl>? = null
            var detent: Detents? = null
            var fader: FaderCurve? = null
            JsonPull(input).use { json ->
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "eqControls" -> controls = readControls(json)
                        "detent" -> detent = readDetent(json)
                        "fader" -> fader = readFader(json)
                        else -> json.skipValue()
                    }
                }
                json.endObject()
            }
            return SurfaceTables(
                controls ?: throw JsonException("no eqControls"),
                detent ?: throw JsonException("no detent"),
                fader ?: throw JsonException("no fader"),
            )
        }

        private fun readControls(json: JsonPull): List<EqControl> {
            val out = ArrayList<EqControl>(16)
            json.beginArray()
            while (json.hasNext()) {
                val fields = HashMap<String, String>()
                json.beginObject()
                while (json.hasNext()) {
                    val name = json.nextName()
                    fields[name] = when (json.peek()) {
                        JsonPull.Token.STRING -> json.nextString()
                        JsonPull.Token.NUMBER -> json.nextNumber()
                        else -> { json.skipValue(); "" }
                    }
                }
                json.endObject()
                fun text(name: String) = fields[name] ?: throw JsonException("an EQ control without $name")
                fun number(name: String) = text(name).toDoubleOrNull() ?: throw JsonException("$name is not a number")
                out += EqControl(
                    row = text("row"),
                    label = text("label"),
                    short = text("short"),
                    law = when (text("law")) {
                        "frequency" -> EqLaw.FREQUENCY
                        "gain" -> EqLaw.GAIN
                        "width" -> EqLaw.WIDTH
                        "shape" -> EqLaw.SHAPE
                        "level" -> EqLaw.LEVEL
                        "pan" -> EqLaw.PAN
                        "stereoWidth" -> EqLaw.STEREO_WIDTH
                        else -> throw JsonException("a law nobody knows: ${text("law")}")
                    },
                    min = number("min"),
                    max = number("max"),
                    colour = number("colour").toInt(),
                    switchRow = text("switch"),
                    press = when (text("press")) {
                        "toggleSwitch" -> EqPress.TOGGLE_SWITCH
                        "toggleShape" -> EqPress.TOGGLE_SHAPE
                        else -> EqPress.NONE
                    },
                    shelf = text("shelf"),
                )
            }
            json.endArray()
            return out
        }

        private fun readNumbers(json: JsonPull): Map<String, Double> {
            val out = HashMap<String, Double>()
            json.beginObject()
            while (json.hasNext()) {
                val name = json.nextName()
                if (json.peek() == JsonPull.Token.NUMBER) out[name] = json.nextNumber().toDouble() else json.skipValue()
            }
            json.endObject()
            return out
        }

        private fun readDetent(json: JsonPull): Detents {
            val numbers = readNumbers(json)
            fun number(name: String) = numbers[name] ?: throw JsonException("no detent.$name")
            return Detents(
                number("octavesPerDetent"), number("gainStepDb"),
                number("widthDoublingsPerDetent"), number("levelTravelPerDetent"), number("placePerDetent"),
            )
        }

        private fun readFader(json: JsonPull): FaderCurve {
            var silence: Double? = null
            var loudest: Double? = null
            val points = ArrayList<Pair<Double, Double>>()
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "silenceDb" -> silence = json.nextNumber().toDouble()
                    "loudestDb" -> loudest = json.nextNumber().toDouble()
                    "points" -> {
                        json.beginArray()
                        while (json.hasNext()) {
                            json.beginArray()
                            val fraction = json.nextNumber().toDouble()
                            val db = json.nextNumber().toDouble()
                            json.endArray()
                            points += fraction to db
                        }
                        json.endArray()
                    }
                    else -> json.skipValue()
                }
            }
            json.endObject()
            return FaderCurve(
                silence ?: throw JsonException("no fader.silenceDb"),
                loudest ?: throw JsonException("no fader.loudestDb"),
                points,
            )
        }
    }
}
