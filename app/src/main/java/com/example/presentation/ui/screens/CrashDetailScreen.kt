package com.example.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.navigation.NavController
import com.example.domain.model.*
import com.example.presentation.viewmodel.CrashDetailViewModel
import com.example.presentation.navigation.Screen
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.ui.theme.JetBrainsMono
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf

import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.theme.VibeDesignTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashDetailScreen(
    crashId: String,
    navController: NavController,
    viewModel: CrashDetailViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(crashId) {
        viewModel.loadCrash(crashId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crash_details)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.crashes_back)
                        )
                    }
                },
                actions = {
                    if (uiState.crash != null) {
                        IconButton(onClick = { navController.navigate(Screen.FixHistory(uiState.crash?.projectId)) }) {
                            Icon(Icons.Default.History, contentDescription = stringResource(R.string.fix_history))
                        }
                    }
                    if (uiState.crash?.isResolved == false) {
                        IconButton(
                            onClick = { viewModel.resolveCrash() },
                            enabled = !uiState.isResolving
                        ) {
                            if (uiState.isResolving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(R.string.crash_detail_resolve)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.crash == null -> {
                CrashDetailUnavailableState(
                    isLoadError = uiState.loadError,
                    onRetry = { viewModel.loadCrash(crashId) },
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            else -> {
                val crash = uiState.crash!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CrashHeader(crash)

                    SymbolicationStatusBanner(
                        report = uiState.symbolication,
                        isLoading = uiState.isSymbolicating,
                        errorMessage = uiState.symbolicationError,
                        onRetry = { viewModel.symbolicateCrash() }
                    )

                    Text(
                        stringResource(R.string.stack_trace),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    CrashTraceContent(
                        report = uiState.symbolication,
                        rawStackTrace = crash.stackTrace
                    )

                    Spacer(Modifier.height(8.dp))

                    CrashInvestigationTeamSection(
                        isInvestigating = uiState.isInvestigatingWithTeam,
                        teamExecution = uiState.teamExecution,
                        onStartInvestigation = { viewModel.investigateWithCrashTeam() },
                        onViewReport = { viewModel.setShowTeamDialog(true) }
                    )

                    Spacer(Modifier.height(8.dp))

                    if (uiState.diagnosis == null) {
                        Button(
                            onClick = { viewModel.analyzeCrash() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isAnalyzing && !uiState.isInvestigatingWithTeam
                        ) {
                            if (uiState.isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.analyzing))
                            } else {
                                Icon(Icons.Default.AutoAwesome, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.ai_diagnosis))
                            }
                        }
                    } else {
                        DiagnosisCard(uiState.diagnosis!!, navController, crash)
                    }

                    if (uiState.showTeamDialog) {
                        CrashInvestigationDialog(
                            execution = uiState.teamExecution,
                            crashReport = crash,
                            isInvestigating = uiState.isInvestigatingWithTeam,
                            onDismiss = { viewModel.setShowTeamDialog(false) },
                            onNavigateToFix = {
                                viewModel.setShowTeamDialog(false)
                                navController.navigate(Screen.FixAssistant(crash.id))
                            }
                        )
                    }

                    uiState.error?.let { message ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CrashDetailUnavailableState(
    isLoadError: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("crash_detail_unavailable_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isLoadError) Icons.Default.CloudOff else Icons.Default.Info,
            contentDescription = null,
            tint = if (isLoadError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(
                if (isLoadError) R.string.crash_detail_load_error_title
                else R.string.crash_detail_not_found_title
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (isLoadError) R.string.crash_detail_load_error_desc
                else R.string.crash_detail_not_found_desc
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).testTag("crash_detail_back_button")
            ) {
                Text(stringResource(R.string.crashes_back))
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f).testTag("crash_detail_retry_button")
            ) {
                Text(stringResource(R.string.crashes_retry))
            }
        }
    }
}

@Composable
fun CrashHeader(crash: CrashReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityBadge(crash.severity)
                Spacer(Modifier.width(8.dp))
                if (crash.isResolved) {
                    Surface(
                        modifier = Modifier.testTag("crash_detail_resolved_status"),
                        color = VibeDesignTokens.Color.Success.copy(alpha = 0.14f),
                        contentColor = VibeDesignTokens.Color.Success,
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                stringResource(R.string.crashes_resolved),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                crash.exceptionType.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.crashes_exception_unavailable),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VibeDesignTokens.Color.TextPrimary
            )
            Text(
                crash.message.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.crashes_message_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = VibeDesignTokens.Color.TextMuted
            )

            HorizontalDivider(
                color = VibeDesignTokens.Color.Grid,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth < 420.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("crash_header_metadata_compact"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoItem(
                                label = stringResource(R.string.crash_detail_device),
                                value = crash.deviceModel,
                                modifier = Modifier.weight(1f)
                            )
                            InfoItem(
                                label = stringResource(R.string.crash_detail_os),
                                value = crash.osVersion,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        InfoItem(
                            label = stringResource(R.string.crash_detail_version),
                            value = crash.appVersion,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("crash_header_metadata_wide"),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        InfoItem(
                            label = stringResource(R.string.crash_detail_device),
                            value = crash.deviceModel,
                            modifier = Modifier.weight(1f)
                        )
                        InfoItem(
                            label = stringResource(R.string.crash_detail_os),
                            value = crash.osVersion,
                            modifier = Modifier.weight(1f)
                        )
                        InfoItem(
                            label = stringResource(R.string.crash_detail_version),
                            value = crash.appVersion,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun InfoItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.takeIf { it.isNotBlank() } ?: stringResource(R.string.crashes_unavailable), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SeverityBadge(severity: Severity) {
    val color = when (severity) {
        Severity.LOW -> Color(0xFF4CAF50)
        Severity.MEDIUM -> Color(0xFFFFC107)
        Severity.HIGH -> Color(0xFFFF9800)
        Severity.CRITICAL -> Color(0xFFF44336)
    }
    Surface(
        color = color.copy(alpha = 0.2f),
        contentColor = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = stringResource(
                when (severity) {
                    Severity.CRITICAL -> R.string.crashes_severity_critical
                    Severity.HIGH -> R.string.crashes_severity_high
                    Severity.MEDIUM -> R.string.crashes_severity_medium
                    Severity.LOW -> R.string.crashes_severity_low
                }
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StackTraceCard(stackTrace: String) {
    if (stackTrace.isBlank()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("crash_detail_stack_trace_unavailable"),
            shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
            colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpaceBackground),
            border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.18f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(R.string.crash_detail_stack_trace_header),
                    style = MaterialTheme.typography.labelSmall,
                    color = VibeDesignTokens.Color.TextMuted,
                    fontFamily = JetBrainsMono
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.crash_detail_stack_trace_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextMuted
                )
            }
        }
        return
    }

    val clipboardManager = LocalClipboardManager.current
    var copied by remember(stackTrace) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpaceBackground),
        border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(VibeDesignTokens.Color.Error, CircleShape)
                    )
                    Text(
                        text = stringResource(R.string.crash_detail_stack_trace_header),
                        style = MaterialTheme.typography.labelSmall,
                        color = VibeDesignTokens.Color.TextMuted,
                        fontFamily = JetBrainsMono
                    )
                }
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(stackTrace))
                        copied = true
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("crash_detail_copy_stack_trace_button")
                ) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = stringResource(
                            if (copied) R.string.crash_detail_stack_trace_copied
                            else R.string.crash_detail_copy_stack_trace
                        ),
                        tint = if (copied) VibeDesignTokens.Color.Success else VibeDesignTokens.Color.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            HorizontalDivider(
                color = VibeDesignTokens.Color.Grid,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stackTrace,
                        color = VibeDesignTokens.Color.TextPrimary,
                        fontFamily = JetBrainsMono,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
@Composable
fun DiagnosisCard(diagnosis: AIDiagnosis, navController: NavController, crash: CrashReport?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.smart_diagnostic_report), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    CategoryBadge(diagnosis.category)
                    CertaintyBadge(diagnosis.certainty)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            DiagnosisSection(title = stringResource(R.string.summary), content = diagnosis.summary, icon = Icons.Default.Info)
            
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BugReport, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.root_cause_analysis), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    Text(diagnosis.rootCause, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }

            DiagnosisSection(title = stringResource(R.string.context), content = diagnosis.executionContext, icon = Icons.Default.Computer)
            
            diagnosis.githubUrl?.let { url ->
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                TextButton(
                    onClick = { uriHandler.openUri(url) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.padding(start = 24.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.github_view_code), style = MaterialTheme.typography.labelSmall)
                }
            }

            diagnosis.diffAnalysis?.let { diff ->
                if (diff.isNotBlank()) {
                    DiagnosisSection(title = stringResource(R.string.diff_analysis), content = diff, icon = Icons.Default.CompareArrows)
                }
            }

            DiagnosisSection(title = stringResource(R.string.technical_evidence), content = diagnosis.technicalEvidence, icon = Icons.Default.FactCheck)
            DiagnosisSection(title = stringResource(R.string.why_it_happened), content = diagnosis.whyItHappened, icon = Icons.Default.HelpCenter)
            DiagnosisSection(title = stringResource(R.string.solution), content = diagnosis.solution, icon = Icons.Default.Build)
            
            diagnosis.suggestedCode?.let { code ->
                if (code.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.suggested_code), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    StackTraceCard(code)
                }
            }

            diagnosis.missingInformation?.let { info ->
                if (info.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.missing_information), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                            Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            
                            Spacer(Modifier.height(8.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (info.contains("symbol", ignoreCase = true) || info.contains("mapping", ignoreCase = true)) {
                                    Button(
                                        onClick = { crash?.projectId?.let { navController.navigate(Screen.DebugSymbols(it)) } },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text(stringResource(R.string.action_upload_symbols), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                if (info.contains("github", ignoreCase = true) || info.contains("code", ignoreCase = true)) {
                                    OutlinedButton(
                                        onClick = { crash?.projectId?.let { navController.navigate(Screen.GitHubConnect(it)) } },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(stringResource(R.string.action_link_github), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { crash?.id?.let { navController.navigate(Screen.FixAssistant(it)) } },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.suggest_fix))
                }

                OutlinedButton(
                    onClick = { crash?.id?.let { navController.navigate(Screen.FixAssistant(it)) } },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Verified, null, tint = Color(0xFF2E7D32))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.verify_fix))
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.confidence_level), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { diagnosis.confidence },
                    modifier = Modifier.weight(1f).height(8.dp),
                    color = if (diagnosis.confidence > 0.7f) Color(0xFF4CAF50) else Color(0xFFFFC107),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Spacer(Modifier.width(8.dp))
                Text("${(diagnosis.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CertaintyBadge(certainty: DiagnosisCertainty) {
    val color = when (certainty) {
        DiagnosisCertainty.CONFIRMED -> Color(0xFF2E7D32)
        DiagnosisCertainty.HIGHLY_LIKELY -> Color(0xFF1976D2)
        DiagnosisCertainty.POSSIBLE -> Color(0xFFFF9800)
        DiagnosisCertainty.UNKNOWN -> Color.Gray
    }
    val labelRes = when (certainty) {
        DiagnosisCertainty.CONFIRMED -> R.string.diagnosis_certainty_confirmed
        DiagnosisCertainty.HIGHLY_LIKELY -> R.string.diagnosis_certainty_highly_likely
        DiagnosisCertainty.POSSIBLE -> R.string.diagnosis_certainty_possible
        DiagnosisCertainty.UNKNOWN -> R.string.diagnosis_certainty_unknown
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
@Composable
fun DiagnosisSection(title: String, content: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }
        Text(content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 24.dp))
    }
}

@Composable
fun SymbolicationStatusBanner(
    report: SymbolicationReport?,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit
) {
    val status = report?.status
    if (report == null && !isLoading && errorMessage == null) return

    val color = when {
        isLoading -> MaterialTheme.colorScheme.primary
        status == SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED -> Color(0xFF2E7D32)
        status == SymbolicationStatus.PARTIALLY_SYMBOLICATED -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.error
    }
    val title = when {
        isLoading -> R.string.crash_detail_symbolication_loading
        status == null -> R.string.crash_detail_symbolication_error
        status == SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED -> R.string.crash_detail_symbolication_success
        status == SymbolicationStatus.PARTIALLY_SYMBOLICATED -> R.string.crash_detail_symbolication_partial
        status == SymbolicationStatus.MISSING_DEBUG_SYMBOLS -> R.string.crash_detail_symbolication_missing
        status == SymbolicationStatus.BUILD_MISMATCH -> R.string.crash_detail_symbolication_mismatch
        status == SymbolicationStatus.SOURCE_REVISION_MISSING -> R.string.crash_detail_symbolication_source_missing
        status == SymbolicationStatus.PROCESSING_FAILED -> R.string.crash_detail_symbolication_failed
        status == SymbolicationStatus.PENDING -> R.string.crash_detail_symbolication_pending
        else -> R.string.crash_detail_symbolication_error
    }
    val icon = when {
        isLoading -> Icons.Default.Sync
        status == SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED -> Icons.Default.Verified
        status == SymbolicationStatus.PARTIALLY_SYMBOLICATED -> Icons.Default.Warning
        else -> Icons.Default.ErrorOutline
    }
    val message = errorMessage?.takeIf { it.isNotBlank() }?.let { detail ->
        "${stringResource(R.string.crash_detail_symbolication_error_desc)}\n$detail"
    } ?: when (status) {
        SymbolicationStatus.PARTIALLY_SYMBOLICATED -> stringResource(R.string.crash_detail_symbolication_partial_desc)
        SymbolicationStatus.MISSING_DEBUG_SYMBOLS -> stringResource(R.string.crash_detail_symbolication_missing_desc)
        SymbolicationStatus.BUILD_MISMATCH -> stringResource(R.string.crash_detail_symbolication_mismatch_desc)
        SymbolicationStatus.SOURCE_REVISION_MISSING -> stringResource(R.string.crash_detail_symbolication_source_missing_desc)
        SymbolicationStatus.PROCESSING_FAILED -> stringResource(R.string.crash_detail_symbolication_failed_desc)
        null -> stringResource(R.string.crash_detail_symbolication_error_desc)
        else -> report?.error
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = color
                )
            } else {
                Icon(imageVector = icon, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                message?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.8f))
                }
            }
            if (!isLoading && status != SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("crash_symbolication_retry")
                ) {
                    Text(stringResource(R.string.crashes_retry), color = color)
                }
            }
        }
    }
}

@Composable
fun CrashTraceContent(report: SymbolicationReport?, rawStackTrace: String) {
    val displayableReport = report?.takeIf {
        it.status == SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED ||
            it.status == SymbolicationStatus.PARTIALLY_SYMBOLICATED
    }

    if (displayableReport != null) {
        SymbolicatedStackTrace(displayableReport)
    } else {
        StackTraceCard(rawStackTrace)
    }
}

@Composable
fun SymbolicatedStackTrace(report: SymbolicationReport) {
    val clipboardManager = LocalClipboardManager.current
    val copyText = remember(report.frames) {
        report.frames.joinToString("\n") { it.toClipboardLine() }
    }
    var copied by remember(report.frames) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpaceBackground),
        border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.18f)),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Control)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.crash_detail_symbolicated_trace_header),
                    style = MaterialTheme.typography.labelSmall,
                    color = VibeDesignTokens.Color.TextMuted,
                    fontFamily = JetBrainsMono,
                    modifier = Modifier.weight(1f)
                )
                if (report.frames.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(copyText))
                            copied = true
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("crash_detail_copy_symbolicated_stack_trace_button")
                    ) {
                        Icon(
                            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = stringResource(
                                if (copied) R.string.crash_detail_stack_trace_copied
                                else R.string.crash_detail_copy_stack_trace
                            ),
                            tint = if (copied) VibeDesignTokens.Color.Success else VibeDesignTokens.Color.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            HorizontalDivider(
                color = VibeDesignTokens.Color.Grid,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            if (report.frames.isEmpty()) {
                Text(
                    text = stringResource(R.string.crash_detail_symbolication_no_frames),
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextMuted,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        report.frames.forEachIndexed { index, frame ->
                            SymbolicatedFrameItem(frame)
                            if (index < report.frames.lastIndex) {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun SymbolicatedFrame.toClipboardLine(): String {
    val methodName = methodName?.takeIf { it.isNotBlank() }
    val fileName = fileName?.takeIf { it.isNotBlank() }
    val location = fileName?.let { file ->
        lineNumber?.takeIf { it > 0 }?.let { "$file:$it" } ?: file
    }
    val libraryName = libraryName?.takeIf { it.isNotBlank() && it != "system" }
    val primaryEvidence = listOfNotNull(methodName, location?.let { "($it)" })
        .ifEmpty { listOf(originalLine) }
    return (primaryEvidence + listOfNotNull(libraryName)).joinToString(" ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymbolicatedFrameItem(frame: SymbolicatedFrame) {
    val methodName = frame.methodName?.takeIf { it.isNotBlank() }
    val fileName = frame.fileName?.takeIf { it.isNotBlank() }
    val location = fileName?.let { file ->
        frame.lineNumber?.takeIf { it > 0 }?.let { "$file:$it" } ?: file
    }
    val libraryName = frame.libraryName?.takeIf { it.isNotBlank() && it != "system" }

    if (methodName == null && location == null && libraryName == null) {
        Text(
            text = frame.originalLine,
            color = VibeDesignTokens.Color.TextPrimary,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("symbolicated_frame_content"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (methodName == null && location == null) {
                Text(
                    text = frame.originalLine,
                    color = VibeDesignTokens.Color.TextPrimary,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp
                )
            }
            if (methodName != null) {
                Text(
                    text = methodName,
                    color = if (frame.isSystem) VibeDesignTokens.Color.TextMuted else VibeDesignTokens.Color.Cyan,
                    fontFamily = JetBrainsMono,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (location != null) {
                Text(
                    text = "($location)",
                    color = VibeDesignTokens.Color.Success,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp
                )
            }
            if (libraryName != null) {
                Surface(
                    color = VibeDesignTokens.Color.Grid,
                    contentColor = VibeDesignTokens.Color.TextMuted,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = libraryName,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }

@Composable
fun CategoryBadge(category: com.example.domain.model.ErrorCategory) {
    val labelRes = when (category) {
        com.example.domain.model.ErrorCategory.NULL_POINTER_EXCEPTION -> R.string.error_category_null_pointer_exception
        com.example.domain.model.ErrorCategory.OUT_OF_MEMORY -> R.string.error_category_out_of_memory
        com.example.domain.model.ErrorCategory.NETWORK_ERROR -> R.string.error_category_network_error
        com.example.domain.model.ErrorCategory.API_ERROR -> R.string.error_category_api_error
        com.example.domain.model.ErrorCategory.DATABASE_ERROR -> R.string.error_category_database_error
        com.example.domain.model.ErrorCategory.ANR -> R.string.error_category_anr
        com.example.domain.model.ErrorCategory.UI_THREAD_BLOCKING -> R.string.error_category_ui_thread_blocking
        com.example.domain.model.ErrorCategory.NATIVE_CRASH -> R.string.error_category_native_crash
        com.example.domain.model.ErrorCategory.MEMORY_LEAK -> R.string.error_category_memory_leak
        com.example.domain.model.ErrorCategory.DEPENDENCY_CONFLICT -> R.string.error_category_dependency_conflict
        com.example.domain.model.ErrorCategory.PERMISSION_ERROR -> R.string.error_category_permission_error
        com.example.domain.model.ErrorCategory.RUNTIME_EXCEPTION -> R.string.error_category_runtime_exception
        com.example.domain.model.ErrorCategory.FATAL_EXCEPTION -> R.string.error_category_fatal_exception
        com.example.domain.model.ErrorCategory.UNKNOWN_ERROR -> R.string.error_category_unknown_error
        com.example.domain.model.ErrorCategory.CUSTOM -> R.string.error_category_custom
    }
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp
        )
    }
}
@Composable
fun CrashInvestigationTeamSection(
    isInvestigating: Boolean,
    teamExecution: TeamExecution?,
    onStartInvestigation: () -> Unit,
    onViewReport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.crash_team_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.crash_team_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (teamExecution?.status == ExecutionStatus.COMPLETED) {
                    Surface(
                        color = Color(0xFF2E7D32),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.crash_team_completed),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.crash_team_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            if (isInvestigating) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.crash_team_running),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            teamExecution?.error?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        TextButton(onClick = onViewReport) {
                            Text(stringResource(R.string.crash_team_continue), fontSize = 11.sp)
                        }
                    }
                }
            } else if (teamExecution?.status == ExecutionStatus.COMPLETED) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onViewReport,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.crash_team_view_report), fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onStartInvestigation
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.crash_team_retry), fontSize = 12.sp)
                    }
                }
            } else {
                Button(
                    onClick = onStartInvestigation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Psychology, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.crash_team_start))
                }
            }
        }
    }
}

@Composable
fun CrashInvestigationDialog(
    execution: TeamExecution?,
    crashReport: CrashReport,
    isInvestigating: Boolean,
    onDismiss: () -> Unit,
    onNavigateToFix: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.crash_team_report_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header badge
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            stringResource(
                                R.string.crash_team_crash_summary,
                                crashReport.exceptionType.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.crashes_exception_unavailable),
                                stringResource(
                                    when (crashReport.severity) {
                                        Severity.CRITICAL -> R.string.crashes_severity_critical
                                        Severity.HIGH -> R.string.crashes_severity_high
                                        Severity.MEDIUM -> R.string.crashes_severity_medium
                                        Severity.LOW -> R.string.crashes_severity_low
                                    }
                                )
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            crashReport.message.takeIf { it.isNotBlank() } ?: stringResource(R.string.crashes_message_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Steps progress list
                if (execution != null && execution.steps.isNotEmpty()) {
                    Text(
                        stringResource(R.string.crash_team_progress, execution.steps.count { it.status == StepStatus.COMPLETED }),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    execution.steps.forEach { step ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (step.status) {
                                    StepStatus.COMPLETED -> Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                    StepStatus.IN_PROGRESS -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else -> Icon(Icons.Default.Schedule, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${step.role}: ${step.memberName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                    Text(step.task, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Final Report display
                if (execution?.finalResult != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        stringResource(R.string.crash_team_final_report),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = execution.finalResult,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    // Governance Disclaimer
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.crash_team_governance),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (execution?.finalResult != null) {
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(execution.finalResult))
                        copied = true
                    }) {
                        Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(if (copied) R.string.crash_team_copied else R.string.crash_team_copy_report))
                    }
                    Button(onClick = onNavigateToFix) {
                        Icon(Icons.Default.Build, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.crash_team_fix_assistant))
                    }
                } else {
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.crash_team_ok))
                    }
                }
            }
        },
        dismissButton = {
            if (execution?.finalResult != null) {
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.crash_team_close))
                }
            }
        }
    )
}
