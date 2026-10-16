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

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.localization.locStatic
import com.pob31.godot.remote.prompt.PromptAddress
import com.pob31.godot.remote.prompt.PromptSettings
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotMetrics
import com.pob31.godot.remote.ui.theme.GodotType

/**
 * SCRIPT PROMPT (CHOUFLEUR), on the Connections tab: the address of the operator's Choufleur
 * cue list - typed, or scanned off the code Choufleur draws - kept on the tablet. Set, it adds
 * the Script tab; cleared, the tab goes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptSection(settings: PromptSettings) {
    val saved by settings.address.collectAsState()
    var typed by remember(saved) { mutableStateOf(saved.orEmpty()) }
    var scanProblem by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val scan = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult // cancelled
        val address = PromptAddress.normalise(text)
        if (address == null) {
            scanProblem = locStatic("prompt.scanNotAddress", "text" to text.take(120))
        } else {
            scanProblem = null
            typed = address
            settings.save(address)
        }
    }
    val options = {
        ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(locStatic("prompt.scanPrompt"))
            .setBeepEnabled(false)
            .setOrientationLocked(false)
    }
    // The camera is asked for when the code is to be scanned, not before.
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scan.launch(options()) else scanProblem = locStatic("prompt.cameraRefused")
    }

    val normalised = PromptAddress.normalise(typed)
    Surface(color = GodotColors.panelIn, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(loc("prompt.title"), style = MaterialTheme.typography.titleMedium, color = GodotColors.ink, fontWeight = FontWeight.SemiBold)
            Text(loc("prompt.explain"), color = GodotColors.inkDim)
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.trim(); scanProblem = null },
                singleLine = true,
                label = { Text(loc("prompt.address")) },
                textStyle = GodotType.number,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
            when {
                typed.isNotEmpty() && normalised == null -> Text(loc("prompt.notAnAddress"), color = GodotColors.failed)
                normalised != null && normalised != typed -> Text(loc("prompt.willSave", "address" to normalised), color = GodotColors.inkFaint)
            }
            if (normalised != null && PromptAddress.isLocalName(normalised)) Text(loc("prompt.localName"), color = GodotColors.stopping)
            scanProblem?.let { Text(it, color = GodotColors.failed) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { normalised?.let { typed = it; settings.save(it) } },
                    enabled = normalised != null && normalised != saved,
                    colors = ButtonDefaults.buttonColors(containerColor = GodotColors.standby, contentColor = Color.Black),
                    modifier = Modifier.heightIn(min = GodotMetrics.touch)
                ) { Text(loc("prompt.save")) }
                OutlinedButton(
                    onClick = { typed = ""; scanProblem = null; settings.clear() },
                    enabled = saved != null,
                    modifier = Modifier.heightIn(min = GodotMetrics.touch)
                ) { Text(loc("prompt.clear"), color = if (saved != null) GodotColors.ink else GodotColors.inkOff) }
                OutlinedButton(
                    onClick = {
                        scanProblem = null
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            scan.launch(options())
                        } else {
                            askCamera.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.heightIn(min = GodotMetrics.touch)
                ) { Text(loc("prompt.scan"), color = GodotColors.ink) }
            }
            Text(loc("prompt.buzzer"), color = GodotColors.inkFaint)
            Text(loc("prompt.awake"), color = GodotColors.inkFaint)
        }
    }
}
