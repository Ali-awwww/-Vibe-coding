package com.example.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Shared visual tokens for the Vibe Engineering design language.
 * Keep screen-level styling references here instead of duplicating raw values.
 */
object VibeDesignTokens {
    object Color {
        val SpaceBackground = androidx.compose.ui.graphics.Color(0xFF050914)
        val SpacePanel = androidx.compose.ui.graphics.Color(0xE6101B2B)
        val SpacePanelRaised = androidx.compose.ui.graphics.Color(0xF0142235)
        val Cyan = androidx.compose.ui.graphics.Color(0xFF28D7F2)
        val OnCyan = androidx.compose.ui.graphics.Color(0xFF050914)
        val Violet = androidx.compose.ui.graphics.Color(0xFF9B6BFF)
        val TextPrimary = androidx.compose.ui.graphics.Color(0xFFEAF8FF)
        val TextMuted = androidx.compose.ui.graphics.Color(0xFF9CB1C8)
        val Grid = androidx.compose.ui.graphics.Color(0x1A8B949E)
        val Success = androidx.compose.ui.graphics.Color(0xFF36E6A0)
        val Error = androidx.compose.ui.graphics.Color(0xFFFF5875)
    }

    object Shape {
        val Control = 13.dp
        val Panel = 22.dp
        val Compact = 8.dp
    }

    object Spacing {
        val Micro = 4.dp
        val Small = 8.dp
        val Medium = 14.dp
        val Large = 20.dp
        val Section = 28.dp
    }
}
