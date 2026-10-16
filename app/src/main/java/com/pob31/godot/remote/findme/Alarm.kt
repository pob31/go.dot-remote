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
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.util.Log

/**
 * THE RULES OF THE FIND-ME ALARM, pure so a test reads them (the author, 2026-10-16: "Find
 * should sound the alarm and flash the camera LED too").
 */
object FindMePlan {
    /**
     * The screen and the camera's light change every 250 ms: two flashes a second, under
     * the three a second at which a flash becomes a risk to somebody photosensitive.
     */
    const val FLASH_MS = 250L

    /** Whole flashes a second at [stepMs] a change - lit then dark is one flash. */
    fun flashesPerSecond(stepMs: Long): Double = 1000.0 / (2 * stepMs)

    /**
     * The alarm stream's volume for the alarm: its loudest when the operator left it at
     * nothing - a tablet that cannot be heard cannot be found - else null, theirs untouched.
     */
    fun raisedVolume(current: Int, max: Int): Int? = if (current <= 0 && max > 0) max else null
}

/**
 * THE ALARM STREAM TURNED UP for the alarm when it was at nothing, and put back after - once,
 * however many alarms overlap: the first raise remembers the operator's volume and the
 * restore puts that one back.
 */
object AlarmVolume {
    private const val TAG = "AlarmVolume"
    private var restoreTo: Int? = null

    @Synchronized
    fun raise(context: Context) {
        try {
            val audio = context.getSystemService(AudioManager::class.java) ?: return
            val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            val target = FindMePlan.raisedVolume(current, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)) ?: return
            if (restoreTo == null) restoreTo = current
            audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        } catch (e: Exception) {
            // Do Not Disturb may refuse it: the alarm sounds at what volume there is.
            Log.w(TAG, "could not turn the alarm up", e)
        }
    }

    @Synchronized
    fun restore(context: Context) {
        val to = restoreTo ?: return
        restoreTo = null
        try {
            context.getSystemService(AudioManager::class.java)?.setStreamVolume(AudioManager.STREAM_ALARM, to, 0)
        } catch (e: Exception) {
            Log.w(TAG, "could not put the alarm volume back", e)
        }
    }
}

/**
 * THE ALARM'S SOUND, looping on the alarm stream through a MediaPlayer (a Ringtone stops when
 * it likes): the tablet's alarm, else its ringtone, else its notification sound, else a tone
 * of the system's own - never silence by choice.
 */
class AlarmSound(private val context: Context) {
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null

    fun start() {
        if (player != null || tone != null) return
        for (uri in FindMe.alarmSounds()) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(FindMe.alarmAttributes)
                    setDataSource(context, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "no alarm from $uri", e)
                player?.release()
                player = null
            }
        }
        try {
            tone = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME).apply {
                startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK)
            }
        } catch (e: Exception) {
            Log.w(TAG, "no tone either", e)
        }
    }

    fun stop() {
        player?.let {
            try { it.stop() } catch (e: Exception) { /* never started */ }
            it.release()
        }
        player = null
        tone?.let {
            try { it.stopTone() } catch (e: Exception) { /* already still */ }
            it.release()
        }
        tone = null
    }

    private companion object {
        const val TAG = "AlarmSound"
    }
}

/**
 * THE CAMERA'S LIGHT, as a torch: the back camera's flash unit if it has one, else any
 * camera's. No CAMERA permission is needed for the torch (Android 6+). A camera in use by
 * another app, or gone, refuses: the screen still flashes. [off] is safe to call whenever.
 */
class Torch(context: Context) {
    private val cameras: CameraManager? = context.getSystemService(CameraManager::class.java)
    private val id: String? = findFlash()
    private var lit = false

    /** Whether this tablet has a light to flash. */
    val available: Boolean get() = id != null

    private fun findFlash(): String? = try {
        val manager = cameras ?: return null
        val withFlash = manager.cameraIdList.filter {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        withFlash.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    } catch (e: Exception) {
        Log.w(TAG, "no camera to ask about a flash", e)
        null
    }

    fun set(on: Boolean) {
        if (on == lit) return
        if (write(on)) lit = on
    }

    fun off() {
        write(false)
        lit = false
    }

    private fun write(on: Boolean): Boolean {
        val camera = id ?: return false
        return try {
            cameras?.setTorchMode(camera, on)
            true
        } catch (e: CameraAccessException) {
            Log.w(TAG, "the camera is busy or disabled", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "the camera is gone", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "the torch is not allowed", e)
            false
        }
    }

    private companion object {
        const val TAG = "Torch"
    }
}
