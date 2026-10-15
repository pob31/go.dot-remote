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

/*  THE RUNNING TAB'S READING (A.5): every run the engine publishes, as rows - Go.dot's
    client/model/RunModel.{h,cpp} ported for what the tablet shows. Present tense: a
    finished run is kept by the engine for five seconds and then stops being published,
    so nothing here decides what to forget. Read afresh each pass (at most 25 a second),
    by address: a pane of runs is a dozen rows, not five hundred.

    FLAT, AND IN THE ORDER THINGS HAPPENED (RunModel's `inShowOrder`): siblings by when
    they started, the not-yet-started last - where the next cue belongs - and each run's
    members directly under it, indented by how deep they sit, the holder's name said on
    the member's row. */

/** A run's colour where it has got to (`run/timbre`, "h s l": hue in degrees, then 0..1, 0..1). */
data class Timbre(val hue: Double, val saturation: Double, val lightness: Double) {
    companion object {
        /** Null for anything but three numbers - an empty reading is "not analysed yet", said elsewhere. */
        fun parse(text: String): Timbre? {
            val parts = text.trim().split(Regex("\\s+"))
            if (parts.size != 3) return null
            val numbers = parts.map { it.toDoubleOrNull() ?: return null }
            if (numbers.any { !it.isFinite() }) return null
            return Timbre(numbers[0], numbers[1], numbers[2])
        }
    }
}

/** Where a member of a group's round stands, for its pill (PRD §3.6, "Round pills"). */
enum class PillState {
    /** In the round, not reached yet: drawn plainly; a tap prunes it for this round. */
    PENDING,
    /** In the round and sounding now. */
    PLAYING,
    /** In the round and over: dimmed. */
    PLAYED,
    /** A member that is not in this round - pruned for it, or not drawn: dimmed, as played (same meaning). */
    OUT_THIS_ROUND,
    /** Pruned until the group's run ends: dimmed with a cross; a tap puts it back from the next round. */
    PRUNED,
}

data class Pill(val cue: String, val number: String, val name: String, val state: PillState)

data class RunRow(
    val id: String,
    val cue: String,
    val number: String,
    val name: String,
    val kind: String,
    val mode: String = "",
    val verb: String = "",
    /** preparing, waiting, armed, playing, stopping, postWait, done, failed. */
    val state: String,
    /** A group run's part: entering, preparing, prepared, header, members, footer; empty otherwise. */
    val phase: String = "",
    val position: Double = 0.0,
    val remaining: Double = 0.0,
    val started: Long = 0,
    val level: Double? = null,
    val trim: Double? = null,
    val meter: Double? = null,
    /** Null while the analyser has not reached the file ([timbreUnsaid]) or the run has none. */
    val timbre: Timbre? = null,
    /** The run's timbre row is there and empty: the file is not analysed yet. */
    val timbreUnsaid: Boolean = false,
    val rate: Double = 1.0,
    val parentRun: String = "",
    /** The holder's cue as a line says it ("2 Storm"); empty at the top. */
    val parentLabel: String = "",
    val depth: Int = 0,
    val pills: List<Pill> = emptyList(),
    val seekable: Boolean = false,
    val solo: Boolean = false,
    val held: Boolean = false,
    /** The strip a sampler member holds, by identifier; empty for every other run. */
    val strip: String = "",
    val error: String = "",
    /** The range playing, from nought; -1 when the run is in none. */
    val range: Int = -1,
    val iteration: Int = 0,
    val iterations: Int = 1,
    val roundFrom: Double = 0.0,
    val roundLength: Double = 0.0,
    /** The cue's `duration`: the file's length; nought when nobody measured it. */
    val length: Double = 0.0,
    /** How long the wait it is in is, from the cue: its pre-wait, or its post-wait. */
    val waitTotal: Double = 0.0,
    /** How many runs sit under this one: what a kill of it takes with it. */
    val descendants: Int = 0,
) {
    val icon: GodotIcon get() = iconFor(kind, mode, verb)
    val finished: Boolean get() = state == "done" || state == "failed"

    /** A pre-wait or a post-wait counting down: the time LEFT is what is shown. */
    val counting: Boolean get() = state == "waiting" || state == "postWait"

    /** Once a launch was placed, `position` means something (gogo.js `launched`). */
    val launched: Boolean get() = state != "preparing" && state != "waiting" && state != "armed" && state != "failed"

    val stateKey: String? get() = if (state in RunLine.STATES) "remote.runState.$state" else null

    val kindKey: String? get() = if (kind in CueLine.KINDS) "remote.kind.$kind" else null

    /** A sound's run, which the tablet's trim fader rides (`run/trim`, tonight's and never stored). */
    val hasTrim: Boolean get() = (kind == "media" || kind == "mic") && !finished && trim != null

    val trimAddress: String get() = "/godot/run/$id/trim"

    /** "round 2/3", "round 2/∞" for a scene that plays more than one round; null otherwise (RunModel `countWords`). */
    val roundCount: Pair<Int, Int>? get() = if (kind == "group" && iteration >= 1 && iterations != 1) iteration to iterations else null

    /**
     * The stretch the strip is drawn over and a scrub held to, in the run's own seconds
     * (RunModel `stripSpan`): a scene's solved round, a sound's file; null when unknown.
     */
    fun span(): Pair<Double, Double>? = when {
        kind == "group" -> if (roundLength > 0.0) roundFrom to roundFrom + roundLength else null
        length > 0.0 -> 0.0 to length
        else -> null
    }

    /** Whether a drag on its strip is a seek: the engine says a seek would move it, and the strip has a length. */
    val scrubbable: Boolean get() = seekable && state == "playing" && span() != null

    /** Where the head sits on the strip, 0..1; null with no span. */
    fun through(): Double? {
        val (from, to) = span() ?: return null
        return ((position - from) / (to - from)).coerceIn(0.0, 1.0)
    }
}

/** What a run's sheet offers to stop it with, in the order offered (the engine's `run.stop` verbs). */
enum class StopVerb(val word: String, val key: String) {
    /** Now: members come down in order and the footer runs (§4.4's graceful stop). */
    HARD("hard", "running.stopNow"),
    /** At the end of the member playing - a group's boundary. */
    AFTER_MEMBER("afterMember", "running.stopAfterMember"),
    /** At the end of this round - how an endless loop is left without a cut. */
    AFTER_ROUND("afterIteration", "running.stopAfterRound"),
}

/** A command to send: its name and its arguments, every one a string (contract §6). */
data class Sent(val command: String, val args: List<String>) {
    constructor(command: String, vararg args: String) : this(command, args.toList())
}

/**
 * WHAT A GESTURE ON THE RUNNING TAB SENDS, one table so a test reads it with no screen.
 * `run.stop` takes only `hard`, `afterMember` and `afterIteration` (RunCommands.cpp: a
 * fade-and-stop is a stop CUE, whose time somebody decided; `advance` is its own command,
 * for a run in a range). A boundary is a group's: against anything else the engine stops
 * at once, so only a group's sheet offers them.
 */
object RunActions {
    fun stopVerbs(row: RunRow): List<StopVerb> = when {
        row.finished -> emptyList()
        row.kind == "group" -> listOf(StopVerb.HARD, StopVerb.AFTER_MEMBER, StopVerb.AFTER_ROUND)
        else -> listOf(StopVerb.HARD)
    }

    fun stop(row: RunRow, verb: StopVerb) = Sent("run.stop", row.id, verb.word)

    /** The kill: now, no footer; a group's takes every run under it. The swipe's and the sheet's. */
    fun kill(row: RunRow): Sent? = if (row.finished) null else Sent("run.kill", row.id)

    /** Leave the range at the end of its pass: only a run that is in one (`run/range` >= 0). */
    fun advance(row: RunRow): Sent? = if (row.finished || row.range < 0) null else Sent("run.advance", row.id)

    /** A sampler clip soloed on its strip, or let go: only a run that holds a strip. */
    fun solo(row: RunRow): Sent? = if (row.finished || row.strip.isEmpty()) null else Sent("run.solo", row.id, if (row.solo) "false" else "true")

    fun seek(row: RunRow, seconds: Double) = Sent("run.seek", row.id, com.pob31.godot.remote.link.WireText.number(seconds, 3))

    /**
     * A pill tapped (PRD §3.6): a member still to come is dropped from this round; one
     * pruned until the group ends is put back (from the next round - the engine does not
     * redraw a round in progress). Nothing for one already played or out of this round.
     */
    fun pillTap(row: RunRow, pill: Pill): Sent? = when (pill.state) {
        PillState.PENDING -> Sent("run.prune", row.id, pill.cue, "round")
        PillState.PRUNED -> Sent("run.unprune", row.id, pill.cue)
        else -> null
    }

    /** A pill held: dropped until the group's run ends ("Modifier/double-click", PRD §3.6). */
    fun pillHold(row: RunRow, pill: Pill): Sent? = when (pill.state) {
        PillState.PRUNED -> Sent("run.unprune", row.id, pill.cue)
        else -> Sent("run.prune", row.id, pill.cue, "group")
    }
}

private fun words(text: String?): List<String> = text?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList()

/** Every run the engine publishes, in show order, nested by parent. */
fun readRuns(mirror: Mirror): List<RunRow> {
    val order = words(mirror.text("/godot/run/order"))
    if (order.isEmpty()) return emptyList()

    val parentOf = HashMap<String, String>(order.size * 2)
    for (id in order) parentOf[id] = mirror.text("/godot/run/$id/parent") ?: ""

    val rows = ArrayList<RunRow>(order.size)
    for (id in order) rows += readRun(mirror, id, parentOf)

    val byId = rows.associateBy { it.id }
    val withPills = rows.map { row ->
        val parentLabel = byId[row.parentRun]?.let { lineOf(it.number, it.name) } ?: ""
        val pills = if (row.kind == "group") pillsOf(mirror, row, rows) else emptyList()
        row.copy(parentLabel = parentLabel, pills = pills, descendants = countUnder(row.id, rows))
    }
    return inShowOrder(withPills)
}

private fun lineOf(number: String, name: String) = if (number.isEmpty()) name else if (name.isEmpty()) number else "$number $name"

private fun countUnder(id: String, rows: List<RunRow>): Int {
    var count = 0
    val frontier = ArrayDeque(listOf(id))
    val seen = HashSet<String>()
    while (frontier.isNotEmpty() && count < rows.size) {
        val at = frontier.removeFirst()
        if (!seen.add(at)) continue
        for (row in rows) if (row.parentRun == at) { count++; frontier.addLast(row.id) }
    }
    return count
}

private fun readRun(mirror: Mirror, id: String, parentOf: Map<String, String>): RunRow {
    val base = "/godot/run/$id/"
    fun text(row: String) = mirror.text(base + row) ?: ""
    fun number(row: String) = mirror.value(base + row)?.asDouble()
    fun flag(row: String) = mirror.value(base + row)?.asBoolean() == true

    val cue = text("cue")
    val cueBase = "/godot/cue/$cue/"
    val label = if (cue.isEmpty()) CueLabel("", "", "") else cueLabel(mirror, cue)
    val state = text("state").ifEmpty { "armed" }
    val timbreNode = mirror[base + "timbre"]
    val timbreText = timbreNode?.text

    var depth = 0
    var up = parentOf[id] ?: ""
    while (up.isNotEmpty() && depth < 64) {
        depth++
        up = parentOf[up] ?: ""
    }

    val kind = text("kind")
    val waitRow = if (state == "postWait") "postWait" else "preWait"
    return RunRow(
        id = id,
        cue = cue,
        number = label.number,
        name = label.name,
        kind = kind,
        mode = if (cue.isEmpty()) "" else mirror.text(cueBase + "mode") ?: "",
        verb = if (cue.isEmpty()) "" else mirror.text(cueBase + "verb") ?: "",
        state = state,
        phase = text("phase"),
        position = number("position") ?: 0.0,
        remaining = number("remaining") ?: 0.0,
        started = mirror.value(base + "started")?.asLong() ?: 0L,
        level = number("level"),
        trim = number("trim"),
        meter = number("meter"),
        timbre = timbreText?.let { Timbre.parse(it) },
        timbreUnsaid = timbreNode != null && timbreText.isNullOrBlank(),
        rate = number("rate") ?: 1.0,
        parentRun = parentOf[id] ?: "",
        depth = depth,
        seekable = flag("seekable"),
        solo = flag("solo"),
        held = flag("held"),
        strip = text("strip"),
        error = text("error"),
        range = number("range")?.toInt() ?: -1,
        iteration = number("iteration")?.toInt() ?: 0,
        iterations = number("iterations")?.toInt() ?: 1,
        roundFrom = number("roundFrom") ?: 0.0,
        roundLength = number("roundLength") ?: 0.0,
        length = if (cue.isEmpty()) 0.0 else mirror.value(cueBase + "duration")?.asDouble() ?: 0.0,
        waitTotal = if (cue.isEmpty()) 0.0 else mirror.value(cueBase + waitRow)?.asDouble() ?: 0.0,
    )
}

/**
 * THE ROUND'S PILLS (PRD §3.6): the round in progress in the order it will play, each
 * member's state from the latest of this run's members' runs of it; then the members
 * not in this round, dimmed; then those pruned until the group ends, crossed. Nothing for
 * a group run that has not drawn a round and pruned nothing.
 */
fun pillsOf(mirror: Mirror, group: RunRow, runs: List<RunRow>): List<Pill> {
    val base = "/godot/run/${group.id}/"
    val round = words(mirror.text(base + "round"))
    val pruned = words(mirror.text(base + "pruned"))
    if (round.isEmpty() && pruned.isEmpty()) return emptyList()

    val latest = HashMap<String, RunRow>()
    for (run in runs) if (run.parentRun == group.id) latest[run.cue] = run // run order is creation order: the last wins

    fun pill(cue: String, state: PillState): Pill {
        val label = cueLabel(mirror, cue)
        return Pill(cue, label.number, label.name, state)
    }

    val out = ArrayList<Pill>()
    for (cue in round) {
        val run = latest[cue]
        val state = when (run?.state) {
            "waiting", "playing", "stopping", "postWait" -> PillState.PLAYING
            "done", "failed" -> PillState.PLAYED
            else -> PillState.PENDING
        }
        out += pill(cue, state)
    }
    if (round.isNotEmpty() && group.cue.isNotEmpty()) {
        for (cue in words(mirror.text("/godot/cue/${group.cue}/order"))) {
            if (cue !in round && cue !in pruned) out += pill(cue, PillState.OUT_THIS_ROUND)
        }
    }
    for (cue in pruned) out += pill(cue, PillState.PRUNED)
    return out
}

/**
 * The rows in the order the show happened, each parent's members under it (RunModel
 * `inShowOrder`): siblings by `started`, the not-started last, stable within a tie. A run
 * whose parent is not here is a root, so no run goes undrawn - a run left out is a run
 * nobody can kill.
 */
fun inShowOrder(rows: List<RunRow>): List<RunRow> {
    val present = rows.mapTo(HashSet()) { it.id }
    val children = HashMap<String, MutableList<RunRow>>()
    val roots = ArrayList<RunRow>()
    for (row in rows) {
        if (row.parentRun.isEmpty() || row.parentRun !in present) roots += row
        else children.getOrPut(row.parentRun) { ArrayList() } += row
    }
    val byStart = compareBy<RunRow> { if (it.started > 0) it.started else Long.MAX_VALUE }
    val out = ArrayList<RunRow>(rows.size)
    fun walk(row: RunRow) {
        if (out.size >= rows.size) return
        out += row
        children[row.id]?.sortedWith(byStart)?.forEach { walk(it) }
    }
    roots.sortedWith(byStart).forEach { walk(it) }
    return if (out.size == rows.size) out else rows
}
