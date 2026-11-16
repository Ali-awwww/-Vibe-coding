package com.example.presentation.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.domain.model.*
import com.example.presentation.viewmodel.TeamChatTab
import com.example.presentation.viewmodel.TeamChatUiState
import com.example.ui.theme.CrashIntelligenceTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TeamChatScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unauthenticatedStateDisplaysAuthCardAndHidesWorkspaceTabs() {
        var signInClicked = false
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                TeamChatScreenContent(
                    uiState = TeamChatUiState(isAuthenticated = false),
                    onBack = {},
                    onNavigateToLogin = { signInClicked = true },
                    onNavigateToUsage = {},
                    onNavigateToDetail = {},
                    onSelectTab = {},
                    onSelectTeam = {},
                    onSelectProject = {},
                    onTaskPromptChange = {},
                    onSetShowAttachmentDialog = {},
                    onAddAttachedFile = { _, _, _, _, _ -> },
                    onRemoveAttachedFile = {},
                    onSendTask = {},
                    onConfirmSendAfterEstimation = {},
                    onDismissEstimation = {},
                    onEstimateTask = {},
                    onPauseTeam = {},
                    onResumeTeam = {},
                    onStopTeam = {},
                    onRetryFailed = {},
                    onSetShowAssignDialog = {},
                    onConfirmAssignTask = { _, _ -> },
                    onSetShowChangeAgentDialog = {},
                    onConfirmChangeAgent = { _, _ -> },
                    onSetShowInstructionDialog = {},
                    onSendInstruction = {},
                    onRefresh = {},
                    onClearError = {},
                    onClearStatusMessage = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("team_chat_auth_card").assertIsDisplayed()
        composeRule.onNodeWithTag("team_chat_tab_row").assertDoesNotExist()
        composeRule.onNodeWithTag("teams_auth_signin_button").performClick()
        assertTrue(signInClicked)
    }

    @Test
    fun authenticatedChatInputDisplaysStatusBarAndTriggersSendTask() {
        val team = ModelTeam(
            id = "team-1",
            name = "Diagnostic Squad",
            members = listOf(TeamMember(id = "m1", name = "Planner", modelId = "gemini-3.8-flash")),
            governance = TeamGovernanceSettings(freeOnly = true)
        )
        var sendTriggered = false

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                TeamChatScreenContent(
                    uiState = TeamChatUiState(
                        isAuthenticated = true,
                        isCloudConfigured = false,
                        teams = listOf(team),
                        selectedTeam = team,
                        taskPrompt = "Analyze stack trace",
                        activeTab = TeamChatTab.CHAT_INPUT
                    ),
                    onBack = {},
                    onNavigateToLogin = {},
                    onNavigateToUsage = {},
                    onNavigateToDetail = {},
                    onSelectTab = {},
                    onSelectTeam = {},
                    onSelectProject = {},
                    onTaskPromptChange = {},
                    onSetShowAttachmentDialog = {},
                    onAddAttachedFile = { _, _, _, _, _ -> },
                    onRemoveAttachedFile = {},
                    onSendTask = { sendTriggered = true },
                    onConfirmSendAfterEstimation = {},
                    onDismissEstimation = {},
                    onEstimateTask = {},
                    onPauseTeam = {},
                    onResumeTeam = {},
                    onStopTeam = {},
                    onRetryFailed = {},
                    onSetShowAssignDialog = {},
                    onConfirmAssignTask = { _, _ -> },
                    onSetShowChangeAgentDialog = {},
                    onConfirmChangeAgent = { _, _ -> },
                    onSetShowInstructionDialog = {},
                    onSendInstruction = {},
                    onRefresh = {},
                    onClearError = {},
                    onClearStatusMessage = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("team_live_status_bar").assertIsDisplayed()
        composeRule.onNodeWithText("LOCAL", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("team_task_prompt_input").assertTextContains("Analyze stack trace", substring = true)
        composeRule.onNodeWithTag("send_team_task_button").performScrollTo().performClick()
        assertTrue(sendTriggered)
    }

    @Test
    fun activityLogDisplaysHonestEmptyConsensusAndDisagreementMessagesWithoutFabrication() {
        val team = ModelTeam(
            id = "team-1",
            name = "Diagnostic Squad",
            members = listOf(TeamMember(id = "m1", name = "Planner", modelId = "gemini-3.8-flash"))
        )
        val execution = TeamExecution(
            id = "exec-1",
            teamId = "team-1",
            userId = "user-1",
            prompt = "Check null safety",
            consensusPoints = emptyList(),
            disagreementPoints = emptyList()
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                TeamChatScreenContent(
                    uiState = TeamChatUiState(
                        isAuthenticated = true,
                        teams = listOf(team),
                        selectedTeam = team,
                        currentExecution = execution,
                        activeTab = TeamChatTab.ACTIVITY_LOG
                    ),
                    onBack = {},
                    onNavigateToLogin = {},
                    onNavigateToUsage = {},
                    onNavigateToDetail = {},
                    onSelectTab = {},
                    onSelectTeam = {},
                    onSelectProject = {},
                    onTaskPromptChange = {},
                    onSetShowAttachmentDialog = {},
                    onAddAttachedFile = { _, _, _, _, _ -> },
                    onRemoveAttachedFile = {},
                    onSendTask = {},
                    onConfirmSendAfterEstimation = {},
                    onDismissEstimation = {},
                    onEstimateTask = {},
                    onPauseTeam = {},
                    onResumeTeam = {},
                    onStopTeam = {},
                    onRetryFailed = {},
                    onSetShowAssignDialog = {},
                    onConfirmAssignTask = { _, _ -> },
                    onSetShowChangeAgentDialog = {},
                    onConfirmChangeAgent = { _, _ -> },
                    onSetShowInstructionDialog = {},
                    onSendInstruction = {},
                    onRefresh = {},
                    onClearError = {},
                    onClearStatusMessage = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("empty_consensus_message")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("empty_disagreements_message")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
