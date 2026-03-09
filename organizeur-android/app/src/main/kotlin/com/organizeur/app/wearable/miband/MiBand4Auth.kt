package com.organizeur.app.wearable.miband

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Mi Band 4 authentication protocol.
 *
 * The auth flow is:
 * 1. Send the auth key to the band (AUTH_SEND_KEY + 16 bytes key)
 * 2. Request a random number from the band (AUTH_REQUEST_RANDOM)
 * 3. Band responds with 16 random bytes
 * 4. Encrypt the random bytes with AES-ECB using the auth key
 * 5. Send encrypted result back (AUTH_SEND_ENCRYPTED + 16 bytes encrypted)
 * 6. Band responds with success or failure
 */
object MiBand4Auth {

    fun buildSendKeyCommand(authKey: ByteArray): ByteArray {
        return MiBand4Constants.AUTH_SEND_KEY + authKey
    }

    fun buildRequestRandomCommand(): ByteArray {
        return MiBand4Constants.AUTH_REQUEST_RANDOM
    }

    fun buildEncryptedResponse(randomData: ByteArray, authKey: ByteArray): ByteArray {
        val encrypted = encryptAES(randomData, authKey)
        return MiBand4Constants.AUTH_SEND_ENCRYPTED + encrypted
    }

    private fun encryptAES(data: ByteArray, key: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(data)
    }

    /**
     * Parse an auth key string (32 hex characters) into a 16-byte array.
     * Returns null if the string is invalid.
     */
    fun parseAuthKey(hexString: String): ByteArray? {
        val cleaned = hexString.trim().replace(" ", "").replace(":", "").replace("-", "")
        if (cleaned.length != 32) return null
        return try {
            cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * Parse the auth characteristic notification to determine the auth state.
     */
    fun parseAuthResponse(value: ByteArray): AuthResult {
        if (value.size < 3 || value[0] != MiBand4Constants.AUTH_RESPONSE) {
            return AuthResult.Unknown
        }

        // Format: [0x10] [step] [status] [data...]
        // step: 0x01=send key, 0x02=request random, 0x03=send encrypted
        // status: 0x01 or 0x81 = success, 0x04 = fail
        val step = value[1].toInt() and 0xFF
        val status = value[2].toInt() and 0xFF
        val success = status != 0x04

        return when (step) {
            0x01 -> {
                if (success) AuthResult.KeySent
                else AuthResult.Failed("Send key rejected")
            }
            0x02 -> {
                if (value.size >= 19 && success) {
                    AuthResult.RandomReceived(value.copyOfRange(3, 19))
                } else {
                    AuthResult.Failed("Random request rejected")
                }
            }
            0x03 -> {
                if (success) AuthResult.Authenticated
                else AuthResult.Failed("Encryption verification failed")
            }
            else -> AuthResult.Unknown
        }
    }
}

sealed class AuthResult {
    data object KeySent : AuthResult()
    data class RandomReceived(val randomData: ByteArray) : AuthResult() {
        override fun equals(other: Any?) = other is RandomReceived && randomData.contentEquals(other.randomData)
        override fun hashCode() = randomData.contentHashCode()
    }
    data object Authenticated : AuthResult()
    data class Failed(val reason: String) : AuthResult()
    data object Unknown : AuthResult()
}
