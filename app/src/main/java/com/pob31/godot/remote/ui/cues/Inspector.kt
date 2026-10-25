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

package com.pob31.godot.remote.ui.cues

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pob31.godot.remote.link.Link
import com.pob31.godot.remote.link.WireText
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.DcaChoice
import com.pob31.godot.remote.model.EqReading
import com.pob31.godot.remote.model.FieldLine
import com.pob31.godot.remote.model.FieldText
import com.pob31.godot.remote.model.Inspection
import com.pob31.godot.remote.model.InspectorLine
import com.pob31.godot.remote.model.SendLine
import com.pob31.godot.remote.model.SurfaceTables
import com.pob31.godot.remote.model.TabWrites
import com.pob31.godot.remote.model.placeKnobs
import com.pob31.godot.remote.ui.GodotIconMark
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.waveform.Waveform
import com.pob31.godot.remote.ui.theme.GodotMetrics
import com.pob31.godot.remote.ui.theme.GodotType
import com.pob31.godot.remote.ui.widgets.DbFader
import com.pob31.godot.remote.ui.widgets.EqBandSwitch
import com.pob31.godot.remote.ui.widgets.EqKnobView
import com.pob31.godot.remote.ui.widgets.LiveWord
import com.pob31.godot.remote.ui.widgets.LockedWord
import com.pob31.godot.remote.ui.widgets.Mark
import com.pob31.godot.remote.ui.widgets.MarkIcon
import com.pob31.godot.remote.ui.widgets.NumberBox
import com.pob31.godot.remote.ui.widgets.SwapSwitch

/**
 * THE INSPECTOR (A.6): what the tablet may change on the picked cue, and nothing else -
 * the rows of [com.pob31.godot.remote.model.Editables], in that order, for those the cue
 * has; what each row IS comes from the mirror's node (its range, its unit, whether it may
 * be written). Under the show lock a row the show records is greyed and says "Locked";
 * the EQ and the sends keep moving, marked live while a locked show rides them, and the
 * count of what rides live is said at the top.
 */
@Composable
fun InspectorView(
    inspection: Inspection?,
    writes: TabWrites,
    tables: SurfaceTables,
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    link: Link? = null,
) {
    if (inspection == null) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(loc("cues.inspector.none"), color = GodotColors.inkFaint)
        }
        return
    }
    val cue = inspection.cue
    Column(
        modifier
            .fillMaxWidth()
            .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val accent = GodotColors.kindAccent(cue.kind, cue.mode)
            GodotIconMark(cue.icon, accent, size = 28.dp)
            Spacer(Modifier.width(10.dp))
            if (cue.number.isNotEmpty()) {
                Text(cue.number, color = GodotColors.ink, style = GodotType.number.copy(fontSize = 20.sp), maxLines = 1)
                Spacer(Modifier.width(10.dp))
            }
            Text(cue.name, color = GodotColors.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(10.dp))
            Text(cue.kindKey?.let { loc(it) } ?: cue.kind, color = accent, maxLines = 1)
        }
        // A sound's file as it sounds (PRD §3.30), fetched from the machine it plays on; its head only while it plays.
        if (link != null && inspection.hash.isNotEmpty()) {
            Waveform(inspection.hash, link, Modifier.fillMaxWidth().height(72.dp), playhead = inspection.playhead?.toFloat())
        }
        if (inspection.locked) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LockedWord()
                Text(loc("cues.inspector.lockedNote"), color = GodotColors.inkDim, modifier = Modifier.weight(1f))
            }
            if (inspection.liveCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiveWord()
                    Text(loc("cues.inspector.liveCount", "count" to inspection.liveCount.toString()), color = GodotColors.waiting, modifier = Modifier.weight(1f))
                }
            }
        }
        for (line in inspection.lines) InspectorLineView(line, writes, tables)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Heading(text: String, lockedOut: Boolean = false, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, color = GodotColors.inkDim, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        if (lockedOut) LockedWord()
        trailing()
    }
}

@Composable
private fun InspectorLineView(line: InspectorLine, writes: TabWrites, tables: SurfaceTables) {
    val label = loc(line.editable.labelKey)
    when (line) {
        is InspectorLine.Field -> FieldView(line, label, writes, tables)
        is InspectorLine.Enabled -> EnabledView(line, label, writes)
        is InspectorLine.RunTrim -> Section {
            Heading(label)
            DbFader(line.trim, line.address, writes, tables.fader, label = label, thickness = 56.dp)
        }
        is InspectorLine.Sends -> Section { SendsView(line.sends, label, writes, tables) }
        is InspectorLine.Eq -> Section { EqView(line.eq, label, writes, tables) }
        is InspectorLine.Dca -> Section { DcaView(line, label, writes, tables) }
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(GodotColors.panel, RoundedCornerShape(6.dp))
            .border(1.dp, GodotColors.rule, RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
private fun FieldView(line: InspectorLine.Field, label: String, writes: TabWrites, tables: SurfaceTables) {
    val field = line.field
    val each = line.editable
    Section {
        Heading(label, field.lockedOut)
        Box(Modifier.alpha(if (field.lockedOut) 0.5f else 1f)) {
            when (each.control) {
                com.pob31.godot.remote.model.EditControl.FADER ->
                    DbFader(field.number ?: tables.fader.silenceDb, field.address, writes, tables.fader, enabled = field.editable, label = label, thickness = 56.dp)
                com.pob31.godot.remote.model.EditControl.NUMBER ->
                    NumberBox(
                        shown = FieldText.shown(field.number, each.decimals, each.blankZero),
                        onSend = { writes.setNow(field.address, it) },
                        decimals = each.decimals,
                        blankZero = each.blankZero,
                        signed = (field.min ?: 0.0) < 0.0,
                        unit = unitWord(field.unit),
                        enabled = field.editable,
                        width = 120.dp,
                    )
                com.pob31.godot.remote.model.EditControl.CHOICE -> ChoiceView(field, writes)
                com.pob31.godot.remote.model.EditControl.SWITCH -> SwitchLine(
                    field.flag, field.editable,
                    words = if (field.flag) loc("cues.flagged") else loc("cues.notFlagged"),
                    mark = if (field.flag) Mark.FLAG else null,
                ) { writes.setNow(field.address, WireText.bool(it)) }
                com.pob31.godot.remote.model.EditControl.TEXT -> NotesView(field, writes)
                else -> Unit
            }
        }
    }
}

/** A unit as the engine spells it, said in the box's trailing word: "s", "dB", "Hz"; "×" for a speed. */
private fun unitWord(unit: String?): String? = when (unit) {
    null, "" -> null
    "x" -> "×"
    else -> unit
}

@Composable
private fun SwitchLine(on: Boolean, enabled: Boolean, words: String, mark: Mark? = null, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = GodotMetrics.touch)) {
        Switch(
            checked = on, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = GodotColors.live, checkedThumbColor = Color.Black)
        )
        Spacer(Modifier.width(12.dp))
        mark?.let {
            MarkIcon(it, GodotColors.flag, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(words, color = if (enabled) GodotColors.ink else GodotColors.inkOff)
    }
}

/** `enabled`, and what an enable or disable cue did to it for this run - shown, never written here. */
@Composable
private fun EnabledView(line: InspectorLine.Enabled, label: String, writes: TabWrites) {
    val field = line.field
    Section {
        Heading(label, field.lockedOut)
        Box(Modifier.alpha(if (field.lockedOut) 0.5f else 1f)) {
            SwitchLine(field.flag, field.editable, if (field.flag) loc("cues.enabled") else loc("cues.disabled")) {
                writes.setNow(field.address, WireText.bool(it))
            }
        }
        when (line.override) {
            "on" -> Text(loc("cues.overrideOn"), color = GodotColors.live)
            "off" -> Text(loc("cues.overrideOff"), color = GodotColors.stopping)
        }
    }
}

/** A closed set of words (the speed's mode), each a button that says itself; the one in force lit and marked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceView(field: FieldLine, writes: TabWrites) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (choice in field.choices) {
            val chosen = choice == field.text
            Row(
                Modifier
                    .heightIn(min = GodotMetrics.touch)
                    .background(if (chosen) GodotColors.picked else GodotColors.panelHigh, RoundedCornerShape(6.dp))
                    .clickable(enabled = field.editable && !chosen) { writes.setNow(field.address, choice) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (chosen) {
                    MarkIcon(Mark.DONE, Color.Black, size = 16.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(choice, color = if (chosen) Color.Black else GodotColors.ink)
            }
        }
    }
}

/** Notes, several lines; written when the box is left or saved, never letter by letter. */
@Composable
private fun NotesView(field: FieldLine, writes: TabWrites) {
    var text by remember(field.address) { mutableStateOf(field.text) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(field.text, focused) { if (!focused) text = field.text }
    val dirty = text != field.text
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            enabled = field.editable,
            minLines = 2,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                if (focused && !it.isFocused && text != field.text) writes.setNow(field.address, text)
                focused = it.isFocused
            }
        )
        if (dirty && field.editable) {
            TextButton(onClick = { writes.setNow(field.address, text) }, modifier = Modifier.heightIn(min = GodotMetrics.touch)) {
                Text(loc("cues.saveNotes"), color = GodotColors.picked)
            }
        }
    }
}

/**
 * A cue's sends, each its mix channel's name, a switch that says in or out, its level and
 * its place in the mix - a pan or a balance, a stereo send's width and its sides swapped
 * (namespace draft §113) - all ridden live under the lock.
 */
@Composable
private fun SendsView(sends: List<SendLine>, label: String, writes: TabWrites, tables: SurfaceTables) {
    Heading(label)
    if (sends.isEmpty()) {
        Text(loc("cues.noSends"), color = GodotColors.inkFaint)
        return
    }
    for (send in sends) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(send.busName, color = GodotColors.ink, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (send.live) {
                    LiveWord()
                    Spacer(Modifier.width(10.dp))
                }
                Switch(
                    checked = send.on, onCheckedChange = { writes.setNow(send.onAddress, WireText.bool(it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = GodotColors.live, checkedThumbColor = Color.Black)
                )
                Spacer(Modifier.width(8.dp))
                Text(loc(if (send.on) "widgets.in" else "widgets.out"), color = if (send.on) GodotColors.ink else GodotColors.inkFaint, modifier = Modifier.width(48.dp))
            }
            DbFader(send.level, send.levelAddress, writes, tables.fader, colour = if (send.on) GodotColors.fade else GodotColors.inkOff, label = send.busName, thickness = 48.dp)
            SendPlace(send, writes, tables)
        }
    }
}

/** One send's place, when its placement has one: its lean, and a stereo send's width and swap. */
@Composable
private fun SendPlace(send: SendLine, writes: TabWrites, tables: SurfaceTables) {
    val words = mapOf("pan" to loc("cues.place.pan"), "balance" to loc("cues.place.balance"), "width" to loc("cues.place.width"))
    val place = placeKnobs(listOf(send), words)
    if (place.isEmpty) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (knob in place.leans.knobs) EqKnobView(knob, place.leans, writes, tables, size = 84.dp, colour = GodotColors.fade)
        for (knob in place.widths.knobs) EqKnobView(knob, place.widths, writes, tables, size = 84.dp, colour = GodotColors.fade)
        for (swap in place.widths.switches) SwapSwitch(swap, null, writes)
    }
}

/**
 * THE EQ: in or out, the six switches (high-pass, four bands, low-pass) as words that say
 * in or out, and the sixteen controls of the surface tables as rotaries in the author's
 * order, each in its band's colour, greyed and said "out" when its band is switched out.
 * A turn is relative, finer away from the knob; a press does what the tables say.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqView(eq: EqReading, label: String, writes: TabWrites, tables: SurfaceTables) {
    Heading(label) {
        eq.on?.let { if (it.live) LiveWord() }
    }
    eq.on?.let { on ->
        SwitchLine(on.on, true, loc(if (on.on) "cues.eqIn" else "cues.eqOut")) { writes.setNow(on.address, WireText.bool(it)) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (switch in eq.switches) EqBandSwitch(switch, writes)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (knob in eq.knobs) EqKnobView(knob, eq, writes, tables)
    }
}

/** The DCA mark (a show row: locked under the lock) and the trim of the DCA it names (tonight's, never locked). */
@Composable
private fun DcaView(line: InspectorLine.Dca, label: String, writes: TabWrites, tables: SurfaceTables) {
    val field = line.field
    var open by remember { mutableStateOf(false) }
    Heading(label, field.lockedOut)
    Box {
        OutlinedButton(
            onClick = { open = true },
            enabled = field.editable,
            modifier = Modifier.heightIn(min = GodotMetrics.touch).widthIn(min = 200.dp)
        ) {
            val name = line.choices.firstOrNull { it.id == line.current }?.name ?: if (line.current.isEmpty()) loc("cues.dcaNone") else line.current
            Text(name, color = if (field.editable) GodotColors.ink else GodotColors.inkOff)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(loc("cues.dcaNone")) }, onClick = { open = false; writes.setNow(field.address, "") })
            for (choice: DcaChoice in line.choices) {
                DropdownMenuItem(text = { Text(choice.name) }, onClick = { open = false; writes.setNow(field.address, choice.id) })
            }
        }
    }
    val trimAddress = line.trimAddress
    if (trimAddress != null && line.trim != null) {
        val name = line.choices.firstOrNull { it.id == line.current }?.name ?: line.current
        Text(loc("cues.dcaTrim", "dca" to name), color = GodotColors.inkFaint, fontSize = 13.sp)
        DbFader(line.trim, trimAddress, writes, tables.fader, colour = GodotColors.standby, label = name, thickness = 48.dp)
    }
}
