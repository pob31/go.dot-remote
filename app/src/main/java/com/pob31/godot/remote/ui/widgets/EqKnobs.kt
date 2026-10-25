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

package com.pob31.godot.remote.ui.widgets

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pob31.godot.remote.link.WireText
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.EqKnob
import com.pob31.godot.remote.model.EqLaw
import com.pob31.godot.remote.model.EqLaws
import com.pob31.godot.remote.model.EqPress
import com.pob31.godot.remote.model.EqReading
import com.pob31.godot.remote.model.EqSwitch
import com.pob31.godot.remote.model.SurfaceTables
import com.pob31.godot.remote.model.TabWrites
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotMetrics

/*  THE EQ'S CONTROLS, AS THE INSPECTOR AND THE SURFACE'S PAGES BOTH DRAW THEM: one of the
    surface tables' sixteen controls on a [Rotary] - its law, its range, its band's colour,
    what a press does - and a band's switch as a button that says in or out. A send's
    level on the Send page is the same rotary on the fader's law (model `sendKnobs`), and
    its place on the Pan page a lean or a width (model `placeKnobs`). */

private val BAND_WORDS = mapOf("eqOn" to "EQ", "eqHpf" to "HP", "eqB1On" to "B1", "eqB2On" to "B2", "eqB3On" to "B3", "eqB4On" to "B4", "eqLpf" to "LP")
private val BAND_COLOURS = mapOf("eqHpf" to 0, "eqB1On" to 1, "eqB2On" to 2, "eqB3On" to 3, "eqB4On" to 4, "eqLpf" to 5)

/** A band's switch - or the whole EQ's (`eqOn`) - as a button: its name, in or out, the live mark under the lock. */
@Composable
fun EqBandSwitch(switch: EqSwitch, writes: TabWrites, enabled: Boolean = true) {
    val colour = BAND_COLOURS[switch.row]?.let { GodotColors.eq[it] } ?: GodotColors.ink
    Row(
        Modifier
            .heightIn(min = GodotMetrics.touch)
            .background(if (switch.on) colour.copy(alpha = 0.25f) else GodotColors.panelIn, RoundedCornerShape(6.dp))
            .border(2.dp, if (switch.on) colour else GodotColors.rule, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { writes.setNow(switch.address, WireText.bool(!switch.on)) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(BAND_WORDS[switch.row] ?: switch.row, color = GodotColors.ink, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Text(loc(if (switch.on) "widgets.in" else "widgets.out"), color = if (switch.on) GodotColors.ink else GodotColors.inkFaint)
        if (switch.live) {
            Spacer(Modifier.width(6.dp))
            MarkIcon(Mark.LIVE, GodotColors.waiting, size = 14.dp)
        }
    }
}

/**
 * A stereo send's sides swapped or not (`send/swap`, namespace draft §113), as a button that
 * says which - its mix channel's name before it where several are drawn together.
 */
@Composable
fun SwapSwitch(switch: EqSwitch, name: String?, writes: TabWrites, enabled: Boolean = true) {
    Row(
        Modifier
            .heightIn(min = GodotMetrics.touch)
            .background(if (switch.on) GodotColors.fade.copy(alpha = 0.25f) else GodotColors.panelIn, RoundedCornerShape(6.dp))
            .border(2.dp, if (switch.on) GodotColors.fade else GodotColors.rule, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { writes.setNow(switch.address, WireText.bool(!switch.on)) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!name.isNullOrEmpty()) {
            Text(name, color = GodotColors.ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.width(6.dp))
        }
        Text(loc(if (switch.on) "widgets.swapped" else "widgets.notSwapped"), color = if (switch.on) GodotColors.ink else GodotColors.inkFaint)
        if (switch.live) {
            Spacer(Modifier.width(6.dp))
            MarkIcon(Mark.LIVE, GodotColors.waiting, size = 14.dp)
        }
    }
}

/**
 * ONE CONTROL ON A ROTARY: turned relative from touch-down (finer away from the knob), its
 * value kept to the grain the engine keeps it to, a drag one undo step (touch, values,
 * release); greyed and said "out" when its band is switched out. A press does what the
 * tables say: a gain's switches its band, a shape's flips peak and shelf. The value sent
 * is shown until the engine has it, or for a second and a half.
 */
@Composable
fun EqKnobView(
    knob: EqKnob,
    eq: EqReading,
    writes: TabWrites,
    tables: SurfaceTables,
    size: Dp = 92.dp,
    colour: Color? = null,
    enabled: Boolean = true,
) {
    val control = knob.control
    val ink = colour ?: GodotColors.eq[control.colour.coerceIn(0, GodotColors.eq.lastIndex)]
    var sent by remember(knob.address) { mutableStateOf<Pair<Double, Long>?>(null) }
    val enginePlace = if (control.law == EqLaw.SHAPE) (if (knob.shape == "peak" || knob.shape.isEmpty()) 0.0 else 1.0)
                      else EqLaws.place(control.law, knob.value, control.min, control.max, tables.fader)
    val held = sent?.takeIf { (place, at) -> SystemClock.uptimeMillis() - at < 1500 && kotlin.math.abs(place - enginePlace) > 1e-6 }?.first
    val place = held ?: enginePlace
    fun valueOf(at: Double) =
        EqLaws.grained(control.law, EqLaws.valueAt(control.law, at, control.min, control.max, tables.fader), control.min, control.max, tables.fader.silenceDb)
    val text = if (control.law == EqLaw.SHAPE) EqLaws.shapeText(if (place >= 0.5) control.shelf else "peak")
               else EqLaws.valueText(control.law, valueOf(place), tables.fader.silenceDb)

    Rotary(
        place = place,
        label = control.label,
        valueText = text,
        colour = ink,
        bipolar = control.law == EqLaw.GAIN || control.law == EqLaw.PAN,
        dimmed = !knob.bandOn || eq.on?.on == false,
        word = if (!knob.bandOn) loc("widgets.out") else null,
        live = knob.live,
        enabled = enabled,
        size = size,
        onStart = { if (control.law != EqLaw.SHAPE) writes.touch(knob.address) },
        onTurn = { at -> if (control.law != EqLaw.SHAPE) writes.set(knob.address, WireText.number(valueOf(at), 2)) },
        onEnd = { at ->
            if (control.law == EqLaw.SHAPE) {
                val shape = if (at >= 0.5) control.shelf else "peak"
                if (shape != knob.shape) writes.setNow(knob.address, shape)
                sent = (if (at >= 0.5) 1.0 else 0.0) to SystemClock.uptimeMillis()
            } else {
                writes.set(knob.address, WireText.number(valueOf(at), 2))
                writes.release(knob.address)
                sent = EqLaws.place(control.law, valueOf(at), control.min, control.max, tables.fader) to SystemClock.uptimeMillis()
            }
        },
        onPress = when (control.press) {
            EqPress.TOGGLE_SWITCH -> ({
                eq.switches.firstOrNull { it.row == control.switchRow }?.let { writes.setNow(it.address, WireText.bool(!it.on)) }
                Unit
            })
            EqPress.TOGGLE_SHAPE -> ({
                writes.setNow(knob.address, EqLaws.pressedShape(knob.shape, control.shelf))
                Unit
            })
            EqPress.CENTRE -> ({
                writes.setNow(knob.address, WireText.number(0.0, 2))
                sent = EqLaws.place(control.law, 0.0, control.min, control.max) to SystemClock.uptimeMillis()
                Unit
            })
            EqPress.NONE -> null
        },
    )
}
