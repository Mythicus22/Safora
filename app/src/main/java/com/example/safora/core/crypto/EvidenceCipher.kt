package com.example.safora.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EvidenceCipher {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12

    fun generate256BitKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }

    // Default key for testing when key shares are not initialized
    val DEFAULT_KEY_BYTES = byteArrayOf(
        0x53.toByte(), 0x41.toByte(), 0x4F.toByte(), 0x4F.toByte(),
        0x52.toByte(), 0x41.toByte(), 0x5F.toByte(), 0x53.toByte(),
        0x45.toByte(), 0x43.toByte(), 0x55.toByte(), 0x52.toByte(),
        0x45.toByte(), 0x5F.toByte(), 0x4B.toByte(), 0x45.toByte(),
        0x59.toByte(), 0x32.toByte(), 0x30.toByte(), 0x32.toByte(),
        0x36.toByte(), 0x5F.toByte(), 0x5F.toByte(), 0x41.toByte(),
        0x45.toByte(), 0x53.toByte(), 0x47.toByte(), 0x43.toByte(),
        0x4D.toByte(), 0x32.toByte(), 0x35.toByte(), 0x36.toByte()
    )

    fun encrypt(data: ByteArray, keyBytes: ByteArray = DEFAULT_KEY_BYTES): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(IV_LENGTH_BYTES).apply { SecureRandom().nextBytes(this) }
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val encryptedBytes = cipher.doFinal(data)
        // Prepend IV (12 bytes) to ciphertext
        return iv + encryptedBytes
    }

    fun decrypt(encryptedPayload: ByteArray, keyBytes: ByteArray = DEFAULT_KEY_BYTES): ByteArray {
        if (encryptedPayload.size < IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Payload too short for AES-GCM")
        }
        val iv = encryptedPayload.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = encryptedPayload.copyOfRange(IV_LENGTH_BYTES, encryptedPayload.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }

    fun hashSha256(data: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
