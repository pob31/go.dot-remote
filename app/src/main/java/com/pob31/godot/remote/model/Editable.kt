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

import com.pob31.godot.remote.osc.OscValue

/*  WHAT THE TABLET MAY CHANGE, AS ONE TABLE (namespace draft §68, ANO - the author's):
    a cue's level, its sends, its EQ, the DCAs, the waits, the speed, the on/off switches,
    flags and notes; a run's trim. NEVER THE SHAPE OF THE CUE LIST: no cue made, deleted,
    moved, renamed or renumbered - no row here is a name, a number or an order, and no
    command the tabs send makes or moves anything (EditableTest holds both to Go.dot's
    parameter table and command list).

    The inspector draws these rows, in this order, for whichever of them the picked cue
    has: what a row IS - its type, range, unit, whether it may be written - comes from the
    mirror's node, never from here. What here says is which rows, how each is asked, and
    what the show lock does to it:

    - REFUSED: a row the show file records; the lock refuses it, so it is shown greyed
      with the word "Locked".
    - RIDES_LIVE: the EQ and the sends - under the lock they are ridden as a fader is,
      heard at once and written to nothing until somebody keeps them (the author,
      2026-09-25; decision AM; engine cue/LiveEdits.h), marked live while they do.
    - NOT_STORED: a run's trim, a DCA's trim, a flag - tonight's, or state beside the
      standby, which the lock lets through. */

enum class EditControl { FADER, RUN_TRIM, SENDS, EQ, DCA, NUMBER, CHOICE, SWITCH, TEXT }

enum class UnderLock { REFUSED, RIDES_LIVE, NOT_STORED }

data class Editable(
    /** The cue's row it edits (`/godot/cue/<id>/<row>`); a section's own word for the composite ones. */
    val row: String,
    val control: EditControl,
    val labelKey: String,
    val underLock: UnderLock,
    /** Places a typed number is written with. */
    val decimals: Int = 1,
    /** A box that shows nought as nothing and sends "0" when cleared (FieldText). */
    val blankZero: Boolean = false,
    /** The kinds of cue it is offered for, or null for every cue that has the row. */
    val kinds: Set<String>? = null,
)

object Editables {
    val ALL: List<Editable> = listOf(
        Editable("level", EditControl.FADER, "cues.field.level", UnderLock.REFUSED),
        Editable("trim", EditControl.RUN_TRIM, "cues.field.runTrim", UnderLock.NOT_STORED),
        Editable("sends", EditControl.SENDS, "cues.field.sends", UnderLock.RIDES_LIVE),
        Editable("eq", EditControl.EQ, "cues.field.eq", UnderLock.RIDES_LIVE),
        Editable("dca", EditControl.DCA, "cues.field.dca", UnderLock.REFUSED),
        Editable("preWait", EditControl.NUMBER, "cues.field.preWait", UnderLock.REFUSED, decimals = 3),
        Editable("postWait", EditControl.NUMBER, "cues.field.postWait", UnderLock.REFUSED, decimals = 3),
        // A media cue's speed; a fade's `rate` is where it takes its target, another question.
        Editable("rate", EditControl.NUMBER, "cues.field.rate", UnderLock.REFUSED, decimals = 4, kinds = setOf("media")),
        Editable("rateMode", EditControl.CHOICE, "cues.field.rateMode", UnderLock.REFUSED, kinds = setOf("media")),
        Editable("enabled", EditControl.SWITCH, "cues.field.enabled", UnderLock.REFUSED),
        Editable("flagged", EditControl.SWITCH, "cues.field.flagged", UnderLock.NOT_STORED),
        Editable("notes", EditControl.TEXT, "cues.field.notes", UnderLock.REFUSED),
    )

    /**
     * The send rows a send's strip writes (`/godot/send/<id>/<row>`): its level, in or out,
     * and its place in the mix (namespace draft §113) - ridden live under the lock, as the
     * level is.
     */
    val SEND_ROWS = listOf("level", "on", "pan", "width", "balance", "swap")

    /** The DCA row its fader writes (`/godot/dca/<id>/trim`): tonight's, never stored. */
    const val DCA_TRIM = "trim"

    /** The EQ's switches besides its sixteen controls' own rows, which come from the surface tables. */
    val EQ_SWITCHES = listOf("eqOn", "eqHpf", "eqB1On", "eqB2On", "eqB3On", "eqB4On", "eqLpf")

    /**
     * Every row the tablet may write, as (owner word, row): the allow-list, the EQ rows from
     * [tables], the send rows, the run's trim and the DCA's trim. What EditableTest holds
     * to Go.dot's parameter table.
     */
    fun writableRows(tables: SurfaceTables): Set<Pair<String, String>> {
        val out = LinkedHashSet<Pair<String, String>>()
        for (each in ALL) {
            when (each.control) {
                EditControl.FADER, EditControl.NUMBER, EditControl.CHOICE, EditControl.SWITCH, EditControl.TEXT, EditControl.DCA ->
                    out += "cue" to each.row
                EditControl.RUN_TRIM -> out += "run" to "trim"
                EditControl.SENDS -> SEND_ROWS.forEach { out += "send" to it }
                EditControl.EQ -> {
                    EQ_SWITCHES.forEach { out += "cue" to it }
                    tables.eqControls.forEach { out += "cue" to it.row }
                }
            }
        }
        out += "dca" to DCA_TRIM
        return out
    }

    /** The commands the cue list and the inspector send - no structure among them. */
    val COMMANDS = setOf("standby.set", "cue.fire", "list.focus", "node.touch", "node.release")
}

// ------------------------------------------------------------------------- the reading

/** One number, switch, choice or text row of a cue, read off its node. */
data class FieldLine(
    val address: String,
    val value: OscValue?,
    val unit: String?,
    val min: Double?,
    val max: Double?,
    val choices: List<String>,
    /** The node says it may be written. */
    val writable: Boolean,
    /** The lock refuses it now: greyed, with the word. */
    val lockedOut: Boolean,
) {
    val number: Double? get() = value?.asDouble()
    val text: String get() = value?.text() ?: ""
    val flag: Boolean get() = value?.asBoolean() == true
    val editable: Boolean get() = writable && !lockedOut
}

data class SendLine(
    val id: String,
    /** The mix it feeds: a bus, or an aux - a shared rack channel (namespace draft §93). */
    val bus: String,
    val busName: String,
    val level: Double,
    val on: Boolean,
    /** A locked show rides it live, unsaved (`send/live`). */
    val live: Boolean,
    /**
     * Which of its place's rows are heard (`send/placement`, the engine's word, derived): pan
     * where a mono cue meets a stereo mix, stereo where two channels meet two (balance, width,
     * swap), fold where a stereo cue meets a mono mix (balance), none otherwise.
     */
    val placement: String = PLACE_NONE,
    val pan: Double = 0.0,
    val width: Double = 1.0,
    val balance: Double = 0.0,
    val swap: Boolean = false,
) {
    val levelAddress: String get() = "/godot/send/$id/level"
    val onAddress: String get() = "/godot/send/$id/on"
    fun address(row: String): String = "/godot/send/$id/$row"

    companion object {
        const val PLACE_NONE = "none"
        const val PLACE_PAN = "pan"
        const val PLACE_STEREO = "stereo"
        const val PLACE_FOLD = "fold"
    }
}

data class EqKnob(
    val control: EqControl,
    val address: String,
    val value: Double,
    /** A shape's word: peak, lowShelf, highShelf. */
    val shape: String,
    /** Its band's switch: off keeps the numbers and takes the band out. */
    val bandOn: Boolean,
    val live: Boolean,
)

data class EqSwitch(val row: String, val address: String, val on: Boolean, val live: Boolean)

data class EqReading(val on: EqSwitch?, val switches: List<EqSwitch>, val knobs: List<EqKnob>)

data class DcaChoice(val id: String, val name: String)

sealed class InspectorLine {
    abstract val editable: Editable

    data class Field(override val editable: Editable, val field: FieldLine) : InspectorLine()

    /** The run of this cue that plays now, and its trim. */
    data class RunTrim(override val editable: Editable, val runId: String, val state: String, val trim: Double) : InspectorLine() {
        val address: String get() = "/godot/run/$runId/trim"
    }

    data class Sends(override val editable: Editable, val sends: List<SendLine>, val lockedOut: Boolean) : InspectorLine()

    data class Eq(override val editable: Editable, val eq: EqReading, val lockedOut: Boolean) : InspectorLine()

    /** The DCA mark (a show row) and the trim of the DCA it names (tonight's, beside it). */
    data class Dca(
        override val editable: Editable,
        val field: FieldLine,
        val choices: List<DcaChoice>,
        val trim: Double?,
    ) : InspectorLine() {
        val current: String get() = this.field.text
        val trimAddress: String? get() = current.takeIf { it.isNotEmpty() }?.let { "/godot/dca/$it/trim" }
    }

    /** `enabled`, and beside it what an enable or disable cue switched for this run (`override`, read only). */
    data class Enabled(override val editable: Editable, val field: FieldLine, val override: String) : InspectorLine()
}

data class Inspection(
    val cue: CueLine,
    val locked: Boolean,
    /** How many changes a locked show rides live (`/godot/document/live`). */
    val liveCount: Int,
    val lines: List<InspectorLine>,
    /** The sound's file as Go.dot knows it (`cue/hash`): what its waveform is fetched by; empty for none. */
    val hash: String = "",
    /** Where the run of it playing now has got to, 0..1 of the file; null while none plays. */
    val playhead: Double? = null,
)

private fun words(text: String?): List<String> = text?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList()

/** The picked cue's rows, from [Editables.ALL], for whichever of them it has. Null when the cue is not there. */
fun readInspection(mirror: Mirror, cueId: String, tables: SurfaceTables): Inspection? {
    val base = "/godot/cue/$cueId/"
    if (mirror[base + "kind"] == null) return null
    val cue = cueLine(mirror, cueId)
    val locked = mirror.value("/godot/document/locked")?.asBoolean() == true
    val liveRows = words(mirror.text(base + "live")).toSet()

    fun field(address: String, editable: Editable): FieldLine? {
        val node = mirror[address] ?: return null
        val range = node.range.firstOrNull()
        return FieldLine(
            address = address,
            value = node.value,
            unit = node.unit,
            min = range?.min,
            max = range?.max,
            choices = range?.vals ?: emptyList(),
            writable = node.access == ACCESS_UNKNOWN || node.writable,
            lockedOut = locked && editable.underLock == UnderLock.REFUSED,
        )
    }

    val lines = ArrayList<InspectorLine>()
    for (each in Editables.ALL) {
        if (each.kinds != null && cue.kind !in each.kinds) continue
        when (each.control) {
            EditControl.FADER, EditControl.NUMBER, EditControl.CHOICE, EditControl.TEXT ->
                field(base + each.row, each)?.let { lines += InspectorLine.Field(each, it) }
            EditControl.SWITCH -> {
                val line = field(base + each.row, each) ?: continue
                lines += if (each.row == "enabled") InspectorLine.Enabled(each, line, mirror.text(base + "override") ?: "file")
                         else InspectorLine.Field(each, line)
            }
            EditControl.RUN_TRIM -> playingRunOf(mirror, cueId)?.let { (run, state) ->
                val trim = mirror.value("/godot/run/$run/trim")?.asDouble() ?: return@let
                lines += InspectorLine.RunTrim(each, run, state, trim)
            }
            EditControl.SENDS -> {
                if (cue.kind != "media" && cue.kind != "mic") continue
                if (mirror[base + "sends"] == null) continue
                lines += InspectorLine.Sends(each, readSends(mirror, cueId), locked && each.underLock == UnderLock.REFUSED)
            }
            EditControl.EQ -> {
                if (mirror[base + "eqOn"] == null) continue
                lines += InspectorLine.Eq(each, readEq(mirror, cueId, tables, liveRows), locked && each.underLock == UnderLock.REFUSED)
            }
            EditControl.DCA -> {
                val line = field(base + each.row, each) ?: continue
                val choices = words(mirror.text("/godot/dca/order")).ifEmpty { mirror.ids("dca") }
                    .map { DcaChoice(it, mirror.text("/godot/dca/$it/name") ?: it) }
                val trim = line.text.takeIf { it.isNotEmpty() }?.let { mirror.value("/godot/dca/$it/trim")?.asDouble() }
                lines += InspectorLine.Dca(each, line, choices, trim)
            }
        }
    }
    return Inspection(
        cue, locked, mirror.value("/godot/document/live")?.asLong()?.toInt() ?: 0, lines,
        hash = if (cue.kind == "media") mirror.text(base + "hash") ?: "" else "",
        playhead = if (cue.kind == "media") playheadOf(mirror, cueId) else null,
    )
}

/**
 * The newest run of [cue] a GO or a hand has let go and that is not over - not one the
 * horizon only made ready (`started` nought) - with its state.
 */
fun playingRunOf(mirror: Mirror, cue: String): Pair<String, String>? {
    for (id in words(mirror.text("/godot/run/order")).asReversed()) {
        val base = "/godot/run/$id/"
        if (mirror.text(base + "cue") != cue) continue
        val state = mirror.text(base + "state") ?: continue
        if (state == "preparing" || state == "done" || state == "failed") continue
        if ((mirror.value(base + "started")?.asLong() ?: 0L) <= 0L) continue
        return id to state
    }
    return null
}

/**
 * Where a sound's run that plays now stands in its file, 0..1 - its position over the
 * cue's `duration`, as the Running tab's strip; null while none plays or the length is
 * not known.
 */
fun playheadOf(mirror: Mirror, cue: String): Double? {
    val (run, state) = playingRunOf(mirror, cue) ?: return null
    if (state != "playing" && state != "stopping") return null
    val length = mirror.value("/godot/cue/$cue/duration")?.asDouble() ?: return null
    if (!(length > 0.0)) return null
    val position = mirror.value("/godot/run/$run/position")?.asDouble() ?: return null
    return (position / length).coerceIn(0.0, 1.0)
}

/**
 * The cue's sends, each with its mix channel's name (`cue/sends` lists them by identifier):
 * a bus's, or an aux's - a slot's, since an aux is a shared rack channel and no bus.
 */
fun readSends(mirror: Mirror, cueId: String): List<SendLine> =
    words(mirror.text("/godot/cue/$cueId/sends")).mapNotNull { id ->
        val base = "/godot/send/$id/"
        val bus = mirror.text(base + "bus") ?: return@mapNotNull null
        SendLine(
            id = id,
            bus = bus,
            busName = mirror.text("/godot/bus/$bus/name")?.takeIf { it.isNotEmpty() }
                ?: mirror.text("/godot/slot/$bus/name")?.takeIf { it.isNotEmpty() } ?: bus,
            level = mirror.value(base + "level")?.asDouble() ?: -120.0,
            on = mirror.value(base + "on")?.asBoolean() != false,
            live = mirror.value(base + "live")?.asBoolean() == true,
            placement = mirror.text(base + "placement")?.takeIf { it.isNotEmpty() } ?: SendLine.PLACE_NONE,
            pan = mirror.value(base + "pan")?.asDouble() ?: 0.0,
            width = mirror.value(base + "width")?.asDouble() ?: 1.0,
            balance = mirror.value(base + "balance")?.asDouble() ?: 0.0,
            swap = mirror.value(base + "swap")?.asBoolean() == true,
        )
    }

fun readEq(mirror: Mirror, cueId: String, tables: SurfaceTables, liveRows: Set<String>): EqReading {
    val base = "/godot/cue/$cueId/"
    fun switch(row: String) = mirror[base + row]?.let { EqSwitch(row, base + row, it.value?.asBoolean() == true, row in liveRows) }
    val switches = Editables.EQ_SWITCHES.drop(1).mapNotNull { switch(it) }
    val bandOn = switches.associate { it.row to it.on }
    val knobs = tables.eqControls.mapNotNull { control ->
        val node = mirror[base + control.row] ?: return@mapNotNull null
        EqKnob(
            control = control,
            address = base + control.row,
            value = node.value?.asDouble() ?: control.min,
            shape = if (control.law == EqLaw.SHAPE) node.text ?: "peak" else "",
            bandOn = bandOn[control.switchRow] ?: true,
            live = control.row in liveRows,
        )
    }
    return EqReading(switch("eqOn"), switches, knobs)
}
