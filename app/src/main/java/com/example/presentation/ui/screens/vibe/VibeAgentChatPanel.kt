package com.example.presentation.ui.screens.vibe

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.*

@Composable
fun VibeAgentChatPanel(
    mode: AgentExecutionMode,
    onModeChange: (AgentExecutionMode) -> Unit,
    messages: List<AgentChatMessage>,
    activePlan: ExecutionPlan?,
    isLoading: Boolean,
    onSendMessage: (String) -> Unit,
    onExecutePlan: () -> Unit,
    onOpenDiffViewer: () -> Unit,
    onOpenReviewIssues: () -> Unit,
    workspace: Workspace?,
    activeAgentModel: String,
    agentIsFree: Boolean,
    activeTeam: ModelTeam?,
    availableTeams: List<ModelTeam>,
    selectedMemberId: String?,
    onSelectTeam: (ModelTeam?) -> Unit,
    onSelectMember: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, activePlan?.steps?.map { it.status }) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with Execution Mode Selector
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Text(
                                "AI Autonomous Agent",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Free AI & Governance Badges
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (agentIsFree) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (agentIsFree) Icons.Default.Shield else Icons.Default.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = if (agentIsFree) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = activeAgentModel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = if (agentIsFree) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            
                            // Permission Level Badge
                            val permissionLevel = workspace?.permissionLevel ?: AgentPermissionLevel.READ
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when (permissionLevel) {
                                    AgentPermissionLevel.READ -> MaterialTheme.colorScheme.secondaryContainer
                                    AgentPermissionLevel.EDIT -> MaterialTheme.colorScheme.tertiaryContainer
                                    AgentPermissionLevel.EXECUTE -> Color(0xFFFBE9E7)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = when (permissionLevel) {
                                            AgentPermissionLevel.READ -> Icons.Default.Lock
                                            AgentPermissionLevel.EDIT -> Icons.Default.Edit
                                            AgentPermissionLevel.EXECUTE -> Icons.Default.Gavel
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = when (permissionLevel) {
                                            AgentPermissionLevel.READ -> MaterialTheme.colorScheme.secondary
                                            AgentPermissionLevel.EDIT -> MaterialTheme.colorScheme.tertiary
                                            AgentPermissionLevel.EXECUTE -> Color(0xFFD84315)
                                        }
                                    )
                                    Text(
                                        text = permissionLevel.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = when (permissionLevel) {
                                            AgentPermissionLevel.READ -> MaterialTheme.colorScheme.secondary
                                            AgentPermissionLevel.EDIT -> MaterialTheme.colorScheme.tertiary
                                            AgentPermissionLevel.EXECUTE -> Color(0xFFD84315)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Execution Mode Tabs
                    TabRow(
                        selectedTabIndex = mode.ordinal,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        Tab(
                            selected = mode == AgentExecutionMode.PLAN,
                            onClick = { onModeChange(AgentExecutionMode.PLAN) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.FormatListNumbered, null, modifier = Modifier.size(14.dp))
                                    Text("تخطيط (Plan)", fontSize = 11.sp, fontWeight = if (mode == AgentExecutionMode.PLAN) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        )
                        Tab(
                            selected = mode == AgentExecutionMode.AGENT,
                            onClick = { onModeChange(AgentExecutionMode.AGENT) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp))
                                    Text("تنفيذ (Agent)", fontSize = 11.sp, fontWeight = if (mode == AgentExecutionMode.AGENT) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        )
                        Tab(
                            selected = mode == AgentExecutionMode.REVIEW,
                            onClick = { onModeChange(AgentExecutionMode.REVIEW) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.FactCheck, null, modifier = Modifier.size(14.dp))
                                    Text("مراجعة (Review)", fontSize = 11.sp, fontWeight = if (mode == AgentExecutionMode.REVIEW) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        )
                    }

                    // Mode explanatory helper
                    Text(
                        text = when (mode) {
                            AgentExecutionMode.PLAN -> "يخطط للتنفيذ ويعرض الخطوات المنظمة دون تعديل الملفات."
                            AgentExecutionMode.AGENT -> "ينفذ التعديلات داخل المشروع مع حفظ نقاط استعادة وطلب إذن العمليات الحساسة."
                            AgentExecutionMode.REVIEW -> "يفحص الكود ويكتشف الثغرات والأخطاء المحتملة دون تعديل الكود."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    // Team Selector
                    var showTeamMenu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { showTeamMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Groups, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = activeTeam?.name ?: "اختر فريق عمل (Select Team)",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = showTeamMenu, onDismissRequest = { showTeamMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("بدون فريق (Single Agent)") },
                                onClick = { onSelectTeam(null); showTeamMenu = false }
                            )
                            availableTeams.forEach { team ->
                                DropdownMenuItem(
                                    text = { Text(team.name) },
                                    onClick = { onSelectTeam(team); showTeamMenu = false }
                                )
                            }
                        }
                    }

                    if (activeTeam != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("التحدث إلى (Talk to):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Leader option
                            FilterChip(
                                selected = selectedMemberId == null,
                                onClick = { onSelectMember(null) },
                                label = { Text("قائد الفريق (Leader)", fontSize = 10.sp) },
                                leadingIcon = { Icon(Icons.Default.Star, null, modifier = Modifier.size(14.dp)) }
                            )
                            
                            // Specific members
                            activeTeam.members.forEach { member ->
                                FilterChip(
                                    selected = selectedMemberId == member.id,
                                    onClick = { onSelectMember(member.id) },
                                    label = { Text(member.name, fontSize = 10.sp) },
                                    leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                    }
                }
            }

            // Quick Sequential Command Suggestions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val suggestions = listOf(
                    "غيّر لون الزر إلى الأزرق",
                    "أضف صفحة إعدادات",
                    "اربط صفحة الإعدادات بقاعدة البيانات",
                    "أصلح الخطأ السابق",
                    "حافظ على التصميم الحالي",
                    "لا تغير بقية الشاشات"
                )
                suggestions.forEach { suggestion ->
                    SuggestionChip(
                        onClick = { onSendMessage(suggestion) },
                        label = { Text(suggestion, fontSize = 10.sp) }
                    )
                }
            }

            HorizontalDivider()

            // Chat Messages Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages) { message ->
                    ChatMessageItem(
                        message = message,
                        onExecutePlan = onExecutePlan,
                        onOpenDiff = onOpenDiffViewer,
                        onOpenReview = onOpenReviewIssues
                    )
                }

                if (isLoading) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    "الوكيل يحلل سياق المشروع وينفذ الخطوات...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = {
                            Text(
                                when (mode) {
                                    AgentExecutionMode.PLAN -> "اطلب خطة تنفيذ لميزة جديدة..."
                                    AgentExecutionMode.AGENT -> "اطلب كتابة كود أو تعديل شاشة..."
                                    AgentExecutionMode.REVIEW -> "اطلب مراجعة الأمان أو جودة الكود..."
                                },
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (textInput.isNotBlank()) {
                                        onSendMessage(textInput.trim())
                                        textInput = ""
                                    }
                                },
                                enabled = textInput.isNotBlank() && !isLoading
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    "Send",
                                    tint = if (textInput.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: AgentChatMessage,
    onExecutePlan: () -> Unit,
    onOpenDiff: () -> Unit,
    onOpenReview: () -> Unit
) {
    val isUser = message.sender == AgentMessageSender.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = when {
                    isUser -> Icons.Default.Person
                    message.sender == AgentMessageSender.TEAM_MEMBER -> Icons.Default.Groups
                    else -> Icons.Default.AutoAwesome
                },
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isUser) MaterialTheme.colorScheme.primary else Color(0xFF7F52FF)
            )
            Text(
                text = message.senderName ?: if (isUser) "المطور" else "AI Coding Agent",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(Modifier.height(4.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Plan Step-by-Step Card
                if (message.plan != null) {
                    Spacer(Modifier.height(10.dp))
                    PlanCard(plan = message.plan, onExecute = onExecutePlan)
                }

                // Diffs & Summary Card
                if (message.summary != null && message.summary.changedFiles.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    ExecutionSummaryCard(summary = message.summary, onOpenDiff = onOpenDiff)
                }

                // Review Issues Card
                if (message.reviewIssues.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    ReviewSummaryCard(issues = message.reviewIssues, onOpenReview = onOpenReview)
                }
            }
        }
    }
}

@Composable
fun PlanCard(
    plan: ExecutionPlan,
    onExecute: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "خطة التنفيذ المقترحة",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "${plan.steps.size} خطوات",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (plan.detectedTech.isNotBlank()) {
                Text(
                    "التقنية: ${plan.detectedTech}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            Spacer(Modifier.height(6.dp))

            // Step items
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                plan.steps.forEach { step ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when (step.status) {
                            StepStatus.COMPLETED -> Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                            StepStatus.IN_PROGRESS -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            StepStatus.FAILED -> Icon(Icons.Default.Cancel, null, modifier = Modifier.size(16.dp), tint = Color(0xFFF44336))
                            else -> Icon(Icons.Default.RadioButtonUnchecked, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                        }

                        Column {
                            Text(
                                "${step.order}. ${step.title}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (step.targetFiles.isNotEmpty()) {
                                Text(
                                    step.targetFiles.joinToString(", "),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onExecute,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("تنفيذ الخطة الآن (Execute Plan)")
            }
        }
    }
}

@Composable
fun ExecutionSummaryCard(
    summary: ExecutionSummary,
    onOpenDiff: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                "ملخص التغييرات البرمجية",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (summary.createdCount > 0) {
                    Text("+${summary.createdCount} تم إنشاؤه", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                if (summary.modifiedCount > 0) {
                    Text("~${summary.modifiedCount} تم تعديله", color = Color(0xFF2196F3), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                if (summary.deletedCount > 0) {
                    Text("-${summary.deletedCount} تم حذفه", color = Color(0xFFF44336), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenDiff,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Icon(Icons.Default.Difference, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("مراجعة الـ Diff ومقارنة الأكواد (${summary.changedFiles.size} ملف)")
            }
        }
    }
}

@Composable
fun ReviewSummaryCard(
    issues: List<CodeIssue>,
    onOpenReview: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "نتائج فحص الكود (Review)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${issues.size} ملاحظة",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(6.dp))

            Button(
                onClick = onOpenReview,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Icon(Icons.Default.BugReport, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("عرض تفاصيل الملاحظات والثغرات")
            }
        }
    }
}
