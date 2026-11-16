package com.example.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.di.DependencyProvider
import com.example.domain.model.*
import com.example.presentation.navigation.Screen
import com.example.presentation.ui.components.PreExecutionEstimationDialog
import com.example.presentation.ui.components.TeamGovernanceDialog
import com.example.presentation.viewmodel.TeamDetailUiState
import com.example.presentation.viewmodel.TeamDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamDetailScreen(navController: NavController, teamId: String) {
    val context = LocalContext.current
    val viewModel: TeamDetailViewModel = viewModel {
        DependencyProvider.provideTeamDetailViewModel(context, teamId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = com.example.ui.theme.VibeDesignTokens.Color.SpaceBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.team?.name ?: "تفاصيل الفريق") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(
                        enabled = uiState.team != null && uiState.isAuthenticated,
                        onClick = { navController.navigate(Screen.TeamChat(teamId)) }
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = "محادثة الفريق")
                    }
                    IconButton(
                        enabled = uiState.team != null && uiState.isAuthenticated,
                        onClick = { navController.navigate(Screen.TeamUsage(teamId)) }
                    ) {
                        Icon(Icons.Default.QueryStats, contentDescription = "استهلاك الفريق")
                    }
                    IconButton(
                        enabled = uiState.team != null && uiState.isAuthenticated && !uiState.isMutating,
                        onClick = { viewModel.setGovernanceDialogVisible(true) }
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "إعدادات الحوكمة")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.example.ui.theme.VibeDesignTokens.Color.SpaceBackground,
                    titleContentColor = com.example.ui.theme.VibeDesignTokens.Color.TextPrimary,
                    navigationIconContentColor = com.example.ui.theme.VibeDesignTokens.Color.TextPrimary,
                    actionIconContentColor = com.example.ui.theme.VibeDesignTokens.Color.Cyan
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(com.example.ui.theme.VibeDesignTokens.Color.SpaceBackground)
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize().testTag("team_detail_loading"),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = com.example.ui.theme.VibeDesignTokens.Color.Cyan)
                }

                !uiState.isAuthenticated -> TeamDetailStatePanel(
                    title = "يلزم تسجيل الدخول",
                    message = TeamDetailViewModel.AUTH_REQUIRED_MESSAGE,
                    onRetry = null
                )

                uiState.team == null -> TeamDetailStatePanel(
                    title = "الفريق غير متاح",
                    message = uiState.loadError ?: "لم يتم العثور على الفريق.",
                    onRetry = { viewModel.refresh() }
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("team_detail_content"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    uiState.team?.let { team ->
                        item {
                            TeamInfoSection(
                                team = team,
                                onViewUsage = { navController.navigate(Screen.TeamUsage(teamId)) },
                                onEditGovernance = { viewModel.setGovernanceDialogVisible(true) }
                            )
                        }

                        item {
                            Text("أعضاء الفريق", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = com.example.ui.theme.VibeDesignTokens.Color.TextPrimary)
                        }

                        if (team.members.isEmpty()) {
                            item {
                                Text(
                                    "لا يوجد أعضاء في هذا الفريق بعد.",
                                    color = com.example.ui.theme.VibeDesignTokens.Color.TextMuted,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                )
                            }
                        } else {
                            items(team.members, key = { it.id }) { member ->
                                MemberStatusCard(member)
                            }
                        }

                        item {
                            HorizontalDivider(color = com.example.ui.theme.VibeDesignTokens.Color.Grid)
                            Spacer(Modifier.height(8.dp))
                            Text("تنفيذ مهمة برمجية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = com.example.ui.theme.VibeDesignTokens.Color.TextPrimary)
                        }

                        item {
                            ExecutionInputSection(
                                prompt = uiState.taskPrompt,
                                onPromptChange = { viewModel.updateTaskPrompt(it) },
                                onExecute = { viewModel.executeTeamTask() },
                                onEstimate = { viewModel.requestEstimation() },
                                isLoading = uiState.isExecuting || uiState.currentExecution?.status == ExecutionStatus.EXECUTING,
                                isEstimating = uiState.isEstimating
                            )
                        }

                        uiState.currentExecution?.let { execution ->
                            item { ExecutionStatusSection(execution) }

                            if (execution.sharedMemory.entries.isNotEmpty()) {
                                item { SharedMemorySection(execution.sharedMemory) }
                            }

                            if (execution.messages.isNotEmpty()) {
                                item { AgentMessagesSection(execution.messages) }
                            }

                            item {
                                Text("خطة التنفيذ والمهام", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = com.example.ui.theme.VibeDesignTokens.Color.TextPrimary)
                            }

                            items(execution.steps, key = { it.id }) { step ->
                                ExecutionStepCard(step)
                            }
                        }

                        if (uiState.currentExecution == null && uiState.executions.isNotEmpty()) {
                            item {
                                Text("سجل التنفيذ السابق", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = com.example.ui.theme.VibeDesignTokens.Color.TextPrimary)
                            }
                            items(uiState.executions, key = { it.id }) { previousExecution ->
                                ExecutionHistoryItem(previousExecution)
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showEstimationDialog && uiState.preEstimation != null) {
        PreExecutionEstimationDialog(
            estimation = uiState.preEstimation!!,
            onConfirm = { viewModel.executeTeamTask() },
            onDismiss = { viewModel.dismissEstimation() }
        )
    }

    if (uiState.showGovernanceDialog && uiState.team != null) {
        TeamGovernanceDialog(
            currentSettings = uiState.team!!.governance,
            onSave = { viewModel.updateGovernance(it) },
            onDismiss = { viewModel.setGovernanceDialogVisible(false) }
        )
    }
}


@Composable
internal fun TeamDetailStatePanel(
    title: String,
    message: String,
    onRetry: (() -> Unit)?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("team_detail_unavailable"),
        color = com.example.ui.theme.VibeDesignTokens.Color.SpacePanel,
        shape = RoundedCornerShape(com.example.ui.theme.VibeDesignTokens.Shape.Panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.VibeDesignTokens.Color.Grid)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Groups,
                contentDescription = null,
                tint = com.example.ui.theme.VibeDesignTokens.Color.Warning,
                modifier = Modifier.size(32.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = com.example.ui.theme.VibeDesignTokens.Color.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = com.example.ui.theme.VibeDesignTokens.Color.TextMuted
            )
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.example.ui.theme.VibeDesignTokens.Color.Cyan,
                        contentColor = com.example.ui.theme.VibeDesignTokens.Color.OnCyan
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("إعادة المحاولة")
                }
            }
        }
    }
}

@Composable
fun TeamInfoSection(
    team: ModelTeam,
    onViewUsage: () -> Unit,
    onEditGovernance: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
                    Text(team.collaborationStyle.labelAr, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(team.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Flag, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("الهدف: ${team.goal}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            
            // Governance Card within Info Section
            Surface(
                color = if (team.governance.freeOnly) Color(0xFF1B5E20).copy(alpha = 0.1f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (team.governance.freeOnly) Color(0xFF2E7D32).copy(alpha = 0.3f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (team.governance.freeOnly) Icons.Default.CheckCircle else Icons.Default.Security,
                            null,
                            tint = if (team.governance.freeOnly) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (team.governance.freeOnly) "Free AI Only (مجاني 100%)" else "سقف التكلفة: ${team.governance.maxTeamCostUsd}$",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (team.governance.freeOnly) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onViewUsage, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                            Icon(Icons.Default.BarChart, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("الاستهلاك", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = onEditGovernance, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Tune, null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabelValue("نمط التعاون", team.collaborationStyle.labelEn)
                LabelValue("أقصى طلبات", team.governance.maxApiRequests.toString())
                LabelValue("أقصى تزامن", team.governance.maxParallelRequests.toString())
            }
        }
    }
}

@Composable
fun LabelValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MemberStatusCard(member: TeamMember) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = when(member.status) {
                    MemberStatus.WORKING -> MaterialTheme.colorScheme.primary
                    MemberStatus.COMPLETED -> Color(0xFF2E7D32)
                    MemberStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                },
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when(member.status) {
                            MemberStatus.WORKING -> Icons.Default.Autorenew
                            MemberStatus.COMPLETED -> Icons.Default.Check
                            MemberStatus.FAILED -> Icons.Default.ErrorOutline
                            else -> Icons.Default.Person
                        },
                        null,
                        tint = Color.White
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(member.name, fontWeight = FontWeight.Bold)
                Text("${member.role.labelAr} • ${member.status.name}", style = MaterialTheme.typography.labelSmall)
                if (member.status == MemberStatus.WORKING) {
                    Text("المهمة: ${member.currentTask ?: "جاري التنفيذ..."}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun ExecutionInputSection(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onExecute: () -> Unit,
    onEstimate: () -> Unit,
    isLoading: Boolean,
    isEstimating: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            label = { Text("أدخل المهمة للفريق...") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onEstimate,
                modifier = Modifier.weight(1f),
                enabled = !isLoading && !isEstimating && prompt.isNotBlank()
            ) {
                if (isEstimating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Calculate, null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("تقدير الموارد", style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = onExecute,
                modifier = Modifier.weight(1.3f),
                enabled = !isLoading && prompt.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("بدء التنفيذ الجماعي", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun ExecutionStatusSection(execution: TeamExecution) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when(execution.status) {
                ExecutionStatus.COMPLETED -> Color(0xFFE8F5E9)
                ExecutionStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الحالة: ${execution.status.name}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (execution.status == ExecutionStatus.EXECUTING) CircularProgressIndicator(modifier = Modifier.size(16.dp))
            }
            if (execution.finalResult != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("النتيجة والتقرير النهائي:", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(execution.finalResult))
                        copied = true
                    }) {
                        Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (copied) "تم النسخ" else "نسخ")
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        execution.finalResult,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "ضوابط الحوكمة: لا يتم تطبيق الإصلاحات أو دمجها في الإنتاج دون موافقة المطور.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SharedMemorySection(memory: SharedMemory) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text("الذاكرة المشتركة للفريق", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            memory.entries.takeLast(5).forEach { entry ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Surface(
                        color = when(entry.type) {
                            MemoryType.DECISION -> Color(0xFF2E7D32)
                            MemoryType.FACT -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                        modifier = Modifier.size(6.dp).align(Alignment.CenterVertically)
                    ) {}
                    Spacer(Modifier.width(8.dp))
                    Text(entry.content, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun ExecutionStepCard(step: ExecutionStep) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = if (step.dependsOnIds.isNotEmpty()) 24.dp else 0.dp),
        border = if (step.status == StepStatus.FAILED) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${step.memberName} (${step.role})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                if (step.retryCount > 0) {
                    Text("إعادة: ${step.retryCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(end = 8.dp))
                }
                StepStatusBadge(step.status)
            }
            Text("المهمة: ${step.task}", style = MaterialTheme.typography.bodySmall)
            
            if (step.dependsOnIds.isNotEmpty()) {
                Text("تعتمد على: ${step.dependsOnIds.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }

            if (step.error != null) {
                Text("خطأ: ${step.error}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            if (step.output != null) {
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(4.dp)) {
                    Text(step.output, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun StepStatusBadge(status: StepStatus) {
    val color = when(status) {
        StepStatus.COMPLETED -> Color(0xFF2E7D32)
        StepStatus.FAILED -> MaterialTheme.colorScheme.error
        StepStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(color = color.copy(alpha = 0.1f), contentColor = color, shape = CircleShape) {
        Text(status.name, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun AgentMessagesSection(messages: List<AgentMessage>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Chat, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("سجل تواصل الفريق (Messages)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            messages.takeLast(3).forEach { msg ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("من: ${msg.fromMemberId}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(msg.content, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Divider(modifier = Modifier.alpha(0.2f).padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun ExecutionHistoryItem(execution: TeamExecution) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(execution.prompt, fontWeight = FontWeight.Bold, maxLines = 1)
            Row {
                Text(java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(execution.startedAt)), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(execution.status.name, style = MaterialTheme.typography.labelSmall, color = if (execution.status == ExecutionStatus.COMPLETED) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
            }
        }
    }
}
