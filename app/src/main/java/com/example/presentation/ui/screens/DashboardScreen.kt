package com.example.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.R
import com.example.presentation.navigation.Screen
import com.example.presentation.ui.screens.dashboard.*
import com.example.presentation.viewmodel.DashboardUiState
import com.example.presentation.viewmodel.DashboardViewModel
import com.example.ui.theme.VibeDesignTokens

@Composable
fun DashboardScreen(
    navController: NavController,
    onSignOut: () -> Unit,
    viewModel: DashboardViewModel,
    onSignIn: () -> Unit = { navController.navigate(Screen.Login) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardScreenContent(
        uiState = uiState,
        onNavigate = { route -> navController.navigate(route) },
        onSelectProject = viewModel::selectProject,
        onRefresh = viewModel::refresh,
        onMarkNotificationRead = viewModel::markNotificationAsRead,
        onMarkAllNotificationsRead = viewModel::markAllNotificationsAsRead,
        onClearNotifications = viewModel::clearAllNotifications,
        onSignOut = onSignOut,
        onSignIn = onSignIn
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreenContent(
    uiState: DashboardUiState,
    onNavigate: (Screen) -> Unit,
    onSelectProject: (String?) -> Unit,
    onRefresh: () -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    onClearNotifications: () -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit = {}
) {
    var showNotificationsSheet by remember { mutableStateOf(false) }
    val dashboardErrorMessage = when (uiState.error) {
        DashboardViewModel.ERROR_SESSION_EXPIRED -> stringResource(R.string.dashboard_error_session_expired)
        DashboardViewModel.ERROR_CONNECTION -> stringResource(R.string.dashboard_error_connection)
        else -> stringResource(R.string.dashboard_error_load)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CosmicDashboardBackdrop(modifier = Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = VibeDesignTokens.Color.SpacePanel,
                        titleContentColor = VibeDesignTokens.Color.TextPrimary,
                        actionIconContentColor = VibeDesignTokens.Color.Cyan
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeveloperMode,
                                contentDescription = null,
                                tint = VibeDesignTokens.Color.Cyan
                            )
                            Text(
                                text = stringResource(R.string.dashboard),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        if (uiState.isAuthenticated) {
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_refresh_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.dashboard_refresh_content_description))
                            }
                            IconButton(
                                onClick = { showNotificationsSheet = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_notifications_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (uiState.unreadNotificationCount > 0) {
                                            Badge {
                                                Text(uiState.unreadNotificationCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.dashboard_notifications_content_description))
                                }
                            }
                            IconButton(
                                onClick = onSignOut,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_sign_out_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.dashboard_sign_out_content_description))
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = VibeDesignTokens.Color.SpacePanel,
                    contentColor = VibeDesignTokens.Color.TextPrimary,
                    tonalElevation = 0.dp,
                    windowInsets = NavigationBarDefaults.windowInsets
                ) {
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VibeDesignTokens.Color.Cyan,
                        selectedTextColor = VibeDesignTokens.Color.Cyan,
                        indicatorColor = VibeDesignTokens.Color.Cyan.copy(alpha = 0.16f),
                        unselectedIconColor = VibeDesignTokens.Color.TextMuted,
                        unselectedTextColor = VibeDesignTokens.Color.TextMuted
                    )
                    NavigationBarItem(
                        selected = true,
                        onClick = {},
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                        label = { Text(stringResource(R.string.dashboard), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = navItemColors
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { onNavigate(Screen.Projects) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        label = { Text(stringResource(R.string.projects), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = navItemColors
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { onNavigate(Screen.Crashes(projectId = uiState.selectedProjectId)) },
                        icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                        label = { Text(stringResource(R.string.crashes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = navItemColors
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { onNavigate(Screen.Issues(uiState.selectedProjectId)) },
                        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                        label = { Text(stringResource(R.string.issues), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = navItemColors
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { onNavigate(Screen.Settings) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.settings), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = navItemColors
                    )
                }
            }
        ) { innerPadding ->
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("dashboard_loading"),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = VibeDesignTokens.Color.Cyan)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize()
                            .testTag("dashboard_lazy_column"),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        if (!uiState.isAuthenticated) {
                            item {
                                DashboardAuthRequiredCard(onSignIn = onSignIn)
                            }
                            return@LazyColumn
                        }

                        if (!uiState.error.isNullOrBlank()) {
                            item {
                                DashboardErrorBanner(
                                    errorMessage = dashboardErrorMessage,
                                    onRetry = onRefresh
                                )
                            }
                        }

                        item {
                            DeveloperSessionHeader(
                                user = uiState.currentUser,
                                isCloudConfigured = uiState.isCloudConfigured
                            )
                        }

                        if (uiState.projects.isNotEmpty()) {
                            item {
                                DashboardSectionHeader(
                                    title = stringResource(R.string.dashboard_active_project_context),
                                    badge = stringResource(if (uiState.selectedProjectId == null) R.string.dashboard_scope_all else R.string.dashboard_scope_selected)
                                )
                                Spacer(Modifier.height(8.dp))
                                ProjectContextFilterRow(
                                    projects = uiState.projects,
                                    selectedProjectId = uiState.selectedProjectId,
                                    onSelectProject = onSelectProject
                                )
                            }
                        }

                        item {
                            DashboardSectionHeader(
                                title = stringResource(R.string.overview),
                                badge = "${uiState.projectCount} PRJ"
                            )
                            Text(
                                text = stringResource(R.string.dashboard_metrics_scope_description),
                                style = MaterialTheme.typography.labelSmall,
                                color = VibeDesignTokens.Color.TextMuted
                            )
                            Spacer(Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    DashboardStatCard(
                                        label = stringResource(R.string.projects),
                                        value = uiState.projectCount.toString(),
                                        icon = Icons.Default.Folder,
                                        accent = VibeDesignTokens.Color.Cyan,
                                        tag = "dashboard_kpi_projects",
                                        modifier = Modifier.weight(1f),
                                        onClick = { onNavigate(Screen.Projects) }
                                    )
                                    DashboardStatCard(
                                        label = stringResource(R.string.dashboard_applications),
                                        value = uiState.applicationCount.toString(),
                                        icon = Icons.Default.Apps,
                                        accent = VibeDesignTokens.Color.Violet,
                                        tag = "dashboard_kpi_applications",
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            uiState.selectedProjectId?.let { projectId ->
                                                onNavigate(Screen.Applications(projectId))
                                            } ?: onNavigate(Screen.Projects)
                                        }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    DashboardStatCard(
                                        label = stringResource(R.string.issues),
                                        value = uiState.issueCount.toString(),
                                        icon = Icons.Default.Warning,
                                        accent = VibeDesignTokens.Color.Success,
                                        tag = "dashboard_kpi_issues",
                                        modifier = Modifier.weight(1f),
                                        onClick = { onNavigate(Screen.Issues(uiState.selectedProjectId)) }
                                    )
                                    DashboardStatCard(
                                        label = stringResource(R.string.crashes),
                                        value = uiState.crashCount.toString(),
                                        icon = Icons.Default.BugReport,
                                        accent = VibeDesignTokens.Color.Error,
                                        tag = "dashboard_kpi_crashes",
                                        modifier = Modifier.weight(1f),
                                        onClick = { onNavigate(Screen.Crashes(projectId = uiState.selectedProjectId)) }
                                    )
                                }
                            }
                        }

                        item {
                            DashboardSectionHeader(title = stringResource(R.string.dashboard_engineering_ai_tools))
                            Spacer(Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                DashboardToolCard(
                                    title = stringResource(R.string.crash_watcher_title),
                                    subtitle = stringResource(R.string.crash_watcher_desc),
                                    icon = Icons.Default.Visibility,
                                    accent = VibeDesignTokens.Color.Cyan,
                                    tag = "dashboard_tool_crash_watcher",
                                    onClick = { onNavigate(Screen.CrashWatcher) }
                                )
                                DashboardToolCard(
                                    title = stringResource(R.string.dashboard_vibe_studio_title),
                                    subtitle = stringResource(R.string.dashboard_vibe_studio_description),
                                    icon = Icons.Default.AutoAwesome,
                                    accent = VibeDesignTokens.Color.Cyan,
                                    tag = "dashboard_tool_vibe_studio",
                                    onClick = { onNavigate(Screen.VibeStudio) }
                                )
                                DashboardToolCard(
                                    title = stringResource(R.string.dashboard_model_teams_title),
                                    subtitle = stringResource(R.string.dashboard_model_teams_description),
                                    icon = Icons.Default.GroupWork,
                                    accent = VibeDesignTokens.Color.Violet,
                                    tag = "dashboard_tool_model_teams",
                                    onClick = { onNavigate(Screen.ModelTeams) }
                                )
                                DashboardToolCard(
                                    title = stringResource(R.string.dev_debug_center),
                                    subtitle = stringResource(R.string.dev_debug_center_desc),
                                    icon = Icons.Default.Healing,
                                    accent = VibeDesignTokens.Color.Success,
                                    tag = "dashboard_tool_dev_debug",
                                    onClick = { onNavigate(Screen.DevDebugCenter()) }
                                )
                            }
                        }

                        item {
                            DashboardSectionHeader(title = stringResource(R.string.dashboard_crash_analysis_testing))
                            Spacer(Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DashboardCompactToolCard(
                                        title = stringResource(R.string.analytics_title),
                                        icon = Icons.Default.BarChart,
                                        tag = "dashboard_tool_analytics",
                                        modifier = Modifier.weight(1f),
                                        onClick = { onNavigate(Screen.Analytics) }
                                    )
                                    DashboardCompactToolCard(
                                        title = stringResource(R.string.live_testing),
                                        icon = Icons.Default.PlayCircle,
                                        tag = "dashboard_tool_live_testing",
                                        modifier = Modifier.weight(1f),
                                        onClick = { onNavigate(Screen.LiveTesting) }
                                    )
                                }
                                DashboardToolCard(
                                    title = stringResource(R.string.dashboard_release_gate_title),
                                    subtitle = stringResource(R.string.dashboard_release_gate_description),
                                    icon = Icons.Default.FactCheck,
                                    accent = VibeDesignTokens.Color.Success,
                                    tag = "dashboard_tool_quality_gate",
                                    onClick = { onNavigate(Screen.ProductQualityCenter) }
                                )
                                DashboardToolCard(
                                    title = stringResource(R.string.dashboard_team_validation_title),
                                    subtitle = stringResource(R.string.dashboard_team_validation_description),
                                    icon = Icons.Default.Verified,
                                    accent = VibeDesignTokens.Color.Violet,
                                    tag = "dashboard_tool_team_validation",
                                    onClick = { onNavigate(Screen.TeamValidation) }
                                )
                            }
                        }

                        item {
                            DashboardSectionHeader(
                                title = stringResource(R.string.dashboard_recent_issues),
                                badge = "${uiState.recentIssues.size}"
                            )
                        }

                        if (uiState.recentIssues.isEmpty() && uiState.error.isNullOrBlank()) {
                            item {
                                DashboardPanel(
                                    modifier = Modifier.fillMaxWidth().testTag("dashboard_empty_issues_card")
                                ) {
                                    Text(
                                        text = stringResource(R.string.dashboard_empty_issues),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = VibeDesignTokens.Color.TextMuted,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            items(uiState.recentIssues, key = { "issue-${it.id}" }) { issue ->
                                DashboardIssueRow(
                                    issue = issue,
                                    onClick = { onNavigate(Screen.Issues(issue.projectId)) }
                                )
                            }
                        }

                        item {
                            DashboardSectionHeader(
                                title = stringResource(R.string.recent_crashes),
                                badge = "${uiState.recentCrashes.size}"
                            )
                        }

                        if (uiState.recentCrashes.isEmpty() && uiState.error.isNullOrBlank()) {
                            item {
                                DashboardPanel(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dashboard_empty_crashes_card")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = VibeDesignTokens.Color.Success,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.no_recent_crashes),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = VibeDesignTokens.Color.TextMuted,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(uiState.recentCrashes, key = { it.id }) { crash ->
                                DashboardCrashRow(
                                    crash = crash,
                                    onClick = { onNavigate(Screen.CrashDetail(crash.id)) }
                                )
                            }
                        }
                    }
                }
            }

            if (showNotificationsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showNotificationsSheet = false },
                    containerColor = VibeDesignTokens.Color.SpacePanelRaised,
                    contentColor = VibeDesignTokens.Color.TextPrimary
                ) {
                    DashboardNotificationsSheetContent(
                        uiState = uiState,
                        onMarkNotificationRead = onMarkNotificationRead,
                        onMarkAllRead = onMarkAllNotificationsRead,
                        onClearAll = onClearNotifications,
                        onOpenCrash = { crashId ->
                            showNotificationsSheet = false
                            onNavigate(Screen.CrashDetail(crashId))
                        }
                    )
                }
            }
        }
    }
}
