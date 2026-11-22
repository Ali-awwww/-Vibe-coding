package com.example.presentation.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.domain.model.AgentConnectionStatus
import com.example.domain.model.Application
import com.example.domain.model.TestSession
import com.example.domain.model.TestSessionStatus
import com.example.ui.theme.CrashIntelligenceTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config
class LiveTestingAvailabilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun persistedOnlineBadgeDoesNotEnableStartOrConnectWithoutAgentTransport() {
        val application = Application(
            id = "app-live",
            projectId = "project-live",
            name = "Live app",
            packageName = "com.example.live",
            agentConnectionStatus = AgentConnectionStatus.ONLINE
        )

        composeRule.setContent {
            val navController = rememberNavController()
            CrashIntelligenceTheme(darkTheme = true) {
                ApplicationCard(
                    application = application,
                    navController = navController,
                    onStartTesting = {},
                    onConnectAgent = {},
                    agentTransportAvailable = false
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("live_testing_agent_status")
            .assertTextEquals(appContext().getString(R.string.live_testing_agent_unavailable_status))
        composeRule.onNodeWithTag("live_testing_start_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("live_testing_connect_agent_button").assertDoesNotExist()
    }

    @Test
    fun startDialogCannotSubmitWithoutAgentTransport() {
        val application = Application(
            id = "app-live",
            projectId = "project-live",
            name = "Live app",
            packageName = "com.example.live"
        )
        var submitted = false

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                StartTestingDialog(
                    application = application,
                    onDismiss = {},
                    onConfirm = { _, _ -> submitted = true },
                    agentTransportAvailable = false
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("start_testing_confirm_button").assertIsNotEnabled()
        assertFalse(submitted)
    }

    @Test
    fun unavailableNoticeExplainsThatNoLiveSessionCanBeVerified() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                AgentTransportUnavailableNotice()
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(appContext().getString(R.string.live_testing_agent_unavailable_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(appContext().getString(R.string.live_testing_agent_unavailable_body))
            .assertIsDisplayed()
    }

    @Test
    fun storedSessionIsNotShownAsACompletedLiveSessionWithoutTransport() {
        val session = TestSession(
            id = "local-session",
            projectId = "project-live",
            applicationId = "app-live",
            buildId = "local-build",
            version = "1.0.0",
            status = TestSessionStatus.COMPLETED
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                SessionHistoryItem(
                    session = session,
                    onClick = {},
                    agentTransportAvailable = false
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(appContext().getString(R.string.live_testing_local_record_unverified))
            .assertIsDisplayed()
    }

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()
}
