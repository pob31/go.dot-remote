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

import com.pob31.godot.remote.json.JsonPull
import com.pob31.godot.remote.json.jsonQuote
import com.pob31.godot.remote.model.Addresses
import com.pob31.godot.remote.model.Mirror
import com.pob31.godot.remote.model.NodeMeta
import com.pob31.godot.remote.model.SnapshotReader
import com.pob31.godot.remote.osc.OscBundle
import com.pob31.godot.remote.osc.OscMessage
import com.pob31.godot.remote.osc.OscPacket
import com.pob31.godot.remote.osc.OscValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.Closeable
import java.io.IOException
import java.io.StringReader
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Where a link stands (remote protocol §7), each said in words on screen:
 * - [CONNECTING]: a socket is opening or the snapshot is being read;
 * - [LIVE]: a bundle came within the last half second;
 * - [STALE]: none for half a second - the app greys what it shows;
 * - [CLOCK_STOPPED]: none for three seconds while the socket still answers its pings -
 *   Go.dot's clock is standing still (an audio interface lost, PRD §6.2), not the network;
 * - [GONE]: the socket failed or closed, or a ping went unanswered; a new one is on its way;
 * - [OFF]: not linked, by the operator's choice.
 */
enum class LinkState { OFF, CONNECTING, LIVE, STALE, CLOCK_STOPPED, GONE }

/** The link's clocks. The defaults are the contract's; the tests shrink the waits. */
data class LinkTiming(
    val staleMs: Long = 500,
    val clockStoppedMs: Long = 3000,
    /** A WebSocket ping this often; OkHttp fails the socket when one goes unanswered by the next. */
    val pingMs: Long = 1000,
    val watchMs: Long = 100,
    val backoffMs: List<Long> = listOf(500, 1000, 2000, 5000),
    val throttleMs: Long = 20,
    /** Bundles kept while the snapshot is read: 1000 ticks is twenty seconds of them. */
    val keptMax: Int = 1000,
)

/** Who this tablet is to Go.dot (HELLO): an id drawn once at install, and the operator's name for it. */
data class Identity(val id: String, val name: String)

/**
 * ONE MACHINE'S LINK: a WebSocket to Go.dot, the [mirror] of its tree, and the writes.
 *
 * THE SEQUENCE is the contract's (§2): open the socket; send STREAM, then HELLO; GET the
 * whole tree; apply it; then the bundles that arrived meanwhile whose tick is later than
 * the snapshot's, in order; then every bundle as it comes. A bundle at the mirror's tick
 * or earlier is already in it and is dropped, whenever it arrives.
 *
 * ONE THREAD OF EVENTS. The socket's callbacks, the HTTP answers and the watchdog all
 * post to one channel, and one coroutine handles them in order; it alone writes the
 * mirror. Nothing here blocks the socket's reader on a parse.
 *
 * STRUCTURE (§4): PATH_ADDED makes the node; when what the node is (its metadata) is not
 * known for its row, its object is fetched once per burst - the PATH_ADDED frames before a
 * tick's bundle - and only the metadata is taken from the answer, never its values, which
 * are the stream's. PATH_REMOVED drops the node.
 *
 * WRITES (§6) go only on a socket that has said STREAM and HELLO, so they can never
 * overtake them; values are one string each, throttled per address ([WriteThrottle]).
 */
class Link(
    val host: String,
    val port: Int,
    private val identity: Identity,
    private val http: OkHttpClient,
    private val timing: LinkTiming = LinkTiming(),
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
) : Closeable {

    val mirror = Mirror()

    private val _state = MutableStateFlow(LinkState.OFF)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _problem = MutableStateFlow<String?>(null)

    /** Why the link last went down, as the system said it; null while it has not. */
    val problem: StateFlow<String?> = _problem.asStateFlow()

    private val _origin = MutableStateFlow<String?>(null)

    /** How Go.dot spells this tablet in its log (`ws:<ip>:<port>`), learned from `/godot/network/remotes`. */
    val origin: StateFlow<String?> = _origin.asStateFlow()

    private val _refusals = MutableSharedFlow<Refusal>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** The engine's refusals of what THIS tablet sent, as they come. */
    val refusals: SharedFlow<Refusal> = _refusals.asSharedFlow()

    private val _lastRefusal = MutableStateFlow<Refusal?>(null)
    val lastRefusal: StateFlow<Refusal?> = _lastRefusal.asStateFlow()

    private val _finds = MutableSharedFlow<FindRequest>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Go.dot asking this tablet to show itself (FIND, §5). */
    val finds: SharedFlow<FindRequest> = _finds.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val throttle = WriteThrottle(timing.throttleMs, scope, clockMs, ::sendNow)
    private val calls: MutableSet<Call> = Collections.synchronizedSet(HashSet())

    /** The socket writes may use: set once STREAM and HELLO are on it. */
    @Volatile private var openSocket: WebSocket? = null

    /** The generation a socket's callbacks must carry to count. */
    @Volatile private var liveGeneration = -1

    // ---------------------------------------------------------------- the processor's own

    private enum class Phase { IDLE, OPENING, SNAPSHOT, LIVE, DOWN }

    private var phase = Phase.IDLE
    private var wanted = false
    private var generation = 0
    private var socket: WebSocket? = null
    private val kept = ArrayList<Event.Frame>()
    private var appliedTick = -1L
    private var lastBundleAt = 0L
    private var attempt = 0

    /** This burst's objects whose rows' metadata is missing, with the keys missing. */
    private val burst = LinkedHashMap<String, MutableSet<String>>()

    /** Metadata keys a GET is already on its way for. */
    private val inFlightKeys = HashSet<String>()

    private sealed class Event {
        data object Start : Event()
        data object Stop : Event()
        data object Watch : Event()
        class Reconnect(val gen: Int) : Event()
        class Opened(val gen: Int) : Event()
        sealed class Frame(val gen: Int) : Event()
        class Text(gen: Int, val text: String) : Frame(gen)
        class Binary(gen: Int, val bytes: ByteArray) : Frame(gen)
        class Down(val gen: Int, val why: String) : Event()
        class Snapshot(val gen: Int, val result: SnapshotReader.Result) : Event()
        class Meta(val gen: Int, val keys: Set<String>, val result: SnapshotReader.Result?) : Event()
    }

    init {
        scope.launch { for (event in events) handle(event) }
        scope.launch {
            while (isActive) {
                delay(timing.watchMs)
                events.trySend(Event.Watch)
            }
        }
    }

    /** Link, and keep linking: reconnects follow until [stop]. */
    fun start() {
        events.trySend(Event.Start)
    }

    /** Unlink, by the operator's choice. */
    fun stop() {
        events.trySend(Event.Stop)
    }

    override fun close() {
        openSocket = null
        liveGeneration = -1
        socket?.close(1000, null)
        synchronized(calls) { calls.forEach { it.cancel() } }
        scope.cancel()
        _state.value = LinkState.OFF
    }

    // ---------------------------------------------------------------- writes

    /** The command `a.b` with its arguments as strings: `/godot/cmd/a/b ,s...` (§6). */
    fun command(name: String, vararg args: String): Boolean =
        sendNow(OscMessage("/godot/cmd/" + name.replace('.', '/'), args.map { OscValue.Str(it) }))

    /** A value, throttled to one write per address every 20 ms, the latest kept. */
    fun set(address: String, text: String) {
        throttle.offer(address, OscMessage(address, OscValue.Str(text)))
    }

    fun set(address: String, value: Double, decimals: Int = 4) = set(address, WireText.number(value, decimals))

    /** A value now, unthrottled, dropping any pending one: a typed value, a switch. */
    fun setNow(address: String, text: String): Boolean =
        throttle.sendNow(address, OscMessage(address, OscValue.Str(text)))

    fun setNow(address: String, value: Boolean): Boolean = setNow(address, WireText.bool(value))

    /** A drag begins (`node.touch`): the document will fold its run of writes into one undo step. */
    fun touch(address: String): Boolean = command("node.touch", address)

    /** A drag ends: its last value lands now, unthrottled, then `node.release`. */
    fun release(address: String): Boolean {
        throttle.flush(address)
        return command("node.release", address)
    }

    private fun sendNow(message: OscMessage): Boolean {
        val ws = openSocket ?: return false
        return ws.send(message.encode().toByteString())
    }

    // ---------------------------------------------------------------- the processor

    private fun handle(event: Event) {
        when (event) {
            Event.Start -> if (!wanted) {
                wanted = true
                attempt = 0
                connect()
            }
            Event.Stop -> if (wanted) {
                wanted = false
                teardown()
                socket?.close(1000, null)
                socket = null
                phase = Phase.IDLE
                _state.value = LinkState.OFF
            }
            Event.Watch -> watch()
            is Event.Reconnect -> if (wanted && phase == Phase.DOWN && event.gen == generation) connect()
            is Event.Opened -> if (event.gen == generation && phase == Phase.OPENING) opened()
            is Event.Frame -> if (event.gen == generation) frame(event)
            is Event.Down -> if (event.gen == generation && (phase == Phase.OPENING || phase == Phase.SNAPSHOT || phase == Phase.LIVE)) goDown(event.why)
            is Event.Snapshot -> if (event.gen == generation && phase == Phase.SNAPSHOT) snapshot(event.result)
            is Event.Meta -> if (event.gen == generation) meta(event)
        }
    }

    private fun connect() {
        generation++
        val gen = generation
        liveGeneration = gen
        phase = Phase.OPENING
        _state.value = LinkState.CONNECTING
        kept.clear()
        burst.clear()
        inFlightKeys.clear()
        val request = Request.Builder().url("ws://$host:$port/").build()
        socket = http.newWebSocket(request, Listener(gen))
    }

    private inner class Listener(private val gen: Int) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            // STREAM first so nothing falls between the snapshot and the stream (§2), then HELLO.
            webSocket.send(STREAM_FRAME)
            webSocket.send(helloFrame())
            if (gen == liveGeneration) openSocket = webSocket
            events.trySend(Event.Opened(gen))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            events.trySend(Event.Text(gen, text))
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            events.trySend(Event.Binary(gen, bytes.toByteArray()))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            events.trySend(Event.Down(gen, "closed by the machine: $code $reason".trim()))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            events.trySend(Event.Down(gen, "closed: $code $reason".trim()))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            events.trySend(Event.Down(gen, t.message ?: t.javaClass.simpleName))
        }
    }

    private fun helloFrame(): String {
        val name = identity.name.replace('\t', ' ').replace('\n', ' ')
        return "{\"COMMAND\": \"HELLO\", \"DATA\": ${jsonQuote(identity.id + "\t" + name)}}"
    }

    private fun opened() {
        phase = Phase.SNAPSHOT
        val gen = generation
        scope.launch {
            try {
                events.trySend(Event.Snapshot(gen, fetch("/godot")))
            } catch (e: Exception) {
                events.trySend(Event.Down(gen, "the snapshot: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    private fun fetch(path: String): SnapshotReader.Result {
        val call = http.newCall(Request.Builder().url("http://$host:$port$path").build())
        calls.add(call)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $path")
                return SnapshotReader.read(response.body.charStream())
            }
        } finally {
            calls.remove(call)
        }
    }

    private fun snapshot(result: SnapshotReader.Result) {
        mirror.install(result.state, result.tick)
        appliedTick = result.tick
        phase = Phase.LIVE
        attempt = 0
        lastBundleAt = clockMs()
        _state.value = LinkState.LIVE
        _problem.value = null
        learnOrigin()
        val replay = ArrayList(kept)
        kept.clear()
        for (frame in replay) live(frame)
    }

    private fun frame(frame: Event.Frame) {
        when (phase) {
            Phase.SNAPSHOT -> {
                // FIND is for now; everything else waits for the snapshot.
                if (frame is Event.Text && commandOf(frame.text)?.first == "FIND") {
                    live(frame)
                    return
                }
                kept.add(frame)
                if (kept.size > timing.keptMax) goDown("the snapshot took too long")
            }
            Phase.LIVE -> live(frame)
            else -> Unit
        }
    }

    private fun live(frame: Event.Frame) {
        when (frame) {
            is Event.Text -> text(frame.text)
            is Event.Binary -> binary(frame.bytes)
        }
    }

    private fun commandOf(text: String): Pair<String, String>? = try {
        var command: String? = null
        var data = ""
        JsonPull(StringReader(text)).use { json ->
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "COMMAND" -> command = json.nextString()
                    "DATA" -> if (json.peek() == JsonPull.Token.STRING) data = json.nextString()
                              else if (json.peek() == JsonPull.Token.NUMBER) data = json.nextNumber()
                              else json.skipValue()
                    else -> json.skipValue()
                }
            }
        }
        command?.let { it to data }
    } catch (e: IOException) {
        null
    }

    private fun text(text: String) {
        val (command, data) = commandOf(text) ?: return
        when (command) {
            "PATH_ADDED" -> {
                mirror.added(data)
                if (!mirror.hasMeta(data)) {
                    val key = Addresses.metaKey(data)
                    if (key !in inFlightKeys) burst.getOrPut(Addresses.objectOf(data)) { HashSet() }.add(key)
                }
            }
            "PATH_REMOVED" -> mirror.removed(data)
            "FIND" -> _finds.tryEmit(FindRequest(data.trim().toIntOrNull()?.takeIf { it > 0 } ?: 30))
        }
    }

    /**
     * The burst is over - a bundle came, or a watchdog beat passed with none: one GET per
     * object whose rows nobody has described yet, and none for an object whose rows
     * another GET in flight will describe.
     */
    private fun flushBurst() {
        if (burst.isEmpty()) return
        val gen = generation
        for ((objectPath, keys) in burst) {
            val missing = keys.filterTo(HashSet()) { it !in inFlightKeys && !mirror.hasMetaKey(it) }
            if (missing.isEmpty()) continue
            inFlightKeys.addAll(missing)
            scope.launch {
                val result = try {
                    fetch(objectPath)
                } catch (e: Exception) {
                    null // the object went, or the link did; its rows stay undescribed until asked again
                }
                events.trySend(Event.Meta(gen, missing, result))
            }
        }
        burst.clear()
    }

    private fun meta(event: Event.Meta) {
        inFlightKeys.removeAll(event.keys)
        val answer = event.result ?: return
        for (node in answer.state.nodes.values) {
            mirror.learnMeta(node.address, NodeMeta(node.access, node.range, node.unit, node.description))
        }
    }

    private fun binary(bytes: ByteArray) {
        val packet = OscPacket.decode(bytes) ?: return
        lastBundleAt = clockMs()
        flushBurst()
        val messages = when (packet) {
            is OscMessage -> listOf(packet)
            is OscBundle -> packet.messages()
        }
        val tick = messages.firstOrNull { it.address == Addresses.TICK }?.values?.firstOrNull()?.asLong()
        if (tick != null && tick <= appliedTick) return // already in the mirror
        var remotesChanged = false
        var errorChanged = false
        for (message in messages) {
            mirror.putValues(message.address, message.typeTags, message.values)
            when (message.address) {
                Addresses.REMOTES -> remotesChanged = true
                Addresses.LAST_ERROR -> errorChanged = true
            }
        }
        if (tick != null) {
            appliedTick = tick
            mirror.setTick(tick)
        }
        if (remotesChanged) learnOrigin()
        if (errorChanged) noteRefusal()
        if (_state.value != LinkState.LIVE) _state.value = LinkState.LIVE
    }

    private fun learnOrigin() {
        val remotes = mirror.text(Addresses.REMOTES) ?: return
        RemoteLine.originOf(remotes, identity.id)?.let { _origin.value = it }
    }

    private fun noteRefusal() {
        val refusal = Refusal.parse(mirror.text(Addresses.LAST_ERROR) ?: return) ?: return
        val mine = _origin.value ?: return
        if (refusal.origin != mine) return
        _lastRefusal.value = refusal
        _refusals.tryEmit(refusal)
    }

    private fun watch() {
        if (phase != Phase.LIVE) return
        val since = clockMs() - lastBundleAt
        val now = when {
            since >= timing.clockStoppedMs -> LinkState.CLOCK_STOPPED
            since >= timing.staleMs -> LinkState.STALE
            else -> LinkState.LIVE
        }
        if (_state.value != now) _state.value = now
        flushBurst()
    }

    private fun teardown() {
        openSocket = null
        liveGeneration = -1
        synchronized(calls) { calls.forEach { it.cancel() } }
        kept.clear()
        burst.clear()
        inFlightKeys.clear()
        throttle.clear()
    }

    private fun goDown(why: String) {
        teardown()
        socket?.cancel()
        socket = null
        phase = Phase.DOWN
        _state.value = LinkState.GONE
        _problem.value = why
        val wait = timing.backoffMs[minOf(attempt, timing.backoffMs.size - 1)]
        attempt++
        val gen = generation
        scope.launch {
            delay(wait)
            events.trySend(Event.Reconnect(gen))
        }
    }

    companion object {
        const val STREAM_FRAME = "{\"COMMAND\": \"STREAM\", \"DATA\": \"/godot\"}"

        /** The client every link shares: a ping a second (§7), no timeout on the socket itself. */
        fun httpClient(timing: LinkTiming = LinkTiming()): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(timing.pingMs, TimeUnit.MILLISECONDS)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
