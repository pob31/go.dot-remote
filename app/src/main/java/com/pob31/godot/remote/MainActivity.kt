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

package com.pob31.godot.remote

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.pob31.godot.remote.findme.AppVisibility
import com.pob31.godot.remote.link.EngineLinkService
import com.pob31.godot.remote.link.Quit
import com.pob31.godot.remote.localization.LocalLanguage
import com.pob31.godot.remote.localization.LocalizationManager
import com.pob31.godot.remote.prompt.PromptPage
import com.pob31.godot.remote.ui.RemoteApp
import com.pob31.godot.remote.ui.theme.GodotTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The one activity. It starts the link service (which outlives it) and binds to it while
 * it is in front; swiped away and back, it finds the links where they were. The screen
 * stays on while it is in front: a tablet that dims mid-cue is a tablet nobody trusts.
 *
 * THE WHOLE SCREEN IS THE APP'S (the author, 2026-10-16: "the Android top status bar can be
 * reclaimed too"): drawn edge to edge, the status bar and the navigation bar hidden - a
 * swipe from the edge shows them for a moment - and hidden again whenever the app comes
 * back or gets the focus back. The header starts at the top, clear of a camera cutout.
 *
 * Quit ([Quit]) finishes it and takes its task out of the recent apps.
 */
class MainActivity : ComponentActivity() {

    private val service = MutableStateFlow<EngineLinkService?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service.value = (binder as? EngineLinkService.LinkBinder)?.service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service.value = null
        }
    }

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Granted or not, the link runs: without the permission its notification is not shown.
        startLinkService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        lifecycleScope.launch {
            Quit.asked.collect {
                // Choufleur's page and its connection go with the remote.
                PromptPage.sharedOrNull()?.drop()
                finishAndRemoveTask()
            }
        }
        setContent {
            val language by LocalizationManager.currentLanguage.collectAsState()
            val bound by service.collectAsState()
            CompositionLocalProvider(LocalLanguage provides language) {
                GodotTheme {
                    RemoteApp(bound)
                }
            }
        }

        // POST_NOTIFICATIONS is asked for at the first launch (Android 13+); the service starts after.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startLinkService()
        }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Back from a dialog, the notification shade or the keyboard: hidden again.
        if (hasFocus) hideSystemBars()
    }

    private fun startLinkService() {
        ContextCompat.startForegroundService(this, Intent(this, EngineLinkService::class.java))
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.inFront = true
        bindService(Intent(this, EngineLinkService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        AppVisibility.inFront = false
        unbindService(connection)
        service.value = null
        super.onStop()
    }
}
