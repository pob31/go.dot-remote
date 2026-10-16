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

package com.pob31.godot.remote.prompt

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.pob31.godot.remote.buzzer.BuzzerBridge
import com.pob31.godot.remote.buzzer.BuzzerLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the Script tab's page stands. */
sealed interface PageState {
    /** No page asked for yet. */
    data object Empty : PageState

    data class Loading(val address: String) : PageState

    data class Shown(val address: String) : PageState

    /** It did not load: [problem] is a word key under `prompt.problem`, [detail] its argument. */
    data class Failed(val address: String, val problem: String, val detail: String = "") : PageState
}

/**
 * THE ONE WEBVIEW THE SCRIPT TAB SHOWS, held by the process rather than by the tab: switching
 * to the Cues tab and back must not reload Choufleur's page, which keeps its own WebSocket,
 * its scroll and its place in the script (it resyncs on a reconnect, but a reload is a blank
 * second and a lost scroll an operator would see mid-act). The tab attaches it when it is
 * shown and lets it go when it is not; the page runs on in between.
 *
 * Its context is a [MutableContextWrapper]: the activity while the tab shows it (a page's
 * dialogs want one), the application's while it does not, so a rotated-away activity is not
 * kept alive by the page. UI thread only.
 */
class PromptPage private constructor(private val app: Context) {

    private val _state = MutableStateFlow<PageState>(PageState.Empty)
    val state: StateFlow<PageState> = _state.asStateFlow()

    /** Counts the WebViews made: a new one (after its renderer died) is a new view to attach. */
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    private var view: WebView? = null
    private var wrapper: MutableContextWrapper? = null

    /** The address last asked for: shown again only when it changes, or on Reload. */
    private var asked: String? = null

    /** The Choufleur machine the page may stay on, any port; any other address opens in the tablet's browser. */
    private var machine: String? = null

    /** The wrist buzzer offered to Choufleur's page, made with each WebView. */
    private var bridge: BuzzerBridge? = null

    /** The WebView, made on first use, taken from any old parent and given [host] as its context. */
    fun attach(host: Context): WebView {
        val page = view ?: create()
        wrapper?.baseContext = host
        (page.parent as? ViewGroup)?.removeView(page)
        return page
    }

    /** The tab no longer shows [page]: it leaves its parent and lets go of the activity. */
    fun detach(page: WebView) {
        (page.parent as? ViewGroup)?.removeView(page)
        if (page === view) wrapper?.baseContext = app
    }

    /** Shows [address]: loaded when it is not the one asked for last, left alone when it is. */
    fun show(address: String) {
        if (asked == address && view != null) return
        load(address)
    }

    /** Reload: the address again from scratch when it failed, the page's own reload when it is shown. */
    fun reload() {
        val address = asked ?: return
        val page = view
        if (page == null || _state.value !is PageState.Shown) load(address) else {
            _state.value = PageState.Loading(address)
            page.reload()
        }
    }

    /** No address any more (Clear, Quit): the page and its connection go. */
    fun drop() {
        asked = null
        machine = null
        val page = view ?: run { _state.value = PageState.Empty; return }
        view = null
        bridge?.close()
        bridge = null
        (page.parent as? ViewGroup)?.removeView(page)
        page.destroy()
        wrapper = null
        _state.value = PageState.Empty
        _generation.value++
    }

    /** The current page is Choufleur's, at the address set: what a bridge asks before it answers. */
    @Volatile
    var onOrigin: Boolean = false
        private set

    private fun load(address: String) {
        asked = address
        machine = PromptAddress.machineOf(address)
        val page = view ?: create()
        _state.value = PageState.Loading(address)
        page.loadUrl(address)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): WebView {
        val context = MutableContextWrapper(app)
        val page = WebView(context)
        // MATCH_PARENT, not the wrap-content an AndroidView gives a view by default: a WebView
        // sized to its content takes its CSS viewport height from that content, and a page
        // laid out at `html, body { height: 100% }` - Choufleur's - comes out zero tall and
        // blank (found on the author's tablet against a live Choufleur).
        page.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wrapper = context
        view = page
        page.setBackgroundColor(GROUND)
        page.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            userAgentString = "$userAgentString GoDotRemote/${versionName()}"
        }
        if (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        page.webChromeClient = WebChromeClient() // the page's alert() and confirm() shown, not swallowed
        page.webViewClient = Client()
        // Before the first page, so the page finds it as it starts.
        bridge = BuzzerBridge(page, BuzzerLink.shared(app)) { onOrigin }
        return page
    }

    private fun versionName(): String =
        try {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }

    private fun onMachine(url: String): Boolean = machine != null && PromptAddress.machineOf(url) == machine

    private inner class Client : WebViewClient() {
        /** Set by an error in the main frame, read when the (error) page finishes. */
        private var failed = false

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            failed = false
            onOrigin = onMachine(url)
            val address = asked ?: return
            if (_state.value !is PageState.Failed) _state.value = PageState.Loading(address)
        }

        override fun onPageFinished(view: WebView, url: String) {
            val address = asked ?: return
            if (!failed && _state.value is PageState.Loading) _state.value = PageState.Shown(address)
            if (!failed) bridge?.pageLoaded()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            failed = true
            onOrigin = false
            val address = asked ?: return
            val problem = when (error.errorCode) {
                ERROR_CONNECT -> "connect"
                ERROR_HOST_LOOKUP -> "hostLookup"
                ERROR_TIMEOUT -> "timeout"
                else -> "other"
            }
            _state.value = PageState.Failed(address, problem, error.description?.toString().orEmpty())
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (!request.isForMainFrame) return
            failed = true
            onOrigin = false
            val address = asked ?: return
            _state.value = PageState.Failed(address, "http", errorResponse.statusCode.toString())
        }

        /** Choufleur's own addresses stay here; anything else opens in the tablet's browser. */
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (onMachine(url)) return false
            openOutside(view.context, url)
            return true
        }

        /** The page's renderer was killed (memory, a crash): the WebView is dropped, never the app. */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (view !== this@PromptPage.view) return true
            val address = asked
            this@PromptPage.view = null
            bridge?.close()
            bridge = null
            wrapper = null
            onOrigin = false
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            _state.value = if (address != null) PageState.Failed(address, "stopped") else PageState.Empty
            _generation.value++
            return true
        }
    }

    companion object {
        /** The app's ground, behind the page while it loads: no white flash in a dark wing. */
        private const val GROUND = 0xFF16161A.toInt()

        private const val CHROME = "com.android.chrome"

        @Volatile
        private var shared: PromptPage? = null

        fun shared(context: Context): PromptPage =
            shared ?: synchronized(this) {
                shared ?: PromptPage(context.applicationContext).also { shared = it }
            }

        /** The page, if one was ever asked for: Quit drops it without making one. */
        fun sharedOrNull(): PromptPage? = shared

        /**
         * Opens [address] in Chrome - the browser where the wrist buzzer can pair - or in
         * whatever browser the tablet has when Chrome is not there. False when none opens it.
         */
        fun openInChrome(context: Context, address: String): Boolean {
            val uri = Uri.parse(address)
            val chrome = Intent(Intent.ACTION_VIEW, uri).setPackage(CHROME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(chrome)
                true
            } catch (e: ActivityNotFoundException) {
                openOutside(context, address)
            }
        }

        fun openOutside(context: Context, address: String): Boolean =
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e: ActivityNotFoundException) {
                false
            }
    }
}
