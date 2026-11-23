package com.example.presentation.ui.screens.vibe

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.*
import com.example.presentation.viewmodel.VibeStudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeTestingPanelDialog(
    uiState: StudioUiState,
    viewModel: VibeStudioViewModel,
    onDismiss: () -> Unit
) {
    val report = uiState.qualityReport
    val autoDebug = uiState.autoDebugState

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header
                TestingPanelHeader(
                    uiState = uiState,
                    report = report,
                    onDismiss = onDismiss,
                    onRunTests = { viewModel.runQualityAudit() },
                    onStartAutoDebug = { viewModel.startAutoDebug() }
                )

                HorizontalDivider()

                // Navigation Tabs
                ScrollableTabRow(
                    selectedTabIndex = uiState.activeTestingTab.ordinal,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    TestingTab.values().forEach { tab ->
                        val badgeCount = when (tab) {
                            TestingTab.TESTS -> report?.totalTests ?: 0
                            TestingTab.FINDINGS -> report?.findings?.size ?: 0
                            TestingTab.CRASH_INTELLIGENCE -> if (report?.associatedCrashReportId != null) 1 else 0
                            TestingTab.AUTO_DEBUG -> if (autoDebug.attemptCount > 0) autoDebug.attemptCount else 0
                            else -> 0
                        }

                        Tab(
                            selected = uiState.activeTestingTab == tab,
                            onClick = { viewModel.setTestingTab(tab) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = tab.titleAr,
                                        fontWeight = if (uiState.activeTestingTab == tab) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (badgeCount > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (uiState.activeTestingTab == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = badgeCount.toString(),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (uiState.activeTestingTab == tab) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                // Tab Content
                Box(modifier = Modifier.weight(1f).padding(16.dp)) {
                    when (uiState.activeTestingTab) {
                        TestingTab.OVERVIEW -> OverviewTabContent(report, autoDebug, viewModel)
                        TestingTab.TESTS -> TestsTabContent(report, viewModel)
                        TestingTab.FINDINGS -> FindingsTabContent(report)
                        TestingTab.CRASH_INTELLIGENCE -> CrashIntelligenceTabContent(report, viewModel)
                        TestingTab.AUTO_DEBUG -> AutoDebugTabContent(autoDebug, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun TestingPanelHeader(
    uiState: StudioUiState,
    report: QualityAuditReport?,
    onDismiss: () -> Unit,
    onRunTests: () -> Unit,
    onStartAutoDebug: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.FactCheck,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "محرك الفحوصات الساكنة وضمان الجودة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "بيئة الفحص والتصحيح الآلي • ${uiState.activeWorkspace?.techStack ?: "التقنية التلقائية"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Readiness Status Badge
            val readiness = report?.readiness ?: ProductionReadiness.UNTESTED
            val (badgeBg, badgeText, badgeIcon) = when (readiness) {
                ProductionReadiness.READY -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Default.CheckCircle)
                ProductionReadiness.NEEDS_REVIEW -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Icons.Default.Warning)
                ProductionReadiness.NOT_READY -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Icons.Default.Cancel)
                ProductionReadiness.UNTESTED -> Triple(Color(0xFFF5F5F5), Color(0xFF616161), Icons.Default.Help)
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeBg,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(badgeIcon, contentDescription = null, tint = badgeText, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = readiness.labelAr,
                        color = badgeText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = onRunTests,
                enabled = !uiState.isAuditRunning,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (uiState.isAuditRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جاري الفحص...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تشغيل الفحوصات")
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onStartAutoDebug,
                enabled = !uiState.isAuditRunning && (report?.failedTests ?: 0) > 0,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF7F52FF))
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Debug with AI")
            }

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "إغلاق")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTabContent(
    report: QualityAuditReport?,
    autoDebug: AutoDebugState,
    viewModel: VibeStudioViewModel
) {
    if (report == null) {
        EmptyTestingState(onRunTests = { viewModel.runQualityAudit() })
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            // Main Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (report.readiness) {
                        ProductionReadiness.READY -> Color(0xFFF1F8E9)
                        ProductionReadiness.NEEDS_REVIEW -> Color(0xFFFFF8E1)
                        ProductionReadiness.NOT_READY -> Color(0xFFFFEBEE)
                        ProductionReadiness.UNTESTED -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = report.summaryMessage,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "استغرق الفحص ${report.durationMs}ms • تم فحص كافة وحدات الشفرة المصدرية وملفات التكوين الحقيقية.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (report.failedTests > 0) {
                        Button(
                            onClick = { viewModel.startAutoDebug() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F52FF)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("بدء التصحيح الذاتي")
                        }
                    }
                }
            }
        }

        item {
            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "إجمالي الفحوصات",
                    value = report.totalTests.toString(),
                    icon = Icons.Default.Assessment,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "فحوصات ناجحة",
                    value = report.passedTests.toString(),
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "فحوصات فاشلة",
                    value = report.failedTests.toString(),
                    icon = Icons.Default.Cancel,
                    color = if (report.failedTests > 0) Color(0xFFC62828) else Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "أخطاء برمجية مؤكدة",
                    value = report.confirmedErrorsCount.toString(),
                    icon = Icons.Default.BugReport,
                    color = if (report.confirmedErrorsCount > 0) Color(0xFFD32F2F) else Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "تحذيرات أمنية وأداء",
                    value = report.warningsCount.toString(),
                    icon = Icons.Default.Warning,
                    color = if (report.warningsCount > 0) Color(0xFFF57C00) else Color.Gray,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            // Technology-Aware Suites Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "الأجنحة المطبقة استناداً لتقنية المشروع (${report.techStack})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val suites = report.testResults.map { it.type }.distinct()
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suites.forEach { type ->
                            val passInSuite = report.testResults.filter { it.type == type && it.status == TestStatus.PASSED }.size
                            val totalInSuite = report.testResults.filter { it.type == type }.size

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (passInSuite == totalInSuite) Icons.Default.Check else Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = if (passInSuite == totalInSuite) Color(0xFF2E7D32) else Color(0xFFC62828),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${type.titleAr} ($passInSuite/$totalInSuite)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TestsTabContent(
    report: QualityAuditReport?,
    viewModel: VibeStudioViewModel
) {
    if (report == null || report.testResults.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد نتائج فحص ساكن مسجلة بعد. اضغط 'تشغيل الفحوصات' للبدء.")
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                text = "تنبيه: النتائج أدناه فحوصات ساكنة لملفات المشروع. لم يُشغّل مترجم أو مشغّل اختبارات فعلي؛ حالات PASS لا تثبت نجاح البناء أو التشغيل.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
        }
        items(report.testResults) { test ->
            TestCaseItemCard(
                test = test,
                onSendToCrashIntelligence = { viewModel.sendFailureToCrashIntelligence(test) },
                onDebugWithAi = { viewModel.startAutoDebug() }
            )
        }
    }
}

@Composable
private fun TestCaseItemCard(
    test: TestCaseResult,
    onSendToCrashIntelligence: () -> Unit,
    onDebugWithAi: () -> Unit
) {
    var expanded by remember { mutableStateOf(test.status == TestStatus.FAILED) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (test.status == TestStatus.FAILED) Color(0xFFFFF5F5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (test.status == TestStatus.FAILED) Color(0xFFFFCDD2) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        when (test.status) {
                            TestStatus.PASSED -> Icons.Default.CheckCircle
                            TestStatus.FAILED -> Icons.Default.Cancel
                            TestStatus.SKIPPED -> Icons.Default.RemoveCircleOutline
                            TestStatus.RUNNING -> Icons.Default.HourglassEmpty
                        },
                        contentDescription = null,
                        tint = when (test.status) {
                            TestStatus.PASSED -> Color(0xFF2E7D32)
                            TestStatus.FAILED -> Color(0xFFC62828)
                            TestStatus.SKIPPED -> Color.Gray
                            TestStatus.RUNNING -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = test.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${test.suiteName} • ${test.durationMs}ms" + (test.targetFile?.let { " • $it" } ?: ""),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 10.dp))

                    test.errorMessage?.let { error ->
                        Text(
                            text = "رسالة الخطأ:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFFC62828)
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            color = Color(0xFF263238),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = error,
                                color = Color(0xFFFF8A80),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    test.suggestedFix?.let { fix ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFF57C00), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "اقتراح المعالجة: $fix",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (test.status == TestStatus.FAILED) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onDebugWithAi,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F52FF)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تصحيح بالذكاء الاصطناعي", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = onSendToCrashIntelligence,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إرسال إلى Crash Intelligence", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindingsTabContent(report: QualityAuditReport?) {
    if (report == null || report.findings.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("لم يتم العثور على ثغرات أمنية أو مشاكل جودة في الكود المصدر.", fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(report.findings) { finding ->
            FindingItemCard(finding)
        }
    }
}

@Composable
private fun FindingItemCard(finding: QualityFinding) {
    val (severityColor, severityBg) = when (finding.severity) {
        IssueSeverity.ERROR -> Pair(Color(0xFFC62828), Color(0xFFFFEBEE))
        IssueSeverity.SECURITY -> Pair(Color(0xFFD32F2F), Color(0xFFFFEBEE))
        IssueSeverity.WARNING -> Pair(Color(0xFFE65100), Color(0xFFFFF3E0))
        IssueSeverity.INFO -> Pair(Color(0xFF1565C0), Color(0xFFE3F2FD))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(6.dp), color = severityBg) {
                        Text(
                            text = finding.category.titleAr,
                            color = severityColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = finding.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Text(
                    text = "${finding.filePath}:${finding.line}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = finding.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            finding.codeSnippet?.let { snippet ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E1E1E)
                ) {
                    Text(
                        text = snippet,
                        color = Color(0xFFECEFF1),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "التوصية: ${finding.recommendation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CrashIntelligenceTabContent(
    report: QualityAuditReport?,
    viewModel: VibeStudioViewModel
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "تكامل Crash Intelligence التلقائي",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "يربط محرك الاختبارات أخطاء التشغيل وفشل البناء بنظام المراقبة والتشخيص الذكي لتحديد السبب الجذري فوراً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        val failedTests = report?.testResults?.filter { it.status == TestStatus.FAILED } ?: emptyList()

        if (failedTests.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "لا توجد أعطال أو استثناءات مسجلة في هذا المشروع",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "جميع الاختبارات تعمل بصورة طبيعية ومستقرة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(failedTests) { failedTest ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5F5)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "تقرير عطل متولد: ${failedTest.name}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFFEBEE)) {
                                Text(
                                    text = "CRITICAL",
                                    color = Color(0xFFC62828),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "الملف المتأثر: ${failedTest.targetFile ?: "غير محدد"}:${failedTest.lineNumber ?: 1}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF212121)
                        ) {
                            Text(
                                text = failedTest.stackTrace ?: failedTest.errorMessage ?: "Stack trace unavailable",
                                color = Color(0xFFFFCC80),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { viewModel.retestAfterFix() },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إعادة الاختبار بعد الإصلاح")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoDebugTabContent(
    autoDebug: AutoDebugState,
    viewModel: VibeStudioViewModel
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            // Attempt tracker banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AI Auto Debug Engine (التصحيح الذاتي المحدود)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "محاولة ${autoDebug.attemptCount} من أصل ${autoDebug.maxAttempts} • مزود بحماية صارمة ضد الحلقات اللانهائية.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (autoDebug.isDebugging) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        autoDebug.currentStepMessage.takeIf { it.isNotBlank() }?.let { msg ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEDE7F6)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF5E35B1), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = msg, color = Color(0xFF311B92), fontSize = 13.sp)
                    }
                }
            }
        }

        val proposal = autoDebug.activeProposal
        if (proposal != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "اقتراح الإصلاح للمحاولة ${proposal.attempt}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = proposal.diagnosedRootCause,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "معاينة الفروقات البرمجية (Proposed Diff):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        proposal.proposedDiffs.forEach { diff ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E1E1E)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "الملف: ${diff.path}",
                                        color = Color(0xFF81D4FA),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    diff.diffLines.take(15).forEach { line ->
                                        val color = when (line.type) {
                                            DiffLineType.ADD -> Color(0xFF81C784)
                                            DiffLineType.REMOVE -> Color(0xFFE57373)
                                            DiffLineType.CONTEXT -> Color(0xFFECEFF1)
                                        }
                                        val prefix = when (line.type) {
                                            DiffLineType.ADD -> "+"
                                            DiffLineType.REMOVE -> "-"
                                            DiffLineType.CONTEXT -> " "
                                        }
                                        Text(
                                            text = "$prefix ${line.content}",
                                            color = color,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(onClick = { viewModel.cancelAutoDebug() }) {
                                Text("إلغاء")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.applyAutoDebugFix() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تطبيق الإصلاح وإعادة الفحص")
                            }
                        }
                    }
                }
            }
        }

        autoDebug.finalStatus?.let { status ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE8F5E9)
                ) {
                    Text(
                        text = status,
                        color = Color(0xFF1B5E20),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun EmptyTestingState(onRunTests: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Filled.FactCheck,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "محرك الفحوصات الساكنة وضمان الجودة جاهز للفحص",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "قم بتشغيل الفحص التلقائي لاختبار بناء الجمل، الأنواع، الثغرات الأمنية، وجاهزية الإنتاج.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRunTests) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("بدء الفحص الشامل الآن")
            }
        }
    }
}
