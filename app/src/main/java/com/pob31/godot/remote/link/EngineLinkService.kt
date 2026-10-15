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

package com.pob31.godot.remote.link

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.pob31.godot.remote.MainActivity
import com.pob31.godot.remote.R
import com.pob31.godot.remote.localization.locStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** A remembered machine and its link. */
data class LinkedMachine(val machine: Machine, val link: Link)

/**
 * THE LINK SERVICE: a foreground service that holds one [Link] per remembered machine for
 * as long as the app is linked, whether its screen is in front or swiped away, so coming
 * back is a redraw and never a reconnect (PRD §3.17).
 *
 * TYPE connectedDevice, not WFS-DIY's dataSync: Android 15 caps a dataSync service at six
 * hours in twenty-four, which is shorter than a long day of rehearsal. connectedDevice asks
 * for one of its prerequisites; CHANGE_WIFI_STATE (a normal permission, granted at install)
 * is declared for it.
 *
 * While any link is wanted it holds a Wi-Fi lock in low-latency mode, so the radio does
 * not doze between bundles. Bound by the activity through [LinkBinder]; the UI reads
 * StateFlows.
 */
class EngineLinkService : LifecycleService() {

    inner class LinkBinder : Binder() {
        val service: EngineLinkService get() = this@EngineLinkService
    }

    private val binder = LinkBinder()
    private lateinit var store: MachineStore
    private val timing = LinkTiming()
    private val http by lazy { Link.httpClient(timing) }
    private var wifiLock: WifiManager.WifiLock? = null

    private val _links = MutableStateFlow<List<LinkedMachine>>(emptyList())
    val links: StateFlow<List<LinkedMachine>> = _links.asStateFlow()

    private val _onScreen = MutableStateFlow<LinkedMachine?>(null)

    /** The machine whose words, cues and runs the app shows (remote protocol §8). */
    val onScreen: StateFlow<LinkedMachine?> = _onScreen.asStateFlow()

    private val _foregroundProblem = MutableStateFlow<String?>(null)

    /** Why the system refused to keep the service in the foreground, if it did. */
    val foregroundProblem: StateFlow<String?> = _foregroundProblem.asStateFlow()

    val identity: Identity get() = Identity(store.installId, store.tabletName)

    val tabletName: String get() = store.tabletName

    val machines: List<Machine> get() = store.machines

    override fun onCreate() {
        super.onCreate()
        store = MachineStore(this)
        createChannel()

        // The machines left linked are linked again.
        val restored = store.machines.filter { it.linked }.map { LinkedMachine(it, newLink(it)) }
        _links.value = restored
        _onScreen.value = restored.firstOrNull { it.machine.key == store.onScreen } ?: restored.firstOrNull()
        restored.forEach { it.link.start() }

        watchLinks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        goForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        _links.value.forEach { it.link.close() }
        releaseWifiLock()
        super.onDestroy()
    }

    private fun newLink(machine: Machine) = Link(machine.host, machine.port, identity, http, timing)

    // ---------------------------------------------------------------- what the screen asks

    /**
     * Link to [host]:[port], remembered under [name], and put it on screen. A machine
     * already linked at that address keeps its link (and its mirror); a new name is kept.
     */
    fun connect(name: String, host: String, port: Int) {
        val machine = Machine(name.trim(), host.trim(), port, linked = true)
        val existing = _links.value.firstOrNull { it.machine.key == machine.key }
        val linked = if (existing != null) {
            existing.copy(machine = machine).also { updated ->
                _links.value = _links.value.map { if (it.machine.key == machine.key) updated else it }
            }
        } else {
            LinkedMachine(machine, newLink(machine)).also { _links.value = _links.value + it }
        }
        linked.link.start()
        _onScreen.value = linked
        remember(machine)
        store.onScreen = machine.key
    }

    /** Unlink [key]'s machine; it stays remembered, unlinked. */
    fun disconnect(key: String) {
        val linked = _links.value.firstOrNull { it.machine.key == key } ?: return
        linked.link.close()
        _links.value = _links.value - linked
        if (_onScreen.value?.machine?.key == key) _onScreen.value = _links.value.firstOrNull()
        remember(linked.machine.copy(linked = false))
    }

    fun putOnScreen(key: String) {
        _links.value.firstOrNull { it.machine.key == key }?.let {
            _onScreen.value = it
            store.onScreen = key
        }
    }

    private fun remember(machine: Machine) {
        val others = store.machines.filter { it.key != machine.key }
        store.machines = listOf(machine) + others
    }

    // ---------------------------------------------------------------- foreground

    private fun goForeground() {
        val notification = notification(_onScreen.value)
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            _foregroundProblem.value = null
        } catch (e: Exception) {
            // Android 12+ refuses a start from the background (a sticky restart may be one);
            // Android 14+ refuses a type whose prerequisite permission is missing.
            Log.w(TAG, "the system refused the foreground service", e)
            _foregroundProblem.value = e.toString()
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            locStatic("remote.notification.channelName"),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = locStatic("remote.notification.channelDescription")
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(onScreen: LinkedMachine?, state: LinkState = LinkState.OFF): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (onScreen != null) locStatic("remote.notification.linkedTo", "machine" to onScreen.machine.label)
                    else locStatic("remote.notification.notLinked")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle(title)
            .setContentText(if (onScreen != null) linkWord(state) else null)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun watchLinks() {
        // The notification says the machine on screen and how its link stands.
        lifecycleScope.launch {
            _onScreen.flatMapLatest { linked ->
                linked?.link?.state?.map { linked to it } ?: flowOf(null to LinkState.OFF)
            }.distinctUntilChanged().collect { (linked, state) ->
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(linked, state))
            }
        }
        // The Wi-Fi lock is held while any link is wanted.
        lifecycleScope.launch {
            _links.flatMapLatest { list ->
                if (list.isEmpty()) flowOf(false)
                else combine(list.map { it.link.state }) { states -> states.any { it != LinkState.OFF } }
            }.distinctUntilChanged().collect { anyUp ->
                if (anyUp) acquireWifiLock() else releaseWifiLock()
            }
        }
    }

    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                   else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifiLock = wifi.createWifiLock(mode, "godot-remote:link").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWifiLock() {
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    companion object {
        private const val TAG = "EngineLinkService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "link"

        fun linkWord(state: LinkState): String = locStatic(
            when (state) {
                LinkState.OFF -> "remote.link.off"
                LinkState.CONNECTING -> "remote.link.connecting"
                LinkState.LIVE -> "remote.link.live"
                LinkState.STALE -> "remote.link.stale"
                LinkState.CLOCK_STOPPED -> "remote.link.clockStopped"
                LinkState.GONE -> "remote.link.gone"
            }
        )
    }
}
