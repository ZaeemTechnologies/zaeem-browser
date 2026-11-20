package com.zaeem.browser.core

/** Domain-suffix blocklist. Understands hosts files, plain domain lists and simple ABP `||domain^` rules. */
class TrackerMatcher(domains: Set<String> = emptySet()) {
    // Concurrent sets: the list can be refreshed on a background thread while requests are being checked.
    private val blocked: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply { addAll(domains) }
    private val allowed: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val size: Int get() = blocked.size

    fun addAll(d: Collection<String>) { blocked.addAll(d) }
    fun allowSite(host: String) { allowed.add(host.lowercase().removePrefix("www.")) }
    fun clearAllowList() = allowed.clear()

    /** True if [requestHost] or any parent domain is listed, unless [pageHost] is allow-listed. */
    fun isBlocked(requestHost: String, pageHost: String? = null): Boolean {
        if (pageHost != null && matchesAny(pageHost, allowed)) return false
        return matchesAny(requestHost, blocked)
    }

    /** Third-party = registrable-ish domains differ (last two labels; good enough without a PSL). */
    fun isThirdParty(requestHost: String, pageHost: String?): Boolean {
        if (pageHost == null) return false
        return site(requestHost) != site(pageHost)
    }

    private fun site(h: String): String {
        val p = h.lowercase().split('.')
        return if (p.size <= 2) h.lowercase() else p.takeLast(2).joinToString(".")
    }

    private fun matchesAny(host: String, set: Set<String>): Boolean {
        var h = host.lowercase().trimEnd('.')
        while (h.isNotEmpty()) {
            if (h in set) return true
            val i = h.indexOf('.')
            if (i < 0) return false
            h = h.substring(i + 1)
        }
        return false
    }

    companion object {
        private val DOMAIN = Regex("""[a-z0-9]([a-z0-9._-]*[a-z0-9])?\.[a-z]{2,}""")
        fun parse(text: String): Set<String> {
            val out = HashSet<String>()
            for (raw in text.lineSequence()) {
                var line = raw.trim().lowercase()
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!") || line.startsWith("[")) continue
                if (line.startsWith("@@")) continue // exceptions are not imported
                line = line.substringBefore('#').trim()
                val dom = when {
                    line.startsWith("||") -> line.removePrefix("||").substringBefore('^').substringBefore('/').substringBefore('$')
                    line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ") -> line.substringAfter(' ').trim().substringBefore(' ')
                    ' ' !in line && '/' !in line && '*' !in line -> line
                    else -> continue
                }
                if (dom != "localhost" && DOMAIN.matches(dom)) out.add(dom)
            }
            return out
        }
    }
}
