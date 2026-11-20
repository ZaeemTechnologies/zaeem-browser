package com.zaeem.browser.security

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import com.zaeem.browser.core.*
import com.zaeem.browser.data.Prefs

class AppLock(private val prefs: Prefs) {
    sealed class Result {
        object Ok : Result()
        object Wrong : Result()
        class LockedOut(val remainingMs: Long) : Result()
    }

    /** Runtime state only; always starts locked when a lock is configured. */
    @Volatile var unlocked = false
    private var backgroundedAt: Long? = null

    fun isConfigured(): Boolean = prefs.lockEnabled && prefs.secretBlob != null
    fun needsUnlock(): Boolean = isConfigured() && !unlocked

    fun setSecret(type: String, secret: CharArray): Boolean {
        val ok = if (type == "pin") SecretHasher.isValidPin(String(secret)) else SecretHasher.isValidPassword(String(secret))
        if (!ok) return false
        val rec = SecretHasher.create(secret)
        prefs.secretBlob = KeystoreBox.encrypt(rec.encode().toByteArray())
        prefs.lockType = type; prefs.lockEnabled = true
        prefs.failedAttempts = 0; unlocked = true
        return true
    }

    fun disable() { prefs.secretBlob = null; prefs.lockEnabled = false; prefs.failedAttempts = 0; unlocked = true }

    fun lockoutRemaining(now: Long = System.currentTimeMillis()) =
        LockoutPolicy.remainingMillis(prefs.failedAttempts, prefs.lastFailureAt, now)

    fun verify(secret: CharArray, now: Long = System.currentTimeMillis()): Result {
        val rem = lockoutRemaining(now)
        if (rem > 0) return Result.LockedOut(rem)
        val rec = prefs.secretBlob?.let { KeystoreBox.decrypt(it) }?.let { SecretRecord.decode(String(it)) }
        if (rec != null && SecretHasher.verify(secret, rec)) {
            prefs.failedAttempts = 0; unlocked = true; return Result.Ok
        }
        prefs.failedAttempts = prefs.failedAttempts + 1; prefs.lastFailureAt = now
        val after = lockoutRemaining(now)
        return if (after > 0) Result.LockedOut(after) else Result.Wrong
    }

    fun lockNow() { unlocked = false }

    fun onBackground() { backgroundedAt = System.currentTimeMillis() }
    fun clearBackground() { backgroundedAt = null }

    /** Returns true if the app must be locked now. */
    fun onForeground(): Boolean {
        val should = LockPolicy.shouldLock(isConfigured(), backgroundedAt, System.currentTimeMillis(), prefs.lockTimeoutMs)
        backgroundedAt = null
        if (should) unlocked = false
        return needsUnlock()
    }

    // ---- biometrics (platform BiometricPrompt; a UI gate in addition to the PIN, never a replacement for it)
    fun biometricAvailable(a: Activity): Boolean {
        if (!prefs.biometricEnabled) return false
        val bm = a.getSystemService(BiometricManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 30)
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        else @Suppress("DEPRECATION") (bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS)
    }

    fun biometricPrompt(a: Activity, title: String, onSuccess: () -> Unit, onUsePin: () -> Unit) {
        val ex = a.mainExecutor
        val b = BiometricPrompt.Builder(a).setTitle(title).setSubtitle("ZAEEM BROWSER")
            .setNegativeButton("Use ${if (prefs.lockType == "pin") "PIN" else "password"}", ex) { _, _ -> onUsePin() }
        if (Build.VERSION.SDK_INT >= 30) b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        b.build().authenticate(CancellationSignal(), ex, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult?) { unlocked = true; onSuccess() }
            override fun onAuthenticationError(code: Int, msg: CharSequence?) { onUsePin() }
        })
    }
}
