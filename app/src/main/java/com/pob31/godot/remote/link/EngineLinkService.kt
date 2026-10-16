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
import com.pob31.godot.remote.buzzer.BuzzerLink
import com.pob31.godot.remote.MainActivity
import com.pob31.godot.remote.R
import com.pob31.godot.remote.findme.FindMe
import com.pob31.godot.remote.localization.locStatic
import com.pob31.godot.remote.model.readPair
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** A remembered machine and its link. */
data class LinkedMachine(val machine: Machine, val link: Link)

/**
 * THE LINK SERVICE: a foreground service that holds one [Link] per linked machine for
 * as long as the app is linked, whether its screen is in front or swiped away, so coming
 * back is a redraw and never a reconnect (PRD §3.17).
 *
 * TYPE connectedDevice, not WFS-DIY's dataSync: Android 15 caps a dataSync service at six
 * hours in twenty-four, which is shorter than a long day of rehearsal. connectedDevice asks
 * for one of its prerequisites; CHANGE_WIFI_STATE (a normal permission, granted at install)
 * is declared for it.
 *
 * SEVERAL MACHINES AT ONCE (§8): a main and its backup may both be linked, each its own
 * link and mirror; ONE is on screen, and every tab and the transport header read its
 * mirror. The one the operator put there stays while it is linked; otherwise
 * [chooseOnScreen] picks the one sending to the devices.
 *
 * While any link is wanted it holds a Wi-Fi lock in low-latency mode, so the radio does
 * not doze between bundles. Bound by the activity through [LinkBinder]; the UI reads
 * StateFlows. A FIND from any linked machine sounds the find-me alarm ([FindMe]).
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
    private val watchers = ArrayList<Job>()

    /** Quit (the Connections tab's button, the notification's action): see [Quit]. */
    private val quitter = Quitter(object : QuitSteps {
        override fun stopWatching() {
            watchers.forEach { it.cancel() }
            watchers.clear()
        }

        override fun closeLinks() {
            closeAllLinks(_links, _onScreen)
            // The wrist buzzer with them: still remembered, linked again at the next launch.
            BuzzerLink.shared(this@EngineLinkService).close()
        }

        override fun releaseWifiLock() = this@EngineLinkService.releaseWifiLock()

        override fun removeNotification() {
            ServiceCompat.stopForeground(this@EngineLinkService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }

        override fun stopService() = stopSelf()

        override fun closeScreens() = Quit.closeScreens()
    })

    private val _machines = MutableStateFlow<List<Machine>>(emptyList())

    /** Every remembered machine, linked or not, in the operator's order. */
    val machines: StateFlow<List<Machine>> = _machines.asStateFlow()

    private val _links = MutableStateFlow<List<LinkedMachine>>(emptyList())

    /** The linked machines and their links. */
    val links: StateFlow<List<LinkedMachine>> = _links.asStateFlow()

    private val _onScreen = MutableStateFlow<LinkedMachine?>(null)

    /** The machine whose words, cues and runs the app shows (remote protocol §8). */
    val onScreen: StateFlow<LinkedMachine?> = _onScreen.asStateFlow()

    private val _foregroundProblem = MutableStateFlow<String?>(null)

    /** Why the system refused to keep the service in the foreground, if it did. */
    val foregroundProblem: StateFlow<String?> = _foregroundProblem.asStateFlow()

    private val _tabletName = MutableStateFlow("")

    /** What the operator calls this tablet: said in HELLO. */
    val tabletName: StateFlow<String> = _tabletName.asStateFlow()

    val identity: Identity get() = Identity(store.installId, store.tabletName)

    /** Drawn once at install: how Go.dot tells this tablet from another (HELLO, §5). */
    val installId: String get() = store.installId

    override fun onCreate() {
        super.onCreate()
        store = MachineStore(this)
        createChannel()
        FindMe.createChannel(this)
        _tabletName.value = store.tabletName
        _machines.value = store.machines
        startLinking()
    }

    /** The machines left linked are linked again, and the links watched. */
    private fun startLinking() {
        val restored = store.machines.filter { it.linked }.map { LinkedMachine(it, newLink(it)) }
        _links.value = restored
        restored.forEach { it.link.start() }
        // Choufleur's wrist buzzer lives as the links do: linked while the remote runs, whatever the tab.
        BuzzerLink.shared(this).start()
        reconsiderOnScreen()
        watchLinks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == Quit.ACTION) {
            quit()
            return START_NOT_STICKY
        }
        // Launched again while a quit service was still bound by a screen on its way out.
        if (quitter.revive()) startLinking()
        goForeground()
        return START_STICKY
    }

    /**
     * QUIT: every machine unlinked - each keeping its Linked switch, so the next launch links
     * it again - the Wi-Fi lock let go, the notification gone, the service stopped, and the
     * open screen finished with its task.
     */
    fun quit() {
        quitter.quit()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        _links.value.forEach { it.link.close() }
        BuzzerLink.shared(this).close()
        releaseWifiLock()
        super.onDestroy()
    }

    private fun newLink(machine: Machine) = Link(machine.host, machine.port, identity, http, timing)

    // ---------------------------------------------------------------- what the screen asks

    /**
     * A machine remembered and linked: a new one at the end of the list; one already there
     * (by address and port) renamed and linked, keeping its link and its mirror.
     */
    fun addMachine(name: String, host: String, port: Int) {
        val machine = Machine(name.trim(), host.trim(), port, linked = true)
        val list = MachineList.put(_machines.value, machine) ?: return
        remember(list)
        val existing = _links.value.firstOrNull { it.machine.key == machine.key }
        if (existing != null) {
            replaceLinked(existing.copy(machine = machine))
        } else {
            val linked = LinkedMachine(machine, newLink(machine))
            _links.value = _links.value + linked
            linked.link.start()
        }
        reconsiderOnScreen()
    }

    /**
     * The machine at [oldKey] edited. The same address keeps its link (a new name only); a
     * new one is a new link, on screen if the old one was. False when the new address is
     * another remembered machine's.
     */
    fun editMachine(oldKey: String, name: String, host: String, port: Int): Boolean {
        val old = _machines.value.firstOrNull { it.key == oldKey } ?: return false
        val machine = old.copy(name = name.trim(), host = host.trim(), port = port)
        val list = MachineList.put(_machines.value, machine, replacing = oldKey) ?: return false
        remember(list)
        val linked = _links.value.firstOrNull { it.machine.key == oldKey }
        if (linked != null) {
            if (machine.key == oldKey) {
                replaceLinked(linked.copy(machine = machine))
            } else {
                linked.link.close()
                val fresh = LinkedMachine(machine, newLink(machine))
                _links.value = _links.value.map { if (it === linked) fresh else it }
                if (store.onScreen == oldKey) store.onScreen = machine.key
                if (_onScreen.value === linked) _onScreen.value = fresh
                fresh.link.start()
            }
        }
        reconsiderOnScreen()
        return true
    }

    /** Forget [key]'s machine: unlinked, and gone from the list. */
    fun removeMachine(key: String) {
        unlink(key)
        remember(MachineList.remove(_machines.value, key))
        if (store.onScreen == key) store.onScreen = null
        reconsiderOnScreen()
    }

    /** Link [key]'s machine, or unlink it; it stays remembered either way. */
    fun setLinked(key: String, linked: Boolean) {
        val machine = _machines.value.firstOrNull { it.key == key } ?: return
        remember(MachineList.setLinked(_machines.value, key, linked))
        if (linked) {
            if (_links.value.none { it.machine.key == key }) {
                val fresh = LinkedMachine(machine.copy(linked = true), newLink(machine))
                _links.value = _links.value + fresh
                fresh.link.start()
            }
        } else {
            unlink(key)
        }
        reconsiderOnScreen()
    }

    /** One tap: [key]'s machine on screen, and kept there while it is linked. */
    fun putOnScreen(key: String) {
        _links.value.firstOrNull { it.machine.key == key }?.let {
            _onScreen.value = it
            store.onScreen = key
        }
    }

    /** The tablet renamed: every link connects again, so its HELLO says the new name. */
    fun setTabletName(name: String) {
        val cleaned = name.trim().replace('\t', ' ').replace('\n', ' ')
        if (cleaned.isEmpty() || cleaned == store.tabletName) return
        store.tabletName = cleaned
        _tabletName.value = cleaned
        val now = identity
        _links.value.forEach { it.link.reintroduce(now) }
    }

    private fun unlink(key: String) {
        val linked = _links.value.firstOrNull { it.machine.key == key } ?: return
        linked.link.close()
        _links.value = _links.value - linked
        if (_onScreen.value === linked) _onScreen.value = null
    }

    private fun replaceLinked(updated: LinkedMachine) {
        _links.value = _links.value.map { if (it.link === updated.link) updated else it }
        if (_onScreen.value?.link === updated.link) _onScreen.value = updated
    }

    private fun remember(list: List<Machine>) {
        _machines.value = list
        store.machines = list
    }

    /** Whether a linked machine sends to the devices, from its own mirror; null while it has not said. */
    private fun sendsOf(key: String): Boolean? {
        val link = _links.value.firstOrNull { it.machine.key == key }?.link ?: return null
        if (link.mirror.tick.value < 0 || link.mirror["/godot/pair/role"] == null) return null
        return readPair(link.mirror).sends
    }

    /** The machine on screen chosen again ([chooseOnScreen]): whenever the links change or one comes live. */
    private fun reconsiderOnScreen() {
        val linked = _links.value
        val key = chooseOnScreen(
            chosen = store.onScreen,
            current = _onScreen.value?.machine?.key,
            linked = linked.map { it.machine.key },
            sends = ::sendsOf,
        )
        val chosen = linked.firstOrNull { it.machine.key == key }
        if (_onScreen.value !== chosen) _onScreen.value = chosen
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
        val quit = PendingIntent.getService(
            this, 1,
            Intent(this, EngineLinkService::class.java).setAction(Quit.ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_link)
            .addAction(R.drawable.ic_stat_link, locStatic("remote.notification.quit"), quit)
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
        watchers += lifecycleScope.launch {
            _onScreen.flatMapLatest { linked ->
                linked?.link?.state?.map { linked to it } ?: flowOf(null to LinkState.OFF)
            }.distinctUntilChanged().collect { (linked, state) ->
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(linked, state))
            }
        }
        // The Wi-Fi lock is held while any link is wanted; the machine on screen is chosen
        // again as links come and go (a main found sending once its link is live).
        watchers += lifecycleScope.launch {
            _links.flatMapLatest { list ->
                if (list.isEmpty()) flowOf(emptyList())
                else combine(list.map { it.link.state }) { states -> states.toList() }
            }.distinctUntilChanged().collect { states ->
                if (states.any { it != LinkState.OFF }) acquireWifiLock() else releaseWifiLock()
                reconsiderOnScreen()
            }
        }
        // Any linked machine may ask the tablet to show itself.
        watchers += lifecycleScope.launch {
            _links.flatMapLatest { list ->
                if (list.isEmpty()) emptyFlow()
                else merge(*list.map { linked -> linked.link.finds.map { linked.machine.label to it } }.toTypedArray())
            }.collect { (machine, request) ->
                FindMe.alert(this@EngineLinkService, machine, request.seconds)
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

        /** The key of [state]'s word, for the screen and the notification alike. */
        fun linkWordKey(state: LinkState): String = when (state) {
            LinkState.OFF -> "remote.link.off"
            LinkState.CONNECTING -> "remote.link.connecting"
            LinkState.LIVE -> "remote.link.live"
            LinkState.STALE -> "remote.link.stale"
            LinkState.CLOCK_STOPPED -> "remote.link.clockStopped"
            LinkState.GONE -> "remote.link.gone"
            LinkState.TOO_OLD -> "remote.link.tooOld"
        }

        fun linkWord(state: LinkState): String = locStatic(linkWordKey(state))
    }
}
