package com.zaeem.browser.core

enum class SensitiveKind(val label: String) {
    CARD("a payment-card number"),
    PRIVATE_KEY("a private key"),
    OTP("a one-time code"),
    SECRET("a password-like secret"),
}

object SensitiveDetector {
    fun luhn(digits: String): Boolean {
        var sum = 0; var alt = false
        for (c in digits.reversed()) {
            var n = c - '0'
            if (alt) { n *= 2; if (n > 9) n -= 9 }
            sum += n; alt = !alt
        }
        return sum % 10 == 0
    }

    fun classify(text: String): SensitiveKind? {
        val t = text.trim()
        if (t.isEmpty() || t.length > 4096) return null
        if ("-----BEGIN" in t && "PRIVATE KEY" in t) return SensitiveKind.PRIVATE_KEY
        val digits = t.filter { it.isDigit() }
        if (t.all { it.isDigit() || it == ' ' || it == '-' } && digits.length in 13..19 && luhn(digits)) return SensitiveKind.CARD
        if (t.length in 6..8 && t.all { it.isDigit() }) return SensitiveKind.OTP
        if (t.length in 8..64 && t.none { it.isWhitespace() }) {
            val classes = listOf(t.any { it.isLowerCase() }, t.any { it.isUpperCase() }, t.any { it.isDigit() },
                t.any { !it.isLetterOrDigit() }).count { it }
            if (classes >= 3 && !t.startsWith("http", true)) return SensitiveKind.SECRET
        }
        return null
    }
}
