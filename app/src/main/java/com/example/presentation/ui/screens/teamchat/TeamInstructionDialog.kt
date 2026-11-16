package com.example.presentation.ui.screens.teamchat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.ExecutionStep
import com.example.domain.model.ModelTeam
import com.example.domain.model.TeamInstruction

enum class InstructionType(val labelAr: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TO_LEADER("تعليمات للقائد", Icons.Default.Psychology),
    TO_MEMBER("مهمة لعضو معين", Icons.Default.Person),
    BROADCAST("ملاحظة للفريق كاملًا", Icons.Default.Campaign),
    REQUEST_REVIEW("طلب مراجعة جديدة", Icons.Default.FactCheck),
    RETRY_STEP("إعادة تنفيذ مهمة", Icons.Default.Replay)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamInstructionDialog(
    team: ModelTeam,
    steps: List<ExecutionStep>,
    onDismiss: () -> Unit,
    onSendInstruction: (TeamInstruction) -> Unit
) {
    var selectedType by remember { mutableStateOf(InstructionType.TO_LEADER) }
    var instructionText by remember { mutableStateOf("") }
    var selectedMemberId by remember { mutableStateOf(team.members.firstOrNull()?.id ?: "") }
    var selectedStepId by remember { mutableStateOf(steps.firstOrNull()?.id ?: "") }
    var critiqueFocus by remember { mutableStateOf("تدقيق الأمان والتعامل مع الأخطاء وحالات الحدود (Edge cases)") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(selectedType.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("إرسال تعليمات للفريق", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("نوع التوجيه أو التعليمة:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InstructionType.values().forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type; validationError = null },
                                leadingIcon = { Icon(type.icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(type.labelAr, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                when (selectedType) {
                    InstructionType.TO_LEADER -> {
                        item {
                            Text("تعليمات موجهة لقائد الفريق (${team.members.find { it.id == team.leaderMemberId }?.name ?: team.members.firstOrNull()?.name}):", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = instructionText,
                                onValueChange = { instructionText = it; validationError = null },
                                placeholder = { Text("مثال: ركز على تقليل التكلفة، أو أعد تقسيم المهام لتشمل اختبارات الأمان أولاً...") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("instruction_leader_input")
                            )
                        }
                    }

                    InstructionType.TO_MEMBER -> {
                        item {
                            Text("اختر العضو المستهدف:", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(4.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(team.members) { member ->
                                    FilterChip(
                                        selected = selectedMemberId == member.id,
                                        onClick = { selectedMemberId = member.id },
                                        label = { Text("${member.name} (${member.role.labelAr})", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = instructionText,
                                onValueChange = { instructionText = it; validationError = null },
                                label = { Text("المهمة المباشرة للعضو") },
                                placeholder = { Text("مثال: قم بإنشاء وحدة فحص المدخلات للقيم الفارغة...") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).testTag("instruction_member_input")
                            )
                        }
                    }

                    InstructionType.BROADCAST -> {
                        item {
                            Text("ملاحظة أو قيد هندسي لكامل أعضاء الفريق:", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = instructionText,
                                onValueChange = { instructionText = it; validationError = null },
                                placeholder = { Text("مثال: يمنع استخدام أي مكتبات غير موثقة، والاعتماد فقط على Kotlin Coroutines...") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("instruction_broadcast_input")
                            )
                        }
                    }

                    InstructionType.REQUEST_REVIEW -> {
                        item {
                            Text("محور المراجعة النقدية:", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = critiqueFocus,
                                onValueChange = { critiqueFocus = it },
                                label = { Text("مجال التركيز (Security / Performance / Reliability)") },
                                modifier = Modifier.fillMaxWidth().testTag("instruction_critique_focus_input")
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("سيقوم المراجع أو المدقق الأمني في الفريق بمراجعة مخرجات الفريق وتقديم نقد شامل.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    InstructionType.RETRY_STEP -> {
                        item {
                            if (steps.isEmpty()) {
                                Text("لا توجد مهام منشأة بعد لإعادة تنفيذها.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("اختر المهمة المطلوب إعادة تنفيذها:", style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    steps.forEach { step ->
                                        FilterChip(
                                            selected = selectedStepId == step.id,
                                            onClick = { selectedStepId = step.id },
                                            label = { Text("${step.memberName}: ${step.task.take(40)}", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (validationError != null) {
                    item {
                        Text(validationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedType) {
                        InstructionType.TO_LEADER -> {
                            if (instructionText.isBlank()) {
                                validationError = "يرجى كتابة التعليمات للقائد"
                                return@Button
                            }
                            onSendInstruction(TeamInstruction.ToLeader(instructionText))
                        }
                        InstructionType.TO_MEMBER -> {
                            if (instructionText.isBlank()) {
                                validationError = "يرجى كتابة نص المهمة للعضو"
                                return@Button
                            }
                            onSendInstruction(TeamInstruction.ToMember(selectedMemberId, instructionText))
                        }
                        InstructionType.BROADCAST -> {
                            if (instructionText.isBlank()) {
                                validationError = "يرجى كتابة الملاحظة العامة"
                                return@Button
                            }
                            onSendInstruction(TeamInstruction.BroadcastAll(instructionText))
                        }
                        InstructionType.REQUEST_REVIEW -> {
                            onSendInstruction(TeamInstruction.RequestReview(critiqueFocus = critiqueFocus))
                        }
                        InstructionType.RETRY_STEP -> {
                            if (selectedStepId.isBlank()) {
                                validationError = "يرجى اختيار المهمة المراد إعادتها"
                                return@Button
                            }
                            onSendInstruction(TeamInstruction.RetryStep(selectedStepId))
                        }
                    }
                },
                modifier = Modifier.testTag("confirm_send_instruction_button")
            ) {
                Text("إرسال")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
