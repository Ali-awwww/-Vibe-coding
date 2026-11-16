package com.example.presentation.ui.screens.validation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.validation.ValidationCategory
import com.example.domain.validation.ValidationStatus
import com.example.domain.validation.ValidationTestItem
import com.example.presentation.viewmodel.TeamValidationUiState
import com.example.presentation.viewmodel.TeamValidationViewModel

@Composable
fun ValidationMatrixTab(
    uiState: TeamValidationUiState,
    viewModel: TeamValidationViewModel
) {
    val progress = uiState.matrixProgress
    val filteredItems = if (uiState.selectedCategory == null) {
        progress.items
    } else {
        progress.items.filter { it.category == uiState.selectedCategory }
    }

    val passedCount = progress.items.count { it.status == ValidationStatus.PASSED }
    val failedCount = progress.items.count { it.status == ValidationStatus.FAILED }
    val runningCount = progress.items.count { it.status == ValidationStatus.RUNNING }
    val skippedCount = progress.items.count { it.status == ValidationStatus.SKIPPED }
    val pendingCount = progress.items.count { it.status == ValidationStatus.PENDING }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Stats & Action Bar
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "مصفوفة فحص الـ 20 معيارًا",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "التحقق من التعاون الفعلي، عزل المفاتيح، والحوكمة الصفرية",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { viewModel.runAllMatrixTests() },
                            enabled = !progress.isRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("run_all_validation_tests_button")
                        ) {
                            if (progress.isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري الفحص (${progress.currentTestIndex}/${progress.totalTests})")
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تشغيل الكل")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress indicators
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatPill("ناجح: $passedCount", MaterialTheme.colorScheme.primary, Icons.Default.CheckCircle)
                        if (failedCount > 0) {
                            StatPill("فاشل: $failedCount", MaterialTheme.colorScheme.error, Icons.Default.Error)
                        }
                        if (skippedCount > 0) {
                            StatPill("متجاوز: $skippedCount", MaterialTheme.colorScheme.outline, Icons.Default.Block)
                        }
                        if (pendingCount > 0) {
                            StatPill("لم يُنفذ: $pendingCount", MaterialTheme.colorScheme.outline, Icons.Default.Schedule)
                        }
                        if (runningCount > 0) {
                            StatPill("قيد التشغيل: $runningCount", MaterialTheme.colorScheme.primary, Icons.Default.Sync)
                        }
                        StatPill("المجموع: ${progress.items.size}", MaterialTheme.colorScheme.outline, Icons.Default.FactCheck)
                    }
                }
            }
        }

        // Category Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedCategory == null,
                    onClick = { viewModel.filterCategory(null) },
                    label = { Text("الكل (${progress.items.size})") }
                )
                ValidationCategory.entries.forEach { category ->
                    val count = progress.items.count { it.category == category }
                    FilterChip(
                        selected = uiState.selectedCategory == category,
                        onClick = { viewModel.filterCategory(category) },
                        label = { Text("${category.labelAr} ($count)") }
                    )
                }
            }
        }

        // Test Items
        items(filteredItems, key = { it.id }) { testItem ->
            val isExpanded = uiState.expandedTestIds.contains(testItem.id)
            ValidationTestCard(
                testItem = testItem,
                isExpanded = isExpanded,
                onToggleExpand = { viewModel.toggleExpandTest(testItem.id) },
                onRunSingle = { viewModel.runSingleTest(testItem.id) },
                isRunning = progress.isRunning
            )
        }
    }
}

@Composable
fun ValidationTestCard(
    testItem: ValidationTestItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRunSingle: () -> Unit,
    isRunning: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (testItem.status) {
                ValidationStatus.PASSED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ValidationStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ValidationStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ValidationStatus.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggleExpand() }
            .testTag("validation_card_${testItem.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Icon
                when (testItem.status) {
                    ValidationStatus.PASSED -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Passed",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(24.dp)
                    )
                    ValidationStatus.FAILED -> Icon(
                        Icons.Default.Cancel,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    ValidationStatus.RUNNING -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ValidationStatus.SKIPPED -> Icon(
                        Icons.Default.Block,
                        contentDescription = "Skipped",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                    else -> Icon(
                        Icons.Default.Schedule,
                        contentDescription = "Pending",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${testItem.id}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = testItem.nameAr,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = testItem.nameEn,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (testItem.durationMs > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${testItem.durationMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onRunSingle,
                    enabled = !isRunning && testItem.status != ValidationStatus.RUNNING,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Re-test",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    Text(
                        text = testItem.descriptionAr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (testItem.modelsInvolved.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "النماذج/المحركات:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            testItem.modelsInvolved.forEach { model ->
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = model,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    testItem.evidence?.let { evidence ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "الدليل البرمجي والتحقق الفعلي:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Text(
                                text = evidence,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    testItem.errorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "خطأ: $err",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatPill(label: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
