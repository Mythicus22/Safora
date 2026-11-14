package com.example.safora.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Shamir's Secret Sharing over GF(256) (Rijndael's finite field polynomial 0x11b).
 * Computes k-of-n threshold secret sharing on arbitrary byte arrays (like 256-bit AES keys).
 */
object ShamirSecretSharing {
    private val random = SecureRandom()

    // GF(256) exp and log tables with generator 0x03 and irreducible polynomial 0x11b
    private val expTable = IntArray(512)
    private val logTable = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            expTable[i] = x
            expTable[i + 255] = x
            logTable[x] = i
            x = x xor (x shl 1)
            if ((x and 0x100) != 0) {
                x = x xor 0x11b
            }
        }
        logTable[0] = 0
    }

    private fun gfAdd(a: Int, b: Int): Int = a xor b

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return expTable[logTable[a] + logTable[b]]
    }

    private fun gfDiv(a: Int, b: Int): Int {
        if (b == 0) throw ArithmeticException("Division by zero in GF(256)")
        if (a == 0) return 0
        return expTable[(logTable[a] - logTable[b] + 255) % 255]
    }

    data class Share(val index: Int, val data: ByteArray) {
        fun toHex(): String = "%02x".format(index) + ":" + data.joinToString("") { "%02x".format(it) }

        companion object {
            fun fromHex(hex: String): Share {
                val parts = hex.split(":")
                val idx = parts[0].toInt(16)
                val bytes = parts[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                return Share(idx, bytes)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Share
            return index == other.index && data.contentEquals(other.data)
        }

        override fun hashCode(): Int = 31 * index + data.contentHashCode()
    }

    /**
     * Splits secret into n shares such that any k (threshold) shares can reconstruct it.
     * indices: 1..n (0 is reserved for secret evaluation)
     */
    fun split(secret: ByteArray, threshold: Int, totalShares: Int): List<Share> {
        require(threshold in 1..totalShares) { "Threshold must be between 1 and totalShares" }
        require(totalShares < 256) { "Total shares cannot exceed 255 in GF(256)" }

        val sharesData = Array(totalShares) { ByteArray(secret.size) }

        for (byteIdx in secret.indices) {
            val secretByte = secret[byteIdx].toInt() and 0xFF
            // Polynomial coefficients: a_0 = secretByte, a_1..a_{k-1} random
            val poly = IntArray(threshold)
            poly[0] = secretByte
            for (c in 1 until threshold) {
                poly[c] = random.nextInt(256)
            }

            // Evaluate poly(x) for x = 1..totalShares
            for (shareIdx in 0 until totalShares) {
                val x = shareIdx + 1
                var y = 0
                var xPow = 1
                for (coeff in poly) {
                    y = gfAdd(y, gfMul(coeff, xPow))
                    xPow = gfMul(xPow, x)
                }
                sharesData[shareIdx][byteIdx] = y.toByte()
            }
        }

        return (0 until totalShares).map { i -> Share(i + 1, sharesData[i]) }
    }

    /**
     * Reconstructs the secret from threshold shares using Lagrange polynomial interpolation at x = 0.
     */
    fun reconstruct(shares: List<Share>, threshold: Int): ByteArray {
        require(shares.size >= threshold) { "Need at least $threshold shares to reconstruct secret" }
        val usedShares = shares.distinctBy { it.index }.take(threshold)
        val secretSize = usedShares[0].data.size
        val secret = ByteArray(secretSize)

        for (byteIdx in 0 until secretSize) {
            var secretByte = 0
            for (i in 0 until threshold) {
                val xi = usedShares[i].index
                val yi = usedShares[i].data[byteIdx].toInt() and 0xFF

                // Lagrange basis polynomial L_i(0) = \prod_{j != i} (0 - x_j) / (x_i - x_j)
                // In GF(256), -x == x
                var li = 1
                for (j in 0 until threshold) {
                    if (i != j) {
                        val xj = usedShares[j].index
                        val num = xj
                        val den = gfAdd(xi, xj)
                        li = gfMul(li, gfDiv(num, den))
                    }
                }
                secretByte = gfAdd(secretByte, gfMul(yi, li))
            }
            secret[byteIdx] = secretByte.toByte()
        }

        return secret
    }
}
