package com.example.presentation.ui.screens.teamchat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.ModelTeam

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectTaskAssignmentDialog(
    team: ModelTeam,
    onDismiss: () -> Unit,
    onConfirmAssign: (memberId: String, taskPrompt: String) -> Unit
) {
    var selectedMemberId by remember { mutableStateOf(team.members.firstOrNull()?.id ?: "") }
    var taskPrompt by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AssignmentInd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("إسناد مهمة لعضو محدد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("اختر العضو المسند إليه:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    team.members.forEach { member ->
                        FilterChip(
                            selected = selectedMemberId == member.id,
                            onClick = { selectedMemberId = member.id },
                            label = { Text(member.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                val targetMember = team.members.find { it.id == selectedMemberId }
                if (targetMember != null) {
                    Text("الدور: ${targetMember.role.labelAr} • النموذج: ${targetMember.modelId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }

                OutlinedTextField(
                    value = taskPrompt,
                    onValueChange = { taskPrompt = it; validationError = null },
                    label = { Text("تفاصيل المهمة المطلوبة") },
                    placeholder = { Text("اكتب المهمة البرمجية الدقيقة المطلوب من هذا العضو تنفيذها...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .testTag("direct_assign_task_prompt_input")
                )

                if (validationError != null) {
                    Text(validationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedMemberId.isBlank()) {
                        validationError = "يرجى اختيار العضو"
                        return@Button
                    }
                    if (taskPrompt.isBlank()) {
                        validationError = "يرجى كتابة تفاصيل المهمة"
                        return@Button
                    }
                    onConfirmAssign(selectedMemberId, taskPrompt)
                },
                modifier = Modifier.testTag("confirm_assign_task_button")
            ) {
                Text("إسناد وبدء التنفيذ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
