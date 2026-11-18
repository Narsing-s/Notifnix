package com.notifnix.app

import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Stores a salted PBKDF2 hash instead of a recoverable PIN. */
internal object PinSecurity {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256

    fun createRecord(pin: String): String {
        require(pin.length in 4..6 && pin.all(Char::isDigit)) { "PIN must contain 4–6 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        return salt.toHex() + ":" + hash.toHex()
    }

    fun verify(pin: String, record: String): Boolean {
        if (pin.length !in 4..6 || !pin.all(Char::isDigit)) return false
        val parts = record.split(":")
        if (parts.size != 2) return false
        val salt = parts[0].fromHex() ?: return false
        val expected = parts[1].fromHex() ?: return false
        if (salt.size != 16 || expected.size != KEY_LENGTH_BITS / 8) return false
        return MessageDigest.isEqual(expected, derive(pin, salt))
    }

    fun loadOrMigrate(prefs: SharedPreferences): String? {
        prefs.getString("app_pin_hash", null)?.takeIf { it.isNotBlank() }?.let { return it }
        val legacyPin = prefs.getString("app_pin", null)?.takeIf { it.isNotBlank() } ?: return null
        if (legacyPin.length !in 4..6 || !legacyPin.all(Char::isDigit)) {
            prefs.edit().remove("app_pin").apply()
            return null
        }
        val record = createRecord(legacyPin)
        prefs.edit().putString("app_pin_hash", record).remove("app_pin").apply()
        return record
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun String.fromHex(): ByteArray? {
        if (length % 2 != 0 || any { it.digitToIntOrNull(16) == null }) return null
        return runCatching { chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
    }
}