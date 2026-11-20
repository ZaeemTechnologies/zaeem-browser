package com.zaeem.browser

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.zaeem.browser.core.EraseMode

/** Best-effort cleanup when the user swipes the app away. The next-launch sweep in Engine.init is the guarantee. */
class CleanupService : Service() {
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_NOT_STICKY
    override fun onTaskRemoved(rootIntent: Intent?) {
        val app = application as ZaeemApp
        if (app.prefs.config().clearOnExit) {
            app.sessions.cancelWithoutWipe()
            app.eraser.erase(EraseMode.COMPLETE.scopes) { stopSelf() }
        } else stopSelf()
    }
}
