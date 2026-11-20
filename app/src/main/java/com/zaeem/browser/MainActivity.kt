package com.zaeem.browser

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.zaeem.browser.core.*
import com.zaeem.browser.data.Prefs
import com.zaeem.browser.data.Stats
import com.zaeem.browser.engine.*
import com.zaeem.browser.security.AppLock
import com.zaeem.browser.ui.*
import kotlin.math.sqrt

abstract class Screen {
    lateinit var a: MainActivity
    abstract fun build(): View
    open fun onShow() {}
    open fun onHide() {}
    open fun onBack(): Boolean = false
}

class MainActivity : Activity(), BrowserHost, SensorEventListener {
    lateinit var app: ZaeemApp
    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private val stack = ArrayList<Screen>()
    private var lockView: View? = null
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var rtCb: ((Boolean) -> Unit)? = null
    private var rtPerms: List<String> = emptyList()
    private var bgAt = 0L
    private var lastShake = 0L
    private val main = Handler(Looper.getMainLooper())
    lateinit var space: Prefs.Space
    private var pendingUrl: String? = null
    private var externalLaunch = false
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    companion object {
        const val ACTION_PANIC = "com.zaeem.browser.PANIC"
        private const val REQ_FILE = 11
        private const val REQ_VOICE = 12
        private const val REQ_PERM = 12
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        app = application as ZaeemApp
        Ui.applyTheme(app.prefs.str("theme", "obsidian") ?: "obsidian", (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        app.host = this; app.tabs.host = this
        app.tabs.onPage = { (stack.lastOrNull() as? BrowserScreen)?.refreshChrome() }
        app.sessions.onEnded = { runOnUiThread { space = app.prefs.spaces().first { it.id == "personal" }; goHome(); toast("Timed session ended. Its data was erased.") } }
        space = app.prefs.spaces().first { it.id == "personal" }
        window.statusBarColor = Ui.BG; window.navigationBarColor = Ui.BG
        try { if (android.os.Build.VERSION.SDK_INT >= 30) { window.decorView; window.insetsController?.let { val m = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS; it.setSystemBarsAppearance(if (Ui.isLight) m else 0, m) } } } catch (_: Throwable) { }
        root = FrameLayout(this).apply { setBackgroundColor(Ui.BG); filterTouchesWhenObscured = true }   // tapjacking protection
        // Android 15 draws edge-to-edge for targetSdk 35: keep content clear of system bars, cutout and keyboard.
        root.setOnApplyWindowInsetsListener { v, ins ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = ins.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                val ime = ins.getInsets(android.view.WindowInsets.Type.ime())
                v.setPadding(b.left, b.top, b.right, maxOf(b.bottom, ime.bottom))
                android.view.WindowInsets.CONSUMED
            } else {
                @Suppress("DEPRECATION") v.setPadding(ins.systemWindowInsetLeft, ins.systemWindowInsetTop, ins.systemWindowInsetRight, ins.systemWindowInsetBottom)
                @Suppress("DEPRECATION") ins.consumeSystemWindowInsets()
            }
        }
        content = FrameLayout(this); root.addView(content)
        setContentView(root)
        applyScreenPolicy()
        startService(Intent(this, CleanupService::class.java))
        watchClipboard()
        if (intent?.action == ACTION_PANIC) { root.setBackgroundColor(0xFF000000.toInt()); app.eraser.panic(this); return }
        captureLink(intent)
        showSplash()
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        if (i?.action == ACTION_PANIC) { panic(); return }
        captureLink(i)
        if (lockView == null && stack.isNotEmpty()) consumePending()
    }

    /** Links from other apps: only http(s) is accepted, and only opened once the app is unlocked. */
    private fun captureLink(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) i.data?.toString()?.takeIf { UrlUtils.isWebUrl(it) }?.let { pendingUrl = it }
    }

    private fun consumePending() { pendingUrl?.let { pendingUrl = null; openUrl(it) } }

    // ---------------------------------------------------------------- startup
    private fun showSplash() {
        val v = SplashView(this, app.startupNote)
        root.addView(v)
        val steps = listOf("Engine ready" to 0.4f, "Storage swept" to 0.75f, "Protection loaded" to 1f)
        steps.forEachIndexed { i, s -> main.postDelayed({ v.setProgress(s.second, s.first) }, 250L * (i + 1)) }
        main.postDelayed({
            root.removeView(v)
            goHome()
            if (app.lock.needsUnlock()) showLock()
            else if (!app.prefs.firstRunDone) { app.prefs.firstRunDone = true; push(OnboardingScreen()) }
            else consumePending()
        }, 1100)
    }

    // ---------------------------------------------------------------- lifecycle
    override fun onStart() {
        super.onStart()
        if (externalLaunch) { externalLaunch = false; app.lock.clearBackground() }   // returning from a picker/share sheet we opened
        if (!::content.isInitialized || stack.isEmpty()) return
        val cfg = app.prefs.config()
        if (cfg.clearAfterBackgroundMinutes > 0 && bgAt > 0 &&
            System.currentTimeMillis() - bgAt >= cfg.clearAfterBackgroundMinutes * 60_000L) {
            app.eraser.erase(EraseMode.QUICK.scopes) { }
            goHome()
        }
        app.sessions.check()
        if (app.lock.onForeground()) showLock()
    }

    override fun onStop() {
        super.onStop()
        bgAt = System.currentTimeMillis(); app.lock.onBackground(); Stats.flush(app.prefs)
        if (app.prefs.vault_autolock()) app.vault.lock()
    }

    override fun onPause() {
        super.onPause()
        if (app.lock.isConfigured() || app.prefs.config().noScreenshots) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        stopShake()
    }

    override fun onResume() {
        super.onResume()
        applyScreenPolicy()
        if (app.prefs.panicShake) startShake()
    }

    fun applyScreenPolicy() {
        val sec = app.prefs.config().noScreenshots
        if (sec) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!(sec || app.lock.isConfigured()))
    }

    // ---------------------------------------------------------------- navigation
    fun push(s: Screen) {
        stack.lastOrNull()?.onHide()
        s.a = this; stack.add(s); show(s)
    }

    fun pop() {
        if (stack.size <= 1) return
        stack.removeAt(stack.size - 1).onHide()
        show(stack.last())
    }

    fun goHome() {
        stack.forEach { it.onHide() }; stack.clear()
        push(HomeScreen())
    }

    fun top(): Screen? = stack.lastOrNull()

    private fun show(s: Screen) {
        content.removeAllViews(); content.addView(s.build(), FrameLayout.LayoutParams(-1, -1)); s.onShow()
    }

    fun refresh() { stack.lastOrNull()?.let { show(it) } }

    override fun onBackPressed() {
        if (customView != null) { hideCustomView(); return }
        if (lockView != null) { moveTaskToBack(true); return }
        val t = stack.lastOrNull()
        if (t != null && t.onBack()) return
        if (stack.size > 1) pop() else moveTaskToBack(true)
    }

    // ---------------------------------------------------------------- browsing
    fun openUrl(input: String, newTab: Boolean = true) {
        val act = app.tabs.active
        if (newTab || act == null || act.space.id != space.id) app.tabs.open(space, input)
        else app.tabs.navigate(act, input)
        if (stack.lastOrNull() !is BrowserScreen) push(BrowserScreen()) else (stack.last() as BrowserScreen).onShow()
    }

    fun showTab(t: com.zaeem.browser.engine.Tab) {
        app.tabs.select(t); space = t.space
        if (stack.lastOrNull() is TabsScreen) pop()
        if (stack.lastOrNull() is BrowserScreen) (stack.last() as BrowserScreen).onShow() else push(BrowserScreen())
    }

    fun showBrowser() { if (stack.lastOrNull() !is BrowserScreen) push(BrowserScreen()) }

    fun enterSpace(s: Prefs.Space) {
        val go = { space = s; app.tabs.inSpace(s.id).lastOrNull()?.let { app.tabs.select(it) }; toast("Space: ${s.name}"); goHome() }
        if (s.locked) requireAuth("Open ${s.name}") { go() } else go()
    }

    // ---------------------------------------------------------------- lock
    fun showLock() {
        if (lockView != null) return
        app.tabs.tabs.forEach { it.webView?.onPause() }
        val v = LockView(this) { hideLock() }
        lockView = v; root.addView(v, FrameLayout.LayoutParams(-1, -1)); content.visibility = View.INVISIBLE
        v.start()
    }

    private fun hideLock() {
        lockView?.let { root.removeView(it) }; lockView = null
        content.visibility = View.VISIBLE
        stack.lastOrNull()?.let { if (it is BrowserScreen) it.onShow() }
        consumePending()
    }

    fun lockNow() { app.lock.lockNow(); if (app.lock.isConfigured()) showLock() else toast("App Lock is not set up") }

    fun requireAuth(reason: String, ok: () -> Unit) {
        if (!app.lock.isConfigured()) {
            confirm("App Lock required", "Set a PIN or password first to protect: $reason.", "Set up") { push(AppLockScreen()) }; return
        }
        val pin = { promptSecret(reason, ok) }
        if (app.lock.biometricAvailable(this)) app.lock.biometricPrompt(this, reason, ok) { pin() } else pin()
    }

    private fun promptSecret(reason: String, ok: () -> Unit) {
        val isPin = app.prefs.lockType == "pin"
        input(if (isPin) "Enter PIN" else "Enter password", reason, "", true, isPin) { s ->
            when (val r = app.lock.verify(s.toCharArray())) {
                is AppLock.Result.Ok -> ok()
                is AppLock.Result.Wrong -> toast("Wrong ${if (isPin) "PIN" else "password"}")
                is AppLock.Result.LockedOut -> toast("Too many attempts. Try again in ${r.remainingMs / 1000}s")
            }
        }
    }

    fun panic() { root.setBackgroundColor(0xFF000000.toInt()); content.visibility = View.INVISIBLE; app.eraser.panic(this) }

    // ---------------------------------------------------------------- dialogs
    private fun builder() = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)

    fun confirm(title: String, msg: String, yes: String = "OK", no: String = "Cancel", onYes: () -> Unit) {
        builder().setTitle(title).setMessage(msg).setPositiveButton(yes) { _, _ -> onYes() }.setNegativeButton(no, null).show()
    }

    fun info(title: String, msg: String) { builder().setTitle(title).setMessage(msg).setPositiveButton("OK", null).show() }

    fun input(title: String, hint: String, initial: String = "", password: Boolean = false, numeric: Boolean = false, cb: (String) -> Unit) {
        val e = EditText(this).apply {
            setText(initial); setHint(hint); setTextColor(Ui.TEXT); setHintTextColor(Ui.MUTED); setSingleLine()
            inputType = when { password && numeric -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
            if (password) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        val box = FrameLayout(this).apply { setPadding(Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 20), 0); addView(e) }
        builder().setTitle(title).setView(box).setPositiveButton("OK") { _, _ -> cb(e.text.toString()) }.setNegativeButton("Cancel", null).show()
    }

    fun choose(title: String, items: List<String>, cb: (Int) -> Unit) {
        builder().setTitle(title).setItems(items.toTypedArray()) { _, i -> cb(i) }.show()
    }

    override fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    // ---------------------------------------------------------------- BrowserHost
    override fun onTabsChanged() {
        runOnUiThread {
            val t = stack.lastOrNull()
            if (t is BrowserScreen) { val act = app.tabs.active; if (act != null && act.webView == null) t.onShow() else t.refreshChrome() }
            else if (t is TabsScreen) refresh()
        }
    }

    override fun askPermission(origin: String, kind: PermKind, cb: (Decision) -> Unit) {
        val h = UrlUtils.host(origin) ?: origin
        builder().setTitle("${kind.label} request").setMessage("$h wants to use your ${kind.label.lowercase()}.")
            .setItems(arrayOf("Allow once", "Allow during this session", "Always allow", "Block")) { _, i ->
                cb(listOf(Decision.ALLOW_ONCE, Decision.ALLOW_SESSION, Decision.ALWAYS_ALLOW, Decision.BLOCK)[i])
            }.setOnCancelListener { cb(Decision.BLOCK) }.show()
    }

    override fun ensureRuntimePermissions(perms: List<String>, cb: (Boolean) -> Unit) {
        val missing = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) { cb(true); return }
        rtCb = cb; rtPerms = perms
        requestPermissions(missing.toTypedArray(), REQ_PERM)
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        if (code == REQ_PERM) { val ok = rtPerms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }; rtCb?.invoke(ok); rtCb = null }
    }

    private var voiceCb: ((String) -> Unit)? = null

    /** Voice search uses the phone's own speech recogniser (usually Google's), so the user is told before it opens. */
    fun voiceSearch(cb: (String) -> Unit) {
        confirm("Voice search", "Speech is processed by your phone's speech-recognition app, which is usually Google's and may send audio to its servers. ZAEEM BROWSER never receives or stores the audio.", "Continue") {
            val i = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say what to search")
            }
            voiceCb = cb
            try { externalLaunch = true; startActivityForResult(i, REQ_VOICE) } catch (e: Exception) { voiceCb = null; toast("No speech recognition app on this phone") }
        }
    }

    /** Image search = the search engine's image results for the typed words (nothing is uploaded). */
    fun imageSearch(query: String) {
        if (query.isBlank()) { toast("Type what to look for first"); return }
        openUrl("https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(query.trim(), "UTF-8") + "&iax=images&ia=images")
    }

    override fun chooseFile(intent: Intent, cb: ValueCallback<Array<Uri>>) {
        fileCb?.onReceiveValue(null); fileCb = cb
        try { externalLaunch = true; startActivityForResult(intent, REQ_FILE) } catch (e: Exception) { fileCb = null; cb.onReceiveValue(null) }
    }

    @Deprecated("platform API")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ_VOICE) {
            val text = if (res == RESULT_OK) data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() else null
            val cb = voiceCb; voiceCb = null
            if (!text.isNullOrBlank()) cb?.invoke(text)
        }
        if (req == REQ_FILE) { fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data)); fileCb = null }
    }

    override fun showCustomView(v: View, cb: WebChromeClient.CustomViewCallback) {
        if (customView != null) { cb.onCustomViewHidden(); return }
        customView = v; customCb = cb; root.addView(v, FrameLayout.LayoutParams(-1, -1)); content.visibility = View.INVISIBLE
    }

    override fun hideCustomView() {
        customView?.let { root.removeView(it) }; customView = null; customCb?.onCustomViewHidden(); customCb = null
        content.visibility = if (lockView == null) View.VISIBLE else View.INVISIBLE
    }

    override fun confirmDownload(url: String, ua: String?, cd: String?, mime: String?, length: Long) {
        val h = UrlUtils.host(url) ?: url
        if (!url.startsWith("https://")) { info("Insecure download blocked", "$h does not use HTTPS. ZAEEM BROWSER only downloads over secure connections."); return }
        val name = app.downloader.sanitize(android.webkit.URLUtil.guessFileName(url, cd, mime))
        val risky = DownloadItem.RISKY.contains(name.substringAfterLast('.', "").lowercase())
        val size = if (length > 0) " (${android.text.format.Formatter.formatShortFileSize(this, length)})" else ""
        confirm(if (risky) "Risky file type" else "Download file?",
            "$name$size\nfrom $h" + if (risky) "\n\nThis type of file can run code on your device. Only continue if you fully trust the source." else "",
            "Download") {
            val cookie = app.tabs.active?.let { Engine.cookieManager(Engine.profileName(it.space.id, it.space.ephemeral)).getCookie(url) }
            if (app.downloader.start(url, ua, cookie, cd, mime) != null) toast("Downloading $name") else toast("Download refused")
        }
    }

    override fun showLinkPreview(url: String, tab: Tab) {
        val host = UrlUtils.host(url) ?: "(no host)"
        val scheme = UrlUtils.scheme(url) ?: "?"
        val warn = buildString {
            if (scheme == "http") append("\n⚠ Not encrypted (HTTP).")
            if (UrlUtils.isPunycode(host)) append("\n⚠ Host uses punycode (xn--): could imitate another site.")
            if (scheme != "http" && scheme != "https") append("\n⚠ Not a web link; it will not be opened.")
        }
        builder().setTitle(host).setMessage("$url$warn")
            .setPositiveButton("Open") { _, _ -> if (UrlUtils.isWebUrl(url)) app.tabs.navigate(tab, url) else toast("Blocked") }
            .setNegativeButton("New tab") { _, _ -> if (UrlUtils.isWebUrl(url)) openUrl(url) else toast("Blocked") }
            .setNeutralButton("Copy") { _, _ -> copy(url) }.show()
    }

    /** Launch another app/screen without the auto-lock timer treating the round trip as 'minimised'. */
    fun external(i: Intent) { externalLaunch = true; startActivity(i) }

    fun copy(text: String) { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("url", text)); toast("Copied") }

    override fun showTlsBlocked(host: String, reason: String) { toast("Blocked $host: $reason") }

    override fun askHttpFallback(host: String, onContinue: () -> Unit) {
        confirm("No secure connection", "$host does not support HTTPS. Continue over unencrypted HTTP? Anyone on the network could read or change what you see.", "Continue (HTTP)", "Stay safe") { onContinue() }
    }

    // ---------------------------------------------------------------- clipboard guard
    private fun watchClipboard() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipListener = ClipboardManager.OnPrimaryClipChangedListener {
            if (!app.prefs.config().clipboardGuard) return@OnPrimaryClipChangedListener
            val t = try { cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() } catch (e: Exception) { null } ?: return@OnPrimaryClipChangedListener
            val kind = SensitiveDetector.classify(t) ?: return@OnPrimaryClipChangedListener
            runOnUiThread {
                builder().setTitle("Clipboard Guard").setMessage("Sensitive content detected: this looks like ${kind.label}. Allow copy?\n\nIf allowed, it is cleared from the clipboard after 60 seconds.")
                    .setPositiveButton("Allow") { _, _ -> main.postDelayed({ clearClip() }, 60_000) }
                    .setNegativeButton("Block") { _, _ -> clearClip() }.setCancelable(false).show()
            }
        }
        cm.addPrimaryClipChangedListener(clipListener)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW || level >= TRIM_MEMORY_UI_HIDDEN) app.tabs.discardBackground()
    }

    override fun onDestroy() {
        clipListener?.let { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(it) }
        super.onDestroy()
    }

    private fun clearClip() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
    }

    // ---------------------------------------------------------------- shake to panic
    private fun startShake() {
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }
    private fun stopShake() { (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(this) }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()
        if (g > 3.2f) { if (now - lastShake in 150..900) { lastShake = 0; panic() } else lastShake = now }
    }
}
