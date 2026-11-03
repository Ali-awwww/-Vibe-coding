package com.example.presentation.ui.screens.vibe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.WorkspaceCheckpoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VibeCheckpointsDialog(
    checkpoints: List<WorkspaceCheckpoint>,
    onCreateCheckpoint: (title: String, description: String) -> Unit,
    onRevertToCheckpoint: (checkpointId: String) -> Unit,
    onDeleteCheckpoint: (checkpointId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateForm by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newDescription by remember { mutableStateOf("") }
    var confirmRevertId by remember { mutableStateOf<String?>(null) }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.primary)
                        Text("نقاط الاستعادة (Checkpoints)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null)
                    }
                }

                Text(
                    "يحتفظ النظام تلقائياً بنسخة احتياطية قبل كل تعديل برمجي. يمكنك استعادة أي حالة سابقة بضغطة زر.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(Modifier.height(8.dp))

                if (showCreateForm) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("إنشاء نقطة استعادة يدوية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = newTitle,
                                onValueChange = { newTitle = it },
                                label = { Text("عنوان النقطة") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newDescription,
                                onValueChange = { newDescription = it },
                                label = { Text("الوصف") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (newTitle.isNotBlank()) {
                                            onCreateCheckpoint(newTitle, newDescription)
                                            newTitle = ""
                                            newDescription = ""
                                            showCreateForm = false
                                        }
                                    },
                                    enabled = newTitle.isNotBlank()
                                ) {
                                    Text("حفظ النقطة")
                                }
                                TextButton(onClick = { showCreateForm = false }) {
                                    Text("إلغاء")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    OutlinedButton(
                        onClick = { showCreateForm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("إنشاء نقطة استعادة جديدة يدوياً")
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Checkpoints List
                if (checkpoints.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("لا توجد نقاط استعادة مسجلة بعد لهذا المشروع.", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(checkpoints) { checkpoint ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            checkpoint.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            dateFormat.format(Date(checkpoint.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    if (checkpoint.description.isNotBlank()) {
                                        Text(
                                            checkpoint.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                "${checkpoint.totalFiles} ملف محفوظ",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            IconButton(
                                                onClick = { onDeleteCheckpoint(checkpoint.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, "Delete", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                            }

                                            Button(
                                                onClick = { confirmRevertId = checkpoint.id },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("استعادة هذه الحالة")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog before rollback
    if (confirmRevertId != null) {
        AlertDialog(
            onDismissRequest = { confirmRevertId = null },
            title = { Text("تأكيد استعادة نقطة سابقة") },
            text = { Text("هل أنت متأكد من استعادة هذه النقطة؟ سيتم إرجاع جميع ملفات المشروع إلى حالتها الدقيقة عند التقاط هذه النسخة.") },
            confirmButton = {
                Button(
                    onClick = {
                        val id = confirmRevertId
                        confirmRevertId = null
                        if (id != null) {
                            onRevertToCheckpoint(id)
                            onDismiss()
                        }
                    }
                ) {
                    Text("نعم، استعد الكود")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevertId = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
