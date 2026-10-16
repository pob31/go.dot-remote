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

package com.pob31.godot.remote.ui.buzzer

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pob31.godot.remote.buzzer.BuzzerAccess
import com.pob31.godot.remote.buzzer.BuzzerContract
import com.pob31.godot.remote.buzzer.BuzzerPhase
import com.pob31.godot.remote.buzzer.BuzzerStatus
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.GodotIcon
import com.pob31.godot.remote.ui.GodotIconMark
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotMetrics
import com.pob31.godot.remote.ui.theme.GodotType

/**
 * THE WRIST BUZZER, on the Connections tab (the author: "Put the link to the buzzer in the
 * connection tab"): the buzzer the remote links to, how the link stands in words, its battery
 * and what its info says; Test and Identify; Unlink; and a scan that lists the buzzers heard,
 * each with its signal and a Link. Bluetooth's permissions are asked for when Scan or Link is
 * pressed, never at launch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuzzerSection(access: BuzzerAccess) {
    val status by access.status.collectAsState()
    val found by access.found.collectAsState()
    val scanning by access.scanning.collectAsState()
    val context = LocalContext.current
    var refused by remember { mutableStateOf(false) }
    var afterAsking by remember { mutableStateOf<(() -> Unit)?>(null) }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { answers ->
        val granted = answers.values.all { it }
        refused = !granted
        if (granted) afterAsking?.invoke()
        afterAsking = null
        access.permissionsAnswered()
    }
    /** [then], once Bluetooth is allowed - asked for first when it is not. */
    fun allowed(then: () -> Unit) {
        val missing = access.missingPermissions()
        if (missing.isEmpty()) {
            refused = false
            then()
        } else {
            afterAsking = then
            ask.launch(missing.toTypedArray())
        }
    }

    Surface(color = GodotColors.panelIn, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GodotIconMark(GodotIcon.BUZZER, GodotColors.inkDim, size = 24.dp)
                Spacer(Modifier.width(10.dp))
                Text(loc("buzzer.title"), style = MaterialTheme.typography.titleMedium, color = GodotColors.ink, fontWeight = FontWeight.SemiBold)
            }
            Text(loc("buzzer.explain"), color = GodotColors.inkDim)

            if (status.remembered == null) {
                Text(loc("buzzer.none"), color = GodotColors.inkFaint)
            } else {
                Remembered(status, access)
            }
            if (status.notABuzzer) Text(loc("buzzer.notABuzzer"), color = GodotColors.failed)
            when (status.phase) {
                BuzzerPhase.NO_PERMISSION -> OutlinedButton(
                    onClick = { allowed { } },
                    modifier = Modifier.heightIn(min = GodotMetrics.touch)
                ) { Text(loc("buzzer.allow"), color = GodotColors.ink) }
                BuzzerPhase.BLUETOOTH_OFF -> OutlinedButton(
                    onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    modifier = Modifier.heightIn(min = GodotMetrics.touch)
                ) { Text(loc("buzzer.openSettings"), color = GodotColors.ink) }
                else -> Unit
            }
            if (refused) Text(loc("buzzer.refused"), color = GodotColors.failed)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { if (scanning) access.stopScan() else allowed { access.scan() } },
                    modifier = Modifier.heightIn(min = GodotMetrics.touch)
                ) { Text(loc(if (scanning) "buzzer.stop" else "buzzer.scan"), color = GodotColors.ink) }
                if (scanning) Text(loc("buzzer.looking"), color = GodotColors.inkFaint)
            }
            if (scanning && found.isEmpty()) Text(loc("buzzer.noneHeard"), color = GodotColors.inkFaint)
            for (each in found) {
                Surface(
                    color = GodotColors.panel, shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, GodotColors.rule), modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(each.name, color = GodotColors.ink, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                each.address + " · " + loc("buzzer.signal", "dbm" to each.rssi.toString()),
                                style = GodotType.number, color = GodotColors.inkFaint
                            )
                        }
                        val isIt = status.remembered?.address == each.address
                        Button(
                            onClick = { allowed { access.link(each) } },
                            enabled = !isIt,
                            colors = ButtonDefaults.buttonColors(containerColor = GodotColors.standby, contentColor = Color.Black),
                            modifier = Modifier.heightIn(min = GodotMetrics.touch)
                        ) { Text(loc("buzzer.link")) }
                    }
                }
            }
            Text(loc("buzzer.pageNote"), color = GodotColors.inkFaint)
        }
    }
}

/** The buzzer the remote links to: name, address, state in words, battery, info, and its buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Remembered(status: BuzzerStatus, access: BuzzerAccess) {
    val remembered = status.remembered ?: return
    val linked = status.phase == BuzzerPhase.LINKED
    Surface(
        color = GodotColors.panel, shape = RoundedCornerShape(6.dp),
        border = BorderStroke(if (linked) 2.dp else 1.dp, if (linked) GodotColors.live else GodotColors.rule),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(remembered.name, style = MaterialTheme.typography.titleMedium, color = GodotColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(remembered.address, style = GodotType.number, color = GodotColors.inkFaint)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A mark AND a word: the colour never says it alone.
                Box(Modifier.size(10.dp).background(phaseColour(status.phase), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(loc(status.phase.key), color = GodotColors.ink, fontWeight = FontWeight.Medium)
                status.battery?.takeIf { linked }?.let {
                    Text("  ·  " + loc("buzzer.battery", "percent" to it.toString()), color = if (it <= 20) GodotColors.stopping else GodotColors.inkDim)
                }
            }
            status.info?.takeIf { linked }?.let { info ->
                val words = buildList {
                    info.firmware?.let { add(loc("buzzer.firmware", "version" to it)) }
                    when {
                        info.driverPresent == false -> add(loc("buzzer.noDriver"))
                        info.calibrated == false -> add(loc("buzzer.uncalibrated"))
                        info.calibrated == true -> add(loc("buzzer.calibrated"))
                    }
                    if (info.contract != BuzzerContract.CONTRACT) {
                        add(loc("buzzer.contract", "theirs" to info.contract.toString(), "ours" to BuzzerContract.CONTRACT.toString()))
                    }
                }
                val trouble = info.driverPresent == false || info.calibrated == false || info.contract != BuzzerContract.CONTRACT
                if (words.isNotEmpty()) Text(words.joinToString("  ·  "), color = if (trouble) GodotColors.stopping else GodotColors.inkDim)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = access::test, enabled = linked,
                    colors = ButtonDefaults.buttonColors(containerColor = GodotColors.panelHigh, contentColor = GodotColors.ink),
                    modifier = Modifier.heightIn(min = GodotMetrics.touch)
                ) { Text(loc("buzzer.test")) }
                Button(
                    onClick = access::identify, enabled = linked,
                    colors = ButtonDefaults.buttonColors(containerColor = GodotColors.panelHigh, contentColor = GodotColors.ink),
                    modifier = Modifier.heightIn(min = GodotMetrics.touch)
                ) { Text(loc("buzzer.identify")) }
                TextButton(onClick = access::forget, modifier = Modifier.heightIn(min = GodotMetrics.touch)) {
                    Text(loc("buzzer.unlink"), color = GodotColors.inkDim)
                }
            }
        }
    }
}

private fun phaseColour(phase: BuzzerPhase): Color = when (phase) {
    BuzzerPhase.LINKED -> GodotColors.live
    BuzzerPhase.SEARCHING -> GodotColors.waiting
    BuzzerPhase.LOST, BuzzerPhase.NO_PERMISSION, BuzzerPhase.BLUETOOTH_OFF -> GodotColors.failed
    BuzzerPhase.NONE -> GodotColors.inkOff
}
