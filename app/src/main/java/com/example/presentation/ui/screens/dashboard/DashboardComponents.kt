package com.example.presentation.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.*
import com.example.presentation.viewmodel.DashboardUiState
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens

@Composable
internal fun CosmicDashboardBackdrop(modifier: Modifier = Modifier) {
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
                center = Offset(size.width * 0.85f, size.height * 0.10f),
                radius = size.minDimension * 0.60f
            ),
            radius = size.minDimension * 0.60f,
            center = Offset(size.width * 0.85f, size.height * 0.10f)
        )
        val grid = VibeDesignTokens.Color.Cyan.copy(alpha = 0.06f)
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
internal fun DashboardPanel(
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
internal fun DashboardSectionHeader(
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
internal fun DashboardAuthRequiredCard(
    onSignIn: () -> Unit
) {
    DashboardPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_auth_required_card"),
        borderColor = VibeDesignTokens.Color.Violet.copy(alpha = 0.55f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium)) {
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
                    text = "تسجيل الدخول مطلوب",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.TextPrimary
                )
            }
            Text(
                text = "لحماية مشاريعك وبيانات الأعطال، يرجى تسجيل الدخول لعرض لوحة التحكم والإحصائيات.",
                style = MaterialTheme.typography.bodySmall,
                color = VibeDesignTokens.Color.TextMuted
            )
            Button(
                onClick = onSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("dashboard_sign_in_button"),
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeDesignTokens.Color.Cyan,
                    contentColor = VibeDesignTokens.Color.OnCyan
                )
            ) {
                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("تسجيل الدخول", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun DashboardErrorBanner(
    errorMessage: String,
    onRetry: () -> Unit
) {
    DashboardPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_error_card"),
        borderColor = VibeDesignTokens.Color.Error.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = VibeDesignTokens.Color.Error
            )
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = VibeDesignTokens.Color.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dashboard_retry_button"),
                border = BorderStroke(1.dp, VibeDesignTokens.Color.Error.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VibeDesignTokens.Color.Error)
            ) {
                Text("إعادة المحاولة")
            }
        }
    }
}

@Composable
internal fun DeveloperSessionHeader(
    user: UserAccount?,
    isCloudConfigured: Boolean
) {
    // Configuration presence is not proof that a remote sync has succeeded.
    val badgeColor = VibeDesignTokens.Color.Warning
    val badgeLabel = stringResource(
        if (isCloudConfigured) R.string.dashboard_firebase_configured_sync_unverified
        else R.string.dashboard_local_cache_cloud_unconfigured
    )
    val accountLabel = user?.displayName?.takeIf { it.isNotBlank() }
        ?: user?.email?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.dashboard_account_label)

    DashboardPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_session_header")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = VibeDesignTokens.Color.Cyan.copy(alpha = 0.14f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.4f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = VibeDesignTokens.Color.Cyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.dashboard_greeting, accountLabel),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VibeDesignTokens.Color.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.dashboard_account_context),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMono,
                            color = VibeDesignTokens.Color.TextMuted
                        )
                    }
                }
            }

            Surface(
                color = badgeColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)),
                modifier = Modifier
                    .semantics(mergeDescendants = true) {}
                    .testTag("dashboard_sync_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isCloudConfigured) Icons.Default.Cloud else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProjectContextFilterRow(
    projects: List<Project>,
    selectedProjectId: String?,
    onSelectProject: (String?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_project_filter_row")
    ) {
        item {
            FilterChip(
                selected = selectedProjectId == null,
                onClick = { onSelectProject(null) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dashboard_project_chip_all"),
                label = { Text("الكل (${projects.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VibeDesignTokens.Color.Cyan.copy(alpha = 0.2f),
                    selectedLabelColor = VibeDesignTokens.Color.Cyan,
                    labelColor = VibeDesignTokens.Color.TextMuted
                )
            )
        }
        items(projects, key = { it.id }) { project ->
            FilterChip(
                selected = selectedProjectId == project.id,
                onClick = { onSelectProject(project.id) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dashboard_project_chip_${project.id}"),
                label = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VibeDesignTokens.Color.Cyan.copy(alpha = 0.2f),
                    selectedLabelColor = VibeDesignTokens.Color.Cyan,
                    labelColor = VibeDesignTokens.Color.TextMuted
                )
            )
        }
    }
}

@Composable
internal fun DashboardStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(tag),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(VibeDesignTokens.Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = VibeDesignTokens.Color.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = JetBrainsMono,
                color = VibeDesignTokens.Color.TextPrimary
            )
        }
    }
}

@Composable
internal fun DashboardToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag(tag),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(VibeDesignTokens.Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = accent.copy(alpha = 0.15f),
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = VibeDesignTokens.Color.TextMuted
            )
        }
    }
}

@Composable
internal fun DashboardCompactToolCard(
    title: String,
    icon: ImageVector,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(tag),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(VibeDesignTokens.Spacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VibeDesignTokens.Color.Cyan,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = VibeDesignTokens.Color.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun DashboardIssueRow(issue: Issue, onClick: () -> Unit) {
    val severityColor = when (issue.severity) {
        Severity.LOW -> VibeDesignTokens.Color.Cyan
        Severity.MEDIUM -> VibeDesignTokens.Color.Warning
        Severity.HIGH, Severity.CRITICAL -> VibeDesignTokens.Color.Error
    }
    val statusLabel = when (issue.status) {
        IssueStatus.NEW -> stringResource(R.string.dashboard_issue_status_new)
        IssueStatus.INVESTIGATING -> stringResource(R.string.dashboard_issue_status_investigating)
        IssueStatus.RESOLVED -> stringResource(R.string.dashboard_issue_status_resolved)
        IssueStatus.REOPENED -> stringResource(R.string.dashboard_issue_status_reopened)
    }
    val severityLabel = when (issue.severity) {
        Severity.LOW -> stringResource(R.string.crashes_severity_low)
        Severity.MEDIUM -> stringResource(R.string.crashes_severity_medium)
        Severity.HIGH -> stringResource(R.string.crashes_severity_high)
        Severity.CRITICAL -> stringResource(R.string.crashes_severity_critical)
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("dashboard_issue_${issue.id}"),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        border = BorderStroke(1.dp, severityColor.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(VibeDesignTokens.Spacing.Medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = issue.title.ifBlank { stringResource(R.string.dashboard_untitled_issue) },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = VibeDesignTokens.Color.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (issue.subtitle.isNotBlank()) {
                Text(
                    text = issue.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = VibeDesignTokens.Color.Cyan.copy(alpha = 0.12f), shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact)) {
                    Text(statusLabel, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = VibeDesignTokens.Color.Cyan)
                }
                Surface(color = severityColor.copy(alpha = 0.12f), shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact)) {
                    Text(severityLabel, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = severityColor)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.dashboard_issue_occurrences, issue.occurrenceCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = VibeDesignTokens.Color.TextMuted
                )
            }
        }
    }
}

internal fun DashboardCrashRow(
    crash: CrashReport,
    onClick: () -> Unit
) {
    val severityColor = when (crash.severity) {
        Severity.LOW -> VibeDesignTokens.Color.Cyan
        Severity.MEDIUM -> VibeDesignTokens.Color.Warning
        Severity.HIGH, Severity.CRITICAL -> VibeDesignTokens.Color.Error
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("dashboard_crash_${crash.id}"),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        border = BorderStroke(1.dp, severityColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(VibeDesignTokens.Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(severityColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = crash.exceptionType,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetBrainsMono,
                    color = VibeDesignTokens.Color.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = crash.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = VibeDesignTokens.Color.TextMuted
            )
        }
    }
}

@Composable
internal fun DashboardNotificationsSheetContent(
    uiState: DashboardUiState,
    onMarkNotificationRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onClearAll: () -> Unit,
    onOpenCrash: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = VibeDesignTokens.Color.Cyan)
                Text(
                    text = "مركز الإشعارات والتنبيهات",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.TextPrimary
                )
            }
            if (uiState.notifications.isNotEmpty()) {
                Row {
                    TextButton(
                        onClick = onMarkAllRead,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("قراءة الكل", color = VibeDesignTokens.Color.Cyan)
                    }
                    TextButton(
                        onClick = onClearAll,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("مسح", color = VibeDesignTokens.Color.Error)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (uiState.notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = VibeDesignTokens.Color.TextMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "لا توجد إشعارات جديدة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VibeDesignTokens.Color.TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                items(uiState.notifications, key = { it.id }) { notif ->
                    val notifColor = when (notif.type) {
                        NotificationType.CRASH_ALERT, NotificationType.VERIFICATION_FAILED -> VibeDesignTokens.Color.Error
                        NotificationType.ANR_ALERT -> VibeDesignTokens.Color.Warning
                        NotificationType.FIX_READY -> VibeDesignTokens.Color.Cyan
                        NotificationType.PR_CREATED, NotificationType.SECURITY_ALERT -> VibeDesignTokens.Color.Violet
                        NotificationType.VERIFICATION_PASSED -> VibeDesignTokens.Color.Success
                    }
                    val notifIcon = when (notif.type) {
                        NotificationType.CRASH_ALERT -> Icons.Default.BugReport
                        NotificationType.ANR_ALERT -> Icons.Default.HourglassBottom
                        NotificationType.FIX_READY -> Icons.Default.AutoFixHigh
                        NotificationType.PR_CREATED -> Icons.Default.CallMerge
                        NotificationType.VERIFICATION_PASSED -> Icons.Default.CheckCircle
                        NotificationType.VERIFICATION_FAILED -> Icons.Default.Cancel
                        NotificationType.SECURITY_ALERT -> Icons.Default.Security
                    }

                    Surface(
                        onClick = {
                            onMarkNotificationRead(notif.id)
                            notif.targetCrashId?.takeIf { it.isNotBlank() }?.let(onOpenCrash)
                        },
                        color = if (!notif.isRead) VibeDesignTokens.Color.SpacePanelRaised else VibeDesignTokens.Color.SpacePanel,
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                        border = BorderStroke(1.dp, notifColor.copy(alpha = if (!notif.isRead) 0.45f else 0.18f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = notifColor.copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = notifIcon,
                                        contentDescription = null,
                                        tint = notifColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = notif.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (!notif.isRead) FontWeight.Bold else FontWeight.Normal,
                                        color = VibeDesignTokens.Color.TextPrimary
                                    )
                                    if (!notif.isRead) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(VibeDesignTokens.Color.Cyan)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = notif.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VibeDesignTokens.Color.TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
