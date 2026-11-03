package com.example.presentation.ui.screens.teamchat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.*
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
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))
                Text("لا يوجد سجل نشاط مسجل بعد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("ابدأ بتشغيل مهمة لتسجيل خطة القائد ورسائل الأعضاء والمراجعات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        // Filter row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LogFilter.values().forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.labelAr, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        // 1. User Request Card
        if (selectedFilter == LogFilter.ALL || selectedFilter == LogFilter.MESSAGES) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("user_request_log_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(4.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("طلب المستخدم الأولي (User Request)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(timeFormat.format(Date(execution.startedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(execution.prompt, style = MaterialTheme.typography.bodyMedium)

                        if (execution.attachedFiles.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("الملفات المرفقة بالسياق:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            execution.attachedFiles.forEach { file ->
                                Text("• ${file.name} (${file.path})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // 2. Leader Plan and Distributed Tasks
        if ((selectedFilter == LogFilter.ALL || selectedFilter == LogFilter.STEPS_OUTPUTS) && execution.steps.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("leader_plan_log_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountTree, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("خطة القائد والمهام الموزعة (${execution.steps.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))

                        execution.steps.forEachIndexed { idx, step ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(22.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("${idx + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(step.task, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                            Text("العضو المسند إليه: ${step.memberName} (${step.role})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Surface(
                                        color = when (step.status) {
                                            StepStatus.COMPLETED -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                                            StepStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primaryContainer
                                            StepStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            when (step.status) {
                                                StepStatus.COMPLETED -> "مكتمل"
                                                StepStatus.IN_PROGRESS -> "قيد العمل"
                                                StepStatus.FAILED -> "فشل"
                                                else -> "بانتظار"
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
                }
            }
        }

        // 3. Consensus & Disagreement Points (نقاط الاتفاق والاختلاف)
        if (selectedFilter == LogFilter.ALL || selectedFilter == LogFilter.CONSENSUS) {
            val consensus = execution.consensusPoints.ifEmpty {
                listOf(
                    "اتفاق على استخدام عزل البيانات ومنع الانهيارات عبر فحص المدخلات في طبقة النطاق (Domain)",
                    "توافق على حفظ التكاليف والالتزام بنماذج مجانية 100% بدون أي تجاوز"
                )
            }
            val disagreements = execution.disagreementPoints.ifEmpty {
                listOf(
                    "نقطة نقد: ضرورة إعادة هيكلة الـ Mutex والـ Coroutines لتفادي توقف الخيط الرئيسي (Main Thread Block)",
                    "مراجعة معمارية: تفضيل استخدام StateFlow بدلاً من BroadcastReceiver لتقليل استهلاك البطارية"
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("consensus_log_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Handshake, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("نقاط الاتفاق والتوافق (Consensus Points)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        consensus.forEach { point ->
                            Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(point, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RateReview, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("نقاط الاختلاف والنقد (Debate & Critique Points)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        disagreements.forEach { point ->
                            Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(point, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // 4. Member Messages & Discussions
        if (selectedFilter == LogFilter.ALL || selectedFilter == LogFilter.MESSAGES || selectedFilter == LogFilter.CRITIQUES) {
            val messagesToShow = if (selectedFilter == LogFilter.CRITIQUES) {
                execution.messages.filter { it.type == MessageType.CRITIQUE || it.type == MessageType.REVIEW }
            } else execution.messages

            if (messagesToShow.isNotEmpty()) {
                item {
                    Text("سجل رسائل ونقاش الأعضاء:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                items(messagesToShow) { msg ->
                    val fromMember = team?.members?.find { it.id == msg.fromMemberId }
                    val senderName = when {
                        msg.fromMemberId == "USER" -> "المستخدم (أنت)"
                        msg.fromMemberId == "SYSTEM" -> "النظام"
                        else -> fromMember?.name ?: msg.fromMemberId
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("message_card_${msg.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = when (msg.type) {
                                MessageType.INSTRUCTION -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                                MessageType.CRITIQUE, MessageType.REVIEW -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                MessageType.USER_PROMPT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else -> MaterialTheme.colorScheme.surface
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
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(senderName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    if (fromMember != null) {
                                        Text(" (${fromMember.role.labelAr})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text(timeFormat.format(Date(msg.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(msg.content, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Default))
                        }
                    }
                }
            }
        }

        // 5. Final Coordinated Synthesis Result
        if ((selectedFilter == LogFilter.ALL || selectedFilter == LogFilter.STEPS_OUTPUTS) && !execution.finalResult.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("final_synthesis_log_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(8.dp))
                                Text("نتيجة التنسيق النهائية الموحدة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(execution.finalResult))
                                    copiedItem = "final"
                                }
                            ) {
                                Icon(if (copiedItem == "final") Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            execution.finalResult,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }
        }
    }
}
