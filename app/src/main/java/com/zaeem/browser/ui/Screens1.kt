package com.zaeem.browser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.zaeem.browser.MainActivity
import com.zaeem.browser.Screen
import com.zaeem.browser.core.*
import com.zaeem.browser.security.AppLock
import com.zaeem.browser.ui.Ui.add

// ============================================================ 1. Splash
class SplashView(c: Context, note: String) : LinearLayout(c) {
    private val status = Ui.text(c, note, 12f, Ui.MUTED).apply { gravity = Gravity.CENTER }
    private val bar = ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100; progress = 5; progressTintList = android.content.res.ColorStateList.valueOf(Ui.EMERALD)
    }
    init {
        orientation = VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Ui.BG); setPadding(Ui.dp(c, 48), 0, Ui.dp(c, 48), 0)
        addView(GlobeView(c), LayoutParams(Ui.dp(c, 140), Ui.dp(c, 140)))
        addView(Ui.text(c, "ZAEEM", 32f, Ui.TEXT, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.12f }, Ui.lp(c, 12))
        addView(Ui.text(c, "B R O W S E R", 15f, Ui.EMERALD, true).apply { gravity = Gravity.CENTER })
        addView(Ui.text(c, "By Zaeem Technologies", 13f, Ui.MUTED).apply { gravity = Gravity.CENTER }, Ui.lp(c, 20))
        addView(Ui.text(c, "Browsing. On your terms.", 14f, Ui.MINT).apply { gravity = Gravity.CENTER }, Ui.lp(c, 8))
        addView(bar, Ui.lp(c, 28)); addView(status, Ui.lp(c, 8))
    }
    fun setProgress(f: Float, label: String) { bar.progress = (f * 100).toInt(); status.text = label }
}

// ============================================================ 2. PIN / password / biometric lock
@SuppressLint("ViewConstructor")
class LockView(private val a: MainActivity, private val onUnlocked: () -> Unit) : FrameLayout(a) {
    private val lock get() = a.app.lock
    private val isPin get() = a.app.prefs.lockType == "pin"
    private val entered = StringBuilder()
    private val dots = Ui.text(a, "", 26f, Ui.EMERALD, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.3f }
    private val msg = Ui.text(a, "", 13f, Ui.WARN).apply { gravity = Gravity.CENTER }
    private val h = Handler(Looper.getMainLooper())
    private val pw = EditText(a).apply {
        setHint("Password"); setTextColor(Ui.TEXT); setHintTextColor(Ui.MUTED); setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        background = Ui.round(a, Ui.SURFACE, 14, Ui.STROKE); setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12))
    }
    private val tick = object : Runnable { override fun run() { updateLockout(); if (lock.lockoutRemaining() > 0) h.postDelayed(this, 500) } }

    init {
        setBackgroundColor(Ui.BG); isClickable = true
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(Ui.dp(a, 32), Ui.dp(a, 48), Ui.dp(a, 32), Ui.dp(a, 24)) }
        col.addView(ShieldView(a, 0), LinearLayout.LayoutParams(Ui.dp(a, 76), Ui.dp(a, 84)))
        col.addView(Ui.text(a, if (isPin) "Enter PIN" else "Enter password", 20f, Ui.TEXT, true), Ui.lp(a, 16))
        if (isPin) {
            col.addView(dots, Ui.lp(a, 12))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✓"))
            val grid = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            for (r in rows) {
                val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
                for (k in r) row.addView(key(k), LinearLayout.LayoutParams(Ui.dp(a, 74), Ui.dp(a, 74)).apply { setMargins(Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 6)) })
                grid.addView(row)
            }
            col.addView(grid, Ui.lp(a, 12))
        } else {
            col.addView(pw, Ui.lp(a, 16))
            col.addView(Ui.button(a, "Unlock") { submit(pw.text.toString()); pw.setText("") }, Ui.lp(a, 12))
        }
        col.addView(msg, Ui.lp(a, 10))
        col.addView(Ui.text(a, "Use Biometric", 14f, Ui.MUTED).apply {
            gravity = Gravity.CENTER; setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 12)); isClickable = true
            setOnClickListener { biometric() }
            visibility = if (lock.biometricAvailable(a)) View.VISIBLE else View.GONE
        }, Ui.lp(a, 6))
        addView(ScrollView(a).apply { addView(col); isFillViewport = true })
        updateLockout()
    }

    private fun key(k: String) = TextView(a).apply {
        text = k; textSize = 24f; gravity = Gravity.CENTER; setTextColor(if (k == "✓") Ui.EMERALD else Ui.TEXT); isClickable = true
        background = Ui.ripple(a, Ui.round(a, Ui.SURFACE, 40, Ui.STROKE))
        setOnClickListener {
            when (k) {
                "⌫" -> if (entered.isNotEmpty()) entered.deleteCharAt(entered.length - 1)
                "✓" -> { submit(entered.toString()); entered.clear() }
                else -> if (entered.length < 12) entered.append(k)
            }
            dots.text = "●".repeat(entered.length)
        }
    }

    private fun submit(s: String) {
        when (val r = lock.verify(s.toCharArray())) {
            is AppLock.Result.Ok -> { h.removeCallbacks(tick); onUnlocked() }
            is AppLock.Result.Wrong -> { msg.text = "Wrong ${if (isPin) "PIN" else "password"}"; dots.text = "" }
            is AppLock.Result.LockedOut -> { updateLockout(); h.post(tick) }
        }
        entered.clear(); dots.text = ""
    }

    private fun updateLockout() {
        val rem = lock.lockoutRemaining()
        if (rem > 0) msg.text = "Too many attempts. Try again in ${(rem + 999) / 1000}s"
        else if (msg.text.startsWith("Too many")) msg.text = ""
    }

    private fun biometric() { lock.biometricPrompt(a, "Unlock ZAEEM Browser", { onUnlocked() }) { } }

    fun start() { if (lock.biometricAvailable(a)) biometric(); if (lock.lockoutRemaining() > 0) h.post(tick) }
}

// ============================================================ First run
class OnboardingScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "Welcome to ZAEEM Browser", null) {
        add(Ui.text(a, "Privacy by default. Choose how strict you want to start. You can change every setting later.", 14f, Ui.MUTED))
        for ((p, d) in listOf(
            PrivacyPreset.BALANCED to "HTTPS-first, tracker and third-party-cookie blocking, standard fingerprint protection. Best compatibility.",
            PrivacyPreset.STRICT to "Adds WebRTC blocking, strict fingerprint protection, screenshot blocking, camera/mic/location blocked by default, clears data after 15 minutes in the background.",
            PrivacyPreset.MAXIMUM to "Strict plus Safe Browsing lookups off and data cleared after 1 minute in the background. Some sites will break.")) {
            add(Ui.nav(a, p.name.lowercase().replaceFirstChar { it.uppercase() }, d) { a.app.prefs.saveConfig(PrivacyConfig.forPreset(p)); a.applyScreenPolicy(); a.pop() }, 10)
        }
        add(Ui.text(a, "No history is kept in any preset. Your choice only changes how aggressive the protections are.", 12f, Ui.MUTED), 14)
    }
    override fun onBack() = true
}

// ============================================================ 3. Home
class HomeScreen : Screen() {
    override fun build(): View {
        val c: Context = a
        val cfg = a.app.prefs.config()
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        val scroll = ScrollView(c)
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 16)) }
        val top = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(Ui.text(c, "☰", 24f, Ui.TEXT).apply { setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 12), 0); isClickable = true; setOnClickListener { a.push(SettingsScreen()) } })
        val brand = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(Ui.text(c, "ZAEEM Browser", 16f, Ui.TEXT, true))
        brand.addView(Ui.text(c, "By Zaeem Technologies", 11f, Ui.MUTED))
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(ShieldView(c, 1).apply { setOnClickListener { a.push(DashboardScreen()) } }, LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 34)))
        col.addView(top)

        val search = EditText(c).apply {
            hint = "Search privately or enter address"; setHintTextColor(Ui.MUTED); setTextColor(Ui.TEXT); setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; imeOptions = EditorInfo.IME_ACTION_GO
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            background = null; setPadding(Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 14))
            setOnEditorActionListener { v, id, ev ->
                if (id == EditorInfo.IME_ACTION_GO || (ev?.keyCode == KeyEvent.KEYCODE_ENTER)) { val t = v.text.toString(); if (t.isNotBlank()) a.openUrl(t); true } else false
            }
        }
        fun icon(glyph: String, desc: String, go: () -> Unit) = Ui.text(c, glyph, 20f, Ui.EMERALD).apply {
            setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8)); isClickable = true; contentDescription = desc; setOnClickListener { go() }
        }
        val pill = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = Ui.round(c, Ui.SURFACE, 28, Ui.STROKE); setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0)
        }
        pill.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
        pill.addView(SearchIcon(c).apply { setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8)); isClickable = true; contentDescription = "Search"; setOnClickListener { val t = search.text.toString(); if (t.isNotBlank()) a.openUrl(t) } }, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)))
        if (false) pill.addView(icon("🔍", "Search") { val t = search.text.toString(); if (t.isNotBlank()) a.openUrl(t) })
        col.addView(pill, Ui.lp(c, 16))

        val ok = protectionOk(cfg)
        val hero = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        hero.addView(GlobeView(c), LinearLayout.LayoutParams(Ui.dp(c, 150), Ui.dp(c, 150)))
        hero.addView(Ui.text(c, "Browsing. On your terms.", 16f, Ui.TEXT, true), Ui.lp(c, 2))
        hero.addView(Ui.text(c, "Privacy. Precision. Independence.", 12f, Ui.MUTED), Ui.lp(c, 4))
        hero.addView(Ui.text(c, (if (ok) "Protection on" else "Reduced protection") + " · " + (a.app.prefs.matchingPreset()?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Custom") + " preset · " + a.space.name, 12f, if (ok) Ui.MINT else Ui.WARN), Ui.lp(c, 10))
        col.addView(hero, Ui.lp(c, 18))

        fun tile(title: String, glyph: String, go: () -> Unit) = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isClickable = true
            background = Ui.ripple(c, Ui.round(c, Ui.SURFACE, 16, Ui.STROKE)); setPadding(Ui.dp(c, 8), Ui.dp(c, 18), Ui.dp(c, 8), Ui.dp(c, 18))
            addView(Ui.text(c, glyph, 26f, Ui.EMERALD)); addView(Ui.text(c, title, 13f, Ui.TEXT, true).apply { gravity = Gravity.CENTER }, Ui.lp(c, 6))
            setOnClickListener { go() }
        }
        fun row(l: View, r: View) = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(l, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = Ui.dp(c, 6) })
            addView(r, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = Ui.dp(c, 6) })
        }
        col.addView(row(tile("Privacy\nDashboard", "✓") { a.push(DashboardScreen()) }, tile("One-Tap\nErase", "✕") { a.push(EraseScreen()) }), Ui.lp(c, 14))
        col.addView(row(tile("Lock\nBrowser", "◉") { a.lockNow() }, tile("Private\nSpaces", "◫") { a.push(SpacesScreen()) }), Ui.lp(c, 12))
        col.addView(row(tile("Timed\nSession", "⏱") { a.push(SessionScreen()) }, tile("Panic\nButton", "⚠") { a.push(PanicScreen()) }), Ui.lp(c, 12))
        col.addView(row(tile("Private\nVault", "✦") { a.push(VaultScreen()) }, tile("Tracker\nRadar", "◎") { a.push(TrackerRadarScreen()) }), Ui.lp(c, 12))
        scroll.addView(col); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomNav(a, 0))
        return root
    }

    private fun protectionOk(c: PrivacyConfig) = c.httpsFirst && c.blockTrackers && c.blockThirdPartyCookies

    override fun onBack() = false
}

fun bottomNav(a: MainActivity, selected: Int): View {
    val c: Context = a
    val bar = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Ui.SURFACE); setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6)) }
    val tabs = a.app.tabs.tabs.size
    val items = listOf("⌂" to "Home", "▣" to "Tabs${if (tabs > 0) " ($tabs)" else ""}", "↓" to "Downloads", "≡" to "Menu")
    items.forEachIndexed { i, (g, l) ->
        bar.addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isClickable = true
            addView(Ui.text(c, g, 20f, if (i == selected) Ui.EMERALD else Ui.MUTED))
            addView(Ui.text(c, l, 11f, if (i == selected) Ui.EMERALD else Ui.MUTED))
            setOnClickListener { when (i) { 0 -> a.goHome(); 1 -> a.push(TabsScreen()); 2 -> a.push(DownloadsScreen()); else -> a.push(SettingsScreen()) } }
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }
    return bar
}

// ============================================================ 16. Search & navigation (browser)
class BrowserScreen : Screen() {
    private lateinit var url: EditText
    private lateinit var progress: ProgressBar
    private lateinit var holder: FrameLayout
    private lateinit var secure: TextView
    private lateinit var tabsBtn: TextView
    private lateinit var back: TextView
    private lateinit var fwd: TextView

    override fun build(): View {
        val c: Context = a
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        val bar = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6)) }
        secure = Ui.text(c, "⌂", 18f, Ui.EMERALD).apply { setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0); isClickable = true; setOnClickListener { a.push(InspectorScreen()) } }
        url = EditText(c).apply {
            hint = "Search or enter address"; setHintTextColor(Ui.MUTED); setTextColor(Ui.TEXT); setSingleLine(); textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; imeOptions = EditorInfo.IME_ACTION_GO
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO; setSelectAllOnFocus(true)
            background = Ui.round(c, Ui.SURFACE, 20, Ui.STROKE); setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8))
            setOnEditorActionListener { v, id, _ ->
                if (id == EditorInfo.IME_ACTION_GO) { val t = v.text.toString(); if (t.isNotBlank()) a.openUrl(t, newTab = false); v.clearFocus(); true } else false
            }
            setOnFocusChangeListener { v, f -> if (f) (v as EditText).setText(a.app.tabs.active?.url?.takeIf { it != "about:blank" } ?: "") else refreshChrome() }
        }
        val reload = Ui.text(c, "⟳", 20f, Ui.MINT).apply {
            setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 6), 0); isClickable = true
            setOnClickListener { a.app.tabs.active?.webView?.let { if ((a.app.tabs.active?.progress ?: 100) < 100) it.stopLoading() else it.reload() } }
        }
        bar.addView(secure); bar.addView(url, LinearLayout.LayoutParams(0, -2, 1f)); bar.addView(reload)
        root.addView(bar)
        progress = ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = android.content.res.ColorStateList.valueOf(Ui.EMERALD)
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, Ui.dp(c, 3)))
        holder = FrameLayout(c); root.addView(holder, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Ui.SURFACE); setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8)) }
        fun b(label: String, go: () -> Unit) = Ui.text(c, label, 22f, Ui.MINT).apply { gravity = Gravity.CENTER; isClickable = true; setOnClickListener { go() } }
        back = b("‹") { a.app.tabs.active?.webView?.let { if (it.canGoBack()) it.goBack() else a.pop() } ?: a.pop() }
        fwd = b("›") { a.app.tabs.active?.webView?.let { if (it.canGoForward()) it.goForward() } }
        tabsBtn = b("▣") { a.push(TabsScreen()) }
        nav.addView(back, LinearLayout.LayoutParams(0, -2, 1f)); nav.addView(fwd, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(b("⌂") { a.goHome() }, LinearLayout.LayoutParams(0, -2, 1f)); nav.addView(tabsBtn, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(b("⋮") { menu() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(nav)
        return root
    }

    override fun onShow() {
        val tm = a.app.tabs
        val t = tm.active ?: tm.open(a.space, null)
        val wv = tm.view(t)
        (wv.parent as? android.view.ViewGroup)?.removeView(wv)
        holder.removeAllViews(); holder.addView(wv); wv.onResume()
        refreshChrome()
        if (t.url == "about:blank") { url.requestFocus() }
    }

    override fun onHide() { a.app.tabs.active?.webView?.onPause(); (a.app.tabs.active?.webView?.parent as? android.view.ViewGroup)?.removeAllViews() }

    override fun onBack(): Boolean {
        val wv = a.app.tabs.active?.webView
        if (wv != null && wv.canGoBack()) { wv.goBack(); return true }
        return false
    }

    fun refreshChrome() {
        if (!::url.isInitialized) return
        val t = a.app.tabs.active ?: return
        if (!url.hasFocus()) url.setText(if (t.url == "about:blank") "" else (UrlUtils.host(t.url) ?: t.url))
        progress.progress = if (t.progress >= 100) 0 else t.progress
        secure.text = when { t.url.startsWith("https://") -> "🔒"; t.url.startsWith("http://") -> "⚠"; else -> "⌂" }
        secure.setTextColor(if (t.url.startsWith("http://")) Ui.WARN else Ui.EMERALD)
        tabsBtn.text = "▣${a.app.tabs.tabs.size}"
        val wv = t.webView
        back.alpha = 1f; fwd.alpha = if (wv?.canGoForward() == true) 1f else 0.35f
    }

    private fun menu() {
        val items = listOf("New tab", "Connection security", "Add to Private Vault", "Share link", "Tracker Radar", "Privacy Dashboard",
            "Downloads", "Settings", "Lock now", "Close this tab", "PANIC")
        a.choose("ZAEEM Browser", items) { i ->
            val t = a.app.tabs.active
            when (i) {
                0 -> { a.app.tabs.open(a.space, null); onShow() }
                1 -> a.push(InspectorScreen())
                2 -> if (t != null && UrlUtils.isWebUrl(t.url)) a.requireAuth("Private Vault") { addToVault(t) } else a.toast("Nothing to bookmark")
                3 -> if (t != null && UrlUtils.isWebUrl(t.url)) a.external(android.content.Intent.createChooser(
                    android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, t.url), "Share link"))
                4 -> a.push(TrackerRadarScreen())
                5 -> a.push(DashboardScreen())
                6 -> a.push(DownloadsScreen())
                7 -> a.push(SettingsScreen())
                8 -> a.lockNow()
                9 -> if (t != null) { a.app.tabs.close(t); if (a.app.tabs.tabs.isEmpty()) a.goHome() else onShow() }
                10 -> a.panic()
            }
        }
    }

    private fun addToVault(t: com.zaeem.browser.engine.Tab) {
        val v = a.app.vault
        if (!v.unlocked && !v.unlock()) { a.toast("Vault could not be unlocked"); return }
        v.add(com.zaeem.browser.data.Bookmark("General", t.title.ifBlank { t.host }, t.url)); a.toast("Saved to Private Vault")
    }
}

// ============================================================ 17. Tab management
class TabsScreen : Screen() {
    override fun build(): View {
        val tm = a.app.tabs
        return Ui.scaffold(a, "Tabs (${tm.tabs.size})", { a.pop() }, "New tab" to { a.openUrl("about:blank") }) {
            if (tm.tabs.isEmpty()) add(Ui.text(a, "No open tabs. Tabs are never restored after you close the app.", 14f, Ui.MUTED), 20)
            for (t in tm.tabs.toList()) {
                val row = LinearLayout(a).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isClickable = true
                    background = Ui.ripple(a, Ui.round(a, if (t === tm.active) Ui.SURFACE2 else Ui.SURFACE, 14, if (t === tm.active) Ui.EMERALD else Ui.STROKE))
                    setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12))
                    setOnClickListener { a.showTab(t) }
                }
                val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
                col.addView(Ui.text(a, t.title, 15f, Ui.TEXT, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                col.addView(Ui.text(a, "${t.host} · ${t.space.name}${if (t.webView == null && t.url != "about:blank") " · sleeping" else ""}", 12f, Ui.MUTED))
                row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(Ui.text(a, "✕", 20f, Ui.MUTED).apply { setPadding(Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 14), Ui.dp(a, 6)); isClickable = true; setOnClickListener { tm.close(t); a.refresh() } })
                add(row, 8)
            }
            if (tm.tabs.isNotEmpty()) add(Ui.button(a, "Close all tabs", 1) { tm.closeAll(); a.refresh() }, 16)
        }
    }
}


/** Minimal 24dp-style search glyph (circle + handle), drawn in the accent colour. */
class SearchIcon(c: Context) : View(c) {
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND }
    override fun onDraw(cv: android.graphics.Canvas) {
        val w = (width - paddingLeft - paddingRight).toFloat(); val h = (height - paddingTop - paddingBottom).toFloat(); val s = minOf(w, h)
        val ox = paddingLeft + (w - s) / 2f; val oy = paddingTop + (h - s) / 2f
        p.color = Ui.TEXT; p.strokeWidth = s * .09f
        cv.drawCircle(ox + s * .42f, oy + s * .42f, s * .30f, p)
        cv.drawLine(ox + s * .64f, oy + s * .64f, ox + s * .90f, oy + s * .90f, p)
    }
}
