package com.example.presentation.ui.screens.teamchat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.domain.model.AIModel
import com.example.domain.model.ModelTeam
import com.example.domain.model.TeamMember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeAgentDialog(
    team: ModelTeam,
    availableModels: List<AIModel>,
    onDismiss: () -> Unit,
    onConfirmChange: (memberId: String, newModelId: String) -> Unit
) {
    var selectedMemberId by remember { mutableStateOf(team.members.firstOrNull()?.id ?: "") }
    var selectedModelId by remember {
        val currentMember = team.members.firstOrNull()
        mutableStateOf(currentMember?.modelId ?: availableModels.firstOrNull()?.id ?: "")
    }
    var freeOnlyFilter by remember { mutableStateOf(team.governance.freeOnly) }

    val filteredModels = remember(availableModels, freeOnlyFilter) {
        if (freeOnlyFilter) availableModels.filter { it.isFree } else availableModels
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("تبديل نموذج العضو (Change Agent)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                Text("اختر العضو المطلوب تعديل نموذجه:", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    team.members.forEach { member ->
                        FilterChip(
                            selected = selectedMemberId == member.id,
                            onClick = {
                                selectedMemberId = member.id
                                selectedModelId = member.modelId
                            },
                            label = { Text(member.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("النماذج المتاحة:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("مجانية فقط", style = MaterialTheme.typography.labelSmall)
                        Switch(
                            checked = freeOnlyFilter,
                            onCheckedChange = { freeOnlyFilter = it },
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredModels) { model ->
                        val isSelected = selectedModelId == model.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedModelId = model.id },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(model.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(model.providerId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (model.isFree) {
                                    Surface(
                                        color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "مجاني 100%",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF2E7D32),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "مدفوع (${model.inputPricePer1M}$/1M)",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedMemberId.isNotEmpty() && selectedModelId.isNotEmpty()) {
                        onConfirmChange(selectedMemberId, selectedModelId)
                    }
                },
                modifier = Modifier.testTag("confirm_change_agent_button")
            ) {
                Text("تطبيق التغيير")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.size(48.dp * scale)
)
