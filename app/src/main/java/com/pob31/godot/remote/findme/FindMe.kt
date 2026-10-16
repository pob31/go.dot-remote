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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pob31.godot.remote.R
import com.pob31.godot.remote.localization.locStatic

/** Whether the app's window is in front: set by MainActivity, read when a FIND comes. */
object AppVisibility {
    @Volatile var inFront: Boolean = false
}

/**
 * FIND-ME (namespace draft §68, ANP): Go.dot asks, the tablet shows itself - WFS-DIY's
 * remote's Find Device, carried over and made to work with the app behind another or the
 * tablet locked.
 *
 * TWO DOORS, because Android shuts one or the other:
 * - the app in front: [FindMeActivity] is started at once;
 * - otherwise: a notification on the high-importance "find" channel, category alarm,
 *   with a full-screen intent - over the lock screen the system opens [FindMeActivity]
 *   itself. Android 14 may withhold full-screen intents ([canShowOverLock]); the
 *   notification is then a heads-up that still sounds (on the alarm stream, over and over:
 *   FLAG_INSISTENT) and vibrates until it is touched, swiped away, or its time is up.
 *
 * EITHER WAY IT IS HEARD: an alarm volume left at nothing is turned up to its loudest for
 * the alarm and put back after ([AlarmVolume]); the find screen also flashes the camera's
 * light in step with itself ([Torch]).
 */
object FindMe {
    private const val TAG = "FindMe"
    const val CHANNEL_ID = "find"
    const val NOTIFICATION_ID = 2

    const val EXTRA_MACHINE = "machine"
    const val EXTRA_UNTIL = "until"

    /** Long and short, long and short: a phone's alarm, not a message's buzz. */
    val VIBRATION = longArrayOf(0, 600, 200, 600, 200, 1200, 400)

    val alarmAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** The tablet's alarm sound, or its ringtone, or its notification sound: never silence by choice. */
    fun alarmSound(): Uri? = alarmSounds().firstOrNull()

    /** Every sound the alarm may try, in order: the alarm, the ringtone, the notification sound. */
    fun alarmSounds(): List<Uri> = listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION)
        .mapNotNull { RingtoneManager.getDefaultUri(it) }
        .distinct()

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            locStatic("remote.find.channelName"),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = locStatic("remote.find.channelDescription")
            setSound(alarmSound(), alarmAttributes)
            enableVibration(true)
            vibrationPattern = VIBRATION
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Whether Android lets the alarm open over the lock screen (Android 14+ asks; before, always). */
    fun canShowOverLock(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 34) context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        else true

    /** Whether the app's notifications are shown at all: without them, nothing finds a tablet in the background. */
    fun notificationsOn(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** The system page where the operator lets the app open over the lock screen (Android 14+). */
    fun overLockSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName))
        else notificationSettings(context)

    fun notificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    private fun activityIntent(context: Context, machine: String, until: Long): Intent =
        Intent(context, FindMeActivity::class.java)
            .putExtra(EXTRA_MACHINE, machine)
            .putExtra(EXTRA_UNTIL, until)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** [machine] asks this tablet to show itself for [seconds]. */
    fun alert(context: Context, machine: String, seconds: Int) {
        val until = SystemClock.elapsedRealtime() + seconds * 1000L
        val intent = activityIntent(context, machine, until)

        if (AppVisibility.inFront) {
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "could not open the find screen directly", e)
            }
        }

        val open = PendingIntent.getActivity(
            context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle(locStatic("remote.find.notificationTitle", "machine" to machine))
            .setContentText(locStatic("remote.find.notificationText"))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(seconds * 1000L)
            .setOnlyAlertOnce(false)
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        // Heard even with the alarm volume at nothing: turned up for the time asked, then put back.
        AlarmVolume.raise(context)
        Handler(Looper.getMainLooper()).postDelayed({ AlarmVolume.restore(context.applicationContext) }, seconds * 1000L)
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "notifications are not allowed: the tablet cannot be found in the background", e)
        }
    }

    /** The alarm is answered: its notification goes, and with it any sound it was making. */
    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** The Machines tab's "Test find": the screen, the sound and the vibration, here, for [seconds]. */
    fun test(context: Context, machine: String, seconds: Int = 3) {
        context.startActivity(activityIntent(context, machine, SystemClock.elapsedRealtime() + seconds * 1000L))
    }
}
