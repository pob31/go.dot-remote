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

package com.pob31.godot.remote.buzzer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.pob31.godot.remote.RemotePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A buzzer heard by a scan: its address, its CHF-XXXX name, how strongly it was heard. */
data class FoundBuzzer(val address: String, val name: String, val rssi: Int)

/** The buzzer the remote links to, kept by its address. */
data class RememberedBuzzer(val address: String, val name: String)

data class BuzzerStatus(
    val remembered: RememberedBuzzer? = null,
    val phase: BuzzerPhase = BuzzerPhase.NONE,
    val battery: Int? = null,
    val info: BuzzerInfo? = null,
    /** The wearable linked but does not have the buzzer's service: not a Choufleur buzzer. */
    val notABuzzer: Boolean = false,
)

/** What the Connections tab's Buzzer section needs: a seam for the screenshot test. */
interface BuzzerAccess {
    val status: StateFlow<BuzzerStatus>
    val found: StateFlow<List<FoundBuzzer>>
    val scanning: StateFlow<Boolean>

    /** The Android permissions Bluetooth still wants, asked for when Scan or Link is pressed. */
    fun missingPermissions(): List<String>

    /** The permissions were answered: a remembered buzzer is looked for again. */
    fun permissionsAnswered()
    fun scan()
    fun stopScan()
    fun link(found: FoundBuzzer)

    /** Unlink and forget: the remote stops looking for it. */
    fun forget()
    fun test()
    fun identify()
}

/**
 * THE LINK TO CHOUFLEUR'S WRIST BUZZER (the author, 2026-10-16: "If the Go.dot remote could
 * also link to the buzzers that would be great"): Android's own BLE, no library. One per
 * process; the link service starts it with the machines and Quit closes it, so it lives while
 * the remote does, whichever tab is on screen. Choufleur's page in the Script tab writes its
 * warnings through it ([BuzzerBridge]); the remote itself only keeps it alive (a heartbeat
 * every 30 s, inside the wearable's 90 s watchdog) and offers Test and Identify.
 *
 * Remembered by address and looked for again for as long as it is remembered: a scan for that
 * address finds it (so a reboot of the tablet does not lose it), a direct link takes it, and a
 * link that falls is handed to Android's autoConnect, which takes it back the moment it is in
 * range; tries spaced by [ReconnectPlan] cover what autoConnect does not.
 *
 * Everything runs on one thread of its own, GATT operations one at a time.
 */
@SuppressLint("MissingPermission") // every call is behind hasConnect()/hasScan(), and a SecurityException is caught
class BuzzerLink private constructor(private val app: Context) : BuzzerAccess {

    private val thread = HandlerThread("buzzer").apply { start() }
    private val handler = Handler(thread.looper)
    private val prefs = app.getSharedPreferences(RemotePrefs.NAME, Context.MODE_PRIVATE)
    private val manager = app.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = manager?.adapter

    private val _status = MutableStateFlow(BuzzerStatus(remembered = loadRemembered()).let {
        it.copy(phase = if (it.remembered == null) BuzzerPhase.NONE else BuzzerPhase.SEARCHING)
    })
    override val status: StateFlow<BuzzerStatus> = _status.asStateFlow()

    private val _found = MutableStateFlow<List<FoundBuzzer>>(emptyList())
    override val found: StateFlow<List<FoundBuzzer>> = _found.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    override val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** The service wants the buzzer linked: false before it starts and after Quit. */
    private var wanted = false
    private var gatt: BluetoothGatt? = null
    private var device: BluetoothDevice? = null
    private var vibe: BluetoothGattCharacteristic? = null
    private val heartbeat = Heartbeat()
    private val plan = ReconnectPlan()
    private var receiverOn = false

    // ---- what the screen and the bridge call (any thread) ----

    override fun missingPermissions(): List<String> {
        val wants = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return wants.filter { ContextCompat.checkSelfPermission(app, it) != PackageManager.PERMISSION_GRANTED }
    }

    override fun permissionsAnswered() = post { if (wanted) lookFor() }

    /** The link service starts: a remembered buzzer is looked for. */
    fun start() = post {
        wanted = true
        if (!receiverOn) {
            ContextCompat.registerReceiver(app, bluetoothState, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverOn = true
        }
        lookFor()
    }

    /** Quit: the link closes; the buzzer stays remembered, linked again at the next launch. */
    fun close() = post {
        wanted = false
        if (receiverOn) {
            runCatching { app.unregisterReceiver(bluetoothState) }
            receiverOn = false
        }
        stopScanNow()
        drop()
        val remembered = _status.value.remembered
        _status.value = BuzzerStatus(remembered, if (remembered == null) BuzzerPhase.NONE else BuzzerPhase.SEARCHING)
    }

    override fun scan() = post {
        if (!hasScan()) {
            phase(BuzzerEvent.NO_PERMISSION)
            return@post
        }
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: run { phase(BuzzerEvent.BLUETOOTH_OFF); return@post }
        stopScanNow()
        _found.value = emptyList()
        try {
            scanner.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(BuzzerContract.SERVICE)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                listing
            )
            _scanning.value = true
            handler.postDelayed(stopListing, SCAN_MS)
        } catch (e: SecurityException) {
            phase(BuzzerEvent.NO_PERMISSION)
        }
    }

    override fun stopScan() = post { stopScanNow() }

    override fun link(found: FoundBuzzer) = post {
        stopScanNow()
        drop()
        val remembered = RememberedBuzzer(found.address, found.name)
        prefs.edit().putString(KEY_ADDRESS, remembered.address).putString(KEY_NAME, remembered.name).apply()
        _status.value = BuzzerStatus(remembered, BuzzerPhase.SEARCHING)
        plan.linked()
        wanted = true
        device = runCatching { adapter?.getRemoteDevice(found.address) }.getOrNull()
        connect(autoConnect = false)
    }

    override fun forget() = post {
        prefs.edit().remove(KEY_ADDRESS).remove(KEY_NAME).apply()
        drop()
        device = null
        _status.value = BuzzerStatus()
    }

    override fun test() {
        write(BuzzerContract.frame(BuzzerContract.OP_TEST, 0))
    }

    override fun identify() {
        write(BuzzerContract.frame(BuzzerContract.OP_IDENTIFY))
    }

    /** Writes one frame, without response; false when no buzzer is linked (the page says "dropped"). */
    fun write(frame: ByteArray): Boolean {
        if (frame.isEmpty() || frame.size > 2 || _status.value.phase != BuzzerPhase.LINKED) return false
        post {
            enqueueWrite(frame)
            val op = frame[0].toInt() and 0xff
            if (op == BuzzerContract.OP_HEARTBEAT) heartbeat.sent(SystemClock.elapsedRealtime())
            // A calibration takes up to ~3 s; the info byte then says how it went.
            if (op == BuzzerContract.OP_CALIBRATE) handler.postDelayed({ readInfo() }, 3_000L)
        }
        return true
    }

    // ---- the link (the buzzer's thread) ----

    private fun post(block: () -> Unit) {
        handler.post(block)
    }

    private fun hasConnect() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun hasScan() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun phase(event: BuzzerEvent) {
        val now = _status.value
        _status.value = now.copy(phase = now.phase.next(event))
    }

    /** Looks for the remembered buzzer: a short scan for its address, then a direct link. */
    private fun lookFor() {
        handler.removeCallbacks(retry)
        val remembered = _status.value.remembered ?: return
        if (!wanted || _status.value.phase == BuzzerPhase.LINKED || gatt != null) return
        if (!hasConnect()) {
            phase(BuzzerEvent.NO_PERMISSION)
            return
        }
        val adapter = adapter
        if (adapter == null || !adapter.isEnabled) {
            phase(BuzzerEvent.BLUETOOTH_OFF)
            return
        }
        if (_status.value.phase == BuzzerPhase.NO_PERMISSION || _status.value.phase == BuzzerPhase.BLUETOOTH_OFF) {
            _status.value = _status.value.copy(phase = BuzzerPhase.SEARCHING)
        }
        val scanner = adapter.bluetoothLeScanner
        if (!hasScan() || scanner == null) {
            // No scan allowed: Android's autoConnect alone, on the address as it is.
            device = device ?: runCatching { adapter.getRemoteDevice(remembered.address) }.getOrNull()
            connect(autoConnect = true)
            return
        }
        try {
            stopSeeking()
            scanner.startScan(
                listOf(ScanFilter.Builder().setDeviceAddress(remembered.address).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                seeking
            )
            seekingOn = true
            handler.postDelayed(seekTimeout, SEEK_MS)
        } catch (e: SecurityException) {
            phase(BuzzerEvent.NO_PERMISSION)
        } catch (e: IllegalArgumentException) {
            retryLater()
        }
    }

    private var seekingOn = false

    private val seeking = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = post {
            if (!seekingOn) return@post
            stopSeeking()
            device = result.device
            connect(autoConnect = false)
        }

        override fun onScanFailed(errorCode: Int) = post {
            stopSeeking()
            retryLater()
        }
    }

    private val seekTimeout = Runnable {
        if (!seekingOn) return@Runnable
        stopSeeking()
        // Not heard: autoConnect waits for it meanwhile, and the next look is spaced out.
        device = device ?: runCatching { adapter?.getRemoteDevice(_status.value.remembered?.address) }.getOrNull()
        if (gatt == null) connect(autoConnect = true)
        retryLater()
    }

    private fun stopSeeking() {
        handler.removeCallbacks(seekTimeout)
        if (!seekingOn) return
        seekingOn = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(seeking) }
    }

    private val retry = Runnable {
        if (_status.value.phase == BuzzerPhase.LINKED || !wanted) return@Runnable
        // A pending link is left to Android for a while; one that has waited that long is
        // replaced by a fresh look, which also finds a wearable whose address Android forgot.
        if (gatt != null && SystemClock.elapsedRealtime() - pendingSince < AUTO_WAIT_MS) {
            retryLater()
            return@Runnable
        }
        closeGatt()
        lookFor()
    }

    /** When the pending link was asked for. */
    private var pendingSince = 0L

    private fun retryLater() {
        if (!wanted || _status.value.remembered == null) return
        handler.removeCallbacks(retry)
        handler.postDelayed(retry, plan.nextDelay())
    }

    private fun connect(autoConnect: Boolean) {
        val target = device ?: run { retryLater(); return }
        closeGatt()
        phase(BuzzerEvent.CONNECTING)
        try {
            gatt = target.connectGatt(app, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
            pendingSince = SystemClock.elapsedRealtime()
            if (gatt == null) retryLater()
        } catch (e: SecurityException) {
            phase(BuzzerEvent.NO_PERMISSION)
        }
    }

    /** The link closed by the remote: no drop is reported, nothing is retried. */
    private fun drop() {
        handler.removeCallbacks(retry)
        handler.removeCallbacks(beat)
        stopSeeking()
        closeGatt()
        heartbeat.dropped()
    }

    private fun closeGatt() {
        val g = gatt ?: return
        gatt = null
        vibe = null
        ops.clear()
        busy = null
        handler.removeCallbacks(opTimeout)
        runCatching { g.disconnect() }
        runCatching { g.close() }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) = post {
            if (g !== gatt) {
                runCatching { g.close() }
                return@post
            }
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                runCatching { g.discoverServices() }.onFailure { closeGatt(); retryLater() }
                return@post
            }
            if (newState != BluetoothProfile.STATE_DISCONNECTED && status == BluetoothGatt.GATT_SUCCESS) return@post
            val wasLinked = _status.value.phase == BuzzerPhase.LINKED
            handler.removeCallbacks(beat)
            heartbeat.dropped()
            _status.value = _status.value.let { it.copy(phase = it.phase.next(BuzzerEvent.DROPPED), battery = null) }
            closeGatt()
            if (!wanted) return@post
            if (wasLinked) {
                // Out of range or switched off: Android takes it back when it is heard again.
                connect(autoConnect = true)
            }
            retryLater()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = post {
            if (g !== gatt) return@post
            val service = g.getService(BuzzerContract.SERVICE)
            val vibe = service?.getCharacteristic(BuzzerContract.VIBE)
            if (vibe == null) {
                _status.value = _status.value.copy(notABuzzer = true)
                closeGatt()
                retryLater()
                return@post
            }
            this@BuzzerLink.vibe = vibe
            handler.removeCallbacks(retry)
            plan.linked()
            val now = SystemClock.elapsedRealtime()
            heartbeat.linked(now)
            _status.value = _status.value.copy(phase = BuzzerPhase.LINKED, notABuzzer = false)
            scheduleBeat()
            readInfo()
            g.getService(BuzzerContract.BATTERY_SERVICE)?.getCharacteristic(BuzzerContract.BATTERY_LEVEL)?.let { level ->
                enqueue { gatt -> gatt.readCharacteristic(level) }
                enqueue { gatt -> notifyOn(gatt, level) }
            }
        }

        @Deprecated("Android 12 and below")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION") val value = c.value
            read(c, value, status)
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) =
            read(c, value, status)

        private fun read(c: BluetoothGattCharacteristic, value: ByteArray?, status: Int) = post {
            if (status == BluetoothGatt.GATT_SUCCESS && value != null) heard(c.uuid, value)
            opDone()
        }

        @Deprecated("Android 12 and below")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") val value = c.value ?: return
            post { heard(c.uuid, value) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = post {
            heard(c.uuid, value)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) = post { opDone() }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) = post { opDone() }
    }

    private fun heard(uuid: java.util.UUID, value: ByteArray) {
        when (uuid) {
            BuzzerContract.INFO -> _status.value = _status.value.copy(info = BuzzerInfo.parse(value))
            BuzzerContract.BATTERY_LEVEL -> if (value.isNotEmpty()) _status.value = _status.value.copy(battery = value[0].toInt() and 0xff)
        }
    }

    private fun readInfo() {
        val info = gatt?.getService(BuzzerContract.SERVICE)?.getCharacteristic(BuzzerContract.INFO) ?: return
        enqueue { g -> g.readCharacteristic(info) }
    }

    private fun notifyOn(g: BluetoothGatt, c: BluetoothGattCharacteristic): Boolean {
        if (!g.setCharacteristicNotification(c, true)) return false
        val config = c.getDescriptor(BuzzerContract.CLIENT_CONFIG) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(config, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            config.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(config)
        }
    }

    private fun enqueueWrite(frame: ByteArray) {
        enqueue { g ->
            val c = vibe ?: return@enqueue false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                c.value = frame
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
        }
    }

    // ---- GATT operations, one at a time ----

    private val ops = ArrayDeque<(BluetoothGatt) -> Boolean>()
    private var busy: ((BluetoothGatt) -> Boolean)? = null

    private fun enqueue(op: (BluetoothGatt) -> Boolean) {
        if (ops.size >= MAX_QUEUED) ops.removeFirst() // a wearable gone quiet must not grow a backlog of stale warnings
        ops.addLast(op)
        pump()
    }

    private fun pump() {
        while (busy == null) {
            val g = gatt ?: return
            val op = ops.removeFirstOrNull() ?: return
            val started = try {
                op(g)
            } catch (e: SecurityException) {
                false
            }
            if (started) {
                busy = op
                handler.postDelayed(opTimeout, OP_TIMEOUT_MS)
            }
        }
    }

    private fun opDone() {
        handler.removeCallbacks(opTimeout)
        busy = null
        pump()
    }

    private val opTimeout = Runnable { opDone() }

    // ---- the heartbeat ----

    private val beat = Runnable {
        val now = SystemClock.elapsedRealtime()
        if (_status.value.phase == BuzzerPhase.LINKED && heartbeat.due(now)) {
            enqueueWrite(BuzzerContract.frame(BuzzerContract.OP_HEARTBEAT))
            heartbeat.sent(now)
        }
        scheduleBeat()
    }

    private fun scheduleBeat() {
        handler.removeCallbacks(beat)
        val wait = heartbeat.wait(SystemClock.elapsedRealtime()) ?: return
        handler.postDelayed(beat, wait + 50L)
    }

    // ---- the list a scan fills ----

    private val listing = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = post {
            if (!_scanning.value) return@post
            val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: result.device.address
            val heard = FoundBuzzer(result.device.address, name, result.rssi)
            _found.value = (_found.value.filter { it.address != heard.address } + heard).sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) = post { stopScanNow() }
    }

    private val stopListing = Runnable { stopScanNow() }

    private fun stopScanNow() {
        handler.removeCallbacks(stopListing)
        if (!_scanning.value) return
        _scanning.value = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(listing) }
    }

    private val bluetoothState = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> post {
                    if (_status.value.remembered != null) _status.value = _status.value.copy(phase = BuzzerPhase.SEARCHING)
                    lookFor()
                }
                BluetoothAdapter.STATE_OFF -> post {
                    drop()
                    stopScanNow()
                    if (_status.value.remembered != null) phase(BuzzerEvent.BLUETOOTH_OFF)
                }
            }
        }
    }

    private fun loadRemembered(): RememberedBuzzer? {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return null
        return RememberedBuzzer(address, prefs.getString(KEY_NAME, null) ?: address)
    }

    companion object {
        private const val KEY_ADDRESS = "buzzerAddress"
        private const val KEY_NAME = "buzzerName"
        private const val SCAN_MS = 15_000L
        private const val SEEK_MS = 12_000L
        private const val OP_TIMEOUT_MS = 2_000L
        private const val MAX_QUEUED = 16
        private const val AUTO_WAIT_MS = 30_000L

        @Volatile
        private var shared: BuzzerLink? = null

        fun shared(context: Context): BuzzerLink =
            shared ?: synchronized(this) {
                shared ?: BuzzerLink(context.applicationContext).also { shared = it }
            }
    }
}
