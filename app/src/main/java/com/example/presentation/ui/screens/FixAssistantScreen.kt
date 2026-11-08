package com.example.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.R
import com.example.domain.model.*
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.FixAssistantUiState
import com.example.presentation.viewmodel.FixAssistantViewModel
import com.example.ui.components.EngButton
import com.example.ui.components.EngCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

internal sealed interface FixAssistantContentState {
    data object Loading : FixAssistantContentState
    data object MissingCrash : FixAssistantContentState
    data class LoadError(val message: String) : FixAssistantContentState
    data object DiagnosisMissing : FixAssistantContentState
    data object ReadyWithoutFix : FixAssistantContentState
    data class GenerationError(val message: String) : FixAssistantContentState
    data class ReadyWithFixes(val count: Int) : FixAssistantContentState
}

internal fun deriveFixAssistantContentState(uiState: FixAssistantUiState): FixAssistantContentState {
    if (uiState.isLoading) return FixAssistantContentState.Loading

    uiState.loadError?.let { return FixAssistantContentState.LoadError(it) }

    val crash = uiState.crash
    if (crash == null) return FixAssistantContentState.MissingCrash

    if (uiState.suggestions.isNotEmpty()) {
        return FixAssistantContentState.ReadyWithFixes(uiState.suggestions.size)
    }

    if (uiState.error != null) {
        return FixAssistantContentState.GenerationError(uiState.error)
    }

    uiState.suggestionsLoadError?.let { return FixAssistantContentState.LoadError(it) }

    if (uiState.diagnosis == null) {
        return FixAssistantContentState.DiagnosisMissing
    }

    return FixAssistantContentState.ReadyWithoutFix
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixAssistantScreen(
    crashId: String,
    navController: NavController,
    viewModel: FixAssistantViewModel,
    userId: String?
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(crashId) {
        viewModel.loadCrashAndDiagnosis(crashId)
    }

    Scaffold(
        containerColor = VibeDesignTokens.Color.SpaceBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.ai_fix_assistant),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "AI / FIX ENGINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = VibeDesignTokens.Color.Cyan
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "العودة",
                            tint = VibeDesignTokens.Color.Cyan
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.FixHistory()) }) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = stringResource(R.string.fix_history),
                            tint = VibeDesignTokens.Color.Cyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VibeDesignTokens.Color.SpaceBackground,
                    titleContentColor = VibeDesignTokens.Color.TextPrimary,
                    navigationIconContentColor = VibeDesignTokens.Color.Cyan,
                    actionIconContentColor = VibeDesignTokens.Color.Cyan
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            FixAssistantBackdrop(Modifier.fillMaxSize())

            when (val contentState = deriveFixAssistantContentState(uiState)) {
                FixAssistantContentState.Loading -> FixAssistantLoadingPanel()

                FixAssistantContentState.MissingCrash -> Box(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    FixAssistantNoticePanel(
                        title = stringResource(R.string.fix_assistant_missing_crash_title),
                        description = stringResource(R.string.fix_assistant_missing_crash_desc),
                        icon = Icons.Default.BugReport,
                        accent = VibeDesignTokens.Color.Error,
                        actionLabel = "العودة",
                        onAction = { navController.popBackStack() }
                    )
                }

                is FixAssistantContentState.LoadError -> Box(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    FixAssistantNoticePanel(
                        title = stringResource(R.string.fix_assistant_load_error_title),
                        description = contentState.message,
                        icon = Icons.Default.ErrorOutline,
                        accent = VibeDesignTokens.Color.Error,
                        actionLabel = "إعادة التحميل",
                        onAction = { viewModel.loadCrashAndDiagnosis(crashId) }
                    )
                }

                FixAssistantContentState.DiagnosisMissing,
                FixAssistantContentState.ReadyWithoutFix,
                is FixAssistantContentState.GenerationError,
                is FixAssistantContentState.ReadyWithFixes -> {
                    val crash = uiState.crash
                    if (crash != null) {
                        FixAssistantContent(
                            crash = crash,
                            uiState = uiState,
                            contentState = contentState,
                            canCreatePr = !userId.isNullOrBlank(),
                            onBack = { navController.popBackStack() },
                            onGenerate = { viewModel.generateFix() },
                            onCopy = { fix ->
                                clipboardManager.setText(AnnotatedString(fix.suggestedCode))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("تم نسخ الكود المقترح")
                                }
                            },
                            onCreatePr = { fix ->
                                userId?.takeIf { it.isNotBlank() }?.let {
                                    viewModel.applyFixAndCreatePr(fix.id, it)
                                }
                            }
                        )
                    } else {
                        FixAssistantNoticePanel(
                            title = stringResource(R.string.fix_assistant_missing_crash_title),
                            description = stringResource(R.string.fix_assistant_missing_crash_desc),
                            icon = Icons.Default.BugReport,
                            accent = VibeDesignTokens.Color.Error,
                            actionLabel = "العودة",
                            onAction = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FixAssistantContent(
    crash: CrashReport,
    uiState: FixAssistantUiState,
    contentState: FixAssistantContentState,
    canCreatePr: Boolean,
    onBack: () -> Unit,
    onGenerate: () -> Unit,
    onCopy: (FixSuggestion) -> Unit,
    onCreatePr: (FixSuggestion) -> Unit
) {
    val latestVerification = uiState.verificationResult ?: uiState.verifications.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = VibeDesignTokens.Spacing.Medium, vertical = VibeDesignTokens.Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium)
    ) {
        FixCrashHeader(crash)

        uiState.diagnosis?.let { FixDiagnosisPanel(it) }

        VerificationUnavailableBanner()

        uiState.verificationsLoadError?.let { message ->
            FixAssistantNoticePanel(
                title = "تعذر تحميل سجل التحقق",
                description = message,
                icon = Icons.Default.ErrorOutline,
                accent = VibeDesignTokens.Color.Error
            )
        }

        latestVerification?.let { record ->
            SectionHeader("سجل تحقق سابق")
            VerificationCard(record = record, onReVerify = null)
        }

        when (contentState) {
            FixAssistantContentState.DiagnosisMissing -> FixAssistantNoticePanel(
                title = "التشخيص غير متوفر",
                description = "ارجع إلى الشاشة السابقة لإجراء التشخيص قبل توليد مقترح إصلاح.",
                icon = Icons.Default.AutoAwesome,
                accent = VibeDesignTokens.Color.Violet,
                actionLabel = "العودة",
                onAction = onBack
            )

            FixAssistantContentState.ReadyWithoutFix -> EmptyFixState(
                isGenerating = uiState.isGenerating,
                onGenerate = onGenerate
            )

            is FixAssistantContentState.GenerationError -> FixAssistantNoticePanel(
                title = stringResource(R.string.fix_assistant_generation_error_title),
                description = contentState.message,
                icon = Icons.Default.ErrorOutline,
                accent = VibeDesignTokens.Color.Error,
                actionLabel = stringResource(R.string.fix_assistant_retry_generation),
                onAction = onGenerate
            )

            is FixAssistantContentState.ReadyWithFixes -> {
                uiState.suggestionsLoadError?.let { message ->
                    FixAssistantNoticePanel(
                        title = "تعذر تحديث قائمة المقترحات",
                        description = message,
                        icon = Icons.Default.ErrorOutline,
                        accent = VibeDesignTokens.Color.Error
                    )
                }

                uiState.error?.let { message ->
                    FixAssistantNoticePanel(
                        title = "تعذر إكمال العملية",
                        description = message,
                        icon = Icons.Default.ErrorOutline,
                        accent = VibeDesignTokens.Color.Error
                    )
                }

                SectionHeader("مقترحات الإصلاح (${contentState.count})")

                EngButton(
                    onClick = onGenerate,
                    enabled = !uiState.isGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = VibeDesignTokens.Color.SpaceBackground,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.fix_assistant_generate_more))
                }

                uiState.suggestions.forEach { fix ->
                    FixSuggestionCard(
                        fix = fix,
                        isCreatingPr = uiState.isCreatingPr,
                        isVerifying = uiState.isVerifying,
                        prUrl = uiState.prUrl?.takeIf { it == fix.githubPrUrl },
                        canCreatePr = canCreatePr,
                        onCopy = { onCopy(fix) },
                        onCreatePr = { onCreatePr(fix) }
                    )
                }
            }

            FixAssistantContentState.Loading,
            FixAssistantContentState.MissingCrash,
            is FixAssistantContentState.LoadError -> Unit
        }
    }
}

@Composable
private fun FixAssistantBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val gridStep = 34.dp.toPx()
        val gridColor = VibeDesignTokens.Color.Grid
        for (x in 0..(size.width / gridStep).toInt()) {
            drawLine(
                color = gridColor,
                start = Offset(x * gridStep, 0f),
                end = Offset(x * gridStep, size.height),
                strokeWidth = 1f
            )
        }
        for (y in 0..(size.height / gridStep).toInt()) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y * gridStep),
                end = Offset(size.width, y * gridStep),
                strokeWidth = 1f
            )
        }

        val glowCenter = Offset(size.width * 0.82f, size.height * 0.18f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(VibeDesignTokens.Color.Cyan.copy(alpha = 0.16f), Color.Transparent),
                center = glowCenter,
                radius = size.minDimension * 0.48f
            ),
            center = glowCenter,
            radius = size.minDimension * 0.48f
        )
        drawCircle(
            color = VibeDesignTokens.Color.Violet.copy(alpha = 0.12f),
            center = glowCenter,
            radius = size.minDimension * 0.28f,
            style = Stroke(width = 1.2f)
        )
    }
}

@Composable
private fun FixAssistantLoadingPanel() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        EngCard(modifier = Modifier.padding(VibeDesignTokens.Spacing.Large)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium)
            ) {
                CircularProgressIndicator(color = VibeDesignTokens.Color.Cyan)
                Text("تحميل تقرير الانهيار والتشخيص...", color = VibeDesignTokens.Color.TextPrimary)
                Text(
                    "CRASH / DIAGNOSIS",
                    style = MaterialTheme.typography.labelSmall,
                    color = VibeDesignTokens.Color.TextMuted,
                    fontFamily = JetBrainsMono
                )
            }
        }
    }
}

@Composable
private fun FixAssistantNoticePanel(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    EngCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(VibeDesignTokens.Spacing.Medium)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                Text(title, color = VibeDesignTokens.Color.TextPrimary, fontWeight = FontWeight.Bold)
            }
            Text(
                description,
                color = VibeDesignTokens.Color.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
            if (actionLabel != null && onAction != null) {
                EngButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun VerificationUnavailableBanner() {
    EngCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium)
        ) {
            Icon(Icons.Default.Block, contentDescription = null, tint = VibeDesignTokens.Color.Error)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.verification_unavailable_title),
                    color = VibeDesignTokens.Color.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.verification_unavailable_desc),
                    color = VibeDesignTokens.Color.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Surface(
                color = VibeDesignTokens.Color.Error.copy(alpha = 0.12f),
                contentColor = VibeDesignTokens.Color.Error,
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact)
            ) {
                Text(
                    "BLOCKED",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun FixCrashHeader(crash: CrashReport) {
    var stackTraceExpanded by remember(crash.id) { mutableStateOf(false) }
    val severityColor = when (crash.severity) {
        Severity.LOW -> VibeDesignTokens.Color.Success
        Severity.MEDIUM -> VibeDesignTokens.Color.Cyan
        Severity.HIGH -> VibeDesignTokens.Color.Violet
        Severity.CRITICAL -> VibeDesignTokens.Color.Error
    }
    val formattedTimestamp = remember(crash.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(crash.timestamp))
    }

    EngCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = severityColor, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        crash.exceptionType.ifBlank { "Unknown exception" },
                        color = VibeDesignTokens.Color.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "CRASH ID / ${crash.id.take(8)}",
                        color = VibeDesignTokens.Color.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono
                    )
                }
                Surface(
                    color = severityColor.copy(alpha = 0.14f),
                    contentColor = severityColor,
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact)
                ) {
                    Text(
                        crash.severity.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                crash.message.ifBlank { "لا توجد رسالة تفصيلية للانهيار." },
                color = VibeDesignTokens.Color.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)
            ) {
                CrashMetadataChip("APP", crash.appVersion.ifBlank { "—" }, Modifier.weight(1f))
                CrashMetadataChip("OS", crash.osVersion.ifBlank { "—" }, Modifier.weight(1f))
                CrashMetadataChip("DEVICE", crash.deviceModel.ifBlank { "—" }, Modifier.weight(1f))
            }

            Text(
                formattedTimestamp,
                color = VibeDesignTokens.Color.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMono
            )

            TextButton(
                onClick = { stackTraceExpanded = !stackTraceExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (stackTraceExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = VibeDesignTokens.Color.Cyan
                )
                Spacer(Modifier.width(6.dp))
                Text(if (stackTraceExpanded) "إخفاء Stack Trace" else "عرض Stack Trace", color = VibeDesignTokens.Color.Cyan)
            }

            AnimatedVisibility(visible = stackTraceExpanded && crash.stackTrace.isNotBlank()) {
                StackTraceCard(crash.stackTrace)
            }
        }
    }
}

@Composable
private fun CrashMetadataChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = VibeDesignTokens.Color.SpacePanelRaised,
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
        border = androidx.compose.foundation.BorderStroke(1.dp, VibeDesignTokens.Color.Grid)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = VibeDesignTokens.Color.Cyan, fontFamily = JetBrainsMono)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = VibeDesignTokens.Color.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FixDiagnosisPanel(diagnosis: AIDiagnosis) {
    var evidenceExpanded by remember(diagnosis.crashId) { mutableStateOf(false) }
    val certaintyLabel = when (diagnosis.certainty) {
        DiagnosisCertainty.CONFIRMED -> "مؤكد"
        DiagnosisCertainty.HIGHLY_LIKELY -> "مرجح بدرجة عالية"
        DiagnosisCertainty.POSSIBLE -> "محتمل"
        DiagnosisCertainty.UNKNOWN -> "غير محدد"
    }
    val confidencePercent = (diagnosis.confidence.coerceIn(0f, 1f) * 100f).roundToInt()

    EngCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium)) {
            SectionHeader("تحليل السبب الجذري")
            if (diagnosis.summary.isNotBlank()) {
                Text(diagnosis.summary, color = VibeDesignTokens.Color.TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            if (diagnosis.rootCause.isNotBlank()) {
                Text(diagnosis.rootCause, color = VibeDesignTokens.Color.TextMuted, style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)
            ) {
                DiagnosisMetadataChip("CERTAINTY", certaintyLabel, Modifier.weight(1f))
                DiagnosisMetadataChip("CATEGORY", diagnosis.category.name.replace('_', ' '), Modifier.weight(1f))
                DiagnosisMetadataChip("CONFIDENCE", if (diagnosis.confidence > 0f) "${confidencePercent}%" else "—", Modifier.weight(1f))
            }

            if (diagnosis.sourceLocation.isNotBlank()) {
                Text("المصدر", style = MaterialTheme.typography.labelMedium, color = VibeDesignTokens.Color.Cyan, fontWeight = FontWeight.Bold)
                Text(
                    diagnosis.sourceLocation,
                    color = VibeDesignTokens.Color.TextPrimary,
                    fontFamily = JetBrainsMono,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (diagnosis.whyItHappened.isNotBlank()) {
                Text("لماذا حدث؟", style = MaterialTheme.typography.labelMedium, color = VibeDesignTokens.Color.Cyan, fontWeight = FontWeight.Bold)
                Text(diagnosis.whyItHappened, color = VibeDesignTokens.Color.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (diagnosis.solution.isNotBlank()) {
                Text("الاتجاه المقترح للحل", style = MaterialTheme.typography.labelMedium, color = VibeDesignTokens.Color.Cyan, fontWeight = FontWeight.Bold)
                Text(diagnosis.solution, color = VibeDesignTokens.Color.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (!diagnosis.missingInformation.isNullOrBlank()) {
                Surface(
                    color = VibeDesignTokens.Color.Violet.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VibeDesignTokens.Color.Violet.copy(alpha = 0.4f))
                ) {
                    Text(
                        "معلومات ناقصة: ${diagnosis.missingInformation}",
                        modifier = Modifier.padding(10.dp),
                        color = VibeDesignTokens.Color.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (diagnosis.technicalEvidence.isNotBlank()) {
                TextButton(
                    onClick = { evidenceExpanded = !evidenceExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (evidenceExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = VibeDesignTokens.Color.Cyan
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (evidenceExpanded) "إخفاء الأدلة الفنية" else "عرض الأدلة الفنية", color = VibeDesignTokens.Color.Cyan)
                }
                AnimatedVisibility(visible = evidenceExpanded) {
                    Surface(
                        color = VibeDesignTokens.Color.SpaceBackground,
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VibeDesignTokens.Color.Grid)
                    ) {
                        Text(
                            diagnosis.technicalEvidence,
                            modifier = Modifier.padding(12.dp),
                            color = VibeDesignTokens.Color.TextMuted,
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosisMetadataChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = VibeDesignTokens.Color.SpacePanelRaised,
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
        border = androidx.compose.foundation.BorderStroke(1.dp, VibeDesignTokens.Color.Grid)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = VibeDesignTokens.Color.Cyan, fontFamily = JetBrainsMono)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = VibeDesignTokens.Color.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EmptyFixState(isGenerating: Boolean, onGenerate: () -> Unit) {
    EngCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(44.dp), tint = VibeDesignTokens.Color.Cyan)
            Text(
                stringResource(R.string.fix_assistant_no_fix_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VibeDesignTokens.Color.TextPrimary
            )
            Text(
                stringResource(R.string.fix_assistant_no_fix_desc),
                style = MaterialTheme.typography.bodySmall,
                color = VibeDesignTokens.Color.TextMuted,
                textAlign = TextAlign.Center
            )
            EngButton(
                onClick = onGenerate,
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = VibeDesignTokens.Color.SpaceBackground,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.suggest_fix))
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
    onCreatePr: () -> Unit
) {
    var expanded by remember(fix.id) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val candidatePrUrl = listOfNotNull(prUrl, fix.githubPrUrl)
        .firstOrNull { isGitHubPullRequestUrl(it) }

    EngCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)
            ) {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    tint = VibeDesignTokens.Color.Cyan
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.review_fix),
                        color = VibeDesignTokens.Color.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        fix.filePath.ifBlank { "مسار الملف غير متوفر" },
                        color = VibeDesignTokens.Color.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FixStatusChip(fix.status)
            }

            Text(stringResource(R.string.fix_explanation), color = VibeDesignTokens.Color.Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(fix.explanation.ifBlank { "لم تُرفق تفاصيل التفسير." }, color = VibeDesignTokens.Color.TextPrimary, style = MaterialTheme.typography.bodyMedium)

            Text(stringResource(R.string.potential_side_effects), color = VibeDesignTokens.Color.Error, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(fix.potentialSideEffects.ifBlank { "لم تُحدد آثار جانبية في هذا المقترح." }, color = VibeDesignTokens.Color.TextMuted, style = MaterialTheme.typography.bodySmall)

            TestResultsSummary(fix.testResults)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EngButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.Code,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (expanded) "إخفاء Diff" else "عرض Diff")
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "نسخ الكود المقترح",
                        tint = VibeDesignTokens.Color.Cyan
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)) {
                    SectionHeader(stringResource(R.string.original_code))
                    StackTraceCard(fix.originalCode)
                    SectionHeader(stringResource(R.string.proposed_changes))
                    StackTraceCard(fix.suggestedCode)
                }
            }

            HorizontalDivider(color = VibeDesignTokens.Color.Grid)

            when {
                candidatePrUrl != null && fix.status == FixStatus.PR_CREATED -> {
                    EngButton(
                        onClick = { uriHandler.openUri(candidatePrUrl) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("مشاهدة Pull Request على GitHub")
                    }
                }

                fix.status == FixStatus.PR_CREATED -> {
                    Text(
                        "حالة PR مسجلة لكن رابط GitHub غير متوفر أو غير صالح؛ لن نفتح رابطًا فارغًا.",
                        color = VibeDesignTokens.Color.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                else -> {
                    Text(
                        if (canCreatePr) stringResource(R.string.pr_disclaimer) else "يلزم تسجيل الدخول بحساب صالح لإنشاء Pull Request.",
                        color = VibeDesignTokens.Color.TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                    EngButton(
                        onClick = onCreatePr,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreatingPr && canCreatePr
                    ) {
                        if (isCreatingPr) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = VibeDesignTokens.Color.SpaceBackground,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.Merge, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.create_github_pr))
                    }
                }
            }

            OutlinedButton(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                enabled = false,
                colors = ButtonDefaults.outlinedButtonColors(
                    disabledContentColor = VibeDesignTokens.Color.TextMuted
                )
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.Block, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.verification_unavailable_action))
            }
        }
    }
}

@Composable
private fun FixStatusChip(status: FixStatus) {
    val accent = when (status) {
        FixStatus.PROPOSED -> VibeDesignTokens.Color.Cyan
        FixStatus.APPLIED -> VibeDesignTokens.Color.Violet
        FixStatus.PR_CREATED -> VibeDesignTokens.Color.TextMuted
        FixStatus.REJECTED -> VibeDesignTokens.Color.Error
    }
    val label = when (status) {
        FixStatus.PROPOSED -> "PROPOSED"
        FixStatus.APPLIED -> "APPLIED"
        FixStatus.PR_CREATED -> "PR CREATED"
        FixStatus.REJECTED -> "REJECTED"
    }
    Surface(
        color = accent.copy(alpha = 0.14f),
        contentColor = accent,
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.4f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = JetBrainsMono
        )
    }
}

@Composable
fun TestResultsSummary(resultsJson: String?) {
    val parsedResults = remember(resultsJson) {
        resultsJson?.takeIf { it.isNotBlank() }?.let {
            runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = VibeDesignTokens.Color.SpacePanelRaised,
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
        border = androidx.compose.foundation.BorderStroke(1.dp, VibeDesignTokens.Color.Grid)
    ) {
        Column(
            modifier = Modifier.padding(VibeDesignTokens.Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)
        ) {
            Text(
                stringResource(R.string.test_results),
                color = VibeDesignTokens.Color.TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            when {
                resultsJson.isNullOrBlank() -> Text(
                    stringResource(R.string.test_results_not_run),
                    color = VibeDesignTokens.Color.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )

                parsedResults == null -> Text(
                    stringResource(R.string.test_results_unreadable),
                    color = VibeDesignTokens.Color.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )

                else -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)
                ) {
                    TestItem("Compilation", parseTestStatus(parsedResults, "compilation"), Modifier.weight(1f))
                    TestItem("Static Analysis", parseTestStatus(parsedResults, "static_analysis"), Modifier.weight(1f))
                    TestItem("Unit Tests", parseTestStatus(parsedResults, "unit_tests"), Modifier.weight(1f))
                }
            }
        }
    }
}

private fun parseTestStatus(results: JsonObject, key: String): String =
    results[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() } ?: "NOT_RUN"

@Composable
fun TestItem(label: String, status: String, modifier: Modifier = Modifier) {
    val normalized = status.trim().uppercase(Locale.ROOT)
    val isPassed = normalized in setOf("PASSED", "PASS", "SUCCESS")
    val isFailed = normalized in setOf("FAILED", "FAIL", "ERROR")
    val isRunning = normalized in setOf("RUNNING", "IN_PROGRESS")
    val accent = when {
        isPassed -> VibeDesignTokens.Color.Success
        isFailed -> VibeDesignTokens.Color.Error
        isRunning -> VibeDesignTokens.Color.Cyan
        else -> VibeDesignTokens.Color.TextMuted
    }
    val icon = when {
        isPassed -> Icons.Default.CheckCircle
        isFailed -> Icons.Default.Cancel
        isRunning -> Icons.Default.Autorenew
        else -> Icons.Default.HelpOutline
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = VibeDesignTokens.Color.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = "$label: $status", modifier = Modifier.size(14.dp), tint = accent)
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontSize = 10.sp,
                fontFamily = JetBrainsMono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun isGitHubPullRequestUrl(url: String): Boolean =
    url.startsWith("https://github.com/") &&
        url.contains("/pull/") &&
        !url.contains(' ')

