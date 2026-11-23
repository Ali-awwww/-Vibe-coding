package com.example.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.R
import com.example.domain.model.*
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.DevDebugCenterViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevDebugCenterScreen(
    workspaceId: String? = null,
    navController: NavController,
    viewModel: DevDebugCenterViewModel
) {
    val state by viewModel.state.collectAsState()
    val workspaces by viewModel.workspaces.collectAsState()

    var showSdkDialog by remember { mutableStateOf(false) }
    var showSimulateCrashDialog by remember { mutableStateOf(false) }

    LaunchedEffect(workspaceId) {
        if (!workspaceId.isNullOrBlank()) {
            viewModel.initForWorkspace(workspaceId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.dev_debug_center),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = state.selectedWorkspace?.name ?: "Crash Intelligence Loop",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Screen.VibeStudio)
                            }
                        },
                        modifier = Modifier.testTag("dev_debug_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.testTag("dev_debug_refresh_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Workspace selector tabs
            if (workspaces.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = workspaces.indexOfFirst { it.id == state.selectedWorkspace?.id }.coerceAtLeast(0),
                    edgePadding = 16.dp,
                    divider = {},
                    modifier = Modifier.fillMaxWidth().testTag("workspace_selector_tabs")
                ) {
                    workspaces.forEach { ws ->
                        Tab(
                            selected = ws.id == state.selectedWorkspace?.id,
                            onClick = { viewModel.selectWorkspace(ws.id) },
                            text = {
                                Text(
                                    text = ws.name,
                                    maxLines = 1,
                                    fontWeight = if (ws.id == state.selectedWorkspace?.id) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            // Feedback and Error Banners
            state.actionFeedbackMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearFeedbackMessage() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            state.error?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearFeedbackMessage() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (state.isLoading && state.selectedWorkspace == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.testTag("dev_debug_loading"))
                }
            } else if (workspaces.isEmpty()) {
                EmptyWorkspaceState(onGoToVibeStudio = { navController.navigate(Screen.VibeStudio) })
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    // 1. Monitoring & Live Session Context Card
                    item {
                        MonitoringAndSessionCard(
                            state = state,
                            onViewSdkSnippet = { showSdkDialog = true },
                            onReportCrash = { showSimulateCrashDialog = true }
                        )
                    }

                    // 2. Closed-Loop Incidents & Resolution Card
                    item {
                        IncidentsResolutionCard(
                            state = state,
                            onSelectIncident = { viewModel.selectIncident(it) },
                            onDiagnose = { viewModel.diagnoseCrash(it.crashReport.id) },
                            onApplyFix = { it.fixSuggestion?.let { fix -> viewModel.applyFix(fix.id) } },
                            onRetestAndVerify = { incident ->
                                incident.fixSuggestion?.let { fix ->
                                    viewModel.retestAndVerify(incident.crashReport.id, fix.id)
                                }
                            }
                        )
                    }

                    // 3. Closed-Loop Verification Timeline Card
                    if (state.verifications.isNotEmpty()) {
                        item {
                            VerificationHistoryCard(verifications = state.verifications)
                        }
                    }

                    // 4. Recent Build & Execution Telemetry
                    if (state.recentBuildLogs.isNotEmpty()) {
                        item {
                            TelemetryLogsCard(logs = state.recentBuildLogs)
                        }
                    }
                }
            }
        }
    }

    // Dialog: SDK Integration Snippet
    if (showSdkDialog && state.monitoringProfile != null) {
        val profile = state.monitoringProfile!!
        AlertDialog(
            onDismissRequest = { showSdkDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تهيئة Crash Intelligence SDK")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "التقنية المستهدفة: ${profile.targetPlatform}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = profile.sdkSnippet,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSdkDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // Dialog: Ingest / Simulate Real Crash
    if (showSimulateCrashDialog) {
        SimulateCrashDialog(
            onDismiss = { showSimulateCrashDialog = false },
            onConfirm = { excType, msg, stack ->
                viewModel.simulateCrash(excType, msg, stack)
                showSimulateCrashDialog = false
            }
        )
    }
}

@Composable
fun MonitoringAndSessionCard(
    state: DevDebugCenterState,
    onViewSdkSnippet: () -> Unit,
    onReportCrash: () -> Unit
) {
    val ws = state.selectedWorkspace
    val profile = state.monitoringProfile
    val runContext = state.activeRunContext

    Card(
        modifier = Modifier.fillMaxWidth().testTag("monitoring_session_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.monitoring_profile),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = ws?.techStack ?: "Unknown",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // IDs and Setup Summary
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "معرف مساحة العمل (Workspace ID)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = ws?.id?.take(12) ?: "---", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "حالة ربط SDK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (profile?.isSdkConfigured == true) "مربوط ومتحقق منه" else "المقتطف جاهز؛ SDK غير مثبت",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (profile?.isSdkConfigured == true) Color(0xFF2E7D32) else MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Active Test Run Status
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "جلسة اختبار فعلية (Android Agent)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        runContext?.let {
                            StatusChip(status = it.status.name)
                        } ?: Text(text = "غير متاحة: لا يوجد Agent متصل", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (runContext != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Build: ${runContext.buildId} | Commit: ${runContext.commitSha.take(7)} | Events: ${runContext.eventCount}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewSdkSnippet,
                    modifier = Modifier.weight(1f).testTag("btn_view_sdk_snippet")
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "كود SDK", fontSize = 12.sp)
                }

                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1.2f).testTag("btn_start_test_session")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "غير متاح حالياً", fontSize = 11.sp)
                }

                FilledTonalButton(
                    onClick = onReportCrash,
                    modifier = Modifier.weight(1.2f).testTag("btn_simulate_crash")
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "محاكاة عطل محلي", fontSize = 11.sp)
                }
            }
            Text(
                text = "لن تُنشأ جلسة اختبار وهمية. يلزم Android Agent فعلي ومشغّل عمليات معزول لبدء الاختبار.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
fun IncidentsResolutionCard(
    state: DevDebugCenterState,
    onSelectIncident: (ClosedLoopIncident) -> Unit,
    onDiagnose: (ClosedLoopIncident) -> Unit,
    onApplyFix: (ClosedLoopIncident) -> Unit,
    onRetestAndVerify: (ClosedLoopIncident) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("incidents_resolution_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Healing, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.incidents_found),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = "${state.incidents.size} عطل",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (state.incidents.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.no_incidents_found),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "استقبل تقارير telemetry من SDK مثبت فعلياً أو أنشئ سجل محاكاة محلياً للتحليل الأولي.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Incidents selector row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(state.incidents) { inc ->
                        val isSelected = inc.crashReport.id == state.activeIncident?.crashReport?.id
                        IncidentPill(
                            incident = inc,
                            isSelected = isSelected,
                            onClick = { onSelectIncident(inc) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                state.activeIncident?.let { active ->
                    ActiveIncidentDetails(
                        incident = active,
                        isFixing = state.isFixing,
                        isVerifying = state.isVerifying,
                        onDiagnose = { onDiagnose(active) },
                        onApplyFix = { onApplyFix(active) },
                        onRetestAndVerify = { onRetestAndVerify(active) }
                    )
                }
            }
        }
    }
}

@Composable
fun IncidentPill(
    incident: ClosedLoopIncident,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when (incident.status) {
                            IncidentStatus.VERIFIED -> Color(0xFF2E7D32)
                            IncidentStatus.FIX_APPLIED -> Color(0xFF1976D2)
                            IncidentStatus.FIX_PROPOSED -> Color(0xFFF57C00)
                            else -> Color(0xFFD32F2F)
                        }
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = incident.crashReport.exceptionType,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = incident.status.labelAr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ActiveIncidentDetails(
    incident: ClosedLoopIncident,
    isFixing: Boolean,
    isVerifying: Boolean,
    onDiagnose: () -> Unit,
    onApplyFix: () -> Unit,
    onRetestAndVerify: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        // Status & Metadata
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = incident.crashReport.message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = when (incident.status) {
                    IncidentStatus.VERIFIED -> Color(0xFFE8F5E9)
                    IncidentStatus.FIX_APPLIED -> Color(0xFFE3F2FD)
                    IncidentStatus.FIX_PROPOSED -> Color(0xFFFFF3E0)
                    else -> Color(0xFFFFEBEE)
                },
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = incident.status.labelAr,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = when (incident.status) {
                        IncidentStatus.VERIFIED -> Color(0xFF2E7D32)
                        IncidentStatus.FIX_APPLIED -> Color(0xFF1976D2)
                        IncidentStatus.FIX_PROPOSED -> Color(0xFFF57C00)
                        else -> Color(0xFFD32F2F)
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        incident.faultyFilePath?.let { path ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$path : السطر ${incident.faultyLineNumber ?: "?"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Stack Trace (Strictly LTR Monospace)
        Text(text = "سجل التتبع (Stack Trace):", style = MaterialTheme.typography.labelSmall)
        Surface(
            color = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 100.dp)
        ) {
            Text(
                text = incident.crashReport.stackTrace.take(400) + if (incident.crashReport.stackTrace.length > 400) "..." else "",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFFE0E0E0),
                modifier = Modifier.padding(8.dp),
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
            )
        }

        // Diagnosis Section
        incident.diagnosis?.let { diag ->
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تحليل محلي أولي للسبب المحتمل",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = diag.rootCause, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Fix Suggestion & Diff Section
        incident.proposedDiff?.let { diff ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "التعديل البرمجي المقترح (Proposed Diff):", style = MaterialTheme.typography.labelSmall)
            Surface(
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    diff.diffLines.take(12).forEach { line ->
                        val lineBg = when (line.type) {
                            DiffLineType.ADD -> Color(0xFF1B5E20)
                            DiffLineType.REMOVE -> Color(0xFFB71C1C)
                            DiffLineType.CONTEXT -> Color.Transparent
                        }
                        val prefix = when (line.type) {
                            DiffLineType.ADD -> "+ "
                            DiffLineType.REMOVE -> "- "
                            DiffLineType.CONTEXT -> "  "
                        }
                        Text(
                            text = prefix + line.content,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(lineBg)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
                        )
                    }
                }
            }
        }

        // Verification outcome if available
        incident.verificationRecord?.let { vr ->
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = verificationStatusBackground(vr.status),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (vr.status) {
                            VerificationStatus.FIX_VERIFIED_IN_TEST -> Icons.Default.CheckCircle
                            VerificationStatus.TEST_FAILED, VerificationStatus.CRASH_REPRODUCED -> Icons.Default.Cancel
                            VerificationStatus.DIFFERENT_CRASH_DETECTED, VerificationStatus.INCONCLUSIVE -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = verificationStatusForeground(vr.status),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = vr.comparisonSummary,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = verificationStatusForeground(vr.status)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dynamic Action Button based on incident status
        when (incident.status) {
            IncidentStatus.DETECTED -> {
                Button(
                    onClick = onDiagnose,
                    modifier = Modifier.fillMaxWidth().testTag("btn_diagnose_incident")
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تحليل محلي أولي وتوليد مقترح إصلاح")
                }
            }
            IncidentStatus.DIAGNOSED, IncidentStatus.FIX_PROPOSED -> {
                Button(
                    onClick = onApplyFix,
                    enabled = !isFixing,
                    modifier = Modifier.fillMaxWidth().testTag("btn_apply_fix")
                ) {
                    if (isFixing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.apply_fix_action))
                    }
                }
            }
            IncidentStatus.FIX_APPLIED -> {
                Button(
                    onClick = onRetestAndVerify,
                    enabled = !isVerifying,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    modifier = Modifier.fillMaxWidth().testTag("btn_retest_verify")
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.retest_verify_action))
                    }
                }
            }
            IncidentStatus.VERIFIED -> {
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF2E7D32))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تم التحقق من الإصلاح بنجاح في البناء المغلق ✓",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
            IncidentStatus.REPRODUCED -> {
                Button(
                    onClick = onDiagnose,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().testTag("btn_re_diagnose")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إعادة التحليل والمحاولة بإصلاح بديل")
                }
            }
        }
    }
}

private fun verificationStatusBackground(status: VerificationStatus): Color = when (status) {
    VerificationStatus.FIX_VERIFIED_IN_TEST -> Color(0xFFE8F5E9)
    VerificationStatus.TEST_FAILED, VerificationStatus.CRASH_REPRODUCED -> Color(0xFFFFEBEE)
    VerificationStatus.DIFFERENT_CRASH_DETECTED, VerificationStatus.INCONCLUSIVE -> Color(0xFFFFF8E1)
}

private fun verificationStatusForeground(status: VerificationStatus): Color = when (status) {
    VerificationStatus.FIX_VERIFIED_IN_TEST -> Color(0xFF2E7D32)
    VerificationStatus.TEST_FAILED, VerificationStatus.CRASH_REPRODUCED -> Color(0xFFD32F2F)
    VerificationStatus.DIFFERENT_CRASH_DETECTED, VerificationStatus.INCONCLUSIVE -> Color(0xFF8D6E00)
}

private fun verificationStatusLabel(status: VerificationStatus): String = when (status) {
    VerificationStatus.FIX_VERIFIED_IN_TEST -> "ناجح ومؤكد"
    VerificationStatus.TEST_FAILED -> "فشلت الاختبارات الفعلية"
    VerificationStatus.CRASH_REPRODUCED -> "أُعيد إنتاج العطل"
    VerificationStatus.DIFFERENT_CRASH_DETECTED -> "عطل مختلف"
    VerificationStatus.INCONCLUSIVE -> "غير حاسم؛ لا توجد أدلة تنفيذ"
}

@Composable
fun VerificationHistoryCard(verifications: List<CrashVerificationRecord>) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("verification_history_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FactCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "سجل التحقق والمقارنة (Closed-Loop Verifications)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            verifications.forEach { v ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (v.buildId.startsWith("unverified-")) "Attempt ${v.buildId.take(14)}" else "Build ${v.buildId.take(14)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            )
                            Surface(
                                color = verificationStatusBackground(v.status),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = verificationStatusLabel(v.status),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = verificationStatusForeground(v.status),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = v.comparisonSummary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryLogsCard(logs: List<BuildLog>) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("telemetry_logs_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "سجل العمليات والـ Telemetry (${logs.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        items(logs) { log ->
                            val color = when (log.type) {
                                LogType.ERROR -> Color(0xFFEF5350)
                                LogType.SUCCESS -> Color(0xFF66BB6A)
                                LogType.WARNING -> Color(0xFFFFA726)
                                else -> Color(0xFFB0BEC5)
                            }
                            Text(
                                text = log.message,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = color,
                                style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimulateCrashDialog(
    onDismiss: () -> Unit,
    onConfirm: (exceptionType: String, message: String, stackTrace: String) -> Unit
) {
    var exceptionType by remember { mutableStateOf("java.lang.NullPointerException") }
    var message by remember { mutableStateOf("Attempt to invoke virtual method on a null object reference") }
    var stackTrace by remember {
        mutableStateOf(
            "java.lang.NullPointerException: Attempt to invoke virtual method on a null object reference\n" +
            "    at com.example.service.UserService.getUserProfile(UserService.kt:42)\n" +
            "    at com.example.ui.MainActivity.onCreate(MainActivity.kt:28)"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text("محاكاة عطل محلي")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "هذا سجل تجريبي محلي فقط؛ لا يمثل عطلاً من جهاز حقيقي ولا يرسل بيانات إلى خدمة خارجية.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                OutlinedTextField(
                    value = exceptionType,
                    onValueChange = { exceptionType = it },
                    label = { Text("نوع الاستثناء (Exception Type)") },
                    modifier = Modifier.fillMaxWidth().testTag("input_exception_type")
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("رسالة الخطأ (Message)") },
                    modifier = Modifier.fillMaxWidth().testTag("input_exception_message")
                )

                OutlinedTextField(
                    value = stackTrace,
                    onValueChange = { stackTrace = it },
                    label = { Text("Stack Trace") },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
                    modifier = Modifier.fillMaxWidth().height(120.dp).testTag("input_exception_stack")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(exceptionType, message, stackTrace) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("btn_confirm_simulate_crash")
            ) {
                Text("إنشاء سجل محاكاة محلي")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun StatusChip(status: String) {
    val (bg, fg) = when (status) {
        "APP_RUNNING" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        "CRASH_DETECTED" -> Pair(Color(0xFFFFEBEE), Color(0xFFD32F2F))
        "COMPLETED", "VERIFIED" -> Pair(Color(0xFFE3F2FD), Color(0xFF1976D2))
        else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(color = bg, shape = RoundedCornerShape(12.dp)) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun EmptyWorkspaceState(onGoToVibeStudio: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "لا توجد مساحات عمل (Workspaces) بعد",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "أنشئ مشروعاً جديداً في AI Vibe Studio لبدء دورة التطوير وتتبع الأعطال الذكية الموحدة.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGoToVibeStudio) {
                Text("الانتقال إلى AI Vibe Studio")
            }
        }
    }
}
