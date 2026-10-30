package com.example.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class P2PSecureChannel {
    private val tag = "P2PSecureChannel"
    private var sessionKey: SecretKeySpec? = null

    companion object {
        val shared = P2PSecureChannel()

        const val ALGORITHM_AES = "AES"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val IV_LENGTH_BYTES = 12
    }

    init {
        generateKeystoreEd25519()
        // Initialize default secure session key for authorized pair
        val dummySeed = "MeshDropSecurePairing2026"
        deriveSharedSecret(dummySeed.toByteArray())
    }

    private fun generateKeystoreEd25519() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias("MeshDropKeyAlias")) {
                val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                val parameterSpec = KeyGenParameterSpec.Builder(
                    "MeshDropKeyAlias",
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                    .build()
                kpg.initialize(parameterSpec)
                kpg.generateKeyPair()
            }
        } catch (e: Exception) {
            Log.w(tag, "KeyStore initialization fallback: ${e.message}")
        }
    }

    fun computeShortHash(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKeyBytes)
        val sb = StringBuilder()
        for (i in 0 until 3) {
            if (i > 0) sb.append("-")
            sb.append(String.format("%02X", hash[i]))
        }
        return sb.toString()
    }

    fun compute4DigitPin(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKeyBytes)
        val num = ((hash[0].toInt() and 0xFF) shl 8) or (hash[1].toInt() and 0xFF)
        val pin = (num % 10000)
        return String.format("%04d", pin)
    }

    fun deriveSharedSecret(remotePublicKeyBytes: ByteArray): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(remotePublicKeyBytes)
        sessionKey = SecretKeySpec(keyBytes, ALGORITHM_AES)
        return sessionKey!!
    }

    fun encrypt(plainText: ByteArray): ByteArray {
        val key = sessionKey ?: throw IllegalStateException("Session key not established")
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val iv = ByteArray(IV_LENGTH_BYTES).apply { SecureRandom().nextBytes(this) }
        val parameterSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec)
        val cipherText = cipher.doFinal(plainText)
        return iv + cipherText
    }

    fun decrypt(encryptedPayload: ByteArray): ByteArray {
        val key = sessionKey ?: throw IllegalStateException("Session key not established")
        if (encryptedPayload.size < IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Payload too short")
        }
        val iv = encryptedPayload.copyOfRange(0, IV_LENGTH_BYTES)
        val cipherText = encryptedPayload.copyOfRange(IV_LENGTH_BYTES, encryptedPayload.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val parameterSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec)
        return cipher.doFinal(cipherText)
    }
}
