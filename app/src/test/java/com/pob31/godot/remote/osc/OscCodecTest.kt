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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OscCodecTest {

    private fun bytes(vararg parts: Any): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (part in parts) {
            when (part) {
                is String -> out.write(part.toByteArray(Charsets.UTF_8))
                is Int -> out.write(part)
                is ByteArray -> out.write(part)
                else -> error("bad part $part")
            }
        }
        return out.toByteArray()
    }

    private fun roundTrip(packet: OscPacket): OscPacket {
        val encoded = packet.encode()
        assertEquals("length is a multiple of four", 0, encoded.size % 4)
        val decoded = OscPacket.decode(encoded)
        assertEquals(packet, decoded)
        return decoded!!
    }

    // ------------------------------------------------------------- byte-exact

    @Test
    fun goIsEncodedByteForByte() {
        val expected = bytes("/godot/cmd/go", 0, 0, 0, ",", 0, 0, 0)
        assertEquals(20, expected.size)
        assertArrayEquals(expected, OscMessage("/godot/cmd/go").encode())
    }

    @Test
    fun aLevelWriteIsEncodedByteForByte() {
        val expected = bytes(
            "/godot/cue/ABCD1234/level", 0, 0, 0, // 25 characters, a NUL, two of padding
            ",s", 0, 0,
            "-6.5", 0, 0, 0, 0
        )
        assertEquals(40, expected.size)
        assertArrayEquals(expected, OscMessage("/godot/cue/ABCD1234/level", OscValue.Str("-6.5")).encode())
    }

    @Test
    fun numbersAreBigEndian() {
        val encoded = OscMessage("/n", OscValue.Int32(0x01020304), OscValue.Int64(0x0102030405060708L), OscValue.Float32(1.0f)).encode()
        val expected = bytes(
            "/n", 0, 0, ",ihf", 0, 0, 0, 0,
            1, 2, 3, 4,
            1, 2, 3, 4, 5, 6, 7, 8,
            0x3F, 0x80, 0, 0
        )
        assertArrayEquals(expected, encoded)
    }

    // ------------------------------------------------------------- round trips

    @Test
    fun everyTagRoundTrips() {
        val message = OscMessage(
            "/godot/every/tag",
            OscValue.Int32(-42),
            OscValue.Int64(Long.MIN_VALUE + 7),
            OscValue.Float32(-6.5f),
            OscValue.Float64(Math.PI),
            OscValue.Str("café — ☕"),
            OscValue.Blob(byteArrayOf(1, 2, 3, 4, 5)),
            OscValue.True,
            OscValue.False,
            OscValue.Nil,
            OscValue.Impulse,
            OscValue.TimeTag(0x83AA7E80_00000001uL.toLong()),
        )
        assertEquals("ihfdsbTFNIt", message.typeTags)
        roundTrip(message)
    }

    @Test
    fun eachTagAloneRoundTrips() {
        val samples = listOf(
            OscValue.Int32(Int.MAX_VALUE), OscValue.Int64(-1L), OscValue.Float32(Float.NaN),
            OscValue.Float64(-0.0), OscValue.Str(""), OscValue.Blob(ByteArray(0)), OscValue.Blob(ByteArray(4) { 9 }),
            OscValue.True, OscValue.False, OscValue.Nil, OscValue.Impulse, OscValue.TimeTag(OscBundle.IMMEDIATELY),
        )
        for (value in samples) roundTrip(OscMessage("/one", value))
    }

    @Test
    fun aMessageWithNoArgumentsRoundTrips() {
        roundTrip(OscMessage("/godot/cmd/run/stopAll"))
    }

    @Test
    fun stringsAtThePaddingBoundaries() {
        for (length in listOf(0, 1, 2, 3, 4, 5, 7, 8, 9)) {
            val text = "x".repeat(length)
            val padded = OscCodec.paddedBytes(text)
            assertEquals("padded length of $length", (length + 4) / 4 * 4, padded.size)
            assertEquals(0, padded[padded.size - 1].toInt())
            roundTrip(OscMessage("/s", OscValue.Str(text)))
            // As an address, too: "/" plus the run.
            roundTrip(OscMessage("/" + text, OscValue.Int32(length)))
        }
    }

    @Test
    fun aBundleOfBundlesRoundTrips() {
        val inner = OscBundle(
            OscBundle.IMMEDIATELY,
            listOf(OscMessage("/godot/cue/ABCD1234/level", OscValue.Float64(-3.0)), OscBundle(5L, emptyList()))
        )
        val outer = OscBundle(
            0x0102030405060708L,
            listOf(
                OscMessage("/godot/engine/tick", OscValue.Int64(1234)),
                inner,
                OscMessage("/godot/run/R1234567/state", OscValue.Str("running")),
            )
        )
        val decoded = roundTrip(outer) as OscBundle
        assertEquals(
            listOf("/godot/engine/tick", "/godot/cue/ABCD1234/level", "/godot/run/R1234567/state"),
            decoded.messages().map { it.address }
        )
    }

    @Test
    fun anEmptyBundleRoundTrips() {
        roundTrip(OscBundle())
    }

    // ------------------------------------------------------------- malformed

    @Test
    fun aMessageCutAtAnyByteIsNull() {
        val whole = OscMessage(
            "/godot/cue/ABCD1234/level",
            OscValue.Str("-6.5"), OscValue.Int32(3), OscValue.Float64(1.5), OscValue.Blob(byteArrayOf(1, 2, 3)), OscValue.True
        ).encode()
        assertNotNull(OscPacket.decode(whole))
        for (cut in 0 until whole.size) {
            assertNull("cut at $cut", OscPacket.decode(whole.copyOf(cut)))
        }
    }

    @Test
    fun aBundleCutAtAnyByteNeverThrows() {
        val whole = OscBundle(
            OscBundle.IMMEDIATELY,
            listOf(
                OscMessage("/godot/engine/tick", OscValue.Int64(77)),
                OscBundle(OscBundle.IMMEDIATELY, listOf(OscMessage("/a", OscValue.Str("abc")))),
                OscMessage("/godot/cue/ABCD1234/name", OscValue.Str("Announce")),
            )
        ).encode()

        // The offsets where an element ends: a bundle cut there is a whole bundle with fewer elements.
        val boundaries = mutableSetOf(16)
        var at = 16
        while (at < whole.size) {
            val size = ((whole[at].toInt() and 0xFF) shl 24) or ((whole[at + 1].toInt() and 0xFF) shl 16) or
                ((whole[at + 2].toInt() and 0xFF) shl 8) or (whole[at + 3].toInt() and 0xFF)
            at += 4 + size
            boundaries.add(at)
        }

        for (cut in 0 until whole.size) {
            val decoded = OscPacket.decode(whole.copyOf(cut))
            if (cut in boundaries) {
                assertTrue("cut at element boundary $cut is a shorter bundle", decoded is OscBundle)
            } else {
                assertNull("cut at $cut", decoded)
            }
        }
    }

    @Test
    fun unknownTagIsNull() {
        assertNull(OscPacket.decode(bytes("/a", 0, 0, ",q", 0, 0)))
    }

    @Test
    fun missingTypeTagStringIsNull() {
        assertNull(OscPacket.decode(bytes("/abc", 0, 0, 0, 0)))
    }

    @Test
    fun badPaddingIsNull() {
        assertNull(OscPacket.decode(bytes("/a", 0, 1, ",", 0, 0, 0)))
        assertNull(OscPacket.decode(bytes("/a", 0, 0, ",s", 0, 0, "x", 0, 7, 0)))
    }

    @Test
    fun trailingBytesAfterAMessageAreNull() {
        assertNull(OscPacket.decode(bytes("/a", 0, 0, ",", 0, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun badBundleSizesAreNull() {
        val header = bytes("#bundle", 0, 0, 0, 0, 0, 0, 0, 0, 1)
        val element = OscMessage("/a").encode()
        fun withSize(size: Int) = bytes(header, size ushr 24, size ushr 16, size ushr 8, size and 0xFF, element)
        assertNotNull(OscPacket.decode(withSize(element.size)))
        assertNull("size past the end", OscPacket.decode(withSize(element.size + 4)))
        assertNull("size not a multiple of four", OscPacket.decode(withSize(element.size - 1)))
        assertNull("zero size", OscPacket.decode(withSize(0)))
        assertNull("negative size", OscPacket.decode(withSize(-4)))
        assertNull("not #bundle", OscPacket.decode(bytes("#bundlx", 0, 0, 0, 0, 0, 0, 0, 0, 1)))
    }

    @Test
    fun garbageIsNullAndNeverThrows() {
        val random = java.util.Random(31)
        repeat(5000) {
            val length = random.nextInt(64)
            val junk = ByteArray(length).also(random::nextBytes)
            if (length > 0) junk[0] = if (random.nextBoolean()) '/'.code.toByte() else '#'.code.toByte()
            OscPacket.decode(junk) // must return, whatever it returns
        }
        assertNull(OscPacket.decode(ByteArray(0)))
        assertNull(OscPacket.decode(bytes("abcd")))
    }
}
