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

package com.pob31.godot.remote.ui.mapping

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.link.LinkedMachine
import com.pob31.godot.remote.link.WireText
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.mapping.HeldWarps
import com.pob31.godot.remote.mapping.LinkMappingWrites
import com.pob31.godot.remote.mapping.MappingBlock
import com.pob31.godot.remote.mapping.MappingReading
import com.pob31.godot.remote.mapping.MappingWrites
import com.pob31.godot.remote.mapping.NudgeDirection
import com.pob31.godot.remote.mapping.NudgeStep
import com.pob31.godot.remote.mapping.OutputRow
import com.pob31.godot.remote.mapping.PointDrag
import com.pob31.godot.remote.mapping.Warp
import com.pob31.godot.remote.mapping.WarpDragSession
import com.pob31.godot.remote.mapping.WarpLayer
import com.pob31.godot.remote.mapping.mappingBlock
import com.pob31.godot.remote.mapping.nudged
import com.pob31.godot.remote.mapping.readMapping
import com.pob31.godot.remote.mapping.regridWrites
import com.pob31.godot.remote.mapping.regridded
import com.pob31.godot.remote.model.GodotIcon
import com.pob31.godot.remote.model.TypedNumber
import com.pob31.godot.remote.ui.GodotIconMark
import com.pob31.godot.remote.ui.rememberReading
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotMetrics
import com.pob31.godot.remote.ui.theme.GodotType
import java.util.Locale

/** What is picked when the tab opens: for the screenshots, which open on a point. */
data class MappingPick(val output: String? = null, val layer: Int = 0, val point: Int? = null)

/** The pick the tab opens on; nothing but the screenshots provides one. */
val LocalMappingPick = compositionLocalOf { MappingPick() }

/**
 * THE MAPPING TAB (A.8; PRD §3.17, "video mapping at the projection surface - walk to the
 * wall"): the machine on screen's video outputs, each output's warps drawn as the renderer
 * bends them, a point dragged or nudged, the grid split, a zone's opacity and blend,
 * Identify to find which projector is which, and the alignment grid drawn on the projector
 * through its warp to set the geometry by. Nothing structural: no zone made or taken
 * away (§68.2, ANO).
 */
@Composable
fun MappingTab(onScreen: LinkedMachine?) {
    if (onScreen == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(loc("remote.tabs.noMachine"), color = GodotColors.inkFaint, textAlign = TextAlign.Center)
        }
        return
    }
    val link = onScreen.link
    val reading = rememberReading(link, MappingReading()) { readMapping(it) }
    val state by link.state.collectAsState()
    val writes = remember(link) { LinkMappingWrites(link) }
    MappingView(reading, state, writes, LocalMappingPick.current)
}

/** The tab drawn from a reading and a link's state - no link of its own, so a test draws it. */
@Composable
fun MappingView(
    reading: MappingReading,
    linkState: LinkState,
    writes: MappingWrites,
    initial: MappingPick = MappingPick(),
    nowMs: () -> Long = SystemClock::uptimeMillis,
) {
    if (reading.outputs.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GodotIconMark(GodotIcon.VIDEO, GodotColors.inkOff, size = 48.dp)
            Text(loc("mapping.noOutputs"), color = GodotColors.inkFaint, textAlign = TextAlign.Center)
        }
        return
    }

    var outputId by rememberSaveable { mutableStateOf(initial.output) }
    val output = reading.output(outputId) ?: return
    val layerOf = remember { mutableStateMapOf<String, Int>().apply { initial.output?.let { put(it, initial.layer) } } }
    var point by remember { mutableStateOf(initial.point) }
    var step by rememberSaveable { mutableStateOf(NudgeStep.FINE) }
    val held = remember { HeldWarps() }
    var heldVersion by remember { mutableIntStateOf(0) }

    val block = mappingBlock(reading.locked, linkState)
    val editable = block == MappingBlock.NONE
    val layerIndex = (layerOf[output.id] ?: 0).coerceIn(0, output.layers.size - 1)
    heldVersion.let { } // read: a held warp's change redraws
    val now = nowMs()
    val shown: List<Warp> = output.layers.map { held.shown(it.base, it.warp, now) }
    val layer = output.layers[layerIndex]
    val warp = shown[layerIndex]
    val pickedPoint = point?.takeIf { it in 0 until warp.size }

    val density = LocalDensity.current
    val slopPx = with(density) { 10.dp.toPx() }.toDouble()
    val precisionPx = with(density) { 48.dp.toPx() }.toDouble()

    fun pick(layerAt: Int, pointAt: Int?) {
        layerOf[output.id] = layerAt
        point = pointAt
    }

    fun write(base: String, next: Warp) {
        held.hold(base, next, false, nowMs())
        heldVersion++
    }

    val actions = MappingActions(
        identify = { writes.identify(output.id, !output.testPattern) },
        grid = { writes.grid(output.id, output.grid != true) },
        nudge = { direction ->
            val at = pickedPoint
            if (editable && at != null) {
                val next = nudged(warp, at, direction, step)
                if (writes.setNow(layer.meshAddress, next.text())) write(layer.base, next)
            }
        },
        regrid = { columns, rows ->
            if (editable && (columns != warp.columns || rows != warp.rows)) {
                val next = regridded(warp, columns, rows)
                if (writes.setMany(regridWrites(layer.base, next))) {
                    write(layer.base, next)
                    point = null
                }
            }
        },
        step = { step = it },
    )

    val view: @Composable (Modifier) -> Unit = { modifier ->
        MeshView(
            warps = output.layers.mapIndexed { n, it -> DrawnWarp(shown[n], layerName(reading, it, n)) },
            picked = layerIndex,
            pickedPoint = pickedPoint,
            aspect = reading.aspectOf(output),
            viewKey = output.id,
            onPick = { l, p -> pick(l, p) },
            grab = { l, p, ux, uy ->
                if (!editable) null
                else {
                    val target = output.layers[l]
                    val from = held.shown(target.base, target.warp, nowMs())
                    val session = WarpDragSession(target.base, from, p, PointDrag(from.x[p], from.y[p], ux, uy, slopPx, precisionPx), writes)
                    object : PointGrab {
                        override fun move(dx: Double, dy: Double) {
                            if (session.update(dx, dy)) {
                                held.hold(target.base, session.warp, true, nowMs())
                                heldVersion++
                            }
                        }

                        override fun end() {
                            session.end()
                            held.letGo(target.base, nowMs())
                            heldVersion++
                        }
                    }
                }
            },
            modifier = modifier,
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Side by side on a tablet held sideways; stacked on a phone and on a tablet held upright.
        val wide = maxWidth >= 840.dp && maxWidth >= maxHeight
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        val live = linkState == LinkState.LIVE
        val panel: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BlockLine(block, linkState)
                OutputWords(reading, output, enabled = live, onIdentify = actions.identify, onGrid = actions.grid)
                WarpList(reading, output, layerIndex, onPick = { pick(it, null) })
                GridPanel(warp, editable, actions)
                if (layer.isZone) ZonePanel(layer, editable, writes)
                else Text(loc("mapping.zone.ownHasNone"), color = GodotColors.inkOff)
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().alpha(if (live) 1f else 0.55f)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    OutputPicker(reading, output.id) { outputId = it; point = null }
                    view(Modifier.fillMaxWidth().weight(1f))
                    PointBar(warp, pickedPoint, step, block, actions, wide = true)
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(GodotColors.rule))
                panel(
                    Modifier
                        .width(400.dp)
                        .fillMaxHeight()
                        .background(GodotColors.panel)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                )
            }
        } else {
            Column(Modifier.fillMaxSize().alpha(if (live) 1f else 0.55f)) {
                OutputPicker(reading, output.id) { outputId = it; point = null }
                val viewHeight: Dp = minOf(boxWidth / reading.aspectOf(output).toFloat() * 1.2f, boxHeight * 0.45f)
                view(Modifier.fillMaxWidth().height(viewHeight))
                PointBar(warp, pickedPoint, step, block, actions, wide = boxWidth >= 600.dp)
                Box(Modifier.fillMaxWidth().height(1.dp).background(GodotColors.rule))
                panel(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(GodotColors.panel)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
        }
    }
}

/** The tab's buttons as callbacks, so the panels below stay drawing only. */
private class MappingActions(
    val identify: () -> Unit,
    val grid: () -> Unit,
    val nudge: (NudgeDirection) -> Unit,
    val regrid: (columns: Int, rows: Int) -> Unit,
    val step: (NudgeStep) -> Unit,
)

/** A warp's name on screen: the output's own by its canvas, a zone by its name or, lacking one, its canvas. */
@Composable
private fun layerName(reading: MappingReading, layer: WarpLayer, n: Int): String {
    val canvas = reading.canvasLabel(layer.canvas) ?: loc("mapping.warps.noCanvas")
    return if (!layer.isZone) loc("mapping.warps.own", "canvas" to canvas)
    else loc("mapping.warps.zone", "number" to n.toString(), "name" to layer.name.ifEmpty { canvas })
}

/** Why nothing may be changed, said: the lock, or the link's own word. */
@Composable
private fun BlockLine(block: MappingBlock, linkState: LinkState) {
    when (block) {
        MappingBlock.NONE -> Unit
        // The header says how the link stands; here, only what it stops.
        MappingBlock.LINK -> Text(loc("mapping.stale"), color = GodotColors.inkDim)
        MappingBlock.LOCKED -> Surface(
            color = GodotColors.panelIn, shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, GodotColors.standby), modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(loc("mapping.locked"), color = GodotColors.standby, fontWeight = FontWeight.SemiBold)
                Text(loc("mapping.lockedExplain"), color = GodotColors.inkDim)
            }
        }
    }
}

/** The outputs, one button each: its name, the one shown marked by a bar AND its weight. */
@Composable
private fun OutputPicker(reading: MappingReading, current: String, onPick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(GodotColors.panel)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(loc("mapping.outputs"), color = GodotColors.inkFaint, modifier = Modifier.padding(horizontal = 8.dp))
        for (output in reading.outputs) {
            val selected = output.id == current
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .widthIn(min = 96.dp)
                    .clickable(role = Role.Tab) { onPick(output.id) }
                    .heightIn(min = GodotMetrics.touch)
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GodotIconMark(GodotIcon.VIDEO, if (selected) GodotColors.picked else GodotColors.inkFaint, size = 20.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        output.label, color = if (selected) GodotColors.ink else GodotColors.inkFaint,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(if (selected) GodotColors.picked else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = GodotColors.ink, fontWeight = FontWeight.SemiBold)
}

/** The output in words: what it shows and where, whether it is on, and whether it is showing or why not (§4.8). */
@Composable
private fun OutputWords(reading: MappingReading, output: OutputRow, enabled: Boolean, onIdentify: () -> Unit, onGrid: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            output.label, style = MaterialTheme.typography.titleLarge, color = GodotColors.ink,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        val canvas = reading.canvases[output.canvas]
        Text(
            if (canvas == null) loc("mapping.output.noCanvas")
            else loc("mapping.output.canvas", "canvas" to canvas.label, "size" to "${canvas.width} × ${canvas.height}"),
            color = GodotColors.inkDim
        )
        Text(
            when {
                output.cardLabel.isNotEmpty() -> loc("mapping.output.sendsTo", "card" to output.cardLabel)
                output.sends -> loc("mapping.output.sends", "kind" to kindWord(output.kind))
                output.display.isEmpty() -> loc("mapping.output.noDisplay")
                else -> loc("mapping.output.display", "display" to output.display)
            },
            color = GodotColors.inkDim
        )
        val showing = output.bound && output.problem.isEmpty()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        when {
                            !output.enabled -> GodotColors.inkOff
                            showing -> GodotColors.live
                            else -> GodotColors.failed
                        },
                        RoundedCornerShape(5.dp)
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    !output.enabled -> loc("mapping.output.off")
                    showing -> loc("mapping.output.showing")
                    else -> loc("mapping.output.notShowing")
                },
                color = GodotColors.ink, fontWeight = FontWeight.Medium
            )
        }
        if (output.enabled && !showing && output.problem.isNotEmpty()) {
            Text(output.problem, color = GodotColors.failed)
        }
        ProjectorSwitch(output.testPattern, enabled, "mapping.identify", onIdentify)
        // The grid, beside Identify: offered only by a Go.dot that has it (its row is there).
        output.grid?.let { ProjectorSwitch(it, enabled, "mapping.projectorGrid", onGrid) }
    }
}

/** A switch that puts something on the projector itself - Identify, the grid - its word and what it does. */
@Composable
private fun ProjectorSwitch(on: Boolean, enabled: Boolean, key: String, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = on,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = GodotColors.standby, checkedThumbColor = Color.Black)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(loc("$key.label"), color = GodotColors.ink, fontWeight = FontWeight.Medium)
            Text(loc(if (on) "$key.on" else "$key.off"), color = if (on) GodotColors.standby else GodotColors.inkFaint)
        }
    }
}

@Composable
private fun kindWord(kind: String): String = when (kind) {
    "ndi" -> "NDI"
    "spout" -> "Spout"
    "syphon" -> "Syphon"
    else -> kind
}

/** Each warp the output draws, its own first and then each zone, the picked one marked by a bar AND bold. */
@Composable
private fun WarpList(reading: MappingReading, output: OutputRow, picked: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Heading(loc("mapping.warps.title"))
        for ((n, layer) in output.layers.withIndex()) {
            val selected = n == picked
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (selected) GodotColors.panelHigh else Color.Transparent, RoundedCornerShape(4.dp))
                    .clickable(role = Role.RadioButton) { onPick(n) }
                    .heightIn(min = GodotMetrics.touch),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(32.dp)
                        .background(if (selected) GodotColors.picked else Color.Transparent)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        layerName(reading, layer, n), color = if (selected) GodotColors.ink else GodotColors.inkDim,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    val detail = buildList {
                        add(loc("mapping.grid.points", "columns" to layer.warp.columns.toString(), "rows" to layer.warp.rows.toString()))
                        if (layer.isZone) {
                            add(blendWord(layer.blend))
                            add("${percent(layer.opacity)} %")
                        }
                    }.joinToString(" · ")
                    Text(detail, color = GodotColors.inkFaint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun blendWord(blend: String): String = when (blend) {
    "normal", "add", "screen", "multiply" -> loc("mapping.blend.$blend")
    else -> blend
}

/** A percentage as the tablet reads it: whole when whole, else to the tenth, in the tablet's language. */
private fun percent(value: Double): String =
    if (value == Math.rint(value)) value.toLong().toString() else String.format(Locale.getDefault(), "%.1f", value)

/**
 * THE PICKED POINT AND THE NUDGE PAD, the drag's non-gestural equivalent, in a bar under
 * the picture - always in view, at the wall, whatever the panel beside it is scrolled to.
 * The arrows stay where they are when no point is picked, greyed, so the picture above
 * never jumps as one is; under the lock they are greyed and the bar says "Locked".
 */
@Composable
private fun PointBar(warp: Warp, point: Int?, step: NudgeStep, block: MappingBlock, actions: MappingActions, wide: Boolean) {
    val can = block == MappingBlock.NONE && point != null
    val words: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (point == null) {
                Text(loc("mapping.point.none"), color = GodotColors.inkFaint, style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        loc("mapping.point.which", "column" to (warp.columnOf(point) + 1).toString(), "row" to (warp.rowOf(point) + 1).toString()),
                        color = GodotColors.inkDim
                    )
                    if (block == MappingBlock.LOCKED) {
                        Spacer(Modifier.width(10.dp))
                        Text(loc("mapping.locked"), color = GodotColors.standby, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("x  " + WireText.number(warp.x[point], 4), style = GodotType.number, color = GodotColors.ink)
                    Text("y  " + WireText.number(warp.y[point], 4), style = GodotType.number, color = GodotColors.ink)
                }
            }
        }
    }
    val steps: @Composable (Boolean) -> Unit = { said ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (said) Text(loc("mapping.point.step"), color = GodotColors.inkDim)
            for (each in NudgeStep.entries) {
                val on = each == step
                val name = loc(if (each == NudgeStep.FINE) "mapping.point.fine" else "mapping.point.coarse")
                val described = loc("mapping.point.step") + " " + name
                OutlinedButton(
                    onClick = { actions.step(each) },
                    border = BorderStroke(if (on) 2.dp else 1.dp, if (on) GodotColors.picked else GodotColors.rule),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (on) GodotColors.panelHigh else Color.Transparent),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .heightIn(min = GodotMetrics.touch)
                        .semantics { contentDescription = described }
                ) {
                    Text(
                        name, color = if (on) GodotColors.ink else GodotColors.inkDim, style = GodotType.number,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
    val arrows: @Composable (Arrangement.Horizontal, Modifier) -> Unit = { arrangement, modifier ->
        Row(modifier, horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
            NudgeButton(NudgeDirection.LEFT, "mapping.point.left", can, actions.nudge)
            NudgeButton(NudgeDirection.UP, "mapping.point.up", can, actions.nudge)
            NudgeButton(NudgeDirection.DOWN, "mapping.point.down", can, actions.nudge)
            NudgeButton(NudgeDirection.RIGHT, "mapping.point.right", can, actions.nudge)
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(GodotColors.panel)
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(GodotColors.rule))
        if (wide) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                words(Modifier.weight(1f))
                steps(true)
                arrows(Arrangement.spacedBy(8.dp), Modifier)
            }
        } else {
            // A phone: the point's words and the steps on one line, the arrows across the width under them.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    words(Modifier.weight(1f).heightIn(min = 44.dp))
                    if (point != null) steps(false)
                }
                arrows(Arrangement.SpaceEvenly, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun NudgeButton(direction: NudgeDirection, key: String, editable: Boolean, nudge: (NudgeDirection) -> Unit) {
    val size = 56.dp
    val words = loc(key)
    Button(
        onClick = { nudge(direction) },
        enabled = editable,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GodotColors.panelHigh, contentColor = GodotColors.ink,
            disabledContainerColor = GodotColors.panelIn, disabledContentColor = GodotColors.inkOff,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = words }
    ) {
        // The standby arrows' chevrons, turned: < is left, > right; turned a quarter, up and down.
        val (icon, turn) = when (direction) {
            NudgeDirection.LEFT -> GodotIcon.PREVIOUS to 0f
            NudgeDirection.RIGHT -> GodotIcon.NEXT to 0f
            NudgeDirection.UP -> GodotIcon.PREVIOUS to 90f
            NudgeDirection.DOWN -> GodotIcon.NEXT to 90f
        }
        GodotIconMark(icon, if (editable) GodotColors.ink else GodotColors.inkOff, Modifier.rotate(turn), size = 32.dp)
    }
}

/** The grid's splits: a column or a row of points more or fewer, the warp keeping its shape - one setMany. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GridPanel(warp: Warp, editable: Boolean, actions: MappingActions) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Heading(loc("mapping.grid.title"))
        Text(
            loc("mapping.grid.points", "columns" to warp.columns.toString(), "rows" to warp.rows.toString()),
            color = GodotColors.inkDim
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Stepper(
                "mapping.grid.columns", warp.columns, editable,
                fewer = { actions.regrid(warp.columns - 1, warp.rows) },
                more = { actions.regrid(warp.columns + 1, warp.rows) },
                fewerKey = "mapping.grid.columnFewer", moreKey = "mapping.grid.columnMore",
            )
            Stepper(
                "mapping.grid.rows", warp.rows, editable,
                fewer = { actions.regrid(warp.columns, warp.rows - 1) },
                more = { actions.regrid(warp.columns, warp.rows + 1) },
                fewerKey = "mapping.grid.rowFewer", moreKey = "mapping.grid.rowMore",
            )
        }
        Text(loc("mapping.grid.explain"), color = GodotColors.inkFaint, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Stepper(key: String, value: Int, editable: Boolean, fewer: () -> Unit, more: () -> Unit, fewerKey: String, moreKey: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(loc(key), color = GodotColors.inkDim, modifier = Modifier.widthIn(min = 104.dp))
        StepButton("−", loc(fewerKey), editable && value > 2, fewer)
        Text(value.toString(), style = GodotType.number, color = GodotColors.ink, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
        StepButton("+", loc(moreKey), editable && value < Warp.MAX_POINTS, more)
    }
}

@Composable
private fun StepButton(sign: String, words: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier
            .size(GodotMetrics.touch)
            .semantics { contentDescription = words }
    ) { Text(sign, color = if (enabled) GodotColors.ink else GodotColors.inkOff, style = GodotType.numberLarge) }
}

/** A zone's opacity - a plain slider and a number box - and its blend, the engine's closed set as buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZonePanel(layer: WarpLayer, editable: Boolean, writes: MappingWrites) {
    var dragging by remember(layer.base) { mutableStateOf<Float?>(null) }
    val shown = dragging ?: layer.opacity.toFloat()
    val min = layer.opacityMin.toFloat()
    val max = layer.opacityMax.toFloat().takeIf { it > min } ?: (min + 100f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading(loc("mapping.zone.opacity"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = shown.coerceIn(min, max),
                onValueChange = {
                    if (dragging == null) writes.touch(layer.opacityAddress)
                    dragging = it
                    writes.set(layer.opacityAddress, WireText.number(it.toDouble(), 1))
                },
                onValueChangeFinished = {
                    writes.release(layer.opacityAddress)
                    dragging = null
                },
                valueRange = min..max,
                enabled = editable,
                colors = SliderDefaults.colors(
                    thumbColor = GodotColors.ink, activeTrackColor = GodotColors.picked, inactiveTrackColor = GodotColors.rule,
                ),
                modifier = Modifier.weight(1f).heightIn(min = GodotMetrics.touch)
            )
            Spacer(Modifier.width(12.dp))
            NumberBox(
                value = shown.toDouble(), enabled = editable, unit = "%",
                onCommit = { typed -> writes.setNow(layer.opacityAddress, WireText.number(typed.coerceIn(min.toDouble(), max.toDouble()), 1)) },
            )
        }
        if (layer.blends.isNotEmpty()) {
            Heading(loc("mapping.zone.blend"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (each in layer.blends) {
                    val on = each == layer.blend
                    OutlinedButton(
                        onClick = { if (!on) writes.setNow(layer.blendAddress, each) },
                        enabled = editable,
                        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) GodotColors.picked else GodotColors.rule),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (on) GodotColors.panelHigh else Color.Transparent),
                        modifier = Modifier.heightIn(min = GodotMetrics.touch)
                    ) {
                        Text(
                            blendWord(each), color = if (on) GodotColors.ink else GodotColors.inkDim,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/** A number typed: shown in the tablet's way, read with a comma or a point, sent in none (TypedNumber). */
@Composable
private fun NumberBox(value: Double, enabled: Boolean, unit: String, onCommit: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(percent(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = TypedNumber.filterTyping(it, allowDecimal = true) },
        enabled = enabled,
        singleLine = true,
        suffix = { Text(unit, color = GodotColors.inkFaint) },
        textStyle = GodotType.number.copy(color = GodotColors.ink, textAlign = TextAlign.End),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { TypedNumber.parse(text)?.let(onCommit) }),
        modifier = Modifier.width(120.dp)
    )
}
