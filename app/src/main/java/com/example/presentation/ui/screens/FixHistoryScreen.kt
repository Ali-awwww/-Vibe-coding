package com.example.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.R
import com.example.domain.model.CrashVerificationRecord
import com.example.domain.model.VerificationStatus
import com.example.presentation.viewmodel.FixHistoryViewModel
import com.example.ui.components.EngCard
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixHistoryScreen(
    projectId: String? = null,
    navController: NavController,
    viewModel: FixHistoryViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.loadData(projectId)
    }

    Scaffold(
        containerColor = VibeDesignTokens.Color.SpaceBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.fix_history), fontWeight = FontWeight.Bold)
                        Text(
                            "VERIFICATION / AUDIT LOG",
                            style = MaterialTheme.typography.labelSmall,
                            color = VibeDesignTokens.Color.Cyan
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VibeDesignTokens.Color.Cyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VibeDesignTokens.Color.SpaceBackground,
                    titleContentColor = VibeDesignTokens.Color.TextPrimary,
                    navigationIconContentColor = VibeDesignTokens.Color.Cyan
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = VibeDesignTokens.Color.Cyan)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = VibeDesignTokens.Spacing.Medium),
                verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium),
                contentPadding = PaddingValues(
                    top = VibeDesignTokens.Spacing.Medium,
                    bottom = VibeDesignTokens.Spacing.Large
                )
            ) {
                item { VerificationRulesBanner() }
                item { VerificationUnavailableBanner() }
                item {
                    StatusFilterRow(
                        selectedStatus = uiState.selectedStatus,
                        onSelectStatus = { viewModel.filterByStatus(it) }
                    )
                }
                uiState.error?.let { message ->
                    item {
                        FixHistoryLoadError(
                            message = message,
                            onRetry = { viewModel.loadData(uiState.selectedProjectId) }
                        )
                    }
                }

                if (uiState.filteredVerifications.isEmpty()) {
                    if (uiState.error == null) item { EmptyVerificationsView() }
                } else {
                    items(uiState.filteredVerifications, key = { it.id }) { record ->
                        VerificationCard(record = record, onReVerify = null)
                    }
                }
            }
        }
    }
}

@Composable
fun VerificationRulesBanner() {
    EngCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Verified,
                    contentDescription = null,
                    tint = VibeDesignTokens.Color.Cyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.verification_rules),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.TextPrimary
                )
            }
            Text(
                stringResource(R.string.verification_rules_desc),
                style = MaterialTheme.typography.bodySmall,
                color = VibeDesignTokens.Color.TextMuted,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun StatusFilterRow(
    selectedStatus: VerificationStatus?,
    onSelectStatus: (VerificationStatus?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedStatus == null,
            onClick = { onSelectStatus(null) },
            label = { Text(stringResource(R.string.all_statuses)) }
        )
        VerificationStatus.values().forEach { status ->
            FilterChip(
                selected = selectedStatus == status,
                onClick = { onSelectStatus(if (selectedStatus == status) null else status) },
                label = { Text(getVerificationStatusLabel(status), fontSize = 12.sp) },
                leadingIcon = {
                    Surface(
                        color = getVerificationStatusColor(),
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.size(8.dp)
                    ) {}
                }
            )
        }
    }
}

@Composable
fun VerificationCard(
    record: CrashVerificationRecord,
    onReVerify: (() -> Unit)? = null
) {
    var expanded by remember(record.id) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val formattedDate = remember(record.verifiedAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.verifiedAt))
    }
    val validPrUrl = record.githubPrUrl?.takeIf(::isGitHubPullRequestUrl)
    val displayedSummary = stringResource(R.string.verification_unavailable_desc)

    EngCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VerificationStatusBadge()
                Text(
                    formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = VibeDesignTokens.Color.TextMuted,
                    fontFamily = JetBrainsMono
                )
            }

            Text(
                record.crashExceptionType.ifBlank { "Application Crash" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VibeDesignTokens.Color.TextPrimary
            )
            if (record.crashMessage.isNotBlank()) {
                Text(
                    record.crashMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextMuted,
                    maxLines = 2
                )
            }

            Surface(
                color = VibeDesignTokens.Color.SpacePanelRaised,
                shape = MaterialTheme.shapes.small,
                border = androidx.compose.foundation.BorderStroke(1.dp, VibeDesignTokens.Color.Grid),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VerificationMetadataColumn(
                        stringResource(R.string.baseline_version),
                        record.previousAppVersion.ifBlank { "—" },
                        Modifier.weight(1f)
                    )
                    VerificationMetadataColumn(
                        stringResource(R.string.target_version),
                        record.newAppVersion.ifBlank { "—" },
                        Modifier.weight(1f)
                    )
                    VerificationMetadataColumn(
                        stringResource(R.string.tested_build),
                        record.buildId.ifBlank { "—" },
                        Modifier.weight(1f)
                    )
                }
            }

            if (!record.fixCommitSha.isNullOrBlank() || validPrUrl != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    record.fixCommitSha?.takeIf { it.isNotBlank() }?.let { sha ->
                        AssistChip(
                            onClick = {},
                            label = { Text("Commit: $sha", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Commit, null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                    validPrUrl?.let { url ->
                        AssistChip(
                            onClick = { uriHandler.openUri(url) },
                            label = { Text("Pull Request", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }
            }

            Text(
                "الحالة المسجلة (غير موثقة): ${getVerificationStatusLabel(record.status)}",
                color = VibeDesignTokens.Color.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = VibeDesignTokens.Color.Cyan
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (expanded) "إخفاء المقارنة والتحليل" else "عرض المقارنة والتحليل الفارق",
                        color = VibeDesignTokens.Color.Cyan
                    )
                }

                FilledTonalButton(
                    onClick = { onReVerify?.invoke() },
                    enabled = onReVerify != null,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        if (onReVerify != null) Icons.Default.Refresh else Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (onReVerify != null) stringResource(R.string.re_verify)
                        else stringResource(R.string.verification_unavailable_action),
                        fontSize = 12.sp
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)
                ) {
                    HorizontalDivider(color = VibeDesignTokens.Color.Grid)

                    Text(
                        stringResource(R.string.comparison_summary),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.Cyan
                    )
                    Surface(
                        color = VibeDesignTokens.Color.SpacePanelRaised,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            displayedSummary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            color = VibeDesignTokens.Color.TextMuted,
                            lineHeight = 18.sp
                        )
                    }

                    if (record.testScopeDetails.isNotBlank()) {
                        Text(
                            stringResource(R.string.test_scope),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = VibeDesignTokens.Color.Cyan
                        )
                        Surface(
                            color = VibeDesignTokens.Color.SpacePanelRaised,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.verification_history_untrusted_details),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                color = VibeDesignTokens.Color.TextMuted
                            )
                        }
                    }

                    Text(
                        stringResource(R.string.old_stack_trace),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.Cyan
                    )
                    StackTraceCard(record.oldStackTrace)

                }
            }
        }
    }
}

@Composable
private fun VerificationMetadataColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = VibeDesignTokens.Color.TextMuted)
        Text(
            value,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
            color = VibeDesignTokens.Color.TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
fun VerificationStatusBadge() {
    val color = VibeDesignTokens.Color.TextMuted
    val label = stringResource(R.string.status_legacy_unverified)

    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.HelpOutline, contentDescription = label, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}



fun getVerificationStatusColor(): Color =
    VibeDesignTokens.Color.TextMuted



@Composable
fun getVerificationStatusLabel(status: VerificationStatus): String {
    return when (status) {
        VerificationStatus.FIX_VERIFIED_IN_TEST -> stringResource(R.string.status_fix_verified)
        VerificationStatus.CRASH_REPRODUCED -> stringResource(R.string.status_crash_reproduced)
        VerificationStatus.DIFFERENT_CRASH_DETECTED -> stringResource(R.string.status_different_crash)
        VerificationStatus.INCONCLUSIVE -> stringResource(R.string.status_inconclusive)
        VerificationStatus.TEST_FAILED -> stringResource(R.string.status_test_failed)
    }
}



@Composable
private fun FixHistoryLoadError(
    message: String,
    onRetry: () -> Unit
) {
    EngCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(VibeDesignTokens.Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = VibeDesignTokens.Color.Error
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "تعذر تحميل سجل التحقق",
                    color = VibeDesignTokens.Color.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                message,
                color = VibeDesignTokens.Color.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("إعادة المحاولة")
            }
        }
    }
}

@Composable
fun EmptyVerificationsView() {
    EngCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(VibeDesignTokens.Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)
        ) {
            Icon(
                Icons.Default.FactCheck,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = VibeDesignTokens.Color.Cyan
            )
            Text(
                stringResource(R.string.no_verifications_found),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VibeDesignTokens.Color.TextPrimary
            )
            Text(
                stringResource(R.string.no_verifications_desc),
                style = MaterialTheme.typography.bodySmall,
                color = VibeDesignTokens.Color.TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}



