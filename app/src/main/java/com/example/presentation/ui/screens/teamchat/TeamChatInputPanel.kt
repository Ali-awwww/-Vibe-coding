package com.example.presentation.ui.screens.teamchat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.*
import com.example.ui.theme.VibeDesignTokens

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeamChatInputPanel(
    teams: List<ModelTeam>,
    selectedTeam: ModelTeam?,
    projects: List<Project>,
    selectedProject: Project?,
    taskPrompt: String,
    attachedFiles: List<AttachedFileContext>,
    currentExecution: TeamExecution?,
    isExecuting: Boolean,
    isPaused: Boolean,
    onSelectTeam: (ModelTeam) -> Unit,
    onSelectProject: (Project?) -> Unit,
    onTaskPromptChange: (String) -> Unit,
    onOpenAttachDialog: () -> Unit,
    onRemoveAttachedFile: (Int) -> Unit,
    onSendTask: () -> Unit,
    onEstimateTask: () -> Unit,
    onPauseTeam: () -> Unit,
    onResumeTeam: () -> Unit,
    onStopTeam: () -> Unit,
    onRetryFailed: () -> Unit,
    onOpenAssignDialog: () -> Unit,
    onOpenChangeAgentDialog: () -> Unit,
    onOpenInstructionDialog: () -> Unit,
    onReviewResultClick: () -> Unit
) {
    var expandedTeamMenu by remember { mutableStateOf(false) }
    var expandedProjectMenu by remember { mutableStateOf(false) }

    val quickTaskTemplates = remember {
        listOf(
            "تحقيق في انهيار NullPointerException وفحص السجلات البرمجية",
            "مراجعة كود شاملة مع تدقيق أمني واكتشاف ثغرات الذاكرة",
            "بناء وحدة معمارية لمعالجة مهام المزامنة السحابية",
            "إعادة هيكلة طبقة البيانات وتوليد اختبارات وحدوية شاملة"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedCard(
                    onClick = { expandedTeamMenu = true },
                    colors = CardDefaults.outlinedCardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
                    border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("select_team_dropdown_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الفريق النشط", style = MaterialTheme.typography.labelSmall, color = VibeDesignTokens.Color.TextMuted)
                            Text(
                                text = selectedTeam?.name ?: "اختر الفريق",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = VibeDesignTokens.Color.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = VibeDesignTokens.Color.Cyan)
                    }
                }

                DropdownMenu(
                    expanded = expandedTeamMenu,
                    onDismissRequest = { expandedTeamMenu = false }
                ) {
                    teams.forEach { team ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(team.name, fontWeight = FontWeight.Bold)
                                    Text("${team.members.size} أعضاء • ${team.collaborationStyle.labelAr}", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            onClick = {
                                onSelectTeam(team)
                                expandedTeamMenu = false
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                OutlinedCard(
                    onClick = { expandedProjectMenu = true },
                    colors = CardDefaults.outlinedCardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
                    border = BorderStroke(1.dp, VibeDesignTokens.Color.Violet.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("select_project_dropdown_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("المشروع المرتبط", style = MaterialTheme.typography.labelSmall, color = VibeDesignTokens.Color.TextMuted)
                            Text(
                                text = selectedProject?.name ?: "بدون مشروع محدد",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = VibeDesignTokens.Color.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = VibeDesignTokens.Color.Violet)
                    }
                }

                DropdownMenu(
                    expanded = expandedProjectMenu,
                    onDismissRequest = { expandedProjectMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("بدون مشروع محدد (مستقل)") },
                        onClick = {
                            onSelectProject(null)
                            expandedProjectMenu = false
                        }
                    )
                    projects.forEach { project ->
                        DropdownMenuItem(
                            text = { Text(project.name, fontWeight = FontWeight.Bold) },
                            onClick = {
                                onSelectProject(project)
                                expandedProjectMenu = false
                            }
                        )
                    }
                }
            }
        }

        Text("نماذج مهام هندسية سريعة:", style = MaterialTheme.typography.labelSmall, color = VibeDesignTokens.Color.TextMuted)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(quickTaskTemplates.size) { idx ->
                val template = quickTaskTemplates[idx]
                SuggestionChip(
                    onClick = { onTaskPromptChange(template) },
                    label = { Text(template.take(28) + "...", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp), tint = VibeDesignTokens.Color.Cyan) }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
            colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
            border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.28f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نص المهمة البرمجية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VibeDesignTokens.Color.TextPrimary)
                    if (taskPrompt.isNotEmpty()) {
                        TextButton(
                            onClick = { onTaskPromptChange("") },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("مسح", style = MaterialTheme.typography.labelSmall, color = VibeDesignTokens.Color.Cyan)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = taskPrompt,
                    onValueChange = onTaskPromptChange,
                    placeholder = { Text("صف المشكلة، كود الانهيار، أو الميزة التي ترغب في تكليف فريق النماذج بإنجازها...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp)
                        .testTag("team_task_prompt_input"),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Control)
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = VibeDesignTokens.Color.Cyan)
                        Spacer(Modifier.width(6.dp))
                        Text("الملفات المرفقة (${attachedFiles.size}):", style = MaterialTheme.typography.labelMedium, color = VibeDesignTokens.Color.TextPrimary)
                    }

                    TextButton(
                        onClick = onOpenAttachDialog,
                        modifier = Modifier.testTag("open_attach_dialog_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إرفاق ملف مسموح به")
                    }
                }

                if (attachedFiles.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        itemsIndexed(attachedFiles) { index, file ->
                            InputChip(
                                selected = true,
                                onClick = { },
                                label = { Text("${file.name} (${file.sizeBytes / 1024} KB)", style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { onRemoveAttachedFile(index) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "إزالة الملف", modifier = Modifier.size(14.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSendTask,
                    enabled = !isExecuting && selectedTeam != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibeDesignTokens.Color.Cyan,
                        contentColor = VibeDesignTokens.Color.OnCyan
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .minimumInteractiveComponentSize()
                        .testTag("send_team_task_button")
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = VibeDesignTokens.Color.OnCyan, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("جارٍ التنفيذ...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إرسال المهمة", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onEstimateTask,
                    enabled = !isExecuting && selectedTeam != null,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("estimate_team_task_button")
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تقدير الموارد")
                }
            }

            Surface(
                color = VibeDesignTokens.Color.SpacePanelRaised,
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                border = BorderStroke(1.dp, VibeDesignTokens.Color.Grid),
                modifier = Modifier.fillMaxWidth()
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPauseTeam,
                        enabled = isExecuting && !isPaused,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = VibeDesignTokens.Color.Warning.copy(alpha = 0.22f)),
                        modifier = Modifier.testTag("pause_team_button")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إيقاف مؤقت", style = MaterialTheme.typography.labelSmall)
                    }

                    FilledTonalButton(
                        onClick = onResumeTeam,
                        enabled = isPaused,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = VibeDesignTokens.Color.Cyan.copy(alpha = 0.22f)),
                        modifier = Modifier.testTag("resume_team_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("استكمال", style = MaterialTheme.typography.labelSmall)
                    }

                    FilledTonalButton(
                        onClick = onStopTeam,
                        enabled = isExecuting || isPaused,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = VibeDesignTokens.Color.Error.copy(alpha = 0.22f)),
                        modifier = Modifier.testTag("stop_team_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إيقاف", style = MaterialTheme.typography.labelSmall)
                    }

                    val hasFailed = currentExecution?.steps?.any { it.status == StepStatus.FAILED } == true
                    FilledTonalButton(
                        onClick = onRetryFailed,
                        enabled = hasFailed && !isExecuting,
                        modifier = Modifier.testTag("retry_failed_tasks_button")
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إعادة الفاشل", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenAssignDialog,
                    enabled = selectedTeam != null,
                    modifier = Modifier.testTag("assign_task_button")
                ) {
                    Icon(Icons.Default.AssignmentInd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إسناد مهمة", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onOpenChangeAgentDialog,
                    enabled = selectedTeam != null,
                    modifier = Modifier.testTag("change_agent_button")
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تبديل نموذج", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onOpenInstructionDialog,
                    enabled = selectedTeam != null,
                    modifier = Modifier.testTag("send_instructions_button")
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("توجيه", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = onReviewResultClick,
                    enabled = currentExecution != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibeDesignTokens.Color.Violet,
                        contentColor = VibeDesignTokens.Color.TextPrimary
                    ),
                    modifier = Modifier.testTag("review_results_button")
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("مراجعة", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
