package com.example.presentation.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.presentation.viewmodel.TeamUsageUiState
import com.example.ui.theme.CrashIntelligenceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TeamUsageDashboardScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun noTelemetryDoesNotRenderZeroAsMeasuredUsage() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                TeamMetricsOverview(TeamUsageUiState(hasMeasuredUsage = false))
            }
        }

        composeRule.onNodeWithTag("team_usage_no_measurements").assertIsDisplayed()
        composeRule.onNodeWithText("لا توجد بيانات قياس موثقة خلال هذه الفترة.").assertIsDisplayed()
        composeRule.onNodeWithText("0.000$").assertDoesNotExist()
    }

    @Test
    fun unconfirmedCostIsNotLabeledAsConfirmed() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                TeamMetricsOverview(
                    TeamUsageUiState(
                        hasMeasuredUsage = true,
                        hasConfirmedCost = false,
                        totalRequests = 2,
                        totalTokens = 40,
                        unconfirmedCostUsd = 0.025
                    )
                )
            }
        }

        composeRule.onNodeWithText("غير متاحة").assertIsDisplayed()
        composeRule.onNodeWithText("تكلفة تقديرية غير مؤكدة: 0.025$").assertIsDisplayed()
    }

    @Test
    fun unavailableStateRetryInvokesRefresh() {
        var retryCount = 0
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                TeamUsageStatePanel(
                    title = "الفريق غير متاح",
                    message = "تعذر تحميل البيانات.",
                    onRetry = { retryCount++ }
                )
            }
        }

        composeRule.onNodeWithTag("team_usage_unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("إعادة المحاولة").performClick()
        assertEquals(1, retryCount)
    }
}
