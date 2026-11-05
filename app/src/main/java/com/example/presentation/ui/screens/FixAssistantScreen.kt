package com.example.presentation.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.R
import com.example.domain.model.*
import com.example.presentation.viewmodel.FixAssistantViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import com.example.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixAssistantScreen(
    crashId: String,
    navController: NavController,
    viewModel: FixAssistantViewModel,
    userId: String?
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showVerifyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(crashId) {
        viewModel.loadCrashAndDiagnosis(crashId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_fix_assistant)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.FixHistory()) }) {
                        Icon(Icons.Default.History, contentDescription = "Fix History")
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                uiState.crash?.let { crash ->
                    FixCrashHeader(crash)
                }

                // Verification in Progress Banner
                if (uiState.isVerifying) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Column {
                                Text(
                                    stringResource(R.string.verifying_fix),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "تشغيل السيناريو الآلي ومراقبة الذاكرة وتتبع المكدس...",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                // Show Latest Verification Result if available
                val latestVerification = uiState.verificationResult ?: uiState.verifications.firstOrNull()
                if (latestVerification != null) {
                    Text(
                        "نتيجة التحقق الأخيرة (Latest Verification)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    VerificationCard(
                        record = latestVerification,
                        onReVerify = { showVerifyDialog = true }
                    )
                }

                if (uiState.suggestions.isEmpty()) {
                    EmptyFixState(
                        isGenerating = uiState.isGenerating,
                        onGenerate = { viewModel.generateFix() }
                    )
                } else {
                    uiState.suggestions.forEach { fix ->
                        FixSuggestionCard(
                            fix = fix,
                            isCreatingPr = uiState.isCreatingPr,
                            isVerifying = uiState.isVerifying,
                            prUrl = uiState.prUrl,
                            canCreatePr = !userId.isNullOrBlank(),
                            onCopy = { clipboardManager.setText(AnnotatedString(fix.suggestedCode)) },
                            onCreatePr = { userId?.takeIf { it.isNotBlank() }?.let { viewModel.applyFixAndCreatePr(fix.id, it) } },
                            onVerifyFix = { showVerifyDialog = true }
                        )
                    }
                }

                uiState.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showVerifyDialog) {
        VerifyFixDialog(
            defaultVersion = "1.2.1-patch",
            defaultBuildId = "build-fix-${System.currentTimeMillis().toString().takeLast(4)}",
            onDismiss = { showVerifyDialog = false },
            onConfirm = { newVer, bId, scenario, outcome ->
                viewModel.verifyFix(
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
fun FixCrashHeader(crash: CrashReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BugReport, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(crash.exceptionType, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(crash.message, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

@Composable
fun EmptyFixState(isGenerating: Boolean, onGenerate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(
                "هل أنت مستعد لإصلاح هذا الانهيار؟",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "سيقوم المساعد الذكي بتحليل الكود المصدري واقتراح حل برمجي دقيق.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(
                onClick = onGenerate,
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.suggest_fix))
                }
            }
        }
    }
}

@Composable
fun FixSuggestionCard(
    fix: FixSuggestion,
    isCreatingPr: Boolean,
    isVerifying: Boolean,
    prUrl: String?,
    canCreatePr: Boolean,
    onCopy: () -> Unit,
    onCreatePr: () -> Unit,
    onVerifyFix: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.review_fix), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(fix.filePath.substringAfterLast("/"), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.fix_explanation), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(fix.explanation, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.potential_side_effects), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            Text(fix.potentialSideEffects, style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(16.dp))
            TestResultsSummary(fix.testResults)

            Spacer(Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
                    Text(if (expanded) "إخفاء الكود" else "عرض الكود (Diff)")
                }
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, null)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.original_code), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    StackTraceCard(fix.originalCode)
                    
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.proposed_changes), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    StackTraceCard(fix.suggestedCode)
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Action row 1: GitHub PR
            if (prUrl != null || fix.status == FixStatus.PR_CREATED) {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Button(
                    onClick = { uriHandler.openUri(prUrl ?: fix.githubPrUrl ?: "") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24292F))
                ) {
                    Icon(Icons.Default.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("مشاهدة Pull Request على GitHub")
                }
            } else {
                Text(
                    if (canCreatePr) stringResource(R.string.pr_disclaimer) else "يلزم تسجيل الدخول بحساب صالح لإنشاء Pull Request.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(
                    onClick = onCreatePr,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCreatingPr && canCreatePr,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isCreatingPr) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Merge, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.create_github_pr))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action row 2: Verify Fix in Sandbox
            OutlinedButton(
                onClick = onVerifyFix,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isVerifying
            ) {
                Icon(Icons.Default.CheckCircleOutline, null, tint = Color(0xFF2E7D32))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.verify_fix), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TestResultsSummary(resultsJson: String?) {
    if (resultsJson == null) return
    
    val results = remember(resultsJson) {
        try {
            Json.parseToJsonElement(resultsJson).jsonObject
        } catch (e: Exception) {
            null
        }
    } ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.small)
            .padding(12.dp)
    ) {
        Text(stringResource(R.string.test_results), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TestItem(stringResource(R.string.test_compilation), results["compilation"]?.jsonPrimitive?.content ?: "N/A")
            TestItem(stringResource(R.string.test_static_analysis), results["static_analysis"]?.jsonPrimitive?.content ?: "N/A")
            TestItem(stringResource(R.string.test_unit_tests), results["unit_tests"]?.jsonPrimitive?.content ?: "N/A")
        }
    }
}

@Composable
fun TestItem(label: String, status: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (status == "PASSED") Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (status == "PASSED") Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
    }
}
