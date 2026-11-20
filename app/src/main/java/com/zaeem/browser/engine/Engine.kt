package com.zaeem.browser.engine

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.zaeem.browser.data.Prefs
import java.io.File

/**
 * Chromium (Android System WebView) with one named WebView Profile per Private Space.
 * A profile has its own cookies, storage, cache and permissions, enforced by the engine (not just visual grouping).
 */
object Engine {
    @Volatile var multiProfile = false; private set
    var sweepSummary = "not run"; private set

    fun profileName(spaceId: String, ephemeral: Boolean) = (if (ephemeral) "eph_" else "space_") + spaceId

    /**
     * Must run before any WebView exists in this process: the engine refuses to delete a profile that has been
     * loaded. Deletes ephemeral profiles (timed sessions), profiles queued for deletion, and, when
     * "clear on exit" is on, every space profile, so data does not outlive the app even after a crash or reboot.
     */
    fun init(prefs: Prefs) {
        multiProfile = try { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) } catch (e: Throwable) { false }
        if (!multiProfile) { sweepSummary = "Isolation unavailable: this WebView version is too old for profiles"; return }
        var deleted = 0; var failed = 0
        val store = ProfileStore.getInstance()
        val pending = prefs.pendingDeletes()
        val wipeSpaces = prefs.config().clearOnExit
        val names = try { store.allProfileNames } catch (e: Exception) { emptyList() }
        val stillPending = mutableSetOf<String>()
        for (n in names) {
            if (n == Profile.DEFAULT_PROFILE_NAME) continue
            val due = n.startsWith("eph_") || n in pending || (wipeSpaces && n.startsWith("space_"))
            if (!due) continue
            try { if (store.deleteProfile(n)) deleted++ } catch (e: Exception) { failed++; stillPending.add(n) }
        }
        prefs.setPendingDeletes(stillPending)
        prefs.timedSession = null
        sweepSummary = "startup sweep: $deleted profile(s) deleted, $failed failed"
    }

    fun profile(name: String): Profile? =
        if (multiProfile) ProfileStore.getInstance().getOrCreateProfile(name) else null

    fun cookieManager(name: String): CookieManager = profile(name)?.cookieManager ?: CookieManager.getInstance()

    fun bind(wv: WebView, name: String) { if (multiProfile) WebViewCompat.setProfile(wv, name) }

    fun webViewDirs(ctx: Context): List<File> = listOf(File(ctx.dataDir, "app_webview"), File(ctx.dataDir, "app_textures"))
        .filter { it.exists() }

    fun dirSize(f: File): Long = if (f.isFile) f.length() else f.listFiles()?.sumOf { dirSize(it) } ?: 0L

    fun webViewDataBytes(ctx: Context): Long = webViewDirs(ctx).sumOf { dirSize(it) }
}
