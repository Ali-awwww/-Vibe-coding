package com.example.presentation.ui.screens.quality

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.quality.CompletionCriterion
import com.example.domain.model.quality.CriterionEvaluation
import com.example.domain.model.quality.ScreenAuditRecord
import com.example.domain.model.quality.ScreenCompletionStatus
import com.example.ui.theme.JetBrainsMono

@Composable
fun ScreenQualityInspectorDialog(
    record: ScreenAuditRecord,
    onDismiss: () -> Unit,
    onLaunchScreen: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("screen_inspector_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.screenNameAr,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = record.screenName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = when (record.status) {
                            ScreenCompletionStatus.PRODUCTION_READY -> Color(0xFF238636).copy(alpha = 0.2f)
                            ScreenCompletionStatus.VERIFIED -> Color(0xFF1F6FEB).copy(alpha = 0.2f)
                            ScreenCompletionStatus.FAILED, ScreenCompletionStatus.BLOCKED -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = record.status.labelAr,
                            color = when (record.status) {
                                ScreenCompletionStatus.PRODUCTION_READY -> Color(0xFF3FB950)
                                ScreenCompletionStatus.VERIFIED -> Color(0xFF58A6FF)
                                ScreenCompletionStatus.FAILED, ScreenCompletionStatus.BLOCKED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.tertiary
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_inspector_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Metadata cards
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Navigation, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Route: ${record.routeClass}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Source: ${record.sourceFilePath}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetBrainsMono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (record.testFilePath != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Rule, null, modifier = Modifier.size(16.dp), tint = Color(0xFF3FB950))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                text = "Test: ${record.testFilePath}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = JetBrainsMono,
                                    color = Color(0xFF3FB950)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "فحص الـ 13 معيارًا للاكتمال الهندسي",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${record.satisfiedApplicableCriteria} / ${record.totalApplicableCriteria} معيارًا محققًا (${record.completionRate.toInt()}%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JetBrainsMono
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { record.completionRate / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = if (record.completionRate >= 95f) Color(0xFF3FB950) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Criteria list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CompletionCriterion.values()) { criterion ->
                        val eval = record.evaluations[criterion]
                        CriterionRowItem(criterion = criterion, evaluation = eval)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إغلاق")
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onLaunchScreen(record.routeClass)
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("launch_inspected_screen_button")
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("فتح الشاشة للتجربة الحية")
                    }
                }
            }
        }
    }
}

@Composable
private fun CriterionRowItem(
    criterion: CompletionCriterion,
    evaluation: CriterionEvaluation?
) {
    val isSatisfied = evaluation?.isSatisfied == true
    val isApplicable = evaluation?.isApplicable != false

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (!isApplicable) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                else if (isSatisfied) Color(0xFF238636).copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = when {
                    !isApplicable -> Icons.Default.RemoveCircleOutline
                    isSatisfied -> Icons.Default.CheckCircle
                    else -> Icons.Default.Cancel
                },
                contentDescription = null,
                tint = when {
                    !isApplicable -> MaterialTheme.colorScheme.onSurfaceVariant
                    isSatisfied -> Color(0xFF3FB950)
                    else -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${criterion.code}: ${criterion.titleAr}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (!isApplicable) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "معفى (Exempt)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = criterion.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (evaluation != null) {
                    Spacer(Modifier.height(4.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = if (!isApplicable && evaluation.exemptionReason != null) {
                                "Reason: ${evaluation.exemptionReason}"
                            } else {
                                "Evidence: ${evaluation.evidence}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMono,
                            color = if (isSatisfied) Color(0xFF58A6FF) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
