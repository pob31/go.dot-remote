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

package com.pob31.godot.remote.ui.waveform

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pob31.godot.remote.link.Link
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.media.TimbreCache
import com.pob31.godot.remote.media.TimbreSource
import com.pob31.godot.remote.media.TimbreStrip
import com.pob31.godot.remote.media.columnsOf
import com.pob31.godot.remote.ui.rememberReading
import com.pob31.godot.remote.ui.theme.GodotColors
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The cache the strips read; the app's own unless a test provides one. */
val LocalTimbreCache = staticCompositionLocalOf<TimbreCache?> { null }

/**
 * A SOUND'S WAVEFORM, as Go.dot's run pane draws it (PRD §3.30): a column a pixel, its
 * colour what that slice SOUNDS like (hue from the spectral centroid, saturation from how
 * tonal it is, lightness rising with the centroid), its height the peak, mirrored about the
 * middle so it reads as a waveform; its edge traced; a playhead when [playhead] is given
 * (0..1 of the strip). The shape carries what the colour says to a reader who cannot sort
 * the hues (§4.8).
 *
 * For any tab: [hash] is the cue's `/godot/cue/<id>/hash`, [link] the machine it is asked
 * of. The level is fetched for the strip's width, on a background thread, kept under the
 * show's folder; until it comes - or while the machine has not analysed the file - a flat
 * grey line, never an invented shape. Asked again every few seconds while there is none.
 */
@Composable
fun Waveform(hash: String?, link: Link?, modifier: Modifier = Modifier, playhead: Float? = null) {
    val cache = LocalTimbreCache.current ?: TimbreCache.shared(LocalContext.current)
    val show = rememberReading(link, "") { it.text("/godot/document/name") ?: "" }
    var width by remember { mutableIntStateOf(0) }
    var strip by remember(hash, show) { mutableStateOf<TimbreStrip?>(null) }
    LaunchedEffect(hash, show, width, link) {
        if (hash == null || width <= 0) return@LaunchedEffect
        val source = link?.let { TimbreSource(it.host, it.port) }
        var wait = 2000L
        while (true) {
            val got = cache.strip(source, show, hash, width)
            if (got != null) {
                strip = got
                return@LaunchedEffect
            }
            delay(wait)
            wait = min(wait * 2, 30_000L)
        }
    }
    WaveformView(strip, modifier, playhead, onWidth = { width = it })
}

/** The strip drawn from what has been fetched: no fetching of its own, so a test draws it. */
@Composable
fun WaveformView(strip: TimbreStrip?, modifier: Modifier = Modifier, playhead: Float? = null, onWidth: (Int) -> Unit = {}) {
    val words = loc(if (strip == null) "waveform.notAnalysed" else "waveform.description")
    var width by remember { mutableIntStateOf(0) }
    // Bucketed once per strip and width, not at every playhead move.
    val columns = remember(strip, width) { if (strip == null) emptyList() else columnsOf(strip.frames, width) }
    Canvas(
        modifier
            .onSizeChanged { width = it.width; onWidth(it.width) }
            .semantics { contentDescription = words }
    ) {
        val middle = size.height / 2f
        val half = size.height / 2f
        if (columns.isEmpty()) {
            // Not analysed yet, or nothing fetched: grey, and no shape (§3.30).
            drawRect(GodotColors.inkOff, Offset(0f, middle - 0.5f), Size(size.width, 1f))
        } else {
            val reach = IntArray(columns.size) { max(1, (columns[it].peak * half).roundToInt()) }
            for ((x, column) in columns.withIndex()) {
                val colour = Color.hsv(
                    column.hue.coerceIn(0f, 359.99f), column.saturation.coerceIn(0f, 1f),
                    (0.25f + column.lightness * 0.6f).coerceIn(0f, 1f),
                )
                drawRect(colour, Offset(x.toFloat(), middle - reach[x]), Size(1f, 2f * reach[x]))
            }
            // The edge of the shape, traced in black - a job, not a token - with a riser to each
            // neighbour so it is a line and not a row of dashes.
            for (x in columns.indices) {
                val far = reach[x]
                drawRect(Color.Black, Offset(x.toFloat(), middle - far), Size(1f, 1f))
                drawRect(Color.Black, Offset(x.toFloat(), middle + far - 1), Size(1f, 1f))
                if (x == 0) continue
                val before = reach[x - 1]
                val rise = max(far, before) - min(far, before)
                if (rise > 0) {
                    drawRect(Color.Black, Offset(x.toFloat(), middle - max(far, before)), Size(1f, rise.toFloat()))
                    drawRect(Color.Black, Offset(x.toFloat(), middle + min(far, before)), Size(1f, rise.toFloat()))
                }
            }
        }
        playhead?.let { through ->
            // A line of ink between two of black: it reads on any colour (the run pane's head, in dp).
            val edge = max(1f, 0.75.dp.toPx())
            val ink = max(2f, 1.5.dp.toPx())
            val whole = ink + 2 * edge
            val x = through.coerceIn(0f, 1f) * (size.width - whole)
            drawRect(Color.Black, Offset(x, 0f), Size(whole, size.height))
            drawRect(GodotColors.ink, Offset(x + edge, 0f), Size(ink, size.height))
        }
    }
}
