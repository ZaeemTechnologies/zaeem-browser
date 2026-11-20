package com.zaeem.browser.engine

import android.content.Context
import com.zaeem.browser.core.TrackerMatcher
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Seed list ships in assets; an optional larger list can be downloaded on demand (user-initiated, HTTPS only,
 * from a public hosts-format list on GitHub: free, no account). No automatic background fetching.
 */
class Blocklist(private val ctx: Context) {
    val matcher = TrackerMatcher()
    var source = "seed"; private set
    private val file get() = File(ctx.filesDir, "blocklist.txt")

    fun load() {
        val seed = ctx.assets.open("blocklist_seed.txt").bufferedReader().use { it.readText() }
        matcher.addAll(TrackerMatcher.parse(seed))
        if (file.exists()) runCatching {
            matcher.addAll(TrackerMatcher.parse(file.readText())); source = "seed + downloaded"
        }
    }

    /** Blocking call: run off the main thread. Returns number of entries or throws. */
    fun download(url: String = DEFAULT_URL): Int {
        require(url.startsWith("https://")) { "HTTPS only" }
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "ZAEEM-Browser-ListUpdater")
        if (c.responseCode != 200) throw RuntimeException("HTTP ${c.responseCode}")
        val text = c.inputStream.bufferedReader().use { it.readText() }
        val parsed = TrackerMatcher.parse(text)
        if (parsed.size < 100) throw RuntimeException("List looks wrong (${parsed.size} entries); ignored")
        file.writeText(text)
        matcher.addAll(parsed); source = "seed + downloaded"
        return parsed.size
    }

    fun clearDownloaded() { file.delete() }

    companion object {
        const val DEFAULT_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    }
}
