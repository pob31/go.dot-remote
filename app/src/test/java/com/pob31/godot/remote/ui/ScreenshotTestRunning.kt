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

package com.pob31.godot.remote.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.localization.LocalLanguage
import com.pob31.godot.remote.localization.LocalizationManager
import com.pob31.godot.remote.model.Mirror
import com.pob31.godot.remote.model.RecordingWrites
import com.pob31.godot.remote.model.ShowFixture
import com.pob31.godot.remote.model.ShowFixture.CRICKETS
import com.pob31.godot.remote.model.ShowFixture.RUN_CRICKETS
import com.pob31.godot.remote.model.ShowFixture.RUN_NIGHT_BED
import com.pob31.godot.remote.model.ShowFixture.RUN_STORM
import com.pob31.godot.remote.model.ShowFixture.RUN_THUNDER
import com.pob31.godot.remote.model.ShowFixture.RUN_THUNDER_AND_RAIN
import com.pob31.godot.remote.model.ShowFixture.THUNDER
import com.pob31.godot.remote.model.ShowFixture.THUNDER_AND_RAIN
import com.pob31.godot.remote.model.put
import com.pob31.godot.remote.model.readHeader
import com.pob31.godot.remote.model.readRuns
import com.pob31.godot.remote.ui.running.RunSheet
import com.pob31.godot.remote.ui.running.RunningView
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * THE RUNNING TAB, DRAWN ON THE JVM into app/build/screenshots/ to be looked at: a real
 * engine's runs (fixtures/show-runs.json) brought to where a show would have them - a
 * sound playing with its timbre and trim, a group mid-scene with a member counting down,
 * a shuffled bed with its pills, one stopping, one failed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTestRunning {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val tables = ShowFixture.tables()

    private fun shoot(name: String, language: String = "en", content: @Composable () -> Unit) {
        LocalizationManager.loadLanguage(RuntimeEnvironment.getApplication(), language, remember = false)
        compose.setContent {
            CompositionLocalProvider(LocalLanguage provides language) {
                GodotTheme { Box(Modifier.fillMaxSize().background(GodotColors.ground)) { content() } }
            }
        }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** The fixture's runs as a show in progress would have them. */
    private fun showInProgress(): Mirror = ShowFixture.mirror().apply {
        put("/godot/audio/status", "running")
        // 1 Thunder and rain: playing, 12.3 s into 40, its colour, ridden down 4.5 dB.
        put("/godot/run/$RUN_THUNDER_AND_RAIN/state", "playing")
        put("/godot/run/$RUN_THUNDER_AND_RAIN/position", 12.34)
        put("/godot/run/$RUN_THUNDER_AND_RAIN/seekable", true)
        put("/godot/cue/$THUNDER_AND_RAIN/duration", 40.0)
        put("/godot/run/$RUN_THUNDER_AND_RAIN/timbre", "205 0.55 0.5")
        put("/godot/run/$RUN_THUNDER_AND_RAIN/trim", -4.5)
        put("/godot/run/$RUN_THUNDER_AND_RAIN/meter", -14.0)
        put("/godot/run/$RUN_THUNDER_AND_RAIN/level", -6.0)
        // 2 Storm: its members; Thunder stopping, 3.2 s in.
        put("/godot/run/$RUN_THUNDER/state", "stopping")
        put("/godot/run/$RUN_THUNDER/position", 3.2)
        put("/godot/cue/$THUNDER/duration", 30.0)
        put("/godot/run/$RUN_THUNDER/timbre", "28 0.7 0.45")
        put("/godot/run/$RUN_THUNDER/meter", -30.0)
        // 3 Night bed: round 1 of for ever, crickets playing, road to come, wind skipped.
        put("/godot/run/$RUN_CRICKETS/position", 8.7)
        put("/godot/cue/$CRICKETS/duration", 20.0)
        put("/godot/run/$RUN_CRICKETS/timbre", "95 0.5 0.5")
        put("/godot/run/$RUN_CRICKETS/meter", -22.0)
        put("/godot/run/$RUN_NIGHT_BED/position", 8.7)
        put("/godot/run/$RUN_STORM/position", 3.2)
    }

    @Composable
    private fun tab(mirror: Mirror, state: LinkState = LinkState.LIVE) =
        RunningView(readRuns(mirror), state, RecordingWrites(), tables.fader)

    @Composable
    private fun withHeader(mirror: Mirror, state: LinkState = LinkState.LIVE) {
        Shell(
            null,
            header = { TransportHeaderView("Main FOH", state, readHeader(mirror), null, send = { true }, nowMs = { 0L }) },
            machinesTab = {},
            initialTab = RemoteTab.RUNNING,
            runningTab = { tab(mirror, state) },
        )
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun runningOnTheTablet() = shoot("running-tablet") { withHeader(showInProgress()) }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun runningOnTheTabletInFrench() = shoot("running-tablet-fr", "fr") { withHeader(showInProgress()) }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun runningStale() = shoot("running-stale") { withHeader(showInProgress(), LinkState.STALE) }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun runningOnAPhone() = shoot("running-phone") { tab(showInProgress()) }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun runningOnAPhoneInFrench() = shoot("running-phone-fr", "fr") { tab(showInProgress()) }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun aGroupsSheet() = shoot("running-sheet") {
        val bed = readRuns(showInProgress()).first { it.id == RUN_NIGHT_BED }
        Box(Modifier.background(GodotColors.panelHigh)) { RunSheet(bed, onSend = {}, onClose = {}) }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun aSoundsSheetInFrench() = shoot("running-sheet-phone-fr", "fr") {
        val sound = readRuns(showInProgress()).first { it.id == RUN_THUNDER_AND_RAIN }
        Box(Modifier.background(GodotColors.panelHigh)) { RunSheet(sound, onSend = {}, onClose = {}) }
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun nothingRunning() = shoot("running-empty") {
        RunningView(emptyList(), LinkState.LIVE, RecordingWrites(), tables.fader)
    }
}
