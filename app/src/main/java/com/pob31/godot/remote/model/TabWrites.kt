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

import com.pob31.godot.remote.link.Link

/**
 * WHAT A TAB SENDS, AND NOTHING ELSE: the link's writes (remote protocol §6) behind an
 * interface, so a screen is drawn and pressed in a test with no engine and every write
 * read back. A command's arguments are strings; a value is one string; a drag is touch,
 * values, release - one undo step for the document.
 */
interface TabWrites {
    /** A command; false when it could not leave (no link on screen, or not linked yet). */
    fun send(sent: Sent): Boolean

    /** A value while a finger moves: throttled to one each 20 ms, the latest kept. */
    fun set(address: String, text: String)

    /** A value now: a typed number, a switch, a choice. */
    fun setNow(address: String, text: String): Boolean

    fun touch(address: String): Boolean

    /** The drag's end: its last value lands now, then the release. */
    fun release(address: String): Boolean

    companion object {
        /** No machine on screen: everything is refused, and the screen says so. */
        val NONE = object : TabWrites {
            override fun send(sent: Sent) = false
            override fun set(address: String, text: String) = Unit
            override fun setNow(address: String, text: String) = false
            override fun touch(address: String) = false
            override fun release(address: String) = false
        }
    }
}

/** The writes of the link to the machine on screen. */
class LinkWrites(private val link: Link) : TabWrites {
    override fun send(sent: Sent) = link.command(sent.command, *sent.args.toTypedArray())
    override fun set(address: String, text: String) = link.set(address, text)
    override fun setNow(address: String, text: String) = link.setNow(address, text)
    override fun touch(address: String) = link.touch(address)
    override fun release(address: String) = link.release(address)
}
