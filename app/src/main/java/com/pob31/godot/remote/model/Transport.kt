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

package com.pob31.godot.remote.model

import com.pob31.godot.remote.link.Refusal
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToLong

/*  THE TRANSPORT HEADER'S READING (A.4): Go.dot's client/model/Transport.cpp, ported for
    the rows the tablet's header shows - the list in focus, the cue its last GO fired and
    the run of it still going, the cue in standby, the lock, the audio, and Doh!'s window
    read off the engine's own tick. A value, taken at most 25 times a second from the
    mirror of the machine on screen; the screen compares it with the last one. */

/** One step of `/godot/list/<id>/history`: `tick:cue:origin`. */
data class HistoryStep(val tick: Long, val cue: String, val origin: Char)

/**
 * The history as the engine writes it - newest first, space-separated - with a word that
 * is not `tick:cue:origin` skipped rather than read as half a step (LoadToTime.cpp).
 */
fun readHistory(text: String): List<HistoryStep> = text.split(' ').mapNotNull { word ->
    val first = word.indexOf(':')
    val last = word.lastIndexOf(':')
    if (first < 0 || last == first || last + 1 >= word.length) return@mapNotNull null
    val cue = word.substring(first + 1, last)
    if (cue.isEmpty()) null else HistoryStep(word.substring(0, first).toLongOrNull() ?: 0L, cue, word[last + 1])
}

/** A cue as the header shows it: its number, its name, and what it is (a picture and a word). */
data class CueLine(
    val id: String,
    val number: String,
    val name: String,
    val kind: String,
    val mode: String,
    val verb: String,
    val notes: String,
) {
    val icon: GodotIcon get() = iconFor(kind, mode, verb)

    /** The key of the kind's word, or null for a kind this app has no word for (said as the engine spells it). */
    val kindKey: String? get() = if (kind in KINDS) "remote.kind.$kind" else null

    companion object {
        val KINDS = setOf(
            "memo", "group", "media", "fade", "transport", "osc", "midi", "start", "mic", "video", "process",
            "timecode", "script",
        )
    }
}

fun cueLine(mirror: Mirror, cue: String): CueLine {
    val base = "/godot/cue/$cue/"
    val label = cueLabel(mirror, cue)
    return CueLine(
        id = cue,
        number = label.number,
        name = label.name,
        kind = mirror.text(base + "kind") ?: "",
        mode = mirror.text(base + "mode") ?: "",
        verb = mirror.text(base + "verb") ?: "",
        notes = mirror.text(base + "notes") ?: "",
    )
}

/** A run of a cue as the header shows it: where it is, and how far. */
data class RunLine(val id: String, val state: String, val position: Double, val remaining: Double) {
    /** A pre-wait or a post-wait counting down: the time LEFT is what is shown, not how far in. */
    val counting: Boolean get() = state == "waiting" || state == "postWait"

    val stateKey: String? get() = if (state in STATES) "remote.runState.$state" else null

    companion object {
        val STATES = setOf("preparing", "waiting", "armed", "playing", "stopping", "postWait", "done", "failed")
    }
}

/** Where Doh!'s window stands (Transport.h `DohPhase`). */
enum class DohPhase { OVER, OPEN, FADING }

/** The Doh! button's look: its phase, how much of its colour is left (1 whole, 0 idle), whole seconds left. */
data class DohLook(val phase: DohPhase = DohPhase.OVER, val strength: Double = 0.0, val secondsLeft: Int = 0)

data class HeaderReading(
    /** `/godot/list/focus`, or the first of `/godot/list/order` when it names none (strip.js's fallback). */
    val listId: String = "",
    val listName: String = "",
    /** The cue the newest step of the list's history FIRED - a Doh! step is not a firing. */
    val current: CueLine? = null,
    val currentOrigin: Char? = null,
    /** The newest run of [current] still going: not preparing, not finished. */
    val currentRun: RunLine? = null,
    /** The cue in standby: what GO fires. */
    val next: CueLine? = null,
    /** `/godot/list/<id>/resume`: "<cue> <seconds>" when the next GO carries a paused cue on. */
    val resume: String = "",
    /** `/godot/list/<id>/loaded`: "<cue> <seconds>" when a load to time holds the standby ready, or empty. */
    val loaded: String = "",
    /** `/godot/document/locked`: null while the engine has not said. */
    val locked: Boolean? = null,
    /** `/godot/audio/status`: "running", "stopped", "noClock"... */
    val audioStatus: String = "",
    /** `/godot/engine/tick`, the clock everything here counts in. */
    val tick: Long = -1,
    /** `/godot/list/doh`: "<list> <cue> <tick>" of the GO Doh! would take back, or empty. */
    val doh: String = "",
    /** That cue's number, or its name when it has none. */
    val dohCue: String = "",
    /** `/godot/list/dohWindow`, seconds, as the engine wrote it. */
    val dohWindow: String = "",
    /** `/godot/list/dohForget`: "<list> <cue>" of a resume a press would forget, or empty. */
    val dohForget: String = "",
    /** `/godot/engine/paused` (Go.dot's PRD §3.34): the show held by Pause until Play. */
    val paused: Boolean = false,
) {
    val audioRunning: Boolean get() = audioStatus == "running"

    /** The window in seconds: the show's, the schema's ten when it has not said, never below nought (Doh! off). */
    val dohWindowSeconds: Double get() = maxOf(0.0, dohWindow.trim().toDoubleOrNull() ?: 10.0)

    /**
     * THE BUTTON'S LOOK, from the engine's tick, the GO's and the window - never from a clock
     * of the tablet's (Transport.cpp `dohLook`): open in its colour; fading over the window's
     * last two seconds (half the window when it is shorter than four); over - idle and no
     * press - from the tick a press would start being refused.
     */
    fun dohLook(): DohLook {
        val parts = words(doh)
        if (parts.size != 3 || dohCue.isEmpty()) return DohLook()
        val now = tick
        val at = parts[2].toLongOrNull() ?: -1L
        val window = (dohWindowSeconds * TICKS_PER_SECOND).roundToLong()
        if (now < 0 || at < 0 || now < at || now - at >= window) return DohLook()
        val left = window - (now - at)
        val fade = maxOf(1L, minOf(2 * TICKS_PER_SECOND, window / 2))
        return if (left < fade) {
            DohLook(DohPhase.FADING, left.toDouble() / fade.toDouble(), ((left + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND).toInt())
        } else {
            DohLook(DohPhase.OPEN, 1.0, ((left + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND).toInt())
        }
    }

    /** "Doh! 12" while the last GO can be taken back, "Doh!" otherwise. */
    fun dohCaption(): String = if (dohLook().phase == DohPhase.OVER) "Doh!" else "Doh! $dohCue"

    /** Whether a press would do something: take back the GO, or forget the resume the last Doh! left. */
    fun dohClickable(): Boolean = dohLook().phase != DohPhase.OVER || words(dohForget).size == 2

    /*  PAUSE / PLAY (Go.dot's PRD §3.34; the author, 2026-10-25: "Add it to the Android remote
        too"): the command a press sends is what the button says - "Pause" while the show runs,
        "Play" while it is held - two commands, never a toggle, so a press made on a mirror a
        pass behind cannot undo itself. */
    fun pauseCommand(): String = if (paused) "run.playAll" else "run.pauseAll"
    fun pauseCaptionKey(): String = if (paused) "remote.header.play" else "remote.header.pause"

    /** GO's word when it is grey (the author, 2026-09-25): no audio, no clock, paused; null while the audio runs. */
    fun goLine(): Said? = when {
        paused -> Said("remote.header.paused")
        audioRunning -> null
        audioStatus == "noClock" -> Said("remote.header.noClock")
        audioStatus.isEmpty() -> Said("remote.header.audioUnsaid")
        else -> Said("remote.header.noAudio")
    }

    /** "resumes at 0:08" beside the standby when the next GO carries it on; null otherwise. */
    fun resumeWords(): Said? {
        val parts = words(resume)
        val standby = next?.id ?: return null
        if (parts.isEmpty() || parts[0] != standby) return null
        val seconds = parts.getOrNull(1)?.toDoubleOrNull()
        if (seconds == null || !(seconds >= 0.0)) return Said("remote.header.resumes")
        val whole = floor(seconds).toLong()
        return Said("remote.header.resumesAt", "time" to String.format(Locale.ROOT, "%d:%02d", whole / 60, whole % 60))
    }

    /**
     * "loaded at 0:12.3 - GO plays from here" under the standby while a load to time holds
     * it ready at a second (namespace draft §117, `list/loaded`, split at its last space as
     * the desktop's LoadToTime.cpp does); null when nothing is held or the hold is another
     * cue's. Before the cue (-1) GO is an ordinary GO, said as the desktop says it.
     */
    fun loadedWords(): Said? {
        val text = loaded.trim()
        val space = text.lastIndexOf(' ')
        if (space <= 0) return null
        val standby = next?.id ?: return null
        if (text.substring(0, space) != standby) return null
        val seconds = text.substring(space + 1).toDoubleOrNull() ?: return null
        return if (seconds >= 0.0) Said("remote.header.loadedAt", "time" to clockText(seconds))
               else Said("remote.header.loadedBefore")
    }

    companion object {
        /** The engine's clock: fifty ticks a second. */
        const val TICKS_PER_SECOND = 50L
    }
}

private fun words(text: String): List<String> = text.split(' ').filter { it.isNotEmpty() }

/** One pass over the mirror: a few lookups by address and the run order's words, never a scan. */
fun readHeader(mirror: Mirror): HeaderReading {
    var listId = mirror.text("/godot/list/focus") ?: ""
    if (listId.isEmpty()) listId = words(mirror.text("/godot/list/order") ?: "").firstOrNull() ?: ""
    val list = "/godot/list/$listId/"

    val step = if (listId.isEmpty()) null
               else readHistory(mirror.text(list + "history") ?: "").firstOrNull { it.origin != 'd' }
    val current = step?.let { cueLine(mirror, it.cue) }
    val standby = if (listId.isEmpty()) "" else mirror.text(list + "standby") ?: ""

    val doh = mirror.text("/godot/list/doh") ?: ""
    val dohParts = words(doh)
    val dohCue = if (dohParts.size == 3) cueLabel(mirror, dohParts[1]).let { it.number.ifEmpty { it.name } } else ""

    return HeaderReading(
        listId = listId,
        listName = if (listId.isEmpty()) "" else mirror.text(list + "name") ?: "",
        current = current,
        currentOrigin = step?.origin,
        currentRun = current?.let { runOf(mirror, it.id) },
        next = standby.takeIf { it.isNotEmpty() }?.let { cueLine(mirror, it) },
        resume = if (listId.isEmpty()) "" else mirror.text(list + "resume") ?: "",
        loaded = if (listId.isEmpty()) "" else mirror.text(list + "loaded") ?: "",
        locked = mirror.value("/godot/document/locked")?.asBoolean(),
        audioStatus = mirror.text("/godot/audio/status") ?: "",
        tick = mirror.value("/godot/engine/tick")?.asLong() ?: mirror.tick.value,
        doh = doh,
        dohCue = dohCue,
        dohWindow = mirror.text("/godot/list/dohWindow") ?: "",
        dohForget = mirror.text("/godot/list/dohForget") ?: "",
        paused = mirror.value("/godot/engine/paused")?.asBoolean() == true,
    )
}

/** The newest run of [cue] that is still going - `/godot/run/order` is in the order the runs were made. */
private fun runOf(mirror: Mirror, cue: String): RunLine? {
    for (id in words(mirror.text("/godot/run/order") ?: "").asReversed()) {
        val base = "/godot/run/$id/"
        if (mirror.text(base + "cue") != cue) continue
        val state = mirror.text(base + "state") ?: continue
        if (state == "preparing" || state == "done" || state == "failed") continue
        return RunLine(
            id = id,
            state = state,
            position = mirror.value(base + "position")?.asDouble() ?: 0.0,
            remaining = mirror.value(base + "remaining")?.asDouble() ?: 0.0,
        )
    }
    return null
}

/** Seconds as the header writes them: "0:12.3", "12:04.0" - a decimal point in every language. */
fun clockText(seconds: Double): String {
    val tenths = (maxOf(0.0, seconds) * 10.0).roundToLong()
    val minutes = tenths / 600
    val rest = tenths % 600
    return String.format(Locale.ROOT, "%d:%02d.%d", minutes, rest / 10, rest % 10)
}

/** The origin of a history step in words: GO, by name, trigger, press (LoadToTime.cpp `originWord`). */
fun originKey(origin: Char): String? = when (origin) {
    'g' -> "remote.origin.go"
    'f' -> "remote.origin.byName"
    't' -> "remote.origin.trigger"
    'p' -> "remote.origin.press"
    else -> null
}

/**
 * THE LAST REFUSAL OF THIS TABLET, AS A SENTENCE (Transport.cpp `errorLine`): what was
 * refused and why, the engine's reason word kept as it is, with the refusals an operator
 * meets mid-show - a bounced GO, Doh!'s four - said in words, each naming what to do about
 * it. A record that does not parse is shown whole, so a format change is seen rather than
 * swallowed. [audioStatus] "noClock" takes the line first, as on the desktop: the cues it
 * paused are the first thing anybody will ask about. Then [paused] (Go.dot's PRD §3.34),
 * said for as long as the show is held - and a press it refused said as that.
 */
fun refusalLine(refusal: Refusal?, dohWindowSeconds: Double, audioStatus: String = "", paused: Boolean = false): Said? {
    if (audioStatus == "noClock") return Said("remote.refusal.noClock")
    if (paused) {
        val command = refusal?.command
        return if (command != null && refusal.reason == "paused")
            Said("remote.refusal.ignoredPaused", "command" to if (command == "go") "GO" else command)
        else Said("remote.refusal.paused")
    }
    refusal ?: return null
    val command = refusal.command ?: return Said.verbatim(refusal.raw.trim())
    val reason = refusal.reason

    if (refusal.detail != null) {
        return if (command == "node.setMany") Said("remote.refusal.setMany", "reason" to reason, "where" to refusal.detail)
               else Said.verbatim(refusal.raw.trim())
    }
    when (command) {
        "go" -> if (reason == "too-soon") return Said("remote.refusal.goTooSoon")
        "go.doh" -> when (reason) {
            "too-late" -> return Said(if (dohWindowSeconds > 0.0) "remote.refusal.dohTooLate" else "remote.refusal.dohOff")
            "nothing-to-take-back" -> return Said("remote.refusal.dohNothing")
            "trigger-after-go" -> return Said("remote.refusal.dohTrigger")
            "too-soon" -> return Said("remote.refusal.dohTooSoon")
        }
        "go.flag" -> when (reason) {
            "nothing-to-flag" -> return Said("remote.refusal.flagNothing")
            "unknown-id" -> return Said("remote.refusal.flagGone")
        }
    }
    return Said("remote.refusal.withCommand", "command" to command, "reason" to reason)
}
