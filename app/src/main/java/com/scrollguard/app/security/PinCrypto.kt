package com.scrollguard.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

data class PinHashRecord(
    val saltBase64: String,
    val hashBase64: String,
    val iterations: Int = PBKDF2_ITERATIONS
) {
    companion object {
        const val PBKDF2_ITERATIONS = 120_000
        const val KEY_LENGTH_BITS = 256
        const val SALT_BYTES = 16
    }
}

@Singleton
class PinCrypto @Inject constructor() {

    private val secureRandom = SecureRandom()

    fun generateSalt(): ByteArray {
        val salt = ByteArray(PinHashRecord.SALT_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun hashPin(
        pin: CharArray,
        salt: ByteArray,
        iterations: Int = PinHashRecord.PBKDF2_ITERATIONS
    ): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, PinHashRecord.KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun createHashRecord(pin: CharArray): PinHashRecord {
        val salt = generateSalt()
        val derived = hashPin(pin, salt)
        return try {
            PinHashRecord(
                saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                hashBase64 = Base64.encodeToString(derived, Base64.NO_WRAP),
                iterations = PinHashRecord.PBKDF2_ITERATIONS
            )
        } finally {
            Arrays.fill(salt, 0.toByte())
            Arrays.fill(derived, 0.toByte())
        }
    }

    fun verifyPin(pin: CharArray, record: PinHashRecord): Boolean {
        val salt = Base64.decode(record.saltBase64, Base64.NO_WRAP)
        val expectedHash = Base64.decode(record.hashBase64, Base64.NO_WRAP)
        val candidateHash = hashPin(pin, salt, record.iterations)
        return try {
            MessageDigest.isEqual(expectedHash, candidateHash)
        } finally {
            Arrays.fill(salt, 0.toByte())
            Arrays.fill(expectedHash, 0.toByte())
            Arrays.fill(candidateHash, 0.toByte())
        }
    }

    fun generateRecoveryCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        val chars = CharArray(8) { idx ->
            alphabet[(bytes[idx].toInt() and 0xFF) % alphabet.length]
        }
        return String(chars).also {
            Arrays.fill(bytes, 0.toByte())
            Arrays.fill(chars, '\u0000')
        }
    }

    /**
     * Wraps a serialized record with an Android Keystore AES-256-GCM key when available.
     * Falls back cleanly if AndroidKeyStore is unavailable (e.g., JVM Robolectric tests).
     */
    fun sealWithKeystore(plainText: String): String {
        return runCatching {
            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val ctB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            "KS1:$ivB64:$ctB64"
        }.getOrElse {
            "RAW:$plainText"
        }
    }

    fun unsealWithKeystore(sealed: String): String? {
        if (sealed.startsWith("RAW:")) {
            return sealed.removePrefix("RAW:")
        }
        if (!sealed.startsWith("KS1:")) return null
        return runCatching {
            val parts = sealed.split(":", limit = 3)
            if (parts.size != 3) return null
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[2], Base64.NO_WRAP)
            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "scrollguard_pin_wrap_key"

        fun wipe(chars: CharArray) {
            Arrays.fill(chars, '\u0000')
        }
    }
}
