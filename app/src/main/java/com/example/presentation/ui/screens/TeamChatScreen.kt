package com.example.presentation.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
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
import com.example.presentation.viewmodel.TeamChatTab
import com.example.presentation.viewmodel.TeamChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamChatScreen(
    navController: NavController,
    teamId: String? = null,
    projectId: String? = null
) {
    val context = LocalContext.current
    val viewModel: TeamChatViewModel = viewModel {
        DependencyProvider.provideTeamChatViewModel(context, teamId, projectId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            uiState.selectedTeam?.name ?: "محادثة فريق النماذج",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (uiState.selectedProject != null) "المشروع: ${uiState.selectedProject?.name}" else "تنسيق هندسي متعدد النماذج",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("team_chat_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Quick Pause / Resume if executing
                    if (uiState.isExecuting && !uiState.isPaused) {
                        IconButton(
                            onClick = { viewModel.pauseTeam() },
                            modifier = Modifier.testTag("top_bar_pause_team")
                        ) {
                            Icon(Icons.Default.PauseCircle, contentDescription = "Pause", tint = MaterialTheme.colorScheme.error)
                        }
                    } else if (uiState.isPaused) {
                        IconButton(
                            onClick = { viewModel.resumeTeam() },
                            modifier = Modifier.testTag("top_bar_resume_team")
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Navigate to Team Usage
                    uiState.selectedTeam?.let { team ->
                        IconButton(onClick = { navController.navigate(Screen.TeamUsage(team.id)) }) {
                            Icon(Icons.Default.QueryStats, contentDescription = "Usage Dashboard")
                        }
                        IconButton(onClick = { navController.navigate(Screen.TeamDetail(team.id)) }) {
                            Icon(Icons.Default.Info, contentDescription = "Team Detail")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live Status & Governance Summary Bar
            TeamLiveStatusBar(
                team = uiState.selectedTeam,
                currentExecution = uiState.currentExecution,
                isExecuting = uiState.isExecuting,
                isPaused = uiState.isPaused,
                durationSeconds = uiState.executionDurationSeconds
            )

            // Primary Navigation TabRow (5 core requirements)
            ScrollableTabRow(
                selectedTabIndex = uiState.activeTab.ordinal,
                modifier = Modifier.fillMaxWidth().testTag("team_chat_tab_row"),
                edgePadding = 12.dp
            ) {
                TeamChatTab.values().forEach { tab ->
                    Tab(
                        selected = uiState.activeTab == tab,
                        onClick = { viewModel.setActiveTab(tab) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when (tab) {
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

            // Tab Content
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
                            onSelectTeam = { viewModel.selectTeam(it) },
                            onSelectProject = { viewModel.selectProject(it) },
                            onTaskPromptChange = { viewModel.setTaskPrompt(it) },
                            onOpenAttachDialog = { viewModel.setShowAttachmentDialog(true) },
                            onRemoveAttachedFile = { viewModel.removeAttachedFile(it) },
                            onSendTask = { viewModel.sendTask() },
                            onEstimateTask = { viewModel.requestEstimation() },
                            onPauseTeam = { viewModel.pauseTeam() },
                            onResumeTeam = { viewModel.resumeTeam() },
                            onStopTeam = { viewModel.stopTeam() },
                            onRetryFailed = { viewModel.retryFailedTasks() },
                            onOpenAssignDialog = { viewModel.setShowAssignDialog(true) },
                            onOpenChangeAgentDialog = { viewModel.setShowChangeAgentDialog(true) },
                            onOpenInstructionDialog = { viewModel.setShowInstructionDialog(true) },
                            onReviewResultClick = { viewModel.setActiveTab(TeamChatTab.RESULTS_COMPARE) }
                        )
                    }

                    TeamChatTab.LIVE_COLLABORATION -> {
                        LiveTeamCollaborationPanel(
                            team = uiState.selectedTeam,
                            currentExecution = uiState.currentExecution,
                            isExecuting = uiState.isExecuting,
                            isPaused = uiState.isPaused,
                            durationSeconds = uiState.executionDurationSeconds,
                            onAssignTaskToMember = { memberId ->
                                viewModel.setShowAssignDialog(true)
                            },
                            onChangeMemberModel = { memberId ->
                                viewModel.setShowChangeAgentDialog(true)
                            },
                            onRetryStep = { stepId ->
                                viewModel.sendInstruction(TeamInstruction.RetryStep(stepId))
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
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("مركز إرسال التعليمات والتوجيهات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("أرسل توجيهات استراتيجية للقائد، أو مهمة لعضو، أو ملاحظة للفريق، أو اطلب مراجعة نقدية.", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.setShowInstructionDialog(true) },
                                    modifier = Modifier.fillMaxWidth().testTag("open_send_instruction_dialog")
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("فتح نموذج إرسال التعليمات")
                                }
                            }

                            // Show recent instructions and team feedback
                            Text("التعليمات والتوجيهات المرسلة مؤخراً:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            val instructionMessages = uiState.currentExecution?.messages?.filter {
                                it.type == MessageType.INSTRUCTION || it.type == MessageType.REVIEW
                            } ?: emptyList()

                            if (instructionMessages.isEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Text(
                                        "لم يتم إرسال تعليمات مخصصة بعد خلال جلسة العمل الحالية.",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                instructionMessages.forEach { msg ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    if (msg.fromMemberId == "USER") "منك (المستخدم)" else "رد: ${msg.fromMemberId}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    if (msg.type == MessageType.INSTRUCTION) "تعليمة" else "مراجعة نقدية",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(msg.content, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
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

    // Pre-execution Estimation Dialog
    if (uiState.showEstimationDialog && uiState.preEstimation != null) {
        PreExecutionEstimationDialog(
            estimation = uiState.preEstimation!!,
            onConfirm = { viewModel.sendTask(skipEstimationCheck = true) },
            onDismiss = { viewModel.dismissEstimation() }
        )
    }

    // Attach File Context Dialog
    if (uiState.showAttachmentDialog) {
        TeamFileAttachmentDialog(
            onDismiss = { viewModel.setShowAttachmentDialog(false) },
            onAddFile = { name, path, content, size, type ->
                viewModel.addAttachedFile(name, path, content, size, type)
            }
        )
    }

    // Direct Task Assignment Dialog
    if (uiState.showAssignDialog && uiState.selectedTeam != null) {
        DirectTaskAssignmentDialog(
            team = uiState.selectedTeam!!,
            onDismiss = { viewModel.setShowAssignDialog(false) },
            onConfirmAssign = { memberId, prompt ->
                viewModel.assignDirectTask(memberId, prompt)
            }
        )
    }

    // Change Agent Dialog
    if (uiState.showChangeAgentDialog && uiState.selectedTeam != null) {
        ChangeAgentDialog(
            team = uiState.selectedTeam!!,
            availableModels = uiState.availableModels,
            onDismiss = { viewModel.setShowChangeAgentDialog(false) },
            onConfirmChange = { memberId, newModelId ->
                viewModel.changeMemberAgent(memberId, newModelId)
            }
        )
    }

    // Send Instruction Dialog
    if (uiState.showInstructionDialog && uiState.selectedTeam != null) {
        TeamInstructionDialog(
            team = uiState.selectedTeam!!,
            steps = uiState.currentExecution?.steps ?: emptyList(),
            onDismiss = { viewModel.setShowInstructionDialog(false) },
            onSendInstruction = { instruction ->
                viewModel.sendInstruction(instruction)
            }
        )
    }
}

@Composable
fun TeamLiveStatusBar(
    team: ModelTeam?,
    currentExecution: TeamExecution?,
    isExecuting: Boolean,
    isPaused: Boolean,
    durationSeconds: Long
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Members count & Free status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("${team?.members?.size ?: 0} أعضاء", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                Spacer(Modifier.width(10.dp))
                if (team?.governance?.freeOnly == true) {
                    Surface(
                        color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "Free AI Only",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Right: Duration & Status
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isExecuting || isPaused || durationSeconds > 0) {
                    val mins = durationSeconds / 60
                    val secs = durationSeconds % 60
                    Text(
                        String.format("%02d:%02d", mins, secs),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                }

                Surface(
                    color = when {
                        isPaused -> MaterialTheme.colorScheme.tertiaryContainer
                        isExecuting -> MaterialTheme.colorScheme.primaryContainer
                        currentExecution?.status == ExecutionStatus.COMPLETED -> Color(0xFF2E7D32).copy(alpha = 0.2f)
                        currentExecution?.status == ExecutionStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surface
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        when {
                            isPaused -> "موقوف"
                            isExecuting -> "يعمل"
                            currentExecution?.status == ExecutionStatus.COMPLETED -> "ناجح"
                            currentExecution?.status == ExecutionStatus.FAILED -> "فشل"
                            else -> "خامل"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
