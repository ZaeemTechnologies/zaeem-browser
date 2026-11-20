package com.zaeem.browser.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2-HMAC-SHA256 record. Uses the platform JCE only: no custom cryptography. */
class SecretRecord(val salt: ByteArray, val iterations: Int, val hash: ByteArray) {
    fun encode(): String = "v1:$iterations:${toHex(salt)}:${toHex(hash)}"

    companion object {
        fun decode(s: String): SecretRecord? {
            val p = s.split(":")
            if (p.size != 4 || p[0] != "v1") return null
            val it = p[1].toIntOrNull() ?: return null
            if (it < 10_000) return null
            val salt = fromHex(p[2]) ?: return null
            val hash = fromHex(p[3]) ?: return null
            return SecretRecord(salt, it, hash)
        }
        fun toHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
        fun fromHex(s: String): ByteArray? {
            if (s.isEmpty() || s.length % 2 != 0) return null
            return try {
                ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
            } catch (e: NumberFormatException) { null }
        }
    }
}

object SecretHasher {
    const val DEFAULT_ITERATIONS = 600_000   // OWASP guidance for PBKDF2-HMAC-SHA256
    private const val KEY_BITS = 256

    private fun derive(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
    }

    fun create(secret: CharArray, rng: SecureRandom = SecureRandom(), iterations: Int = DEFAULT_ITERATIONS): SecretRecord {
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        return SecretRecord(salt, iterations, derive(secret, salt, iterations))
    }

    fun verify(secret: CharArray, rec: SecretRecord): Boolean =
        MessageDigest.isEqual(derive(secret, rec.salt, rec.iterations), rec.hash) // constant time

    /** PINs are 4 to 12 digits; passwords 8 to 128 chars. */
    fun isValidPin(p: String) = p.length in 4..12 && p.all { it in '0'..'9' }
    fun isValidPassword(p: String) = p.length in 8..128
}
