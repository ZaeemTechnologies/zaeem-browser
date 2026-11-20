package com.zaeem.browser.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.LinearLayout
import com.zaeem.browser.Screen
import com.zaeem.browser.core.*
import com.zaeem.browser.data.Bookmark
import com.zaeem.browser.data.Stats
import com.zaeem.browser.engine.*
import com.zaeem.browser.ui.Ui.add
import java.text.DateFormat
import java.util.Date

internal fun LinearLayout.note(c: Context, t: String, top: Int = 10) = add(Ui.text(c, t, 12f, Ui.MUTED), top)

// ============================================================ 9. Permission Guardian
class PermissionsScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "Permission Guardian", { a.pop() }) {
        val p = a.app.prefs; val cfg = p.config()
        add(Ui.section(a, "Defaults"), 0)
        add(Ui.toggle(a, "Block camera & microphone by default", "Off = ask each time (Allow once / session / always / Block)", cfg.blockMicCameraByDefault) { v -> p.update { it.copy(blockMicCameraByDefault = v) } }, 4)
        add(Ui.toggle(a, "Block location by default", "Off = ask each time", cfg.blockLocationByDefault) { v -> p.update { it.copy(blockLocationByDefault = v) } }, 6)
        add(Ui.toggle(a, "Block pop-ups", "Stops scripts opening windows without a tap (applies to new tabs)", cfg.blockPopups) { v -> p.update { it.copy(blockPopups = v) } }, 6)
        add(Ui.kv(a, "Notifications", "Always blocked", Ui.MUTED), 6)
        add(Ui.kv(a, "Downloads", "HTTPS only, always asks", Ui.MUTED), 0)
        add(Ui.section(a, "Site decisions"))
        val list = a.app.perms.entries()
        if (list.isEmpty()) note(a, "No saved or session decisions.", 0)
        for (e in list) {
            val h = UrlUtils.host(e.origin) ?: e.origin
            val label = when (e.decision) { Decision.ALWAYS_ALLOW -> "Always allowed"; Decision.ALLOW_SESSION -> "Allowed this session"; Decision.BLOCK -> "Blocked"; else -> "Allowed once" }
            add(Ui.nav(a, "$h · ${e.kind.label}", label + " · tap to revoke") { a.app.perms.revoke(e.origin, e.kind); a.refresh() }, 6)
        }
        if (list.isNotEmpty()) add(Ui.button(a, "Revoke all", 1) { a.app.perms.revokeAll(); a.refresh() }, 14)
        note(a, "Session permissions are revoked automatically when a timed session ends or you erase. Android's own permission prompts still apply and are requested only when a site asks.")
    }
}

// ============================================================ 10. Privacy Settings (hub)
class SettingsScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "Privacy Settings", { a.pop() }) {
        val p = a.app.prefs; val cfg = p.config()
        add(Ui.section(a, "Preset"), 0)
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        for (pr in PrivacyPreset.values()) row.addView(Ui.button(a, pr.name.lowercase().replaceFirstChar { it.uppercase() }, if (p.matchingPreset() == pr) 0 else 1) {
            p.saveConfig(PrivacyConfig.forPreset(pr)); a.applyScreenPolicy(); a.refresh()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(Ui.dp(a, 3), 0, Ui.dp(a, 3), 0) })
        add(row, 2)
        if (p.matchingPreset() == null) note(a, "Custom: your settings differ from every preset.", 6)
        add(Ui.section(a, "Protection (applies to new tabs)"))
        add(Ui.kv(a, "No History Mode", "Always on", Ui.EMERALD), 0)
        note(a, "ZAEEM BROWSER never writes browsing history, search history or session restore data.", 0)
        add(Ui.toggle(a, "Auto-clear on exit", "Delete cookies and site data when the app closes (also swept at next launch)", cfg.clearOnExit) { v -> p.update { it.copy(clearOnExit = v) } }, 8)
        val bg = listOf("Off" to 0, "After 1 minute" to 1, "After 5 minutes" to 5, "After 15 minutes" to 15, "After 30 minutes" to 30)
        add(Ui.nav(a, "Clear data after time in background", bg.firstOrNull { it.second == cfg.clearAfterBackgroundMinutes }?.first ?: "Custom") {
            a.choose("Clear after", bg.map { it.first }) { i -> p.update { it.copy(clearAfterBackgroundMinutes = bg[i].second) }; a.refresh() }
        }, 6)
        add(Ui.toggle(a, "Block trackers", "Prevent tracking across sites", cfg.blockTrackers) { v -> p.update { it.copy(blockTrackers = v) } }, 6)
        add(Ui.toggle(a, "HTTPS-first", "Upgrade http:// links; warn before falling back", cfg.httpsFirst) { v -> p.update { it.copy(httpsFirst = v) } }, 6)
        add(Ui.toggle(a, "Block third-party cookies", null, cfg.blockThirdPartyCookies) { v -> p.update { it.copy(blockThirdPartyCookies = v) } }, 6)
        add(Ui.toggle(a, "Safe Browsing (phishing/malware)", "Uses Google hash-prefix lookups; off in Maximum preset", cfg.safeBrowsing) { v -> p.update { it.copy(safeBrowsing = v) } }, 6)
        add(Ui.toggle(a, "Disable autofill", "Stops Android autofill services reading pages", cfg.disableAutofill) { v -> p.update { it.copy(disableAutofill = v) } }, 6)
        add(Ui.toggle(a, "Screenshot protection", "Block screenshots and hide the recent-apps preview", cfg.noScreenshots) { v -> p.update { it.copy(noScreenshots = v) }; a.applyScreenPolicy() }, 6)
        add(Ui.section(a, "More"))
        for ((t, s, f) in listOf<Triple<String, String, () -> Screen>>(
            Triple("App Lock", "PIN, password, biometrics", { AppLockScreen() }),
            Triple("Permission Guardian", "Camera, microphone, location, pop-ups", { PermissionsScreen() }),
            Triple("Fingerprinting protection", "Canvas, WebGL, WebRTC, device info", { FingerprintScreen() }),
            Triple("Secure DNS", "What the system DNS is doing", { DnsScreen() }),
            Triple("Clipboard Guard", "Sensitive copy detection", { ClipboardGuardScreen() }),
            Triple("Screen Privacy Mode", "Screenshots and previews", { ScreenPrivacyScreen() }),
            Triple("Private Vault", "Encrypted bookmarks", { VaultScreen() }),
            Triple("Tracker Radar", "Blocked trackers", { TrackerRadarScreen() }),
            Triple("Privacy audit", "Check this configuration", { AuditScreen() }),
            Triple("Security updates", "Engine and system patch level", { UpdatesScreen() }),
            Triple("Browser settings", "Search engine, lists, about", { BrowserSettingsScreen() }))) add(Ui.nav(a, t, s) { a.push(f()) }, 6)
    }
}

// ============================================================ 11. Private Bookmark Vault
class VaultScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "Private Bookmarks", { a.pop() }, if (a.app.vault.unlocked) "+ Add" to { addDialog() } else null) {
        val v = a.app.vault
        val hero = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        hero.addView(Ui.text(a, if (v.unlocked) "🔓" else "🔒", 44f, Ui.EMERALD).apply { gravity = Gravity.CENTER })
        add(hero, 4)
        if (!v.unlocked) {
            add(Ui.button(a, "Unlock Vault") { a.requireAuth("Private Vault") { if (v.unlock()) a.refresh() else a.toast("Vault could not be decrypted") } }, 12)
            note(a, "Bookmarks are encrypted with AES-256-GCM using a key that never leaves the Android Keystore, and need your App Lock to open.")
            return@scaffold
        }
        val all = v.list()
        if (all.isEmpty()) note(a, "Empty. Add bookmarks with + Add or from the browser menu.", 12)
        for ((folder, items) in all.groupBy { it.folder }) {
            add(Ui.section(a, "📁 $folder"), 4)
            for (b in items) add(Ui.nav(a, b.title, UrlUtils.host(b.url) ?: b.url) {
                a.choose(b.title, listOf("Open", "Delete")) { i -> if (i == 0) a.openUrl(b.url) else { v.remove(b); a.refresh() } }
            }, 6)
        }
        add(Ui.button(a, "Lock vault now", 1) { v.lock(); a.refresh() }, 16)
        add(Ui.button(a, "Delete vault", 2) { a.confirm("Delete vault?", "All private bookmarks are permanently deleted.", "Delete") { v.wipe(); a.refresh() } }, 8)
    }

    private fun addDialog() {
        a.input("Title", "Title", "") { t ->
            a.input("Address", "https://…", "") { u ->
                val url = UrlUtils.resolve(u, a.app.prefs.searchEngine().template, true)
                if (!UrlUtils.isWebUrl(url)) { a.toast("Not a valid web address"); return@input }
                a.input("Folder", "Folder", "General") { f -> a.app.vault.add(Bookmark(f.ifBlank { "General" }, t.ifBlank { UrlUtils.host(url) ?: url }, url)); a.refresh() }
            }
        }
    }
}

// ============================================================ 12. Tracker Radar
class RadarView(c: Context, private val n: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val r = minOf(cx, cy) * 0.92f
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0x6614D48A
        for (i in 1..4) cv.drawCircle(cx, cy, r * i / 4f, p)
        cv.drawLine(cx - r, cy, cx + r, cy, p); cv.drawLine(cx, cy - r, cx, cy + r, p)
        p.style = Paint.Style.FILL; p.color = Ui.EMERALD; cv.drawCircle(cx, cy, 7f, p)
        p.textAlign = Paint.Align.CENTER; p.textSize = r * 0.28f; p.color = Ui.MINT; p.isFakeBoldText = true
        cv.drawText("$n", cx, cy - r * 0.18f, p)
    }
}

class TrackerRadarScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "Tracker Radar", { a.pop() }) {
        val total = Stats.trackersBlocked.get()
        add(RadarView(a, total), 4).also { it.layoutParams = Ui.lp(a, 4, 0, Ui.dp(a, 190)) }
        add(Ui.text(a, if (total == 1) "1 tracker request blocked this session" else "$total tracker requests blocked this session", 15f, Ui.TEXT, true).apply { gravity = Gravity.CENTER }, 6)
        val hosts = Stats.byHost.entries.sortedByDescending { it.value.get() }.take(60)
        add(Ui.section(a, "Blocked domains"))
        if (hosts.isEmpty()) note(a, "Nothing blocked yet. Browse a page and come back.", 0)
        for (h in hosts) add(Ui.kv(a, h.key, "${h.value.get()}×"), 0)
        add(Ui.section(a, "Filter list"))
        val c = Ui.card(a)
        c.addView(Ui.kv(a, "Entries", a.app.blocklist.matcher.size.toString()))
        c.addView(Ui.kv(a, "Source", a.app.blocklist.source))
        c.addView(Ui.kv(a, "Blocking", if (a.app.prefs.config().blockTrackers) "On" else "Off", if (a.app.prefs.config().blockTrackers) Ui.EMERALD else Ui.WARN))
        add(c)
        add(Ui.button(a, "Update list (download, HTTPS)", 1) { BrowserSettingsScreen.updateLists(a) }, 12)
        note(a, "Counts live in memory only and reset on erase.")
    }
}

// ============================================================ 13. Secure Downloads
class DownloadsScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "Downloads", { a.pop() }) {
        val d = a.app.downloader
        if (d.items.isEmpty()) add(Ui.text(a, "No downloads. Files are fetched over HTTPS only, kept in private app storage, and never opened automatically.", 13f, Ui.MUTED), 16)
        for (it in d.items) {
            val col = Ui.card(a)
            col.addView(Ui.text(a, it.name, 15f, Ui.TEXT, true))
            val size = android.text.format.Formatter.formatShortFileSize(a, it.done) + if (it.total > 0) " / " + android.text.format.Formatter.formatShortFileSize(a, it.total) else ""
            col.addView(Ui.text(a, "${it.state} · $size${if (it.risky) " · ⚠ risky type" else ""}", 12f, if (it.risky) Ui.WARN else Ui.MUTED))
            val btns = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            when (it.state) {
                "Completed" -> {
                    btns.addView(Ui.button(a, "Open", 1) { open(it) }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = Ui.dp(a, 4) })
                    btns.addView(Ui.button(a, "Delete", 2) { d.remove(it); a.refresh() }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = Ui.dp(a, 4) })
                }
                "Downloading", "Queued" -> btns.addView(Ui.button(a, "Cancel", 1) { it.cancel = true }, LinearLayout.LayoutParams(0, -2, 1f))
                else -> btns.addView(Ui.button(a, "Remove", 1) { d.remove(it); a.refresh() }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            col.addView(btns, Ui.lp(a, 8)); add(col, 8)
        }
        if (d.items.isNotEmpty()) add(Ui.button(a, "Clear All", 1) { d.clearAll(); a.refresh() }, 16)
        note(a, "“Open” copies the file to Downloads/ZAEEM so another app can open it. Delete removes the private copy.")
    }

    private fun open(it: DownloadItem) {
        val go = {
            val uri = a.app.downloader.export(it)
            if (uri == null) a.toast("Could not export file") else {
                val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.name.substringAfterLast('.', "")) ?: "*/*"
                try { a.external(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                catch (e: Exception) { a.toast("Saved to Downloads/ZAEEM. No app found to open it.") }
            }
        }
        if (it.risky) a.confirm("Risky file", "${it.name} can run code if you open it. Continue?", "Continue") { go() } else go()
    }

    override fun onShow() { a.app.downloader.onChange = { a.refresh() } }
    override fun onHide() { a.app.downloader.onChange = null }
}

// ============================================================ 14. Clipboard Guard
class ClipboardGuardScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "Clipboard Guard", { a.pop() }) {
        val on = a.app.prefs.config().clipboardGuard
        val hero = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        hero.addView(ShieldView(a, 1, if (on) Ui.EMERALD else Ui.MUTED), LinearLayout.LayoutParams(Ui.dp(a, 80), Ui.dp(a, 88)))
        add(hero, 6)
        add(Ui.toggle(a, "Clipboard Guard", "Ask before sensitive text stays on the clipboard", on) { v -> a.app.prefs.update { it.copy(clipboardGuard = v) }; a.refresh() }, 14)
        add(Ui.section(a, "Detects"))
        note(a, "• Payment-card numbers (Luhn-checked)\n• One-time codes (6 to 8 digits)\n• Private-key blocks\n• Password-like strings (mixed case, digits, symbols)", 0)
        note(a, "If you allow it, the clipboard is cleared after 60 seconds. If you block it, it is cleared immediately.", 12)
        note(a, "Limits: Android only lets an app read the clipboard while it is in the foreground, so Guard sees copies made while ZAEEM BROWSER is open. It cannot stop other apps reading the clipboard.", 12)
    }
}

// ============================================================ 15. Screen Privacy Mode
class ScreenPrivacyScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "Screen Privacy Mode", { a.pop() }) {
        val on = a.app.prefs.config().noScreenshots
        val hero = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        hero.addView(Ui.text(a, if (on) "🚫📷" else "📷", 44f, Ui.EMERALD).apply { gravity = Gravity.CENTER })
        hero.addView(Ui.text(a, if (on) "Screenshots are blocked" else "Screenshots are allowed", 17f, Ui.TEXT, true), Ui.lp(a, 6))
        add(hero, 8)
        add(Ui.toggle(a, "Block screenshots and screen recording", "Uses Android's secure-window flag", on) { v -> a.app.prefs.update { it.copy(noScreenshots = v) }; a.applyScreenPolicy(); a.refresh() }, 16)
        add(Ui.kv(a, "Recent-apps preview", if (on || a.app.lock.isConfigured()) "Hidden" else "Visible", if (on || a.app.lock.isConfigured()) Ui.EMERALD else Ui.WARN), 10)
        note(a, "The preview is hidden whenever Screenshot protection or App Lock is on. Limit: this stops Android's screenshot and screen-recording features; it cannot stop someone photographing the screen with another device.")
    }
}

// ============================================================ 18. Connection Security Inspector
class InspectorScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "Connection Security", { a.pop() }) {
        val t = a.app.tabs.active
        val cfg = a.app.prefs.config()
        if (t == null || !UrlUtils.isWebUrl(t.url)) { note(a, "Open a web page first.", 16); return@scaffold }
        val https = t.url.startsWith("https://")
        val card = Ui.card(a)
        card.addView(Ui.kv(a, "Site", t.host))
        card.addView(Ui.kv(a, "Connection", if (https) "Encrypted (HTTPS)" else "NOT encrypted (HTTP)", if (https) Ui.EMERALD else Ui.DANGER))
        add(card, 8)
        val cert = t.webView?.certificate
        if (https && cert != null) {
            add(Ui.section(a, "Certificate"))
            val c = Ui.card(a)
            c.addView(Ui.kv(a, "Issued to", cert.issuedTo?.cName ?: "?"))
            c.addView(Ui.kv(a, "Organization", cert.issuedTo?.oName?.ifBlank { "—" } ?: "—"))
            c.addView(Ui.kv(a, "Issued by", cert.issuedBy?.oName?.ifBlank { null } ?: cert.issuedBy?.cName ?: "?"))
            val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
            cert.validNotBeforeDate?.let { c.addView(Ui.kv(a, "Valid from", df.format(it))) }
            cert.validNotAfterDate?.let { c.addView(Ui.kv(a, "Valid until", df.format(it))) }
            add(c)
        }
        add(Ui.section(a, "Protections on this page"))
        val p = Ui.card(a)
        p.addView(Ui.kv(a, "Certificate errors", "Always blocked", Ui.EMERALD))
        p.addView(Ui.kv(a, "Mixed (insecure) content", "Blocked", Ui.EMERALD))
        p.addView(Ui.kv(a, "Third-party cookies", if (cfg.blockThirdPartyCookies) "Blocked" else "Allowed", if (cfg.blockThirdPartyCookies) Ui.EMERALD else Ui.WARN))
        p.addView(Ui.kv(a, "Tracker blocking", if (cfg.blockTrackers) "On" else "Off", if (cfg.blockTrackers) Ui.EMERALD else Ui.WARN))
        p.addView(Ui.kv(a, "Space", t.space.name))
        add(p)
        note(a, "Details come from the engine's connection record for this page. A valid certificate proves the connection is encrypted to that domain, not that the site is trustworthy.")
    }
}

// ============================================================ 19. Secure DNS
class DnsScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "Secure DNS", { a.pop() }) {
        val cr = a.contentResolver
        val mode = Settings.Global.getString(cr, "private_dns_mode") ?: "unknown"
        val host = Settings.Global.getString(cr, "private_dns_specifier")
        val label = when (mode) { "hostname" -> "On: $host"; "opportunistic" -> "Automatic (uses encrypted DNS if the network offers it)"; "off" -> "Off"; else -> "Unknown (managed or unreadable)" }
        val ok = mode == "hostname" || mode == "opportunistic"
        val c = Ui.card(a)
        c.addView(Ui.kv(a, "Android Private DNS", label, if (mode == "hostname") Ui.EMERALD else if (ok) Ui.WARN else Ui.DANGER))
        add(c, 8)
        add(Ui.button(a, "Open network settings") { try { a.external(Intent(Settings.ACTION_WIRELESS_SETTINGS)) } catch (e: Exception) { a.toast("Open Settings > Network > Private DNS") } }, 14)
        add(Ui.section(a, "Provider hostnames (tap to copy)"))
        for ((n, h) in listOf("Quad9 (malware blocking)" to "dns.quad9.net", "Mullvad" to "dns.mullvad.net", "Cloudflare" to "one.one.one.one", "AdGuard (ads/trackers)" to "dns.adguard-dns.com"))
            add(Ui.nav(a, n, h) { a.copy(h) }, 6)
        note(a, "Honest scope: the Chromium WebView engine uses Android's DNS and cannot be given its own DNS-over-HTTPS server by an app. Private DNS (DNS-over-TLS) set in Android Settings > Network > Private DNS protects every app, including this browser. Settings > Private DNS > “Private DNS provider hostname”, paste a hostname above.", 14)
    }
}

// ============================================================ 20. Fingerprinting protection
class FingerprintScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "Fingerprinting Protection", { a.pop() }) {
        val p = a.app.prefs; val cfg = p.config()
        for ((lv, t) in listOf(FingerprintLevel.OFF to "Off", FingerprintLevel.STANDARD to "Standard: canvas noise, generic CPU/memory values", FingerprintLevel.STRICT to "Strict: Standard + generic WebGL, languages, plugins, audio noise"))
            add(Ui.toggleChoice(a, t, cfg.fingerprint == lv) { p.update { it.copy(fingerprint = lv) }; a.refresh() }, 6)
        add(Ui.toggle(a, "Block WebRTC", "Prevents local/public IP exposure through WebRTC. Breaks in-browser video calls.", cfg.blockWebRtc) { v -> p.update { it.copy(blockWebRtc = v) } }, 14)
        add(Ui.kv(a, "Global Privacy Control signal", "Always sent", Ui.EMERALD), 10)
        add(Ui.kv(a, "Third-party cookies", if (cfg.blockThirdPartyCookies) "Blocked" else "Allowed"), 0)
        note(a, "Applies to new tabs. These scripts reduce the most common fingerprinting surfaces; they do not make you anonymous. Randomised values can themselves be a signal, and a determined tracker may still recognise you by IP address, screen size or behaviour. For strong anonymity use Tor Browser.", 14)
    }
}

// ============================================================ 21. Privacy audit
class AuditScreen : Screen() {
    private data class Check(val name: String, val level: Int, val detail: String)   // 0 pass, 1 warn, 2 fail

    override fun build(): View = Ui.scaffold(a, "Privacy Audit", { a.pop() }) {
        val checks = run()
        val fail = checks.count { it.level == 2 }; val warn = checks.count { it.level == 1 }
        add(Ui.text(a, "${checks.size - fail - warn} passed · $warn warning(s) · $fail failed", 15f, if (fail > 0) Ui.DANGER else if (warn > 0) Ui.WARN else Ui.MINT, true), 6)
        for (c in checks) {
            val col = if (c.level == 0) Ui.EMERALD else if (c.level == 1) Ui.WARN else Ui.DANGER
            val row = Ui.card(a)
            row.addView(Ui.text(a, (if (c.level == 0) "✓  " else if (c.level == 1) "!  " else "✗  ") + c.name, 14f, col, true))
            row.addView(Ui.text(a, c.detail, 12f, Ui.MUTED))
            add(row, 8)
        }
        note(a, "This audit reads your actual configuration and device state. It does not send anything anywhere.", 14)
    }

    private fun run(): List<Check> {
        val cfg = a.app.prefs.config(); val out = mutableListOf<Check>()
        fun add(n: String, ok: Boolean, good: String, bad: String, lvl: Int = 1) = out.add(Check(n, if (ok) 0 else lvl, if (ok) good else bad))
        add("Isolated spaces", Engine.multiProfile, "Per-space engine profiles active", "WebView too old for profiles; spaces are not isolated", 2)
        val wv = Updates.webView(a)
        val days = wv?.let { (System.currentTimeMillis() - it.lastUpdateTime) / 86_400_000L }
        add("WebView engine freshness", days != null && days <= 45, "${wv?.packageName} ${wv?.versionName}, updated ${days} day(s) ago", if (days == null) "Could not read the WebView package" else "Last updated $days days ago. Update it: browser security fixes arrive through WebView.")
        add("App Lock", a.app.lock.isConfigured(), "Enabled", "Not set; anyone with your unlocked phone can open the browser")
        add("HTTPS-first", cfg.httpsFirst, "On", "Off")
        add("Tracker blocking", cfg.blockTrackers, "On, ${a.app.blocklist.matcher.size} domains", "Off")
        add("Third-party cookies", cfg.blockThirdPartyCookies, "Blocked", "Allowed")
        add("Fingerprint protection", cfg.fingerprint != FingerprintLevel.OFF, cfg.fingerprint.name.lowercase(), "Off")
        add("Pop-up blocking", cfg.blockPopups, "On", "Off")
        add("Autofill", cfg.disableAutofill, "Disabled in pages", "Android autofill can read page fields")
        add("Clear on exit", cfg.clearOnExit, "On (also swept at next launch)", "Cookies and site data persist between launches")
        add("Screenshot protection", cfg.noScreenshots, "On", "Off (recents preview still hidden if App Lock is on)", 1)
        val dns = Settings.Global.getString(a.contentResolver, "private_dns_mode")
        add("System Private DNS", dns == "hostname", "Strict provider set", "Mode: ${dns ?: "unknown"}. DNS queries may be visible to your network.")
        val patch = Build.VERSION.SECURITY_PATCH
        val old = runCatching { (System.currentTimeMillis() - java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(patch)!!.time) / 86_400_000L }.getOrNull()
        add("Android security patch", old != null && old <= 120, "$patch", "Patch level $patch is ${old ?: "?"} days old")
        val always = a.app.perms.entries().count { it.decision == Decision.ALWAYS_ALLOW }
        add("Permanent permissions", always == 0, "None saved", "$always site(s) have permanent camera/mic/location access")
        add("Debuggable build", (a.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0, "Release build", "Debug build installed; use a release build for daily use")
        out.add(Check("Local storage verification", 0, "Browser storage on disk: ${android.text.format.Formatter.formatShortFileSize(a, Engine.webViewDataBytes(a))}, ${a.app.prefs.pendingDeletes().size} profile(s) queued for deletion. ${Engine.sweepSummary}"))
        return out
    }
}

// ============================================================ 22. Security update information
object Updates { fun webView(c: Context) = if (Build.VERSION.SDK_INT >= 26) WebView.getCurrentWebViewPackage() else null }

class UpdatesScreen : Screen() {
    override fun build(): View = Ui.scaffold(a, "Security Updates", { a.pop() }) {
        val wv = Updates.webView(a)
        val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
        val c = Ui.card(a)
        c.addView(Ui.kv(a, "Browser engine", "Chromium via ${wv?.packageName ?: "unknown"}"))
        c.addView(Ui.kv(a, "Engine version", wv?.versionName ?: "?"))
        c.addView(Ui.kv(a, "Engine updated", wv?.let { df.format(Date(it.lastUpdateTime)) } ?: "?"))
        c.addView(Ui.kv(a, "Android security patch", Build.VERSION.SECURITY_PATCH))
        c.addView(Ui.kv(a, "Android version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"))
        val pi = a.packageManager.getPackageInfo(a.packageName, 0)
        c.addView(Ui.kv(a, "ZAEEM BROWSER", pi.versionName ?: "?"))
        add(c, 8)
        val pkg = wv?.packageName
        if (pkg != null) {
            add(Ui.button(a, "Check engine in Play Store") {
                try { a.external(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))) } catch (e: Exception) { a.toast("Play Store not available") }
            }, 14)
            add(Ui.button(a, "Open engine app settings", 1) { a.external(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))) }, 8)
        }
        note(a, "How updates work: ZAEEM BROWSER uses the system Chromium engine (the same approach as GrapheneOS's Vanadium), so Chromium security fixes ship through Android System WebView updates, not through this app. Keep the engine and Android patch level current. Updates to ZAEEM BROWSER itself are installed from the GitHub release you built or downloaded; no background update service runs.", 14)
    }
}

// ============================================================ 23. Browser settings
class BrowserSettingsScreen : Screen() {
    companion object {
        fun updateLists(a: com.zaeem.browser.MainActivity) {
            a.toast("Downloading filter list…")
            Thread {
                val msg = try { "Loaded ${a.app.blocklist.download()} domains" } catch (e: Exception) { "Update failed: ${e.message}" }
                a.runOnUiThread { a.app.prefs.blocklistUpdatedAt = System.currentTimeMillis(); a.toast(msg); a.refresh() }
            }.start()
        }
    }

    override fun build(): View = Ui.scaffold(a, "Browser Settings", { a.pop() }) {
        val p = a.app.prefs
        add(Ui.nav(a, "Search engine", p.searchEngine().name) {
            a.choose("Search engine", Prefs_ENGINES.map { it.name }) { i -> p.put("engine", Prefs_ENGINES[i].id); a.refresh() }
        }, 4)
        note(a, "Search suggestions are not requested: what you type is only sent when you press Go.", 4)
        add(Ui.section(a, "Tracker lists"))
        add(Ui.kv(a, "Entries", a.app.blocklist.matcher.size.toString()), 0)
        add(Ui.kv(a, "Last downloaded", if (p.blocklistUpdatedAt == 0L) "Never (using built-in seed list)" else DateFormat.getDateTimeInstance().format(Date(p.blocklistUpdatedAt))), 0)
        add(Ui.button(a, "Download larger list (HTTPS, from GitHub)", 1) { updateLists(a) }, 8)
        add(Ui.section(a, "What is retained"))
        note(a, "Deleted by ZAEEM BROWSER: tabs, cookies, site storage, cache, downloads, permission decisions. Never stored: history, search terms, session restore.\n\nNOT under our control: the sites you visit and their logs, your search provider, your network operator/ISP, DNS resolver, any VPN, Android system logs, and your Google account if Safe Browsing or Play Services are used.", 0)
        add(Ui.section(a, "Engine status"))
        add(Ui.kv(a, "Startup sweep", Engine.sweepSummary), 0)
        add(Ui.section(a, "About"))
        note(a, "ZAEEM BROWSER · no ads · no analytics · no accounts · no servers.\nOpen-source components: Android System WebView (Chromium, BSD-style), androidx.webkit (Apache-2.0), Kotlin standard library (Apache-2.0).", 0)
    }
}

private val Prefs_ENGINES get() = com.zaeem.browser.data.Prefs.ENGINES
