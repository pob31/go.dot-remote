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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * One whole reading of a machine's tree: the nodes, the owner index and the metadata.
 * A snapshot builds a fresh one off to the side and the mirror swaps it in at once, so a
 * reconnect never shows half of the old tree and half of the new.
 */
class MirrorState(expectedNodes: Int = 4096) {
    internal val nodes = ConcurrentHashMap<String, Node>(expectedNodes)

    /** `/godot/<owner>/<id>` -> the rows it has ("" for the object's own node). */
    internal val objects = ConcurrentHashMap<String, MutableSet<String>>()

    /** owner -> its ids: what a list of cues or runs reads, never a scan of [nodes]. */
    internal val owners = ConcurrentHashMap<String, MutableSet<String>>()

    /** [Addresses.metaKey] -> what the rows under it are. */
    internal val meta = ConcurrentHashMap<String, NodeMeta>()

    /** Rows made from the stream before HTTP said what they are, by the key that will. Writer only. */
    internal val awaitingMeta = HashMap<String, MutableSet<String>>()

    /** Puts [node] in; true when its address is new. */
    internal fun put(node: Node): Boolean {
        val isNew = nodes.put(node.address, node) == null
        if (isNew) index(node.address)
        return isNew
    }

    internal fun remove(address: String): Boolean {
        nodes.remove(address) ?: return false
        unindex(address)
        return true
    }

    private fun index(address: String) {
        val owned = Addresses.owned(address) ?: return
        objects.getOrPut(owned.objectPath) { ConcurrentHashMap.newKeySet() }.add(owned.row)
        owners.getOrPut(owned.owner) { ConcurrentHashMap.newKeySet() }.add(owned.id)
    }

    private fun unindex(address: String) {
        val owned = Addresses.owned(address) ?: return
        val rows = objects[owned.objectPath] ?: return
        rows.remove(owned.row)
        if (rows.isEmpty()) {
            objects.remove(owned.objectPath)
            owners[owned.owner]?.let { ids ->
                ids.remove(owned.id)
                if (ids.isEmpty()) owners.remove(owned.owner)
            }
        }
    }
}

/**
 * The engine's tree as this tablet knows it: address -> [Node].
 *
 * ONE WRITER, MANY READERS. Only the link's own processing writes (the snapshot, the
 * stream, the structure frames, in the order they arrived); the UI reads by address, as
 * the page does, at no more than 25 Hz, driven by [tick].
 */
class Mirror {
    @Volatile private var current = MirrorState(16)

    private val _tick = MutableStateFlow(-1L)

    /** The engine tick the mirror is at: the snapshot's, then each bundle's. -1 before any. */
    val tick: StateFlow<Long> = _tick.asStateFlow()

    private val _structure = MutableStateFlow(0L)

    /** Bumped whenever a node appears or goes, so a list built from [ids] knows to rebuild. */
    val structure: StateFlow<Long> = _structure.asStateFlow()

    val size: Int get() = current.nodes.size

    operator fun get(address: String): Node? = current.nodes[address]

    fun value(address: String): OscValue? = current.nodes[address]?.value

    fun text(address: String): String? = current.nodes[address]?.text

    /** The ids of [owner]'s objects, sorted: `ids("cue")`, `ids("run")`. */
    fun ids(owner: String): List<String> = current.owners[owner]?.sorted() ?: emptyList()

    /** The rows `/godot/<owner>/<id>` has, "" being its own node. */
    fun rows(owner: String, id: String): Set<String> =
        current.objects["${Addresses.ROOT}$owner/$id"]?.toSet() ?: emptySet()

    fun addresses(): Set<String> = current.nodes.keys.toSet()

    fun metaOf(address: String): NodeMeta? = current.meta[Addresses.metaKey(address)]

    // ---------------------------------------------------------------- the link's writes

    internal fun install(state: MirrorState, tick: Long) {
        current = state
        _tick.value = tick
        _structure.value = _structure.value + 1
    }

    internal fun setTick(tick: Long) {
        _tick.value = tick
    }

    /**
     * Values from the stream. A node the mirror lacks is made from the metadata already
     * known for its row, or from nothing until HTTP says what it is.
     */
    internal fun putValues(address: String, typeTags: String, values: List<OscValue>) {
        val state = current
        val existing = state.nodes[address]
        if (existing != null) {
            state.nodes[address] = existing.copy(typeTags = typeTags, values = values)
        } else {
            make(state, address, typeTags, values)
        }
    }

    /** PATH_ADDED: the node exists from now, holding nothing until the stream gives it a value. */
    internal fun added(address: String) {
        val state = current
        if (!state.nodes.containsKey(address)) make(state, address, "", emptyList())
    }

    private fun make(state: MirrorState, address: String, typeTags: String, values: List<OscValue>) {
        val key = Addresses.metaKey(address)
        val meta = state.meta[key]
        if (meta == null) state.awaitingMeta.getOrPut(key) { HashSet() }.add(address)
        if (state.put(Node.of(address, typeTags, values, meta ?: NodeMeta.UNKNOWN))) bumpStructure()
    }

    /** PATH_REMOVED. */
    internal fun removed(address: String) {
        if (current.remove(address)) bumpStructure()
    }

    internal fun hasMeta(address: String): Boolean = current.meta.containsKey(Addresses.metaKey(address))

    internal fun hasMetaKey(key: String): Boolean = current.meta.containsKey(key)

    /**
     * What HTTP said a row is, without its value: kept for every row of its shape and
     * given to each node of that shape that was waiting for it. A node the mirror does not
     * have is not made - it went while the answer was on its way.
     */
    internal fun learnMeta(address: String, meta: NodeMeta) {
        val state = current
        val key = Addresses.metaKey(address)
        val kept = state.meta.putIfAbsent(key, meta) ?: meta
        val waiting = state.awaitingMeta.remove(key) ?: return
        for (each in waiting) {
            val node = state.nodes[each] ?: continue
            state.nodes[each] = node.withMeta(kept)
        }
    }

    private fun bumpStructure() {
        _structure.value = _structure.value + 1
    }
}
