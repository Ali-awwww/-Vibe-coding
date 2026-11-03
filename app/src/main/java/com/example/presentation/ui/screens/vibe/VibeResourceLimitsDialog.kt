package com.example.presentation.ui.screens.vibe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.SandboxRuntimeState

@Composable
fun VibeResourceLimitsDialog(
    runtimeState: SandboxRuntimeState?,
    onKillStuckProcesses: () -> Unit,
    onDismiss: () -> Unit
) {
    val memoryUsage = runtimeState?.memoryUsageMb ?: 128
    val memoryLimit = runtimeState?.memoryLimitMb ?: 512
    val memoryRatio = (memoryUsage.toFloat() / memoryLimit.toFloat()).coerceIn(0f, 1f)

    val storageUsage = runtimeState?.storageUsageMb ?: 24.2f
    val storageLimit = runtimeState?.storageLimitMb ?: 100f
    val storageRatio = (storageUsage / storageLimit).coerceIn(0f, 1f)

    val cpuUsage = runtimeState?.cpuUsagePercent ?: 14.5f

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary)
                        Text("إدارة موارد البيئة المعزولة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null)
                    }
                }

                Text(
                    "حدود استهلاك الموارد المخصصة لمساحة العمل لحماية أداء النظام واستقراره.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(Modifier.height(16.dp))

                // CPU Meter
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المعالج (CPU)", style = MaterialTheme.typography.labelMedium)
                        Text("$cpuUsage% / 100% (2 vCPU)", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { cpuUsage / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (cpuUsage > 75f) Color(0xFFF44336) else MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Memory Meter
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الذاكرة العشوائية (RAM)", style = MaterialTheme.typography.labelMedium)
                        Text("$memoryUsage MB / $memoryLimit MB", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { memoryRatio },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (memoryRatio > 0.8f) Color(0xFFF44336) else Color(0xFF4CAF50)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Storage Meter
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("التخزين المستقل (Workspace Storage)", style = MaterialTheme.typography.labelMedium)
                        Text(String.format("%.1f MB / %.0f MB", storageUsage, storageLimit), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { storageRatio },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Processes & Ports Information
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المنفذ النشط:", style = MaterialTheme.typography.labelSmall)
                            Text("127.0.0.1:${runtimeState?.port ?: 3000}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المعرف البرمجي للعملية (PID):", style = MaterialTheme.typography.labelSmall)
                            Text(runtimeState?.activeProcessId ?: "None (Stopped)", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المهلة القصوى للتنفيذ (Timeout):", style = MaterialTheme.typography.labelSmall)
                            Text("30 ثانية لكل أمر • إيقاف تلقائي عند الخمول", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Kill Stuck Processes Button
                Button(
                    onClick = {
                        onKillStuckProcesses()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Warning, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("إنهاء العمليات العالقة وتحرير الموارد")
                }
            }
        }
    }
}
