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

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.link.LinkedMachine
import com.pob31.godot.remote.link.Refusal
import com.pob31.godot.remote.localization.loc
import com.pob31.godot.remote.model.CueLine
import com.pob31.godot.remote.model.DohPhase
import com.pob31.godot.remote.model.GodotIcon
import com.pob31.godot.remote.model.HeaderReading
import com.pob31.godot.remote.model.Panic
import com.pob31.godot.remote.model.RunLine
import com.pob31.godot.remote.model.clockText
import com.pob31.godot.remote.model.originKey
import com.pob31.godot.remote.model.readHeader
import com.pob31.godot.remote.model.refusalLine
import com.pob31.godot.remote.ui.theme.GodotColors
import com.pob31.godot.remote.ui.theme.GodotMetrics
import com.pob31.godot.remote.ui.theme.GodotType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val offState: StateFlow<LinkState> = MutableStateFlow(LinkState.OFF)
private val noRefusal: StateFlow<Refusal?> = MutableStateFlow(null)

/**
 * THE TRANSPORT HEADER (A.4), on every tab: the machine on screen's current cue and its
 * run, the next cue with the standby arrows, GO, Doh! and PANIC, and a line of words.
 * Reads that machine's mirror at 25 Hz; sends to it.
 */
@Composable
fun TransportHeader(onScreen: LinkedMachine?) {
    val link = onScreen?.link
    val reading = rememberReading(link, HeaderReading()) { readHeader(it) }
    val state by (link?.state ?: offState).collectAsState()
    val refusal by (link?.lastRefusal ?: noRefusal).collectAsState()
    TransportHeaderView(
        machine = onScreen?.machine?.label,
        state = state,
        reading = reading,
        refusal = refusal,
        send = { command -> link?.command(command) ?: false },
    )
}

/**
 * The header drawn from a reading - no link of its own, so a test draws it from a fixture.
 *
 * LAID OUT AS THE DESKTOP'S (TransportComponent): GO on the left and the cues read outward
 * from it; PANIC at the far right, as far from GO as the row allows; Doh! to PANIC's left,
 * the same height, with a row's width of air between the two (the author, 2026-10-02: "to
 * avoid a total disaster"). GO drawn at twice the type, PANIC at one and a half, Doh! a
 * little larger. Every one is a button with words on it - nothing is a gesture only, and no
 * colour says anything a word does not.
 */
@Composable
fun TransportHeaderView(
    machine: String?,
    state: LinkState,
    reading: HeaderReading,
    refusal: Refusal?,
    send: (String) -> Boolean,
    nowMs: () -> Long = SystemClock::uptimeMillis,
) {
    val panic = remember { Panic() }
    var panicPresses by remember { mutableIntStateOf(0) }
    var notSent by remember { mutableIntStateOf(0) }

    // The word under PANIC goes back to "stop all" when the second press's time is up.
    LaunchedEffect(panicPresses) {
        val until = panic.armedUntil() ?: return@LaunchedEffect
        val wait = until - nowMs()
        if (wait >= 0) {
            delay(wait + 1)
            panicPresses++
        }
    }
    // "Not sent" says so for a few seconds, then the line is the engine's again.
    LaunchedEffect(notSent) {
        if (notSent > 0) {
            delay(4000)
            notSent = 0
        }
    }

    val sendOrSay: (String) -> Unit = { command -> if (!send(command)) notSent++ }
    // Read again at each press and when its time is up: no timer runs while nobody pressed.
    val killNext = remember(panicPresses) { panic.nextIsDouble(nowMs()) }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(GodotColors.panel)
    ) {
        val wide = maxWidth >= 700.dp
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WordsLine(machine, state, reading, refusal, notSent > 0)
            val live = state == LinkState.LIVE
            // GO is never shorter than 96 dp: 128 on the wide row, 104 on a phone's.
            if (wide) {
                Row(Modifier.height(128.dp), verticalAlignment = Alignment.CenterVertically) {
                    GoButton(reading, Modifier.width(196.dp).fillMaxHeight()) { sendOrSay("go") }
                    Spacer(Modifier.width(16.dp))
                    CuesBlock(reading, live, Modifier.weight(1f), 76.dp, sendOrSay)
                    Spacer(Modifier.width(16.dp))
                    DohButton(reading, Modifier.width(136.dp).fillMaxHeight()) { sendOrSay("go.doh") }
                    Spacer(Modifier.width(GodotMetrics.row))
                    PanicButton(killNext, Modifier.width(168.dp).fillMaxHeight()) {
                        sendOrSay(Panic.command(panic.press(nowMs())))
                        panicPresses++
                    }
                }
            } else {
                CuesBlock(reading, live, Modifier.fillMaxWidth(), 60.dp, sendOrSay)
                Row(Modifier.height(104.dp), verticalAlignment = Alignment.CenterVertically) {
                    GoButton(reading, Modifier.weight(1.5f).fillMaxHeight()) { sendOrSay("go") }
                    Spacer(Modifier.width(12.dp))
                    DohButton(reading, Modifier.weight(1f).fillMaxHeight()) { sendOrSay("go.doh") }
                    Spacer(Modifier.width(GodotMetrics.row))
                    PanicButton(killNext, Modifier.weight(1.2f).fillMaxHeight()) {
                        sendOrSay(Panic.command(panic.press(nowMs())))
                        panicPresses++
                    }
                }
            }
        }
    }
}

/** The machine on screen, the link when it is not live, the lock, and the last refusal of this tablet. */
@Composable
private fun WordsLine(machine: String?, state: LinkState, reading: HeaderReading, refusal: Refusal?, notSent: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (machine == null) {
            Text(loc("remote.header.noMachine"), color = GodotColors.inkFaint, maxLines = 1)
            return@Row
        }
        Text(machine, color = GodotColors.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 240.dp))
        if (state != LinkState.LIVE) LinkMark(state)
        if (reading.locked == true) {
            Text(
                loc("remote.header.locked"), color = GodotColors.standby, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier
                    .background(GodotColors.panelHigh, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        val sentence = when {
            notSent -> loc("remote.header.notSent", "machine" to machine)
            else -> refusalLine(refusal, reading.dohWindowSeconds, reading.audioStatus)?.let { say(it) }
        }
        sentence?.let {
            Text(it, color = GodotColors.failed, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

/** Current above, next below - the next one larger and in the standby colour, the arrows beside it. */
@Composable
private fun CuesBlock(reading: HeaderReading, live: Boolean, modifier: Modifier, labelWidth: Dp, send: (String) -> Unit) {
    Column(modifier.alpha(if (live) 1f else 0.45f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowLabel(loc("remote.header.current"), labelWidth)
            val current = reading.current
            if (current == null) {
                Text(loc("remote.header.nothingFired"), color = GodotColors.inkFaint, maxLines = 1)
            } else {
                CueName(current, GodotColors.ink, 18.sp, Modifier.weight(1f, fill = false))
                reading.currentOrigin?.let(::originKey)?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(loc(it), color = GodotColors.inkFaint, maxLines = 1)
                }
                reading.currentRun?.let {
                    Spacer(Modifier.width(12.dp))
                    RunWords(it)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowLabel(loc("remote.header.next"), labelWidth)
            ArrowButton(GodotIcon.PREVIOUS, loc("remote.header.standbyPrevious")) { send("standby.previous") }
            Spacer(Modifier.width(4.dp))
            ArrowButton(GodotIcon.NEXT, loc("remote.header.standbyNext")) { send("standby.next") }
            Spacer(Modifier.width(10.dp))
            val next = reading.next
            if (next == null) {
                Text(loc("remote.header.nothingInStandby"), color = GodotColors.inkFaint, fontSize = 20.sp, maxLines = 1)
            } else {
                val accent = GodotColors.kindAccent(next.kind, next.mode)
                GodotIconMark(next.icon, accent, size = 26.dp)
                Spacer(Modifier.width(8.dp))
                CueName(next, GodotColors.standby, 24.sp, Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(10.dp))
                Text(next.kindKey?.let { loc(it) } ?: next.kind, color = accent, maxLines = 1, softWrap = false)
                reading.resumeWords()?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(say(it), color = GodotColors.waiting, maxLines = 1)
                }
            }
        }
        reading.next?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                notes.replace('\n', ' '), color = GodotColors.inkDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = labelWidth)
            )
        }
    }
}

@Composable
private fun RowLabel(text: String, width: Dp) {
    Text(text, color = GodotColors.inkFaint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(width))
}

@Composable
private fun CueName(cue: CueLine, colour: Color, size: TextUnit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (cue.number.isNotEmpty()) {
            Text(cue.number, color = colour, style = GodotType.number.copy(fontSize = size), maxLines = 1)
            Spacer(Modifier.width(10.dp))
        }
        Text(cue.name, color = colour, fontSize = size, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A run's state in a word and its time: how far in, or - in a wait - how long left. */
@Composable
private fun RunWords(run: RunLine) {
    val word = run.stateKey?.let { loc(it) } ?: run.state
    val time = if (run.counting) loc("remote.header.left", "time" to clockText(run.remaining)) else clockText(run.position)
    val colour = when (run.state) {
        "playing" -> GodotColors.live
        "stopping" -> GodotColors.stopping
        "waiting", "postWait" -> GodotColors.waiting
        else -> GodotColors.inkDim
    }
    Text(word, color = colour, fontWeight = FontWeight.SemiBold, maxLines = 1)
    Spacer(Modifier.width(8.dp))
    Text(time, color = colour, style = GodotType.number, maxLines = 1)
}

@Composable
private fun ArrowButton(icon: GodotIcon, words: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = GodotColors.panelHigh,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .size(GodotMetrics.touch)
            .semantics { contentDescription = words }
    ) {
        GodotIconMark(icon, GodotColors.ink, Modifier.padding(10.dp), size = 28.dp)
    }
}

@Composable
private fun BigButton(
    label: String,
    under: String?,
    ground: Color,
    ink: Color,
    size: TextUnit,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = ground,
        contentColor = ink,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The label shrinks to fit a phone's narrow button rather than break or be cut.
            BasicText(
                label,
                style = TextStyle(color = ink, fontSize = size, fontWeight = FontWeight.Black, textAlign = TextAlign.Center),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = size),
            )
            under?.let {
                BasicText(
                    it,
                    style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp),
                )
            }
        }
    }
}

/**
 * GO WEARS THE AUDIO (the author, 2026-09-25): bright yellow with black letters while a
 * device is open and producing, grey otherwise - and the grey says why in a word under it.
 * It still sends when grey: a MIDI or a network cue needs no interface, and GO never blocks.
 */
@Composable
private fun GoButton(reading: HeaderReading, modifier: Modifier, onClick: () -> Unit) {
    val running = reading.audioRunning
    BigButton(
        label = "GO",
        under = reading.goLine()?.let { say(it) },
        ground = if (running) GodotColors.go else GodotColors.goIdle,
        ink = if (running) GodotColors.goInk else GodotColors.ink,
        size = 44.sp,
        modifier = modifier,
        onClick = onClick,
    )
}

/**
 * DOH! (PRD §3.32): "Doh! 12" in its own colour while the last GO can be taken back, the
 * seconds left under it, fading to the idle look over the window's last seconds and
 * disabled once a press would be refused - all of it off the engine's tick, never a clock
 * of the tablet's. It stays a button while the last Doh! left a resume a press would forget.
 */
@Composable
private fun DohButton(reading: HeaderReading, modifier: Modifier, onClick: () -> Unit) {
    val look = reading.dohLook()
    val mix = (1.0 - look.strength).toFloat()
    val under = when {
        look.phase != DohPhase.OVER -> loc("remote.header.dohLeft", "seconds" to look.secondsLeft.toString())
        reading.dohClickable() -> loc("remote.header.dohForget")
        else -> null
    }
    BigButton(
        label = reading.dohCaption(),
        under = under,
        ground = lerp(GodotColors.doh, GodotColors.goIdle, mix),
        ink = lerp(GodotColors.dohInk, GodotColors.inkDim, mix),
        size = 26.sp,
        modifier = modifier,
        enabled = reading.dohClickable(),
        onClick = onClick,
    )
}

/** PANIC in the refusal's red; the word under it says what THIS press would do: stop all, or KILL ALL. */
@Composable
private fun PanicButton(killNext: Boolean, modifier: Modifier, onClick: () -> Unit) {
    BigButton(
        label = "PANIC",
        under = loc(if (killNext) "remote.header.killAll" else "remote.header.stopAll"),
        ground = GodotColors.failed,
        ink = GodotColors.goInk,
        size = 32.sp,
        modifier = modifier,
        onClick = onClick,
    )
}
