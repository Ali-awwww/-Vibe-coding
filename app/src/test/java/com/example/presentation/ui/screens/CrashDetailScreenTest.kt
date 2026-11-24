package com.example.presentation.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.theme.CrashIntelligenceTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config
class CrashDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingReportOffersRetryAndBackActions() {
        var retried = false
        var navigatedBack = false

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                CrashDetailUnavailableState(
                    isLoadError = false,
                    onRetry = { retried = true },
                    onBack = { navigatedBack = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("crash_detail_unavailable_state").assertIsDisplayed()
        composeRule.onNodeWithTag("crash_detail_retry_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("crash_detail_back_button").assertIsDisplayed().performClick()

        assertTrue(retried)
        assertTrue(navigatedBack)
    }

    @Test
    fun loadFailureUsesTheConnectionRecoveryState() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                CrashDetailUnavailableState(
                    isLoadError = true,
                    onRetry = {},
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("crash_detail_unavailable_state").assertIsDisplayed()
        composeRule.onNodeWithTag("crash_detail_retry_button").assertIsDisplayed()
        composeRule.onNodeWithTag("crash_detail_back_button").assertIsDisplayed()
    }
}
