package com.example.presentation.ui.screens.teams

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.CollaborationStyle
import com.example.domain.model.TeamGovernanceSettings
import com.example.domain.model.TeamSettings
import com.example.ui.theme.VibeDesignTokens

@Composable
internal fun CollaborationStyleSelector(
    selectedStyle: CollaborationStyle,
    onStyleSelected: (CollaborationStyle) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CollaborationStyle.values().forEach { style ->
            val selected = selectedStyle == style
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(VibeDesignTokens.Shape.Control))
                    .background(
                        if (selected) VibeDesignTokens.Color.Cyan.copy(alpha = 0.14f)
                        else VibeDesignTokens.Color.SpacePanel
                    )
                    .clickable { onStyleSelected(style) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected, onClick = { onStyleSelected(style) })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = style.labelAr,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.TextPrimary
                    )
                    Text(
                        text = style.labelEn,
                        style = MaterialTheme.typography.labelSmall,
                        color = VibeDesignTokens.Color.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
internal fun TeamSettingsSection(
    settings: TeamSettings,
    onSettingsChanged: (TeamSettings) -> Unit
) {
    TeamCyberPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "إعدادات التنسيق المتقدمة",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = VibeDesignTokens.Color.TextPrimary
            )

            Column {
                Text(
                    text = "عدد جولات المراجعة: ${settings.maxReviewRounds}",
                    style = MaterialTheme.typography.labelMedium,
                    color = VibeDesignTokens.Color.TextMuted
                )
                Slider(
                    value = settings.maxReviewRounds.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(maxReviewRounds = it.toInt())) },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }

            Column {
                Text(
                    text = "الطلبات المتزامنة: ${settings.maxConcurrentRequests}",
                    style = MaterialTheme.typography.labelMedium,
                    color = VibeDesignTokens.Color.TextMuted
                )
                Slider(
                    value = settings.maxConcurrentRequests.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(maxConcurrentRequests = it.toInt())) },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "التوقف عند فشل أي خطوة حرجة",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextPrimary
                )
                Switch(
                    checked = settings.stopOnFailure,
                    onCheckedChange = { onSettingsChanged(settings.copy(stopOnFailure = it)) }
                )
            }
        }
    }
}

@Composable
internal fun TeamGovernanceCreationSection(
    governance: TeamGovernanceSettings,
    onGovernanceChanged: (TeamGovernanceSettings) -> Unit
) {
    TeamCyberPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "إعدادات التكلفة والحوكمة",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = VibeDesignTokens.Color.TextPrimary
            )

            Surface(
                color = if (governance.freeOnly) VibeDesignTokens.Color.Success.copy(alpha = 0.14f) else VibeDesignTokens.Color.SpacePanelRaised,
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                border = BorderStroke(
                    1.dp,
                    if (governance.freeOnly) VibeDesignTokens.Color.Success.copy(alpha = 0.45f) else VibeDesignTokens.Color.Grid
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "النماذج المجانية فقط (Free Only)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = VibeDesignTokens.Color.TextPrimary
                        )
                        Text(
                            text = "يمنع استدعاء النماذج المدفوعة أو التحويل إليها تلقائياً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeDesignTokens.Color.TextMuted
                        )
                    }
                    Switch(
                        checked = governance.freeOnly,
                        onCheckedChange = { onGovernanceChanged(governance.copy(freeOnly = it)) }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "التبديل التلقائي لبديل مجاني عند نفاد الحصة (429)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextPrimary
                )
                Switch(
                    checked = governance.autoFallbackToFree,
                    onCheckedChange = { onGovernanceChanged(governance.copy(autoFallbackToFree = it)) }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "توزيع الأحمال على المزودين المتصلين",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextPrimary
                )
                Switch(
                    checked = governance.enableLoadBalancing,
                    onCheckedChange = { onGovernanceChanged(governance.copy(enableLoadBalancing = it)) }
                )
            }
        }
    }
}
