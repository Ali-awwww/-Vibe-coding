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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboardManager
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashDetailScreen(
    crashId: String,
    navController: NavController,
    viewModel: CrashDetailViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(crashId) {
        viewModel.loadCrash(crashId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crash_details)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.FixHistory(uiState.crash?.projectId)) }) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.fix_history))
                    }
                    if (uiState.crash?.isResolved == false) {
                        IconButton(onClick = { viewModel.resolveCrash() }) {
                            Icon(Icons.Default.Check, contentDescription = "Resolve")
                        }
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
            uiState.crash?.let { crash ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CrashHeader(crash)
                    
                    SymbolicationStatusBanner(uiState.symbolication, onRetry = { viewModel.symbolicateCrash() })

                    Text(stringResource(R.string.stack_trace), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    if (uiState.symbolication != null && uiState.symbolication!!.status == SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED) {
                        SymbolicatedStackTrace(uiState.symbolication!!)
                    } else {
                        StackTraceCard(crash.stackTrace)
                    }
                    
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
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.analyzing))
                            } else {
                                Icon(Icons.Default.AutoAwesome, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.ai_diagnosis))
                            }
                        }
                    } else {
                        DiagnosisCard(uiState.diagnosis!!, navController, uiState.crash)
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
                    
                    uiState.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun CrashHeader(crash: CrashReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityBadge(crash.severity)
                Spacer(Modifier.width(8.dp))
                if (crash.isResolved) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Resolved") },
                        leadingIcon = { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(crash.exceptionType, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(crash.message, style = MaterialTheme.typography.bodyMedium)
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoItem(label = "Device", value = crash.deviceModel)
                InfoItem(label = "OS", value = crash.osVersion)
                InfoItem(label = "Version", value = crash.appVersion)
            }
        }
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
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
            text = severity.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StackTraceCard(stackTrace: String) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFF85149), CircleShape))
                    Text(
                        text = "STACK_TRACE // SYSTEM",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B949E),
                        fontFamily = JetBrainsMono
                    )
                }
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(stackTrace))
                        copied = true
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy Stack Trace",
                        tint = if (copied) Color(0xFF3FB950) else Color(0xFF8B949E),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            HorizontalDivider(color = Color(0xFF30363D), modifier = Modifier.padding(vertical = 8.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stackTrace,
                        color = Color(0xFFE6EDF3),
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
    val color = when(certainty) {
        DiagnosisCertainty.CONFIRMED -> Color(0xFF2E7D32)
        DiagnosisCertainty.HIGHLY_LIKELY -> Color(0xFF1976D2)
        DiagnosisCertainty.POSSIBLE -> Color(0xFFFF9800)
        DiagnosisCertainty.UNKNOWN -> Color.Gray
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(
            text = certainty.name,
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
fun SymbolicationStatusBanner(report: SymbolicationReport?, onRetry: () -> Unit) {
    if (report == null) return
    
    val color = when(report.status) {
        SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED -> Color(0xFF2E7D32)
        SymbolicationStatus.PARTIALLY_SYMBOLICATED -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.error
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
            Icon(
                imageVector = when(report.status) {
                    SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED -> Icons.Default.Verified
                    SymbolicationStatus.PARTIALLY_SYMBOLICATED -> Icons.Default.Warning
                    else -> Icons.Default.ErrorOutline
                },
                contentDescription = null,
                tint = color
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.status.name.replace("_", " ").lowercase().capitalize(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                report.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.8f))
                }
            }
            if (report.status != SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED) {
                TextButton(onClick = onRetry) {
                    Text("Retry", color = color)
                }
            }
        }
    }
}

@Composable
fun SymbolicatedStackTrace(report: SymbolicationReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(modifier = Modifier.padding(12.dp)) {
                report.frames.forEach { frame ->
                    SymbolicatedFrameItem(frame)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun SymbolicatedFrameItem(frame: SymbolicatedFrame) {
    Column {
        if (frame.fileName != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${frame.methodName}",
                    color = if (frame.isSystem) Color(0xFF9E9E9E) else Color(0xFF64B5F6),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "(${frame.fileName}:${frame.lineNumber})",
                    color = Color(0xFF81C784),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
                if (frame.libraryName != null) {
                    Spacer(Modifier.width(4.dp))
                    Surface(color = Color.Gray.copy(alpha = 0.3f), shape = MaterialTheme.shapes.extraSmall) {
                        Text(frame.libraryName, color = Color.LightGray, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        } else {
            Text(
                text = frame.originalLine,
                color = Color(0xFFE0E0E0),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}

private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }

@Composable
fun CategoryBadge(category: com.example.domain.model.ErrorCategory) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = category.name.replace("_", " "),
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
                        "AI Crash Investigation Team",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "فريق تحقيق تخصصي (9 نماذج ذكاء اصطناعي)",
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
                            "مكتمل",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "يتولى الفريق المؤلف من Crash Analyst، Stack Trace Specialist، Source Code Investigator، Root Cause Analyst، Security Auditor، Fix Developer، Code Reviewer، QA Engineer، و Team Leader التحقيق الشامل في العطل وتحديد السبب الجذري ومراجعة المخاطر الأمنية وتصميم اختبار إعادة الإنتاج واقتراح الـ Patch.",
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
                                "التحقيق جارٍ بواسطة الفريق (10 خطوات)...",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            teamExecution?.error?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        TextButton(onClick = onViewReport) {
                            Text("متابعة", fontSize = 11.sp)
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
                        Text("عرض تقرير التحقيق النهائي", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onStartInvestigation
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إعادة التحقيق", fontSize = 12.sp)
                    }
                }
            } else {
                Button(
                    onClick = onStartInvestigation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Psychology, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("بدء التحقيق المعمق بواسطة الفريق (10 خطوات)")
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
                    "AI Crash Investigation Report",
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
                            "${crashReport.exceptionType} (${crashReport.severity.name})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            crashReport.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Steps progress list
                if (execution != null && execution.steps.isNotEmpty()) {
                    Text(
                        "مسار التحقيق ومهام الأعضاء (${execution.steps.count { it.status == StepStatus.COMPLETED }}/9 مكتملة):",
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
                        "التقرير النهائي الشامل والتوصيات:",
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
                                "ضوابط الحوكمة: لا يتم تطبيق الإصلاح أو دمجه في بيئة الإنتاج دون مراجعة وموافقة المطور الصريحة.",
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
                        Text(if (copied) "تم النسخ" else "نسخ التقرير")
                    }
                    Button(onClick = onNavigateToFix) {
                        Icon(Icons.Default.Build, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("مساعد الإصلاح (Fix)")
                    }
                } else {
                    Button(onClick = onDismiss) {
                        Text("حسناً")
                    }
                }
            }
        },
        dismissButton = {
            if (execution?.finalResult != null) {
                OutlinedButton(onClick = onDismiss) {
                    Text("إغلاق")
                }
            }
        }
    )
}
