package com.example.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.PreExecutionEstimation

@Composable
fun PreExecutionEstimationDialog(
    estimation: PreExecutionEstimation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Calculate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "تقدير الموارد والتكلفة قبل التشغيل",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Free Only / Pricing Reliability Badge
                Surface(
                    color = if (estimation.isFreeOnly) Color(0xFF1B5E20).copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (estimation.isFreeOnly) Icons.Default.MoneyOff else Icons.Default.AttachMoney,
                            contentDescription = null,
                            tint = if (estimation.isFreeOnly) Color(0xFF2E7D32) else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (estimation.isFreeOnly) "وضع Free AI Only: مجاني 100% بدون أي رسوم" else "وضع التسعير المخصص (BYOK)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (estimation.isFreeOnly) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Metrics Grid
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EstimationRow("المهام المقدرة:", "${estimation.estimatedTasksCount} مهمة فرعية")
                        EstimationRow("استدعاءات النماذج:", "${estimation.estimatedCallsCount} استدعاء API")
                        EstimationRow("حجم السياق المتوقع:", "~${String.format("%,d", estimation.estimatedContextTokens)} رمز (Tokens)")
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        
                        EstimationRow(
                            label = "التكلفة التقديرية:",
                            value = if (estimation.isFreeOnly) "0.00$ (مؤكد مجاني)" else "${String.format("%.4f", estimation.estimatedMinCostUsd)}$ - ${String.format("%.4f", estimation.estimatedMaxCostUsd)}$",
                            isHighlight = true
                        )
                    }
                }

                // Disclaimer box
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            estimation.disclaimerText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("تأكيد وبدء التشغيل")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
private fun EstimationRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
