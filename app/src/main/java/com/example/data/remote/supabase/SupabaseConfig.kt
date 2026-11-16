package com.example.data.remote.supabase

import com.example.BuildConfig

/**
 * Validates whether Supabase runtime configuration is present and non-placeholder.
 * Prevents placeholder values from `.env.example` from issuing live PostgREST calls.
 */
object SupabaseConfig {
    const val PLACEHOLDER_URL = "https://your-project.supabase.co"
    const val PLACEHOLDER_PUBLISHABLE_KEY = "YOUR_SUPABASE_PUBLISHABLE_KEY"

    fun normalizeBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "$PLACEHOLDER_URL/"
        }
        val host = trimmed.substringAfter("://", "").substringBefore('/').trim()
        if (host.isBlank()) {
            return "$PLACEHOLDER_URL/"
        }
        return "$trimmed/"
    }

    fun isConfigured(
        rawUrl: String = BuildConfig.SUPABASE_URL,
        publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    ): Boolean {
        val normalizedUrl = rawUrl.trim().trimEnd('/')
        val normalizedKey = publishableKey.trim()
        if (normalizedUrl.isBlank() || normalizedKey.isBlank()) return false
        if (normalizedUrl.equals(PLACEHOLDER_URL, ignoreCase = true)) return false
        if (normalizedKey.equals(PLACEHOLDER_PUBLISHABLE_KEY, ignoreCase = true)) return false
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) return false
        val host = normalizedUrl.substringAfter("://", "").substringBefore('/').trim()
        return host.isNotBlank() && !normalizedKey.startsWith("YOUR_", ignoreCase = true)
    }
}
