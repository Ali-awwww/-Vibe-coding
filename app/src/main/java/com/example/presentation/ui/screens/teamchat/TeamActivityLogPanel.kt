package com.example.presentation.ui.screens.teamchat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.*
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens
import java.text.SimpleDateFormat
import java.util.*

enum class LogFilter(val labelAr: String) {
    ALL("الكل"),
    MESSAGES("الرسائل والنقاش"),
    STEPS_OUTPUTS("المهام والمخرجات"),
    CRITIQUES("المراجعات والنقد"),
    CONSENSUS("الاتفاق والاختلاف")
}

@Composable
fun TeamActivityLogPanel(
    team: ModelTeam?,
    execution: TeamExecution?
) {
    val clipboardManager = LocalClipboardManager.current
    var selectedFilter by remember { mutableStateOf(LogFilter.ALL) }
    var copiedItem by remember { mutableStateOf<String?>(null) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    if (execution == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .testTag("empty_activity_log_state"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(48.dp), tint = VibeDesignTokens.Color.Cyan)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "لا يوجد سجل نشاط مسجل بعد",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "ابدأ بتشغيل مهمة لتسجيل خطة القائد ورسائل الأعضاء والمراجعات.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextMuted
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(LogFilter.values()) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.labelAr, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("log_filter_${filter.name}")
                    )
                }
            }
        }

        if (selectedFilter == LogFilter.ALL || selectedFilter == LogFilter.MESSAGES) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_request_log_card"),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
                    colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
                    border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = VibeDesignTokens.Color.Cyan, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = VibeDesignTokens.Color.OnCyan, modifier = Modifier.padding(4.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("طلب المستخدم الأولي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VibeDesignTokens.Color.TextPrimary)
                            }
                            Text(timeFormat.format(Date(execution.startedAt)), style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono, color = VibeDesignTokens.Color.TextMuted)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(execution.prompt, style = MaterialTheme.typography.bodyMedium, color = VibeDesignTokens.Color.TextPrimary)

                        if (execution.attachedFiles.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("الملفات المرفقة بالسياق:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = VibeDesignTokens.Color.TextMuted)
                            execution.attachedFiles.forEach { file ->
                                Text("• ${file.name} (${file.path})", style = MaterialTheme.typography.bodySmall, color = VibeDesignTokens.Color.Cyan)
                            }
                        }
                    }
                }
            }
        }

        if ((selectedFilter == LogFilter.ALL || selectedFilter == LogFilter.STEPS_OUTPUTS) && execution.steps.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("leader_plan_log_card"),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
                    colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanelRaised),
                    border = BorderStroke(1.dp, VibeDesignTokens.Color.Violet.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountTree, contentDescription = null, tint = VibeDesignTokens.Color.Cyan, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("خطة القائد والمهام الموزعة (${execution.steps.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VibeDesignTokens.Color.TextPrimary)
                        }
                        Spacer(Modifier.height(10.dp))

                        execution.steps.forEachIndexed { idx, step ->
                            Surface(
                                color = VibeDesignTokens.Color.SpacePanel,
                                shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                                border = BorderStroke(1.dp, VibeDesignTokens.Color.Grid),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = CircleShape, color = VibeDesignTokens.Color.Cyan.copy(alpha = 0.18f), modifier = Modifier.size(22.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("${idx + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = VibeDesignTokens.Color.Cyan)
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(step.task, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = VibeDesignTokens.Color.TextPrimary)
                                            Text("العضو المسند إليه: ${step.memberName} (${step.role})", style = MaterialTheme.typography.labelSmall, color = VibeDesignTokens.Color.TextMuted)
                                        }
                                    }

                                    val (stepLabel, stepColor) = when (step.status) {
                                        StepStatus.COMPLETED -> "مكتمل" to VibeDesignTokens.Color.Success
                                        StepStatus.IN_PROGRESS -> "قيد العمل" to VibeDesignTokens.Color.Cyan
                                        StepStatus.FAILED -> "فشل" to VibeDesignTokens.Color.Error
                                        else -> "بانتظار" to VibeDesignTokens.Color.TextMuted
                                    }

                                    Surface(
                                        color = stepColor.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = stepLabel,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = stepColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedFilter == LogFilter.ALL || selectedFilter == LogFilter.CONSENSUS) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("consensus_log_card"),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
                    colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
                    border = BorderStroke(1.dp, VibeDesignTokens.Color.Grid)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Handshake, contentDescription = null, tint = VibeDesignTokens.Color.Success, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("نقاط الاتفاق والتوافق", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VibeDesignTokens.Color.TextPrimary)
                        }

                        if (execution.consensusPoints.isEmpty()) {
                            Text(
                                text = "لم تُسجّل نقاط توافق من هذه الجلسة بعد.",
                                style = MaterialTheme.typography.bodySmall,
                                color = VibeDesignTokens.Color.TextMuted,
                                modifier = Modifier.testTag("empty_consensus_message")
                            )
                        } else {
                            execution.consensusPoints.forEach { point ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VibeDesignTokens.Color.Success, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(point, style = MaterialTheme.typography.bodySmall, color = VibeDesignTokens.Color.TextPrimary)
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RateReview, contentDescription = null, tint = VibeDesignTokens.Color.Error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("نقاط الاختلاف والنقد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VibeDesignTokens.Color.TextPrimary)
                        }

                        if (execution.disagreementPoints.isEmpty()) {
                            Text(
                                text = "لم تُسجّل نقاط اختلاف أو نقد في هذه الجلسة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = VibeDesignTokens.Color.TextMuted,
                                modifier = Modifier.testTag("empty_disagreements_message")
                            )
                        } else {
                            execution.disagreementPoints.forEach { point ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = VibeDesignTokens.Color.Error, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(point, style = MaterialTheme.typography.bodySmall, color = VibeDesignTokens.Color.TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedFilter == LogFilter.ALL || selectedFilter == LogFilter.MESSAGES || selectedFilter == LogFilter.CRITIQUES) {
            val messagesToShow = if (selectedFilter == LogFilter.CRITIQUES) {
                execution.messages.filter { it.type == MessageType.CRITIQUE || it.type == MessageType.REVIEW }
            } else {
                execution.messages
            }

            if (messagesToShow.isNotEmpty()) {
                item {
                    Text("سجل رسائل ونقاش الأعضاء:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VibeDesignTokens.Color.TextPrimary)
                }

                items(messagesToShow) { msg ->
                    val fromMember = team?.members?.find { it.id == msg.fromMemberId }
                    val senderName = when {
                        msg.fromMemberId == "USER" -> "المستخدم (أنت)"
                        msg.fromMemberId == "SYSTEM" -> "النظام"
                        else -> fromMember?.name ?: msg.fromMemberId
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("message_card_${msg.id}"),
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
                        border = BorderStroke(
                            1.dp,
                            when (msg.type) {
                                MessageType.INSTRUCTION -> VibeDesignTokens.Color.Warning.copy(alpha = 0.4f)
                                MessageType.CRITIQUE, MessageType.REVIEW -> VibeDesignTokens.Color.Violet.copy(alpha = 0.4f)
                                MessageType.USER_PROMPT -> VibeDesignTokens.Color.Cyan.copy(alpha = 0.4f)
                                else -> VibeDesignTokens.Color.Grid
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        when (msg.type) {
                                            MessageType.INSTRUCTION -> Icons.Default.Campaign
                                            MessageType.CRITIQUE, MessageType.REVIEW -> Icons.Default.FactCheck
                                            MessageType.USER_PROMPT -> Icons.Default.Person
                                            else -> Icons.Default.Forum
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = VibeDesignTokens.Color.Cyan
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(senderName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = VibeDesignTokens.Color.TextPrimary)
                                    if (fromMember != null) {
                                        Text(" (${fromMember.role.labelAr})", style = MaterialTheme.typography.labelSmall, color = VibeDesignTokens.Color.Cyan)
                                    }
                                }
                                Text(timeFormat.format(Date(msg.timestamp)), style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono, color = VibeDesignTokens.Color.TextMuted)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(msg.content, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Default), color = VibeDesignTokens.Color.TextPrimary)
                        }
                    }
                }
            }
        }

        if ((selectedFilter == LogFilter.ALL || selectedFilter == LogFilter.STEPS_OUTPUTS) && !execution.finalResult.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("final_synthesis_log_card"),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
                    colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanelRaised),
                    border = BorderStroke(1.dp, VibeDesignTokens.Color.Success.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = VibeDesignTokens.Color.Success)
                                Spacer(Modifier.width(8.dp))
                                Text("نتيجة التنسيق النهائية الموحدة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = VibeDesignTokens.Color.TextPrimary)
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(execution.finalResult))
                                    copiedItem = "final"
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (copiedItem == "final") Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "نسخ النتيجة",
                                    tint = VibeDesignTokens.Color.Cyan
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = execution.finalResult,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                            color = VibeDesignTokens.Color.TextPrimary
                        )
                    }
                }
            }
        }
    }
}
