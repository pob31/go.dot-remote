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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pob31.godot.remote.link.EngineLinkService
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.link.Machine
import com.pob31.godot.remote.link.currentIpAddress
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.CueLabel
import com.pob31.godot.remote.model.standbyCue
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotMetrics
import com.pob31.godot.remote.ui.theme.GodotType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample

/**
 * SCAFFOLDING for checking the link on the tablet (stage A.2): a machine's address, a
 * Connect button, how the link stands, the tick, how many nodes the mirror holds, and the
 * cue in standby read from it. The tabs replace it from A.3 on.
 */
@Composable
fun LinkScreen(service: EngineLinkService?) {
    Box(
        Modifier
            .fillMaxSize()
            .background(GodotColors.ground)
    ) {
        if (service == null) {
            Text(loc("remote.screen.starting"), color = GodotColors.inkFaint, modifier = Modifier.padding(24.dp))
        } else {
            LinkPanel(service)
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun LinkPanel(service: EngineLinkService) {
    val onScreen by service.onScreen.collectAsState()
    val foregroundProblem by service.foregroundProblem.collectAsState()
    val remembered = remember(service) { onScreen?.machine ?: service.machines.firstOrNull() }

    var host by remember(service) { mutableStateOf(remembered?.host ?: "") }
    var port by remember(service) { mutableStateOf((remembered?.port ?: Machine.DEFAULT_PORT).toString()) }
    var name by remember(service) { mutableStateOf(remembered?.name ?: "") }
    val ip = remember { currentIpAddress() }

    val link = onScreen?.link
    val state by (link?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(LinkState.OFF) }).collectAsState()
    val problem by (link?.problem ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }).collectAsState()
    val origin by (link?.origin ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }).collectAsState()
    val refusal by (link?.lastRefusal ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()

    // The mirror is read at no more than 25 Hz, driven by its tick.
    var tick by remember(link) { mutableLongStateOf(-1L) }
    var nodes by remember(link) { mutableIntStateOf(0) }
    var standby by remember(link) { mutableStateOf<CueLabel?>(null) }
    LaunchedEffect(link) {
        val mirror = link?.mirror ?: return@LaunchedEffect
        mirror.tick.sample(40).collect { t ->
            tick = t
            nodes = mirror.size
            standby = standbyCue(mirror)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(loc("remote.screen.title"), style = MaterialTheme.typography.headlineMedium, color = GodotColors.ink)
        Text(
            loc("remote.screen.thisTablet", "name" to service.tabletName, "ip" to (ip ?: loc("remote.screen.noAddress"))),
            color = GodotColors.inkFaint
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = host, onValueChange = { host = it.trim() },
                label = { Text(loc("remote.screen.host")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.widthIn(min = 200.dp, max = 280.dp)
            )
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) },
                label = { Text(loc("remote.screen.port")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = GodotType.number,
                modifier = Modifier.width(110.dp)
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(loc("remote.screen.name")) }, singleLine = true,
                modifier = Modifier.widthIn(min = 160.dp, max = 240.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val portNumber = port.toIntOrNull()?.takeIf { it in 1..65535 }
            Button(
                onClick = { if (portNumber != null && host.isNotBlank()) service.connect(name, host, portNumber) },
                enabled = portNumber != null && host.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = GodotColors.standby, contentColor = Color.Black),
                modifier = Modifier.height(GodotMetrics.touch)
            ) { Text(loc("remote.screen.connect")) }
            if (onScreen != null) {
                Button(
                    onClick = { onScreen?.let { service.disconnect(it.machine.key) } },
                    colors = ButtonDefaults.buttonColors(containerColor = GodotColors.panelHigh, contentColor = GodotColors.ink),
                    modifier = Modifier.height(GodotMetrics.touch)
                ) { Text(loc("remote.screen.disconnect")) }
            }
        }

        if (onScreen == null) {
            Text(loc("remote.screen.notLinked"), color = GodotColors.inkFaint)
        } else {
            Text(onScreen!!.machine.label + "  " + onScreen!!.machine.key, color = GodotColors.inkDim)
            LinkStateLine(state)
            Reading(loc("remote.screen.tick"), if (tick >= 0) tick.toString() else "-")
            Reading(loc("remote.screen.nodes"), nodes.toString())
            Reading(
                loc("remote.screen.standby"),
                standby?.let { "${it.number}  ${it.name}" } ?: loc("remote.screen.nothingInStandby"),
                color = if (standby != null) GodotColors.standby else GodotColors.inkFaint
            )
            Text(
                origin?.let { loc("remote.screen.knownAs", "origin" to it) } ?: loc("remote.screen.notKnownYet"),
                color = GodotColors.inkOff
            )
            refusal?.let { r ->
                val sentence = r.command?.let { loc("remote.refusal.withCommand", "command" to it, "reason" to r.reason) }
                    ?: loc("remote.refusal.plain", "reason" to r.reason)
                Text(sentence, color = GodotColors.failed)
            }
            if (state != LinkState.LIVE) {
                problem?.let { Text(loc("remote.screen.lastProblem", "problem" to it), color = GodotColors.inkFaint) }
            }
        }

        foregroundProblem?.let {
            Text(loc("remote.screen.foregroundRefused", "problem" to it), color = GodotColors.failed)
        }
    }
}

/** How the link stands: a mark in the state's colour AND the word, never the colour alone (§4.8). */
@Composable
private fun LinkStateLine(state: LinkState) {
    val colour = when (state) {
        LinkState.OFF -> GodotColors.inkOff
        LinkState.CONNECTING -> GodotColors.waiting
        LinkState.LIVE -> GodotColors.live
        LinkState.STALE -> GodotColors.stopping
        LinkState.CLOCK_STOPPED -> GodotColors.stopping
        LinkState.GONE -> GodotColors.failed
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(loc("remote.screen.link"), color = GodotColors.inkFaint, modifier = Modifier.width(120.dp))
        Box(
            Modifier
                .size(14.dp)
                .background(colour, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(EngineLinkService.linkWord(state), color = GodotColors.ink, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Reading(label: String, value: String, color: Color = GodotColors.ink) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = GodotColors.inkFaint, modifier = Modifier.width(120.dp))
        Text(value, color = color, style = GodotType.numberLarge)
    }
}
