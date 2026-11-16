package com.example.data.remote.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseConfigTest {

    @Test
    fun `placeholder values from env example are treated as unconfigured`() {
        assertFalse(
            SupabaseConfig.isConfigured(
                rawUrl = SupabaseConfig.PLACEHOLDER_URL,
                publishableKey = SupabaseConfig.PLACEHOLDER_PUBLISHABLE_KEY
            )
        )
    }

    @Test
    fun `blank or your-prefixed keys are treated as unconfigured`() {
        assertFalse(SupabaseConfig.isConfigured("https://abc.supabase.co", ""))
        assertFalse(SupabaseConfig.isConfigured("https://abc.supabase.co", "YOUR_KEY_HERE"))
        assertFalse(SupabaseConfig.isConfigured("not-a-url", "sb_publishable_123456"))
    }

    @Test
    fun `valid supabase url and publishable key are treated as configured`() {
        assertTrue(
            SupabaseConfig.isConfigured(
                rawUrl = "https://xyzcompany.supabase.co/",
                publishableKey = "sb_publishable_valid_123456"
            )
        )
    }

    @Test
    fun `normalizeBaseUrl ensures trailing slash and falls back on malformed url`() {
        assertEquals(
            "https://xyzcompany.supabase.co/",
            SupabaseConfig.normalizeBaseUrl("  https://xyzcompany.supabase.co/  ")
        )
        assertEquals(
            "${SupabaseConfig.PLACEHOLDER_URL}/",
            SupabaseConfig.normalizeBaseUrl("invalid-host")
        )
    }
}
