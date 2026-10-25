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

package com.pob31.godot.remote.ui.surface

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
import com.pob31.godot.remote.model.ShowFixture.ACT_ONE
import com.pob31.godot.remote.model.ShowFixture.CRICKETS
import com.pob31.godot.remote.model.ShowFixture.DRIPS
import com.pob31.godot.remote.model.ShowFixture.RAIN
import com.pob31.godot.remote.model.ShowFixture.RUN_CRICKETS
import com.pob31.godot.remote.model.ShowFixture.RUN_THUNDER_AND_RAIN
import com.pob31.godot.remote.model.ShowFixture.SIREN
import com.pob31.godot.remote.model.ShowFixture.THUNDER_AND_RAIN
import com.pob31.godot.remote.model.SurfacePage
import com.pob31.godot.remote.model.THIS_TABLET
import com.pob31.godot.remote.model.put
import com.pob31.godot.remote.model.readHeader
import com.pob31.godot.remote.model.readSurface
import com.pob31.godot.remote.ui.RemoteTab
import com.pob31.godot.remote.ui.Shell
import com.pob31.godot.remote.ui.TransportHeaderView
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
 * THE SURFACE TAB, DRAWN ON THE JVM into app/build/screenshots/: the fixture show
 * (fixtures/show-runs.json) given a D700 that is declared and not connected - eight
 * strips, four sampler strips in use (one playing and aimed at, one held, one armed, one
 * waiting for a voice), two free, two on the show's DCAs - and a pad controller with four
 * pads; its EQ and Send pages; "This tablet"; under the lock; on a phone; in French.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTestSurface {

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

    private fun Mirror.strip(id: String, surface: String, index: Long, role: String, endpoint: String = "absolute",
                             cue: String = "", holder: String = "", target: String = "", word: String = "free", dca: String = "") {
        val base = "/godot/slot/$id/"
        put(base + "kind", "strip")
        put(base + "surface", surface)
        put(base + "index", index)
        put(base + "role", role)
        put(base + "endpoint", endpoint)
        put(base + "cue", cue)
        put(base + "holder", holder)
        put(base + "target", target)
        put(base + "word", word)
        put(base + "dca", dca)
    }

    /** The fixture show mid-scene, with a D700 nobody plugged in and a pad controller that is. */
    private fun show(locked: Boolean = false): Mirror = ShowFixture.mirror().apply {
        put("/godot/audio/status", "running")
        put("/godot/list/focus", ACT_ONE)
        put("/godot/surface/order", "$D700 $PADS")
        for ((id, name, profile, connected, strips) in listOf(
            Surface5(D700, "D700", "d700", false, 8L),
            Surface5(PADS, "", "midiPads", true, 4L),
        )) {
            put("/godot/surface/$id/name", name)
            put("/godot/surface/$id/profile", profile)
            put("/godot/surface/$id/enabled", true)
            put("/godot/surface/$id/connected", connected)
            put("/godot/surface/$id/problem", "")
            put("/godot/surface/$id/strips", strips)
        }
        // 1 Thunder and rain playing, ridden down 4.5 dB, and aimed at; 2 crickets held by a pad somewhere.
        put("/godot/run/$RUN_THUNDER_AND_RAIN/state", "playing")
        put("/godot/run/$RUN_THUNDER_AND_RAIN/meter", -14.0)
        put("/godot/run/$RUN_THUNDER_AND_RAIN/trim", -4.5)
        put("/godot/run/$RUN_CRICKETS/state", "playing")
        put("/godot/run/$RUN_CRICKETS/meter", -22.0)
        put("/godot/run/$RUN_CRICKETS/held", true)
        put("/godot/run/R3000001/trim", 0.0)
        put("/godot/surface/aim", THUNDER_AND_RAIN)
        strip("T7000001", D700, 0, "sampler", cue = THUNDER_AND_RAIN, holder = RUN_THUNDER_AND_RAIN, target = "/godot/run/$RUN_THUNDER_AND_RAIN/trim", word = "playing")
        strip("T7000002", D700, 1, "sampler", cue = CRICKETS, holder = RUN_CRICKETS, target = "/godot/run/$RUN_CRICKETS/trim", word = "held")
        strip("T7000003", D700, 2, "sampler", cue = RAIN, target = "/godot/run/R3000001/trim", word = "armed")
        strip("T7000004", D700, 3, "sampler", cue = DRIPS, word = "pending")
        strip("T7000005", D700, 4, "sampler")
        strip("T7000006", D700, 5, "sampler")
        strip("T7000007", D700, 6, "dca", target = "/godot/dca/D1000001/trim", word = "dca", dca = "D1000001")
        strip("T7000008", D700, 7, "dca", target = "/godot/dca/D2000001/trim", word = "dca", dca = "D2000001")
        put("/godot/run/RPAD0001/held", true)
        put("/godot/run/RPAD0001/meter", -9.0)
        put("/godot/run/RPAD0001/trim", 0.0)
        strip("TPAD0001", PADS, 0, "sampler", "gate", cue = SIREN, holder = "RPAD0001", target = "/godot/run/RPAD0001/trim", word = "held")
        strip("TPAD0002", PADS, 1, "sampler", "gate", cue = DRIPS, word = "armed")
        strip("TPAD0003", PADS, 2, "sampler", "gate")
        strip("TPAD0004", PADS, 3, "sampler", "gate")
        if (locked) {
            put("/godot/document/locked", true)
            put("/godot/document/live", 2L)
            put("/godot/cue/$THUNDER_AND_RAIN/live", "eqB2Gain")
            put("/godot/cue/$THUNDER_AND_RAIN/eqB2Gain", -6.0)
            put("/godot/send/S1000001/live", true)
            put("/godot/send/S1000001/level", -9.0)
        }
    }

    private data class Surface5(val id: String, val name: String, val profile: String, val connected: Boolean, val strips: Long)

    @Composable
    private fun tab(mirror: Mirror, page: SurfacePage, surface: String? = null, state: LinkState = LinkState.LIVE) =
        SurfaceView(readSurface(mirror, surface, tables), state, RecordingWrites(), tables, page)

    @Composable
    private fun withHeader(mirror: Mirror, page: SurfacePage, surface: String? = null, state: LinkState = LinkState.LIVE) {
        Shell(
            null,
            header = { TransportHeaderView("Main FOH", state, readHeader(mirror), null, send = { true }, nowMs = { 0L }) },
            machinesTab = {},
            initialTab = RemoteTab.SURFACE,
            surfaceTab = { tab(mirror, page, surface, state) },
        )
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theFadersOnTheTablet() = shoot("surface-tablet") { withHeader(show(), SurfacePage.FADERS) }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theFadersOnTheTabletInFrench() = shoot("surface-tablet-fr", "fr") { withHeader(show(), SurfacePage.FADERS) }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theEqPage() = shoot("surface-eq-tablet") { withHeader(show(), SurfacePage.EQ) }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theSendPage() = shoot("surface-sends-tablet") { withHeader(show(), SurfacePage.SENDS) }

    /** The aimed cue's sends given places: the first stereo into stereo, swapped; the second a mono pan. */
    private fun Mirror.placed(): Mirror = apply {
        val sends = com.pob31.godot.remote.model.readSends(this, THUNDER_AND_RAIN)
        put(sends[0].address("placement"), "stereo")
        put(sends[0].address("balance"), -0.3)
        put(sends[0].address("width"), 0.6)
        put(sends[0].address("swap"), true)
        put(sends[1].address("placement"), "pan")
        put(sends[1].address("pan"), 0.45)
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun thePanPage() = shoot("surface-pan-tablet") { withHeader(show().placed(), SurfacePage.PAN) }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun thePanPageInFrench() = shoot("surface-pan-tablet-fr", "fr") { withHeader(show().placed(), SurfacePage.PAN) }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun thisTablet() = shoot("surface-this-tablet") { withHeader(show(), SurfacePage.FADERS, THIS_TABLET) }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theEqPageUnderTheLock() = shoot("surface-locked-tablet") { withHeader(show(locked = true), SurfacePage.EQ) }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theFadersUnderTheLock() = shoot("surface-locked-faders-tablet") { withHeader(show(locked = true), SurfacePage.FADERS) }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun nothingAimedAt() = shoot("surface-noaim-tablet") {
        withHeader(show().apply { put("/godot/surface/aim", "") }, SurfacePage.EQ)
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun stale() = shoot("surface-stale") { withHeader(show(), SurfacePage.FADERS, state = LinkState.STALE) }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun theFadersOnAPhone() = shoot("surface-phone") { withHeader(show(), SurfacePage.FADERS) }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun theEqPageOnAPhoneInFrench() = shoot("surface-eq-phone-fr", "fr") { tab(show(), SurfacePage.EQ) }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun thePadsOnAPhone() = shoot("surface-pads-phone") { tab(show(), SurfacePage.FADERS, PADS) }

    @Test
    @Config(qualifiers = "w866dp-h1385dp-port-xhdpi")
    fun theFadersOnTheTabletUpright() = shoot("surface-upright-tablet") { withHeader(show(), SurfacePage.FADERS) }

    private companion object {
        const val D700 = "SD700001"
        const val PADS = "SPAD0001"
    }
}
