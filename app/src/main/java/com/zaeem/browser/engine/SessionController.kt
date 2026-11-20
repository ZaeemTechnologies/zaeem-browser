package com.zaeem.browser.engine

import android.os.Handler
import android.os.Looper
import com.zaeem.browser.ZaeemApp
import com.zaeem.browser.core.SessionDurations
import com.zaeem.browser.core.TimedSession
import com.zaeem.browser.data.Prefs
import java.util.UUID

/**
 * Self-destructing sessions. A session is an ephemeral Private Space (own engine profile) plus a deadline.
 * The profile never survives a process restart: Engine.init deletes every "eph_" profile at next launch,
 * so crashes, force-stops and reboots all end the session's data.
 */
class SessionController(private val app: ZaeemApp) {
    var current: TimedSession? = null; private set
    var space: Prefs.Space? = null; private set
    var onTick: (() -> Unit)? = null
    var onEnded: (() -> Unit)? = null
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { check(); if (current != null) h.postDelayed(this, 1000) }
    }

    fun start(minutes: Int): Prefs.Space {
        if (current != null) end()
        val s = Prefs.Space(UUID.randomUUID().toString().take(8), "Session ${SessionDurations.format(minutes * 60_000L)}", "Temporary", false, true)
        val l = app.prefs.spaces(); l.add(s); app.prefs.saveSpaces(l)
        space = s
        current = TimedSession(System.currentTimeMillis(), minutes * 60_000L)
        app.prefs.timedSession = current!!.encode()
        h.removeCallbacks(tick); h.post(tick)
        return s
    }

    fun remaining(): Long = current?.remaining(System.currentTimeMillis()) ?: 0

    fun check() { val c = current ?: return; if (c.expired(System.currentTimeMillis())) end() else onTick?.invoke() }

    /** Ends the session: close its tabs, clear its data, revoke its permissions, drop the space. */
    fun end() {
        val s = space ?: run { current = null; return }
        app.eraser.wipeSpace(s)
        app.perms.revokeSession()
        val l = app.prefs.spaces(); l.removeAll { it.id == s.id }; app.prefs.saveSpaces(l)
        cancelWithoutWipe()
        app.host?.onTabsChanged()
        onEnded?.invoke()
    }

    fun cancelWithoutWipe() { current = null; space = null; app.prefs.timedSession = null; h.removeCallbacks(tick) }
}
