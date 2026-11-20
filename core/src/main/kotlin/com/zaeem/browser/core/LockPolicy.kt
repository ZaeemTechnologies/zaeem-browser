package com.zaeem.browser.core

object LockoutPolicy {
    const val FREE_ATTEMPTS = 5
    private const val BASE_MS = 30_000L
    private const val MAX_MS = 3_600_000L

    /** Lockout length after [failed] consecutive failures (0 for the first 5). */
    fun delayMillis(failed: Int): Long {
        if (failed < FREE_ATTEMPTS) return 0
        val shift = (failed - FREE_ATTEMPTS).coerceAtMost(10)
        return minOf(BASE_MS * (1L shl shift), MAX_MS)
    }

    fun remainingMillis(failed: Int, lastFailureAt: Long, now: Long): Long =
        (lastFailureAt + delayMillis(failed) - now).coerceAtLeast(0)
}

object LockPolicy {
    /** timeoutMs: 0 = lock as soon as the app is minimised, -1 = never auto-lock. */
    fun shouldLock(lockEnabled: Boolean, backgroundedAt: Long?, now: Long, timeoutMs: Long): Boolean {
        if (!lockEnabled || timeoutMs < 0) return false
        if (backgroundedAt == null) return false
        return now - backgroundedAt >= timeoutMs
    }
}
