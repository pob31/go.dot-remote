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

package com.pob31.godot.remote.findme

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pob31.godot.remote.localization.LocalLanguage
import com.pob31.godot.remote.localization.LocalizationManager
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.ui.theme.GodotColors
import kotlinx.coroutines.delay

/**
 * THE TABLET SHOWING ITSELF: the whole screen flashing Go.dot's GO yellow and its ground,
 * "Here" and the machine that asked in large letters, the tablet's alarm sound looping on
 * the alarm stream (at the alarm's volume, whatever the media volume), and a vibration -
 * until a touch anywhere, Back, or the time asked for runs out. Over the lock screen and
 * turning the screen on (WFS-DIY's FlashScreenActivity, carried over).
 *
 * THE FLASH CHANGES COLOUR EVERY 250 MS - two yellow flashes a second, under the three a
 * second at which a full-screen flash becomes a risk to somebody photosensitive - and the
 * camera's light, where the tablet has one, flashes with it ([Torch]). The alarm stream is
 * turned up for the alarm when it was at nothing, and put back after ([AlarmVolume]).
 * Touch, Back, the time up or the screen destroyed: all of it stops, the light left off.
 */
class FindMeActivity : ComponentActivity() {

    private var sound: AlarmSound? = null
    private var torch: Torch? = null
    private var vibrator: Vibrator? = null
    private var ended = false

    private val machine = mutableStateOf("")
    private val until = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverTheLock()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideBars()
        take(intent)

        // The notification brought us here, or was beaten to it: its own sound stops now.
        FindMe.cancelNotification(this)
        startAlarm()

        setContent {
            val language by LocalizationManager.currentLanguage.collectAsState()
            CompositionLocalProvider(LocalLanguage provides language) {
                FindMeScreen(machine.value, until.longValue, onDone = { end() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Asked again while showing: the later end wins, the name of the latest asker.
        take(intent)
        FindMe.cancelNotification(this)
    }

    private fun take(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(FindMe.EXTRA_MACHINE)?.let { machine.value = it }
        val asked = intent.getLongExtra(FindMe.EXTRA_UNTIL, SystemClock.elapsedRealtime() + 30_000L)
        until.longValue = maxOf(until.longValue, asked)
    }

    private fun showOverTheLock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun hideBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun startAlarm() {
        AlarmVolume.raise(this)
        sound = AlarmSound(this).also { it.start() }
        torch = Torch(this).also { it.set(true) }
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val effect = VibrationEffect.createWaveform(FindMe.VIBRATION, 0)
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator?.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(effect, FindMe.alarmAttributes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "no vibration", e)
        }
    }

    /** The camera's light in step with the screen; nothing once the alarm has ended. */
    private fun flash(lit: Boolean) {
        if (!ended) torch?.set(lit)
    }

    /** Everything stopped - the sound, the light, the vibration - and the volume put back. Safe twice. */
    private fun stopAlarm() {
        sound?.stop()
        sound = null
        torch?.off()
        torch = null
        try { vibrator?.cancel() } catch (e: Exception) { /* already still */ }
        vibrator = null
        AlarmVolume.restore(this)
    }

    /** Touch, Back or the time up: stopped at once, before the screen goes. */
    private fun end() {
        ended = true
        stopAlarm()
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            end()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        ended = true
        stopAlarm()
        super.onDestroy()
    }

    @Composable
    private fun FindMeScreen(machine: String, until: Long, onDone: () -> Unit) {
        var lit by remember { mutableStateOf(true) }
        LaunchedEffect(until) {
            while (SystemClock.elapsedRealtime() < until) {
                delay(FindMePlan.FLASH_MS)
                lit = !lit
                flash(lit)
            }
            onDone()
        }
        val ground = if (lit) GodotColors.go else GodotColors.ground
        val ink = if (lit) GodotColors.goInk else GodotColors.go
        Column(
            Modifier
                .fillMaxSize()
                .background(ground)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() }
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(loc("remote.find.here"), color = ink, fontSize = 120.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            if (machine.isNotEmpty()) {
                Text(machine, color = ink, fontSize = 48.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            Text(
                loc("remote.find.touchToStop"), color = ink, fontSize = 22.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp)
            )
        }
    }

    companion object {
        private const val TAG = "FindMeActivity"
    }
}
