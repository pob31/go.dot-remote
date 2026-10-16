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

import com.pob31.godot.remote.Fixtures
import com.pob31.godot.remote.await
import com.pob31.godot.remote.json.jsonQuote
import com.pob31.godot.remote.model.Addresses
import com.pob31.godot.remote.osc.OscBundle
import com.pob31.godot.remote.osc.OscMessage
import com.pob31.godot.remote.osc.OscPacket
import com.pob31.godot.remote.osc.OscValue
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Go.dot as the link sees it, faked with OkHttp's MockWebServer: `GET /godot` answers a
 * snapshot, `GET /godot/<owner>/<id>` an object, and `/` upgrades to a WebSocket whose
 * server side the test drives - bundles, PATH_ADDED, PATH_REMOVED, FIND, a close.
 */
class FakeEngine : Closeable {
    private var server = MockWebServer()

    @Volatile var snapshot: String = Fixtures.snapshotSmall()

    /** Whether `GET /?HOST_INFO` says STREAM: false is a Go.dot from before the remote. */
    @Volatile var streams: Boolean = true

    /** Run while `GET /godot` is being answered, before the answer leaves. */
    @Volatile var beforeSnapshot: (() -> Unit)? = null

    val objects = ConcurrentHashMap<String, String>()

    /** Every text frame the app sent, every connection, in order. */
    val texts = CopyOnWriteArrayList<String>()

    /** Every OSC packet the app sent. */
    val writes = CopyOnWriteArrayList<OscPacket>()

    /** The text frames received before the first `GET /godot` was answered. */
    @Volatile var textsBeforeSnapshot: List<String>? = null

    /** The paths of every plain GET. */
    val gets = CopyOnWriteArrayList<String>()

    val upgrades = AtomicInteger(0)

    @Volatile var socket: WebSocket? = null

    /** The code the app closed its WebSocket with, once it has. */
    @Volatile var closedWith: Int? = null

    val port: Int get() = server.port

    private val serverSide = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            socket = webSocket
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            texts.add(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            OscPacket.decode(bytes.toByteArray())?.let { writes.add(it) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            closedWith = code
            webSocket.close(1000, null)
        }
    }

    private val dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.headers["Upgrade"].equals("websocket", ignoreCase = true)) {
                upgrades.incrementAndGet()
                return MockResponse.Builder().webSocketUpgrade(serverSide).build()
            }
            val path = request.url.encodedPath
            gets.add(path)
            if (path == "/" && request.url.encodedQuery == "HOST_INFO") {
                val stream = if (streams) "\"STREAM\": true, " else ""
                return MockResponse.Builder().body(
                    "{\"NAME\": \"Go.dot\", \"OSC_PORT\": 8010, \"EXTENSIONS\": {\"ACCESS\": true, ${stream}\"VALUE\": true}}"
                ).build()
            }
            if (path == "/godot") {
                // The app sends STREAM and HELLO before it asks: wait for both to be read.
                val until = System.nanoTime() + 2_000_000_000L
                while (texts.size < 2 && System.nanoTime() < until) Thread.sleep(2)
                if (textsBeforeSnapshot == null) textsBeforeSnapshot = texts.toList()
                beforeSnapshot?.invoke()
                return MockResponse.Builder().body(snapshot).build()
            }
            val answer = objects[path] ?: return MockResponse.Builder().code(404).build()
            return MockResponse.Builder().body(answer).build()
        }
    }

    init {
        server.dispatcher = dispatcher
        server.start()
    }

    /** The machine dies - every connection dropped at once, no goodbye - and comes back on its port. */
    fun restart() {
        val port = server.port
        socket = null
        server.close()
        // The port may not be free again at once (seen on CI's Linux): tried for a few seconds.
        val until = System.nanoTime() + 5_000_000_000L
        while (true) {
            val next = MockWebServer().also { it.dispatcher = dispatcher }
            try {
                next.start(port)
                server = next
                return
            } catch (e: java.net.BindException) {
                runCatching { next.close() }
                if (System.nanoTime() > until) throw e
                Thread.sleep(50)
            }
        }
    }

    fun awaitSocket(): WebSocket {
        await("the app's WebSocket") { socket != null }
        return socket!!
    }

    fun bundle(tick: Long, vararg messages: OscMessage) {
        val elements = listOf<OscPacket>(OscMessage(Addresses.TICK, OscValue.Int64(tick))) + messages
        awaitSocket().send(OscBundle(OscBundle.IMMEDIATELY, elements).encode().toByteString())
    }

    fun text(command: String, data: String) {
        awaitSocket().send("{\"COMMAND\": ${jsonQuote(command)}, \"DATA\": ${jsonQuote(data)}}")
    }

    fun getsOf(path: String): Int = gets.count { it == path }

    /** The app's writes as messages, in the order they arrived. */
    fun messages(): List<OscMessage> = writes.flatMap {
        when (it) {
            is OscMessage -> listOf(it)
            is OscBundle -> it.messages()
        }
    }

    override fun close() {
        // The server side of a MockWebServer socket has no call to cancel: closing the server drops it.
        server.close()
    }
}
