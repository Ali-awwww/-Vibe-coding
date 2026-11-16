package com.example.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.domain.model.Application
import com.example.domain.model.Role
import com.example.presentation.navigation.Screen
import com.example.presentation.ui.screens.dashboard.CosmicDashboardBackdrop
import com.example.presentation.ui.screens.projects.*
import com.example.presentation.viewmodel.ProjectsUiState
import com.example.presentation.viewmodel.ProjectsViewModel
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens

@Composable
fun ProjectsScreen(
    navController: NavController,
    viewModel: ProjectsViewModel,
    initialTab: Int = 0,
    onSignIn: () -> Unit = { navController.navigate(Screen.Login) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProjectsScreenContent(
        uiState = uiState,
        initialTab = initialTab,
        onNavigateBack = { navController.popBackStack() },
        onNavigate = { route -> navController.navigate(route) },
        onRefresh = viewModel::refresh,
        onSelectProject = viewModel::selectProject,
        onAddProject = viewModel::addProject,
        onDeleteProject = viewModel::deleteProject,
        onAddApplication = viewModel::addApplication,
        onDeleteApplication = viewModel::deleteApplication,
        onAddMember = viewModel::addMember,
        onUpdateMemberRole = viewModel::updateMemberRole,
        onRemoveMember = viewModel::removeMember,
        onUpdateSecurityPolicy = viewModel::updateSecurityPolicy,
        onPurgeData = viewModel::purgeOldCrashes,
        onClearNotifications = viewModel::clearNotifications,
        onSignIn = onSignIn
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectsScreenContent(
    uiState: ProjectsUiState,
    initialTab: Int = 0,
    onNavigateBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onRefresh: () -> Unit,
    onSelectProject: (String) -> Unit,
    onAddProject: (name: String, desc: String, retentionDays: Int, piiScrubbing: Boolean) -> Unit,
    onDeleteProject: (String) -> Unit,
    onAddApplication: (projectId: String, name: String, platform: String, packageName: String) -> Unit,
    onDeleteApplication: (String) -> Unit,
    onAddMember: (projectId: String, email: String, role: Role) -> Unit,
    onUpdateMemberRole: (projectId: String, memberUserId: String, role: Role) -> Unit,
    onRemoveMember: (projectId: String, memberUserId: String) -> Unit,
    onUpdateSecurityPolicy: (projectId: String, retentionDays: Int, piiScrubbing: Boolean) -> Unit,
    onPurgeData: (String) -> Unit,
    onClearNotifications: () -> Unit,
    onSignIn: () -> Unit = {}
) {
    var showAddProjectDialog by remember { mutableStateOf(false) }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showSecurityPolicyDialog by remember { mutableStateOf(false) }
    var showDeleteProjectConfirmDialog by remember { mutableStateOf(false) }
    var appPendingDeletion by remember { mutableStateOf<Application?>(null) }
    var selectedTab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 4)) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message, uiState.error) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onClearNotifications()
        } ?: uiState.error?.let {
            snackbarHostState.showSnackbar("خطأ: $it")
            onClearNotifications()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CosmicDashboardBackdrop(modifier = Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = VibeDesignTokens.Color.SpacePanel,
                        titleContentColor = VibeDesignTokens.Color.TextPrimary,
                        navigationIconContentColor = VibeDesignTokens.Color.Cyan,
                        actionIconContentColor = VibeDesignTokens.Color.Cyan
                    ),
                    title = {
                        Column {
                            Text(
                                text = "إدارة المشاريع والتطبيقات",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            uiState.selectedProject?.let { proj ->
                                val syncLabel = if (uiState.isCloudConfigured) "CLOUD" else "LOCAL"
                                Text(
                                    text = "${proj.name} • ${uiState.userRoleInSelectedProject?.name ?: "MEMBER"} • $syncLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMono,
                                    color = VibeDesignTokens.Color.TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("projects_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onRefresh,
                            enabled = uiState.isAuthenticated && !uiState.isMutating,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("projects_refresh_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث")
                        }
                        IconButton(
                            onClick = { showAddProjectDialog = true },
                            enabled = uiState.isAuthenticated && !uiState.isMutating,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("projects_add_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "مشروع جديد")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize()
                ) {
                    if (uiState.isMutating) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("projects_mutating_indicator"),
                            color = VibeDesignTokens.Color.Cyan,
                            trackColor = VibeDesignTokens.Color.SpacePanelRaised
                        )
                    }

                    if (!uiState.isAuthenticated && !uiState.isLoading) {
                        ProjectsAuthRequiredCard(onSignIn = onSignIn)
                        return@Column
                    }

                    if (!uiState.error.isNullOrBlank()) {
                        ProjectsErrorBanner(
                            error = uiState.error,
                            isCloudConfigured = uiState.isCloudConfigured,
                            onRetry = onRefresh
                        )
                    }

                    if (uiState.projects.size > 1) {
                        PrimaryScrollableTabRow(
                            selectedTabIndex = uiState.projects
                                .indexOfFirst { it.id == uiState.selectedProject?.id }
                                .coerceAtLeast(0),
                            containerColor = VibeDesignTokens.Color.SpacePanelRaised,
                            contentColor = VibeDesignTokens.Color.Cyan,
                            edgePadding = 16.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("projects_selector_row")
                        ) {
                            uiState.projects.forEach { proj ->
                                Tab(
                                    selected = uiState.selectedProject?.id == proj.id,
                                    onClick = { onSelectProject(proj.id) },
                                    modifier = Modifier.testTag("project_selector_tab_${proj.id}"),
                                    text = {
                                        Text(
                                            text = proj.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }

                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = VibeDesignTokens.Color.SpacePanel,
                        contentColor = VibeDesignTokens.Color.Cyan,
                        edgePadding = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("projects_section_tabs")
                    ) {
                        val tabs = listOf(
                            Triple("المشاريع", Icons.Default.Folder, "projects_tab_projects"),
                            Triple("التطبيقات", Icons.Default.Apps, "projects_tab_applications"),
                            Triple("أعضاء الفريق", Icons.Default.Group, "projects_tab_members"),
                            Triple("الأمان والاحتفاظ", Icons.Default.Shield, "projects_tab_security"),
                            Triple("سجل التدقيق", Icons.Default.History, "projects_tab_audit")
                        )
                        tabs.forEachIndexed { index, (label, icon, tag) ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                modifier = Modifier.testTag(tag),
                                text = { Text(label, maxLines = 1) },
                                icon = { Icon(icon, contentDescription = null) }
                            )
                        }
                    }

                    if (uiState.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("projects_loading"),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = VibeDesignTokens.Color.Cyan)
                        }
                    } else {
                        when (selectedTab) {
                            0 -> ProjectsListTab(
                                uiState = uiState,
                                onSelectProject = { onSelectProject(it.id) },
                                onOpenIntegrations = { onNavigate(Screen.Integrations(it.id)) },
                                onOpenCrashes = { onNavigate(Screen.Crashes(projectId = it.id)) },
                                onCreateProject = { showAddProjectDialog = true }
                            )
                            1 -> ApplicationsTab(
                                uiState = uiState,
                                onNavigate = onNavigate,
                                onAddApplication = { showAddAppDialog = true },
                                onRequestDeleteApp = { appPendingDeletion = it }
                            )
                            2 -> TeamMembersTab(
                                uiState = uiState,
                                onAddMember = { showAddMemberDialog = true },
                                onUpdateRole = { member, role ->
                                    uiState.selectedProject?.let { proj ->
                                        onUpdateMemberRole(proj.id, member.userId, role)
                                    }
                                },
                                onRemoveMember = { member ->
                                    uiState.selectedProject?.let { proj ->
                                        onRemoveMember(proj.id, member.userId)
                                    }
                                }
                            )
                            3 -> SecurityAndRetentionTab(
                                uiState = uiState,
                                onEditPolicy = { showSecurityPolicyDialog = true },
                                onPurgeData = {
                                    uiState.selectedProject?.let { onPurgeData(it.id) }
                                },
                                onDeleteProject = { showDeleteProjectConfirmDialog = true }
                            )
                            4 -> AuditLogTab(uiState = uiState)
                        }
                    }
                }
            }

            if (showAddProjectDialog) {
                AddProjectSecurityDialog(
                    onDismiss = { showAddProjectDialog = false },
                    onConfirm = { name, desc, retentionDays, piiScrubbing ->
                        onAddProject(name, desc, retentionDays, piiScrubbing)
                        showAddProjectDialog = false
                    }
                )
            }

            if (showAddAppDialog && uiState.selectedProject != null) {
                AddApplicationDialog(
                    onDismiss = { showAddAppDialog = false },
                    onConfirm = { name, platform, pkg ->
                        onAddApplication(uiState.selectedProject.id, name, platform, pkg)
                        showAddAppDialog = false
                    }
                )
            }

            if (showAddMemberDialog && uiState.selectedProject != null) {
                AddMemberDialog(
                    onDismiss = { showAddMemberDialog = false },
                    onConfirm = { email, role ->
                        onAddMember(uiState.selectedProject.id, email, role)
                        showAddMemberDialog = false
                    }
                )
            }

            if (showSecurityPolicyDialog && uiState.selectedProject != null) {
                EditSecurityPolicyDialog(
                    currentRetention = uiState.selectedProject.dataRetentionDays,
                    currentPii = uiState.selectedProject.piiScrubbingEnabled,
                    onDismiss = { showSecurityPolicyDialog = false },
                    onConfirm = { days, pii ->
                        onUpdateSecurityPolicy(uiState.selectedProject.id, days, pii)
                        showSecurityPolicyDialog = false
                    }
                )
            }

            if (showDeleteProjectConfirmDialog && uiState.selectedProject != null) {
                DeleteProjectConfirmDialog(
                    project = uiState.selectedProject,
                    onDismiss = { showDeleteProjectConfirmDialog = false },
                    onConfirm = {
                        onDeleteProject(uiState.selectedProject.id)
                        showDeleteProjectConfirmDialog = false
                    }
                )
            }

            appPendingDeletion?.let { targetApp ->
                DeleteApplicationConfirmDialog(
                    application = targetApp,
                    onDismiss = { appPendingDeletion = null },
                    onConfirm = {
                        onDeleteApplication(targetApp.id)
                        appPendingDeletion = null
                    }
                )
            }
        }
    }
}
