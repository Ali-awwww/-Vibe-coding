package com.example.presentation.ui.screens.vibe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.domain.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Composable
fun VibeTaskReviewDialog(
    review: WorkspaceTaskReview,
    onApprove: (reviewId: String, commitMessage: String?) -> Unit,
    onRevert: (reviewId: String) -> Unit,
    onBranch: (reviewId: String, newBranchName: String) -> Unit,
    onDismiss: () -> Unit
) {
    val moshi = remember { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
    val diffs = remember(review.diffsJson) {
        val adapter = moshi.adapter<List<FileDiff>>(Types.newParameterizedType(List::class.java, FileDiff::class.java))
        runCatching { adapter.fromJson(review.diffsJson) ?: emptyList() }.getOrDefault(emptyList())
    }
    val affectedTests = remember(review.affectedTestsJson) {
        val adapter = moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))
        runCatching { adapter.fromJson(review.affectedTestsJson) ?: emptyList() }.getOrDefault(emptyList())
    }

    var showBranchInput by remember { mutableStateOf(false) }
    var featureBranchName by remember { mutableStateOf("feature-task-${review.id.take(6)}") }
    var customCommitMsg by remember { mutableStateOf("") }

    val createdFiles = diffs.filter { it.changeType == DiffChangeType.CREATED }
    val modifiedFiles = diffs.filter { it.changeType == DiffChangeType.MODIFIED }
    val deletedFiles = diffs.filter { it.changeType == DiffChangeType.DELETED }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.RateReview, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Column {
                                Text(
                                    "مراجعة تغييرات المهمة (Task Review)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "الفرع المستهدف: ${review.branchName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Task intent & explanation
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("طلب المطور:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(review.taskPrompt, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text("سبب وطبيعة التغيير:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                Text(review.explanation, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // Change Summary statistics chips
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${createdFiles.size}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 18.sp)
                                    Text("ملفات جديدة", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1976D2).copy(alpha = 0.15f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${modifiedFiles.size}", fontWeight = FontWeight.Bold, color = Color(0xFF1976D2), fontSize = 18.sp)
                                    Text("ملفات معدلة", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1976D2))
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFD32F2F).copy(alpha = 0.15f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${deletedFiles.size}", fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F), fontSize = 18.sp)
                                    Text("ملفات محذوفة", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD32F2F))
                                }
                            }
                        }
                    }

                    // Affected tests
                    if (affectedTests.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF81C784), RoundedCornerShape(12.dp))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.FactCheck, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                        Text("الاختبارات المتأثرة بالتعديلات (${affectedTests.size}):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = Color(0xFF2E7D32))
                                    }
                                    affectedTests.forEach { testPath ->
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text("• $testPath", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = Color(0xFF1B5E20))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Diff Viewer
                    item {
                        Text("تفاصيل التغييرات (Diffs):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }

                    items(diffs) { fileDiff ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(fileDiff.path, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("+${fileDiff.additions}", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                        Text("-${fileDiff.deletions}", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // Render code diff lines
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF1E1E1E),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                            .horizontalScroll(rememberScrollState())
                                    ) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            fileDiff.diffLines.take(30).forEach { line ->
                                                val bg = when (line.type) {
                                                    DiffLineType.ADD -> Color(0x334CAF50)
                                                    DiffLineType.REMOVE -> Color(0x33F44336)
                                                    DiffLineType.CONTEXT -> Color.Transparent
                                                }
                                                val textColor = when (line.type) {
                                                    DiffLineType.ADD -> Color(0xFF81C784)
                                                    DiffLineType.REMOVE -> Color(0xFFE57373)
                                                    DiffLineType.CONTEXT -> Color(0xFFD0D0D0)
                                                }
                                                val prefix = when (line.type) {
                                                    DiffLineType.ADD -> "+ "
                                                    DiffLineType.REMOVE -> "- "
                                                    DiffLineType.CONTEXT -> "  "
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(bg)
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        prefix + line.content,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 11.sp,
                                                        color = textColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Feature Branch creation option
                    if (showBranchInput) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF7F52FF), RoundedCornerShape(12.dp))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("إنشاء فرع مستقل للمهمة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    Text("سيتم تحويل هذه التغييرات إلى فرع جديد للحفاظ على نظافة واستقرار الفرع الرئيسي.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    OutlinedTextField(
                                        value = featureBranchName,
                                        onValueChange = { featureBranchName = it },
                                        label = { Text("اسم الفرع الجديد") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        TextButton(onClick = { showBranchInput = false }) { Text("إلغاء") }
                                        Button(
                                            onClick = { onBranch(review.id, featureBranchName) },
                                            enabled = featureBranchName.isNotBlank()
                                        ) {
                                            Text("إنشاء الفرع ونقل التغييرات")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Actions Footer
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Approve Button
                            Button(
                                onClick = { onApprove(review.id, customCommitMsg.ifBlank { null }) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, null)
                                Spacer(Modifier.width(6.dp))
                                Text("اعتماد (Commit)")
                            }

                            // Branch Button
                            OutlinedButton(
                                onClick = { showBranchInput = !showBranchInput },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CallSplit, null)
                                Spacer(Modifier.width(6.dp))
                                Text("فرع مستقل")
                            }

                            // Revert Button
                            Button(
                                onClick = { onRevert(review.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Restore, null)
                                Spacer(Modifier.width(6.dp))
                                Text("تراجع (Revert)")
                            }
                        }
                    }
                }
            }
        }
    }
}
