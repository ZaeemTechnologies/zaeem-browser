package com.zaeem.browser

import android.app.Application
import com.zaeem.browser.data.Prefs
import com.zaeem.browser.data.Vault
import com.zaeem.browser.engine.*
import com.zaeem.browser.security.AppLock

class ZaeemApp : Application() {
    lateinit var prefs: Prefs
    lateinit var lock: AppLock
    lateinit var blocklist: Blocklist
    lateinit var perms: PermissionStore
    lateinit var downloader: Downloader
    lateinit var vault: Vault
    lateinit var tabs: TabManager
    lateinit var eraser: Eraser
    lateinit var sessions: SessionController
    var host: BrowserHost? = null
    var startupNote: String = ""

    override fun onCreate() {
        super.onCreate()
        // The ":restart" helper process must not touch engine storage.
        if (getProcessName() != packageName) return
        prefs = Prefs(this)
        Engine.init(prefs)                       // before any WebView exists in this process
        startupNote = Engine.sweepSummary
        lock = AppLock(prefs)
        blocklist = Blocklist(this).also { it.load() }
        perms = PermissionStore(prefs)
        downloader = Downloader(this)
        vault = Vault(this)
        tabs = TabManager(this)
        eraser = Eraser(this)
        sessions = SessionController(this)
        // Remove ephemeral spaces left behind by a previous process (their profiles were just deleted).
        val keep = prefs.spaces().filter { !it.ephemeral }
        if (keep.size != prefs.spaces().size) prefs.saveSpaces(keep)
    }
}
