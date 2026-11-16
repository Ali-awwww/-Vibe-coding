package com.example.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.di.DependencyProvider
import com.example.domain.model.*
import com.example.presentation.navigation.Screen
import com.example.presentation.ui.components.PreExecutionEstimationDialog
import com.example.presentation.ui.screens.teamchat.*
import com.example.presentation.ui.screens.teams.CosmicTeamsBackdrop
import com.example.presentation.ui.screens.teams.TeamsAuthRequiredCard
import com.example.presentation.ui.screens.teams.TeamsErrorBanner
import com.example.presentation.viewmodel.TeamChatTab
import com.example.presentation.viewmodel.TeamChatUiState
import com.example.presentation.viewmodel.TeamChatViewModel
import com.example.ui.theme.VibeDesignTokens

@Composable
fun TeamChatScreen(
    navController: NavController,
    teamId: String? = null,
    projectId: String? = null,
    viewModel: TeamChatViewModel = run {
        val context = LocalContext.current
        viewModel { DependencyProvider.provideTeamChatViewModel(context, teamId, projectId) }
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TeamChatScreenContent(
        uiState = uiState,
        onBack = { navController.popBackStack() },
        onNavigateToLogin = { navController.navigate(Screen.Login) },
        onNavigateToUsage = { navController.navigate(Screen.TeamUsage(it)) },
        onNavigateToDetail = { navController.navigate(Screen.TeamDetail(it)) },
        onSelectTab = viewModel::setActiveTab,
        onSelectTeam = viewModel::selectTeam,
        onSelectProject = viewModel::selectProject,
        onTaskPromptChange = viewModel::setTaskPrompt,
        onSetShowAttachmentDialog = viewModel::setShowAttachmentDialog,
        onAddAttachedFile = viewModel::addAttachedFile,
        onRemoveAttachedFile = viewModel::removeAttachedFile,
        onSendTask = { viewModel.sendTask() },
        onConfirmSendAfterEstimation = { viewModel.sendTask(skipEstimationCheck = true) },
        onDismissEstimation = viewModel::dismissEstimation,
        onEstimateTask = viewModel::requestEstimation,
        onPauseTeam = viewModel::pauseTeam,
        onResumeTeam = viewModel::resumeTeam,
        onStopTeam = viewModel::stopTeam,
        onRetryFailed = viewModel::retryFailedTasks,
        onSetShowAssignDialog = viewModel::setShowAssignDialog,
        onConfirmAssignTask = viewModel::assignDirectTask,
        onSetShowChangeAgentDialog = viewModel::setShowChangeAgentDialog,
        onConfirmChangeAgent = viewModel::changeMemberAgent,
        onSetShowInstructionDialog = viewModel::setShowInstructionDialog,
        onSendInstruction = viewModel::sendInstruction,
        onRefresh = viewModel::refresh,
        onClearError = viewModel::clearError,
        onClearStatusMessage = viewModel::clearStatusMessage
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamChatScreenContent(
    uiState: TeamChatUiState,
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToUsage: (String) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onSelectTab: (TeamChatTab) -> Unit,
    onSelectTeam: (ModelTeam) -> Unit,
    onSelectProject: (Project?) -> Unit,
    onTaskPromptChange: (String) -> Unit,
    onSetShowAttachmentDialog: (Boolean) -> Unit,
    onAddAttachedFile: (String, String, String, Long, String) -> Unit,
    onRemoveAttachedFile: (Int) -> Unit,
    onSendTask: () -> Unit,
    onConfirmSendAfterEstimation: () -> Unit,
    onDismissEstimation: () -> Unit,
    onEstimateTask: () -> Unit,
    onPauseTeam: () -> Unit,
    onResumeTeam: () -> Unit,
    onStopTeam: () -> Unit,
    onRetryFailed: () -> Unit,
    onSetShowAssignDialog: (Boolean) -> Unit,
    onConfirmAssignTask: (String, String) -> Unit,
    onSetShowChangeAgentDialog: (Boolean) -> Unit,
    onConfirmChangeAgent: (String, String) -> Unit,
    onSetShowInstructionDialog: (Boolean) -> Unit,
    onSendInstruction: (TeamInstruction) -> Unit,
    onRefresh: () -> Unit,
    onClearError: () -> Unit,
    onClearStatusMessage: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            onClearError()
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearStatusMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CosmicTeamsBackdrop(modifier = Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = VibeDesignTokens.Color.SpacePanel.copy(alpha = 0.92f),
                        titleContentColor = VibeDesignTokens.Color.TextPrimary,
                        navigationIconContentColor = VibeDesignTokens.Color.TextPrimary,
                        actionIconContentColor = VibeDesignTokens.Color.Cyan
                    ),
                    title = {
                        Column {
                            Text(
                                text = uiState.selectedTeam?.name ?: "محادثة فريق النماذج",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (uiState.selectedProject != null) "المشروع: ${uiState.selectedProject.name}" else "تنسيق هندسي متعدد النماذج",
                                style = MaterialTheme.typography.bodySmall,
                                color = VibeDesignTokens.Color.TextMuted
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("team_chat_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    },
                    actions = {
                        if (uiState.isAuthenticated) {
                            if (uiState.isExecuting && !uiState.isPaused) {
                                IconButton(
                                    onClick = onPauseTeam,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("top_bar_pause_team")
                                ) {
                                    Icon(Icons.Default.PauseCircle, contentDescription = "إيقاف مؤقت", tint = VibeDesignTokens.Color.Error)
                                }
                            } else if (uiState.isPaused) {
                                IconButton(
                                    onClick = onResumeTeam,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("top_bar_resume_team")
                                ) {
                                    Icon(Icons.Default.PlayCircle, contentDescription = "استكمال", tint = VibeDesignTokens.Color.Cyan)
                                }
                            }

                            uiState.selectedTeam?.let { team ->
                                IconButton(
                                    onClick = { onNavigateToUsage(team.id) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("team_chat_usage_action")
                                ) {
                                    Icon(Icons.Default.QueryStats, contentDescription = "لوحة الاستخدام")
                                }
                                IconButton(
                                    onClick = { onNavigateToDetail(team.id) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("team_chat_detail_action")
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = "تفاصيل الفريق")
                                }
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                if (!uiState.isAuthenticated) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TeamsAuthRequiredCard(
                            title = "المصادقة مطلوبة لمحادثة الفريق",
                            subtitle = "سجّل الدخول للوصول إلى فرق النماذج، المشاريع المصرح بها، وسجل التنسيق المحفوظ.",
                            onSignIn = onNavigateToLogin,
                            modifier = Modifier.testTag("team_chat_auth_card")
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                    ) {
                        TeamLiveStatusBar(
                            team = uiState.selectedTeam,
                            currentExecution = uiState.currentExecution,
                            isExecuting = uiState.isExecuting,
                            isPaused = uiState.isPaused,
                            isCloudConfigured = uiState.isCloudConfigured,
                            durationSeconds = uiState.executionDurationSeconds
                        )

                        if (uiState.error != null) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                TeamsErrorBanner(
                                    message = uiState.error,
                                    onRetry = onRefresh,
                                    onDismiss = onClearError
                                )
                            }
                        }

                        ScrollableTabRow(
                            selectedTabIndex = uiState.activeTab.ordinal,
                            containerColor = VibeDesignTokens.Color.SpacePanel.copy(alpha = 0.85f),
                            contentColor = VibeDesignTokens.Color.Cyan,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("team_chat_tab_row"),
                            edgePadding = 12.dp
                        ) {
                            TeamChatTab.values().forEach { tab ->
                                Tab(
                                    selected = uiState.activeTab == tab,
                                    onClick = { onSelectTab(tab) },
                                    modifier = Modifier.testTag("team_chat_tab_${tab.name}"),
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = when (tab) {
                                                    TeamChatTab.CHAT_INPUT -> Icons.Default.Chat
                                                    TeamChatTab.LIVE_COLLABORATION -> Icons.Default.Groups
                                                    TeamChatTab.ACTIVITY_LOG -> Icons.Default.ReceiptLong
                                                    TeamChatTab.INSTRUCTIONS -> Icons.Default.Campaign
                                                    TeamChatTab.RESULTS_COMPARE -> Icons.Default.CompareArrows
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(tab.labelAr, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (uiState.activeTab) {
                                TeamChatTab.CHAT_INPUT -> {
                                    TeamChatInputPanel(
                                        teams = uiState.teams,
                                        selectedTeam = uiState.selectedTeam,
                                        projects = uiState.projects,
                                        selectedProject = uiState.selectedProject,
                                        taskPrompt = uiState.taskPrompt,
                                        attachedFiles = uiState.attachedFiles,
                                        currentExecution = uiState.currentExecution,
                                        isExecuting = uiState.isExecuting,
                                        isPaused = uiState.isPaused,
                                        onSelectTeam = onSelectTeam,
                                        onSelectProject = onSelectProject,
                                        onTaskPromptChange = onTaskPromptChange,
                                        onOpenAttachDialog = { onSetShowAttachmentDialog(true) },
                                        onRemoveAttachedFile = onRemoveAttachedFile,
                                        onSendTask = onSendTask,
                                        onEstimateTask = onEstimateTask,
                                        onPauseTeam = onPauseTeam,
                                        onResumeTeam = onResumeTeam,
                                        onStopTeam = onStopTeam,
                                        onRetryFailed = onRetryFailed,
                                        onOpenAssignDialog = { onSetShowAssignDialog(true) },
                                        onOpenChangeAgentDialog = { onSetShowChangeAgentDialog(true) },
                                        onOpenInstructionDialog = { onSetShowInstructionDialog(true) },
                                        onReviewResultClick = { onSelectTab(TeamChatTab.RESULTS_COMPARE) }
                                    )
                                }

                                TeamChatTab.LIVE_COLLABORATION -> {
                                    LiveTeamCollaborationPanel(
                                        team = uiState.selectedTeam,
                                        currentExecution = uiState.currentExecution,
                                        isExecuting = uiState.isExecuting,
                                        isPaused = uiState.isPaused,
                                        durationSeconds = uiState.executionDurationSeconds,
                                        onAssignTaskToMember = { onSetShowAssignDialog(true) },
                                        onChangeMemberModel = { onSetShowChangeAgentDialog(true) },
                                        onRetryStep = { stepId ->
                                            onSendInstruction(TeamInstruction.RetryStep(stepId))
                                        }
                                    )
                                }

                                TeamChatTab.ACTIVITY_LOG -> {
                                    TeamActivityLogPanel(
                                        team = uiState.selectedTeam,
                                        execution = uiState.currentExecution
                                    )
                                }

                                TeamChatTab.INSTRUCTIONS -> {
                                    TeamInstructionsTabContent(
                                        execution = uiState.currentExecution,
                                        hasSelectedTeam = uiState.selectedTeam != null,
                                        onOpenInstructionDialog = { onSetShowInstructionDialog(true) }
                                    )
                                }

                                TeamChatTab.RESULTS_COMPARE -> {
                                    TeamResultReviewPanel(
                                        team = uiState.selectedTeam,
                                        execution = uiState.currentExecution
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showEstimationDialog && uiState.preEstimation != null) {
        PreExecutionEstimationDialog(
            estimation = uiState.preEstimation,
            onConfirm = onConfirmSendAfterEstimation,
            onDismiss = onDismissEstimation
        )
    }

    if (uiState.showAttachmentDialog) {
        TeamFileAttachmentDialog(
            onDismiss = { onSetShowAttachmentDialog(false) },
            onAddFile = onAddAttachedFile
        )
    }

    if (uiState.showAssignDialog && uiState.selectedTeam != null) {
        DirectTaskAssignmentDialog(
            team = uiState.selectedTeam,
            onDismiss = { onSetShowAssignDialog(false) },
            onConfirmAssign = onConfirmAssignTask
        )
    }

    if (uiState.showChangeAgentDialog && uiState.selectedTeam != null) {
        ChangeAgentDialog(
            team = uiState.selectedTeam,
            availableModels = uiState.availableModels,
            onDismiss = { onSetShowChangeAgentDialog(false) },
            onConfirmChange = onConfirmChangeAgent
        )
    }

    if (uiState.showInstructionDialog && uiState.selectedTeam != null) {
        TeamInstructionDialog(
            team = uiState.selectedTeam,
            steps = uiState.currentExecution?.steps ?: emptyList(),
            onDismiss = { onSetShowInstructionDialog(false) },
            onSendInstruction = onSendInstruction
        )
    }
}
