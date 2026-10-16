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

/**
 * WHICH PICTURE GOES WITH WHAT (Go.dot's client/model/Icons.h): an icon here is only a
 * name; ui/GodotIcons draws each as lines on a 24-unit grid, the desktop's own shapes -
 * never an emoji or a font's glyph. An icon is never the only telling (PRD §4.8): a word
 * stands beside it.
 *
 * The last three are the remote's own, drawn on the same grid: the standby arrows and
 * the Machines tab's screen.
 */
enum class GodotIcon {
    NONE,
    // What a cue is.
    MEMO, MEDIA, MIC, VIDEO, FADE, START, OSC, MIDI, PROCESS, SCRIPT, TIMECODE, GROUP, RANGE, TRIGGER,
    // What a transport cue does, by its verb.
    STOP, STOP_FADE, STOP_AFTER, ADVANCE, RECORD, LOOP, OVERDUB, CLEAR, ENABLE, JUMP,
    // How a group runs its members.
    SEQUENCE, TIMELINE, SAMPLER, TIME_GROUP,
    // Marks and panels the tabs use.
    DISABLED, LIST, SURFACE, WARNING,
    // The remote's own.
    PREVIOUS, NEXT, MACHINE, PROMPT, BUZZER,
}

/**
 * The icon a cue is recognised by (Icons.cpp `iconFor`): a group's mode, a transport
 * cue's verb, else its kind; a kind this app does not know yet draws nothing rather than
 * somebody else's picture.
 */
fun iconFor(kind: String, mode: String = "", verb: String = ""): GodotIcon {
    if (kind == "group") {
        return when (mode) {
            "timeline" -> GodotIcon.TIMELINE
            "sampler" -> GodotIcon.SAMPLER
            "time" -> GodotIcon.TIME_GROUP
            "sequence" -> GodotIcon.SEQUENCE
            else -> GodotIcon.GROUP // no word: the plain group
        }
    }
    if (kind == "transport") {
        return when (verb) {
            "fade" -> GodotIcon.STOP_FADE
            "afterMember", "afterIteration" -> GodotIcon.STOP_AFTER
            "advance" -> GodotIcon.ADVANCE
            "record" -> GodotIcon.RECORD
            "loop" -> GodotIcon.LOOP
            "overdub" -> GodotIcon.OVERDUB
            "clear" -> GodotIcon.CLEAR
            "enable" -> GodotIcon.ENABLE
            "disable" -> GodotIcon.DISABLED
            "jump" -> GodotIcon.JUMP
            else -> GodotIcon.STOP // hard, and a verb nobody has read yet
        }
    }
    return when (kind) {
        "memo" -> GodotIcon.MEMO
        "media" -> GodotIcon.MEDIA
        "mic" -> GodotIcon.MIC
        "video" -> GodotIcon.VIDEO
        "fade" -> GodotIcon.FADE
        "start" -> GodotIcon.START
        "osc" -> GodotIcon.OSC
        "midi" -> GodotIcon.MIDI
        "process" -> GodotIcon.PROCESS
        "script" -> GodotIcon.SCRIPT
        "timecode" -> GodotIcon.TIMECODE
        "range" -> GodotIcon.RANGE
        "trigger" -> GodotIcon.TRIGGER
        else -> GodotIcon.NONE
    }
}
