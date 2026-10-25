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

/*  THE SURFACE TAB'S READING (A.7): the show's control surfaces and their strips, Go.dot's
    client/model/Surfaces.{h,cpp} ported for what the tablet draws. "A surface that can be
    used as a replacement when the MIDI devices aren't available or when walking around
    the house away from the régie" (the author, 2026-10-15).

    WHAT A STRIP IS DOING IS THE ENGINE'S ANSWER, read and never worked out: a strip is a
    slot (`/godot/slot/<id>`, `kind` strip) whose `target` is the node its fader rides NOW
    - a run's trim, a DCA's trim, or nothing - and whose `word` says what it is doing. A
    declared MIDI surface that is not connected still has its strips filled, so the tablet
    stands in for it. Besides the show's surfaces there is always one more, "This tablet":
    a strip for every DCA and nothing else, so the tablet is a surface even in a show that
    declares none.

    WHICH PAGE IS UP IS THE TABLET'S OWN, as a D700's is its own and no command (engine
    surface/SurfaceProfile.h): the faders, the aimed cue's EQ, its sends, or where its sends
    sit in their mixes (the D700's Pan pages, namespace draft §113). The aim is the engine's
    (`/godot/surface/aim`), shared with every surface and the window. */

/** The surface every show has on this tablet: one strip a DCA. Not an engine identifier. */
const val THIS_TABLET = "tablet"

/** The tablet's pages: the strips, the aimed cue's EQ, its sends, their places. */
enum class SurfacePage(val key: String) {
    FADERS("surface.page.faders"),
    EQ("surface.page.eq"),
    SENDS("surface.page.sends"),
    PAN("surface.page.pan"),
}

/** How a surface stands, in the desktop's order (Surfaces.cpp `stateWord`): off first, then connected. */
enum class SurfaceState { TABLET, OFF, CONNECTED, PROBLEM, NOT_CONNECTED }

/** One declared control surface (`/godot/surface/<id>`), or [THIS_TABLET]. */
data class SurfaceLine(
    val id: String,
    val name: String,
    /** virtual, mcu, d700, midiPads, streamDeck; empty for the tablet's. */
    val profile: String,
    val enabled: Boolean = true,
    val connected: Boolean = false,
    /** The engine's sentence when it cannot reach it; never rewritten here. */
    val problem: String = "",
    val stripCount: Int = 0,
) {
    val isTablet: Boolean get() = id == THIS_TABLET

    /** The key of its profile's words ("Asparion D700"), or null for a profile this app has no word for. */
    val profileKey: String? get() = if (profile in PROFILES) "surface.profile.$profile" else null

    val state: SurfaceState
        get() = when {
            isTablet -> SurfaceState.TABLET
            !enabled -> SurfaceState.OFF
            connected || profile == "virtual" || profile.isEmpty() -> SurfaceState.CONNECTED
            problem.isNotEmpty() -> SurfaceState.PROBLEM
            else -> SurfaceState.NOT_CONNECTED
        }

    companion object {
        val PROFILES = setOf("virtual", "mcu", "d700", "midiPads", "streamDeck")
    }
}

/** One strip and what is on it, looked up once a reading. */
data class StripLine(
    val id: String,
    val index: Int,
    /** sampler | dca. */
    val role: String,
    val dca: String = "",
    /** absolute (a fader) | gate (a pad). */
    val endpoint: String = "absolute",
    /** The node its fader rides now: `/godot/run/<id>/trim`, `/godot/dca/<id>/trim`; empty when it rides nothing. */
    val target: String = "",
    /** free, dca, unassigned, armed, pending, playing, held, stopping, closing, lane, rec, off, recording. */
    val word: String = "",
    val cue: String = "",
    /** The run holding it, or empty. */
    val holder: String = "",
    /** The cue's number, or empty for a DCA strip. */
    val number: String = "",
    /** The cue's short name or name; a DCA strip's DCA short name or name; empty for a free strip. */
    val name: String = "",
    /** The cue's kind: SELECT aims only a sound (`surface.aim` refuses anything else). */
    val cueKind: String = "",
    /** The value under the fader, read off [target]; null when it rides nothing. */
    val level: Double? = null,
    /** The holder run's meter; null with no holder. */
    val meter: Double? = null,
    /** A pad is down on the holder somewhere (`run/held`). */
    val held: Boolean = false,
    /** Its cue is the one the rotaries edit (`/godot/surface/aim`). */
    val aimed: Boolean = false,
) {
    val isPad: Boolean get() = endpoint == "gate"
    val isDca: Boolean get() = role == "dca"

    /** A fader that a finger can take: it rides something. */
    val rides: Boolean get() = target.isNotEmpty()

    /** Nothing on it: said in words, never drawn as a blank. */
    val empty: Boolean get() = if (isDca) dca.isEmpty() else cue.isEmpty()

    /** The key of its word, or null for one this app has no word for (shown as the engine spells it). */
    val wordKey: String? get() = if (word in WORDS) "surface.word.$word" else null

    /** SELECT is offered on a sound's strip: it aims the EQ and Send pages at that cue. */
    val selectable: Boolean get() = !isDca && cue.isNotEmpty() && cueKind == "media"

    companion object {
        val WORDS = setOf("free", "dca", "unassigned", "armed", "pending", "playing", "held", "stopping", "closing", "lane", "rec", "off", "recording")
    }
}

/** A sound playing now that the rotaries could be aimed at. */
data class AimChoice(val cue: String, val number: String, val name: String, val state: String)

data class SurfaceReading(
    /** The show's surfaces in its order, then [THIS_TABLET]. */
    val surfaces: List<SurfaceLine> = listOf(SurfaceLine(THIS_TABLET, "", "")),
    /** The one shown: the one asked for while it exists, else the first declared, else the tablet. */
    val shown: SurfaceLine = surfaces.last(),
    val strips: List<StripLine> = emptyList(),
    /** `/godot/surface/aim`; empty when nothing is aimed at. */
    val aim: String = "",
    /** The aimed cue, or null when the aim names no cue the tablet has. */
    val aimCue: CueLine? = null,
    /** The aimed cue's EQ; null when it has none. */
    val eq: EqReading? = null,
    val sends: List<SendLine> = emptyList(),
    /** The sounds playing now, to aim at when nothing is. */
    val aimChoices: List<AimChoice> = emptyList(),
    val locked: Boolean = false,
    val liveCount: Int = 0,
)

/** What a hand on the Surface tab sends: one table, read by a test with no screen. */
object StripActions {
    /** SELECT: the EQ and Send pages edit this strip's cue. */
    fun select(strip: StripLine): Sent? = if (strip.selectable) Sent("surface.aim", strip.cue) else null

    fun aim(cue: String) = Sent("surface.aim", cue)

    /**
     * A pad struck: a sampler strip's clip starts, at a velocity from where it was struck
     * - the top a hard hit, the bottom a soft one (the desktop panel's 1..127). A DCA
     * strip's pad is its gate, which puts the trim back at unity: [dcaUnity], not this.
     */
    fun press(strip: StripLine, velocity: Int): Sent? =
        if (strip.isDca || strip.empty) null else Sent("strip.press", strip.id, velocity.coerceIn(1, 127).toString())

    fun release(strip: StripLine): Sent? = if (strip.isDca || strip.empty) null else Sent("strip.release", strip.id)

    /** A DCA strip's pad: its trim back at nought decibels, one `node.set` and nothing to let go of. */
    fun dcaUnity(strip: StripLine): Pair<String, String>? = if (strip.isDca && strip.rides) strip.target to "0" else null

    /** Where on a pad a finger landed, 0 at the bottom to 1 at the top, as a velocity. */
    fun velocityAt(height: Double): Int = (1 + Math.round(height.coerceIn(0.0, 1.0) * 126.0)).toInt()
}

private fun words(text: String?): List<String> = text?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList()

/** The show's surfaces in `/godot/surface/order` (the identifiers the mirror holds when it has none), then the tablet's. */
fun readSurfaceLines(mirror: Mirror): List<SurfaceLine> {
    val ids = words(mirror.text("/godot/surface/order")).ifEmpty { mirror.ids("surface") }
    val declared = ids.mapNotNull { id ->
        val base = "/godot/surface/$id/"
        if (mirror[base + "profile"] == null && mirror[base + "name"] == null) return@mapNotNull null
        SurfaceLine(
            id = id,
            name = mirror.text(base + "name") ?: "",
            profile = mirror.text(base + "profile") ?: "",
            enabled = mirror.value(base + "enabled")?.asBoolean() != false,
            connected = mirror.value(base + "connected")?.asBoolean() == true,
            problem = mirror.text(base + "problem") ?: "",
            stripCount = mirror.value(base + "strips")?.asLong()?.toInt() ?: 0,
        )
    }
    return declared + SurfaceLine(THIS_TABLET, "", "")
}

/** The strips of one declared surface, in index order. */
fun readStrips(mirror: Mirror, surfaceId: String, aim: String): List<StripLine> {
    val out = ArrayList<StripLine>()
    for (id in mirror.ids("slot")) {
        val base = "/godot/slot/$id/"
        if (mirror.text(base + "kind") != "strip") continue
        if (mirror.text(base + "surface") != surfaceId) continue
        val role = mirror.text(base + "role") ?: "sampler"
        val dca = mirror.text(base + "dca") ?: ""
        val cue = mirror.text(base + "cue") ?: ""
        val holder = mirror.text(base + "holder") ?: ""
        val target = mirror.text(base + "target") ?: ""
        val isDca = role == "dca"
        val label = if (!isDca && cue.isNotEmpty()) cueLabel(mirror, cue) else null
        val shortName = if (!isDca && cue.isNotEmpty()) mirror.text("/godot/cue/$cue/shortName") ?: "" else ""
        out += StripLine(
            id = id,
            index = mirror.value(base + "index")?.asLong()?.toInt() ?: 0,
            role = role,
            dca = dca,
            endpoint = mirror.text(base + "endpoint") ?: "absolute",
            target = target,
            word = mirror.text(base + "word") ?: "",
            cue = cue,
            holder = holder,
            number = label?.number ?: "",
            // A DCA strip is called what its DCA is, the short name first - the ROLE decides, not the row.
            name = if (isDca) dcaName(mirror, dca) else shortName.ifEmpty { label?.name ?: "" },
            cueKind = if (cue.isNotEmpty()) mirror.text("/godot/cue/$cue/kind") ?: "" else "",
            level = if (target.isNotEmpty()) mirror.value(target)?.asDouble() else null,
            meter = if (holder.isNotEmpty()) mirror.value("/godot/run/$holder/meter")?.asDouble() else null,
            held = holder.isNotEmpty() && mirror.value("/godot/run/$holder/held")?.asBoolean() == true,
            aimed = !isDca && cue.isNotEmpty() && cue == aim,
        )
    }
    // Stable: two strips claiming one index keep the mirror's order.
    return out.sortedBy { it.index }
}

/** The DCA's short name, else its name, else nothing (a strip with no DCA says so in its word). */
private fun dcaName(mirror: Mirror, dca: String): String {
    if (dca.isEmpty()) return ""
    val short = mirror.text("/godot/dca/$dca/shortName") ?: ""
    return short.ifEmpty { mirror.text("/godot/dca/$dca/name")?.takeIf { it.isNotEmpty() } ?: dca }
}

/** "This tablet": a fader for every DCA of the show, in `/godot/dca/order`, each riding that DCA's trim. */
fun tabletStrips(mirror: Mirror): List<StripLine> {
    val ids = words(mirror.text("/godot/dca/order")).ifEmpty { mirror.ids("dca") }
    return ids.mapIndexedNotNull { index, dca ->
        val trim = "/godot/dca/$dca/trim"
        if (mirror[trim] == null) return@mapIndexedNotNull null
        StripLine(
            id = "$THIS_TABLET:$dca",
            index = index,
            role = "dca",
            dca = dca,
            target = trim,
            word = "dca",
            name = dcaName(mirror, dca),
            level = mirror.value(trim)?.asDouble(),
        )
    }
}

/** The sounds playing now, newest last, one line a cue: what the pages can be aimed at. */
fun aimChoices(mirror: Mirror): List<AimChoice> {
    val seen = LinkedHashMap<String, AimChoice>()
    for (id in words(mirror.text("/godot/run/order"))) {
        val base = "/godot/run/$id/"
        val cue = mirror.text(base + "cue")?.takeIf { it.isNotEmpty() } ?: continue
        if (mirror.text("/godot/cue/$cue/kind") != "media") continue
        val state = mirror.text(base + "state") ?: continue
        if (state == "done" || state == "failed" || state == "preparing") continue
        val label = cueLabel(mirror, cue)
        seen[cue] = AimChoice(cue, label.number, label.name, state)
    }
    return seen.values.toList()
}

/**
 * The Surface tab's reading of [mirror]: the surfaces, the one [asked] for (the first
 * declared while it is null or gone), its strips, and the aimed cue's EQ and sends.
 */
fun readSurface(mirror: Mirror, asked: String?, tables: SurfaceTables): SurfaceReading {
    val surfaces = readSurfaceLines(mirror)
    val shown = surfaces.firstOrNull { it.id == asked } ?: surfaces.first()
    val aim = mirror.text("/godot/surface/aim") ?: ""
    val strips = if (shown.isTablet) tabletStrips(mirror) else readStrips(mirror, shown.id, aim)
    val aimCue = aim.takeIf { it.isNotEmpty() && mirror["/godot/cue/$it/kind"] != null }?.let { cueLine(mirror, it) }
    val liveRows = words(mirror.text("/godot/cue/$aim/live")).toSet()
    return SurfaceReading(
        surfaces = surfaces,
        shown = shown,
        strips = strips,
        aim = aim,
        aimCue = aimCue,
        eq = if (aimCue != null && mirror["/godot/cue/$aim/eqOn"] != null) readEq(mirror, aim, tables, liveRows) else null,
        sends = if (aimCue != null) readSends(mirror, aim) else emptyList(),
        aimChoices = aimChoices(mirror),
        locked = mirror.value("/godot/document/locked")?.asBoolean() == true,
        liveCount = mirror.value("/godot/document/live")?.asLong()?.toInt() ?: 0,
    )
}

/**
 * THE SEND PAGE'S ROTARIES, as the EQ page's are (engine SurfaceBridge `sendWrite`): a
 * send's level moves along the fader's own law, so its ring is where a fader would be;
 * a press switches the send in and out (`send/on` - off keeps the level). Made as the EQ
 * page's knobs so one rotary draws and writes both.
 */
fun sendKnobs(sends: List<SendLine>, fader: FaderCurve): EqReading {
    val switches = ArrayList<EqSwitch>(sends.size)
    val knobs = ArrayList<EqKnob>(sends.size)
    for (send in sends) {
        val switchRow = "send:${send.id}"
        switches += EqSwitch(switchRow, send.onAddress, send.on, send.live)
        knobs += EqKnob(
            control = EqControl(
                row = "level", label = send.busName, short = send.busName, law = EqLaw.LEVEL,
                min = fader.silenceDb, max = fader.loudestDb, colour = -1, switchRow = switchRow,
                press = EqPress.TOGGLE_SWITCH, shelf = "",
            ),
            address = send.levelAddress,
            value = send.level,
            shape = "",
            bandOn = send.on,
            live = send.live,
        )
    }
    return EqReading(null, switches, knobs)
}

/** A send's place as rotaries: the leans (a pan or a balance) and the widths of the stereo sends. */
data class PlaceKnobs(val leans: EqReading, val widths: EqReading) {
    val isEmpty: Boolean get() = leans.knobs.isEmpty() && widths.knobs.isEmpty()
}

/**
 * THE PAN PAGE'S ROTARIES, as the D700's Pan pages lay them (engine SurfacePages.h, namespace
 * draft §113): for each send, the row its placement says is heard - a pan where one channel
 * meets two, a balance where two meet two or one - in -1..1, a press putting it back in the
 * centre; then, for a send two channels wide into two, its width in 0..1, a press swapping
 * its sides (`send/swap`). A send with no place has neither. Each knob is named by its mix
 * channel, or by [byRow]'s word for its row where one send is drawn alone.
 */
fun placeKnobs(sends: List<SendLine>, byRow: Map<String, String>? = null): PlaceKnobs {
    val leans = ArrayList<EqKnob>(sends.size)
    val widths = ArrayList<EqKnob>()
    val swaps = ArrayList<EqSwitch>()
    for (send in sends) {
        val lean = when (send.placement) {
            SendLine.PLACE_PAN -> "pan"
            SendLine.PLACE_STEREO, SendLine.PLACE_FOLD -> "balance"
            else -> continue
        }
        fun named(row: String) = byRow?.get(row) ?: send.busName
        leans += EqKnob(
            control = EqControl(
                row = lean, label = named(lean), short = named(lean), law = EqLaw.PAN,
                min = -1.0, max = 1.0, colour = -1, switchRow = "", press = EqPress.CENTRE, shelf = "",
            ),
            address = send.address(lean),
            value = if (lean == "pan") send.pan else send.balance,
            shape = "",
            bandOn = send.on,
            live = send.live,
        )
        if (send.placement != SendLine.PLACE_STEREO) continue
        val swapRow = "swap:${send.id}"
        swaps += EqSwitch(swapRow, send.address("swap"), send.swap, send.live)
        widths += EqKnob(
            control = EqControl(
                row = "width", label = named("width"), short = named("width"), law = EqLaw.STEREO_WIDTH,
                min = 0.0, max = 1.0, colour = -1, switchRow = swapRow, press = EqPress.TOGGLE_SWITCH, shelf = "",
            ),
            address = send.address("width"),
            value = send.width,
            shape = "",
            bandOn = send.on,
            live = send.live,
        )
    }
    return PlaceKnobs(EqReading(null, emptyList(), leans), EqReading(null, swaps, widths))
}
