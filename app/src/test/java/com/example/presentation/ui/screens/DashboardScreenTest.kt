package com.example.presentation.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.domain.model.*
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.DashboardUiState
import com.example.presentation.viewmodel.DashboardViewModel
import com.example.presentation.ui.screens.dashboard.DeveloperSessionHeader
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
class DashboardScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unauthenticatedStateDisplaysAuthRequiredGateAndSignInAction() {
        var signInClicked = false
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                DashboardScreenContent(
                    uiState = DashboardUiState(isAuthenticated = false, isLoading = false),
                    onNavigate = {},
                    onSelectProject = {},
                    onRefresh = {},
                    onMarkNotificationRead = {},
                    onMarkAllNotificationsRead = {},
                    onClearNotifications = {},
                    onSignOut = {},
                    onSignIn = { signInClicked = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dashboard_auth_required_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_sign_in_button").performClick()
        assertTrue(signInClicked)
        composeRule.onNodeWithTag("dashboard_kpi_projects").assertDoesNotExist()
    }

    @Test
    fun loadFailureDoesNotPresentEmptyDataAsAHealthyDashboard() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                DashboardScreenContent(
                    uiState = DashboardUiState(
                        isAuthenticated = true,
                        currentUser = UserAccount(id = "u1", email = "dev@vibe.io"),
                        isLoading = false,
                        error = DashboardViewModel.ERROR_LOAD
                    ),
                    onNavigate = {},
                    onSelectProject = {},
                    onRefresh = {},
                    onMarkNotificationRead = {},
                    onMarkAllNotificationsRead = {},
                    onClearNotifications = {},
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dashboard_error_card").assertExists()
        composeRule.onNodeWithText("Could not load dashboard data. Check the connection and retry.").assertExists()
        composeRule.onNodeWithTag("dashboard_empty_issues_card").assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_empty_crashes_card").assertDoesNotExist()
    }

    @Test
    fun configuredFirebaseIsNotPresentedAsSuccessfulSyncOrARealWorkspace() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                DeveloperSessionHeader(
                    user = UserAccount(id = "u1", email = "", displayName = ""),
                    isCloudConfigured = true
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dashboard_sync_badge")
            .assertIsDisplayed()
            .assertTextContains("FIREBASE CONFIGURED • SYNC UNVERIFIED")
        composeRule.onNodeWithText("FIREBASE SYNC").assertDoesNotExist()
        composeRule.onNodeWithText("Welcome, Account").assertExists()
        composeRule.onNodeWithText("VIBE ENGINEERING • ACCOUNT CONTEXT").assertExists()
        composeRule.onNodeWithText("WORKSPACE // SECURE_ENV_01").assertDoesNotExist()
    }

    @Test
    fun unscopedApplicationsKpiPromptsForProjectInsteadOfOpeningTheFirstProject() {
        val navigatedRoutes = mutableListOf<Screen>()
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                DashboardScreenContent(
                    uiState = DashboardUiState(
                        isAuthenticated = true,
                        currentUser = UserAccount(id = "u1", email = "dev@vibe.io"),
                        projects = listOf(
                            Project(id = "p1", name = "Alpha", ownerId = "u1"),
                            Project(id = "p2", name = "Beta", ownerId = "u1")
                        ),
                        applicationCount = 4,
                        isLoading = false
                    ),
                    onNavigate = { navigatedRoutes.add(it) },
                    onSelectProject = {},
                    onRefresh = {},
                    onMarkNotificationRead = {},
                    onMarkAllNotificationsRead = {},
                    onClearNotifications = {},
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("dashboard_kpi_applications"))
        composeRule.onNodeWithTag("dashboard_kpi_applications").performClick()
        assertEquals(Screen.Projects, navigatedRoutes.last())
    }

    @Test
    fun revokedProjectScopeHidesAggregateDataAndOffersAnExplicitReset() {
        var selectedProject: String? = "p1"
        val remainingProject = Project(id = "p2", name = "Beta", ownerId = "u1")

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                DashboardScreenContent(
                    uiState = DashboardUiState(
                        isAuthenticated = true,
                        currentUser = UserAccount(id = "u1", email = "dev@vibe.io"),
                        projects = listOf(remainingProject),
                        selectedProjectId = "p1",
                        isLoading = false,
                        error = DashboardViewModel.ERROR_PROJECT_UNAVAILABLE
                    ),
                    onNavigate = {},
                    onSelectProject = { selectedProject = it },
                    onRefresh = {},
                    onMarkNotificationRead = {},
                    onMarkAllNotificationsRead = {},
                    onClearNotifications = {},
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            "The selected project is no longer available. Choose another project or show all projects."
        ).assertExists()
        composeRule.onNodeWithTag("dashboard_kpi_projects").assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_empty_issues_card").assertDoesNotExist()
        composeRule.onNodeWithText("Show all projects").performClick()

        assertEquals(null, selectedProject)
    }

    @Test
    fun authenticatedStateDisplaysCloudStatusProjectFiltersAndKpiNavigation() {
        val navigatedRoutes = mutableListOf<Screen>()
        var selectedProjectArg: String? = "unset"
        var retryClicked = false

        val project1 = Project(id = "p1", name = "Alpha", ownerId = "u1")
        val project2 = Project(id = "p2", name = "Beta", ownerId = "u1")
        val crash = CrashReport(
            id = "c1",
            projectId = "p1",
            exceptionType = "IllegalStateException",
            message = "Missing token",
            severity = Severity.HIGH
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                DashboardScreenContent(
                    uiState = DashboardUiState(
                        isAuthenticated = true,
                        currentUser = UserAccount(id = "u1", email = "ali@vibe.io", displayName = "Ali"),
                        projects = listOf(project1, project2),
                        selectedProjectId = "p1",
                        recentCrashes = listOf(crash),
                        recentIssues = listOf(Issue(id = "i1", projectId = "p1", title = "Null pointer", occurrenceCount = 7)),
                        crashCount = 42,
                        projectCount = 2,
                        applicationCount = 4,
                        issueCount = 3,
                        isCloudConfigured = false,
                        error = DashboardViewModel.ERROR_CONNECTION
                    ),
                    onNavigate = { navigatedRoutes.add(it) },
                    onSelectProject = { selectedProjectArg = it },
                    onRefresh = { retryClicked = true },
                    onMarkNotificationRead = {},
                    onMarkAllNotificationsRead = {},
                    onClearNotifications = {},
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dashboard_sync_badge")
            .assertIsDisplayed()
            .assertTextContains("LOCAL CACHE • CLOUD UNCONFIGURED")

        composeRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("dashboard_error_card"))
        composeRule.onNodeWithTag("dashboard_error_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_retry_button").performClick()
        assertTrue(retryClicked)

        composeRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("dashboard_kpi_crashes"))
        composeRule.onNodeWithTag("dashboard_kpi_crashes").assertTextContains("42")
        composeRule.onNodeWithTag("dashboard_kpi_issues").performClick()
        assertEquals(Screen.Issues(projectId = "p1"), navigatedRoutes.last())

        composeRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("dashboard_project_filter_row"))
        composeRule.onNodeWithTag("dashboard_project_filter_row")
            .performScrollToNode(hasTestTag("dashboard_project_chip_p2"))
        composeRule.onNodeWithTag("dashboard_project_chip_p2").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals("p2", selectedProjectArg)

        composeRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("dashboard_kpi_applications"))
        composeRule.onNodeWithTag("dashboard_kpi_applications").performClick()
        composeRule.waitForIdle()
        assertEquals(Screen.Applications("p1"), navigatedRoutes.last())

        composeRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("dashboard_kpi_crashes"))
        composeRule.onNodeWithTag("dashboard_kpi_crashes").performClick()
        assertEquals(Screen.Crashes(projectId = "p1"), navigatedRoutes.last())

        composeRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("dashboard_issue_i1"))
        composeRule.onNodeWithTag("dashboard_issue_i1").performClick()
        assertEquals(Screen.Issues(projectId = "p1"), navigatedRoutes.last())

        composeRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("dashboard_crash_c1"))
        composeRule.onNodeWithTag("dashboard_crash_c1").performClick()
        assertEquals(Screen.CrashDetail("c1"), navigatedRoutes.last())
    }
}
