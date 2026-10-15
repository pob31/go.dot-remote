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

package com.pob31.godot.remote.media

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Where a timbre is asked for: the machine's HTTP port, the one its WebSocket is on. */
data class TimbreSource(val host: String, val port: Int)

/** One GET's body, or null for anything but a 200 - a hash not analysed yet is a 404 now and a 200 later. */
fun interface TimbreFetcher {
    fun get(source: TimbreSource, pathAndQuery: String): ByteArray?
}

/** The fetcher the app uses: OkHttp, short waits, nothing kept by OkHttp itself (the route says no-cache). */
class HttpTimbreFetcher(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) : TimbreFetcher {
    override fun get(source: TimbreSource, pathAndQuery: String): ByteArray? = try {
        http.newCall(Request.Builder().url("http://${source.host}:${source.port}$pathAndQuery").build()).execute().use {
            if (it.isSuccessful) it.body.bytes() else null
        }
    } catch (e: IOException) {
        null
    }
}

/**
 * THE WAVEFORMS, FETCHED ONCE AND KEPT (A.9): `GET /media/<hash>/timbre?INFO`, then the
 * level that fits the strip (`?level=N`), each kept on the tablet under the show's folder
 * ([ShowFolders]) across launches, with a small memory LRU on top, so a strip seen once is
 * drawn at once from then on - the Wi-Fi in a theatre is not to be counted on.
 *
 * KEYED BY CONTENT. The hash is the audio's sha256, so the same sound in two shows is kept
 * twice, once in each show's folder: a few kilobytes, which is cheaper than a shared store
 * that "forget this show" could not empty.
 *
 * THE ANALYSIS MOVES (TimbreRoute.h: the same URL can answer other colours one build
 * later). So the header is asked again once a session per sound; a header of another
 * `formatVersion`, or other levels, throws the sound's kept levels away. Without a link, the
 * header kept on the tablet stands.
 *
 * NEVER ON THE UI THREAD: [strip] runs on [Dispatchers.IO]. A malformed answer or a kept
 * file of the wrong length is nothing drawn, never a crash.
 */
class TimbreCache(
    root: File,
    private val fetcher: TimbreFetcher = HttpTimbreFetcher(),
    private val memoryBytes: Int = 4 * 1024 * 1024,
) {
    val folders = ShowFolders(root)

    private val memory = object : LinkedHashMap<String, TimbreFrames>(64, 0.75f, true) {}
    private var memoryUsed = 0

    /** Headers asked of the machine this session, by "<folder>/<hash>". */
    private val checked = ConcurrentHashMap<String, TimbreInfo>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** The strip for [hash] at [width] pixels, from memory, the tablet or the machine; null when there is none yet. */
    suspend fun strip(source: TimbreSource?, show: String, hash: String, width: Int): TimbreStrip? {
        if (!isContentHash(hash) || width <= 0) return null
        val folder = ShowFolders.folderName(show)
        val key = "$folder/$hash"
        return withContext(Dispatchers.IO) {
            locks.getOrPut(key) { Mutex() }.withLock { load(source, show, folder, hash, width) }
        }
    }

    private fun load(source: TimbreSource?, show: String, folder: String, hash: String, width: Int): TimbreStrip? {
        val key = "$folder/$hash"
        val dir = folders.timbreOf(show)
        val info = checked[key] ?: run {
            val (asked, header) = header(source, dir, hash) ?: return null
            if (asked) checked[key] = header
            header
        }
        val level = levelFor(info.levels, width)
        val frames = info.levels[level].frames
        val memoryKey = "$key.L$level"
        remembered(memoryKey)?.let { return TimbreStrip(info, level, it) }

        val file = File(dir, "$hash.L$level")
        if (file.isFile) {
            val kept = try { file.readBytes() } catch (e: IOException) { null }
            TimbreFrames.of(kept ?: ByteArray(0), frames)?.let {
                remember(memoryKey, it)
                return TimbreStrip(info, level, it)
            }
            file.delete() // the wrong length: not this level of this analysis
        }

        val fetched = source?.let { fetcher.get(it, "/media/$hash/timbre?level=$level") } ?: return null
        val got = TimbreFrames.of(fetched, frames) ?: return null
        write(file, fetched)
        remember(memoryKey, got)
        return TimbreStrip(info, level, got)
    }

    /**
     * The header: the machine's when it answers (then true - asked this session), kept on
     * the tablet, and the sound's kept levels thrown away when it differs from the one kept;
     * the tablet's when the machine does not answer (false - ask again next time).
     */
    private fun header(source: TimbreSource?, dir: File, hash: String): Pair<Boolean, TimbreInfo>? {
        val file = File(dir, "$hash.info")
        val kept = if (file.isFile) try { TimbreInfo.parse(file.readText()) } catch (e: IOException) { null } else null
        val answer = source?.let { fetcher.get(it, "/media/$hash/timbre?INFO") }
        val fresh = answer?.let { TimbreInfo.parse(String(it, Charsets.UTF_8)) }?.takeIf { it.sha256 == hash }
        if (fresh == null) return kept?.takeIf { it.sha256 == hash }?.let { false to it }
        if (kept != fresh) {
            dir.listFiles { f -> f.name.startsWith("$hash.L") }?.forEach { it.delete() }
            synchronized(memory) {
                val stale = memory.keys.filter { it.contains("/$hash.L") }
                for (k in stale) memory.remove(k)?.let { memoryUsed -= it.bytes.size }
            }
            write(file, answer)
        }
        return true to fresh
    }

    /** Written beside, then moved into place: a file read later is whole or absent. */
    private fun write(file: File, bytes: ByteArray) {
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".part")
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: IOException) {
            // A full tablet: the strip is drawn this time, and fetched again next time.
        }
    }

    private fun remembered(key: String): TimbreFrames? = synchronized(memory) { memory[key] }

    private fun remember(key: String, frames: TimbreFrames) {
        synchronized(memory) {
            memory.put(key, frames)?.let { memoryUsed -= it.bytes.size }
            memoryUsed += frames.bytes.size
            val each = memory.entries.iterator()
            while (memoryUsed > memoryBytes && each.hasNext()) {
                val oldest = each.next()
                if (oldest.key == key) continue
                memoryUsed -= oldest.value.bytes.size
                each.remove()
            }
        }
    }

    /** The show folders, as the Machines tab lists them. */
    fun list(): List<ShowFolder> = folders.list()

    /** Forgets a show: its folder deleted, and nothing of it left in memory. */
    fun forget(name: String): Boolean {
        synchronized(memory) {
            val gone = memory.keys.filter { it.startsWith("$name/") }
            for (k in gone) memory.remove(k)?.let { memoryUsed -= it.bytes.size }
        }
        checked.keys.removeIf { it.startsWith("$name/") }
        return folders.forget(name)
    }

    /** Bytes held in memory now, for the tests. */
    internal val memoryHeld: Int get() = synchronized(memory) { memoryUsed }

    companion object {
        @Volatile private var shared: TimbreCache? = null

        /** The app's one cache, under its files: `filesDir/shows`. */
        fun shared(context: Context): TimbreCache =
            shared ?: synchronized(this) {
                shared ?: TimbreCache(File(context.applicationContext.filesDir, "shows")).also { shared = it }
            }
    }
}
