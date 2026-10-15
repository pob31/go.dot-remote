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

package com.pob31.godot.remote.link

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * This tablet's address on the local network - WFS-DIY's remote's getCurrentIpAddress,
 * preferring IPv4 - or null when it has none. Shown so the operator can tell the desk
 * which tablet is which.
 */
fun currentIpAddress(): String? {
    try {
        var fallback: String? = null
        for (networkInterface in NetworkInterface.getNetworkInterfaces() ?: return null) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            for (address in networkInterface.inetAddresses) {
                if (address.isLoopbackAddress || !address.isSiteLocalAddress) continue
                if (address is Inet4Address) return address.hostAddress
                if (fallback == null) fallback = address.hostAddress
            }
        }
        return fallback
    } catch (e: Exception) {
        return null
    }
}
