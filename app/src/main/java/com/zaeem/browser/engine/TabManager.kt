package com.zaeem.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.text.TextUtils
import android.view.ViewGroup
import android.webkit.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.zaeem.browser.ZaeemApp
import com.zaeem.browser.core.*
import com.zaeem.browser.data.Prefs
import com.zaeem.browser.data.Stats
import java.io.ByteArrayInputStream
import java.security.SecureRandom

class Tab(val id: Int, val space: Prefs.Space) {
    var url = "about:blank"
    var title = "New tab"
    var progress = 100
    var webView: WebView? = null
    @Volatile var pageHost: String? = null
    var crashed = false
    val host: String get() = UrlUtils.host(url) ?: (if (url == "about:blank") "New tab" else url)
}

class TabManager(private val app: ZaeemApp) {
    val tabs = mutableListOf<Tab>()
    var active: Tab? = null
    var host: BrowserHost? = null
    var onPage: (() -> Unit)? = null           // title/url/progress changed for the active tab
    private var nextId = 1
    private val allowHttpHosts = HashSet<String>()
    private val upgraded = HashMap<String, String>()
    private val fpSeed = SecureRandom().nextInt()
    private val maxLive = 4

    fun inSpace(id: String) = tabs.filter { it.space.id == id }

    fun open(space: Prefs.Space, input: String?): Tab {
        val t = Tab(nextId++, space); tabs.add(t); active = t
        if (!input.isNullOrBlank()) navigate(t, input)
        host?.onTabsChanged(); return t
    }

    fun select(t: Tab) { active = t; trimLive(); host?.onTabsChanged() }

    fun navigate(t: Tab, input: String) {
        val cfg = app.prefs.config()
        var url = UrlUtils.resolve(input, app.prefs.searchEngine().template, cfg.httpsFirst)
        if (cfg.httpsFirst && url.startsWith("http://", true)) {
            val h = UrlUtils.host(url)
            if (h != null && !UrlUtils.isLocalHost(h) && h !in allowHttpHosts) {
                val https = UrlUtils.upgradeToHttps(url)!!; upgraded[https] = url; url = https; Stats.httpsUpgrades.incrementAndGet()
            }
        }
        t.url = url; t.pageHost = UrlUtils.host(url)
        if (url == "about:blank") return
        view(t).loadUrl(url)
    }

    fun close(t: Tab) {
        destroy(t); tabs.remove(t)
        if (active === t) active = tabs.lastOrNull { it.space.id == t.space.id } ?: tabs.lastOrNull()
        host?.onTabsChanged()
    }

    fun closeSpace(spaceId: String) { tabs.filter { it.space.id == spaceId }.forEach { destroy(it); tabs.remove(it) }; fixActive() }
    fun closeAll() { tabs.forEach { destroy(it) }; tabs.clear(); active = null; upgraded.clear(); allowHttpHosts.clear(); host?.onTabsChanged() }
    private fun fixActive() { if (active != null && active !in tabs) active = tabs.lastOrNull(); host?.onTabsChanged() }

    fun destroy(t: Tab) {
        t.webView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.stopLoading(); wv.clearHistory(); wv.webChromeClient = null; wv.destroy()
        }
        t.webView = null
    }

    /** Free memory under pressure: sleeping tabs keep their address and reload when opened again. */
    fun discardBackground() { tabs.filter { it !== active && it.webView != null }.forEach { it.webView?.onPause(); destroy(it) } }

    /** Memory: keep only a few live renderers; older tabs keep their URL and reload on demand. */
    private fun trimLive() {
        val live = tabs.filter { it.webView != null }
        if (live.size <= maxLive) return
        live.filter { it !== active }.take(live.size - maxLive).forEach { it.webView?.onPause(); destroy(it) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun view(t: Tab): WebView {
        t.webView?.let { return it }
        val ctx: Context = (host as android.app.Activity)
        val cfg = app.prefs.config()
        val wv = WebView(ctx)
        Engine.bind(wv, Engine.profileName(t.space.id, t.space.ephemeral))
        wv.layoutParams = ViewGroup.LayoutParams(-1, -1)
        wv.setBackgroundColor(android.graphics.Color.WHITE)   // pages without a background must stay readable
        wv.overScrollMode = android.view.View.OVER_SCROLL_NEVER
        if (cfg.disableAutofill) wv.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        wv.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = false; allowContentAccess = false
            @Suppress("DEPRECATION") run { allowFileAccessFromFileURLs = false; allowUniversalAccessFromFileURLs = false }
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = !cfg.blockPopups
            mediaPlaybackRequiresUserGesture = true
            safeBrowsingEnabled = cfg.safeBrowsing
            @Suppress("DEPRECATION") run { saveFormData = false; savePassword = false }
            setGeolocationEnabled(true)
            builtInZoomControls = true; displayZoomControls = false
            userAgentString = userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        }
        val cm = Engine.cookieManager(Engine.profileName(t.space.id, t.space.ephemeral))
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(wv, !cfg.blockThirdPartyCookies)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(wv, Scripts.build(cfg.fingerprint, cfg.blockWebRtc, fpSeed), setOf("*"))
        }
        wv.webViewClient = Client(t)
        wv.webChromeClient = Chrome(t)
        wv.setDownloadListener { url, ua, cd, mime, len -> host?.confirmDownload(url, ua, cd, mime, len) }
        wv.setOnLongClickListener {
            val r = wv.hitTestResult
            val u = r.extra
            if ((r.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || r.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) && u != null) {
                host?.showLinkPreview(u, t); true
            } else false
        }
        t.webView = wv; t.crashed = false
        if (t.url != "about:blank") wv.loadUrl(t.url)
        return wv
    }

    private fun errorPage(title: String, msg: String) =
        "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
            "<body style='background:#050807;color:#eaf5f0;font-family:sans-serif;padding:28px'>" +
            "<h2 style='color:#6cf2c2'>${TextUtils.htmlEncode(title)}</h2><p style='color:#8fa69c'>${TextUtils.htmlEncode(msg)}</p></body></html>"

    inner class Client(private val tab: Tab) : WebViewClient() {
        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
            if (!r.isForMainFrame) return false
            val scheme = r.url.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                Stats.externalLaunchesBlocked.incrementAndGet()
                host?.toast("Blocked attempt to open another app (${scheme ?: "unknown"}:)"); return true
            }
            val cfg = app.prefs.config()
            val u = r.url.toString()
            val h = r.url.host?.lowercase()
            if (cfg.httpsFirst && scheme == "http" && h != null && !UrlUtils.isLocalHost(h) && h !in allowHttpHosts) {
                val https = UrlUtils.upgradeToHttps(u)!!; upgraded[https] = u; Stats.httpsUpgrades.incrementAndGet()
                v.loadUrl(https); return true
            }
            return false
        }

        override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
            if (!app.prefs.config().blockTrackers || r.isForMainFrame) return null
            val h = r.url.host ?: return null
            if (app.blocklist.matcher.isBlocked(h, tab.pageHost)) {
                Stats.blocked(h.lowercase())
                return WebResourceResponse("text/plain", "utf-8", 204, "Blocked", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }

        override fun onPageStarted(v: WebView, url: String, f: Bitmap?) {
            tab.url = url; tab.pageHost = UrlUtils.host(url); tab.progress = 5
            if (url.startsWith("https://")) Stats.secureNavigations.incrementAndGet()
            else if (url.startsWith("http://")) Stats.insecureNavigations.incrementAndGet()
            if (tab === active) onPage?.invoke()
        }

        override fun onPageFinished(v: WebView, url: String) {
            tab.url = url; tab.title = v.title?.takeIf { it.isNotBlank() } ?: tab.host; tab.progress = 100
            if (tab === active) onPage?.invoke()
            host?.onTabsChanged()
        }

        override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
            if (!r.isForMainFrame) return
            val u = r.url.toString()
            val original = upgraded[u]
            val fallbackCodes = setOf(WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT, WebViewClient.ERROR_IO, WebViewClient.ERROR_FAILED_SSL_HANDSHAKE)
            if (original != null && e.errorCode in fallbackCodes) {
                val h = UrlUtils.host(original) ?: return
                v.loadDataWithBaseURL(null, errorPage("No secure connection", "$h does not offer HTTPS."), "text/html", "utf-8", null)
                host?.askHttpFallback(h) { allowHttpHosts.add(h); upgraded.remove(u); v.loadUrl(original) }
                return
            }
            v.loadDataWithBaseURL(null, errorPage("Can't open this page", e.description.toString()), "text/html", "utf-8", null)
        }

        override fun onReceivedSslError(v: WebView, handler: SslErrorHandler, e: SslError) {
            handler.cancel()                                   // never offer "proceed anyway"
            Stats.sslErrorsBlocked.incrementAndGet()
            val reason = when (e.primaryError) {
                SslError.SSL_EXPIRED -> "the certificate has expired"
                SslError.SSL_NOTYETVALID -> "the certificate is not valid yet"
                SslError.SSL_IDMISMATCH -> "the certificate was issued for a different site name"
                SslError.SSL_UNTRUSTED -> "the certificate authority is not trusted"
                SslError.SSL_DATE_INVALID -> "the certificate date is invalid"
                else -> "the certificate is invalid"
            }
            val h = UrlUtils.host(e.url) ?: e.url
            v.loadDataWithBaseURL(null, errorPage("Connection blocked", "$h: $reason. ZAEEM BROWSER never continues past certificate errors."), "text/html", "utf-8", null)
            host?.showTlsBlocked(h, reason)
        }

        override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean {
            // Contain renderer crashes/kills: drop this tab's view instead of crashing the whole browser.
            tab.crashed = true; destroy(tab)
            host?.toast("A tab's renderer stopped (${if (d.didCrash()) "crash" else "killed by system"}). Reopen it to reload.")
            host?.onTabsChanged(); return true
        }

        override fun doUpdateVisitedHistory(v: WebView, url: String, isReload: Boolean) { /* no history is recorded */ }
    }

    inner class Chrome(private val tab: Tab) : WebChromeClient() {
        override fun onProgressChanged(v: WebView, p: Int) { tab.progress = p; if (tab === active) onPage?.invoke() }
        override fun onReceivedTitle(v: WebView, t: String?) { if (!t.isNullOrBlank()) tab.title = t }
        override fun onCreateWindow(v: WebView, d: Boolean, g: Boolean, m: Message?) = false
        override fun onShowCustomView(view: android.view.View, cb: CustomViewCallback) { host?.showCustomView(view, cb) }
        override fun onHideCustomView() { host?.hideCustomView() }

        override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<android.net.Uri>>, p: FileChooserParams): Boolean {
            host?.chooseFile(p.createIntent(), cb) ?: cb.onReceiveValue(null); return true
        }

        override fun onPermissionRequest(req: PermissionRequest) {
            val origin = req.origin.toString()
            val supported = req.resources.filter { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
            if (supported.isEmpty()) { req.deny(); return }
            val granted = mutableListOf<String>()
            fun finish() {
                if (granted.isEmpty()) { req.deny(); return }
                val rt = granted.map { if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) android.Manifest.permission.CAMERA else android.Manifest.permission.RECORD_AUDIO }
                host?.ensureRuntimePermissions(rt) { ok -> if (ok) req.grant(granted.toTypedArray()) else req.deny() } ?: req.deny()
            }
            fun step(i: Int) {
                if (i >= supported.size) { finish(); return }
                val res = supported[i]
                val kind = if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE) PermKind.CAMERA else PermKind.MICROPHONE
                decide(origin, kind) { allow -> if (allow) granted.add(res); step(i + 1) }
            }
            step(0)
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, cb: GeolocationPermissions.Callback) {
            decide(origin, PermKind.LOCATION) { allow ->
                if (!allow) { cb.invoke(origin, false, false); return@decide }
                host?.ensureRuntimePermissions(listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)) { ok -> cb.invoke(origin, ok, false) }
                    ?: cb.invoke(origin, false, false)
            }
        }
    }

    private fun decide(origin: String, kind: PermKind, cb: (Boolean) -> Unit) {
        val cfg = app.prefs.config()
        when (app.perms.lookup(origin, kind)) {
            Decision.BLOCK -> return cb(false)
            Decision.ALWAYS_ALLOW, Decision.ALLOW_SESSION, Decision.ALLOW_ONCE -> return cb(true)
            null -> {}
        }
        val blockDefault = if (kind == PermKind.LOCATION) cfg.blockLocationByDefault else cfg.blockMicCameraByDefault
        if (blockDefault) { host?.toast("${kind.label} blocked for ${UrlUtils.host(origin) ?: origin} (default policy)"); return cb(false) }
        val h = host ?: return cb(false)
        h.askPermission(origin, kind) { d -> app.perms.record(origin, kind, d); cb(d != Decision.BLOCK) }
    }
}
