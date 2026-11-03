package com.example.presentation.ui.screens.teamchat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.*

@Composable
fun LiveTeamCollaborationPanel(
    team: ModelTeam?,
    currentExecution: TeamExecution?,
    isExecuting: Boolean,
    isPaused: Boolean,
    durationSeconds: Long,
    onAssignTaskToMember: (memberId: String) -> Unit,
    onChangeMemberModel: (memberId: String) -> Unit,
    onRetryStep: (stepId: String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copiedMemberId by remember { mutableStateOf<String?>(null) }

    if (team == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("يرجى اختيار فريق لعرض نشاط الأعضاء المباشر", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status banner
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isPaused -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        isExecuting -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        currentExecution?.status == ExecutionStatus.COMPLETED -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                        currentExecution?.status == ExecutionStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = when {
                                isPaused -> MaterialTheme.colorScheme.tertiary
                                isExecuting -> MaterialTheme.colorScheme.primary
                                currentExecution?.status == ExecutionStatus.COMPLETED -> Color(0xFF2E7D32)
                                currentExecution?.status == ExecutionStatus.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                when {
                                    isPaused -> "الفريق متوقف مؤقتًا (Paused)"
                                    isExecuting -> "الفريق قيد العمل والتنسيق المباشر"
                                    currentExecution?.status == ExecutionStatus.COMPLETED -> "اكتمل تنفيذ مهمة الفريق بنجاح"
                                    currentExecution?.status == ExecutionStatus.FAILED -> "توقف التنفيذ بسبب خطأ"
                                    else -> "الفريق في وضع الاستعداد (Idle)"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "النمط: ${team.collaborationStyle.labelAr} • عدد الأعضاء: ${team.members.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Duration display
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            val mins = durationSeconds / 60
                            val secs = durationSeconds % 60
                            Text(
                                String.format("%02d:%02d", mins, secs),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Section header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "حالة أعضاء الفريق ونشاطهم الفعلي",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "بيانات حقيقية بدون محاكاة",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Member Cards
        items(team.members) { member ->
            val isLeader = member.id == team.leaderMemberId
            val relatedStep = currentExecution?.steps?.findLast { it.memberId == member.id }
            var expandedDetails by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("member_card_${member.id}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (member.status == MemberStatus.WORKING) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    } else MaterialTheme.colorScheme.surface
                ),
                border = if (member.status == MemberStatus.WORKING) {
                    androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else null
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header: Member Name, Role, Model, Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = if (isLeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isLeader) Icons.Default.Stars else Icons.Default.Psychology,
                                        contentDescription = null,
                                        tint = if (isLeader) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        member.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isLeader) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                "قائد",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                                Text(
                                    member.role.labelAr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Real Status Pill
                        MemberStatusBadge(member.status, relatedStep?.status)
                    }

                    Spacer(Modifier.height(10.dp))

                    // Model and Provider details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "النموذج: ${member.modelId}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        if (member.isCostConfirmed) {
                            Surface(
                                color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "حصة مجانية (Free)",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Current Task Description
                    val currentTaskText = member.currentTask ?: relatedStep?.task
                    if (!currentTaskText.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("المهمة الحالية:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(2.dp))
                                Text(currentTaskText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Reason for waiting or failure (CRITICAL requirement: سبب الانتظار أو الفشل عند حدوثه)
                    val waitOrFailureReason = relatedStep?.waitReason ?: relatedStep?.error
                    if (!waitOrFailureReason.isNullOrBlank() || member.status == MemberStatus.WAITING || member.status == MemberStatus.FAILED || member.status == MemberStatus.RATE_LIMITED) {
                        Surface(
                            color = if (member.status == MemberStatus.FAILED) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (member.status == MemberStatus.FAILED) Icons.Default.ErrorOutline else Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = if (member.status == MemberStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        if (member.status == MemberStatus.FAILED) "سبب الفشل:" else "سبب الانتظار:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (member.status == MemberStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        waitOrFailureReason ?: if (member.status == MemberStatus.RATE_LIMITED) "تجاوز حد الاستدعاء المجاني، بانتظار دورة التهدئة (Cooldown)" else "بانتظار مخرجات المهام السابقة في الخطة",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Latest Result snippet
                    val latestResultText = member.lastResult ?: relatedStep?.output
                    if (!latestResultText.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("آخر نتيجة صادرة:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(latestResultText))
                                            copiedMemberId = member.id
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            if (copiedMemberId == member.id) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = "Copy result",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (expandedDetails) latestResultText else latestResultText.take(180) + if (latestResultText.length > 180) "..." else "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (latestResultText.length > 180) {
                                    TextButton(
                                        onClick = { expandedDetails = !expandedDetails },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(if (expandedDetails) "عرض أقل" else "عرض كامل النتيجة", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Bottom info & member actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Execution duration or last active
                        Text(
                            "الاستدعاءات: ${member.requestsCount} • الرموز: ${member.tokensUsed}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Assign Task
                            TextButton(
                                onClick = { onAssignTaskToMember(member.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("إسناد مهمة", style = MaterialTheme.typography.labelSmall)
                            }

                            // Change Model
                            TextButton(
                                onClick = { onChangeMemberModel(member.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("تبديل", style = MaterialTheme.typography.labelSmall)
                            }

                            // Retry button if step failed
                            if (relatedStep?.status == StepStatus.FAILED) {
                                TextButton(
                                    onClick = { onRetryStep(relatedStep.id) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("إعادة", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemberStatusBadge(memberStatus: MemberStatus, stepStatus: StepStatus?) {
    val (label, bg, fg) = when {
        stepStatus == StepStatus.IN_PROGRESS || memberStatus == MemberStatus.WORKING -> Triple("يعمل الآن", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
        stepStatus == StepStatus.COMPLETED || memberStatus == MemberStatus.COMPLETED -> Triple("مكتمل", Color(0xFF2E7D32).copy(alpha = 0.2f), Color(0xFF2E7D32))
        stepStatus == StepStatus.FAILED || memberStatus == MemberStatus.FAILED -> Triple("فشل", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
        memberStatus == MemberStatus.RATE_LIMITED -> Triple("محدود الحصة", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary)
        memberStatus == MemberStatus.WAITING -> Triple("بانتظار دور", MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f), MaterialTheme.colorScheme.tertiary)
        else -> Triple("جاهز (خامل)", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (memberStatus == MemberStatus.WORKING || stepStatus == StepStatus.IN_PROGRESS) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    color = fg,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Bold)
        }
    }
}
