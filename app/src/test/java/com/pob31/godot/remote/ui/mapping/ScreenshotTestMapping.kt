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

package com.pob31.godot.remote.ui.mapping

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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.pob31.godot.remote.Fixtures
import com.pob31.godot.remote.link.Identity
import com.pob31.godot.remote.link.Link
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.link.LinkedMachine
import com.pob31.godot.remote.link.Machine
import com.pob31.godot.remote.localization.LocalLanguage
import com.pob31.godot.remote.localization.LocalizationManager
import com.pob31.godot.remote.model.CueLine
import com.pob31.godot.remote.model.HeaderReading
import com.pob31.godot.remote.model.SnapshotReader
import com.pob31.godot.remote.osc.OscValue
import com.pob31.godot.remote.ui.RemoteTab
import com.pob31.godot.remote.ui.Shell
import com.pob31.godot.remote.ui.TransportHeaderView
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotTheme
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.StringReader

/**
 * THE MAPPING TAB, DRAWN ON THE JVM as ui/ScreenshotTest draws the header: written as PNGs
 * under app/build/screenshots/ to be looked at. Named ScreenshotTest* so the plain `gradlew
 * test` leaves it out (app/build.gradle.kts); run with `-Pscreenshots`. The mirror is the
 * real tree of a `wfg serve` with two zones (fixtures/mapping-snapshot.json).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTestMapping {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val links = mutableListOf<Link>()

    @After
    fun close() = links.forEach { it.close() }

    private fun shoot(name: String, language: String, pick: MappingPick, before: () -> Unit = {}, content: @Composable () -> Unit) {
        LocalizationManager.loadLanguage(RuntimeEnvironment.getApplication(), language, remember = false)
        compose.setContent {
            CompositionLocalProvider(LocalLanguage provides language, LocalMappingPick provides pick) {
                GodotTheme { Box(Modifier.fillMaxSize().background(GodotColors.ground)) { content() } }
            }
        }
        compose.waitForIdle()
        before()
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun machine(locked: Boolean = false, state: LinkState = LinkState.LIVE, grid: Boolean? = null): LinkedMachine {
        val machine = Machine("Main FOH", "192.168.1.10", 5010, linked = true)
        val link = Link(machine.host, machine.port, Identity("id", "tablet"), OkHttpClient()).also { links += it }
        val result = SnapshotReader.read(StringReader(Fixtures.text("fixtures/mapping-snapshot.json")))
        link.mirror.install(result.state, result.tick)
        link.mirror.putValues("/godot/document/locked", "T", listOf(OscValue.of(locked)))
        // A Go.dot with the alignment grid has its row; the fixture's is older and has none.
        grid?.let { link.mirror.putValues("/godot/videoOutput/VD000021/grid", "T", listOf(OscValue.of(it))) }
        val field = Link::class.java.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(link) as MutableStateFlow<LinkState>).value = state
        return LinkedMachine(machine, link)
    }

    private val header = HeaderReading(
        listId = "VD000001",
        listName = "Pictures",
        current = CueLine("VD000002", "1", "Blue wash", "video", "", "", ""),
        currentOrigin = 'g',
        next = CueLine("VD000003", "2", "Clear", "memo", "", "", ""),
        locked = false,
        audioStatus = "running",
        tick = 1100,
    )

    @Composable
    private fun app(onScreen: LinkedMachine, locked: Boolean = false, state: LinkState = LinkState.LIVE) = Shell(
        onScreen,
        header = { TransportHeaderView("Main FOH", state, header.copy(locked = locked), null, send = { true }, nowMs = { 0L }) },
        machinesTab = {},
        initialTab = RemoteTab.MAPPING,
    )

    private val zones = MappingPick(output = "VD000021", layer = 1)
    private val ownPoint = MappingPick(output = "VD000021", layer = 0, point = 5)

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theZonesOnTheTablet() {
        val onScreen = machine()
        shoot("mapping-tablet", "en", zones) { app(onScreen) }
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theGridBesideIdentifyOnTheTablet() {
        val onScreen = machine(grid = true)
        shoot("mapping-grid-tablet", "en", zones) { app(onScreen) }
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theGridInFrench() {
        val onScreen = machine(grid = false)
        shoot("mapping-grid-tablet-fr", "fr", zones) { app(onScreen) }
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun aPointAndItsNudgePadOnTheTablet() {
        val onScreen = machine()
        shoot("mapping-point-tablet", "en", ownPoint) { app(onScreen) }
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun aPointOnTheTabletInFrench() {
        val onScreen = machine()
        shoot("mapping-point-tablet-fr", "fr", ownPoint) { app(onScreen) }
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun lockedOnTheTablet() {
        val onScreen = machine(locked = true)
        shoot("mapping-locked-tablet", "en", zones.copy(point = 2)) { app(onScreen, locked = true) }
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun staleOnTheTablet() {
        val onScreen = machine(state = LinkState.STALE)
        shoot("mapping-stale-tablet", "en", zones) { app(onScreen, state = LinkState.STALE) }
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun aZonesOpacityAndBlendOnTheTablet() {
        val onScreen = machine()
        shoot("mapping-zone-tablet", "en", zones, before = { compose.onNodeWithText("multiply").performScrollTo() }) { app(onScreen) }
    }

    @Test
    @Config(qualifiers = "w866dp-h1385dp-port-xhdpi")
    fun theZonesOnTheTabletUpright() {
        val onScreen = machine()
        shoot("mapping-upright-tablet", "en", zones.copy(point = 1)) { app(onScreen) }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun aPointOnAPhone() {
        val onScreen = machine()
        shoot("mapping-point-phone", "en", ownPoint) { app(onScreen) }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun theZonesOnAPhoneInFrench() {
        val onScreen = machine()
        shoot("mapping-phone-fr", "fr", zones) { app(onScreen) }
    }
}
