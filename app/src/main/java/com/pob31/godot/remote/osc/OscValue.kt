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

/**
 * One OSC argument, by the tags the engine uses (remote protocol §3): `i h f d s b T F N I t`.
 * Plain Kotlin, no Android, so the codec is tested on the JVM as the tablet runs it.
 */
sealed class OscValue {
    abstract val tag: Char

    data class Int32(val value: Int) : OscValue() { override val tag get() = 'i' }
    data class Int64(val value: Long) : OscValue() { override val tag get() = 'h' }
    data class Float32(val value: Float) : OscValue() { override val tag get() = 'f' }
    data class Float64(val value: Double) : OscValue() { override val tag get() = 'd' }
    data class Str(val value: String) : OscValue() { override val tag get() = 's' }

    class Blob(val bytes: ByteArray) : OscValue() {
        override val tag get() = 'b'
        override fun equals(other: Any?) = other is Blob && bytes.contentEquals(other.bytes)
        override fun hashCode() = bytes.contentHashCode()
        override fun toString() = "Blob(${bytes.size} bytes)"
    }

    /** An NTP time tag: seconds since 1900 in the high 32 bits, the fraction in the low. */
    data class TimeTag(val value: Long) : OscValue() { override val tag get() = 't' }

    data object True : OscValue() { override val tag get() = 'T' }
    data object False : OscValue() { override val tag get() = 'F' }
    data object Nil : OscValue() { override val tag get() = 'N' }
    data object Impulse : OscValue() { override val tag get() = 'I' }

    /**
     * The value as the operator would read it, and as a write would carry it: numbers
     * in no locale's way (a decimal point, never a comma), a boolean as `true`/`false`.
     */
    fun text(): String = when (this) {
        is Int32 -> value.toString()
        is Int64 -> value.toString()
        is Float32 -> value.toString()
        is Float64 -> value.toString()
        is Str -> value
        is Blob -> ""
        is TimeTag -> value.toString()
        True -> "true"
        False -> "false"
        Nil, Impulse -> ""
    }

    /** The value as a number where it is one; a boolean reads 1 or 0, as the engine's coercion does. */
    fun asDouble(): Double? = when (this) {
        is Int32 -> value.toDouble()
        is Int64 -> value.toDouble()
        is Float32 -> value.toDouble()
        is Float64 -> value
        is Str -> value.trim().toDoubleOrNull()
        True -> 1.0
        False -> 0.0
        else -> null
    }

    fun asLong(): Long? = when (this) {
        is Int32 -> value.toLong()
        is Int64 -> value
        is Float32 -> value.toLong()
        is Float64 -> value.toLong()
        is Str -> value.trim().toLongOrNull()
        True -> 1L
        False -> 0L
        else -> null
    }

    fun asBoolean(): Boolean? = when (this) {
        True -> true
        False -> false
        is Int32 -> value != 0
        is Int64 -> value != 0L
        is Str -> when (value) { "true" -> true; "false" -> false; else -> null }
        else -> null
    }

    companion object {
        fun of(value: Boolean): OscValue = if (value) True else False
    }
}
