package com.zaeem.browser.data

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Real counters only. In memory; reset by erase. Lifetime tracker count is a bare number in Prefs. */
object Stats {
    val trackersBlocked = AtomicInteger()
    val byHost = ConcurrentHashMap<String, AtomicInteger>()
    val secureNavigations = AtomicInteger()
    val insecureNavigations = AtomicInteger()
    val sslErrorsBlocked = AtomicInteger()
    val externalLaunchesBlocked = AtomicInteger()
    val httpsUpgrades = AtomicInteger()
    private val unsavedLifetime = AtomicInteger()

    fun blocked(host: String) {
        trackersBlocked.incrementAndGet(); unsavedLifetime.incrementAndGet()
        byHost.computeIfAbsent(host) { AtomicInteger() }.incrementAndGet()
    }

    /** Flush bare count to prefs (called on pause). */
    fun flush(p: Prefs) { val n = unsavedLifetime.getAndSet(0); if (n > 0) p.lifetimeBlocked = p.lifetimeBlocked + n }

    fun resetSession() {
        trackersBlocked.set(0); byHost.clear(); secureNavigations.set(0); insecureNavigations.set(0)
        sslErrorsBlocked.set(0); externalLaunchesBlocked.set(0); httpsUpgrades.set(0)
    }

    fun securePercent(): Int? {
        val s = secureNavigations.get(); val i = insecureNavigations.get()
        return if (s + i == 0) null else (100 * s) / (s + i)
    }
}
