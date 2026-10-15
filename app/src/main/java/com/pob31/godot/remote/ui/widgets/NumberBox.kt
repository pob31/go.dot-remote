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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.FieldText
import com.pob31.godot.remote.model.TypedNumber
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotMetrics
import com.pob31.godot.remote.ui.theme.GodotType
import kotlinx.coroutines.delay

/**
 * A NUMBER TYPED IN A BOX - the page's number field, on a tablet. It shows [shown] (what
 * the engine holds, as [FieldText.shown] writes it) except while somebody is typing in it;
 * it commits on Done or when the focus leaves - never letter by letter - through
 * [FieldText.commit]: a comma read as a point, the value written in no locale's way, a
 * cleared box sending nothing (or "0" for a [blankZero] box), and a box that holds no
 * number sending nothing and saying so. [signed] boxes get a keyboard with a minus.
 */
@Composable
fun NumberBox(
    shown: String,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimals: Int = 3,
    blankZero: Boolean = false,
    signed: Boolean = false,
    unit: String? = null,
    enabled: Boolean = true,
    width: Dp = 96.dp,
    placeholder: String = "",
) {
    var text by remember { mutableStateOf(shown) }
    var focused by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    // The engine's value whenever nobody is typing: a push never writes over a hand.
    LaunchedEffect(shown, focused) { if (!focused) text = shown }
    LaunchedEffect(refused) {
        if (refused) {
            delay(2500)
            refused = false
        }
    }

    fun commit() {
        when (val outcome = FieldText.commit(text, decimals, blankZero)) {
            // Nothing changed is nothing to send: a box left as it stood writes nothing.
            is FieldText.Commit.Send -> if (outcome.text != (if (blankZero && shown.isEmpty()) "0" else shown)) onSend(outcome.text)
            FieldText.Commit.Revert -> text = shown
            FieldText.Commit.NotANumber -> {
                refused = true
                text = shown
            }
        }
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(width)
                .heightIn(min = GodotMetrics.touch)
                .background(if (enabled) GodotColors.panelIn else GodotColors.panel, RoundedCornerShape(4.dp))
                .border(
                    if (focused || refused) 2.dp else 1.dp,
                    when {
                        refused -> GodotColors.failed
                        focused -> GodotColors.picked
                        else -> GodotColors.rule
                    },
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (text.isEmpty() && placeholder.isNotEmpty() && !focused) {
                Text(placeholder, color = GodotColors.inkOff, style = GodotType.number)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = TypedNumber.filterTyping(it, allowDecimal = decimals > 0) },
                enabled = enabled,
                singleLine = true,
                textStyle = GodotType.number.copy(
                    color = if (enabled) GodotColors.ink else GodotColors.inkOff,
                    textAlign = TextAlign.End
                ),
                cursorBrush = SolidColor(GodotColors.picked),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (signed) KeyboardType.Text else KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.onFocusChanged {
                    if (focused && !it.isFocused) commit()
                    focused = it.isFocused
                }
            )
        }
        unit?.takeIf { it.isNotEmpty() }?.let {
            Spacer(Modifier.width(6.dp))
            Text(it, color = GodotColors.inkFaint)
        }
        if (refused) {
            Spacer(Modifier.width(8.dp))
            Text(loc("widgets.notANumber"), color = GodotColors.failed)
        }
    }
}
