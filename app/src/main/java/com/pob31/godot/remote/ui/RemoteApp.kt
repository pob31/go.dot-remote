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

import android.app.Activity
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.areNavigationBarsVisible
import androidx.compose.foundation.layout.areStatusBarsVisible
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.pob31.godot.remote.model.hasCart
import com.pob31.godot.remote.buzzer.BuzzerLink
import com.pob31.godot.remote.prompt.PromptPage
import com.pob31.godot.remote.prompt.PromptStore
import com.pob31.godot.remote.ui.mapping.MappingTab
import com.pob31.godot.remote.ui.prompt.ScriptTab
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.cart.CartTab
import com.pob31.godot.remote.ui.cues.CuesTab
import com.pob31.godot.remote.ui.running.RunningTab
import com.pob31.godot.remote.ui.surface.SurfaceTab
import com.pob31.godot.remote.ui.theme.GodotMetrics

/**
 * WHAT THE APP KEEPS CLEAR OF: a camera cutout, the keyboard, and the status and navigation
 * bars - which the activity hides, so they take nothing. NOT the caption bar while the app
 * fills the screen: a tablet's window handle (the three dots at the top of a Lenovo's) stays
 * over the app whatever it asks, and keeping clear of it would give back the band the hidden
 * status bar freed (the author, 2026-10-16). In a window of its own (split screen, a
 * desktop-like window) the caption carries the window's buttons, so it is kept clear of.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun appInsets(): WindowInsets {
    LocalConfiguration.current // read: entering or leaving a window of its own changes it
    val inWindow = (LocalContext.current as? Activity)?.isInMultiWindowMode == true
    if (inWindow) return WindowInsets.safeDrawing
    // A hidden bar's insets are not always nothing: the Lenovo reports the status bar's band,
    // hidden, because the window handle overlaps it. Only a bar on screen is kept clear of.
    var insets = WindowInsets.displayCutout.union(WindowInsets.ime)
    if (WindowInsets.areStatusBarsVisible) insets = insets.union(WindowInsets.statusBars)
    if (WindowInsets.areNavigationBarsVisible) insets = insets.union(WindowInsets.navigationBars)
    return insets
}

/**
 * The tabs, each a word and a drawn picture (never the picture alone). Script is shown only
 * while a Choufleur address is set, Cart only while the show has a cart ([shownTabs]).
 */
enum class RemoteTab(val key: String, val icon: GodotIcon, val laterKey: String?) {
    CUES("remote.tabs.cues", GodotIcon.LIST, "remote.tabs.cuesLater"),
    RUNNING("remote.tabs.running", GodotIcon.START, "remote.tabs.runningLater"),
    SURFACE("remote.tabs.surface", GodotIcon.SURFACE, "remote.tabs.surfaceLater"),
    CART("remote.tabs.cart", GodotIcon.SAMPLER, null),
    MAPPING("remote.tabs.mapping", GodotIcon.VIDEO, "remote.tabs.mappingLater"),
    SCRIPT("remote.tabs.script", GodotIcon.PROMPT, null),
    MACHINES("remote.tabs.machines", GodotIcon.MACHINE, null),
}

/**
 * The tabs in the row: Script only while there is a Choufleur page to show, Cart only while
 * the show on screen has a cart (Go.dot's namespace draft §74).
 */
fun shownTabs(script: Boolean, cart: Boolean = false): List<RemoteTab> =
    RemoteTab.entries.filter { (it != RemoteTab.SCRIPT || script) && (it != RemoteTab.CART || cart) }

/** The tab to show: [picked], unless it is no longer in the row - the Connections tab then, where it was cleared. */
fun tabToShow(picked: RemoteTab, script: Boolean, cart: Boolean = false): RemoteTab =
    if (picked in shownTabs(script, cart)) picked else RemoteTab.MACHINES

/**
 * THE APP'S SHELL: the transport header always at the top, whatever the tab; the tab row
 * under it; and the tab. Every tab and the header read the mirror of the machine ON
 * SCREEN; only the Machines tab reads the others.
 */
@Composable
fun RemoteApp(service: EngineLinkService?, initialTab: RemoteTab? = null) {
    Box(
        Modifier
            .fillMaxSize()
            .background(GodotColors.ground)
            .windowInsetsPadding(appInsets())
    ) {
        if (service == null) {
            Text(loc("remote.screen.starting"), color = GodotColors.inkFaint, modifier = Modifier.padding(24.dp))
            return@Box
        }
        val onScreen by service.onScreen.collectAsState()
        val links by service.links.collectAsState()
        val machines = remember(service) { ServiceMachines(service) }
        val context = LocalContext.current
        val prompt = remember(context) { PromptStore.shared(context) }
        val promptAddress by prompt.address.collectAsState()
        // Cleared: the page and its connection go with the tab.
        LaunchedEffect(promptAddress) { if (promptAddress == null) PromptPage.sharedOrNull()?.drop() }
        val connections = rememberConnectionMarks(
            links, onScreen, promptAddress,
            if (promptAddress != null) PromptPage.shared(context) else null,
            BuzzerLink.shared(context),
        )
        // The Cart tab in the row while the show on screen has a cart (namespace draft §74).
        val cartShown = rememberReading(onScreen?.link, false) { hasCart(it) }
        Shell(
            onScreen = onScreen,
            initialTab = initialTab,
            connections = connections,
            header = { TransportHeader(onScreen) },
            machinesTab = { MachinesTab(machines, rememberSystemFindMe(), prompt, BuzzerLink.shared(context)) },
            runningTab = { RunningTab(onScreen) },
            cuesTab = { CuesTab(onScreen) },
            surfaceTab = { SurfaceTab(onScreen) },
            cartTab = if (cartShown) ({ CartTab(onScreen) }) else null,
            scriptTab = promptAddress?.let { address -> { ScriptTab(address, PromptPage.shared(context)) } },
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
    surfaceTab: (@Composable () -> Unit)? = null,
    /** The show's carts; null while it has none, and the Cart tab is then not in the row. */
    cartTab: (@Composable () -> Unit)? = null,
    /** Choufleur's page; null when no address is set, and the Script tab is then not in the row. */
    scriptTab: (@Composable () -> Unit)? = null,
    /** The connections shown at the end of the tab bar ([connectionMarks]); a tap on them opens the Connections tab. */
    connections: List<ConnectionMark> = emptyList(),
) {
    var picked by rememberSaveable { mutableStateOf(initialTab ?: if (onScreen == null) RemoteTab.MACHINES else RemoteTab.CUES) }
    val tab = tabToShow(picked, scriptTab != null, cartTab != null)
    Column(
        Modifier
            .fillMaxSize()
            .background(GodotColors.ground)
    ) {
        header()
        TabStrip(tab, shownTabs(scriptTab != null, cartTab != null), connections) { picked = it }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (tab) {
                RemoteTab.MACHINES -> machinesTab()
                RemoteTab.MAPPING -> MappingTab(onScreen)
                RemoteTab.RUNNING -> runningTab?.invoke() ?: LaterTab(tab, onScreen)
                RemoteTab.CUES -> cuesTab?.invoke() ?: LaterTab(tab, onScreen)
                RemoteTab.SURFACE -> surfaceTab?.invoke() ?: LaterTab(tab, onScreen)
                RemoteTab.CART -> cartTab?.invoke()
                RemoteTab.SCRIPT -> scriptTab?.invoke()
            }
        }
    }
}

/**
 * The tab row: a word and a picture each, at least a finger's height; the tab shown is
 * lit in the picked colour AND marked by a bar under it. At its right end, the connections
 * ([ConnectionStrip]); a tap there opens the Connections tab.
 */
@Composable
private fun TabStrip(current: RemoteTab, tabs: List<RemoteTab>, connections: List<ConnectionMark>, onPick: (RemoteTab) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(GodotColors.titleBar)
    ) {
        // Side by side when they fit, the connections keeping a finger's width; on a phone,
        // each an equal share of the width, its word under its picture.
        val stacked = maxWidth < 640.dp + 120.dp * (tabs.size - 5) + (if (connections.isEmpty()) 0.dp else 64.dp)
        // An upright tablet: the tabs closer, so the connections keep room at the end.
        val tight = maxWidth < 1000.dp
        Row(Modifier.fillMaxWidth()) {
            for (each in tabs) {
                val selected = each == current
                val ink = if (selected) GodotColors.ink else GodotColors.inkFaint
                val picture = if (selected) GodotColors.picked else GodotColors.inkFaint
                val weight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                Column(
                    (if (stacked) Modifier.weight(1f) else Modifier.width(IntrinsicSize.Max).widthIn(min = if (tight) 108.dp else 132.dp))
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
                                .padding(horizontal = if (tight) 10.dp else 16.dp),
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
            if (connections.isNotEmpty()) {
                ConnectionStrip(
                    connections, compact = stacked,
                    modifier = (if (stacked) Modifier else Modifier.weight(1f)).height(if (stacked) 60.dp else GodotMetrics.touch + 8.dp)
                ) { onPick(RemoteTab.MACHINES) }
            }
        }
    }
}

/** A tab to come: says so. */
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
        // How the link stands is the header's to say.
        if (onScreen == null) Text(loc("remote.tabs.noMachine"), color = GodotColors.inkFaint, textAlign = TextAlign.Center)
    }
}
