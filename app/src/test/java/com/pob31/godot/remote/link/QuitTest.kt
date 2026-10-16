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

import com.pob31.godot.remote.await
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quit (the Connections tab's button, the notification's action): what it does, in what order, and once. */
class QuitTest {

    private class Steps : QuitSteps {
        val done = ArrayList<String>()
        override fun stopWatching() { done += "stop watching" }
        override fun closeLinks() { done += "close links" }
        override fun releaseWifiLock() { done += "release the Wi-Fi lock" }
        override fun removeNotification() { done += "remove the notification" }
        override fun stopService() { done += "stop the service" }
        override fun closeScreens() { done += "close the screens" }
    }

    @Test
    fun aQuitStopsWatchingBeforeItClosesTheLinks_soNothingPutsTheNotificationBack() {
        val steps = Steps()
        assertTrue(Quitter(steps).quit())
        assertEquals(
            listOf(
                "stop watching", "close links", "release the Wi-Fi lock",
                "remove the notification", "stop the service", "close the screens",
            ),
            steps.done
        )
    }

    @Test
    fun theButtonAndTheNotificationBothPressedQuitOnce() {
        val steps = Steps()
        val quitter = Quitter(steps)
        assertTrue(quitter.quit())
        assertFalse(quitter.quit())
        assertEquals(6, steps.done.size)
        assertTrue(quitter.quit)
    }

    @Test
    fun aLaunchAfterAQuitLinksAgain_andOnlyAfterAQuit() {
        val quitter = Quitter(Steps())
        assertFalse("a service that never quit has nothing to start again", quitter.revive())
        quitter.quit()
        assertTrue(quitter.revive())
        assertFalse(quitter.quit)
        assertFalse(quitter.revive())
        assertTrue("and it may be quit again", quitter.quit())
    }

    @Test
    fun theOpenScreenHearsTheQuit() = runBlocking {
        val heard = async { withTimeout(5000) { Quit.asked.first() } }
        // Asked again until the collector is listening: the flow keeps nothing for a later screen.
        while (!heard.isCompleted) {
            Quit.closeScreens()
            yield()
        }
        assertEquals(Unit, heard.await())
    }

    @Test
    fun closingTheLinksClosesEverySocketAndLeavesNothingOnScreen() {
        val main = FakeEngine()
        val backup = FakeEngine()
        val timing = LinkTiming()
        fun linked(name: String, engine: FakeEngine): LinkedMachine {
            val machine = Machine(name, "127.0.0.1", engine.port, linked = true)
            return LinkedMachine(machine, Link(machine.host, machine.port, Identity("tablet-id-1", "Stage left"), Link.httpClient(timing), timing))
        }
        val both = listOf(linked("Main FOH", main), linked("Backup", backup))
        try {
            both.forEach { it.link.start() }
            await("both links are live") { both.all { it.link.state.value == LinkState.LIVE } }
            val links = MutableStateFlow(both)
            val onScreen = MutableStateFlow<LinkedMachine?>(both[0])

            closeAllLinks(links, onScreen)

            assertTrue(links.value.isEmpty())
            assertNull(onScreen.value)
            assertTrue(both.all { it.link.state.value == LinkState.OFF })
            await("each Go.dot sees the tablet go") { main.closedWith == 1000 && backup.closedWith == 1000 }
            // What is remembered is not the links': each machine still says it was left linked.
            assertTrue(both.all { it.machine.linked })
        } finally {
            both.forEach { it.link.close() }
            main.close()
            backup.close()
        }
    }
}
