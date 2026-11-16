package com.example.presentation.ui.screens.teams

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.presentation.viewmodel.ModelTeamsUiState
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens

@Composable
internal fun CosmicTeamsBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    VibeDesignTokens.Color.SpacePanelRaised,
                    VibeDesignTokens.Color.SpaceBackground,
                    VibeDesignTokens.Color.SpaceBackground
                )
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    VibeDesignTokens.Color.Violet.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.85f, size.height * 0.12f),
                radius = size.minDimension * 0.55f
            ),
            radius = size.minDimension * 0.55f,
            center = Offset(size.width * 0.85f, size.height * 0.12f)
        )
        val grid = VibeDesignTokens.Color.Cyan.copy(alpha = 0.05f)
        val step = 32.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            y += step
        }
    }
}

@Composable
internal fun TeamCyberPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = VibeDesignTokens.Color.Cyan.copy(alpha = 0.28f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(VibeDesignTokens.Spacing.Medium),
            content = content
        )
    }
}

@Composable
internal fun TeamSectionHeader(
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
internal fun TeamsAuthRequiredCard(
    title: String,
    subtitle: String,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    TeamCyberPanel(
        modifier = modifier.fillMaxWidth(),
        borderColor = VibeDesignTokens.Color.Violet.copy(alpha = 0.55f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = VibeDesignTokens.Color.Violet
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.TextPrimary
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = VibeDesignTokens.Color.TextMuted
            )
            Button(
                onClick = onSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("teams_auth_signin_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeDesignTokens.Color.Cyan,
                    contentColor = VibeDesignTokens.Color.OnCyan
                ),
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Control)
            ) {
                Icon(Icons.Default.Login, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("تسجيل الدخول", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun TeamsErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    TeamCyberPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("teams_error_banner"),
        borderColor = VibeDesignTokens.Color.Error.copy(alpha = 0.6f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = VibeDesignTokens.Color.Error)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إعادة المحاولة")
                }
                TextButton(onClick = onDismiss) {
                    Text("إغلاق", color = VibeDesignTokens.Color.TextMuted)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TeamsNetworkStatusBanner(
    uiState: ModelTeamsUiState
) {
    val totalConfiguredMembers = uiState.teams.sumOf { it.members.size }
    val activeConnectedMembers = uiState.teams.sumOf { team ->
        team.members.count { it.providerId.isNotBlank() && it.providerId in uiState.connectedProviderIds }
    }

    TeamCyberPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("teams_network_status_banner"),
        borderColor = VibeDesignTokens.Color.Cyan.copy(alpha = 0.35f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "شبكة فرق الوكلاء",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.TextPrimary
                    )
                    Text(
                        text = "فصل صريح بين الوكلاء المهيّئين والاتصال الفعلي بالمزودين",
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeDesignTokens.Color.TextMuted
                    )
                }
                Surface(
                    color = (if (uiState.isCloudConfigured) VibeDesignTokens.Color.Cyan else VibeDesignTokens.Color.Warning).copy(alpha = 0.14f),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                    modifier = Modifier.semantics(mergeDescendants = true) {}.testTag("teams_sync_badge")
                ) {
                    Text(
                        text = if (uiState.isCloudConfigured) "CLOUD READY" else "LOCAL MODE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        color = if (uiState.isCloudConfigured) VibeDesignTokens.Color.Cyan else VibeDesignTokens.Color.Warning
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(
                    label = "الفرق: ${uiState.teams.size}",
                    accent = VibeDesignTokens.Color.Cyan,
                    tag = "teams_count_pill"
                )
                StatusPill(
                    label = "أعضاء مهيّئون: $totalConfiguredMembers",
                    accent = VibeDesignTokens.Color.Violet,
                    tag = "teams_configured_agents_pill"
                )
                StatusPill(
                    label = "متصل فعلياً: $activeConnectedMembers",
                    accent = if (activeConnectedMembers > 0) VibeDesignTokens.Color.Success else VibeDesignTokens.Color.Warning,
                    tag = "teams_connected_agents_pill"
                )
            }
        }
    }
}

@Composable
internal fun StatusPill(
    label: String,
    accent: Color,
    tag: String? = null
) {
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .let { if (tag != null) it.testTag(tag) else it }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}
