package com.example.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.TeamGovernanceSettings

@Composable
fun TeamGovernanceDialog(
    currentSettings: TeamGovernanceSettings,
    onSave: (TeamGovernanceSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var freeOnly by remember { mutableStateOf(currentSettings.freeOnly) }
    var maxCost by remember { mutableStateOf(currentSettings.maxTeamCostUsd.toString()) }
    var maxTokens by remember { mutableStateOf(currentSettings.maxTotalTokens.toString()) }
    var maxRequests by remember { mutableStateOf(currentSettings.maxApiRequests.toString()) }
    var maxParallel by remember { mutableStateOf(currentSettings.maxParallelRequests.toString()) }
    var maxRetries by remember { mutableStateOf(currentSettings.maxRetryAttempts.toString()) }
    var maxTimeoutMinutes by remember { mutableStateOf(currentSettings.maxExecutionTimeMinutes.toString()) }
    var enableLoadBalancing by remember { mutableStateOf(currentSettings.enableLoadBalancing) }
    var autoFallbackToFree by remember { mutableStateOf(currentSettings.autoFallbackToFree) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                "حوكمة الفريق والتحكم بالتكلفة (Governance)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Free Only Toggle Card
                Surface(
                    color = if (freeOnly) Color(0xFF1B5E20).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (freeOnly) Color(0xFF2E7D32).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "وضع النماذج المجانية فقط (Free Only)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (freeOnly) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "يمنع منعاً باتاً استدعاء أي نموذج مدفوع ويمنع التحويل التلقائي للمدفوع.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = freeOnly,
                                onCheckedChange = { freeOnly = it }
                            )
                        }
                    }
                }

                // Cost Limits Section
                Text(
                    "حدود الاستهلاك والأمان",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = maxCost,
                    onValueChange = { maxCost = it },
                    label = { Text("أقصى تكلفة للفريق (USD $)") },
                    enabled = !freeOnly,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        if (freeOnly) Text("معطل لأن وضع Free Only نشط (0.00$)")
                    }
                )

                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    label = { Text("الحد الأقصى للرموز (Tokens)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxRequests,
                        onValueChange = { maxRequests = it },
                        label = { Text("أقصى عدد طلبات") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = maxParallel,
                        onValueChange = { maxParallel = it },
                        label = { Text("أقصى تزامن") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxRetries,
                        onValueChange = { maxRetries = it },
                        label = { Text("محاولات الإعادة") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = maxTimeoutMinutes,
                        onValueChange = { maxTimeoutMinutes = it },
                        label = { Text("المهلة (دقائق)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                HorizontalDivider()

                // Failover & Routing
                Text(
                    "توزيع الأحمال والتعامل مع الحصة",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("التحويل التلقائي للبديل المجاني", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("عند وصول أحد الأعضاء لحد المزود، يتم التبديل لنموذج مجاني بديل مع الحفاظ على السياق.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoFallbackToFree,
                        onCheckedChange = { autoFallbackToFree = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("توزيع الأحمال (Load Balancing)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("توزيع الطلبات على مفاتيح ومزودين متعدّدين مسجلين بحسابك دون تجاوز سياسات المزود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = enableLoadBalancing,
                        onCheckedChange = { enableLoadBalancing = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = currentSettings.copy(
                        freeOnly = freeOnly,
                        maxTeamCostUsd = maxCost.toDoubleOrNull() ?: currentSettings.maxTeamCostUsd,
                        maxTotalTokens = maxTokens.toIntOrNull() ?: currentSettings.maxTotalTokens,
                        maxApiRequests = maxRequests.toIntOrNull() ?: currentSettings.maxApiRequests,
                        maxParallelRequests = maxParallel.toIntOrNull() ?: currentSettings.maxParallelRequests,
                        maxRetryAttempts = maxRetries.toIntOrNull() ?: currentSettings.maxRetryAttempts,
                        maxExecutionTimeMinutes = maxTimeoutMinutes.toIntOrNull() ?: currentSettings.maxExecutionTimeMinutes,
                        enableLoadBalancing = enableLoadBalancing,
                        autoFallbackToFree = autoFallbackToFree
                    )
                    onSave(updated)
                }
            ) {
                Text("حفظ السياسات")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
