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

package com.pob31.godot.remote.mapping

import com.pob31.godot.remote.link.LinkState
import com.pob31.godot.remote.model.Mirror

/** A canvas (namespace draft §35.3): what the show calls it and its size in pixels. */
data class CanvasRow(val id: String, val name: String, val width: Int, val height: Int) {
    val label: String get() = name.ifEmpty { id }

    /** Its shape, width over height; 16:9 when it has no sensible size. */
    val aspect: Double get() = if (width > 0 && height > 0) width.toDouble() / height else 16.0 / 9.0
}

/**
 * ONE WARP AN OUTPUT DRAWS: its own canvas's (the first, [zoneId] null) or a zone's (§40).
 * [base] is where its rows live - "/godot/videoOutput/<id>/" or "/godot/zone/<id>/".
 * Only a zone has a blend and an opacity of its own (XE): the output's own canvas lies
 * normally, whole.
 */
data class WarpLayer(
    val base: String,
    val zoneId: String?,
    val name: String,
    val canvas: String,
    val warp: Warp,
    val blend: String = "normal",
    /** The closed set the engine takes for [blend] (its RANGE VALS), in its order. */
    val blends: List<String> = emptyList(),
    val opacity: Double = 100.0,
    val opacityMin: Double = 0.0,
    val opacityMax: Double = 100.0,
) {
    val isZone: Boolean get() = zoneId != null
    val meshAddress: String get() = base + "mesh"
    val opacityAddress: String get() = base + "opacity"
    val blendAddress: String get() = base + "blend"
}

/**
 * A VIDEO OUTPUT AS THE TABLET SHOWS IT (§35.3, §40, §44): the show's - its name, the
 * canvas it shows, the display it opens on, whether it is on - and tonight's: whether a
 * display is behind it, why not, whether the test pattern is up; and its warps, its own
 * first, then each zone bottom first.
 */
data class OutputRow(
    val id: String,
    val name: String,
    val canvas: String,
    val display: String,
    val kind: String,
    val enabled: Boolean,
    val bound: Boolean,
    val problem: String,
    val testPattern: Boolean,
    val layers: List<WarpLayer>,
    /**
     * `<output>/grid`, tonight's: whether the alignment grid is drawn on it, in the canvas's
     * space and bent through the warp. Null when the row is absent - a Go.dot older than the
     * grid, whose switch is then not offered.
     */
    val grid: Boolean? = null,
) {
    val label: String get() = name.ifEmpty { id }
    val sends: Boolean get() = kind.isNotEmpty() && kind != "display"
}

/** Everything the Mapping tab reads, in one reading; equal readings recompose nothing. */
data class MappingReading(
    val outputs: List<OutputRow> = emptyList(),
    val canvases: Map<String, CanvasRow> = emptyMap(),
    /** `/godot/document/locked`: the warps are show rows, refused while it is true. */
    val locked: Boolean = false,
) {
    fun output(id: String?): OutputRow? = outputs.firstOrNull { it.id == id } ?: outputs.firstOrNull()

    fun canvasLabel(id: String): String? = canvases[id]?.label

    /** The shape the output's picture is drawn in (see [aspectOf]). */
    fun aspectOf(output: OutputRow): Double = canvases[output.canvas]?.aspect ?: (16.0 / 9.0)
}

/**
 * Reads the outputs, their zones and the canvases from a mirror, by address - in the
 * show's order (`/godot/videoOutput/order`, `/godot/canvas/order`, `<output>/zones`
 * bottom first), and anything the order does not name last rather than gone.
 */
fun readMapping(mirror: Mirror): MappingReading {
    val canvases = LinkedHashMap<String, CanvasRow>()
    for (id in inOrder(mirror.text("/godot/canvas/order"), mirror.ids("canvas"))) {
        val base = "/godot/canvas/$id/"
        canvases[id] = CanvasRow(
            id = id,
            name = mirror.text(base + "name") ?: "",
            width = mirror.value(base + "width")?.asLong()?.toInt() ?: 1920,
            height = mirror.value(base + "height")?.asLong()?.toInt() ?: 1080,
        )
    }

    val outputs = ArrayList<OutputRow>()
    for (id in inOrder(mirror.text("/godot/videoOutput/order"), mirror.ids("videoOutput"))) {
        val base = "/godot/videoOutput/$id/"
        val canvas = mirror.text(base + "canvas") ?: ""
        val layers = ArrayList<WarpLayer>()
        layers += WarpLayer(
            base = base, zoneId = null, name = "", canvas = canvas,
            warp = warpAt(mirror, base),
        )
        for (zone in words(mirror.text(base + "zones"))) {
            val zoneBase = "/godot/zone/$zone/"
            val opacity = mirror[zoneBase + "opacity"]
            val blend = mirror[zoneBase + "blend"]
            layers += WarpLayer(
                base = zoneBase, zoneId = zone,
                name = mirror.text(zoneBase + "name") ?: "",
                canvas = mirror.text(zoneBase + "canvas") ?: "",
                warp = warpAt(mirror, zoneBase),
                blend = blend?.text?.takeIf { it.isNotEmpty() } ?: "normal",
                blends = blend?.range?.firstOrNull()?.vals ?: emptyList(),
                opacity = opacity?.value?.asDouble() ?: 100.0,
                opacityMin = opacity?.range?.firstOrNull()?.min ?: 0.0,
                opacityMax = opacity?.range?.firstOrNull()?.max ?: 100.0,
            )
        }
        outputs += OutputRow(
            id = id,
            name = mirror.text(base + "name") ?: "",
            canvas = canvas,
            display = mirror.text(base + "display") ?: "",
            kind = mirror.text(base + "kind")?.takeIf { it.isNotEmpty() } ?: "display",
            enabled = mirror.value(base + "enabled")?.asBoolean() ?: true,
            bound = mirror.value(base + "bound")?.asBoolean() ?: false,
            problem = mirror.text(base + "problem") ?: "",
            testPattern = mirror.value(base + "testPattern")?.asBoolean() ?: false,
            layers = layers,
            grid = mirror[base + "grid"]?.let { it.value?.asBoolean() ?: false },
        )
    }
    return MappingReading(outputs, canvases, mirror.value("/godot/document/locked")?.asBoolean() ?: false)
}

private fun warpAt(mirror: Mirror, base: String): Warp = Warp.read(
    mirror.value(base + "meshColumns")?.asDouble(),
    mirror.value(base + "meshRows")?.asDouble(),
    mirror[base + "mesh"]?.values,
)

private fun words(text: String?): List<String> = text?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() } ?: emptyList()

private fun inOrder(order: String?, known: List<String>): List<String> {
    val named = words(order).filter { it in known }
    return named + known.filter { it !in named }
}

/**
 * WHETHER THE WARPS MAY BE CHANGED FROM HERE, and the word that says why not. The mesh,
 * the splits, a zone's opacity and blend are show rows: refused under the lock (remote
 * protocol §6), so they are greyed with the word "Locked" rather than offered and refused.
 * A link that is not live greys everything with its own word (§7). Identify and the grid
 * are no edit of the show and are taken under the lock (§35.9): only the link stops them.
 */
enum class MappingBlock { NONE, LOCKED, LINK }

fun mappingBlock(locked: Boolean, link: LinkState): MappingBlock = when {
    link != LinkState.LIVE -> MappingBlock.LINK
    locked -> MappingBlock.LOCKED
    else -> MappingBlock.NONE
}
