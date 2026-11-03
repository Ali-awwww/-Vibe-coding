package com.example.presentation.ui.screens.vibe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun VibeGovernanceDialog(
    workspace: Workspace?,
    logs: List<AgentActivityLog>,
    onPermissionChange: (AgentPermissionLevel) -> Unit,
    onClose: () -> Unit
) {
    if (workspace == null) return

    val dateFormat = remember { SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الأمان والحوكمة (Security & Governance)",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onClose) {
                        Text("إغلاق")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Workspace Isolation Info
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "عزل مساحة العمل (Workspace Isolation)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "المشروع '${workspace.name}' معزول تماماً ومقيد بالمعرف: ${workspace.ownerId}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Permission Levels
                Text(
                    text = "مستوى صلاحيات الوكيل (Agent Permission Level)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PermissionButton(
                        level = AgentPermissionLevel.READ,
                        currentLevel = workspace.permissionLevel,
                        onClick = { onPermissionChange(AgentPermissionLevel.READ) },
                        modifier = Modifier.weight(1f)
                    )
                    PermissionButton(
                        level = AgentPermissionLevel.EDIT,
                        currentLevel = workspace.permissionLevel,
                        onClick = { onPermissionChange(AgentPermissionLevel.EDIT) },
                        modifier = Modifier.weight(1f)
                    )
                    PermissionButton(
                        level = AgentPermissionLevel.EXECUTE,
                        currentLevel = workspace.permissionLevel,
                        onClick = { onPermissionChange(AgentPermissionLevel.EXECUTE) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Activity Log
                Text(
                    text = "سجل نشاط الوكيل (Agent Activity Log)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("agent_activity_log_list")
                ) {
                    items(logs) { log ->
                        ActivityLogItem(log, dateFormat)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionButton(
    level: AgentPermissionLevel,
    currentLevel: AgentPermissionLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = level == currentLevel
    val color = when (level) {
        AgentPermissionLevel.READ -> Color(0xFF4CAF50)
        AgentPermissionLevel.EDIT -> Color(0xFF2196F3)
        AgentPermissionLevel.EXECUTE -> Color(0xFFF44336)
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.testTag("permission_${level.name.lowercase()}_button"),
        colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = 0.1f)) else ButtonDefaults.outlinedButtonColors(),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) color else MaterialTheme.colorScheme.outline)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = level.name,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun ActivityLogItem(log: AgentActivityLog, dateFormat: SimpleDateFormat) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = log.actionType,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = dateFormat.format(Date(log.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = log.target,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = log.details,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
