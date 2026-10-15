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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.SurfaceTables
import com.pob31.godot.remote.ui.theme.GodotColors

/** The surface tables from the app's assets, read once a process. */
@Composable
fun rememberSurfaceTables(): SurfaceTables {
    val context = LocalContext.current
    return remember { SurfaceTables.load(context) }
}

/** "Locked", with the lock drawn: what a greyed row says it is (§4.8 - grey alone says nothing). */
@Composable
fun LockedWord(modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(GodotColors.panelHigh, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MarkIcon(Mark.LOCK, GodotColors.standby, size = 14.dp)
        Text(loc("widgets.locked"), color = GodotColors.standby, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** "live", with the live mark: a value a locked show rides, unsaved. */
@Composable
fun LiveWord(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        MarkIcon(Mark.LIVE, GodotColors.waiting, size = 14.dp)
        Text(loc("widgets.live"), color = GodotColors.waiting, maxLines = 1)
    }
}
