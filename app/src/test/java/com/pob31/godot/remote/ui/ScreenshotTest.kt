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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.pob31.godot.remote.Fixtures
import com.pob31.godot.remote.findme.FindMe
import com.pob31.godot.remote.findme.FindMeActivity
import com.pob31.godot.remote.link.Identity
import com.pob31.godot.remote.link.Link
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.link.LinkedMachine
import com.pob31.godot.remote.link.Machine
import com.pob31.godot.remote.link.Refusal
import com.pob31.godot.remote.localization.LocalLanguage
import com.pob31.godot.remote.localization.LocalizationManager
import com.pob31.godot.remote.model.CueLine
import com.pob31.godot.remote.model.HeaderReading
import com.pob31.godot.remote.model.Mirror
import com.pob31.godot.remote.model.RunLine
import com.pob31.godot.remote.model.Said
import com.pob31.godot.remote.model.SnapshotReader
import com.pob31.godot.remote.osc.OscValue
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotTheme
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.StringReader

/**
 * THE HEADER AND THE MACHINES TAB, DRAWN ON THE JVM (Robolectric's native graphics) and
 * written as PNGs under app/build/screenshots/ - to be looked at, not compared: the
 * tablet is the judge. Fixture readings, no engine.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34]) // Robolectric's 35 and 36 want Java 21; the build's toolchain is 17
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val links = mutableListOf<Link>()

    @After
    fun close() = links.forEach { it.close() }

    private fun shoot(name: String, language: String = "en", content: @Composable () -> Unit) {
        LocalizationManager.loadLanguage(RuntimeEnvironment.getApplication(), language, remember = false)
        compose.setContent {
            CompositionLocalProvider(LocalLanguage provides language) {
                GodotTheme { Box(Modifier.fillMaxSize().background(GodotColors.ground)) { content() } }
            }
        }
        compose.waitForIdle()
        // Drawn by the view itself into a bitmap: Compose's window capture waits for a frame
        // Robolectric never presents.
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val playing = HeaderReading(
        listId = Fixtures.LIST,
        listName = "Main",
        current = CueLine("K2M4P6R8", "3", "Fade Announce", "fade", "", "", ""),
        currentOrigin = 'g',
        currentRun = RunLine("RUNAAAAA", "playing", 12.34, 0.0),
        next = CueLine("F7HR8TVD", "4", "Thunder and rain", "media", "", "", "Wait for the actor to reach the door"),
        locked = true,
        audioStatus = "running",
        tick = 1100,
        doh = "${Fixtures.LIST} K2M4P6R8 1000",
        dohCue = "3",
        dohWindow = "10",
    )

    private val refused = Refusal.parse("1090 7 ws:192.168.1.20:5000 too-soon go")

    @Composable
    private fun header(reading: HeaderReading = playing, state: LinkState = LinkState.LIVE, refusal: Refusal? = refused, seat: Said? = null) =
        TransportHeaderView("Main FOH", state, reading, refusal, send = { true }, nowMs = { 0L }, seat = seat)

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun headerOnTheTablet() = shoot("header-tablet") { header() }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun headerOnTheTabletInFrench() = shoot("header-tablet-fr", "fr") { header() }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun headerStaleWithNoAudioAndDohFading() = shoot("header-stale") {
        header(playing.copy(audioStatus = "stopped", tick = 1460, locked = false), LinkState.STALE, null)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun headerOnAPhone() = shoot("header-phone") { header() }

    /** The machine's words between the cues and Doh!: its seat in a pair, the lost link, Locked and a refusal at once. */
    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun headerWithEveryWordOnTheTablet() = shoot("header-words-tablet") {
        header(state = LinkState.GONE, seat = Said("remote.pair.seat.mainSending"))
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun headerWithEveryWordInFrench() = shoot("header-words-tablet-fr", "fr") {
        header(state = LinkState.GONE, seat = Said("remote.pair.seat.backupFollowing"))
    }

    private fun linkWith(machine: Machine, role: String, state: String, linked: Boolean): LinkedMachine {
        val link = Link(machine.host, machine.port, Identity("id", "tablet"), OkHttpClient()).also { links += it }
        val result = SnapshotReader.read(StringReader(Fixtures.snapshotSmall()))
        val mirror: Mirror = link.mirror
        mirror.install(result.state, result.tick)
        mirror.putValues("/godot/pair/role", "s", listOf(OscValue.Str(role)))
        mirror.putValues("/godot/pair/state", "s", listOf(OscValue.Str(state)))
        mirror.putValues("/godot/pair/linked", "T", listOf(OscValue.of(linked)))
        mirror.putValues("/godot/pair/alarm", "s", listOf(OscValue.Str("")))
        // The link is never started here: say it is live, as a link to a real machine would.
        val field = Link::class.java.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val state = field.get(link) as MutableStateFlow<LinkState>
        state.value = LinkState.LIVE
        return LinkedMachine(machine, link)
    }

    private fun machines(): MachinesModel {
        val main = Machine("Main FOH", "192.168.1.10", 5010, linked = true)
        val backup = Machine("Backup FOH", "192.168.1.11", 5010, linked = true)
        val spare = Machine("Rehearsal room", "10.0.0.7", 5010, linked = false)
        val linked = listOf(linkWith(main, "main", "leading", true), linkWith(backup, "backup", "following", true))
        return object : MachinesModel {
            override val machines = MutableStateFlow(listOf(main, backup, spare))
            override val links = MutableStateFlow(linked)
            override val onScreen = MutableStateFlow<LinkedMachine?>(linked[0])
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
    }

    private val findMe = object : FindMeAccess {
        override fun notificationsOn() = true
        override fun canShowOverLock() = false
        override fun openNotificationSettings() = Unit
        override fun openOverLockSettings() = Unit
        override fun test() = Unit
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun machinesTabOnTheTablet() {
        val model = machines()
        shoot("machines-tablet") {
            Shell(model.onScreen.value, header = { header() }, machinesTab = { MachinesTab(model, findMe) }, initialTab = RemoteTab.MACHINES)
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun machinesTabOnAPhone() {
        val model = machines()
        shoot("machines-phone", "fr") {
            Shell(model.onScreen.value, header = { header() }, machinesTab = { MachinesTab(model, findMe) }, initialTab = RemoteTab.MACHINES)
        }
    }

    /** The find-me screen opens, sounds and draws without a crash - it is never tried on the tablet at night. */
    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theFindMeScreen() {
        LocalizationManager.loadLanguage(RuntimeEnvironment.getApplication(), "fr", remember = false)
        val intent = android.content.Intent(RuntimeEnvironment.getApplication(), FindMeActivity::class.java)
            .putExtra(FindMe.EXTRA_MACHINE, "Main FOH")
            .putExtra(FindMe.EXTRA_UNTIL, android.os.SystemClock.elapsedRealtime() + 3000L)
        val controller = Robolectric.buildActivity(FindMeActivity::class.java, intent).setup()
        val view = controller.get().window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/screenshots/find-me-fr.png").apply { parentFile?.mkdirs() }.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        controller.pause().stop().destroy()
    }

    /** Upright, the narrower row: the next cue's number and kind on the arrows' line, its name on a line of its own. */
    @Test
    @Config(qualifiers = "w866dp-h1385dp-port-xhdpi")
    fun headerOnTheTabletUpright() = shoot("header-upright-tablet") {
        header(playing.copy(next = playing.next!!.copy(number = "2", name = "Thunder and rain over the harbour, then the bell", kind = "memo")))
    }

    /** A phone with a long name: never dropped, never under the number - two lines, then an ellipsis. */
    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun headerOnAPhoneWithALongName() = shoot("header-phone-long-fr", "fr") {
        header(playing.copy(next = playing.next!!.copy(number = "12.5", name = "Orage sur le port, puis la cloche de l'église au loin")))
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xhdpi")
    fun headerOnAPhoneOnItsSide() = shoot("header-phone-land") { header() }
}
