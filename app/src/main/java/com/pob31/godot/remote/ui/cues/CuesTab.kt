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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pob31.godot.remote.link.Link
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.link.LinkedMachine
import com.pob31.godot.remote.link.WireText
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.CueActions
import com.pob31.godot.remote.model.CueMarks
import com.pob31.godot.remote.model.CueRow
import com.pob31.godot.remote.model.CueTreeCache
import com.pob31.godot.remote.model.CuesReading
import com.pob31.godot.remote.model.Folds
import com.pob31.godot.remote.model.LinkWrites
import com.pob31.godot.remote.model.Sent
import com.pob31.godot.remote.model.SurfaceTables
import com.pob31.godot.remote.model.TabWrites
import com.pob31.godot.remote.model.readCues
import com.pob31.godot.remote.ui.GodotIconMark
import com.pob31.godot.remote.ui.LinkMark
import com.pob31.godot.remote.ui.rememberReading
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotMetrics
import com.pob31.godot.remote.ui.theme.GodotType
import com.pob31.godot.remote.ui.widgets.Mark
import com.pob31.godot.remote.ui.widgets.MarkIcon
import com.pob31.godot.remote.ui.widgets.rememberSurfaceTables
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val offState: StateFlow<LinkState> = MutableStateFlow(LinkState.OFF)

/**
 * THE CUES TAB (A.6): one cue list of the machine on screen, its groups' sections and
 * folds, and the inspector of the cue picked here. Picking is this tablet's - it never
 * moves the standby (PRD §3.5); the gutter parks it; a long press offers Fire now. Nothing
 * here makes, deletes, moves, renames or renumbers a cue (§68, ANO).
 */
@Composable
fun CuesTab(onScreen: LinkedMachine?) {
    val link = onScreen?.link
    val state by (link?.state ?: offState).collectAsState()
    val writes = remember(link) { link?.let { LinkWrites(it) } ?: TabWrites.NONE }
    val tables = rememberSurfaceTables()
    val folds = remember(link) { Folds() }
    val cache = remember(link) { CueTreeCache() }
    var viewed by rememberSaveable { mutableStateOf<String?>(null) }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var foldVersion by remember { mutableLongStateOf(0L) }
    // Read again at once when the list, the pick or a fold changes here, not only at the next tick.
    val reading = key(viewed, picked, foldVersion) {
        rememberReading(link, CuesReading()) { readCues(it, viewed, picked, folds, cache, tables) }
    }
    CuesView(
        reading, state, writes, tables, picked,
        hasMachine = onScreen != null,
        onView = { viewed = it },
        onPick = { picked = it },
        onFold = { folds.toggle(it); foldVersion = folds.version },
        link = link,
    )
}

/** The tab drawn from a reading - no link of its own, so a test draws and presses it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuesView(
    reading: CuesReading,
    state: LinkState,
    writes: TabWrites,
    tables: SurfaceTables,
    picked: String?,
    hasMachine: Boolean = true,
    onView: (String) -> Unit = {},
    onPick: (String?) -> Unit = {},
    onFold: (String) -> Unit = {},
    inspectorOpen: Boolean = false,
    link: Link? = null,
) {
    var sheetFor by remember { mutableStateOf<String?>(null) }
    var inspector by remember { mutableStateOf(inspectorOpen) }
    var notSent by remember { mutableIntStateOf(0) }
    val send: (Sent) -> Unit = { if (!writes.send(it)) notSent++ }
    val live = state == LinkState.LIVE
    val isGoList = reading.listId.isNotEmpty() && reading.listId == reading.focus

    BoxWithConstraints(Modifier.fillMaxSize().background(GodotColors.ground)) {
        val wide = maxWidth >= 900.dp
        Column(Modifier.fillMaxSize()) {
            ListBar(reading, state, hasMachine, isGoList, notSent > 0, onView) { send(CueActions.focus(reading.listId)) }
            if (!hasMachine) {
                Text(loc("remote.tabs.noMachine"), color = GodotColors.inkFaint, modifier = Modifier.padding(24.dp))
                return@Column
            }
            Row(Modifier.fillMaxSize().alpha(if (live) 1f else 0.45f)) {
                CueList(
                    reading, picked, isGoList, wide,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onPick = { id ->
                        onPick(id)
                        if (!wide) inspector = true
                    },
                    onPark = { send(CueActions.park(it)) },
                    onHold = { sheetFor = it },
                    onFold = onFold,
                )
                if (wide) {
                    Box(
                        Modifier
                            .width(500.dp)
                            .fillMaxHeight()
                            .background(GodotColors.panelInspect)
                    ) {
                        InspectorView(reading.inspection, writes, tables, link = link)
                    }
                }
            }
        }
    }

    if (inspector && picked != null) {
        ModalBottomSheet(
            onDismissRequest = { inspector = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = GodotColors.panelInspect,
        ) { InspectorView(reading.inspection, writes, tables, link = link) }
    }

    val held = sheetFor?.let { id -> reading.rows.firstOrNull { it.id == id && !it.band } }
    if (held != null) {
        ModalBottomSheet(onDismissRequest = { sheetFor = null }, containerColor = GodotColors.panelHigh) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                CueSheet(
                    held, reading.marks, isGoList,
                    onSend = { send(it); sheetFor = null },
                    onFlag = { flag -> writes.setNow(CueActions.flagAddress(held.id), WireText.bool(flag)); sheetFor = null },
                    onClose = { sheetFor = null },
                )
            }
        }
    }
}

/**
 * THE LISTS, BY NAME: the one on screen lit, the GO list marked "GO list" in words. Looking
 * at a list is not making it the GO list - a button says so and does it (`list.focus`).
 */
@Composable
private fun ListBar(reading: CuesReading, state: LinkState, hasMachine: Boolean, isGoList: Boolean, notSent: Boolean, onView: (String) -> Unit, onFocus: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(GodotColors.panel).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(reading.lists, key = { it.id }) { list ->
                    val on = list.id == reading.listId
                    Row(
                        Modifier
                            .heightIn(min = GodotMetrics.touch)
                            .background(if (on) GodotColors.panelHigh else GodotColors.panelIn, RoundedCornerShape(6.dp))
                            .border(2.dp, if (on) GodotColors.picked else GodotColors.rule, RoundedCornerShape(6.dp))
                            .clickable { onView(list.id) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(list.name.ifEmpty { list.id }, color = if (on) GodotColors.ink else GodotColors.inkDim, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                        if (list.id == reading.focus) {
                            Spacer(Modifier.width(8.dp))
                            GoListBadge()
                        }
                    }
                }
            }
            if (hasMachine && state != LinkState.LIVE) LinkMark(state)
        }
        if (notSent) Text(loc("running.notSent"), color = GodotColors.failed)
        if (hasMachine && reading.listId.isNotEmpty() && !isGoList) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onFocus,
                    colors = ButtonDefaults.buttonColors(containerColor = GodotColors.go, contentColor = GodotColors.goInk),
                    modifier = Modifier.heightIn(min = GodotMetrics.touch)
                ) { Text(loc("cues.makeGoList"), fontWeight = FontWeight.SemiBold) }
                Text(loc("cues.notGoList"), color = GodotColors.inkFaint, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GoListBadge() {
    Text(
        loc("cues.goList"), color = GodotColors.goInk, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
        modifier = Modifier.background(GodotColors.go, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** The column heads: the time columns named, so an empty cell can be read (didi.js). */
@Composable
private fun ColumnHeads(wide: Boolean) {
    Row(Modifier.fillMaxWidth().background(GodotColors.panelIn).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(gutter(wide)))
        Text(loc("cues.column.cue"), color = GodotColors.inkFaint, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
        if (wide) {
            for (key in listOf("cues.column.preWait", "cues.column.duration", "cues.column.postWait")) {
                Text(loc(key), color = GodotColors.inkFaint, fontSize = 13.sp, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(timeWidth(wide)))
            }
        } else {
            Text(loc("cues.column.duration"), color = GodotColors.inkFaint, fontSize = 13.sp, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(timeWidth(wide)))
        }
        Spacer(Modifier.width(if (wide) 12.dp else 8.dp))
    }
}

/** The row's columns: wider on a tablet, tighter on a phone so a name keeps room. */
private fun gutter(wide: Boolean) = if (wide) 52.dp else 44.dp
private fun timeWidth(wide: Boolean) = if (wide) 84.dp else 60.dp
private fun indent(wide: Boolean) = if (wide) 22 else 12

@Composable
private fun CueList(
    reading: CuesReading,
    picked: String?,
    isGoList: Boolean,
    wide: Boolean,
    modifier: Modifier,
    onPick: (String) -> Unit,
    onPark: (String) -> Unit,
    onHold: (String) -> Unit,
    onFold: (String) -> Unit,
) {
    Column(modifier) {
        ColumnHeads(wide)
        if (reading.rows.isEmpty()) {
            Text(loc(if (reading.lists.isEmpty()) "cues.noLists" else "cues.empty"), color = GodotColors.inkFaint, modifier = Modifier.padding(24.dp))
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(reading.rows, key = { it.id }) { row ->
                if (row.band) BandRow(row, wide, onFold)
                else CueRowView(row, reading.marks, picked == row.id, isGoList, wide, onPick, onPark, onHold, onFold)
            }
        }
    }
}

/** A section's head: its twist, its word and how many it frames - three tellings, none of them a colour. */
@Composable
private fun BandRow(row: CueRow, wide: Boolean, onFold: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = GodotMetrics.touch)
            .background(GodotColors.panelSection)
            .clickable { onFold(row.foldKey) }
            .padding(start = gutter(wide) + (row.depth * indent(wide)).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarkIcon(if (row.shut) Mark.TWIST_SHUT else Mark.TWIST_OPEN, GodotColors.inkDim, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(row.sectionKey?.let { loc(it) } ?: row.name, color = GodotColors.inkDim, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(loc("cues.count", "count" to row.count.toString()), color = GodotColors.inkFaint)
    }
}

/**
 * A CUE'S ROW: the gutter (the standby's arrow; a tap parks the GO list's standby here,
 * or on the group a header's line stands for), the twist of a group, the kind's picture
 * in its accent, number, name, the flag, the switch words, the times. A tap picks it - the
 * inspector's cue, nothing sent; a long press offers Fire now, Park here, Flag.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CueRowView(
    row: CueRow,
    marks: CueMarks,
    picked: Boolean,
    isGoList: Boolean,
    wide: Boolean,
    onPick: (String) -> Unit,
    onPark: (String) -> Unit,
    onHold: (String) -> Unit,
    onFold: (String) -> Unit,
) {
    val standby = marks.standby == row.id
    val override = marks.overrides[row.id]
    val runs = override == "on" || (override != "off" && row.enabled)
    val ground = when {
        picked -> GodotColors.panelHigh
        standby -> GodotColors.standby.copy(alpha = 0.12f)
        row.section != com.pob31.godot.remote.model.Section.MEMBER -> GodotColors.panelSectionCue
        else -> GodotColors.panel
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(ground)
            .combinedClickable(onClick = { onPick(row.id) }, onLongClick = { onHold(row.id) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The gutter: where the standby stands, and where a tap parks it.
        val parks = row.parksOn.isNotEmpty() && isGoList
        Box(
            Modifier
                .width(gutter(wide))
                .heightIn(min = 56.dp)
                .then(if (parks) Modifier.clickable { onPark(row.parksOn) } else Modifier)
                .semantics { contentDescription = if (standby) "standby" else "" },
            contentAlignment = Alignment.Center
        ) {
            if (standby) MarkIcon(Mark.STANDBY, GodotColors.standby, size = 26.dp)
            else if (parks) Box(Modifier.size(8.dp).background(GodotColors.rule, RoundedCornerShape(4.dp)))
        }
        // The picked row's bar, beside the colour that would otherwise be its only telling.
        Box(Modifier.width(4.dp).height(40.dp).background(if (picked) GodotColors.picked else Color.Transparent))
        Spacer(Modifier.width((row.depth * indent(wide)).dp + 4.dp))
        // The twist's place is kept on every row, so names stand in a column at each depth.
        Box(
            Modifier.size(if (wide) GodotMetrics.touch else 40.dp).then(if (row.isGroup) Modifier.clickable { onFold(row.foldKey) } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (row.isGroup) MarkIcon(if (row.shut) Mark.TWIST_SHUT else Mark.TWIST_OPEN, GodotColors.inkDim, size = 20.dp)
        }
        cueColour(row.colour)?.let {
            Box(Modifier.width(5.dp).height(26.dp).background(it, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(6.dp))
        }
        val accent = GodotColors.kindAccent(row.kind, row.mode)
        GodotIconMark(row.icon, if (runs) accent else GodotColors.inkOff, size = 24.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            row.number, color = if (standby) GodotColors.standby else GodotColors.ink, style = GodotType.number.copy(fontSize = 17.sp),
            maxLines = 1, modifier = if (wide) Modifier.width(72.dp) else Modifier.widthIn(min = 40.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.name, color = when {
                    !runs -> GodotColors.inkOff
                    standby -> GodotColors.standby
                    else -> GodotColors.ink
                },
                fontSize = 17.sp, fontWeight = if (standby || picked) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val words = buildList {
                if (row.shut && row.isGroup) add(loc("cues.shut", "count" to row.count.toString()) to GodotColors.inkFaint)
                when (override) {
                    "on" -> add(loc("cues.overrideOn") to GodotColors.live)
                    "off" -> add(loc("cues.overrideOff") to GodotColors.stopping)
                    else -> if (!row.enabled) add(loc("cues.disabled") to GodotColors.inkFaint)
                }
            }
            if (words.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((text, colour) in words) Text(text, color = colour, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
        if (row.id in marks.flagged) {
            MarkIcon(Mark.FLAG, GodotColors.flag, size = 20.dp, modifier = Modifier.semantics { contentDescription = "flagged" })
            Spacer(Modifier.width(8.dp))
        }
        if (wide) {
            TimeCell(row.preWait, wide)
            TimeCell(row.duration, wide)
            TimeCell(row.postWait, wide)
        } else {
            TimeCell(row.duration, wide)
        }
        Spacer(Modifier.width(if (wide) 12.dp else 8.dp))
    }
}

@Composable
private fun TimeCell(text: String, wide: Boolean) {
    Text(
        text, color = if (text == "0.0") GodotColors.inkOff else GodotColors.inkDim, style = GodotType.number,
        textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(timeWidth(wide))
    )
}

/** A cue's own decoration (`#rrggbb`), drawn as a small tab and never as the only telling. */
private fun cueColour(text: String): Color? {
    if (text.length != 7 || text[0] != '#') return null
    val rgb = text.substring(1).toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}

/**
 * A CUE'S SHEET, from a long press: Fire now (the standby stays where it is), Park the
 * standby here (only on the GO list - the gutter's tap, without the aim), Flag or Unflag.
 */
@Composable
fun CueSheet(row: CueRow, marks: CueMarks, isGoList: Boolean, onSend: (Sent) -> Unit, onFlag: (Boolean) -> Unit, onClose: () -> Unit) {
    val flagged = row.id in marks.flagged
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GodotIconMark(row.icon, GodotColors.kindAccent(row.kind, row.mode), size = 28.dp)
            Spacer(Modifier.width(10.dp))
            if (row.number.isNotEmpty()) {
                Text(row.number, color = GodotColors.ink, style = GodotType.number.copy(fontSize = 20.sp))
                Spacer(Modifier.width(10.dp))
            }
            Text(row.name, color = GodotColors.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SheetButton(loc("cues.fireNow"), GodotColors.go, GodotColors.goInk) { onSend(CueActions.fire(row.id)) }
        if (row.parksOn.isNotEmpty()) {
            if (isGoList) SheetButton(loc("cues.parkHere"), GodotColors.standby, Color.Black) { onSend(CueActions.park(row.parksOn)) }
            else Text(loc("cues.parkOnlyGoList"), color = GodotColors.inkFaint)
        }
        SheetButton(loc(if (flagged) "cues.unflag" else "cues.flag"), GodotColors.panel, GodotColors.ink, mark = Mark.FLAG) { onFlag(!flagged) }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = GodotMetrics.touch)) {
            Text(loc("running.close"), color = GodotColors.inkDim)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SheetButton(text: String, ground: Color, ink: Color, mark: Mark? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = ground, contentColor = ink),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        mark?.let {
            MarkIcon(it, GodotColors.flag, size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}
