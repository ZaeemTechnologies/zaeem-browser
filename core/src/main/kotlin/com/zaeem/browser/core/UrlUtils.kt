package com.zaeem.browser.core

import java.net.URI
import java.net.URLEncoder

object UrlUtils {
    private val WEB = setOf("http", "https")

    fun scheme(url: String): String? = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
    fun host(url: String): String? = runCatching { URI(url).host?.lowercase()?.removePrefix("www.") }.getOrNull()
    fun isWebUrl(url: String) = scheme(url) in WEB && host(url) != null

    fun searchUrl(template: String, query: String): String =
        template.replace("%s", URLEncoder.encode(query, "UTF-8"))

    /**
     * Turns address-bar input into a loadable URL. Only http(s) are ever returned for navigation;
     * javascript:, file:, content:, data:, intent: etc. are treated as search text, never executed.
     */
    fun resolve(input: String, searchTemplate: String, httpsFirst: Boolean): String {
        val t = input.trim()
        if (t.isEmpty() || t.equals("about:blank", true)) return "about:blank"
        if (t.startsWith("http://", true) || t.startsWith("https://", true)) {
            return if (isWebUrl(t)) t else searchUrl(searchTemplate, t)
        }
        if (!t.contains(' ') && looksLikeHost(t)) {
            val u = (if (httpsFirst && !isLocalHost(t)) "https://" else "http://") + t
            if (isWebUrl(u)) return u
        }
        return searchUrl(searchTemplate, t)
    }

    private fun hostPart(t: String) = t.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
    fun isLocalHost(t: String): Boolean {
        val h = hostPart(t).lowercase()
        return h == "localhost" || h.endsWith(".local") || Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(h)
    }
    fun looksLikeHost(t: String): Boolean {
        if (t.contains("://") || t.contains('@')) return false
        val h = hostPart(t).lowercase()
        if (h == "localhost") return true
        if (Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(h)) return true
        return Regex("""([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}""").matches(h)
    }

    /** http -> https (same host/path). Returns null if the URL is not plain http. */
    fun upgradeToHttps(url: String): String? =
        if (url.startsWith("http://", true)) "https://" + url.substring(7) else null

    /** Hostname with an obvious look-alike flag (punycode / mixed script) for the link preview. */
    fun isPunycode(host: String) = host.split('.').any { it.startsWith("xn--") }
}
