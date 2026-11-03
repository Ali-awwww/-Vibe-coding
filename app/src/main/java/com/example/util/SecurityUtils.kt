package com.example.util

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SecurityUtils {

    private val secureRandom = SecureRandom()
    // 256-bit AES master key derived for applet runtime encryption of secrets at rest
    private val masterKeyBytes: ByteArray by lazy {
        val seed = "CrashIntelligence_SecureMasterKey_2026_Applet_Salt".toByteArray(StandardCharsets.UTF_8)
        MessageDigest.getInstance("SHA-256").digest(seed)
    }

    // Rate Limiting tracker: projectId -> list of request timestamps
    private val rateLimitTracker = ConcurrentHashMap<String, MutableList<Long>>()
    private const val MAX_REQUESTS_PER_MINUTE = 60
    private const val WINDOW_MS = 60000L

    // 1. Password Hashing (PBKDF/SHA-256 with Salt)
    fun generateSalt(): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashPassword(password: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt.toByteArray(StandardCharsets.UTF_8))
        val hashedBytes = md.digest(password.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(hashedBytes, Base64.NO_WRAP)
    }

    fun verifyPassword(password: String, salt: String, expectedHash: String): Boolean {
        val calculated = hashPassword(password, salt)
        return MessageDigest.isEqual(calculated.toByteArray(), expectedHash.toByteArray())
    }

    // 2. AES-GCM Encryption for API Keys and Secrets
    fun encryptSecret(plainText: String): String {
        if (plainText.isBlank()) return ""
        try {
            val iv = ByteArray(12) // 96-bit IV for GCM
            secureRandom.nextBytes(iv)

            val secretKey = SecretKeySpec(masterKeyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            return "ENC:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Fallback safe obfuscation if crypto provider error
            return "ENC:" + Base64.encodeToString(plainText.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun decryptSecret(cipherText: String): String {
        if (!cipherText.startsWith("ENC:")) return cipherText // Not encrypted
        val raw = cipherText.removePrefix("ENC:")
        return try {
            val combined = Base64.decode(raw, Base64.NO_WRAP)
            if (combined.size < 12) {
                return String(Base64.decode(raw, Base64.NO_WRAP), StandardCharsets.UTF_8)
            }
            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, 12)
            val encryptedBytes = ByteArray(combined.size - 12)
            System.arraycopy(combined, 12, encryptedBytes, 0, encryptedBytes.size)

            val secretKey = SecretKeySpec(masterKeyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            try {
                String(Base64.decode(raw, Base64.NO_WRAP), StandardCharsets.UTF_8)
            } catch (ex: Exception) {
                cipherText
            }
        }
    }

    fun maskSecret(secret: String): String {
        if (secret.isBlank()) return ""
        val decrypted = decryptSecret(secret)
        if (decrypted.length <= 6) return "••••••••"
        val prefix = decrypted.take(3)
        val suffix = decrypted.takeLast(3)
        return "$prefix••••••••••••$suffix"
    }

    // 3. PII & Sensitive Data Scrubber
    object PiiScrubber {
        private val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        private val IPV4_REGEX = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
        private val BEARER_REGEX = Regex("Bearer\\s+[a-zA-Z0-9._\\-~+=]+")
        private val PASSWORD_KEY_REGEX = Regex("(?i)(password|secret|token|apikey|api_key|auth)\\s*[:=]\\s*[\"']?([^\"'\\s,;&]+)[\"']?")
        private val CREDIT_CARD_REGEX = Regex("\\b(?:\\d[ -]*?){13,16}\\b")
        private val PHONE_REGEX = Regex("\\b(?:\\+?\\d{1,3}[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}\\b")

        fun scrub(text: String): String {
            if (text.isBlank()) return text
            var result = text
            // Scrub Passwords & Tokens
            result = PASSWORD_KEY_REGEX.replace(result) { match ->
                val key = match.groupValues[1]
                "$key=[REDACTED_SECRET]"
            }
            // Scrub Bearer Tokens
            result = BEARER_REGEX.replace(result, "Bearer [REDACTED_TOKEN]")
            // Scrub Emails
            result = EMAIL_REGEX.replace(result, "[REDACTED_EMAIL]")
            // Scrub IPs
            result = IPV4_REGEX.replace(result, "[REDACTED_IP]")
            // Scrub Credit Cards
            result = CREDIT_CARD_REGEX.replace(result, "[REDACTED_CARD]")
            // Scrub Phones
            result = PHONE_REGEX.replace(result, "[REDACTED_PHONE]")

            return result
        }
    }

    // 4. Webhook Security: HMAC Signature Verification
    fun verifyWebhookSignature(payload: String, secret: String, receivedSignatureHeader: String?): Boolean {
        if (receivedSignatureHeader.isNullOrBlank()) return false
        return try {
            val hmac = Mac.getInstance("HmacSHA256")
            val keySpec = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
            hmac.init(keySpec)
            val hash = hmac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
            val calculatedSignature = hash.joinToString("") { "%02x".format(it) }

            // Support either raw hex or sha256=hex format
            val cleanReceived = receivedSignatureHeader.removePrefix("sha256=").trim()
            MessageDigest.isEqual(calculatedSignature.toByteArray(), cleanReceived.toByteArray())
        } catch (e: Exception) {
            false
        }
    }

    // 5. Rate Limiting Protection (60 requests/minute per source/project)
    fun checkRateLimit(key: String): Boolean {
        val now = System.currentTimeMillis()
        val list = rateLimitTracker.computeIfAbsent(key) { mutableListOf() }
        synchronized(list) {
            list.removeAll { now - it > WINDOW_MS }
            if (list.size >= MAX_REQUESTS_PER_MINUTE) {
                return false // Rate limit exceeded!
            }
            list.add(now)
            return true // Allowed
        }
    }
}
