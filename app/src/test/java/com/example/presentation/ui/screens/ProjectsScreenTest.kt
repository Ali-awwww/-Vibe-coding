package com.example.presentation.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.domain.model.*
import com.example.presentation.viewmodel.ProjectsUiState
import com.example.ui.theme.CrashIntelligenceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config
class ProjectsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unauthenticatedStateShowsAuthCardAndDisablesAddProject() {
        var signInClicked = false
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                ProjectsScreenContent(
                    uiState = ProjectsUiState(isAuthenticated = false, isLoading = false),
                    onNavigateBack = {},
                    onNavigate = {},
                    onRefresh = {},
                    onSelectProject = {},
                    onAddProject = { _, _, _, _ -> },
                    onDeleteProject = {},
                    onAddApplication = { _, _, _, _ -> },
                    onDeleteApplication = {},
                    onAddMember = { _, _, _ -> },
                    onUpdateMemberRole = { _, _, _ -> },
                    onRemoveMember = { _, _ -> },
                    onUpdateSecurityPolicy = { _, _, _ -> },
                    onPurgeData = {},
                    onClearNotifications = {},
                    onSignIn = { signInClicked = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("projects_auth_required_card").assertIsDisplayed()
        composeRule.onNodeWithTag("projects_add_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("projects_sign_in_button").performClick()
        assertTrue(signInClicked)
    }

    @Test
    fun initialTabOneOpensApplicationsAndRequiresConfirmationBeforeDeletingApp() {
        val project = Project(id = "p1", name = "Alpha", ownerId = "u1")
        val app = Application(
            id = "app-1",
            projectId = "p1",
            name = "Alpha Android",
            platform = "Android",
            packageName = "com.alpha.app",
            ownerId = "u1",
            sdkSetupStatus = SdkSetupStatus.CONFIGURED,
            agentConnectionStatus = AgentConnectionStatus.ONLINE
        )
        var deletedAppId: String? = null

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                ProjectsScreenContent(
                    uiState = ProjectsUiState(
                        isAuthenticated = true,
                        projects = listOf(project),
                        selectedProject = project,
                        applications = listOf(app),
                        userRoleInSelectedProject = Role.OWNER
                    ),
                    initialTab = 1,
                    onNavigateBack = {},
                    onNavigate = {},
                    onRefresh = {},
                    onSelectProject = {},
                    onAddProject = { _, _, _, _ -> },
                    onDeleteProject = {},
                    onAddApplication = { _, _, _, _ -> },
                    onDeleteApplication = { deletedAppId = it },
                    onAddMember = { _, _, _ -> },
                    onUpdateMemberRole = { _, _, _ -> },
                    onRemoveMember = { _, _ -> },
                    onUpdateSecurityPolicy = { _, _, _ -> },
                    onPurgeData = {},
                    onClearNotifications = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("applications_lazy_column")
            .performScrollToNode(hasTestTag("application_card_app-1"))
        composeRule.onNodeWithTag("application_sdk_status_app-1")
            .assertTextContains("مُهيّأ", substring = true)
        composeRule.onNodeWithTag("delete_application_app-1").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("delete_app_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_app_confirm_button").performClick()
        composeRule.waitForIdle()
        assertEquals("app-1", deletedAppId)
    }

    @Test
    fun addProjectDialogValidatesAndTrimsInputs() {
        var submittedName = ""
        var submittedDesc = ""

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                ProjectsScreenContent(
                    uiState = ProjectsUiState(isAuthenticated = true),
                    onNavigateBack = {},
                    onNavigate = {},
                    onRefresh = {},
                    onSelectProject = {},
                    onAddProject = { name, desc, _, _ ->
                        submittedName = name
                        submittedDesc = desc
                    },
                    onDeleteProject = {},
                    onAddApplication = { _, _, _, _ -> },
                    onDeleteApplication = {},
                    onAddMember = { _, _, _ -> },
                    onUpdateMemberRole = { _, _, _ -> },
                    onRemoveMember = { _, _ -> },
                    onUpdateSecurityPolicy = { _, _, _ -> },
                    onPurgeData = {},
                    onClearNotifications = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("projects_add_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("add_project_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("add_project_confirm_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("add_project_name_input").performTextInput("  Vibe Core  ")
        composeRule.onNodeWithTag("add_project_desc_input").performTextInput("  Main App  ")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("add_project_confirm_button").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        assertEquals("Vibe Core", submittedName)
        assertEquals("Main App", submittedDesc)
    }
}
