package com.zaeem.browser.engine

import com.zaeem.browser.data.Prefs
import org.json.JSONObject

enum class PermKind(val label: String) { CAMERA("Camera"), MICROPHONE("Microphone"), LOCATION("Location") }
enum class Decision { ALLOW_ONCE, ALLOW_SESSION, ALWAYS_ALLOW, BLOCK }

/** Per-origin decisions. "Always" is persisted (origin + kind only); "session" lives in memory and dies with the session/erase. */
class PermissionStore(private val prefs: Prefs) {
    private val session = HashMap<String, Decision>()

    private fun key(origin: String, k: PermKind) = "$origin|${k.name}"
    private fun persisted(): JSONObject = runCatching { JSONObject(prefs.str("permissions", "{}")!!) }.getOrDefault(JSONObject())

    /** null = ask. */
    fun lookup(origin: String, k: PermKind): Decision? {
        session[key(origin, k)]?.let { return it }
        val p = persisted().optString(key(origin, k), "")
        return runCatching { Decision.valueOf(p) }.getOrNull()
    }

    fun record(origin: String, k: PermKind, d: Decision) {
        when (d) {
            Decision.ALLOW_ONCE -> {}
            Decision.ALLOW_SESSION -> session[key(origin, k)] = d
            Decision.ALWAYS_ALLOW, Decision.BLOCK -> prefs.put("permissions", persisted().put(key(origin, k), d.name).toString())
        }
    }

    fun revoke(origin: String, k: PermKind) {
        session.remove(key(origin, k))
        prefs.put("permissions", persisted().apply { remove(key(origin, k)) }.toString())
    }

    fun revokeSession() = session.clear()
    fun revokeAll() { session.clear(); prefs.put("permissions", "{}") }

    data class Entry(val origin: String, val kind: PermKind, val decision: Decision)

    fun entries(): List<Entry> {
        val out = mutableListOf<Entry>()
        session.forEach { (k, v) -> parse(k)?.let { out.add(Entry(it.first, it.second, v)) } }
        val p = persisted()
        p.keys().forEach { k -> parse(k)?.let { pk -> runCatching { out.add(Entry(pk.first, pk.second, Decision.valueOf(p.getString(k)))) } } }
        return out.sortedBy { it.origin }
    }

    private fun parse(k: String): Pair<String, PermKind>? {
        val i = k.lastIndexOf('|'); if (i < 0) return null
        return runCatching { k.substring(0, i) to PermKind.valueOf(k.substring(i + 1)) }.getOrNull()
    }
}
