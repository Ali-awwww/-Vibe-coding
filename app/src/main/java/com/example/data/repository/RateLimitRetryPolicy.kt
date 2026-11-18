package com.example.data.repository

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Bounded retry timing for provider HTTP 429 responses.
 *
 * A Retry-After value may be integer seconds or an RFC-1123 HTTP date.
 * Invalid/missing headers use the configured default; all waits are capped.
 */
internal class RateLimitRetryPolicy(
    val maxRetries: Int = 1,
    private val defaultDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 30_000L
) {
    init {
        require(maxRetries >= 0) { "maxRetries cannot be negative." }
        require(defaultDelayMs >= 0L) { "defaultDelayMs cannot be negative." }
        require(maxDelayMs >= 0L) { "maxDelayMs cannot be negative." }
    }

    fun delayBeforeRetry(
        retryAttempt: Int,
        retryAfterHeader: String?,
        nowMillis: Long
    ): Long? {
        if (retryAttempt !in 1..maxRetries) return null
        val requestedDelay = parseRetryAfterMillis(retryAfterHeader, nowMillis) ?: defaultDelayMs
        return requestedDelay.coerceIn(0L, maxDelayMs)
    }

    private fun parseRetryAfterMillis(value: String?, nowMillis: Long): Long? {
        val header = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        header.toLongOrNull()?.let { seconds ->
            if (seconds < 0L) return null
            return if (seconds > Long.MAX_VALUE / 1_000L) {
                Long.MAX_VALUE
            } else {
                seconds * 1_000L
            }
        }

        val formatter = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
            isLenient = false
        }
        val position = ParsePosition(0)
        val retryAt = runCatching { formatter.parse(header, position) }.getOrNull()
            ?: return null
        if (position.index != header.length) return null
        return (retryAt.time - nowMillis).coerceAtLeast(0L)
    }
}
