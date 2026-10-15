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

package com.pob31.godot.remote.osc

import java.io.ByteArrayOutputStream

/** A message or a bundle, as one WebSocket binary frame carries it. */
sealed class OscPacket {
    abstract fun encode(): ByteArray

    internal abstract fun writeTo(out: ByteArrayOutputStream)

    companion object {
        /** The packet in [bytes], or null when they are not one: never an exception. */
        fun decode(bytes: ByteArray): OscPacket? = OscCodec.decode(bytes)
    }
}

data class OscMessage(val address: String, val values: List<OscValue> = emptyList()) : OscPacket() {

    constructor(address: String, vararg values: OscValue) : this(address, values.toList())

    /** The type tag string without its comma: `ss` for two strings. */
    val typeTags: String get() = values.joinToString("") { it.tag.toString() }

    override fun encode(): ByteArray = ByteArrayOutputStream(64).also { writeTo(it) }.toByteArray()

    override fun writeTo(out: ByteArrayOutputStream) = OscCodec.writeMessage(this, out)
}

/**
 * A bundle: a time tag and its elements, which may be bundles. Go.dot's stream sends
 * one a tick with the time tag "immediately" ([IMMEDIATELY]), the tick its first element.
 */
data class OscBundle(val timeTag: Long = IMMEDIATELY, val elements: List<OscPacket> = emptyList()) : OscPacket() {

    override fun encode(): ByteArray = ByteArrayOutputStream(256).also { writeTo(it) }.toByteArray()

    override fun writeTo(out: ByteArrayOutputStream) = OscCodec.writeBundle(this, out)

    /** Every message inside, nested bundles walked in order. */
    fun messages(): List<OscMessage> {
        val out = ArrayList<OscMessage>(elements.size)
        fun walk(packet: OscPacket) {
            when (packet) {
                is OscMessage -> out.add(packet)
                is OscBundle -> packet.elements.forEach(::walk)
            }
        }
        elements.forEach(::walk)
        return out
    }

    companion object {
        /** OSC 1.0's "immediately": the time tag whose only bit set is the lowest. */
        const val IMMEDIATELY = 1L
    }
}
