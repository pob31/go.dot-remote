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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pob31.godot.remote.link.EngineLinkService
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.link.LinkedMachine
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.GodotIcon
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.cues.CuesTab
import com.pob31.godot.remote.ui.running.RunningTab
import com.pob31.godot.remote.ui.theme.GodotMetrics

/** The five tabs, each a word and a drawn picture (never the picture alone). */
enum class RemoteTab(val key: String, val icon: GodotIcon, val laterKey: String?) {
    CUES("remote.tabs.cues", GodotIcon.LIST, "remote.tabs.cuesLater"),
    RUNNING("remote.tabs.running", GodotIcon.START, "remote.tabs.runningLater"),
    SURFACE("remote.tabs.surface", GodotIcon.SURFACE, "remote.tabs.surfaceLater"),
    MAPPING("remote.tabs.mapping", GodotIcon.VIDEO, "remote.tabs.mappingLater"),
    MACHINES("remote.tabs.machines", GodotIcon.MACHINE, null),
}

/**
 * THE APP'S SHELL: the transport header always at the top, whatever the tab; the tab row
 * under it; and the tab. Every tab and the header read the mirror of the machine ON
 * SCREEN; only the Machines tab reads the others.
 */
@Composable
fun RemoteApp(service: EngineLinkService?) {
    Box(
        Modifier
            .fillMaxSize()
            .background(GodotColors.ground)
            .safeDrawingPadding()
    ) {
        if (service == null) {
            Text(loc("remote.screen.starting"), color = GodotColors.inkFaint, modifier = Modifier.padding(24.dp))
            return@Box
        }
        val onScreen by service.onScreen.collectAsState()
        val machines = remember(service) { ServiceMachines(service) }
        Shell(
            onScreen = onScreen,
            header = { TransportHeader(onScreen) },
            machinesTab = { MachinesTab(machines, rememberSystemFindMe()) },
            runningTab = { RunningTab(onScreen) },
            cuesTab = { CuesTab(onScreen) },
        )
    }
}

@Composable
fun Shell(
    onScreen: LinkedMachine?,
    header: @Composable () -> Unit,
    machinesTab: @Composable () -> Unit,
    initialTab: RemoteTab? = null,
    runningTab: (@Composable () -> Unit)? = null,
    cuesTab: (@Composable () -> Unit)? = null,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab ?: if (onScreen == null) RemoteTab.MACHINES else RemoteTab.CUES) }
    Column(
        Modifier
            .fillMaxSize()
            .background(GodotColors.ground)
    ) {
        header()
        TabStrip(tab) { tab = it }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (tab) {
                RemoteTab.MACHINES -> machinesTab()
                RemoteTab.RUNNING -> runningTab?.invoke() ?: LaterTab(tab, onScreen)
                RemoteTab.CUES -> cuesTab?.invoke() ?: LaterTab(tab, onScreen)
                else -> LaterTab(tab, onScreen)
            }
        }
    }
}

/**
 * The tab row: a word and a picture each, at least a finger's height; the tab shown is
 * lit in the picked colour AND marked by a bar under it.
 */
@Composable
private fun TabStrip(current: RemoteTab, onPick: (RemoteTab) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(GodotColors.titleBar)
    ) {
        // Side by side when they fit; on a phone, each a fifth of the width, its word under its picture.
        val stacked = maxWidth < 640.dp
        Row(Modifier.fillMaxWidth()) {
            for (each in RemoteTab.entries) {
                val selected = each == current
                val ink = if (selected) GodotColors.ink else GodotColors.inkFaint
                val picture = if (selected) GodotColors.picked else GodotColors.inkFaint
                val weight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                Column(
                    (if (stacked) Modifier.weight(1f) else Modifier.width(IntrinsicSize.Max).widthIn(min = 132.dp))
                        .clickable(role = Role.Tab) { onPick(each) }
                        .height(if (stacked) 60.dp else GodotMetrics.touch + 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (stacked) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            GodotIconMark(each.icon, picture, size = 22.dp)
                            Text(loc(each.key), color = ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = weight)
                        }
                    } else {
                        Row(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GodotIconMark(each.icon, picture, size = 22.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(loc(each.key), color = ink, maxLines = 1, fontWeight = weight)
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(if (selected) GodotColors.picked else GodotColors.titleBar)
                    )
                }
            }
        }
    }
}

/** A tab to come: says so, and says how the link stands when the machine on screen is not live. */
@Composable
private fun LaterTab(tab: RemoteTab, onScreen: LinkedMachine?) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GodotIconMark(tab.icon, GodotColors.inkOff, size = 48.dp)
        tab.laterKey?.let { Text(loc(it), color = GodotColors.inkFaint, textAlign = TextAlign.Center) }
        if (onScreen == null) {
            Text(loc("remote.tabs.noMachine"), color = GodotColors.inkFaint, textAlign = TextAlign.Center)
        } else {
            val state by onScreen.link.state.collectAsState()
            if (state != LinkState.LIVE) {
                Text(onScreen.machine.label, color = GodotColors.inkDim)
                LinkMark(state)
            }
        }
    }
}
