package com.example.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM 256-bit payload encryption and decryption manager for LAN socket communication.
 */
object CryptoManager {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH_BYTES = 12

    // Default 256-bit LAN pre-shared secret key (derived deterministically for local network peer pairing)
    private val DEFAULT_KEY_BYTES = byteArrayOf(
        0x2B.toByte(), 0x7E.toByte(), 0x15.toByte(), 0x16.toByte(),
        0x28.toByte(), 0xAE.toByte(), 0xD2.toByte(), 0xA6.toByte(),
        0xAB.toByte(), 0xF7.toByte(), 0x15.toByte(), 0x88.toByte(),
        0x09.toByte(), 0xCF.toByte(), 0x4F.toByte(), 0x3C.toByte(),
        0x67.toByte(), 0x98.toByte(), 0xA2.toByte(), 0x11.toByte(),
        0x33.toByte(), 0x24.toByte(), 0x55.toByte(), 0x66.toByte(),
        0x77.toByte(), 0x88.toByte(), 0x99.toByte(), 0x00.toByte(),
        0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte()
    )

    private val secretKey = SecretKeySpec(DEFAULT_KEY_BYTES, ALGORITHM)

    /**
     * Encrypts plaintext string using AES-GCM and returns a Base64 string containing [IV (12 bytes) + Ciphertext].
     */
    fun encrypt(plainText: String): String {
        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherText.size)

        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts Base64 string containing [IV (12 bytes) + Ciphertext] back to plain string.
     */
    fun decrypt(encryptedBase64: String): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        if (combined.size < IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Invalid encrypted payload size")
        }

        val iv = ByteArray(IV_LENGTH_BYTES)
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES)

        val cipherTextSize = combined.size - IV_LENGTH_BYTES
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(combined, IV_LENGTH_BYTES, cipherText, 0, cipherTextSize)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val plainTextBytes = cipher.doFinal(cipherText)
        return String(plainTextBytes, Charsets.UTF_8)
    }

    /**
     * Encrypt raw byte array (for images and file attachments).
     */
    fun encryptBytes(data: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherText = cipher.doFinal(data)
        val combined = ByteArray(iv.size + cipherText.size)

        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return combined
    }

    /**
     * Decrypt raw byte array (for images and file attachments).
     */
    fun decryptBytes(combined: ByteArray): ByteArray {
        if (combined.size < IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Invalid payload size")
        }

        val iv = ByteArray(IV_LENGTH_BYTES)
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES)

        val cipherTextSize = combined.size - IV_LENGTH_BYTES
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(combined, IV_LENGTH_BYTES, cipherText, 0, cipherTextSize)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(cipherText)
    }
}
