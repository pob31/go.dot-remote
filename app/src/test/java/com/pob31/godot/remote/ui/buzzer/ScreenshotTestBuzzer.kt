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

package com.pob31.godot.remote.ui.buzzer

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.pob31.godot.remote.buzzer.BuzzerAccess
import com.pob31.godot.remote.buzzer.BuzzerInfo
import com.pob31.godot.remote.buzzer.BuzzerPhase
import com.pob31.godot.remote.buzzer.BuzzerStatus
import com.pob31.godot.remote.buzzer.FoundBuzzer
import com.pob31.godot.remote.buzzer.RememberedBuzzer
import com.pob31.godot.remote.localization.LocalLanguage
import com.pob31.godot.remote.localization.LocalizationManager
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** THE BUZZER SECTION OF THE CONNECTIONS TAB, drawn on the JVM into app/build/screenshots/, at the width of its column. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1385dp-h866dp-land-xhdpi")
class ScreenshotTestBuzzer {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(name: String, language: String = "en", content: @Composable () -> Unit) {
        LocalizationManager.loadLanguage(RuntimeEnvironment.getApplication(), language, remember = false)
        compose.setContent {
            CompositionLocalProvider(LocalLanguage provides language) {
                GodotTheme {
                    Box(Modifier.fillMaxSize().background(GodotColors.ground).padding(16.dp)) {
                        Box(Modifier.width(800.dp)) { content() }
                    }
                }
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

    private fun access(status: BuzzerStatus, found: List<FoundBuzzer> = emptyList(), scanning: Boolean = false) = object : BuzzerAccess {
        override val status = MutableStateFlow(status)
        override val found = MutableStateFlow(found)
        override val scanning = MutableStateFlow(scanning)
        override fun missingPermissions() = emptyList<String>()
        override fun permissionsAnswered() = Unit
        override fun scan() = Unit
        override fun stopScan() = Unit
        override fun link(found: FoundBuzzer) = Unit
        override fun forget() = Unit
        override fun test() = Unit
        override fun identify() = Unit
    }

    private val wrist = RememberedBuzzer("D1:23:45:67:89:AB", "CHF-89AB")

    @Test
    fun linked() = shoot("buzzer-linked") {
        BuzzerSection(access(BuzzerStatus(wrist, BuzzerPhase.LINKED, battery = 82, info = BuzzerInfo(1, 0, 3, true, true))))
    }

    @Test
    fun linkedWithABrokenLeadInFrench() = shoot("buzzer-linked-fr", "fr") {
        BuzzerSection(access(BuzzerStatus(wrist, BuzzerPhase.LINKED, battery = 14, info = BuzzerInfo(1, 0, 3, true, false))))
    }

    @Test
    fun lostAndLookingForOthers() = shoot("buzzer-lost-scanning") {
        BuzzerSection(
            access(
                BuzzerStatus(wrist, BuzzerPhase.LOST),
                found = listOf(FoundBuzzer("D1:23:45:67:89:AB", "CHF-89AB", -48), FoundBuzzer("E4:11:02:9C:3F:10", "CHF-3F10", -77)),
                scanning = true,
            )
        )
    }

    @Test
    fun noneAndNoPermission() = shoot("buzzer-none-fr", "fr") {
        BuzzerSection(access(BuzzerStatus(null, BuzzerPhase.NONE), scanning = true))
    }

    @Test
    fun bluetoothOff() = shoot("buzzer-bluetooth-off") {
        BuzzerSection(access(BuzzerStatus(wrist, BuzzerPhase.BLUETOOTH_OFF)))
    }
}
