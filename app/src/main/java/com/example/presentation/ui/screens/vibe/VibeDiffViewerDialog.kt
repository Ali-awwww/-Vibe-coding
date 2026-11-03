package com.example.presentation.ui.screens.vibe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.DiffChangeType
import com.example.domain.model.DiffLineType
import com.example.domain.model.FileDiff

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeDiffViewerDialog(
    diffs: List<FileDiff>,
    onDismiss: () -> Unit
) {
    var selectedFileIndex by remember { mutableIntStateOf(0) }
    val currentDiff = diffs.getOrNull(selectedFileIndex) ?: diffs.firstOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Dialog Top Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Difference, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "مراجعة الـ Diff والتغييرات البرمجية",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${diffs.size} ملفات معدلة",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    }
                }

                if (diffs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد تغييرات معلقة في الـ Diff حالياً.", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        // Left Files List
                        Surface(
                            modifier = Modifier
                                .width(240.dp)
                                .fillMaxHeight(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            tonalElevation = 1.dp
                        ) {
                            LazyColumn(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(diffs.indices.toList()) { index ->
                                    val item = diffs[index]
                                    val isSelected = index == selectedFileIndex

                                    Card(
                                        onClick = { selectedFileIndex = index },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                item.path.substringAfterLast("/"),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                item.path,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1
                                            )
                                            Row(
                                                modifier = Modifier.padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (item.additions > 0) {
                                                    Text("+${item.additions}", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                }
                                                if (item.deletions > 0) {
                                                    Text("-${item.deletions}", color = Color(0xFFC62828), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Right Diff Display
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            // File header
                            if (currentDiff != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(currentDiff.path, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                            Text(
                                                when (currentDiff.changeType) {
                                                    DiffChangeType.CREATED -> "ملف جديد (Created)"
                                                    DiffChangeType.MODIFIED -> "ملف معدل (Modified)"
                                                    DiffChangeType.DELETED -> "ملف محذوف (Deleted)"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when (currentDiff.changeType) {
                                                    DiffChangeType.CREATED -> Color(0xFF2E7D32)
                                                    DiffChangeType.MODIFIED -> Color(0xFF1565C0)
                                                    DiffChangeType.DELETED -> Color(0xFFC62828)
                                                }
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Surface(color = Color(0x204CAF50), shape = RoundedCornerShape(4.dp)) {
                                                Text("+${currentDiff.additions}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            Surface(color = Color(0x20F44336), shape = RoundedCornerShape(4.dp)) {
                                                Text("-${currentDiff.deletions}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color(0xFFC62828), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider()

                                // Code Lines with Strict LTR
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF1E1E1E))
                                            .padding(8.dp)
                                    ) {
                                        items(currentDiff.diffLines) { diffLine ->
                                            val (bgColor, textColor, prefix) = when (diffLine.type) {
                                                DiffLineType.ADD -> Triple(Color(0x302E7D32), Color(0xFF81C784), "+ ")
                                                DiffLineType.REMOVE -> Triple(Color(0x30C62828), Color(0xFFE57373), "- ")
                                                DiffLineType.CONTEXT -> Triple(Color.Transparent, Color(0xFFD4D4D4), "  ")
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(bgColor)
                                                    .padding(vertical = 1.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Line numbers
                                                Text(
                                                    text = (diffLine.oldLineNum?.toString() ?: "").padStart(4),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                                    color = Color.Gray,
                                                    modifier = Modifier.width(36.dp)
                                                )
                                                Text(
                                                    text = (diffLine.newLineNum?.toString() ?: "").padStart(4),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                                    color = Color.Gray,
                                                    modifier = Modifier.width(36.dp)
                                                )

                                                Text(
                                                    text = prefix + diffLine.content,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                                    color = textColor,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Actions Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) {
                        Text("تم الإغلاق")
                    }
                }
            }
        }
    }
}
