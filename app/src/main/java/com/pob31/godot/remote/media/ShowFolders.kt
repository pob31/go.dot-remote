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

import java.io.File

/** A show's folder on the tablet: its name, what it weighs, how many sounds it keeps. */
data class ShowFolder(val name: String, val bytes: Long, val sounds: Int)

/**
 * THE SHOWS' FOLDERS ON THE TABLET (the author: "If there are waveform files to download
 * for display we can make folders for the various shows on the Android app"):
 * `<root>/<show name>/timbre/<hash>.L<n>` and `<hash>.info`, the root being the app's
 * `filesDir/shows`. The show's name is `/godot/document/name`, made safe for a file name.
 * Listed on the Machines tab, each with a Forget.
 */
class ShowFolders(val root: File) {

    /** The folder a show's files go in (not made here). */
    fun folderFor(show: String): File = File(root, folderName(show))

    fun timbreOf(show: String): File = File(folderFor(show), TIMBRE)

    /** Every show folder, by name, with its size and the number of sounds it keeps. */
    fun list(): List<ShowFolder> {
        val shows = root.listFiles { file -> file.isDirectory } ?: return emptyList()
        return shows.sortedBy { it.name.lowercase() }.map { folder ->
            val files = folder.walkTopDown().filter { it.isFile }.toList()
            val sounds = File(folder, TIMBRE).listFiles()
                ?.mapNotNull { file -> file.name.substringBefore('.').takeIf { isContentHash(it) } }
                ?.toSet()?.size ?: 0
            ShowFolder(folder.name, files.sumOf { it.length() }, sounds)
        }
    }

    /**
     * Deletes a show's folder and everything in it. Only a folder directly under the root,
     * named as [list] names it: a name that would climb out of it is refused.
     */
    fun forget(name: String): Boolean {
        if (name.isEmpty() || name != folderName(name)) return false
        val folder = File(root, name)
        if (folder.parentFile?.canonicalFile != root.canonicalFile || !folder.isDirectory) return false
        return folder.deleteRecursively()
    }

    companion object {
        const val TIMBRE = "timbre"

        /**
         * A show's name as a folder's: a character no file system takes - a slash, a colon,
         * a control character... - becomes `_`, the ends trimmed of spaces and dots, at most
         * 80 characters, and "untitled" for nothing at all. Two shows with names that differ
         * only in those share a folder; the files in it are keyed by content, so that costs
         * nothing but a wrong count.
         */
        fun folderName(show: String): String {
            val cleaned = buildString {
                for (c in show) append(if (c < ' ' || c == '\u007f' || c in FORBIDDEN) '_' else c)
            }.trim().trim('.', ' ').take(80).trim()
            return if (cleaned.isEmpty() || cleaned.all { it == '.' }) "untitled" else cleaned
        }

        private const val FORBIDDEN = "\\/:*?\"<>|"
    }
}
