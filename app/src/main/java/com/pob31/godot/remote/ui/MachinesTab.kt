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

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pob31.godot.remote.findme.FindMe
import com.pob31.godot.remote.link.EngineLinkService
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.link.LinkedMachine
import com.pob31.godot.remote.link.Machine
import com.pob31.godot.remote.link.currentIpAddresses
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.localization.locStatic
import com.pob31.godot.remote.model.PairCommand
import com.pob31.godot.remote.model.PairReading
import com.pob31.godot.remote.model.readPair
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotMetrics
import com.pob31.godot.remote.ui.theme.GodotType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.pob31.godot.remote.localization.LocalLanguage
import com.pob31.godot.remote.media.ShowFolder
import com.pob31.godot.remote.media.TimbreCache
import com.pob31.godot.remote.ui.waveform.LocalTimbreCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** What the Machines tab needs of the service: a seam so a screenshot test can hand it a fixture. */
interface MachinesModel {
    val machines: StateFlow<List<Machine>>
    val links: StateFlow<List<LinkedMachine>>
    val onScreen: StateFlow<LinkedMachine?>
    val tabletName: StateFlow<String>
    val installId: String

    /** Why Android refused to keep the link in the background, if it did. */
    val foregroundProblem: StateFlow<String?>

    fun addMachine(name: String, host: String, port: Int)
    fun editMachine(oldKey: String, name: String, host: String, port: Int): Boolean
    fun removeMachine(key: String)
    fun setLinked(key: String, linked: Boolean)
    fun putOnScreen(key: String)
    fun setTabletName(name: String)
}

/** The service as the Machines tab sees it. */
class ServiceMachines(private val service: EngineLinkService) : MachinesModel {
    override val machines get() = service.machines
    override val links get() = service.links
    override val onScreen get() = service.onScreen
    override val tabletName get() = service.tabletName
    override val installId get() = service.installId
    override val foregroundProblem get() = service.foregroundProblem
    override fun addMachine(name: String, host: String, port: Int) = service.addMachine(name, host, port)
    override fun editMachine(oldKey: String, name: String, host: String, port: Int) = service.editMachine(oldKey, name, host, port)
    override fun removeMachine(key: String) = service.removeMachine(key)
    override fun setLinked(key: String, linked: Boolean) = service.setLinked(key, linked)
    override fun putOnScreen(key: String) = service.putOnScreen(key)
    override fun setTabletName(name: String) = service.setTabletName(name)
}

private val offState: StateFlow<LinkState> = MutableStateFlow(LinkState.OFF)
private val noProblem: StateFlow<String?> = MutableStateFlow(null)

/**
 * THE MACHINES TAB (A.3): the machines this tablet remembers - typed, never discovered
 * (ANN) - which are linked, which one is on screen, how each link stands and why it fell;
 * per machine its seat in a pair and the pair's buttons, each sent to THAT machine (§8);
 * this tablet's own name, addresses and id; and the find-me alarm's readiness.
 * Two columns when wide, one when narrow.
 */
@Composable
fun MachinesTab(model: MachinesModel, findMe: FindMeAccess) {
    val machines by model.machines.collectAsState()
    val links by model.links.collectAsState()
    val onScreen by model.onScreen.collectAsState()
    var editing by remember { mutableStateOf<Machine?>(null) }
    var removing by remember { mutableStateOf<Machine?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val machinesColumn: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Heading(loc("remote.machines.title"))
                if (machines.isEmpty()) Text(loc("remote.machines.none"), color = GodotColors.inkFaint)
                for (machine in machines) {
                    MachineCard(
                        machine = machine,
                        linked = links.firstOrNull { it.machine.key == machine.key },
                        isOnScreen = onScreen?.machine?.key == machine.key,
                        model = model,
                        onEdit = { editing = machine },
                        onRemove = { removing = machine },
                    )
                }
                AddMachine(model)
            }
        }
        val tabletColumn: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val foregroundProblem by model.foregroundProblem.collectAsState()
                foregroundProblem?.let { Text(loc("remote.screen.foregroundRefused", "problem" to it), color = GodotColors.failed) }
                ThisTablet(model)
                FindMeSection(findMe)
                ShowFoldersSection()
            }
        }
        if (wide) {
            Row(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                machinesColumn(Modifier.weight(3f))
                tabletColumn(Modifier.weight(2f))
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                machinesColumn(Modifier.fillMaxWidth())
                tabletColumn(Modifier.fillMaxWidth())
            }
        }
    }

    editing?.let { machine ->
        MachineDialog(machine, onDismiss = { editing = null }) { name, host, port ->
            model.editMachine(machine.key, name, host, port).also { if (it) editing = null }
        }
    }
    removing?.let { machine ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(loc("remote.machines.removeTitle", "machine" to machine.label)) },
            text = { Text(loc("remote.machines.removeText")) },
            confirmButton = {
                TextButton(onClick = { model.removeMachine(machine.key); removing = null }) {
                    Text(loc("remote.machines.remove"), color = GodotColors.failed)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(loc("remote.machines.cancel")) } },
            containerColor = GodotColors.panelHigh,
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = GodotColors.ink, fontWeight = FontWeight.SemiBold)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MachineCard(
    machine: Machine,
    linked: LinkedMachine?,
    isOnScreen: Boolean,
    model: MachinesModel,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val link = linked?.link
    val state by (link?.state ?: offState).collectAsState()
    val problem by (link?.problem ?: noProblem).collectAsState()
    val origin by (link?.origin ?: noProblem).collectAsState()
    val pair = rememberReading(link, PairReading.UNPAIRED) { readPair(it) }

    Surface(
        color = GodotColors.panel,
        shape = RoundedCornerShape(6.dp),
        border = if (isOnScreen) BorderStroke(2.dp, GodotColors.picked) else BorderStroke(1.dp, GodotColors.rule),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GodotIconMark(com.pob31.godot.remote.model.GodotIcon.MACHINE, GodotColors.inkDim, size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        machine.label, style = MaterialTheme.typography.titleMedium, color = GodotColors.ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(machine.key, style = GodotType.number, color = GodotColors.inkFaint)
                }
                if (isOnScreen) {
                    Text(
                        loc("remote.machines.onScreen"), color = GodotColors.picked, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                } else if (link != null) {
                    OutlinedButton(
                        onClick = { model.putOnScreen(machine.key) },
                        modifier = Modifier.heightIn(min = GodotMetrics.touch)
                    ) { Text(loc("remote.machines.putOnScreen"), color = GodotColors.ink) }
                }
            }

            LinkMark(state)
            if (state != LinkState.LIVE && state != LinkState.OFF && state != LinkState.TOO_OLD) {
                problem?.let { Text(loc("remote.screen.lastProblem", "problem" to it), color = GodotColors.inkFaint) }
            }
            if (state == LinkState.TOO_OLD) Text(loc("remote.machines.tooOld"), color = GodotColors.inkDim)
            if (link != null && state != LinkState.TOO_OLD) {
                Text(
                    origin?.let { loc("remote.screen.knownAs", "origin" to it) } ?: loc("remote.screen.notKnownYet"),
                    color = GodotColors.inkOff
                )
            }

            if (link != null && pair.paired) {
                pair.seat()?.let { seat ->
                    Text(
                        say(seat), color = if (pair.state == "bothSending") GodotColors.failed else GodotColors.ink,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (pair.alarming) Text(pair.alarm, color = GodotColors.waiting)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (command in PairCommand.entries) {
                        val open = pair.offers(command)
                        val answers = pair.answer() == command
                        Button(
                            onClick = { link.command(command.command) },
                            enabled = open && state == LinkState.LIVE,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (answers) GodotColors.standby else GodotColors.panelHigh,
                                contentColor = if (answers) Color.Black else GodotColors.ink,
                                disabledContainerColor = GodotColors.panelIn,
                                disabledContentColor = GodotColors.inkOff,
                            ),
                            modifier = Modifier.heightIn(min = GodotMetrics.touch)
                        ) { Text(loc(command.key)) }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = machine.linked,
                    onCheckedChange = { model.setLinked(machine.key, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = GodotColors.live, checkedThumbColor = Color.Black)
                )
                Spacer(Modifier.width(10.dp))
                Text(loc(if (machine.linked) "remote.machines.linked" else "remote.machines.notLinked"), color = GodotColors.ink)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = GodotMetrics.touch)) {
                    Text(loc("remote.machines.edit"), color = GodotColors.inkDim)
                }
                TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = GodotMetrics.touch)) {
                    Text(loc("remote.machines.remove"), color = GodotColors.inkDim)
                }
            }
        }
    }
}

/** A machine's three fields: its name, its address, its port (5010 unless said). */
@Composable
private fun MachineFields(
    name: String, onName: (String) -> Unit,
    host: String, onHost: (String) -> Unit,
    port: String, onPort: (String) -> Unit,
) {
    OutlinedTextField(
        value = name, onValueChange = onName, singleLine = true,
        label = { Text(loc("remote.screen.name")) },
        modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = host, onValueChange = { onHost(it.trim()) }, singleLine = true,
            label = { Text(loc("remote.screen.host")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = port, onValueChange = { onPort(it.filter(Char::isDigit).take(5)) }, singleLine = true,
            label = { Text(loc("remote.screen.port")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = GodotType.number,
            modifier = Modifier.width(110.dp)
        )
    }
}

private fun portOf(text: String): Int? = text.toIntOrNull()?.takeIf { it in 1..65535 }

@Composable
private fun AddMachine(model: MachinesModel) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(Machine.DEFAULT_PORT.toString()) }
    Surface(color = GodotColors.panelIn, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(loc("remote.machines.add"), color = GodotColors.ink, fontWeight = FontWeight.SemiBold)
            MachineFields(name, { name = it }, host, { host = it }, port, { port = it })
            val portNumber = portOf(port)
            Button(
                onClick = {
                    if (portNumber != null && host.isNotBlank()) {
                        model.addMachine(name, host, portNumber)
                        name = ""
                        host = ""
                        port = Machine.DEFAULT_PORT.toString()
                    }
                },
                enabled = portNumber != null && host.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = GodotColors.standby, contentColor = Color.Black),
                modifier = Modifier.heightIn(min = GodotMetrics.touch)
            ) { Text(loc("remote.machines.addAndLink")) }
        }
    }
}

@Composable
private fun MachineDialog(machine: Machine, onDismiss: () -> Unit, onSave: (String, String, Int) -> Boolean) {
    var name by remember { mutableStateOf(machine.name) }
    var host by remember { mutableStateOf(machine.host) }
    var port by remember { mutableStateOf(machine.port.toString()) }
    var taken by remember { mutableStateOf(false) }
    val portNumber = portOf(port)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(loc("remote.machines.editTitle")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MachineFields(name, { name = it }, host, { host = it; taken = false }, port, { port = it; taken = false })
                if (taken) Text(loc("remote.machines.taken"), color = GodotColors.failed)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (portNumber != null && host.isNotBlank()) taken = !onSave(name, host, portNumber) },
                enabled = portNumber != null && host.isNotBlank()
            ) { Text(loc("remote.machines.save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(loc("remote.machines.cancel")) } },
        containerColor = GodotColors.panelHigh,
    )
}

@Composable
private fun ThisTablet(model: MachinesModel) {
    val current by model.tabletName.collectAsState()
    var name by remember(current) { mutableStateOf(current) }
    var addresses by remember { mutableStateOf(currentIpAddresses()) }
    LifecycleResumeEffect(Unit) {
        addresses = currentIpAddresses()
        onPauseOrDispose { }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading(loc("remote.tablet.title"))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.replace('\n', ' ').replace('\t', ' ') }, singleLine = true,
                label = { Text(loc("remote.tablet.name")) },
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 160.dp)
            )
            Button(
                onClick = { model.setTabletName(name) },
                enabled = name.isNotBlank() && name.trim() != current,
                colors = ButtonDefaults.buttonColors(containerColor = GodotColors.panelHigh, contentColor = GodotColors.ink),
                modifier = Modifier.heightIn(min = GodotMetrics.touch)
            ) { Text(loc("remote.tablet.rename")) }
        }
        Text(loc("remote.tablet.nameSaid"), color = GodotColors.inkFaint)
        Text(
            if (addresses.isEmpty()) loc("remote.screen.noAddress")
            else loc("remote.tablet.addresses", "addresses" to addresses.joinToString(", ")),
            color = GodotColors.inkDim
        )
        Text(loc("remote.tablet.installId", "id" to model.installId.take(8)), color = GodotColors.inkDim, style = GodotType.number)
    }
}

/** The system's switches the find-me alarm depends on, and a way to try it: a seam for the screenshot test. */
interface FindMeAccess {
    fun notificationsOn(): Boolean
    fun canShowOverLock(): Boolean
    fun openNotificationSettings()
    fun openOverLockSettings()
    fun test()
}

class SystemFindMe(private val context: Context) : FindMeAccess {
    override fun notificationsOn() = FindMe.notificationsOn(context)
    override fun canShowOverLock() = FindMe.canShowOverLock(context)
    override fun openNotificationSettings() = open(FindMe.notificationSettings(context))
    override fun openOverLockSettings() = open(FindMe.overLockSettings(context))
    override fun test() = FindMe.test(context, locStatic("remote.find.test"))

    private fun open(intent: android.content.Intent) {
        try {
            context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // No such settings page on this tablet: the words above still say what is missing.
        }
    }
}

@Composable
fun rememberSystemFindMe(): FindMeAccess {
    val context = LocalContext.current
    return remember(context) { SystemFindMe(context) }
}

@Composable
private fun FindMeSection(findMe: FindMeAccess) {
    var notificationsOn by remember { mutableStateOf(findMe.notificationsOn()) }
    var overLock by remember { mutableStateOf(findMe.canShowOverLock()) }
    // Back from the system's settings: read again.
    LifecycleResumeEffect(findMe) {
        notificationsOn = findMe.notificationsOn()
        overLock = findMe.canShowOverLock()
        onPauseOrDispose { }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading(loc("remote.find.title"))
        Text(loc("remote.find.explain"), color = GodotColors.inkDim)
        if (!notificationsOn) {
            Text(loc("remote.find.notificationsOff"), color = GodotColors.failed)
            OutlinedButton(onClick = findMe::openNotificationSettings, modifier = Modifier.heightIn(min = GodotMetrics.touch)) {
                Text(loc("remote.find.openNotificationSettings"), color = GodotColors.ink)
            }
        }
        if (!overLock) {
            Text(loc("remote.find.overLockOff"), color = GodotColors.stopping)
            OutlinedButton(onClick = findMe::openOverLockSettings, modifier = Modifier.heightIn(min = GodotMetrics.touch)) {
                Text(loc("remote.find.allowOverLock"), color = GodotColors.ink)
            }
        }
        Button(
            onClick = findMe::test,
            colors = ButtonDefaults.buttonColors(containerColor = GodotColors.panelHigh, contentColor = GodotColors.ink),
            modifier = Modifier.heightIn(min = GodotMetrics.touch)
        ) { Text(loc("remote.find.testButton")) }
    }
}

/**
 * SHOW FOLDERS (A.9; the author: "we can make folders for the various shows on the
 * Android app"): the waveforms fetched from Go.dot, kept on this tablet by show, each
 * folder with its size and how many sounds it keeps, and a Forget that asks first. Read off
 * the tablet's files on a background thread, again whenever the tab comes back.
 */
@Composable
private fun ShowFoldersSection() {
    val cache = LocalTimbreCache.current ?: TimbreCache.shared(LocalContext.current)
    val scope = rememberCoroutineScope()
    var folders by remember { mutableStateOf<List<ShowFolder>?>(null) }
    var round by remember { mutableIntStateOf(0) }
    var forgetting by remember { mutableStateOf<ShowFolder?>(null) }
    LifecycleResumeEffect(Unit) {
        round++
        onPauseOrDispose { }
    }
    LaunchedEffect(round) { folders = withContext(Dispatchers.IO) { cache.list() } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading(loc("folders.title"))
        Text(loc("folders.explain"), color = GodotColors.inkDim)
        val list = folders ?: return@Column
        if (list.isEmpty()) Text(loc("folders.none"), color = GodotColors.inkFaint)
        for (folder in list) {
            Surface(
                color = GodotColors.panel, shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, GodotColors.rule), modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    GodotIconMark(com.pob31.godot.remote.model.GodotIcon.MEDIA, GodotColors.inkDim, size = 24.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(folder.name, color = GodotColors.ink, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            loc(if (folder.sounds == 1) "folders.oneSound" else "folders.sounds", "count" to folder.sounds.toString()) +
                                " · " + sizeWords(folder.bytes),
                            color = GodotColors.inkFaint
                        )
                    }
                    TextButton(onClick = { forgetting = folder }, modifier = Modifier.heightIn(min = GodotMetrics.touch)) {
                        Text(loc("folders.forget"), color = GodotColors.inkDim)
                    }
                }
            }
        }
    }
    forgetting?.let { folder ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text(loc("folders.forgetTitle", "show" to folder.name)) },
            text = { Text(loc("folders.forgetText")) },
            confirmButton = {
                TextButton(onClick = {
                    forgetting = null
                    scope.launch {
                        withContext(Dispatchers.IO) { cache.forget(folder.name) }
                        round++
                    }
                }) { Text(loc("folders.forget"), color = GodotColors.failed) }
            },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text(loc("folders.cancel")) } },
            containerColor = GodotColors.panelHigh,
        )
    }
}

/** A size in the tablet's language: bytes, kilobytes to the tenth below ten, megabytes. */
@Composable
private fun sizeWords(bytes: Long): String {
    val locale = Locale.forLanguageTag(LocalLanguage.current)
    fun number(value: Double) = if (value < 10) String.format(locale, "%.1f", value) else String.format(locale, "%.0f", value)
    return when {
        bytes < 1024 -> loc("folders.bytes", "n" to bytes.toString())
        bytes < 1024 * 1024 -> loc("folders.kb", "n" to number(bytes / 1024.0))
        else -> loc("folders.mb", "n" to number(bytes / (1024.0 * 1024.0)))
    }
}
