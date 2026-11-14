package com.example.safora.core.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EvidenceCipher {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12

    // Demo master key for on-device mock/evidence encryption
    private val DEMO_KEY_BYTES = byteArrayOf(
        0x53.toByte(), 0x41.toByte(), 0x46.toByte(), 0x4F.toByte(),
        0x52.toByte(), 0x41.toByte(), 0x5F.toByte(), 0x53.toByte(),
        0x45.toByte(), 0x43.toByte(), 0x55.toByte(), 0x52.toByte(),
        0x45.toByte(), 0x5F.toByte(), 0x4B.toByte(), 0x45.toByte(),
        0x59.toByte(), 0x32.toByte(), 0x30.toByte(), 0x32.toByte(),
        0x36.toByte(), 0x5F.toByte(), 0x5F.toByte(), 0x41.toByte(),
        0x45.toByte(), 0x53.toByte(), 0x47.toByte(), 0x43.toByte(),
        0x4D.toByte(), 0x32.toByte(), 0x35.toByte(), 0x36.toByte()
    )

    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(IV_LENGTH_BYTES).apply { SecureRandom().nextBytes(this) }
        val secretKey = SecretKeySpec(DEMO_KEY_BYTES, "AES")
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val encryptedBytes = cipher.doFinal(data)
        // Prepend IV to ciphertext
        return iv + encryptedBytes
    }

    fun decrypt(encryptedPayload: ByteArray): ByteArray {
        if (encryptedPayload.size < IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Payload too short")
        }
        val iv = encryptedPayload.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = encryptedPayload.copyOfRange(IV_LENGTH_BYTES, encryptedPayload.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        val secretKey = SecretKeySpec(DEMO_KEY_BYTES, "AES")
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
