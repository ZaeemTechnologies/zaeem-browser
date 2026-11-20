package com.zaeem.browser.ui

import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.zaeem.browser.Screen
import com.zaeem.browser.core.*
import com.zaeem.browser.data.Prefs
import com.zaeem.browser.data.Stats
import com.zaeem.browser.engine.Engine
import com.zaeem.browser.engine.Restart
import com.zaeem.browser.ui.Ui.add

// ============================================================ 4. Privacy dashboard
class DashboardScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "Privacy Dashboard", { a.pop() }) {
        val cfg = a.app.prefs.config()
        val warnings = warnings(cfg)
        val ok = warnings.isEmpty()
        val hero = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        hero.addView(ShieldView(a, if (ok) 1 else 2, if (ok) Ui.EMERALD else Ui.WARN), LinearLayout.LayoutParams(Ui.dp(a, 90), Ui.dp(a, 100)))
        hero.addView(Ui.text(a, if (ok) "Protected" else "Attention needed", 22f, if (ok) Ui.MINT else Ui.WARN, true), Ui.lp(a, 8))
        hero.addView(Ui.text(a, if (ok) "Core protections are on" else "${warnings.size} item(s) reduce your protection", 13f, Ui.MUTED))
        add(hero, 6)

        add(Ui.section(a, "This session (real counters)"), 4)
        val c = Ui.card(a)
        c.addView(Ui.kv(a, "Trackers blocked", Stats.trackersBlocked.get().toString()))
        c.addView(Ui.kv(a, "Trackers blocked (lifetime)", (a.app.prefs.lifetimeBlocked + Stats.trackersBlocked.get()).toString()))
        c.addView(Ui.kv(a, "Connections upgraded to HTTPS", Stats.httpsUpgrades.get().toString()))
        c.addView(Ui.kv(a, "Insecure (HTTP) pages opened", Stats.insecureNavigations.get().toString(), if (Stats.insecureNavigations.get() > 0) Ui.WARN else Ui.TEXT))
        c.addView(Ui.kv(a, "Certificate errors blocked", Stats.sslErrorsBlocked.get().toString()))
        c.addView(Ui.kv(a, "App-launch redirects blocked", Stats.externalLaunchesBlocked.get().toString()))
        c.addView(Ui.kv(a, "Secure page loads", Stats.securePercent()?.let { "$it%" } ?: "No pages loaded yet"))
        add(c)
        add(Ui.text(a, "Third-party cookie blocks are done inside the engine, which does not report a count, so none is shown.", 11f, Ui.MUTED), 6)

        add(Ui.section(a, "Active security settings"), 4)
        val s = Ui.card(a)
        fun yn(k: String, b: Boolean) = s.addView(Ui.kv(a, k, if (b) "On" else "Off", if (b) Ui.EMERALD else Ui.WARN))
        yn("HTTPS-first", cfg.httpsFirst); yn("Tracker blocking", cfg.blockTrackers); yn("Third-party cookies blocked", cfg.blockThirdPartyCookies)
        s.addView(Ui.kv(a, "Fingerprint protection", cfg.fingerprint.name.lowercase().replaceFirstChar { it.uppercase() }))
        yn("WebRTC blocked", cfg.blockWebRtc); yn("Safe Browsing", cfg.safeBrowsing); yn("Clear data on exit", cfg.clearOnExit)
        yn("Screenshot protection", cfg.noScreenshots); yn("App Lock", a.app.lock.isConfigured()); yn("Clipboard Guard", cfg.clipboardGuard)
        s.addView(Ui.kv(a, "Permission decisions saved", a.app.perms.entries().size.toString()))
        add(s)

        if (warnings.isNotEmpty()) {
            add(Ui.section(a, "Security warnings"), 4)
            val w = Ui.card(a); warnings.forEach { w.addView(Ui.text(a, "⚠  $it", 13f, Ui.WARN).apply { setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4)) }) }
            add(w)
        }
        add(Ui.button(a, "View Tracker Radar") { a.push(TrackerRadarScreen()) }, 18)
        add(Ui.button(a, "Run privacy audit", 1) { a.push(AuditScreen()) }, 10)
    }

    private fun warnings(c: PrivacyConfig): List<String> = buildList {
        if (!Engine.multiProfile) add("This WebView is too old for isolated spaces. Update Android System WebView.")
        if (!c.httpsFirst) add("HTTPS-first is off.")
        if (!c.blockTrackers) add("Tracker blocking is off.")
        if (!c.blockThirdPartyCookies) add("Third-party cookies are allowed.")
        if (!a.app.lock.isConfigured()) add("No App Lock is set.")
        if (Stats.insecureNavigations.get() > 0) add("You opened ${Stats.insecureNavigations.get()} unencrypted page(s) this session.")
    }
}

// ============================================================ 5. One-Tap Erase
class EraseScreen : Screen() {
    private var mode = EraseMode.QUICK
    private val custom = mutableSetOf(EraseScope.TABS, EraseScope.COOKIES, EraseScope.CACHE)

    private fun scopes() = if (mode == EraseMode.CUSTOM) custom else mode.scopes

    override fun build(): View = Ui.scaffold(a, "Erase Your Activity?", { a.pop() }) {
        val hero = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        hero.addView(Ui.text(a, "🗑", 48f, Ui.EMERALD).apply { gravity = Gravity.CENTER })
        add(hero, 4)
        val modes = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        for (m in EraseMode.values()) modes.addView(Ui.button(a, m.label.substringBefore(' '), if (m == mode) 0 else 1) { mode = m; a.refresh() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(Ui.dp(a, 3), 0, Ui.dp(a, 3), 0) })
        add(modes, 10)
        add(Ui.text(a, "${mode.label}: this will clear", 13f, Ui.MUTED), 12)
        for (s in EraseScope.values()) {
            add(Ui.toggle(a, s.label, null, s in scopes()) { on -> if (mode == EraseMode.CUSTOM) { if (on) custom.add(s) else custom.remove(s) } else { custom.clear(); custom.addAll(mode.scopes); mode = EraseMode.CUSTOM; if (on) custom.add(s) else custom.remove(s); a.refresh() } }, 6)
        }
        add(Ui.button(a, "Erase Now", 0) { confirmErase() }, 18)
        add(Ui.text(a, "This deletes ZAEEM BROWSER's local data. Websites, search providers, your network and Android may still hold their own records (see Browser Settings > What is retained).", 11f, Ui.MUTED), 12)
    }

    private fun confirmErase() {
        val sc = scopes().toSet()
        if (sc.isEmpty()) { a.toast("Nothing selected"); return }
        a.confirm("Erase now?", sc.joinToString("\n") { "• ${it.label}" }, "Erase") {
            a.app.eraser.erase(sc) { r ->
                val complete = sc.containsAll(EraseMode.COMPLETE.scopes)
                val msg = buildString {
                    r.done.forEach { append("✓ $it\n") }; r.failed.forEach { append("✗ $it\n") }
                    append("\nBrowser storage on disk: ${fmt(r.bytesBefore)} → ${fmt(r.bytesAfter)}")
                    if (complete) append("\nProfile folders are queued for deletion at next launch.")
                }
                if (complete) a.confirm("Erase complete", msg + "\n\nRestart now to finish deleting storage?", "Restart now", "Later") { Restart.now(a) }
                else a.info("Erase complete", msg)
                a.goHome()
            }
        }
    }

    private fun fmt(b: Long) = android.text.format.Formatter.formatShortFileSize(a, b)
}

// ============================================================ 6. Privacy Panic Button
class PanicScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "Privacy Panic Button", { a.pop() }) {
        val p = a.app.prefs
        val hero = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        hero.addView(ShieldView(a, 2, Ui.DANGER), LinearLayout.LayoutParams(Ui.dp(a, 100), Ui.dp(a, 110)))
        hero.addView(Ui.text(a, "Close everything and clear session data immediately.", 14f, Ui.TEXT).apply { gravity = Gravity.CENTER }, Ui.lp(a, 12))
        add(hero, 6)
        add(Ui.button(a, "Activate Now", 2) { a.panic() }, 18)
        add(Ui.section(a, "What it does"))
        add(Ui.toggle(a, "Hide content instantly", "Black screen the moment it is triggered", p.panicHide) { p.panicHide = it }, 6)
        add(Ui.toggle(a, "Close all tabs", "Destroys every tab and renderer", p.panicCloseTabs) { p.panicCloseTabs = it }, 6)
        add(Ui.toggle(a, "Clear browsing data", "Cookies, site storage, downloads, timed session; profile folders deleted on restart", p.panicClear) { p.panicClear = it }, 6)
        add(Ui.toggle(a, "Lock the app", "Requires your PIN, password or biometrics to reopen", p.panicLock) { p.panicLock = it }, 6)
        add(Ui.section(a, "Shortcuts"))
        add(Ui.toggle(a, "Shake to activate", "Two hard shakes while the app is open", p.panicShake) { p.panicShake = it }, 6)
        add(Ui.text(a, "Also: long-press the ZAEEM BROWSER app icon and tap Panic, or use ⋮ > PANIC in the browser menu. After it runs, the app restarts into a clean home screen.", 12f, Ui.MUTED), 10)
    }
}

// ============================================================ 7. Self-destructing session
class SessionScreen : Screen() {
    private var minutes = 15
    private var label: TextView? = null

    override fun build(): View = Ui.scaffold(a, "Self-Destructing Session", { a.pop() }) {
        val sc = a.app.sessions
        if (sc.current != null) {
            add(Ui.text(a, "Session will close in", 14f, Ui.MUTED).apply { gravity = Gravity.CENTER }, 16)
            label = Ui.text(a, SessionDurations.format(sc.remaining()), 48f, Ui.MINT, true).apply { gravity = Gravity.CENTER }
            add(label!!, 4)
            add(Ui.text(a, "Space: ${sc.space?.name}", 13f, Ui.MUTED).apply { gravity = Gravity.CENTER }, 4)
            add(Ui.button(a, "Open a tab in this session") { a.space = sc.space!!; a.openUrl("about:blank") }, 20)
            add(Ui.button(a, "End now and erase", 2) { a.confirm("End session?", "Its tabs and data will be erased now.", "End") { sc.end() } }, 10)
        } else {
            add(Ui.text(a, "Session Timer", 14f, Ui.MUTED).apply { gravity = Gravity.CENTER }, 10)
            add(Ui.text(a, SessionDurations.format(minutes * 60_000L), 48f, Ui.MINT, true).apply { gravity = Gravity.CENTER }, 4)
            for (m in SessionDurations.PRESET_MINUTES) add(Ui.toggleChoice(a, "$m Minutes", minutes == m) { minutes = m; a.refresh() }, 6)
            add(Ui.toggleChoice(a, "Custom…", minutes !in SessionDurations.PRESET_MINUTES) {
                a.input("Minutes (1 to 1440)", "e.g. 45", "") { s -> SessionDurations.parseCustomMinutes(s)?.let { minutes = it; a.refresh() } ?: a.toast("Enter 1 to 1440") }
            }, 6)
            add(Ui.button(a, "Start Timer") {
                val s = sc.start(minutes); a.space = s; a.toast("Session started in a clean, isolated space"); a.openUrl("about:blank")
            }, 18)
            add(Ui.text(a, "When time is up: its tabs close, its cookies and storage are cleared, its permissions revoked and its profile deleted. If the app is killed or the phone restarts, the profile is deleted at next launch.", 12f, Ui.MUTED), 12)
        }
    }

    override fun onShow() { a.app.sessions.onTick = { label?.text = SessionDurations.format(a.app.sessions.remaining()) } }
    override fun onHide() { a.app.sessions.onTick = null }
}

// ============================================================ 8. Private Spaces
class SpacesScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "Private Spaces", { a.pop() }, "+ Add" to { add() }) {
        if (!Engine.multiProfile) add(Ui.text(a, "⚠ This device's WebView is too old for isolated spaces (needs profile support). Update Android System WebView. Only the default space is usable.", 13f, Ui.WARN), 4)
        for (s in a.app.prefs.spaces()) {
            val n = a.app.tabs.inSpace(s.id).size
            val row = Ui.nav(a, s.name + if (s.id == a.space.id) "  ● active" else "", "${s.kind}${if (s.ephemeral) " · erased on exit" else ""} · $n tab(s) · own cookies, storage, cache${if (s.locked) " · 🔒 protected" else ""}") { options(s) }
            add(row, 8)
        }
        add(Ui.text(a, "Each space is a separate engine profile: cookies, site storage, cache and permissions are not shared between spaces.", 12f, Ui.MUTED), 14)
    }

    private fun add() {
        if (!Engine.multiProfile) { a.toast("Isolation unavailable on this WebView"); return }
        a.choose("New space type", listOf("Work", "Research", "Private", "Temporary (erased on exit)", "Custom name")) { i ->
            val kinds = listOf("Work", "Research", "Private", "Temporary", "Custom")
            val create = { name: String ->
                val l = a.app.prefs.spaces()
                l.add(Prefs.Space(java.util.UUID.randomUUID().toString().take(8), name.trim().ifEmpty { kinds[i] }.take(24), kinds[i], false, i == 3))
                a.app.prefs.saveSpaces(l); a.refresh()
            }
            if (i == 4) a.input("Space name", "Name", "") { create(it) } else create(kinds[i])
        }
    }

    private fun options(s: Prefs.Space) {
        val items = mutableListOf("Open", if (s.locked) "Remove PIN/biometric protection" else "Protect with PIN/biometrics")
        if (s.id != "personal") items.add("Delete and erase")
        a.choose(s.name, items) { i ->
            when (i) {
                0 -> a.enterSpace(s)
                1 -> a.requireAuth("Change protection for ${s.name}") {
                    val l = a.app.prefs.spaces(); val k = l.indexOfFirst { it.id == s.id }; l[k] = s.copy(locked = !s.locked); a.app.prefs.saveSpaces(l); a.refresh()
                }
                2 -> a.confirm("Delete ${s.name}?", "Its tabs, cookies and storage are erased.", "Delete") {
                    a.app.eraser.wipeSpace(s)
                    a.app.prefs.saveSpaces(a.app.prefs.spaces().filter { it.id != s.id })
                    if (a.space.id == s.id) a.space = a.app.prefs.spaces().first(); a.refresh()
                }
            }
        }
    }
}

// ============================================================ App Lock settings
class AppLockScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "ZAEEM App Lock", { a.pop() }) {
        val p = a.app.prefs; val lock = a.app.lock
        val on = lock.isConfigured()
        add(Ui.kv(a, "Status", if (on) "On (${if (p.lockType == "pin") "PIN" else "password"})" else "Off", if (on) Ui.EMERALD else Ui.WARN), 4)
        add(Ui.button(a, if (on) "Change PIN" else "Set PIN") { setSecret("pin") }, 10)
        add(Ui.button(a, if (on && p.lockType == "password") "Change password" else "Use a password instead", 1) { setSecret("password") }, 8)
        if (on) {
            add(Ui.section(a, "Behaviour"))
            add(Ui.toggle(a, "Biometric unlock", if (lock.biometricAvailable(a)) "Fingerprint or face (Android Strong biometrics)" else "No enrolled strong biometrics found", p.biometricEnabled) { p.biometricEnabled = it }, 4)
            val opts = listOf("Immediately" to 0L, "After 30 seconds" to 30_000L, "After 1 minute" to 60_000L, "After 5 minutes" to 300_000L, "Never (manual only)" to -1L)
            add(Ui.nav(a, "Auto-lock when minimised", opts.firstOrNull { it.second == p.lockTimeoutMs }?.first ?: "Custom") {
                a.choose("Auto-lock", opts.map { it.first }) { i -> p.lockTimeoutMs = opts[i].second; a.refresh() }
            }, 8)
            add(Ui.toggle(a, "Lock Private Vault when minimised", null, p.vault_autolock()) { p.put("vaultAutolock", it) }, 8)
            add(Ui.button(a, "Lock now", 1) { a.lockNow() }, 14)
            add(Ui.button(a, "Turn off App Lock", 2) { a.requireAuth("Turn off App Lock") { lock.disable(); a.applyScreenPolicy(); a.refresh() } }, 8)
        }
        add(Ui.text(a, "PINs and passwords are never stored. Only a salted PBKDF2-HMAC-SHA256 hash, encrypted with a key held in the Android Keystore, is kept. After 5 wrong attempts, lockouts grow from 30 seconds up to 1 hour. App previews are hidden in the recent-apps screen while a lock is set.", 12f, Ui.MUTED), 16)
    }

    private fun setSecret(type: String) {
        val label = if (type == "pin") "PIN (4 to 12 digits)" else "password (8+ characters)"
        val go = {
            a.input("New $label", "", "", true, type == "pin") { first ->
                val valid = if (type == "pin") SecretHasher.isValidPin(first) else SecretHasher.isValidPassword(first)
                if (!valid) { a.toast("That is not a valid $label"); return@input }
                a.input("Confirm", "", "", true, type == "pin") { second ->
                    if (first == second && a.app.lock.setSecret(type, first.toCharArray())) { a.toast("App Lock is on"); a.applyScreenPolicy(); a.refresh() } else a.toast("They did not match")
                }
            }
        }
        if (a.app.lock.isConfigured()) a.requireAuth("Change your lock") { go() } else go()
    }
}
