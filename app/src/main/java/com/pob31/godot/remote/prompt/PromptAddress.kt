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

package com.pob31.godot.remote.prompt

import java.net.URI
import java.net.URISyntaxException

/**
 * THE ADDRESS OF A CHOUFLEUR PAGE (the Script tab; the author, 2026-10-16: "add a tab to show
 * the Choufleur script prompt client"). Choufleur's library server listens on 8080 and hands
 * an operator `http://<host>:8080/list/<list-id>` - drawn as a QR code on its screen - which
 * outlives the show server's own port. What is typed or scanned is made into that shape:
 *
 * - a whole `http://` or `https://` address is kept as it is (a missing path becomes `/`);
 * - a host alone, or a host and a port, or a host and a path, is completed with `http://`
 *   and - when no port is said - Choufleur's 8080;
 * - anything else (another scheme, a space, no host, a port out of range) is not an address.
 *
 * Pure: no Android, so the rules are tested on the JVM.
 */
object PromptAddress {
    /** Choufleur's library server's port (its `PREFERRED_PORT`). */
    const val DEFAULT_PORT = 8080

    private val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://")

    /** The address to keep, or null when [typed] is not one. */
    fun normalise(typed: String): String? {
        val text = typed.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val said = scheme.find(text)
        val whole = if (said != null) {
            val name = said.groupValues[1].lowercase()
            if (name != "http" && name != "https") return null
            name + text.substring(said.groupValues[1].length)
        } else {
            // A bare host: Choufleur's port unless one is said. The authority ends at the
            // first '/', '?' or '#'; a port is what follows its last ':' outside brackets.
            val end = text.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) text.length else it }
            val authority = text.substring(0, end)
            val rest = text.substring(end)
            val hasPort = authority.substringAfterLast(']').contains(':')
            "http://" + authority + (if (hasPort) "" else ":$DEFAULT_PORT") + rest
        }
        val uri = try {
            URI(whole)
        } catch (e: URISyntaxException) {
            return null
        }
        val host = uri.host ?: return null
        if (host.isEmpty() || uri.rawUserInfo != null) return null
        if (uri.port != -1 && uri.port !in 1..65535) return null
        // A port written but not read (http://host:/ or http://host:99999/) is not a port.
        if (uri.port == -1 && uri.rawAuthority?.substringAfterLast(']')?.contains(':') == true) return null
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        val fragment = uri.rawFragment?.let { "#$it" } ?: ""
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "${uri.scheme.lowercase()}://${host.lowercase()}$port$path$query$fragment"
    }

    /**
     * A Bonjour name: Android finds `.local` names since late 2021, but not behind a Private
     * DNS setting or where multicast is dropped (Choufleur's own finding), so the number from
     * Choufleur's screen is the address that cannot fail - said beside the field, not refused.
     */
    fun isLocalName(address: String): Boolean =
        try {
            URI(address).host?.lowercase()?.trimEnd('.')?.endsWith(".local") == true
        } catch (e: URISyntaxException) {
            false
        }

    /**
     * The scheme and host, any port: the Choufleur machine the page may stay on and the
     * bridge is offered to. Not the origin: the durable `/list/<id>` on the library's 8080
     * answers with a redirect to the show server on another port of the same machine
     * (8081 and up), where the page itself lives - found on the author's network.
     */
    fun machineOf(address: String): String? =
        try {
            val uri = URI(address)
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host?.lowercase() ?: return null
            "$scheme://$host"
        } catch (e: URISyntaxException) {
            null
        }
}
