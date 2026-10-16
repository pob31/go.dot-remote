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

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * QUITTING THE REMOTE FOR GOOD (the author, 2026-10-16: "how do you stop the background
 * server, terminating the app for good?"). The link service outlives its screen on purpose;
 * Quit - a button on the Connections tab, an action on the link's notification - is the
 * one way to end it short of Android's force stop.
 *
 * A quit unlinks every machine WITHOUT touching what is remembered: each machine keeps its
 * Linked switch, so the next launch links it again. It is not "unlink everything".
 */
object Quit {
    /** The notification's action: an intent to the service with this action quits it. */
    const val ACTION = "com.pob31.godot.remote.action.QUIT"

    private val _asked = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Heard by the open screen, which finishes and takes its task out of the recent apps. */
    val asked: SharedFlow<Unit> = _asked.asSharedFlow()

    /** Every open screen is told to go; none open, nothing happens - nothing is kept for a later one. */
    fun closeScreens() {
        _asked.tryEmit(Unit)
    }
}

/** The steps of a quit, in their order: the service does them, a test reads them. */
interface QuitSteps {
    /** Nothing more is watched: no notification re-posted, no machine chosen, no FIND heard. */
    fun stopWatching()

    /** Every link closed; the remembered machines left as they are. */
    fun closeLinks()

    fun releaseWifiLock()

    /** The service out of the foreground and its notification gone. */
    fun removeNotification()

    fun stopService()

    fun closeScreens()
}

/**
 * A QUIT, ONCE. The button and the notification may both be pressed, or the notification
 * twice: the second does nothing. A launch after a quit that finds the same service still
 * alive (bound by a screen on its way out) starts it again ([revive]).
 */
class Quitter(private val steps: QuitSteps) {
    var quit: Boolean = false
        private set

    /** True when this call quit; false when it was already done. */
    fun quit(): Boolean {
        if (quit) return false
        quit = true
        steps.stopWatching()
        steps.closeLinks()
        steps.releaseWifiLock()
        steps.removeNotification()
        steps.stopService()
        steps.closeScreens()
        return true
    }

    /** The service is wanted again after a quit: true when it had quit and must link again. */
    fun revive(): Boolean {
        if (!quit) return false
        quit = false
        return true
    }
}

/**
 * Quit's links: every one closed - Go.dot sees the tablet go - none left on screen. The
 * remembered machines are not here to touch: each keeps its Linked switch for the next launch.
 */
fun closeAllLinks(links: MutableStateFlow<List<LinkedMachine>>, onScreen: MutableStateFlow<LinkedMachine?>) {
    val all = links.value
    links.value = emptyList()
    onScreen.value = null
    all.forEach { it.link.close() }
}
