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

package com.pob31.godot.remote.ui.cart

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
import com.pob31.godot.remote.model.ShowFixture.FOYER
import com.pob31.godot.remote.model.ShowFixture.RUN_THUNDER
import com.pob31.godot.remote.model.ShowFixture.STORM
import com.pob31.godot.remote.model.put
import com.pob31.godot.remote.model.readCarts
import com.pob31.godot.remote.model.readHeader
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
 * THE CART TAB, DRAWN ON THE JVM into app/build/screenshots/ (Go.dot's namespace draft §74):
 * the fixture show's foyer list made a cart - a memo of its own and the storm made a bank,
 * one of its members going - on the tablet, in French, and under the lock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTestCart {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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
        put("/godot/list/$FOYER/kind", "cart")
        put("/godot/list/$FOYER/name", "Pads")
        put("/godot/list/$FOYER/order", "CE000001 $STORM")
        put("/godot/cue/$STORM/mode", "sampler")
        put("/godot/run/$RUN_THUNDER/state", "playing")
        put("/godot/document/locked", locked)
    }

    @Composable
    private fun withHeader(mirror: Mirror) {
        Shell(
            null,
            header = { TransportHeaderView("Main FOH", LinkState.LIVE, readHeader(mirror), null, send = { true }, nowMs = { 0L }) },
            machinesTab = {},
            initialTab = RemoteTab.CART,
            cartTab = { CartView(readCarts(mirror), LinkState.LIVE, RecordingWrites()) },
        )
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theCartOnTheTablet() = shoot("cart-tablet") { withHeader(show()) }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theCartOnTheTabletInFrench() = shoot("cart-tablet-fr", "fr") { withHeader(show()) }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theCartUnderTheLock() = shoot("cart-locked-tablet") { withHeader(show(locked = true)) }
}
