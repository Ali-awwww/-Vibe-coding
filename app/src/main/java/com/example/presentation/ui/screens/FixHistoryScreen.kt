package com.example.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.R
import com.example.domain.model.CrashVerificationRecord
import com.example.domain.model.VerificationStatus
import com.example.presentation.viewmodel.FixHistoryViewModel
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
    var showVerifyDialog by remember { mutableStateOf(false) }
    var targetCrashIdForDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(projectId) {
        viewModel.loadData(projectId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fix_history)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Verification Rules Banner
                item {
                    VerificationRulesBanner()
                }

                // Filter chips
                item {
                    StatusFilterRow(
                        selectedStatus = uiState.selectedStatus,
                        onSelectStatus = { viewModel.filterByStatus(it) }
                    )
                }

                if (uiState.filteredVerifications.isEmpty()) {
                    item {
                        EmptyVerificationsView()
                    }
                } else {
                    items(uiState.filteredVerifications, key = { it.id }) { record ->
                        VerificationCard(
                            record = record,
                            onReVerify = {
                                targetCrashIdForDialog = record.crashId
                                showVerifyDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showVerifyDialog && targetCrashIdForDialog != null) {
        VerifyFixDialog(
            defaultVersion = "1.2.2-patch",
            defaultBuildId = "build-verify-${System.currentTimeMillis().toString().takeLast(4)}",
            onDismiss = { showVerifyDialog = false },
            onConfirm = { newVer, bId, scenario, outcome ->
                viewModel.reVerifyFix(
                    crashId = targetCrashIdForDialog!!,
                    fixSuggestionId = null,
                    newVersion = newVer,
                    buildId = bId,
                    scenarioName = scenario,
                    simulatedOutcome = outcome
                )
                showVerifyDialog = false
            }
        )
    }
}

@Composable
fun VerificationRulesBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.verification_rules),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.verification_rules_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
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
                        color = getVerificationStatusColor(status),
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
    onReVerify: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val formattedDate = remember(record.verifiedAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.verifiedAt))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Status badge & timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VerificationStatusBadge(record.status)
                Text(
                    formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            // Crash title & message
            Text(
                text = record.crashExceptionType.ifBlank { "Application Crash" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (record.crashMessage.isNotBlank()) {
                Text(
                    text = record.crashMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(Modifier.height(12.dp))

            // Build metadata chips
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(stringResource(R.string.baseline_version), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(record.previousAppVersion.ifBlank { "1.0.0" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                    Column {
                        Text(stringResource(R.string.target_version), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(record.newAppVersion.ifBlank { "Fixed Version" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                    }
                    Column {
                        Text(stringResource(R.string.tested_build), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(record.buildId, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Linked Git Info
            if (!record.fixCommitSha.isNullOrBlank() || !record.githubPrUrl.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    record.fixCommitSha?.let { sha ->
                        AssistChip(
                            onClick = {},
                            label = { Text("Commit: $sha", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Commit, null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                    record.githubPrUrl?.let { url ->
                        AssistChip(
                            onClick = { uriHandler.openUri(url) },
                            label = { Text("Pull Request", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Toggle Comparison Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (expanded) "إخفاء المقارنة والتحليل" else "عرض المقارنة والتحليل الفارق")
                }

                FilledTonalButton(
                    onClick = onReVerify,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.re_verify), fontSize = 12.sp)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider()

                    // Comparison summary
                    Text(
                        stringResource(R.string.comparison_summary),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = record.comparisonSummary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            lineHeight = 18.sp
                        )
                    }

                    // Test Scope Details
                    if (record.testScopeDetails.isNotBlank()) {
                        Text(
                            stringResource(R.string.test_scope),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = record.testScopeDetails,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Stack Traces Comparison
                    Text(
                        stringResource(R.string.old_stack_trace),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    StackTraceCard(record.oldStackTrace)

                    if (!record.newStackTrace.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.new_stack_trace),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        StackTraceCard(record.newStackTrace)
                    }
                }
            }
        }
    }
}

@Composable
fun VerificationStatusBadge(status: VerificationStatus) {
    val color = getVerificationStatusColor(status)
    val label = getVerificationStatusLabel(status)
    val icon = when (status) {
        VerificationStatus.FIX_VERIFIED_IN_TEST -> Icons.Default.CheckCircle
        VerificationStatus.CRASH_REPRODUCED -> Icons.Default.Cancel
        VerificationStatus.DIFFERENT_CRASH_DETECTED -> Icons.Default.Warning
        VerificationStatus.INCONCLUSIVE -> Icons.Default.HelpOutline
        VerificationStatus.TEST_FAILED -> Icons.Default.Block
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

fun getVerificationStatusColor(status: VerificationStatus): Color {
    return when (status) {
        VerificationStatus.FIX_VERIFIED_IN_TEST -> Color(0xFF2E7D32)
        VerificationStatus.CRASH_REPRODUCED -> Color(0xFFC62828)
        VerificationStatus.DIFFERENT_CRASH_DETECTED -> Color(0xFFEF6C00)
        VerificationStatus.INCONCLUSIVE -> Color(0xFF616161)
        VerificationStatus.TEST_FAILED -> Color(0xFF6A1B9A)
    }
}

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
fun EmptyVerificationsView() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.FactCheck,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.no_verifications_found),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.no_verifications_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyFixDialog(
    defaultVersion: String,
    defaultBuildId: String,
    onDismiss: () -> Unit,
    onConfirm: (version: String, buildId: String, scenario: String, outcome: VerificationStatus?) -> Unit
) {
    var versionText by remember { mutableStateOf(defaultVersion) }
    var buildIdText by remember { mutableStateOf(defaultBuildId) }
    var scenarioText by remember { mutableStateOf("Profile Avatar & Data Binding Automated Reproduction") }
    var selectedOutcome by remember { mutableStateOf<VerificationStatus?>(VerificationStatus.FIX_VERIFIED_IN_TEST) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.verify_fix))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "سيتم تشغيل حزمة اختبار معزولة وإعادة تنفيذ سيناريو الخطأ للتحقق من زوال العطل.",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = versionText,
                    onValueChange = { versionText = it },
                    label = { Text(stringResource(R.string.target_version)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = buildIdText,
                    onValueChange = { buildIdText = it },
                    label = { Text(stringResource(R.string.tested_build)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = scenarioText,
                    onValueChange = { scenarioText = it },
                    label = { Text(stringResource(R.string.select_scenario)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Optional simulation selector for rapid testing
                Text(
                    "محاكاة نتيجة التحقق (Simulation / Sandbox Mode):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedOutcome == VerificationStatus.FIX_VERIFIED_IN_TEST,
                        onClick = { selectedOutcome = VerificationStatus.FIX_VERIFIED_IN_TEST },
                        label = { Text("Verified", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = selectedOutcome == VerificationStatus.CRASH_REPRODUCED,
                        onClick = { selectedOutcome = VerificationStatus.CRASH_REPRODUCED },
                        label = { Text("Reproduced", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = selectedOutcome == VerificationStatus.DIFFERENT_CRASH_DETECTED,
                        onClick = { selectedOutcome = VerificationStatus.DIFFERENT_CRASH_DETECTED },
                        label = { Text("Regression", fontSize = 10.sp) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(versionText, buildIdText, scenarioText, selectedOutcome)
                }
            ) {
                Text(stringResource(R.string.run_verification))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
