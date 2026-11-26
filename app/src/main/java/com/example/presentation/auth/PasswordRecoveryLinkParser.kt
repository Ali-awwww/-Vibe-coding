package com.example.presentation.auth

import com.example.auth.APP_AUTH_REDIRECT_HOST
import com.example.auth.APP_AUTH_REDIRECT_SCHEME
import com.example.domain.repository.PasswordRecoveryCredentials
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal sealed interface PasswordRecoveryLinkResult {
    data class Ready(val credentials: PasswordRecoveryCredentials) : PasswordRecoveryLinkResult
    data class Rejected(val message: String) : PasswordRecoveryLinkResult
}

/**
 * Accepts only the app's Firebase/deep-link password-recovery callback.
 * Supports Firebase Auth `oobCode` (mode=resetPassword) as well as legacy recovery payloads.
 * Credentials are never included in diagnostic messages.
 */
internal fun parsePasswordRecoveryLink(
    rawLink: String,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
): PasswordRecoveryLinkResult {
    val uri = try {
        URI(rawLink)
    } catch (_: Exception) {
        return PasswordRecoveryLinkResult.Rejected("رابط الاستعادة غير صالح أو منتهي الصلاحية.")
    }

    if (!uri.scheme.equals(APP_AUTH_REDIRECT_SCHEME, ignoreCase = true) ||
        !uri.host.equals(APP_AUTH_REDIRECT_HOST, ignoreCase = true) ||
        uri.path != PASSWORD_RECOVERY_PATH
    ) {
        return PasswordRecoveryLinkResult.Rejected("وجهة رابط الاستعادة لا تطابق التطبيق.")
    }

    val parameters = parseParameters(uri.rawFragment) + parseParameters(uri.rawQuery)
    if (!parameters["error"].isNullOrBlank()) {
        return PasswordRecoveryLinkResult.Rejected("رابط الاستعادة غير صالح أو منتهي الصلاحية.")
    }

    val oobCode = parameters["oobCode"]?.takeIf(String::isNotBlank)
    val mode = parameters["mode"]
    if (oobCode != null && (mode == null || mode.equals("resetPassword", ignoreCase = true))) {
        return PasswordRecoveryLinkResult.Ready(
            PasswordRecoveryCredentials(oobCode = oobCode)
        )
    }

    if (!parameters["type"].equals("recovery", ignoreCase = true)) {
        return PasswordRecoveryLinkResult.Rejected("الرابط المستلم ليس رابط استعادة كلمة مرور.")
    }

    val tokenHash = parameters["token_hash"]?.takeIf(String::isNotBlank)
    if (tokenHash != null) {
        return PasswordRecoveryLinkResult.Ready(
            PasswordRecoveryCredentials(tokenHash = tokenHash)
        )
    }

    val accessToken = parameters["access_token"]?.takeIf(String::isNotBlank)
    val refreshToken = parameters["refresh_token"]?.takeIf(String::isNotBlank)
    val expiresAt = parameters["expires_at"]?.toLongOrNull()
        ?: parameters["expires_in"]?.toLongOrNull()?.takeIf { it > 0L }?.let { nowEpochSeconds + it }

    if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank() ||
        expiresAt == null || expiresAt <= nowEpochSeconds
    ) {
        return PasswordRecoveryLinkResult.Rejected("بيانات التحقق في رابط الاستعادة ناقصة أو منتهية.")
    }

    return PasswordRecoveryLinkResult.Ready(
        PasswordRecoveryCredentials(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = expiresAt
        )
    )
}

private fun parseParameters(encoded: String?): Map<String, String> {
    if (encoded.isNullOrBlank()) return emptyMap()
    return encoded.split('&').mapNotNull { pair ->
        if (pair.isBlank()) return@mapNotNull null
        val separator = pair.indexOf('=')
        val rawKey = if (separator >= 0) pair.substring(0, separator) else pair
        val rawValue = if (separator >= 0) pair.substring(separator + 1) else ""
        val key = decodeParameter(rawKey)
        if (key.isBlank()) null else key to decodeParameter(rawValue)
    }.toMap()
}

private fun decodeParameter(value: String): String = try {
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
} catch (_: Exception) {
    ""
}

private const val PASSWORD_RECOVERY_PATH = "/recovery"
