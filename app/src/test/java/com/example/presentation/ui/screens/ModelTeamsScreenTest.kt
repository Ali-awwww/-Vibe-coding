package com.example.presentation.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.domain.model.*
import com.example.presentation.viewmodel.ModelTeamsUiState
import com.example.ui.theme.CrashIntelligenceTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ModelTeamsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unauthenticatedStateShowsAuthCardAndTriggersSignIn() {
        var loginClicked = false
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                ModelTeamsScreenContent(
                    uiState = ModelTeamsUiState(isAuthenticated = false),
                    onBack = {},
                    onNavigateToValidation = {},
                    onNavigateToChat = {},
                    onNavigateToLogin = { loginClicked = true },
                    onNavigateToTeamDetail = {},
                    onNavigateToTeamUsage = {},
                    onStartCreateTeam = {},
                    onCancelCreate = {},
                    onUpdateNewTeamInfo = { _, _, _ -> },
                    onUpdateCollaborationStyle = {},
                    onUpdateTeamSettings = {},
                    onUpdateTeamGovernance = {},
                    onAddMember = { _, _ -> },
                    onRemoveMember = {},
                    onSaveTeam = {},
                    onUseTemplate = {},
                    onRequestDeleteTeam = {},
                    onConfirmDeleteTeam = {},
                    onDismissDeleteTeam = {},
                    onRefresh = {},
                    onClearError = {},
                    onClearStatusMessage = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("model_teams_auth_card").assertIsDisplayed()
        composeRule.onNodeWithTag("new_team_fab").assertDoesNotExist()
        composeRule.onNodeWithTag("teams_auth_signin_button").performClick()
        assertTrue(loginClicked)
    }

    @Test
    fun emptyTeamsRosterStillDisplaysTemplatesAndConnectedVsConfiguredCounts() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                ModelTeamsScreenContent(
                    uiState = ModelTeamsUiState(
                        isAuthenticated = true,
                        isCloudConfigured = false,
                        teams = emptyList()
                    ),
                    onBack = {},
                    onNavigateToValidation = {},
                    onNavigateToChat = {},
                    onNavigateToLogin = {},
                    onNavigateToTeamDetail = {},
                    onNavigateToTeamUsage = {},
                    onStartCreateTeam = {},
                    onCancelCreate = {},
                    onUpdateNewTeamInfo = { _, _, _ -> },
                    onUpdateCollaborationStyle = {},
                    onUpdateTeamSettings = {},
                    onUpdateTeamGovernance = {},
                    onAddMember = { _, _ -> },
                    onRemoveMember = {},
                    onSaveTeam = {},
                    onUseTemplate = {},
                    onRequestDeleteTeam = {},
                    onConfirmDeleteTeam = {},
                    onDismissDeleteTeam = {},
                    onRefresh = {},
                    onClearError = {},
                    onClearStatusMessage = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("teams_sync_badge").assertTextContains("LOCAL MODE")
        composeRule.onNodeWithTag("template_card_${TeamTemplates.CRASH_INVESTIGATION_TEAM.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("model_teams_lazy_column")
            .performScrollToNode(hasTestTag("empty_teams_roster_card"))
        composeRule.onNodeWithTag("empty_teams_roster_card").assertIsDisplayed()
    }

    @Test
    fun customTeamShowsReadinessStatusAndDeleteConfirmDialog() {
        val team = ModelTeam(
            id = "team-ready",
            name = "Diagnostic Squad",
            description = "Analyzes Kotlin stack traces",
            ownerId = "user-1",
            members = listOf(
                TeamMember(id = "m1", name = "Gemini Coder", modelId = "mod-1", providerId = "prov-connected")
            )
        )
        var deleteConfirmed = false

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                ModelTeamsScreenContent(
                    uiState = ModelTeamsUiState(
                        isAuthenticated = true,
                        teams = listOf(team),
                        connectedProviderIds = setOf("prov-connected"),
                        pendingDeleteTeam = team
                    ),
                    onBack = {},
                    onNavigateToValidation = {},
                    onNavigateToChat = {},
                    onNavigateToLogin = {},
                    onNavigateToTeamDetail = {},
                    onNavigateToTeamUsage = {},
                    onStartCreateTeam = {},
                    onCancelCreate = {},
                    onUpdateNewTeamInfo = { _, _, _ -> },
                    onUpdateCollaborationStyle = {},
                    onUpdateTeamSettings = {},
                    onUpdateTeamGovernance = {},
                    onAddMember = { _, _ -> },
                    onRemoveMember = {},
                    onSaveTeam = {},
                    onUseTemplate = {},
                    onRequestDeleteTeam = {},
                    onConfirmDeleteTeam = { deleteConfirmed = true },
                    onDismissDeleteTeam = {},
                    onRefresh = {},
                    onClearError = {},
                    onClearStatusMessage = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("model_teams_lazy_column")
            .performScrollToNode(hasTestTag("team_card_team-ready"))
        composeRule.onNodeWithTag("team_readiness_team-ready").assertTextContains("متصل (1/1)")

        composeRule.onNodeWithTag("confirm_delete_team_button").performClick()
        assertTrue(deleteConfirmed)
    }

    @Test
    fun createTeamModeShowsWizardValidationAndSaveTrigger() {
        var saveClicked = false
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                ModelTeamsScreenContent(
                    uiState = ModelTeamsUiState(
                        isAuthenticated = true,
                        isCreating = true,
                        nameError = "اسم الفريق مطلوب.",
                        membersError = "أضف عضواً واحداً على الأقل من النماذج المتاحة."
                    ),
                    onBack = {},
                    onNavigateToValidation = {},
                    onNavigateToChat = {},
                    onNavigateToLogin = {},
                    onNavigateToTeamDetail = {},
                    onNavigateToTeamUsage = {},
                    onStartCreateTeam = {},
                    onCancelCreate = {},
                    onUpdateNewTeamInfo = { _, _, _ -> },
                    onUpdateCollaborationStyle = {},
                    onUpdateTeamSettings = {},
                    onUpdateTeamGovernance = {},
                    onAddMember = { _, _ -> },
                    onRemoveMember = {},
                    onSaveTeam = { saveClicked = true },
                    onUseTemplate = {},
                    onRequestDeleteTeam = {},
                    onConfirmDeleteTeam = {},
                    onDismissDeleteTeam = {},
                    onRefresh = {},
                    onClearError = {},
                    onClearStatusMessage = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("team_name_input").assertIsDisplayed()
        composeRule.onNodeWithTag("create_team_wizard_list")
            .performScrollToNode(hasTestTag("team_members_error_text"))
        composeRule.onNodeWithTag("team_members_error_text").assertIsDisplayed()

        composeRule.onNodeWithTag("create_team_wizard_list")
            .performScrollToNode(hasTestTag("save_team_button"))
        composeRule.onNodeWithTag("save_team_button").performClick()
        assertTrue(saveClicked)
    }
}
