package com.example.presentation.auth

import com.example.auth.APP_AUTH_REDIRECT_HOST
import com.example.auth.APP_AUTH_REDIRECT_SCHEME
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Accepts only the app's post-verification callback for a confirmed signup.
 * Supports Firebase Auth `verifyEmail` oobCode callbacks as well as direct signup confirmations.
 */
internal fun isVerifiedEmailConfirmationLink(rawLink: String): Boolean {
    val uri = runCatching { URI(rawLink) }.getOrNull() ?: return false
    if (!uri.scheme.equals(APP_AUTH_REDIRECT_SCHEME, ignoreCase = true) ||
        !uri.host.equals(APP_AUTH_REDIRECT_HOST, ignoreCase = true) ||
        uri.path != EMAIL_CONFIRMATION_PATH
    ) return false

    val parameters = parseParameters(uri.rawQuery) + parseParameters(uri.rawFragment)
    if (!parameters["error"].isNullOrBlank()) return false

    val isFirebaseVerifyEmail = parameters["mode"].equals("verifyEmail", ignoreCase = true) &&
        !parameters["oobCode"].isNullOrBlank()
    val isSignupConfirmation = parameters["type"].equals("signup", ignoreCase = true) &&
        !parameters["access_token"].isNullOrBlank()

    return isFirebaseVerifyEmail || isSignupConfirmation
}

private fun parseParameters(encoded: String?): Map<String, String> {
    if (encoded.isNullOrBlank()) return emptyMap()
    return encoded.split('&').mapNotNull { pair ->
        if (pair.isBlank()) return@mapNotNull null
        val separator = pair.indexOf('=')
        val key = decodeParameter(if (separator >= 0) pair.substring(0, separator) else pair)
        val value = decodeParameter(if (separator >= 0) pair.substring(separator + 1) else "")
        key.takeIf(String::isNotBlank)?.let { it to value }
    }.toMap()
}

private fun decodeParameter(value: String): String = runCatching {
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}.getOrDefault("")

private const val EMAIL_CONFIRMATION_PATH = "/email-confirmation"
