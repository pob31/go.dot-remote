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

package com.pob31.godot.remote.media

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/** The cache's paths, what it fetches and when, what it keeps across launches, and Forget. */
class TimbreCacheTest {

    private val sweep = TimbreTest.sweep()
    private val hash = sweep.hash
    private val root: File = Files.createTempDirectory("shows").toFile()
    private val machine = TimbreSource("192.168.1.10", 5010)

    @After
    fun clean() {
        root.deleteRecursively()
    }

    /** Go.dot's route, as a fetcher: every GET recorded; [up] false is a machine that does not answer. */
    private inner class Route(var info: String = sweep.info) : TimbreFetcher {
        val asked = CopyOnWriteArrayList<String>()
        var up = true
        var level: (Int) -> ByteArray? = { sweep.levels.getOrNull(it) }

        override fun get(source: TimbreSource, pathAndQuery: String): ByteArray? {
            asked += pathAndQuery
            if (!up) return null
            val prefix = "/media/$hash/timbre?"
            if (!pathAndQuery.startsWith(prefix)) return null
            val query = pathAndQuery.removePrefix(prefix)
            return when {
                query == "INFO" -> info.toByteArray()
                query.startsWith("level=") -> level(query.removePrefix("level=").toInt())
                else -> null
            }
        }
    }

    private fun strip(cache: TimbreCache, width: Int, show: String = "Hamlet", source: TimbreSource? = machine) =
        runBlocking { cache.strip(source, show, hash, width) }

    @Test
    fun theHeaderThenTheLevelThatFitsKeptUnderTheShowsFolder() {
        val route = Route()
        val strip = strip(TimbreCache(root, route), 40)!!
        assertEquals(2, strip.level)
        assertEquals(71, strip.frames.count)
        assertEquals(listOf("/media/$hash/timbre?INFO", "/media/$hash/timbre?level=2"), route.asked)
        val dir = File(root, "Hamlet/timbre")
        assertArrayEquals(sweep.levels[2], File(dir, "$hash.L2").readBytes())
        assertEquals(sweep.info, File(dir, "$hash.info").readText())
        assertFalse(File(dir, "$hash.L2.part").exists())
    }

    @Test
    fun aStripSeenOnceIsNotAskedForAgain() {
        val route = Route()
        val cache = TimbreCache(root, route)
        strip(cache, 40)
        strip(cache, 40)
        strip(cache, 50) // the same level
        assertEquals(2, route.asked.size)
        // Another width, another level: that level alone, the header already asked this session.
        assertEquals(0, strip(cache, 300)!!.level)
        assertEquals(listOf("/media/$hash/timbre?INFO", "/media/$hash/timbre?level=2", "/media/$hash/timbre?level=0"), route.asked)
    }

    @Test
    fun keptAcrossLaunchesAndDrawnWithNoMachine() {
        strip(TimbreCache(root, Route()), 40)
        // The next launch, the machine away: the tablet's own copy.
        val away = Route().also { it.up = false }
        val strip = strip(TimbreCache(root, away), 40)
        assertNotNull(strip)
        assertArrayEquals(sweep.levels[2], strip!!.frames.bytes)
        // With no link at all, too.
        assertNotNull(strip(TimbreCache(root, away), 40, source = null))
        // A level never fetched cannot be drawn without the machine.
        assertNull(strip(TimbreCache(root, away), 300))
    }

    @Test
    fun theNextLaunchAsksTheHeaderOnceAndTakesTheLevelFromTheTablet() {
        strip(TimbreCache(root, Route()), 40)
        val route = Route()
        val cache = TimbreCache(root, route)
        strip(cache, 40)
        strip(cache, 40)
        assertEquals(listOf("/media/$hash/timbre?INFO"), route.asked)
    }

    @Test
    fun anotherAnalysisThrowsTheKeptLevelsAway() {
        strip(TimbreCache(root, Route()), 40)
        strip(TimbreCache(root, Route()), 300)
        val dir = File(root, "Hamlet/timbre")
        assertTrue(File(dir, "$hash.L0").exists())
        // A build later the ramp moved: formatVersion 5, the same hash, other colours.
        val other = sweep.levels[2].map { (it + 1).toByte() }.toByteArray()
        val route = Route(sweep.info.replace("\"formatVersion\":4", "\"formatVersion\":5"))
        route.level = { if (it == 2) other else sweep.levels.getOrNull(it) }
        val strip = strip(TimbreCache(root, route), 40)!!
        assertEquals(5, strip.info.formatVersion)
        assertArrayEquals(other, strip.frames.bytes)
        assertFalse(File(dir, "$hash.L0").exists())
        assertArrayEquals(other, File(dir, "$hash.L2").readBytes())
    }

    @Test
    fun bytesOfTheWrongLengthAreNothingDrawnAndNothingKept() {
        val route = Route()
        route.level = { sweep.levels[it].copyOf(sweep.levels[it].size - 3) }
        assertNull(strip(TimbreCache(root, route), 40))
        assertFalse(File(root, "Hamlet/timbre/$hash.L2").exists())
        // A header that is not one: nothing.
        assertNull(strip(TimbreCache(root, Route("<html>404</html>")), 40, show = "Other"))
        // A kept level gone bad is thrown away and fetched again.
        strip(TimbreCache(root, Route()), 40)
        File(root, "Hamlet/timbre/$hash.L2").writeBytes(ByteArray(7))
        val again = Route()
        assertNotNull(strip(TimbreCache(root, again), 40))
        assertTrue("/media/$hash/timbre?level=2" in again.asked)
        assertArrayEquals(sweep.levels[2], File(root, "Hamlet/timbre/$hash.L2").readBytes())
    }

    @Test
    fun aHashThatIsNotOneIsNeverAsked() {
        val route = Route()
        val cache = TimbreCache(root, route)
        assertNull(runBlocking { cache.strip(machine, "Hamlet", "../../etc", 40) })
        assertNull(runBlocking { cache.strip(machine, "Hamlet", hash.uppercase(), 40) })
        assertNull(runBlocking { cache.strip(machine, "Hamlet", hash, 0) })
        assertTrue(route.asked.isEmpty())
    }

    @Test
    fun aShowsNameIsMadeSafeForAFolder() {
        assertEquals("Hamlet_ Act 1_2", ShowFolders.folderName("Hamlet: Act 1/2"))
        assertEquals("untitled", ShowFolders.folderName(""))
        assertEquals("untitled", ShowFolders.folderName(".."))
        assertEquals("untitled", ShowFolders.folderName("  .  "))
        assertEquals("a_b_c", ShowFolders.folderName("a\tb\nc"))
        assertEquals("Le Malade imaginaire", ShowFolders.folderName("Le Malade imaginaire"))
        assertEquals(80, ShowFolders.folderName("x".repeat(200)).length)
        strip(TimbreCache(root, Route()), 40, show = "Hamlet: Act 1/2")
        assertTrue(File(root, "Hamlet_ Act 1_2/timbre/$hash.L2").isFile)
    }

    @Test
    fun theSameSoundInTwoShowsIsKeptTwice() {
        val cache = TimbreCache(root, Route())
        strip(cache, 40, show = "Hamlet")
        strip(cache, 40, show = "Macbeth")
        assertTrue(File(root, "Hamlet/timbre/$hash.L2").isFile)
        assertTrue(File(root, "Macbeth/timbre/$hash.L2").isFile)
    }

    @Test
    fun theFoldersAreListedWithTheirSizeAndSoundsAndForgotten() {
        val cache = TimbreCache(root, Route())
        strip(cache, 40, show = "Hamlet")
        strip(cache, 300, show = "Hamlet")
        strip(cache, 40, show = "Macbeth")
        val listed = cache.list()
        assertEquals(listOf("Hamlet", "Macbeth"), listed.map { it.name })
        assertEquals(1, listed[0].sounds) // one sound, two of its levels
        assertEquals((sweep.info.length + sweep.levels[2].size + sweep.levels[0].size).toLong(), listed[0].bytes)
        assertEquals((sweep.info.length + sweep.levels[2].size).toLong(), listed[1].bytes)
        assertTrue(cache.memoryHeld > 0)

        assertTrue(cache.forget("Hamlet"))
        assertFalse(File(root, "Hamlet").exists())
        assertEquals(listOf("Macbeth"), cache.list().map { it.name })
        // Nothing of it left in memory: drawn again, it is fetched again.
        assertEquals(sweep.levels[2].size, cache.memoryHeld)
        val route = Route()
        val after = TimbreCache(root, route)
        strip(after, 40, show = "Hamlet")
        assertEquals(2, route.asked.size)
    }

    @Test
    fun forgetTouchesNothingOutsideTheShowsFolders() {
        val outside = File(root.parentFile, root.name + "-sibling").apply { mkdirs() }
        try {
            File(outside, "keep").writeText("x")
            val cache = TimbreCache(root, Route())
            assertFalse(cache.forget("../" + outside.name))
            assertFalse(cache.forget(".."))
            assertFalse(cache.forget(""))
            assertFalse(cache.forget("Nothing here"))
            assertTrue(File(outside, "keep").exists())
        } finally {
            outside.deleteRecursively()
        }
    }

    @Test
    fun theMemoryKeepsTheNewestWithinItsBound() {
        val cache = TimbreCache(root, Route(), memoryBytes = 1200)
        strip(cache, 300, show = "A") // level 0, 1128 bytes
        strip(cache, 40, show = "B") // level 2, 284 bytes: A's goes
        assertEquals(284, cache.memoryHeld)
        assertTrue(cache.memoryHeld <= 1200)
    }

    // ------------------------------------------------------------------ over HTTP

    @Test
    fun theHttpFetcherTakesA200AndNothingElse() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse.Builder().body(Buffer().write(sweep.levels[3])).build())
            server.enqueue(MockResponse.Builder().code(404).body("no timbre has been analysed for $hash\n").build())
            val source = TimbreSource("127.0.0.1", server.port)
            val fetcher = HttpTimbreFetcher()
            assertArrayEquals(sweep.levels[3], fetcher.get(source, "/media/$hash/timbre?level=3"))
            assertNull(fetcher.get(source, "/media/$hash/timbre?level=9"))
            val asked = server.takeRequest().url
            assertEquals("/media/$hash/timbre", asked.encodedPath)
            assertEquals("level=3", asked.encodedQuery)
        } finally {
            server.close()
        }
        // A machine that is not there: nothing, no exception.
        assertNull(HttpTimbreFetcher().get(TimbreSource("127.0.0.1", 1), "/media/$hash/timbre?INFO"))
    }
}
