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

package com.pob31.godot.remote.buzzer

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * THE BUZZER, OFFERED TO CHOUFLEUR'S PAGE. A WebView has no Web Bluetooth at all, so the page
 * in the Script tab cannot pair the wrist buzzer itself; the remote holds the link
 * ([BuzzerLink]) and the page writes through `window.GoDotRemoteBuzzer`:
 *
 * - `write(hex)` - one frame, two or four hex digits ("02", "0500"), written without response;
 *   false when nothing is linked or the bytes are not a frame;
 * - `linked()` - whether a buzzer is linked now;
 * - `state()` - the detail below, as JSON, for a page that has just loaded.
 *
 * And it tells the page when the link changes, as a `godotremotebuzzer` event on `window`
 * whose detail is `{paired, linked, name, battery, contract, calibrated, driver}`.
 *
 * Only for Choufleur's page: every call answers nothing unless the page on screen is at the
 * machine of the address set ([isChoufleur]), and the Script tab keeps any other address out
 * of the WebView. The page computes the warnings; the bridge only carries the bytes.
 */
class BuzzerBridge(
    private val view: WebView,
    private val link: BuzzerLink,
    private val isChoufleur: () -> Boolean,
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val api = BuzzerJsApi(link, isChoufleur)

    init {
        attach()
    }

    @SuppressLint("JavascriptInterface", "AddJavascriptInterface")
    private fun attach() {
        view.addJavascriptInterface(api, NAME)
        scope.launch {
            link.status.map(::bridgeDetail).distinctUntilChanged().collect { tell(it) }
        }
    }

    /** The page told the link's state again, after it has loaded. */
    fun pageLoaded() = tell(bridgeDetail(link.status.value))

    private fun tell(detail: String) {
        if (!isChoufleur()) return
        view.evaluateJavascript("window.dispatchEvent(new CustomEvent('$EVENT',{detail:$detail}));", null)
    }

    override fun close() {
        scope.cancel()
        runCatching { view.removeJavascriptInterface(NAME) }
    }

    companion object {
        const val NAME = "GoDotRemoteBuzzer"
        const val EVENT = "godotremotebuzzer"
    }
}

/**
 * What the page sees: the annotated methods and nothing else of the remote. Public, as the
 * WebView's bridge calls it by reflection; on the WebView's own thread, not the UI's.
 */
class BuzzerJsApi(private val link: BuzzerLink, private val isChoufleur: () -> Boolean) {
    @JavascriptInterface
    fun write(hex: String?): Boolean {
        if (!isChoufleur()) return false
        val frame = BuzzerContract.parseHex(hex ?: return false) ?: return false
        return link.write(frame)
    }

    @JavascriptInterface
    fun linked(): Boolean = isChoufleur() && link.status.value.phase == BuzzerPhase.LINKED

    @JavascriptInterface
    fun state(): String = if (isChoufleur()) bridgeDetail(link.status.value) else "{}"
}

/** The event's detail as JSON, written by hand: pure, so it is tested on the JVM. */
fun bridgeDetail(status: BuzzerStatus): String {
    val info = status.info
    fun bool(value: Boolean?) = value?.toString() ?: "null"
    return buildString {
        append("{\"paired\":").append(status.remembered != null)
        append(",\"linked\":").append(status.phase == BuzzerPhase.LINKED)
        append(",\"name\":").append(status.remembered?.name?.let(::jsonString) ?: "null")
        append(",\"battery\":").append(status.battery?.toString() ?: "null")
        append(",\"contract\":").append(info?.contract?.toString() ?: "null")
        append(",\"calibrated\":").append(bool(info?.calibrated))
        append(",\"driver\":").append(bool(info?.driverPresent))
        append('}')
    }
}

/** A JSON string, safe inside the script the bridge evaluates (no line separator ends it early). */
fun jsonString(text: String): String = buildString {
    append('"')
    for (c in text) {
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '<' -> append("\\u003c")
            c < ' ' || c == ' ' || c == ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
    append('"')
}
