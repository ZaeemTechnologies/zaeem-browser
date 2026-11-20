package com.zaeem.browser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process

/** Runs in the ":restart" process: kills the main process, then starts the app again in a new one. */
class RestartActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pid = intent.getIntExtra("pid", -1)
        @Suppress("DEPRECATION") val next = intent.getParcelableExtra<Intent>("next")
        if (pid > 0) Process.killProcess(pid)
        if (next != null) startActivity(next)
        finish()
        Runtime.getRuntime().exit(0)
    }
}
