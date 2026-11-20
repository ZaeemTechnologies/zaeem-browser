package com.zaeem.browser.data

import android.content.Context
import android.content.SharedPreferences
import com.zaeem.browser.core.*
import org.json.JSONArray
import org.json.JSONObject

/** Settings only. No URLs, no history, no search terms are ever written here. */
class Prefs(ctx: Context) {
    val sp: SharedPreferences = ctx.getSharedPreferences("zaeem_prefs", Context.MODE_PRIVATE)

    fun bool(k: String, d: Boolean) = sp.getBoolean(k, d)
    fun int(k: String, d: Int) = sp.getInt(k, d)
    fun long(k: String, d: Long) = sp.getLong(k, d)
    fun str(k: String, d: String? = null) = sp.getString(k, d)
    @Volatile private var cache: PrivacyConfig? = null

    fun put(k: String, v: Any?) {
        cache = null
        val e = sp.edit()
        when (v) {
            null -> e.remove(k); is Boolean -> e.putBoolean(k, v); is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v); is String -> e.putString(k, v); else -> error("bad pref type")
        }
        e.apply()
    }

    // ---- privacy configuration (single source of truth for protections)
    private val d = PrivacyConfig.forPreset(PrivacyPreset.BALANCED)
    fun config(): PrivacyConfig = cache ?: build().also { cache = it }

    private fun build() = PrivacyConfig(
        httpsFirst = bool("httpsFirst", d.httpsFirst),
        blockTrackers = bool("blockTrackers", d.blockTrackers),
        blockThirdPartyCookies = bool("blockThirdPartyCookies", d.blockThirdPartyCookies),
        fingerprint = runCatching { FingerprintLevel.valueOf(str("fingerprint", d.fingerprint.name)!!) }.getOrDefault(d.fingerprint),
        blockWebRtc = bool("blockWebRtc", d.blockWebRtc),
        safeBrowsing = bool("safeBrowsing", d.safeBrowsing),
        disableAutofill = bool("disableAutofill", d.disableAutofill),
        blockPopups = bool("blockPopups", d.blockPopups),
        clearOnExit = bool("clearOnExit", d.clearOnExit),
        clearAfterBackgroundMinutes = int("clearAfterBg", d.clearAfterBackgroundMinutes),
        noScreenshots = bool("noScreenshots", d.noScreenshots),
        clipboardGuard = bool("clipboardGuard", d.clipboardGuard),
        blockMicCameraByDefault = bool("blockMicCam", d.blockMicCameraByDefault),
        blockLocationByDefault = bool("blockLoc", d.blockLocationByDefault),
    )

    fun saveConfig(c: PrivacyConfig) {
        cache = null
        sp.edit().putBoolean("httpsFirst", c.httpsFirst).putBoolean("blockTrackers", c.blockTrackers)
            .putBoolean("blockThirdPartyCookies", c.blockThirdPartyCookies).putString("fingerprint", c.fingerprint.name)
            .putBoolean("blockWebRtc", c.blockWebRtc).putBoolean("safeBrowsing", c.safeBrowsing)
            .putBoolean("disableAutofill", c.disableAutofill).putBoolean("blockPopups", c.blockPopups)
            .putBoolean("clearOnExit", c.clearOnExit).putInt("clearAfterBg", c.clearAfterBackgroundMinutes)
            .putBoolean("noScreenshots", c.noScreenshots).putBoolean("clipboardGuard", c.clipboardGuard)
            .putBoolean("blockMicCam", c.blockMicCameraByDefault).putBoolean("blockLoc", c.blockLocationByDefault).apply()
    }

    fun update(f: (PrivacyConfig) -> PrivacyConfig) = saveConfig(f(config()))

    /** The preset the current config exactly matches, or null if customised. */
    fun matchingPreset(): PrivacyPreset? = PrivacyPreset.values().firstOrNull { PrivacyConfig.forPreset(it) == config() }

    // ---- search engine
    data class Engine(val id: String, val name: String, val template: String)
    companion object {
        val ENGINES = listOf(
            Engine("ddg", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
            Engine("startpage", "Startpage", "https://www.startpage.com/do/search?q=%s"),
            Engine("brave", "Brave Search", "https://search.brave.com/search?q=%s"),
            Engine("mojeek", "Mojeek", "https://www.mojeek.com/search?q=%s"),
        )
    }
    fun searchEngine(): Engine = ENGINES.firstOrNull { it.id == str("engine", "ddg") } ?: ENGINES[0]

    // ---- app lock
    var lockEnabled: Boolean get() = bool("lockEnabled", false); set(v) = put("lockEnabled", v)
    var lockType: String get() = str("lockType", "pin")!!; set(v) = put("lockType", v)
    var lockTimeoutMs: Long get() = long("lockTimeout", 0L); set(v) = put("lockTimeout", v)
    var biometricEnabled: Boolean get() = bool("biometric", true); set(v) = put("biometric", v)
    var failedAttempts: Int get() = int("failedAttempts", 0); set(v) = put("failedAttempts", v)
    var lastFailureAt: Long get() = long("lastFailureAt", 0L); set(v) = put("lastFailureAt", v)
    var secretBlob: String? get() = str("secretBlob"); set(v) = put("secretBlob", v)

    // ---- panic
    var panicHide: Boolean get() = bool("panicHide", true); set(v) = put("panicHide", v)
    var panicCloseTabs: Boolean get() = bool("panicTabs", true); set(v) = put("panicTabs", v)
    var panicClear: Boolean get() = bool("panicClear", true); set(v) = put("panicClear", v)
    var panicLock: Boolean get() = bool("panicLock", true); set(v) = put("panicLock", v)
    var panicShake: Boolean get() = bool("panicShake", false); set(v) = put("panicShake", v)

    // ---- session timer (deadline only; never survives process death, see SessionController)
    var timedSession: String? get() = str("timedSession"); set(v) = put("timedSession", v)

    // ---- pending storage-level deletions (applied before any WebView exists on next launch)
    fun pendingDeletes(): Set<String> = sp.getStringSet("pendingDeletes", emptySet()) ?: emptySet()
    fun addPendingDeletes(n: Collection<String>) { sp.edit().putStringSet("pendingDeletes", pendingDeletes() + n).apply() }
    fun setPendingDeletes(n: Set<String>) { sp.edit().putStringSet("pendingDeletes", n).apply() }

    // ---- counters (numbers only)
    var lifetimeBlocked: Long get() = long("lifetimeBlocked", 0L); set(v) = put("lifetimeBlocked", v)
    fun vault_autolock() = bool("vaultAutolock", true)
    var firstRunDone: Boolean get() = bool("firstRun", false); set(v) = put("firstRun", v)
    var blocklistUpdatedAt: Long get() = long("blocklistAt", 0L); set(v) = put("blocklistAt", v)

    // ---- spaces (names and flags only)
    data class Space(val id: String, val name: String, val kind: String, val locked: Boolean, val ephemeral: Boolean)

    fun spaces(): MutableList<Space> {
        val out = mutableListOf<Space>()
        val raw = str("spaces")
        if (raw != null) runCatching {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                out.add(Space(it.getString("id"), it.getString("name"), it.getString("kind"), it.getBoolean("locked"), it.getBoolean("eph")))
            }
        }
        if (out.none { it.id == "personal" }) out.add(0, Space("personal", "Personal", "Default", false, false))
        return out
    }

    fun saveSpaces(l: List<Space>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind).put("locked", it.locked).put("eph", it.ephemeral)) }
        put("spaces", a.toString())
    }
}
