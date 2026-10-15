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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pob31.godot.remote.link.LinkedMachine
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.ui.theme.GodotColors

/** The header's line of words until the transport comes (A.4): the machine on screen and its link. */
@Composable
fun TransportHeader(onScreen: LinkedMachine?) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(GodotColors.panel)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onScreen == null) {
            Text(loc("remote.header.noMachine"), color = GodotColors.inkFaint)
        } else {
            val state by onScreen.link.state.collectAsState()
            Text(onScreen.machine.label, color = GodotColors.ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(16.dp))
            LinkMark(state)
        }
    }
}
