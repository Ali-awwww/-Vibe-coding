package com.example

import androidx.compose.ui.graphics.luminance
import com.example.ui.theme.VibeDesignTokens
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginAccessibilityContrastTest {
    @Test
    fun cyanPrimaryActionForegroundMeetsWcagAaForNormalText() {
        val foreground = VibeDesignTokens.Color.OnCyan.luminance()
        val background = VibeDesignTokens.Color.Cyan.luminance()
        val lighter = maxOf(foreground, background)
        val darker = minOf(foreground, background)
        val contrastRatio = (lighter + 0.05f) / (darker + 0.05f)

        assertTrue(
            "Cyan action foreground/background contrast must be at least 4.5:1; was $contrastRatio",
            contrastRatio >= 4.5f
        )
    }
}
