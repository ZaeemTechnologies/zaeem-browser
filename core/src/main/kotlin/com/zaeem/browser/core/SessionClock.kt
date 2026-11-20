package com.zaeem.browser.core

data class TimedSession(val startedAt: Long, val durationMs: Long) {
    val endsAt: Long get() = startedAt + durationMs
    fun remaining(now: Long): Long = (endsAt - now).coerceAtLeast(0)
    fun expired(now: Long): Boolean = now >= endsAt

    fun encode() = "$startedAt:$durationMs"
    companion object {
        fun decode(s: String?): TimedSession? {
            val p = s?.split(":") ?: return null
            if (p.size != 2) return null
            val a = p[0].toLongOrNull() ?: return null
            val d = p[1].toLongOrNull() ?: return null
            return if (d > 0) TimedSession(a, d) else null
        }
    }
}

object SessionDurations {
    val PRESET_MINUTES = listOf(5, 15, 30, 60)
    fun parseCustomMinutes(s: String): Int? = s.trim().toIntOrNull()?.takeIf { it in 1..1440 }
    fun format(ms: Long): String {
        val total = (ms + 999) / 1000
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
