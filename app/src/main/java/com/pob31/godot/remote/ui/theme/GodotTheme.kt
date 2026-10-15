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

package com.pob31.godot.remote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*  GO.DOT'S LOOK, its tokens by their desktop names (src/wfg/client/model/Theme.cpp,
    clients/desktop/theme.json). The values are the desktop window's, which are three
    greys lighter than the page's: a tablet's screen is read at arm's length in a dark
    room, which is the desktop's case and not a browser's. The drag-and-drop tokens
    (drop-*) are left out: the remote changes no cue list's shape.

    Colour is never the only thing that says something (PRD §4.8): every state worn
    here is said in a word beside it. */
object GodotColors {
    val ink = Color(0xFFE8E6E1)
    val inkDim = Color(0xFFC6C1B9)
    val inkFaint = Color(0xFFA9A49C)
    val inkOff = Color(0xFF8B867F)

    val ground = Color(0xFF16161A)
    val panel = Color(0xFF1D1D22)
    val panelInspect = Color(0xFF222226)
    val panelHigh = Color(0xFF24242B)
    val titleBar = Color(0xFF000000)
    val panelIn = Color(0xFF191920)
    val panelCue = Color(0xFF000000)
    val panelSection = Color(0xFF10141C)
    val panelSectionCue = Color(0xFF10141C)
    val panelRuns = Color(0xFF000000)
    val rule = Color(0xFF3A3A45)

    val standby = Color(0xFFE8B04B)
    val live = Color(0xFF4BC38A)
    val stopping = Color(0xFFD98B4B)
    val failed = Color(0xFFE0685D)
    val waiting = Color(0xFF86A3DB)
    val fade = Color(0xFF5FB8C9)
    val picked = Color(0xFF9A95E4)

    /** GO while the sound will leave, with black letters ([goInk]); [goIdle] while it will not. */
    val go = Color(0xFFFFD60A)
    val goInk = Color(0xFF000000)
    val goIdle = Color(0xFF4A4A52)

    /** Doh! while it can act, black letters on it as on GO; fades to [goIdle] as its window runs out. */
    val doh = Color(0xFFFF6EB4)
    val dohInk = Color(0xFF000000)

    val flag = Color(0xFFFF9330)

    /** The EQ's handles in field order: high-pass, bands one to four, low-pass (engine audio/EqColours.h). */
    val eqHighPass = Color(0xFFE74C3C)
    val eqBand1 = Color(0xFFE67E22)
    val eqBand2 = Color(0xFFFFEB3B)
    val eqBand3 = Color(0xFF2ECC71)
    val eqBand4 = Color(0xFF3498DB)
    val eqLowPass = Color(0xFF9B59B6)
    val eq = listOf(eqHighPass, eqBand1, eqBand2, eqBand3, eqBand4, eqLowPass)

    /*  A kind's accent, worn by its icon and nothing larger: a wash or a bar is the
        state's. `kind-<kind>` in Theme.cpp. */
    val kindMemo = Color(0xFFA9A49C)
    val kindMedia = Color(0xFF6CB4EE)
    val kindMic = Color(0xFFD98FC0)
    val kindVideo = Color(0xFF8CCB7E)
    val kindFade = Color(0xFF5FB8C9)
    val kindTransport = Color(0xFFE08A6E)
    val kindOsc = Color(0xFFD8C26A)
    val kindMidi = Color(0xFFB59CF0)
    val kindProcess = Color(0xFF7FD1AE)
    val kindScript = Color(0xFF8E9CF2)
    val kindTimecode = Color(0xFFBCD65E)
    val kindGroup = Color(0xFFC6C1B9)
    val kindTimeGroup = Color(0xFFCF8CEB)

    /**
     * The accent for a cue's `kind` word (`/godot/cue/<id>/kind`). A start cue wears the
     * transport's, being one of the cues that act on a cue (Theme.cpp); a word this app
     * does not know yet wears the ink's faint grey rather than a guess.
     */
    fun kindAccent(kind: String): Color = when (kind) {
        "memo" -> kindMemo
        "media" -> kindMedia
        "mic" -> kindMic
        "video" -> kindVideo
        "fade" -> kindFade
        "transport", "start" -> kindTransport
        "osc" -> kindOsc
        "midi" -> kindMidi
        "process" -> kindProcess
        "script" -> kindScript
        "timecode" -> kindTimecode
        "group" -> kindGroup
        "timegroup" -> kindTimeGroup
        else -> inkFaint
    }
}

/** The desktop's `type` (1.25) and `row` (26 px before `type`), read as dp. */
object GodotMetrics {
    const val TYPE_SCALE = 1.25f
    val row = (26 * TYPE_SCALE).dp
    /** No finger lands on less: Material's minimum touch target. */
    val touch = 48.dp
}

/** Numbers line up: a monospace face with tabular figures, as the page's `.num`. */
object GodotType {
    val number = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        fontSize = (13 * GodotMetrics.TYPE_SCALE).sp
    )
    val numberLarge = number.copy(fontSize = (20 * GodotMetrics.TYPE_SCALE).sp, fontWeight = FontWeight.Medium)
}

private val GodotColorScheme = darkColorScheme(
    primary = GodotColors.standby,
    onPrimary = Color.Black,
    secondary = GodotColors.picked,
    onSecondary = Color.Black,
    tertiary = GodotColors.fade,
    onTertiary = Color.Black,
    background = GodotColors.ground,
    onBackground = GodotColors.ink,
    surface = GodotColors.panel,
    onSurface = GodotColors.ink,
    surfaceVariant = GodotColors.panelHigh,
    onSurfaceVariant = GodotColors.inkDim,
    surfaceContainer = GodotColors.panel,
    surfaceContainerHigh = GodotColors.panelHigh,
    outline = GodotColors.rule,
    outlineVariant = GodotColors.rule,
    error = GodotColors.failed,
    onError = Color.Black,
)

/** Dark only: Go.dot has no light theme, and a show is run in the dark. */
@Composable
fun GodotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GodotColorScheme,
        typography = Typography(),
        content = content
    )
}
