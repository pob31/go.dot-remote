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

import kotlin.math.abs
import kotlin.math.roundToLong

/*  THE CUES TAB'S LIST (A.6): one cue list as rows in the order they are drawn - Go.dot's
    client/model/ShowModel.{h,cpp} ported. A list's persistent section first, then its cues;
    a group's header, its members, its footer, each section framed by a head row that folds
    (§14.3: a header runs before its group and is not a member of it). A group's own row
    folds its members.

    FOLDS ARE THIS TABLET'S ([Folds]): the desktop writes a fold into the show's state;
    the remote changes nothing it does not have to, so it starts from the show's folds and
    keeps its own from there (§14.1: folds are per client).

    WHAT IS NOT IN A ROW moves at tick rate and is read per pass ([CueMarks]): the standby,
    the flag, an enable cue's override for this run. The rows themselves are rebuilt only
    when the show's revision, the list, the folds or the tree's shape move ([CueTreeCache]). */

/** Where a row sits in its container (ShowModel `Section`). */
enum class Section { PERSISTENT, HEADER, MEMBER, FOOTER }

data class CueRow(
    /** A head of a section (true) or a cue (false). */
    val band: Boolean,
    val id: String,
    val number: String = "",
    val name: String = "",
    val kind: String = "",
    val mode: String = "",
    val verb: String = "",
    val depth: Int = 0,
    val section: Section = Section.MEMBER,
    /** The group this row sits in, or the list at the top. */
    val parent: String = "",
    /** A head's and a group's fold key: the container and the section's word, or the group's id. */
    val foldKey: String = "",
    /** A head's: how many rows it frames. */
    val count: Int = 0,
    val shut: Boolean = false,
    val isGroup: Boolean = false,
    val enabled: Boolean = true,
    val colour: String = "",
    /** The three time columns as text: one decimal, a read-only nought blank (ShowModel `timeText`). */
    val preWait: String = "",
    val duration: String = "",
    val postWait: String = "",
) {
    val icon: GodotIcon get() = iconFor(kind, mode, verb)

    /** Decision X: a member of the list or a group - not a header, a footer or a persistent cue. */
    val mayPark: Boolean get() = !band && section == Section.MEMBER

    /**
     * Where a tap in the gutter sends the standby (ShowModel `parksOn`): the cue; a
     * header's or footer's line - its head too - parks on its group; nothing for a
     * persistent bed.
     */
    val parksOn: String
        get() = when {
            mayPark -> id
            section == Section.HEADER || section == Section.FOOTER -> if (parent.startsWith(LIST_MARK)) "" else parent
            else -> ""
        }

    val sectionKey: String? get() = if (band) "cues.section.${section.name.lowercase()}" else null

    companion object {
        /** Marks a parent that is a list rather than a group, so a section of a list parks nowhere. */
        const val LIST_MARK = "list:"
    }
}

/**
 * THE FOLDS THIS TABLET KEEPS. Seeded from the show's own fold flags and changed only by
 * a hand here; a flag the show changes later (another client's fold) is taken, a toggle
 * made here is never undone by the show's flag catching up (ShowModel `seedFold`).
 */
class Folds {
    private val shut = HashSet<String>()
    private val seen = HashMap<String, Boolean>()
    private val touched = HashSet<String>()

    /** Moves on every change, so a cache keyed on it rebuilds. */
    var version = 0L
        private set

    fun isShut(key: String) = key in shut

    fun toggle(key: String) {
        if (!shut.remove(key)) shut += key
        touched += key
        version++
    }

    /**
     * The show's flag for [key]: applied when it moved since last seen, and when first
     * seen unless a hand here already folded or opened it.
     */
    fun seed(key: String, flag: Boolean) {
        val before = seen[key]
        if (before == flag) return
        seen[key] = flag
        if (before == null && key in touched) return
        if (flag) shut += key else shut -= key
    }
}

private fun words(text: String?): List<String> = text?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList()

/** Builds one list's rows (ShowModel `walk`). [folds] is read and seeded. */
fun buildCueTree(mirror: Mirror, listId: String, folds: Folds): List<CueRow> {
    if (listId.isEmpty() || mirror["/godot/list/$listId/order"] == null) return emptyList()
    val out = ArrayList<CueRow>()
    val placed = HashSet<String>()
    walk(mirror, listId, true, 0, folds, out, placed)
    return out
}

private const val DEEPEST = 64

private fun walk(mirror: Mirror, container: String, isList: Boolean, depth: Int, folds: Folds, out: MutableList<CueRow>, placed: MutableSet<String>) {
    if (depth > DEEPEST) return
    val address = (if (isList) "/godot/list/" else "/godot/cue/") + container + "/"
    val parent = if (isList) CueRow.LIST_MARK + container else container

    if (isList) {
        // Drawn only with cues in it: the tablet makes nothing, so an empty bed is no door here.
        section(mirror, container, parent, isList, words(mirror.text(address + "persistentOrder")), Section.PERSISTENT, "persistent", depth, folds, out, placed)
    } else {
        section(mirror, container, parent, isList, words(mirror.text(address + "headerOrder")), Section.HEADER, "header", depth, folds, out, placed)
    }
    for (id in words(mirror.text(address + "order"))) append(mirror, id, Section.MEMBER, depth, parent, folds, out, placed)
    if (!isList) {
        section(mirror, container, parent, isList, words(mirror.text(address + "footerOrder")), Section.FOOTER, "footer", depth, folds, out, placed)
    }
}

private fun section(
    mirror: Mirror, container: String, parent: String, isList: Boolean, ids: List<String>, which: Section, word: String,
    depth: Int, folds: Folds, out: MutableList<CueRow>, placed: MutableSet<String>,
) {
    if (ids.isEmpty()) return
    val key = "$container/$word"
    val flag = (if (isList) "/godot/list/" else "/godot/cue/") + container + "/" + word + "Folded"
    folds.seed(key, mirror.value(flag)?.asBoolean() == true)
    val shut = folds.isShut(key)
    out += CueRow(band = true, id = key, name = word, depth = depth, section = which, parent = parent, foldKey = key, count = ids.size, shut = shut)
    if (shut) return
    for (id in ids) append(mirror, id, which, depth + 1, parent, folds, out, placed)
}

private fun append(mirror: Mirror, id: String, which: Section, depth: Int, parent: String, folds: Folds, out: MutableList<CueRow>, placed: MutableSet<String>) {
    // A cue drawn twice is a document that disagrees with itself: the first placement wins.
    if (!placed.add(id)) return
    val base = "/godot/cue/$id/"
    val label = cueLabel(mirror, id)
    val kind = mirror.text(base + "kind") ?: ""
    val isGroup = mirror[base + "order"] != null
    val isMovie = kind == "video" && mirror.text(base + "source") == "movie"
    val duration = when {
        kind == "media" || isMovie -> mediaTime(mirror, id)
        kind == "timecode" -> timeText(mirror, base + "length").let { if (it == "0.0" || it == "-0.0") "" else it }
        else -> timeText(mirror, base + "duration")
    }
    if (isGroup) folds.seed(id, mirror.value(base + "folded")?.asBoolean() == true)
    val shut = isGroup && folds.isShut(id)
    out += CueRow(
        band = false,
        id = id,
        number = label.number,
        name = label.name,
        kind = kind,
        mode = mirror.text(base + "mode") ?: "",
        verb = if (kind == "transport") mirror.text(base + "verb") ?: "" else "",
        depth = depth,
        section = which,
        parent = parent,
        foldKey = if (isGroup) id else "",
        count = if (isGroup) words(mirror.text(base + "order")).size else 0,
        shut = shut,
        isGroup = isGroup,
        enabled = mirror.value(base + "enabled")?.asBoolean() != false,
        colour = mirror.text(base + "colour") ?: "",
        preWait = timeText(mirror, base + "preWait"),
        duration = duration,
        postWait = timeText(mirror, base + "postWait"),
    )
    if (isGroup && !shut) walk(mirror, id, false, depth + 1, folds, out, placed)
}

/**
 * A TIME COLUMN'S TEXT (ShowModel `timeText`, didi.js): one decimal, written by hand so
 * no locale puts a comma in it; a READ-ONLY nought is blank, or the column would spell
 * nothing in two ways. A writable nought is a decision somebody made, and is shown. Empty
 * for a cue that has no such row.
 */
fun timeText(mirror: Mirror, address: String, per: Double = 1.0): String {
    val node = mirror[address] ?: return ""
    val seconds = (node.value?.asDouble() ?: return "") / per
    if (!seconds.isFinite()) return ""
    val written = oneDecimal(seconds)
    if (!node.writable && (written == "0.0" || written == "-0.0")) return ""
    return written
}

/** Seconds with one decimal, half away from nought, digit by digit. */
fun oneDecimal(seconds: Double): String {
    val tenths = (abs(seconds) * 10.0).roundToLong()
    val sign = if (seconds < 0 && tenths != 0L) "-" else ""
    return "$sign${tenths / 10}.${tenths % 10}"
}

/** A media cue's time is its file's at its own speed (§22.5): for ever at nought. */
private fun mediaTime(mirror: Mirror, id: String): String {
    val speed = abs(mirror.value("/godot/cue/$id/rate")?.asDouble() ?: 1.0)
    val written = timeText(mirror, "/godot/cue/$id/duration", if (speed > 0.0) speed else 1.0)
    return if (!(speed > 0.0) && written.isNotEmpty()) "∞" else written
}

/** What a row wears this pass, read at tick rate: standby, flag, override for this run, running. */
data class CueMarks(
    val standby: String = "",
    val flagged: Set<String> = emptySet(),
    /** Cue -> `on` or `off`: an enable or disable cue switched it for this run (`cue/override`). */
    val overrides: Map<String, String> = emptyMap(),
)

fun readMarks(mirror: Mirror, listId: String, rows: List<CueRow>): CueMarks {
    val flagged = HashSet<String>()
    val overrides = HashMap<String, String>()
    for (row in rows) {
        if (row.band) continue
        if (mirror.value("/godot/cue/${row.id}/flagged")?.asBoolean() == true) flagged += row.id
        val word = mirror.text("/godot/cue/${row.id}/override")
        if (word == "on" || word == "off") overrides[row.id] = word
    }
    return CueMarks(
        standby = if (listId.isEmpty()) "" else mirror.text("/godot/list/$listId/standby") ?: "",
        flagged = flagged,
        overrides = overrides,
    )
}

/** A list as the picker shows it. */
data class ListLine(val id: String, val name: String)

fun readLists(mirror: Mirror): List<ListLine> =
    words(mirror.text("/godot/list/order")).map { ListLine(it, mirror.text("/godot/list/$it/name") ?: "") }

/**
 * THE ROWS, REBUILT ONLY WHEN THEY CAN HAVE CHANGED (ShowModel `refresh`): the show's
 * revision (an edit, an undo, a load - never a GO or a flag), the list shown, the folds,
 * or the tree's shape (a cue made or gone).
 */
class CueTreeCache {
    private var key: List<Any?>? = null
    private var rows: List<CueRow> = emptyList()

    fun read(mirror: Mirror, listId: String, folds: Folds): List<CueRow> {
        val now = listOf(
            mirror.value("/godot/document/revision")?.asLong(),
            listId,
            folds.version,
            mirror.structure.value,
            mirror.text("/godot/list/$listId/order"),
        )
        if (now != key) {
            rows = buildCueTree(mirror, listId, folds)
            // A seed during the build may have moved nothing; the key is what was read before it.
            key = now.toMutableList().also { it[2] = folds.version }
        }
        return rows
    }
}

/**
 * WHAT A GESTURE ON THE CUE LIST SENDS. Picking a cue sends nothing - the pick is this
 * tablet's (PRD §3.5: looking at a cue never moves the standby). The gutter parks the GO
 * list's standby; Fire now fires a cue and leaves the standby where it is; the GO list is
 * changed only by the button that says so.
 */
object CueActions {
    fun park(cue: String) = Sent("standby.set", cue)
    fun fire(cue: String) = Sent("cue.fire", cue)
    fun focus(list: String) = Sent("list.focus", list)
    fun flagAddress(cue: String) = "/godot/cue/$cue/flagged"
}

/** Everything the Cues tab draws, read in one pass over the mirror. */
data class CuesReading(
    val lists: List<ListLine> = emptyList(),
    /** `/godot/list/focus`: the GO list. */
    val focus: String = "",
    /** The list on screen: the one picked here, else the GO list, else the first. */
    val listId: String = "",
    val rows: List<CueRow> = emptyList(),
    val marks: CueMarks = CueMarks(),
    val inspection: Inspection? = null,
    val locked: Boolean = false,
)

fun readCues(mirror: Mirror, viewed: String?, picked: String?, folds: Folds, cache: CueTreeCache, tables: SurfaceTables): CuesReading {
    val lists = readLists(mirror)
    val focus = mirror.text("/godot/list/focus") ?: ""
    val listId = viewed?.takeIf { id -> lists.any { it.id == id } }
        ?: focus.takeIf { id -> lists.any { it.id == id } }
        ?: lists.firstOrNull()?.id ?: ""
    val rows = cache.read(mirror, listId, folds)
    return CuesReading(
        lists = lists,
        focus = focus,
        listId = listId,
        rows = rows,
        marks = readMarks(mirror, listId, rows),
        inspection = picked?.let { readInspection(mirror, it, tables) },
        locked = mirror.value("/godot/document/locked")?.asBoolean() == true,
    )
}
