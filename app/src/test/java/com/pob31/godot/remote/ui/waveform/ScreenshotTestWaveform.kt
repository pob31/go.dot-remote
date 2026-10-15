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

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.pob31.godot.remote.link.Identity
import com.pob31.godot.remote.link.Link
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.link.LinkedMachine
import com.pob31.godot.remote.link.Machine
import com.pob31.godot.remote.localization.LocalLanguage
import com.pob31.godot.remote.localization.LocalizationManager
import com.pob31.godot.remote.media.TimbreCache
import com.pob31.godot.remote.media.TimbreFetcher
import com.pob31.godot.remote.media.TimbreFrames
import com.pob31.godot.remote.media.TimbreInfo
import com.pob31.godot.remote.media.TimbreSource
import com.pob31.godot.remote.media.TimbreStrip
import com.pob31.godot.remote.media.TimbreTest
import com.pob31.godot.remote.osc.OscValue
import com.pob31.godot.remote.ui.cues.InspectorView
import com.pob31.godot.remote.ui.running.RunningView
import com.pob31.godot.remote.model.readInspection
import com.pob31.godot.remote.model.readRuns
import com.pob31.godot.remote.model.put
import com.pob31.godot.remote.model.ShowFixture
import com.pob31.godot.remote.model.RecordingWrites
import com.pob31.godot.remote.ui.FindMeAccess
import com.pob31.godot.remote.ui.MachinesModel
import com.pob31.godot.remote.ui.MachinesTab
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.nio.file.Files

/**
 * THE WAVEFORM STRIP AND THE SHOW FOLDERS, drawn on the JVM (`-Pscreenshots`), from a real
 * sweep's timbre as `wfg serve` answered it (fixtures/timbre-sweep.json).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTestWaveform {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val sweep = TimbreTest.sweep()
    private val root: File = Files.createTempDirectory("shows").toFile()
    private val links = mutableListOf<Link>()

    @After
    fun close() {
        links.forEach { it.close() }
        root.deleteRecursively()
    }

    /** Go.dot's timbre route for the sweep, and nothing else. */
    private val route = TimbreFetcher { _, path ->
        val prefix = "/media/${sweep.hash}/timbre?"
        when {
            path == prefix + "INFO" -> sweep.info.toByteArray()
            path.startsWith(prefix + "level=") -> sweep.levels.getOrNull(path.removePrefix(prefix + "level=").toInt())
            else -> null
        }
    }

    private fun shoot(name: String, language: String = "en", cache: TimbreCache? = null, before: () -> Unit = {}, dialog: Boolean = false, content: @Composable () -> Unit) {
        LocalizationManager.loadLanguage(RuntimeEnvironment.getApplication(), language, remember = false)
        compose.setContent {
            CompositionLocalProvider(LocalLanguage provides language, LocalTimbreCache provides cache) {
                GodotTheme { Box(Modifier.fillMaxSize().background(GodotColors.ground)) { content() } }
            }
        }
        compose.waitForIdle()
        before()
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        if (dialog) {
            // A dialog is a window of its own: drawn over the screen, dimmed as Android dims it.
            canvas.drawColor(0x99000000.toInt())
            val shown = ShadowDialog.getLatestDialog().window!!.decorView
            canvas.save()
            canvas.translate((view.width - shown.width) / 2f, (view.height - shown.height) / 2f)
            shown.draw(canvas)
            canvas.restore()
        }
        File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun stripAt(level: Int): TimbreStrip {
        val info = TimbreInfo.parse(sweep.info)!!
        return TimbreStrip(info, level, TimbreFrames.of(sweep.levels[level], info.levels[level].frames)!!)
    }

    private fun linkTo(show: String): Link {
        val link = Link("192.168.1.10", 5010, Identity("id", "tablet"), OkHttpClient()).also { links += it }
        link.mirror.putValues("/godot/document/name", "s", listOf(OscValue.Str(show)))
        return link
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theStrips() {
        val cache = TimbreCache(root, route)
        val link = linkTo("Hamlet")
        shoot("waveform-tablet", cache = cache, before = {
            // The fetching strip: the header, its level, kept under the show's folder - then drawn.
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Waveform").fetchSemanticsNodes().size >= 3 }
        }) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Fetched through Waveform(), a playhead at 37 %", color = GodotColors.inkDim)
                Waveform(sweep.hash, link, Modifier.fillMaxWidth().height(96.dp), playhead = 0.37f)
                Text("Drawn from the finest level, no playhead", color = GodotColors.inkDim)
                WaveformView(stripAt(0), Modifier.fillMaxWidth().height(48.dp))
                Text("A Gogo bar's width (the coarsest level that fits), ended", color = GodotColors.inkDim)
                WaveformView(stripAt(2), Modifier.width(160.dp).height(28.dp), playhead = 1f)
                Text("Not analysed yet: grey, and no shape", color = GodotColors.inkDim)
                WaveformView(null, Modifier.fillMaxWidth().height(48.dp), playhead = 0.1f)
            }
        }
        check(File(root, "Hamlet/timbre/${sweep.hash}.info").isFile)
    }

    // ------------------------------------------------------------------ the show folders

    private fun folders(): TimbreCache {
        val cache = TimbreCache(root, route)
        runBlocking {
            val source = TimbreSource("192.168.1.10", 5010)
            cache.strip(source, "Hamlet", sweep.hash, 40)
            cache.strip(source, "Hamlet", sweep.hash, 1200)
            cache.strip(source, "Le Malade imaginaire", sweep.hash, 40)
        }
        // A bigger show's folder, as a real one would weigh.
        File(root, "Rehearsal - week 2/timbre").apply { mkdirs() }.let { dir ->
            for (n in 0 until 24) File(dir, "%064x.L0".format(n)).writeBytes(ByteArray(48_000))
        }
        return cache
    }

    private fun machines(): MachinesModel {
        val main = Machine("Main FOH", "192.168.1.10", 5010, linked = true)
        val link = Link(main.host, main.port, Identity("id", "tablet"), OkHttpClient()).also { links += it }
        val field = Link::class.java.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(link) as MutableStateFlow<LinkState>).value = LinkState.LIVE
        val linked = LinkedMachine(main, link)
        return object : MachinesModel {
            override val machines = MutableStateFlow(listOf(main))
            override val links = MutableStateFlow(listOf(linked))
            override val onScreen = MutableStateFlow<LinkedMachine?>(linked)
            override val tabletName = MutableStateFlow("Lenovo Tab P12")
            override val installId = "9881b1b9-9666-4f71-848a-ba7925dede1b"
            override val foregroundProblem = MutableStateFlow<String?>(null)
            override fun addMachine(name: String, host: String, port: Int) = Unit
            override fun editMachine(oldKey: String, name: String, host: String, port: Int) = true
            override fun removeMachine(key: String) = Unit
            override fun setLinked(key: String, linked: Boolean) = Unit
            override fun putOnScreen(key: String) = Unit
            override fun setTabletName(name: String) = Unit
        }
    }

    private val findMe = object : FindMeAccess {
        override fun notificationsOn() = true
        override fun canShowOverLock() = true
        override fun openNotificationSettings() = Unit
        override fun openOverLockSettings() = Unit
        override fun test() = Unit
    }

    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun theShowFoldersOnTheTablet() {
        val cache = folders()
        val model = machines()
        shoot("machines-folders-tablet", cache = cache, before = {
            compose.waitUntil(10_000) { showing("Rehearsal - week 2") }
            compose.onNodeWithText("Rehearsal - week 2").performScrollTo()
        }) { MachinesTab(model, findMe) }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun theShowFoldersOnAPhoneInFrench() {
        val cache = folders()
        val model = machines()
        shoot("machines-folders-phone-fr", "fr", cache = cache, before = {
            compose.waitUntil(10_000) { showing("Rehearsal - week 2") }
            compose.onNodeWithText("Rehearsal - week 2").performScrollTo()
        }) { MachinesTab(model, findMe) }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun forgettingAShowAsksFirstThenForgets() {
        val cache = folders()
        val model = machines()
        shoot("machines-forget-phone", cache = cache, before = {
            compose.waitUntil(10_000) { showing("Hamlet") }
            compose.onNodeWithText("Hamlet").performScrollTo()
            compose.onAllNodesWithText("Forget")[0].performClick()
        }, dialog = true) { MachinesTab(model, findMe) }
        check(File(root, "Hamlet").isDirectory) // asked, not yet done
        compose.onNode(hasText("Forget") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10_000) { !File(root, "Hamlet").exists() && !showing("Hamlet") }
        check(File(root, "Le Malade imaginaire").isDirectory)
    }

    private fun showing(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    // ------------------------------------------------------------------ where the strips stand

    /** The fixture show with a sound playing 12.3 s into its 40 s file, the sweep its file. */
    private fun soundPlaying(link: Link) = ShowFixture.mirror().apply {
        put("/godot/audio/status", "running")
        put("/godot/run/${ShowFixture.RUN_THUNDER_AND_RAIN}/state", "playing")
        put("/godot/run/${ShowFixture.RUN_THUNDER_AND_RAIN}/position", 12.34)
        put("/godot/run/${ShowFixture.RUN_THUNDER_AND_RAIN}/seekable", true)
        put("/godot/run/${ShowFixture.RUN_THUNDER_AND_RAIN}/meter", -14.0)
        put("/godot/cue/${ShowFixture.THUNDER_AND_RAIN}/duration", 40.0)
        put("/godot/cue/${ShowFixture.THUNDER_AND_RAIN}/hash", sweep.hash)
        put("/godot/document/name", link.mirror.text("/godot/document/name") ?: "")
    }

    /** Under a sound's row on the Running tab, its head where it plays (position over the cue's duration). */
    @Test
    @Config(qualifiers = "w1385dp-h866dp-land-xhdpi")
    fun underARunningSound() {
        val cache = TimbreCache(root, route)
        val link = linkTo("Hamlet")
        val mirror = soundPlaying(link)
        shoot("running-waveform-tablet", cache = cache, before = {
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Waveform").fetchSemanticsNodes().isNotEmpty() }
        }) {
            RunningView(readRuns(mirror), LinkState.LIVE, RecordingWrites(), ShowFixture.tables().fader, link = link)
        }
    }

    /** In a sound's inspector, at the top, its head while it plays. */
    @Test
    @Config(qualifiers = "w411dp-h2400dp-port-xhdpi")
    fun inASoundsInspector() {
        val cache = TimbreCache(root, route)
        val link = linkTo("Hamlet")
        val mirror = soundPlaying(link)
        shoot("cues-inspector-waveform-phone", cache = cache, before = {
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Waveform").fetchSemanticsNodes().isNotEmpty() }
        }) {
            val tables = ShowFixture.tables()
            InspectorView(readInspection(mirror, ShowFixture.THUNDER_AND_RAIN, tables), RecordingWrites(), tables, Modifier.background(GodotColors.panelInspect), scroll = false, link = link)
        }
    }
}

