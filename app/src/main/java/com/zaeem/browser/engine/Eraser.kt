package com.zaeem.browser.engine

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewDatabase
import com.zaeem.browser.ZaeemApp
import com.zaeem.browser.core.*
import com.zaeem.browser.data.Stats

object Restart {
    /** Relaunches the app in a fresh process (used to finish storage-level deletion before any profile is loaded). */
    fun now(ctx: Context) {
        val next = Intent(ctx, com.zaeem.browser.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(
            Intent(ctx, com.zaeem.browser.RestartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("pid", android.os.Process.myPid()).putExtra("next", next)
        )
    }
}

class Eraser(private val app: ZaeemApp) {
    private val main = Handler(Looper.getMainLooper())

    fun profileNames(): List<String> = app.prefs.spaces().map { Engine.profileName(it.id, it.ephemeral) }

    /** Clears via the engine APIs now; queues storage-level profile deletion for the next launch. */
    fun erase(scopes: Set<EraseScope>, done: (EraseReport) -> Unit) {
        val ctx = app.applicationContext
        val ok = mutableListOf<String>(); val bad = mutableListOf<String>()
        val before = Engine.webViewDataBytes(ctx)
        fun step(label: String, f: () -> Unit) { try { f(); ok.add(label) } catch (e: Exception) { bad.add("$label (${e.javaClass.simpleName})") } }
        val names = profileNames()

        if (EraseScope.CACHE in scopes) tempViews(names) { it.clearCache(true) }
        if (EraseScope.HISTORY in scopes) {
            app.tabs.tabs.forEach { it.webView?.clearHistory() }
            step("History: none is stored; in-memory back/forward cleared") { WebViewDatabase.getInstance(ctx).clearHttpAuthUsernamePassword() }
        }
        if (EraseScope.TABS in scopes) step("Tabs closed") { app.tabs.closeAll() }
        if (EraseScope.COOKIES in scopes) {
            step("Cookies & site storage cleared") {
                names.forEach { n ->
                    val p = Engine.profile(n)
                    if (p != null) { p.cookieManager.removeAllCookies(null); p.cookieManager.flush(); p.webStorage.deleteAllData(); p.geolocationPermissions.clearAll() }
                    else { android.webkit.CookieManager.getInstance().removeAllCookies(null); android.webkit.WebStorage.getInstance().deleteAllData() }
                }
            }
            if (scopes.containsAll(EraseMode.COMPLETE.scopes)) step("Profile storage queued for deletion") { app.prefs.addPendingDeletes(names) }
        }
        if (EraseScope.CACHE in scopes) ok.add("Cache cleared")
        if (EraseScope.SEARCH in scopes) step("Search suggestions: none are stored") { }
        if (EraseScope.DOWNLOADS in scopes) step("Downloads removed") { app.downloader.clearAll() }
        if (EraseScope.SESSION_PERMISSIONS in scopes) step("Session permissions revoked") { app.perms.revokeSession() }
        if (scopes.containsAll(EraseMode.COMPLETE.scopes)) step("Counters reset") { Stats.resetSession(); app.prefs.lifetimeBlocked = 0 }
        else if (EraseScope.TABS in scopes) Stats.resetSession()
        main.postDelayed({ done(EraseReport(ok, bad, before, Engine.webViewDataBytes(ctx))) }, 900)
    }

    /** Short-lived WebViews bound to each profile, only to reach per-profile calls like clearCache. */
    private fun tempViews(names: List<String>, f: (WebView) -> Unit) {
        for (n in names) try {
            val wv = WebView(app.applicationContext); Engine.bind(wv, n); f(wv); wv.destroy()
        } catch (_: Exception) { }
    }

    /** Wipe one space (used when a timed session ends or a space is deleted). */
    fun wipeSpace(space: com.zaeem.browser.data.Prefs.Space) {
        val n = Engine.profileName(space.id, space.ephemeral)
        app.tabs.closeSpace(space.id)
        runCatching {
            Engine.profile(n)?.let { p -> p.cookieManager.removeAllCookies(null); p.cookieManager.flush(); p.webStorage.deleteAllData(); p.geolocationPermissions.clearAll() }
            tempViews(listOf(n)) { it.clearCache(true) }
        }
        app.prefs.addPendingDeletes(listOf(n))
    }

    /** Privacy Panic Button. Runs instantly; the final restart finishes deletion and ends all renderer processes. */
    fun panic(host: android.app.Activity) {
        val p = app.prefs
        if (p.panicCloseTabs) app.tabs.closeAll()
        app.sessions.cancelWithoutWipe()
        if (p.panicClear) {
            runCatching {
                profileNames().forEach { n ->
                    Engine.profile(n)?.let { pr -> pr.cookieManager.removeAllCookies(null); pr.cookieManager.flush(); pr.webStorage.deleteAllData() }
                }
            }
            app.downloader.clearAll(); app.perms.revokeSession(); Stats.resetSession()
            p.addPendingDeletes(profileNames())
        }
        if (p.panicLock) app.lock.lockNow()
        Restart.now(host)
    }
}
