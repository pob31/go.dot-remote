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
import com.pob31.godot.remote.model.CueTreeCache
import com.pob31.godot.remote.model.Folds
import com.pob31.godot.remote.model.Mirror
import com.pob31.godot.remote.model.RecordingWrites
import com.pob31.godot.remote.model.ShowFixture
import com.pob31.godot.remote.model.ShowFixture.ACT_ONE
import com.pob31.godot.remote.model.ShowFixture.FOYER
import com.pob31.godot.remote.model.ShowFixture.INTERVAL
import com.pob31.godot.remote.model.ShowFixture.RAIN
import com.pob31.godot.remote.model.ShowFixture.RUN_THUNDER_AND_RAIN
import com.pob31.godot.remote.model.ShowFixture.THUNDER_AND_RAIN
import com.pob31.godot.remote.model.put
import com.pob31.godot.remote.model.readCues
import com.pob31.godot.remote.model.readHeader
import com.pob31.godot.remote.ui.cues.CueSheet
import com.pob31.godot.remote.ui.cues.CuesView
import com.pob31.godot.remote.ui.cues.InspectorView
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
 * THE CUES TAB AND ITS INSPECTOR, DRAWN ON THE JVM into app/build/screenshots/: a real
 * engine's show (fixtures/show-runs.json) - a list with a group's header, members and
 * footer, a shuffled bed, a flag, a disabled cue - and the inspector on a media cue with
 * two sends, its EQ, a DCA and its run's trim; then the same under the show lock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTestCues {

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

    private fun show(locked: Boolean = false): Mirror = ShowFixture.mirror().apply {
        put("/godot/audio/status", "running")
        put("/godot/list/focus", ACT_ONE)
        put("/godot/run/$RUN_THUNDER_AND_RAIN/state", "playing")
        put("/godot/cue/$INTERVAL/override", "on")
        if (locked) {
            put("/godot/document/locked", true)
            put("/godot/document/live", 2L)
            put("/godot/cue/$THUNDER_AND_RAIN/live", "eqB2Gain")
            put("/godot/cue/$THUNDER_AND_RAIN/eqB2Gain", -6.0)
            put("/godot/send/S1000001/live", true)
            put("/godot/send/S1000001/level", -9.0)
        }
    }

    private fun reading(mirror: Mirror, viewed: String? = null, picked: String? = THUNDER_AND_RAIN, folds: Folds = Folds()) =
        readCues(mirror, viewed, picked, folds, CueTreeCache(), tables)

    @Composable
    private fun withHeader(mirror: Mirror, picked: String? = THUNDER_AND_RAIN) {
        Shell(
            null,
            header = { TransportHeaderView("Main FOH", LinkState.LIVE, readHeader(mirror), null, send = { true }, nowMs = { 0L }) },
            machinesTab = {},
            initialTab = RemoteTab.CUES,
            cuesTab = { CuesView(reading(mirror, picked = picked), LinkState.LIVE, RecordingWrites(), tables, picked) },
        )
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun cuesOnTheTablet() = shoot("cues-tablet") { withHeader(show()) }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun cuesOnTheTabletInFrench() = shoot("cues-tablet-fr", "fr") { withHeader(show()) }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun cuesLocked() = shoot("cues-tablet-locked") { withHeader(show(locked = true)) }

    /** Storm folded on the tablet: the standby, an override for this run and a disabled cue in sight. */
    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun cuesFolded() = shoot("cues-tablet-folded") {
        val folds = Folds().apply { toggle(ShowFixture.STORM) }
        CuesView(reading(show(), picked = INTERVAL, folds = folds), LinkState.LIVE, RecordingWrites(), tables, INTERVAL)
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun cuesStale() = shoot("cues-tablet-stale") {
        val mirror = show()
        CuesView(reading(mirror), LinkState.STALE, RecordingWrites(), tables, THUNDER_AND_RAIN)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun cuesOnAPhone() = shoot("cues-phone") {
        CuesView(reading(show(), picked = null), LinkState.LIVE, RecordingWrites(), tables, null)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun anotherListOnAPhoneInFrench() = shoot("cues-phone-foyer-fr", "fr") {
        CuesView(reading(show(), viewed = FOYER, picked = null), LinkState.LIVE, RecordingWrites(), tables, null)
    }

    /** The phone's inspector is a sheet; drawn here whole, on a tall screen, to be read top to bottom. */
    @Test
    @Config(qualifiers = "w411dp-h2400dp-port-xhdpi")
    fun theInspectorOnAPhone() = shoot("cues-inspector-phone") {
        InspectorView(reading(show()).inspection, RecordingWrites(), tables, Modifier.background(GodotColors.panelInspect), scroll = false)
    }

    /** The sends with places (namespace draft §113): a stereo send's balance, width and swap; a mono send's pan. */
    @Test
    @Config(qualifiers = "w411dp-h2400dp-port-xhdpi")
    fun theInspectorWithSendPlacesOnAPhone() = shoot("cues-inspector-places-phone") {
        val mirror = show().apply {
            val sends = com.pob31.godot.remote.model.readSends(this, ShowFixture.THUNDER_AND_RAIN)
            put(sends[0].address("placement"), "stereo")
            put(sends[0].address("balance"), -0.3)
            put(sends[0].address("width"), 0.6)
            put(sends[0].address("swap"), true)
            put(sends[1].address("placement"), "pan")
            put(sends[1].address("pan"), 0.45)
        }
        InspectorView(reading(mirror).inspection, RecordingWrites(), tables, Modifier.background(GodotColors.panelInspect), scroll = false)
    }

    @Test
    @Config(qualifiers = "w411dp-h2400dp-port-xhdpi")
    fun theInspectorLockedOnAPhoneInFrench() = shoot("cues-inspector-phone-locked-fr", "fr") {
        InspectorView(reading(show(locked = true)).inspection, RecordingWrites(), tables, Modifier.background(GodotColors.panelInspect), scroll = false)
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun aMemoInTheInspector() = shoot("cues-inspector-memo") {
        InspectorView(reading(show(), picked = INTERVAL).inspection, RecordingWrites(), tables, Modifier.background(GodotColors.panelInspect))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun aCuesSheet() = shoot("cues-sheet-phone") {
        val mirror = show()
        val reading = reading(mirror)
        Box(Modifier.background(GodotColors.panelHigh)) {
            CueSheet(reading.rows.first { it.id == RAIN }, reading.marks, isGoList = true, onSend = {}, onFlag = {}, onClose = {})
        }
    }
}
