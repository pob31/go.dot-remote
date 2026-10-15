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

/**
 * OSC 1.0's bytes, both ways.
 *
 * WRITING is WFS-DIY's remote's (getPaddedBytes, toBytesBigEndian): a string is its UTF-8
 * bytes, a NUL, and NULs to the next multiple of four; numbers are big-endian.
 *
 * READING IS STRICT AND NEVER THROWS. Whatever is malformed - a packet cut short, a
 * string with no NUL, padding that is not NULs, a tag this codec does not know, a bundle
 * element whose size runs past the bundle, bytes left over after a message's last
 * argument - makes [decode] answer null, the shape of WFS-DIY's ArrayMuteProtocol: one
 * bad frame costs that frame, never the link. A message must carry its type tag string,
 * even an empty one (`,`): without that rule a message cut after its address would read
 * as a whole message with no arguments.
 */
object OscCodec {

    private const val BUNDLE_TAG = "#bundle"

    // ------------------------------------------------------------------ writing

    fun paddedBytes(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val padded = ByteArray((bytes.size + 1 + 3) and -4)
        bytes.copyInto(padded)
        return padded
    }

    private fun ByteArrayOutputStream.int32(value: Int) {
        write(value ushr 24)
        write(value ushr 16)
        write(value ushr 8)
        write(value)
    }

    private fun ByteArrayOutputStream.int64(value: Long) {
        int32((value ushr 32).toInt())
        int32(value.toInt())
    }

    private fun ByteArrayOutputStream.string(text: String) = write(paddedBytes(text))

    internal fun writeMessage(message: OscMessage, out: ByteArrayOutputStream) {
        out.string(message.address)
        out.string("," + message.typeTags)
        for (value in message.values) {
            when (value) {
                is OscValue.Int32 -> out.int32(value.value)
                is OscValue.Int64 -> out.int64(value.value)
                is OscValue.Float32 -> out.int32(value.value.toRawBits())
                is OscValue.Float64 -> out.int64(value.value.toRawBits())
                is OscValue.Str -> out.string(value.value)
                is OscValue.Blob -> {
                    out.int32(value.bytes.size)
                    out.write(value.bytes)
                    repeat((4 - value.bytes.size % 4) % 4) { out.write(0) }
                }
                is OscValue.TimeTag -> out.int64(value.value)
                OscValue.True, OscValue.False, OscValue.Nil, OscValue.Impulse -> Unit
            }
        }
    }

    internal fun writeBundle(bundle: OscBundle, out: ByteArrayOutputStream) {
        out.string(BUNDLE_TAG)
        out.int64(bundle.timeTag)
        for (element in bundle.elements) {
            val bytes = element.encode()
            out.int32(bytes.size)
            out.write(bytes)
        }
    }

    // ------------------------------------------------------------------ reading

    /** Malformed input, inside the reader only: [decode] turns it into null. */
    private object Malformed : Exception() {
        private fun readResolve(): Any = Malformed
        override fun fillInStackTrace(): Throwable = this
    }

    private class Reader(val bytes: ByteArray, var pos: Int, val end: Int) {
        fun need(count: Int) {
            if (count < 0 || pos + count > end) throw Malformed
        }

        fun int32(): Int {
            need(4)
            val b = bytes
            val v = ((b[pos].toInt() and 0xFF) shl 24) or
                ((b[pos + 1].toInt() and 0xFF) shl 16) or
                ((b[pos + 2].toInt() and 0xFF) shl 8) or
                (b[pos + 3].toInt() and 0xFF)
            pos += 4
            return v
        }

        fun int64(): Long {
            val high = int32().toLong()
            val low = int32().toLong() and 0xFFFFFFFFL
            return (high shl 32) or low
        }

        /** A NUL-terminated string and its NUL padding; the WFS-DIY parseOscString, made strict. */
        fun string(): String {
            var nul = pos
            while (nul < end && bytes[nul] != 0.toByte()) nul++
            if (nul >= end) throw Malformed
            val text = String(bytes, pos, nul - pos, Charsets.UTF_8)
            val next = (nul + 1 + 3) and -4
            if (next > end) throw Malformed
            for (i in nul + 1 until next) if (bytes[i] != 0.toByte()) throw Malformed
            pos = next
            return text
        }

        fun blob(): ByteArray {
            val size = int32()
            if (size < 0) throw Malformed
            need(size)
            val data = bytes.copyOfRange(pos, pos + size)
            val next = pos + ((size + 3) and -4)
            if (next > end) throw Malformed
            for (i in pos + size until next) if (bytes[i] != 0.toByte()) throw Malformed
            pos = next
            return data
        }
    }

    fun decode(bytes: ByteArray): OscPacket? = decode(bytes, 0, bytes.size)

    fun decode(bytes: ByteArray, offset: Int, length: Int): OscPacket? {
        if (offset < 0 || length < 0 || offset + length > bytes.size) return null
        return try {
            read(Reader(bytes, offset, offset + length))
        } catch (malformed: Malformed) {
            null
        } catch (other: RuntimeException) {
            // Belt and braces: whatever the checks above missed is still one bad frame.
            null
        }
    }

    private fun read(input: Reader): OscPacket {
        val size = input.end - input.pos
        if (size < 4 || size % 4 != 0) throw Malformed
        return when (input.bytes[input.pos]) {
            '/'.code.toByte() -> readMessage(input)
            '#'.code.toByte() -> readBundle(input)
            else -> throw Malformed
        }
    }

    private fun readBundle(input: Reader): OscBundle {
        if (input.string() != BUNDLE_TAG) throw Malformed
        val timeTag = input.int64()
        val elements = ArrayList<OscPacket>()
        while (input.pos < input.end) {
            val size = input.int32()
            if (size <= 0 || size % 4 != 0) throw Malformed
            input.need(size)
            elements.add(read(Reader(input.bytes, input.pos, input.pos + size)))
            input.pos += size
        }
        return OscBundle(timeTag, elements)
    }

    private fun readMessage(input: Reader): OscMessage {
        val address = input.string()
        if (input.pos >= input.end) throw Malformed // no type tag string: see the class comment
        val tags = input.string()
        if (tags.isEmpty() || tags[0] != ',') throw Malformed
        val values = ArrayList<OscValue>(tags.length - 1)
        for (i in 1 until tags.length) {
            values.add(
                when (tags[i]) {
                    'i' -> OscValue.Int32(input.int32())
                    'h' -> OscValue.Int64(input.int64())
                    'f' -> OscValue.Float32(Float.fromBits(input.int32()))
                    'd' -> OscValue.Float64(Double.fromBits(input.int64()))
                    's' -> OscValue.Str(input.string())
                    'b' -> OscValue.Blob(input.blob())
                    't' -> OscValue.TimeTag(input.int64())
                    'T' -> OscValue.True
                    'F' -> OscValue.False
                    'N' -> OscValue.Nil
                    'I' -> OscValue.Impulse
                    else -> throw Malformed
                }
            )
        }
        if (input.pos != input.end) throw Malformed
        return OscMessage(address, values)
    }
}
