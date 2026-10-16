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

package com.pob31.godot.remote.ui.prompt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.GodotIcon
import com.pob31.godot.remote.prompt.PageState
import com.pob31.godot.remote.prompt.PromptPage
import com.pob31.godot.remote.ui.GodotIconMark
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotType

/**
 * THE SCRIPT TAB: Choufleur's page for this operator's cue list, under the transport header
 * like every tab. The page is [PromptPage]'s one WebView, attached here and let go when the
 * tab is left - never reloaded by coming back.
 */
@Composable
fun ScriptTab(address: String, page: PromptPage) {
    val state by page.state.collectAsState()
    val generation by page.generation.collectAsState()
    val context = LocalContext.current
    var noBrowser by remember { mutableStateOf(false) }
    LaunchedEffect(address) { page.show(address) }
    ScriptTabView(
        address = address,
        state = state,
        noBrowser = noBrowser,
        onReload = page::reload,
        onOpenInChrome = { noBrowser = !PromptPage.openInChrome(context, address) },
    ) {
        key(generation) {
            AndroidView(
                factory = { page.attach(it) },
                onRelease = { page.detach(it) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** The tab drawn from its state, the page given: what the screenshot test draws without a WebView. */
@Composable
fun ScriptTabView(
    address: String,
    state: PageState,
    noBrowser: Boolean = false,
    onReload: () -> Unit,
    onOpenInChrome: () -> Unit,
    page: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(GodotColors.ground)) {
        AddressBar(address, onReload, onOpenInChrome)
        if (noBrowser) {
            Text(
                loc("prompt.noBrowser", "address" to address), color = GodotColors.failed,
                modifier = Modifier.fillMaxWidth().background(GodotColors.panelIn).padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            page()
            when (state) {
                is PageState.Failed -> Failed(state, onReload)
                is PageState.Loading -> Text(
                    loc("prompt.loading", "address" to address), color = GodotColors.inkFaint,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
                )
                else -> Unit
            }
        }
    }
}

/** As thin as a row of words can be: the address, Reload, Open in Chrome. */
@Composable
private fun AddressBar(address: String, onReload: () -> Unit, onOpenInChrome: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(36.dp).background(GodotColors.panelIn).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            address, style = GodotType.number.copy(fontSize = 12.sp), color = GodotColors.inkFaint,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        val compact = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        TextButton(onClick = onReload, contentPadding = compact, modifier = Modifier.height(36.dp)) {
            Text(loc("prompt.reload"), color = GodotColors.inkDim, fontSize = 13.sp)
        }
        TextButton(onClick = onOpenInChrome, contentPadding = compact, modifier = Modifier.height(36.dp)) {
            Text(loc("prompt.openInChrome"), color = GodotColors.inkDim, fontSize = 13.sp)
        }
    }
}

/** It did not load: said in words, with the address and a Retry - never a blank page. */
@Composable
private fun Failed(state: PageState.Failed, onRetry: () -> Unit) {
    val problem = when (state.problem) {
        "http" -> loc("prompt.problem.http", "status" to state.detail)
        "other" -> loc("prompt.problem.other", "detail" to state.detail)
        else -> loc("prompt.problem.${state.problem}")
    }
    Column(
        Modifier.fillMaxSize().background(GodotColors.ground).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GodotIconMark(GodotIcon.WARNING, GodotColors.failed, size = 40.dp)
        Text(
            loc("prompt.failed", "address" to state.address), color = GodotColors.ink, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 640.dp)
        )
        Text(problem, color = GodotColors.inkDim, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 640.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = GodotColors.standby, contentColor = Color.Black)
        ) { Text(loc("prompt.retry")) }
    }
}
