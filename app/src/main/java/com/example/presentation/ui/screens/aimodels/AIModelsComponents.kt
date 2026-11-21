package com.example.presentation.ui.screens.aimodels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardActions
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.*
import com.example.presentation.viewmodel.AIModelsUiState
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens
import com.example.util.SecurityUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CautionAmber = Color(0xFFFFB74D)

@Composable
internal fun VibeSpaceBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val spacing = 36.dp.toPx()
        val lineColor = VibeDesignTokens.Color.Cyan.copy(alpha = 0.06f)
        var x = 0f
        while (x <= size.width) {
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            x += spacing
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            y += spacing
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(VibeDesignTokens.Color.Cyan.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.12f),
                radius = 220.dp.toPx()
            ),
            radius = 220.dp.toPx(),
            center = Offset(size.width * 0.85f, size.height * 0.12f)
        )
    }
}

@Composable
internal fun VibeSectionHeader(
    title: String,
    badge: String? = null,
    isLoading: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(VibeDesignTokens.Color.Cyan, VibeDesignTokens.Color.Violet)
                    )
                )
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = VibeDesignTokens.Color.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            Surface(
                color = VibeDesignTokens.Color.Cyan.copy(alpha = 0.14f),
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.4f))
            ) {
                Text(
                    text = badge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = VibeDesignTokens.Color.Cyan
                )
            }
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = VibeDesignTokens.Color.Cyan
            )
        }
    }
}

@Composable
internal fun AIAuthRequiredCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_models_auth_required_card"),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        border = BorderStroke(1.dp, VibeDesignTokens.Color.Violet.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(VibeDesignTokens.Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = VibeDesignTokens.Color.Violet
                )
                Text(
                    text = stringResource(R.string.ai_models_auth_required_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.TextPrimary
                )
            }
            Text(
                text = stringResource(R.string.ai_models_auth_required_body),
                style = MaterialTheme.typography.bodySmall,
                color = VibeDesignTokens.Color.TextMuted
            )
        }
    }
}

@Composable
internal fun AISettingsDashboard(uiState: AIModelsUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_models_dashboard_card"),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanelRaised),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    VibeDesignTokens.Color.Cyan.copy(alpha = 0.55f),
                    VibeDesignTokens.Color.Violet.copy(alpha = 0.40f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(VibeDesignTokens.Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(VibeDesignTokens.Color.Cyan)
                    )
                    Text(
                        text = stringResource(R.string.ai_models_telemetry_label),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        color = VibeDesignTokens.Color.Cyan
                    )
                }
                Text(
                    text = stringResource(R.string.ai_models_last_30_days),
                    style = MaterialTheme.typography.labelSmall,
                    color = VibeDesignTokens.Color.TextMuted
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium)
            ) {
                TelemetryMetricTile(
                    label = stringResource(R.string.ai_models_successful_requests),
                    value = uiState.successCount.toString(),
                    icon = Icons.Default.CheckCircle,
                    accent = VibeDesignTokens.Color.Success,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricTile(
                    label = stringResource(R.string.ai_models_failed_requests),
                    value = uiState.failedCount.toString(),
                    icon = Icons.Default.ErrorOutline,
                    accent = VibeDesignTokens.Color.Error,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = VibeDesignTokens.Color.Grid)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_models_last_successful_request),
                        style = MaterialTheme.typography.labelSmall,
                        color = VibeDesignTokens.Color.TextMuted
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = uiState.lastSuccessAt?.let {
                            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(it))
                        } ?: stringResource(R.string.ai_models_none),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.TextPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_models_estimated_cost_30_days),
                        style = MaterialTheme.typography.labelSmall,
                        color = VibeDesignTokens.Color.TextMuted
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$${String.format(Locale.US, "%.4f", uiState.totalCost)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.Cyan
                    )
                }
            }
            if (uiState.hasUnpricedUsage) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_models_unpriced_usage_warning"),
                    color = CautionAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                    border = BorderStroke(1.dp, CautionAmber.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CautionAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.ai_models_unpriced_usage_warning),
                            modifier = Modifier.testTag("ai_models_unpriced_usage_warning_message"),
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeDesignTokens.Color.TextPrimary.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = VibeDesignTokens.Color.SpaceBackground.copy(alpha = 0.72f),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = VibeDesignTokens.Color.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
internal fun AICostSettingsCard(
    uiState: AIModelsUiState,
    onFreeOnlyToggle: (Boolean) -> Unit,
    onAutoSelectToggle: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val freeOnlyLabel = stringResource(R.string.ai_models_free_ai_only)
    val autoSelectLabel = stringResource(R.string.ai_models_auto_select_free)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(VibeDesignTokens.Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_models_free_ai_only),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.ai_models_free_ai_only_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeDesignTokens.Color.TextMuted
                    )
                }
                Switch(
                    checked = uiState.freeOnly,
                    onCheckedChange = onFreeOnlyToggle,
                    enabled = enabled,
                    modifier = Modifier
                        .testTag("ai_models_free_only_switch")
                        .semantics { contentDescription = freeOnlyLabel },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VibeDesignTokens.Color.OnCyan,
                        checkedTrackColor = VibeDesignTokens.Color.Cyan,
                        uncheckedThumbColor = VibeDesignTokens.Color.TextMuted,
                        uncheckedTrackColor = VibeDesignTokens.Color.SpaceBackground
                    )
                )
            }

            Surface(
                color = CautionAmber.copy(alpha = 0.12f),
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                border = BorderStroke(1.dp, CautionAmber.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CautionAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.ai_models_firebase_billing_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeDesignTokens.Color.TextPrimary.copy(alpha = 0.9f)
                    )
                }
            }

            HorizontalDivider(color = VibeDesignTokens.Color.Grid)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_models_auto_select_free),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.ai_models_auto_select_free_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeDesignTokens.Color.TextMuted
                    )
                }
                Switch(
                    checked = uiState.autoSelectFree,
                    onCheckedChange = onAutoSelectToggle,
                    enabled = enabled,
                    modifier = Modifier
                        .testTag("ai_models_auto_select_switch")
                        .semantics { contentDescription = autoSelectLabel },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VibeDesignTokens.Color.OnCyan,
                        checkedTrackColor = VibeDesignTokens.Color.Cyan,
                        uncheckedThumbColor = VibeDesignTokens.Color.TextMuted,
                        uncheckedTrackColor = VibeDesignTokens.Color.SpaceBackground
                    )
                )
            }
        }
    }
}

@Composable
internal fun AIModelSelectionSection(
    models: List<AIModel>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    autoSelect: Boolean,
    freeOnly: Boolean,
    hasProviders: Boolean,
    hasConnectedProviders: Boolean,
    isLoading: Boolean,
    enabled: Boolean
) {
    if (autoSelect && models.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_models_smart_routing_card"),
            shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
            colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
            border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = VibeDesignTokens.Color.Cyan)
                Text(
                    text = stringResource(R.string.ai_models_smart_routing_active),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VibeDesignTokens.Color.TextPrimary
                )
            }
        }
    } else if (models.isEmpty()) {
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = VibeDesignTokens.Color.Cyan
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.ai_models_loading_models),
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextMuted
                )
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_models_empty_state"),
                color = VibeDesignTokens.Color.SpacePanel,
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                border = BorderStroke(1.dp, VibeDesignTokens.Color.Grid)
            ) {
                Text(
                    text = when {
                        !hasProviders -> stringResource(R.string.ai_models_empty_no_providers)
                        !hasConnectedProviders -> stringResource(R.string.ai_models_empty_no_connected_providers)
                        freeOnly -> stringResource(R.string.ai_models_empty_no_free_models)
                        else -> stringResource(R.string.ai_models_empty_no_synced_models)
                    },
                    modifier = Modifier
                        .padding(14.dp)
                        .testTag("ai_models_empty_state_message"),
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextMuted
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            models.forEach { model ->
                AIModelItem(
                    model = model,
                    isSelected = model.id == selectedId,
                    onSelect = { onSelect(model.id) },
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
internal fun AIModelItem(
    model: AIModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean = true
) {
    val accent = if (model.isFree) VibeDesignTokens.Color.Success else VibeDesignTokens.Color.Violet
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_model_item_${model.id}"),
        onClick = onSelect,
        enabled = enabled,
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                VibeDesignTokens.Color.SpacePanelRaised
            } else {
                VibeDesignTokens.Color.SpacePanel
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) VibeDesignTokens.Color.Cyan else VibeDesignTokens.Color.Grid
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.TextPrimary,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ModelStatusChip(
                        text = stringResource(if (model.isFree) R.string.ai_models_free else R.string.ai_models_paid),
                        icon = if (model.isFree) Icons.Default.Verified else Icons.Default.Payments,
                        color = accent
                    )
                }
                Text(
                    text = stringResource(R.string.ai_models_model_context, model.apiId, model.contextWindow / 1000),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = VibeDesignTokens.Color.Cyan.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.ai_models_model_pricing,
                        String.format(Locale.US, "%.2f", model.inputPricePer1M),
                        String.format(Locale.US, "%.2f", model.outputPricePer1M)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = VibeDesignTokens.Color.TextMuted
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.ai_models_default_model_selected),
                    tint = VibeDesignTokens.Color.Cyan
                )
            }
        }
    }
}

@Composable
internal fun AIProvidersGuide() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        border = BorderStroke(1.dp, VibeDesignTokens.Color.Violet.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(VibeDesignTokens.Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.ai_models_how_to_start),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = VibeDesignTokens.Color.TextPrimary
            )
            GuideStep("1", stringResource(R.string.ai_models_guide_create_account))
            GuideStep("2", stringResource(R.string.ai_models_guide_create_key))
            GuideStep("3", stringResource(R.string.ai_models_guide_add_key))
        }
    }
}

@Composable
private fun GuideStep(num: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(VibeDesignTokens.Color.Cyan.copy(alpha = 0.18f))
                .border(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.65f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = num,
                color = VibeDesignTokens.Color.Cyan,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = VibeDesignTokens.Color.TextMuted
        )
    }
}

@Composable
private fun ModelStatusChip(text: String, icon: ImageVector, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun AIProviderCard(
    provider: AIProvider,
    onSync: (String) -> Unit,
    onTest: (String) -> Unit,
    onDelete: (String) -> Unit,
    isLoading: Boolean
) {
    val isBuiltInPlatform = provider.type == AIProviderType.FIREBASE_AI_LOGIC
    val isSupported = provider.type == AIProviderType.GOOGLE_GEMINI ||
        provider.type == AIProviderType.OPEN_ROUTER ||
        isBuiltInPlatform
    val displayedStatus = if (isSupported) provider.status else AIProviderStatus.UNSUPPORTED
    val maskedKey = remember(provider.apiKey) {
        val raw = provider.apiKey ?: ""
        runCatching { SecurityUtils.maskSecret(raw) }.getOrElse {
            if (raw.isBlank()) "" else "••••••••"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_provider_card_${provider.id}"),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        border = BorderStroke(
            1.dp,
            if (isSupported) VibeDesignTokens.Color.Cyan.copy(alpha = 0.30f)
            else VibeDesignTokens.Color.Error.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(VibeDesignTokens.Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(VibeDesignTokens.Shape.Control))
                        .background(VibeDesignTokens.Color.Cyan.copy(alpha = 0.12f))
                        .border(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.35f), RoundedCornerShape(VibeDesignTokens.Shape.Control)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (provider.type) {
                            AIProviderType.FIREBASE_AI_LOGIC -> Icons.Default.Bolt
                            AIProviderType.GOOGLE_GEMINI -> Icons.Default.AutoAwesome
                            AIProviderType.OPEN_ROUTER -> Icons.Default.Hub
                            else -> Icons.Default.CloudQueue
                        },
                        contentDescription = null,
                        tint = if (isSupported) VibeDesignTokens.Color.Cyan else VibeDesignTokens.Color.TextMuted
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = provider.type.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        color = VibeDesignTokens.Color.TextMuted
                    )
                }
                ProviderStatusBadge(
                    status = displayedStatus,
                    modifier = Modifier.testTag("ai_provider_status_${provider.id}")
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = VibeDesignTokens.Color.SpaceBackground.copy(alpha = 0.85f),
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                border = BorderStroke(1.dp, VibeDesignTokens.Color.Grid)
            ) {
                Text(
                    text = if (isBuiltInPlatform) {
                        stringResource(R.string.ai_models_builtin_provider_label)
                    } else {
                        stringResource(R.string.ai_models_api_key_value, maskedKey)
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetBrainsMono,
                    color = VibeDesignTokens.Color.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isSupported) {
                Text(
                    text = stringResource(R.string.ai_models_unsupported_provider),
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.Error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onTest(provider.id) },
                    enabled = !isLoading && isSupported,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ai_provider_test_${provider.id}"),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                    border = BorderStroke(
                        1.dp,
                        if (!isLoading && isSupported) VibeDesignTokens.Color.Cyan.copy(alpha = 0.55f)
                        else VibeDesignTokens.Color.Grid
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = VibeDesignTokens.Color.Cyan,
                        disabledContentColor = VibeDesignTokens.Color.TextMuted.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ai_models_test_connection))
                }

                OutlinedButton(
                    onClick = { onSync(provider.id) },
                    enabled = !isLoading && isSupported,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ai_provider_sync_${provider.id}"),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                    border = BorderStroke(
                        1.dp,
                        if (!isLoading && isSupported) VibeDesignTokens.Color.Violet.copy(alpha = 0.55f)
                        else VibeDesignTokens.Color.Grid
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = VibeDesignTokens.Color.TextPrimary,
                        disabledContentColor = VibeDesignTokens.Color.TextMuted.copy(alpha = 0.5f)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = VibeDesignTokens.Color.Cyan
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ai_models_sync_models))
                }

                if (!isBuiltInPlatform) {
                    Spacer(Modifier.weight(1f))

                    IconButton(
                        onClick = { onDelete(provider.id) },
                        enabled = !isLoading,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("ai_provider_delete_${provider.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.ai_models_delete_provider_content_description, provider.name),
                            tint = VibeDesignTokens.Color.Error
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProviderStatusBadge(
    status: AIProviderStatus,
    modifier: Modifier = Modifier
) {
    val (color, icon, labelRes) = when (status) {
        AIProviderStatus.CONNECTED -> Triple(VibeDesignTokens.Color.Success, Icons.Default.CheckCircle, R.string.ai_models_status_connected)
        AIProviderStatus.DISCONNECTED -> Triple(VibeDesignTokens.Color.TextMuted, Icons.Default.LinkOff, R.string.ai_models_status_disconnected)
        AIProviderStatus.ERROR -> Triple(VibeDesignTokens.Color.Error, Icons.Default.ErrorOutline, R.string.ai_models_status_error)
        AIProviderStatus.RATE_LIMITED -> Triple(CautionAmber, Icons.Default.Speed, R.string.ai_models_status_rate_limited)
        AIProviderStatus.UNSUPPORTED -> Triple(VibeDesignTokens.Color.Error, Icons.Default.Block, R.string.ai_models_status_unsupported)
    }
    val label = stringResource(labelRes)
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun AddAIProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, AIProviderType, String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AIProviderType.OPEN_ROUTER) }
    var key by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    val keyVisibilityDescription = stringResource(
        if (keyVisible) R.string.ai_models_hide_key else R.string.ai_models_show_key
    )
    val submitProvider = {
        if (name.isNotBlank() && key.isNotBlank()) {
            onConfirm(name.trim(), type, key.trim(), null)
        }
    }

    AlertDialog(
        modifier = Modifier.testTag("add_provider_dialog").imePadding(),
        onDismissRequest = onDismiss,
        containerColor = VibeDesignTokens.Color.SpacePanelRaised,
        titleContentColor = VibeDesignTokens.Color.TextPrimary,
        textContentColor = VibeDesignTokens.Color.TextPrimary,
        title = { Text(stringResource(R.string.ai_models_add_provider_dialog_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("add_provider_dialog_content"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ai_models_provider_name_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_provider_name_input")
                )

                Text(
                    text = stringResource(R.string.ai_models_provider_type_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = VibeDesignTokens.Color.TextMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AIProviderType.OPEN_ROUTER, AIProviderType.GOOGLE_GEMINI).forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = {
                                Text(
                                    text = if (t == AIProviderType.OPEN_ROUTER) "OpenRouter" else "Google Gemini",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.ai_models_api_key_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_provider_key_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitProvider() }),
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { keyVisible = !keyVisible },
                            modifier = Modifier.testTag("add_provider_key_visibility_toggle")
                        ) {
                            Icon(
                                if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = keyVisibilityDescription
                            )
                        }
                    }
                )

                Surface(
                    color = VibeDesignTokens.Color.SpaceBackground,
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                    border = BorderStroke(1.dp, VibeDesignTokens.Color.Grid)
                ) {
                    Text(
                        text = stringResource(R.string.ai_models_key_storage_notice),
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = VibeDesignTokens.Color.TextMuted
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = submitProvider,
                enabled = name.isNotBlank() && key.isNotBlank(),
                modifier = Modifier.testTag("add_provider_save_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeDesignTokens.Color.Cyan,
                    contentColor = VibeDesignTokens.Color.OnCyan
                )
            ) {
                Text(stringResource(R.string.ai_models_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ai_models_cancel), color = VibeDesignTokens.Color.TextMuted)
            }
        }
    )
}
