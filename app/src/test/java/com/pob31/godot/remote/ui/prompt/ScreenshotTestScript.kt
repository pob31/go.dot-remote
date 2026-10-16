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

package com.pob31.godot.remote.ui.prompt

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.pob31.godot.remote.link.LinkedMachine
import com.pob31.godot.remote.link.Machine
import com.pob31.godot.remote.localization.LocalLanguage
import com.pob31.godot.remote.localization.LocalizationManager
import com.pob31.godot.remote.prompt.PageState
import com.pob31.godot.remote.prompt.PromptPage
import com.pob31.godot.remote.prompt.PromptSettings
import com.pob31.godot.remote.ui.FindMeAccess
import com.pob31.godot.remote.ui.MachinesModel
import com.pob31.godot.remote.ui.MachinesTab
import com.pob31.godot.remote.ui.RemoteTab
import com.pob31.godot.remote.ui.Shell
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

/**
 * THE SCRIPT TAB AND ITS SECTION OF THE CONNECTIONS TAB, drawn on the JVM into
 * app/build/screenshots/. Robolectric's WebView draws nothing, so the page is a stand-in in
 * the loading, shown and failed shots; one shot holds the real WebView to show the tab makes
 * and attaches it without a crash.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTestScript {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val address = "http://192.168.1.40:8080/list/conduite-son"

    private fun shoot(name: String, language: String = "en", scrollTo: String? = null, content: @Composable () -> Unit) {
        LocalizationManager.loadLanguage(RuntimeEnvironment.getApplication(), language, remember = false)
        compose.setContent {
            CompositionLocalProvider(LocalLanguage provides language) {
                GodotTheme { Box(Modifier.fillMaxSize().background(GodotColors.ground)) { content() } }
            }
        }
        compose.waitForIdle()
        scrollTo?.let { compose.onNodeWithText(it).performScrollTo(); compose.waitForIdle() }
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** What a WebView would show: a stand-in, since Robolectric's draws nothing. */
    @Composable
    private fun standIn() = Box(Modifier.fillMaxSize()) {
        Text("(Choufleur's page)", color = GodotColors.inkOff, modifier = Modifier.align(Alignment.Center))
    }

    private val empty = object : MachinesModel {
        override val machines = MutableStateFlow(listOf(Machine("Main FOH", "192.168.1.10", 5010, linked = false)))
        override val links = MutableStateFlow(emptyList<LinkedMachine>())
        override val onScreen = MutableStateFlow<LinkedMachine?>(null)
        override val tabletName = MutableStateFlow("Lenovo Tab P12")
        override val installId = "9881b1b9-9666-4f71-848a-ba7925dede1b"
        override val foregroundProblem = MutableStateFlow<String?>(null)
        override fun addMachine(name: String, host: String, port: Int) = Unit
        override fun editMachine(oldKey: String, name: String, host: String, port: Int) = true
        override fun removeMachine(key: String) = Unit
        override fun setLinked(key: String, linked: Boolean) = Unit
        override fun putOnScreen(key: String) = Unit
        override fun setTabletName(name: String) = Unit
        override fun quit() = Unit
    }

    private val findMe = object : FindMeAccess {
        override fun notificationsOn() = true
        override fun canShowOverLock() = true
        override fun openNotificationSettings() = Unit
        override fun openOverLockSettings() = Unit
        override fun test() = Unit
    }

    private fun settings(saved: String?) = object : PromptSettings {
        override val address = MutableStateFlow(saved)
        override fun save(address: String) { this.address.value = address }
        override fun clear() { address.value = null }
    }

    private fun tab(state: PageState, language: String = "en", name: String) = shoot(name, language) {
        Shell(
            null, header = {}, machinesTab = {}, initialTab = RemoteTab.SCRIPT,
            scriptTab = { ScriptTabView(address, state, onReload = {}, onOpenInChrome = {}) { standIn() } },
        )
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theTabShown() = tab(PageState.Shown(address), name = "script-shown-tablet")

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theTabLoading() = tab(PageState.Loading(address), name = "script-loading-tablet")

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theTabFailed() = tab(PageState.Failed(address, "connect"), name = "script-failed-tablet")

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theTabFailedInFrench() = tab(PageState.Failed(address, "http", "404"), "fr", name = "script-failed-tablet-fr")

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun theTabOnAPhone() = tab(PageState.Failed(address, "hostLookup"), "fr", name = "script-failed-phone-fr")

    /** The real thing: the shared page's WebView, made and attached by the tab. */
    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theTabWithItsWebView() {
        val page = PromptPage.shared(RuntimeEnvironment.getApplication())
        shoot("script-webview-tablet") {
            Shell(null, header = {}, machinesTab = {}, initialTab = RemoteTab.SCRIPT, scriptTab = { ScriptTab(address, page) })
        }
        page.drop()
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theConnectionsSection() = shoot("script-connections-tablet", scrollTo = "Scan the code") {
        Shell(null, header = {}, machinesTab = { MachinesTab(empty, findMe, settings(address)) }, initialTab = RemoteTab.MACHINES, scriptTab = {})
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theConnectionsSectionWithANameAndInFrench() = shoot("script-connections-local-fr", "fr", scrollTo = "Scanner le code") {
        Shell(null, header = {}, machinesTab = { MachinesTab(empty, findMe, settings("http://choufleur.local:8080/list/lx")) }, initialTab = RemoteTab.MACHINES, scriptTab = {})
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun theConnectionsSectionEmptyOnAPhone() = shoot("script-connections-phone", scrollTo = "Scan the code") {
        Shell(null, header = {}, machinesTab = { MachinesTab(empty, findMe, settings(null)) }, initialTab = RemoteTab.MACHINES)
    }
}
