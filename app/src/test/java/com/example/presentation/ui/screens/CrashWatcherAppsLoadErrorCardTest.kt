package com.example.presentation.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.theme.CrashIntelligenceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config
class CrashWatcherAppsLoadErrorCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun errorStateOffersAnAccessibleRetryAction() {
        var retryCount = 0

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                CrashWatcherAppsLoadErrorCard(onRetry = { retryCount++ })
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("crash_watcher_apps_error_card").assertIsDisplayed()
        composeRule.onNodeWithTag("crash_watcher_apps_retry_button").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }
}
