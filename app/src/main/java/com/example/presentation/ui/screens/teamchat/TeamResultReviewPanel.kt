package com.example.presentation.ui.screens.teamchat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.domain.model.ExecutionStep
import com.example.domain.model.ModelTeam
import com.example.domain.model.TeamExecution
import com.example.domain.model.TeamMember

enum class ReviewSubTab(val labelAr: String) {
    MEMBERS_OUTPUTS("مخرجات كل عضو"),
    COMPARE_MODELS("مقارنة نتائج النماذج"),
    UNIFIED_REPORT("تقرير القائد الموحد")
}

@Composable
fun TeamResultReviewPanel(
    team: ModelTeam?,
    execution: TeamExecution?
) {
    val clipboardManager = LocalClipboardManager.current
    var activeSubTab by remember { mutableStateOf(ReviewSubTab.MEMBERS_OUTPUTS) }
    var selectedMemberIndex by remember { mutableIntStateOf(0) }
    var compareMemberAId by remember { mutableStateOf(team?.members?.firstOrNull()?.id ?: "") }
    var compareMemberBId by remember {
        val second = team?.members?.getOrNull(1) ?: team?.members?.firstOrNull()
        mutableStateOf(second?.id ?: "")
    }
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    if (team == null || execution == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("لا توجد نتائج مسجلة للمراجعة حالياً. أرسل مهمة لمشاهدة النتائج.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sub-tabs row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReviewSubTab.values().forEach { subTab ->
                FilterChip(
                    selected = activeSubTab == subTab,
                    onClick = { activeSubTab = subTab },
                    label = { Text(subTab.labelAr, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        when (activeSubTab) {
            // 1. Independent pane for each member's output (مخرجات كل عضو في مساحة مستقلة)
            ReviewSubTab.MEMBERS_OUTPUTS -> {
                val membersWithOutputs = team.members.map { member ->
                    val stepsForMember = execution.steps.filter { it.memberId == member.id && !it.output.isNullOrBlank() }
                    val directOutput = member.lastResult ?: stepsForMember.lastOrNull()?.output ?: "لم تصدر نتيجة برمجية مكتملة من هذا العضو بعد."
                    Triple(member, stepsForMember, directOutput)
                }

                if (membersWithOutputs.isNotEmpty()) {
                    val safeIndex = selectedMemberIndex.coerceIn(0, membersWithOutputs.size - 1)
                    val (currentMember, memberSteps, currentOutput) = membersWithOutputs[safeIndex]

                    // Member selector tabs
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(membersWithOutputs.size) { idx ->
                            val (m, _, _) = membersWithOutputs[idx]
                            InputChip(
                                selected = selectedMemberIndex == idx,
                                onClick = { selectedMemberIndex = idx },
                                label = { Text("${m.name} (${m.role.labelAr})", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("member_independent_output_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Member Header Info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(currentMember.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("الدور: ${currentMember.role.labelAr} • النموذج: ${currentMember.modelId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(currentOutput))
                                        copiedLabel = currentMember.id
                                    }
                                ) {
                                    Icon(if (copiedLabel == currentMember.id) Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = "Copy")
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(10.dp))

                            // Member Steps & Subtask description
                            if (memberSteps.isNotEmpty()) {
                                Text("المهام المنفذة بواسطة هذا العضو:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                memberSteps.forEach { step ->
                                    Text("• ${step.task}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.height(10.dp))
                            }

                            // Full Independent Code & Output View
                            Text("المخرجات والحل المقترح:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            ) {
                                LazyColumn(modifier = Modifier.padding(12.dp)) {
                                    item {
                                        Text(
                                            currentOutput,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Side-by-side or stacked diff comparison between any two models
            ReviewSubTab.COMPARE_MODELS -> {
                val memberA = team.members.find { it.id == compareMemberAId } ?: team.members.firstOrNull()
                val memberB = team.members.find { it.id == compareMemberBId } ?: team.members.getOrNull(1) ?: memberA

                val outputA = execution.steps.findLast { it.memberId == memberA?.id && !it.output.isNullOrBlank() }?.output 
                    ?: memberA?.lastResult ?: "لا توجد نتيجة مسجلة بعد لهذا النموذج."
                val outputB = execution.steps.findLast { it.memberId == memberB?.id && !it.output.isNullOrBlank() }?.output 
                    ?: memberB?.lastResult ?: "لا توجد نتيجة مسجلة بعد لهذا النموذج."

                Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("مقارنة مخرجات نموذجين (Side-by-Side Model Diff)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))

                        // Selectors for A and B
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("النموذج الأول (A):", style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(4.dp))
                                team.members.forEach { m ->
                                    FilterChip(
                                        selected = compareMemberAId == m.id,
                                        onClick = { compareMemberAId = m.id },
                                        label = { Text(m.name, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("النموذج الثاني (B):", style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(4.dp))
                                team.members.forEach { m ->
                                    FilterChip(
                                        selected = compareMemberBId == m.id,
                                        onClick = { compareMemberBId = m.id },
                                        label = { Text(m.name, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))

                        // Side by side or stacked output panels
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Panel A
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        "${memberA?.name ?: "A"} (${memberA?.modelId})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    LazyColumn {
                                        item {
                                            Text(outputA, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                                        }
                                    }
                                }
                            }

                            // Panel B
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        "${memberB?.name ?: "B"} (${memberB?.modelId})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    LazyColumn {
                                        item {
                                            Text(outputB, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Unified leader synthesis report
            ReviewSubTab.UNIFIED_REPORT -> {
                val leader = team.members.find { it.id == team.leaderMemberId } ?: team.members.firstOrNull()
                val reportContent = execution.finalResult ?: leader?.lastResult ?: "لم يكتمل إنتاج تقرير القائد الموحد بعد."

                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("unified_leader_report_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("تقرير قائد الفريق الموحد (Unified Leader Report)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("القائد: ${leader?.name ?: "Leader"} • النمط: ${team.collaborationStyle.labelAr}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(reportContent))
                                    copiedLabel = "leader_report"
                                }
                            ) {
                                Icon(if (copiedLabel == "leader_report") Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = "Copy Report")
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            LazyColumn(modifier = Modifier.padding(12.dp)) {
                                item {
                                    Text(
                                        reportContent,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
