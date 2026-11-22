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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.DynReading
import com.pob31.godot.remote.model.KeyChoice
import com.pob31.godot.remote.model.TabWrites
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotMetrics

/*  A SOUND'S OWN DYNAMICS, WHAT IT LISTENS TO (Go.dot's namespace draft §141): the Key menu,
    as Go.dot's Dynamics panel words it - the cue itself before or after its level, an input,
    an aux, an output (heard a block late), and a key naming what the show has not got kept
    and said, never dropped (BHV). Picking writes `dynKey`, one node.set. */

/** What a key choice says: "this cue, before its level", "Talkback · input"... */
@Composable
fun keyChoiceWords(choice: KeyChoice): String = when (choice.kind) {
    KeyChoice.Kind.BEFORE -> loc("dyn.key.before")
    KeyChoice.Kind.AFTER -> loc("dyn.key.after")
    KeyChoice.Kind.INPUT -> loc("dyn.key.input", "name" to choice.name)
    KeyChoice.Kind.AUX -> loc("dyn.key.aux", "name" to choice.name)
    KeyChoice.Kind.OUTPUT -> loc("dyn.key.output", "name" to choice.name)
    KeyChoice.Kind.GONE -> loc("dyn.key.gone", "name" to choice.name)
}

/** The Key menu: its words on a button, the choices under it, the live mark under the lock. */
@Composable
fun DynKeyMenu(dyn: DynReading, writes: TabWrites, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(loc("dyn.key.label"), color = GodotColors.inkDim)
        Box {
            OutlinedButton(
                onClick = { open = true },
                enabled = enabled,
                modifier = Modifier.heightIn(min = GodotMetrics.touch).widthIn(min = 240.dp)
            ) {
                Text(
                    keyChoiceWords(dyn.keyChoice),
                    color = if (dyn.keyChoice.kind == KeyChoice.Kind.GONE) GodotColors.waiting else GodotColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (choice in dyn.keyChoices) {
                    DropdownMenuItem(
                        text = { Text(keyChoiceWords(choice)) },
                        onClick = {
                            open = false
                            if (choice.value != dyn.key) writes.setNow(dyn.keyAddress, choice.value)
                        }
                    )
                }
            }
        }
        if (dyn.keyLive) LiveWord()
    }
}
