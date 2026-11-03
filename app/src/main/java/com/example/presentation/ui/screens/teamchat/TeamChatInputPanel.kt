package com.example.presentation.ui.screens.teamchat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.*
import com.example.presentation.viewmodel.TeamChatTab

@OptIn(ExperimentalMaterial3Api::class)
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
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Selection Row: Team & Project
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Team Selector
            Box(modifier = Modifier.weight(1f)) {
                OutlinedCard(
                    onClick = { expandedTeamMenu = true },
                    modifier = Modifier.fillMaxWidth().testTag("select_team_dropdown_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("الفريق النشط", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                selectedTeam?.name ?: "اختر الفريق",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
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

            // Project Selector
            Box(modifier = Modifier.weight(1f)) {
                OutlinedCard(
                    onClick = { expandedProjectMenu = true },
                    modifier = Modifier.fillMaxWidth().testTag("select_project_dropdown_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("المشروع المرتبط", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                selectedProject?.name ?: "بدون مشروع محدد",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
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

        // Quick task suggestions
        Text("نماذج مهام هندسية سريعة:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(quickTaskTemplates.size) { idx ->
                val template = quickTaskTemplates[idx]
                SuggestionChip(
                    onClick = { onTaskPromptChange(template) },
                    label = { Text(template.take(28) + "...", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary) }
                )
            }
        }

        // Main Task Prompt Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نص المهمة البرمجية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (taskPrompt.isNotEmpty()) {
                        TextButton(
                            onClick = { onTaskPromptChange("") },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("مسح", style = MaterialTheme.typography.labelSmall)
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
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(10.dp))

                // Attached Files Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("الملفات المرفقة (${attachedFiles.size}):", style = MaterialTheme.typography.labelMedium)
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
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
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
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons Row: Send, Estimation, Pause, Resume, Stop, Retry
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary Send / Execute Button
                Button(
                    onClick = onSendTask,
                    enabled = !isExecuting && selectedTeam != null,
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("send_team_task_button")
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("جارٍ التنفيذ...")
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إرسال المهمة")
                    }
                }

                // Cost / Tokens Estimation Button
                OutlinedButton(
                    onClick = onEstimateTask,
                    enabled = !isExecuting && selectedTeam != null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("estimate_team_task_button")
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تقدير الموارد")
                }
            }

            // Execution Controls Bar (Pause, Resume, Stop, Retry, Review)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pause Team
                    FilledTonalButton(
                        onClick = onPauseTeam,
                        enabled = isExecuting && !isPaused,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
                        modifier = Modifier.testTag("pause_team_button")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إيقاف مؤقت", style = MaterialTheme.typography.labelSmall)
                    }

                    // Resume Team
                    FilledTonalButton(
                        onClick = onResumeTeam,
                        enabled = isPaused,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.testTag("resume_team_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("استكمال", style = MaterialTheme.typography.labelSmall)
                    }

                    // Stop Team
                    FilledTonalButton(
                        onClick = onStopTeam,
                        enabled = isExecuting || isPaused,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.testTag("stop_team_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إيقاف", style = MaterialTheme.typography.labelSmall)
                    }

                    // Retry Failed
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

            // Secondary Quick Actions: Direct Assignment, Agent Swap, Instructions, Review Results
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenAssignDialog,
                    enabled = selectedTeam != null,
                    modifier = Modifier.weight(1f).testTag("assign_task_button")
                ) {
                    Icon(Icons.Default.AssignmentInd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إسناد مهمة", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onOpenChangeAgentDialog,
                    enabled = selectedTeam != null,
                    modifier = Modifier.weight(1f).testTag("change_agent_button")
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تبديل نموذج", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onOpenInstructionDialog,
                    enabled = selectedTeam != null,
                    modifier = Modifier.weight(1f).testTag("send_instructions_button")
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("توجيه", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = onReviewResultClick,
                    enabled = currentExecution != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1.1f).testTag("review_results_button")
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("مراجعة", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
