package com.example.presentation.ui.screens.teamchat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.ExecutionStatus
import com.example.domain.model.MessageType
import com.example.domain.model.ModelTeam
import com.example.domain.model.TeamExecution
import com.example.presentation.ui.screens.teams.TeamCyberPanel
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens

@Composable
internal fun TeamInstructionsTabContent(
    execution: TeamExecution?,
    hasSelectedTeam: Boolean,
    onOpenInstructionDialog: () -> Unit
) {
    val instructionMessages = execution?.messages?.filter {
        it.type == MessageType.INSTRUCTION || it.type == MessageType.REVIEW
    } ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TeamCyberPanel(
                modifier = Modifier.fillMaxWidth(),
                borderColor = VibeDesignTokens.Color.Cyan.copy(alpha = 0.4f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = VibeDesignTokens.Color.Cyan, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "مركز إرسال التعليمات والتوجيهات",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VibeDesignTokens.Color.TextPrimary
                        )
                        Text(
                            text = "أرسل توجيهات استراتيجية للقائد، أو مهمة لعضو، أو اطلب مراجعة نقدية.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeDesignTokens.Color.TextMuted
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = onOpenInstructionDialog,
                enabled = hasSelectedTeam,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeDesignTokens.Color.Cyan,
                    contentColor = VibeDesignTokens.Color.OnCyan
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("open_send_instruction_dialog")
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("فتح نموذج إرسال التعليمات", fontWeight = FontWeight.Bold)
            }
        }

        item {
            Text(
                text = "التعليمات والتوجيهات المرسلة مؤخراً:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = VibeDesignTokens.Color.TextPrimary
            )
        }

        if (instructionMessages.isEmpty()) {
            item {
                TeamCyberPanel(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = VibeDesignTokens.Color.Grid
                ) {
                    Text(
                        text = "لم يتم إرسال تعليمات مخصصة بعد خلال جلسة العمل الحالية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeDesignTokens.Color.TextMuted
                    )
                }
            }
        } else {
            items(instructionMessages.size) { index ->
                val msg = instructionMessages[index]
                TeamCyberPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (msg.fromMemberId == "USER") "منك (المستخدم)" else "رد: ${msg.fromMemberId}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = VibeDesignTokens.Color.Cyan
                        )
                        Text(
                            text = if (msg.type == MessageType.INSTRUCTION) "تعليمة" else "مراجعة نقدية",
                            style = MaterialTheme.typography.labelSmall,
                            color = VibeDesignTokens.Color.TextMuted
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = msg.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeDesignTokens.Color.TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun TeamLiveStatusBar(
    team: ModelTeam?,
    currentExecution: TeamExecution?,
    isExecuting: Boolean,
    isPaused: Boolean,
    isCloudConfigured: Boolean = true,
    durationSeconds: Long
) {
    Surface(
        color = VibeDesignTokens.Color.SpacePanelRaised.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, VibeDesignTokens.Color.Grid),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("team_live_status_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp), tint = VibeDesignTokens.Color.Cyan)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${team?.members?.size ?: 0} أعضاء",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.TextPrimary
                )

                if (team?.governance?.freeOnly == true) {
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        color = VibeDesignTokens.Color.Success.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                        modifier = Modifier.semantics(mergeDescendants = true) {}
                    ) {
                        Text(
                            text = "Free AI Only",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = VibeDesignTokens.Color.Success,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!isCloudConfigured) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = VibeDesignTokens.Color.Warning.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                        modifier = Modifier.semantics(mergeDescendants = true) {}
                    ) {
                        Text(
                            text = "LOCAL",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMono,
                            color = VibeDesignTokens.Color.Warning
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isExecuting || isPaused || durationSeconds > 0) {
                    val mins = durationSeconds / 60
                    val secs = durationSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", mins, secs),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JetBrainsMono,
                        color = VibeDesignTokens.Color.Cyan
                    )
                    Spacer(Modifier.width(8.dp))
                }

                val (statusLabel, statusColor) = when {
                    isPaused -> "موقوف" to VibeDesignTokens.Color.Warning
                    isExecuting -> "يعمل" to VibeDesignTokens.Color.Cyan
                    currentExecution?.status == ExecutionStatus.COMPLETED -> "ناجح" to VibeDesignTokens.Color.Success
                    currentExecution?.status == ExecutionStatus.FAILED -> "فشل" to VibeDesignTokens.Color.Error
                    else -> "خامل" to VibeDesignTokens.Color.TextMuted
                }

                Surface(
                    color = statusColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                    modifier = Modifier.semantics(mergeDescendants = true) {}.testTag("team_chat_execution_status_badge")
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
        }
    }
}
