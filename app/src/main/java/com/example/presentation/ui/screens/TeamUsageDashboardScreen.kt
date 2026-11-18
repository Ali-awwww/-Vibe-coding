package com.example.presentation.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.di.DependencyProvider
import com.example.domain.model.*
import com.example.presentation.ui.components.TeamGovernanceDialog
import com.example.presentation.viewmodel.TeamUsagePeriod
import com.example.presentation.viewmodel.TeamUsageUiState
import com.example.presentation.viewmodel.TeamUsageViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamUsageDashboardScreen(
    navController: NavController,
    teamId: String
) {
    val context = LocalContext.current
    val viewModel: TeamUsageViewModel = viewModel {
        TeamUsageViewModel(
            teamId = teamId,
            teamRepository = DependencyProvider.provideAIModelTeamRepository(context),
            authRepository = DependencyProvider.provideAuthRepository(context)
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showGovernanceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.isAuthenticated, uiState.team?.id) {
        if (!uiState.isAuthenticated || uiState.team == null) showGovernanceDialog = false
    }

    Scaffold(
        containerColor = com.example.ui.theme.VibeDesignTokens.Color.SpaceBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("لوحة استهلاك الفريق", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(uiState.team?.name ?: "", style = MaterialTheme.typography.labelSmall, color = com.example.ui.theme.VibeDesignTokens.Color.TextMuted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(
                        enabled = uiState.isAuthenticated && !uiState.isLoading,
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.testTag("team_usage_refresh")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث بيانات الاستهلاك")
                    }
                    IconButton(
                        enabled = uiState.team != null && uiState.isAuthenticated && !uiState.isMutating,
                        onClick = { showGovernanceDialog = true }
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "إعدادات الحوكمة")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.example.ui.theme.VibeDesignTokens.Color.SpaceBackground,
                    titleContentColor = com.example.ui.theme.VibeDesignTokens.Color.TextPrimary,
                    navigationIconContentColor = com.example.ui.theme.VibeDesignTokens.Color.TextPrimary,
                    actionIconContentColor = com.example.ui.theme.VibeDesignTokens.Color.Cyan
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(com.example.ui.theme.VibeDesignTokens.Color.SpaceBackground)
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize().testTag("team_usage_loading"),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = com.example.ui.theme.VibeDesignTokens.Color.Cyan)
                }

                !uiState.isAuthenticated -> TeamUsageStatePanel(
                    title = "يلزم تسجيل الدخول",
                    message = TeamUsageViewModel.AUTH_REQUIRED_MESSAGE,
                    onRetry = null
                )

                uiState.team == null -> TeamUsageStatePanel(
                    title = "الفريق غير متاح",
                    message = uiState.loadError ?: "لا يمكن عرض بيانات هذا الفريق.",
                    onRetry = { viewModel.refresh() }
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("team_usage_content"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        PeriodFilterChips(
                            selectedPeriod = uiState.selectedPeriod,
                            onSelect = viewModel::updatePeriod
                        )
                    }

                    item { TeamMetricsOverview(uiState) }

                    item {
                        GovernanceStatusCard(
                            team = uiState.team,
                            hasMeasuredUsage = uiState.hasMeasuredUsage,
                            hasPaidUsageUnderFreeOnly = uiState.hasPaidUsageUnderFreeOnly,
                            onEditPolicy = { showGovernanceDialog = true }
                        )
                    }

                    if (uiState.failoverEvents.isNotEmpty()) {
                        item { FailoversSection(uiState.failoverEvents) }
                    }

                    item {
                        Text(
                            "استهلاك أعضاء الفريق",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = com.example.ui.theme.VibeDesignTokens.Color.TextPrimary
                        )
                    }

                    if (uiState.memberSummaries.isNotEmpty()) {
                        item {
                            MemberFilterChips(
                                members = uiState.memberSummaries,
                                selectedId = uiState.selectedMemberId,
                                onSelect = viewModel::selectMemberFilter
                            )
                        }
                        items(uiState.memberSummaries, key = { it.memberId.ifBlank { it.modelId + it.providerId } }) { summary ->
                            MemberUsageCard(summary)
                        }
                    } else {
                        item {
                            Text(
                                "لا توجد بيانات استهلاك موثقة للأعضاء ضمن هذه الفترة.",
                                color = com.example.ui.theme.VibeDesignTokens.Color.TextMuted
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "سجل العمليات والطلبات",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.VibeDesignTokens.Color.TextPrimary
                            )
                            Text(
                                uiState.filteredRecords.size.toString() + " عملية",
                                style = MaterialTheme.typography.labelSmall,
                                color = com.example.ui.theme.VibeDesignTokens.Color.TextMuted
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            placeholder = { Text("بحث بالمهمة أو النموذج أو العضو...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    if (uiState.filteredRecords.isEmpty()) {
                        item { EmptyUsageRecords() }
                    } else {
                        items(uiState.filteredRecords, key = { it.id }) { record ->
                            UsageRecordItem(record)
                        }
                    }
                }
            }
        }
    }

    if (showGovernanceDialog && uiState.team != null) {
        TeamGovernanceDialog(
            currentSettings = uiState.team!!.governance,
            onSave = { updated ->
                viewModel.updateTeamGovernance(updated) {
                    showGovernanceDialog = false
                }
            },
            onDismiss = { if (!uiState.isMutating) showGovernanceDialog = false },
            isSaving = uiState.isMutating
        )
    }
}

@Composable
internal fun TeamUsageStatePanel(
    title: String,
    message: String,
    onRetry: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("team_usage_unavailable"),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.VibeDesignTokens.Color.SpacePanel),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.VibeDesignTokens.Color.Grid)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.QueryStats, contentDescription = null, tint = com.example.ui.theme.VibeDesignTokens.Color.Cyan)
            Text(title, style = MaterialTheme.typography.titleMedium, color = com.example.ui.theme.VibeDesignTokens.Color.TextPrimary)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = com.example.ui.theme.VibeDesignTokens.Color.TextMuted)
            if (onRetry != null) {
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("إعادة المحاولة")
                }
            }
        }
    }
}

@Composable
private fun PeriodFilterChips(
    selectedPeriod: TeamUsagePeriod,
    onSelect: (TeamUsagePeriod) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(TeamUsagePeriod.values().toList()) { period ->
            FilterChip(
                selected = selectedPeriod == period,
                onClick = { onSelect(period) },
                label = { Text(period.labelAr) }
            )
        }
    }
}

@Composable
internal fun TeamMetricsOverview(uiState: TeamUsageUiState) {
    if (!uiState.hasMeasuredUsage) {
        Card(
            modifier = Modifier.fillMaxWidth().testTag("team_usage_no_measurements"),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.VibeDesignTokens.Color.SpacePanel),
            border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.VibeDesignTokens.Color.Grid)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "لا توجد بيانات استهلاك قابلة للقياس خلال هذه الفترة.",
                    style = MaterialTheme.typography.titleSmall,
                    color = com.example.ui.theme.VibeDesignTokens.Color.TextPrimary
                )
                Text(
                    "قد توجد محاولات محظورة أو فاشلة، لكنها لا تثبت حدوث استهلاك قابل للفوترة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = com.example.ui.theme.VibeDesignTokens.Color.TextMuted
                )
            }
        }
        return
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricTile(
            title = "الطلبات المسجلة",
            value = uiState.totalRequests.toString(),
            icon = Icons.Default.Api,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary
        )
        MetricTile(
            title = "الرموز التقديرية",
            value = String.format("%,d", uiState.totalTokens),
            icon = Icons.Default.DataUsage,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.secondary
        )
        MetricTile(
            title = "التكلفة المؤكدة",
            value = if (uiState.hasConfirmedCost) String.format("%.3f", uiState.totalCostUsd) + "$" else "غير متاحة",
            icon = Icons.Default.MonetizationOn,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.tertiary
        )
    }

    Text(
        "أعداد الرموز تقديرية من طول النص، وليست قياساً مباشراً من مزوّد النموذج.",
        color = com.example.ui.theme.VibeDesignTokens.Color.TextMuted,
        style = MaterialTheme.typography.bodySmall
    )

    if (uiState.unconfirmedCostUsd > 0.0) {
        Text(
            "تكلفة تقديرية غير مؤكدة: " + String.format("%.3f", uiState.unconfirmedCostUsd) + "$",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (uiState.hasPaidUsageUnderFreeOnly) {
        Text(
            "تنبيه: رُصد سجل استخدام مدفوع رغم تفعيل سياسة Free Only.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
    uiState.lastMeasuredAt?.let { timestamp ->
        Text(
            "المصدر: سجلات استخدام الفريق • آخر سجل: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp)),
            color = com.example.ui.theme.VibeDesignTokens.Color.TextMuted,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun GovernanceStatusCard(
    team: ModelTeam?,
    hasMeasuredUsage: Boolean,
    hasPaidUsageUnderFreeOnly: Boolean,
    onEditPolicy: () -> Unit
) {
    val gov = team?.governance ?: return
    val warning = hasPaidUsageUnderFreeOnly
    val statusColor = when {
        warning -> MaterialTheme.colorScheme.error
        gov.freeOnly -> com.example.ui.theme.VibeDesignTokens.Color.Success
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (warning) Icons.Default.Warning else if (gov.freeOnly) Icons.Default.CheckCircle else Icons.Default.Shield,
                        null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            warning -> "تنبيه: رُصد استخدام مدفوع مع سياسة Free Only"
                            gov.freeOnly && hasMeasuredUsage -> "سجلات الفترة لا تُظهر استخداماً مدفوعاً"
                            gov.freeOnly -> "سياسة Free Only مهيأة — لا يوجد استهلاك موثق بعد"
                            else -> "سياسة حدود الاستهلاك مهيأة"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                IconButton(onClick = onEditPolicy, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل السياسة", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (gov.freeOnly)
                    "الحدود الحالية تمنع النماذج المدفوعة؛ راقب سجلات القياس لاكتشاف أي مخالفة."
                else
                    "الميزانية القصوى: " + gov.maxTeamCostUsd + "$ • الرموز: " + String.format("%,d", gov.maxTotalTokens),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PolicyPill("الحد: " + gov.maxApiRequests + " طلب")
                PolicyPill("أقصى تزامن: " + gov.maxParallelRequests)
                if (gov.autoFallbackToFree) PolicyPill("بديل مجاني عند 429")
            }
        }
    }
}

@Composable
private fun PolicyPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FailoversSection(failovers: List<ModelFailoverEvent>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapHoriz, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("سجل التحويل التلقائي للحصص (${failovers.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            failovers.take(3).forEach { ev ->
                Text(
                    "• [${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ev.timestamp))}] ${ev.memberName}: تم التبديل إلى ${ev.replacementModelName} مجاناً بعد استنفاد الحصة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MemberFilterChips(
    members: List<MemberUsageSummary>,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("الكل (${members.size})") }
            )
        }
        items(members) { member ->
            FilterChip(
                selected = selectedId == member.memberId,
                onClick = { onSelect(member.memberId) },
                label = { Text("${member.memberName} (${member.requestsCount})") }
            )
        }
    }
}

@Composable
private fun MemberUsageCard(summary: MemberUsageSummary) {
    val costLabel = when {
        !summary.isCostConfirmed -> "تكلفة غير مؤكدة"
        summary.isFreeModel -> "مجاني (مسجل)"
        else -> "مؤكد: " + String.format("%.3f", summary.costUsd) + "$"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.VibeDesignTokens.Color.SpacePanel)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                        Text(summary.role.labelAr.take(1), modifier = Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(summary.memberName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(summary.role.labelAr, style = MaterialTheme.typography.labelSmall, color = com.example.ui.theme.VibeDesignTokens.Color.TextMuted)
                    }
                }
                Surface(
                    color = if (summary.isCostConfirmed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(costLabel, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("النموذج", style = MaterialTheme.typography.labelSmall, color = com.example.ui.theme.VibeDesignTokens.Color.TextMuted)
                    Text(summary.modelName.ifBlank { summary.modelId.ifBlank { "غير محدد" } }, style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text("المزود", style = MaterialTheme.typography.labelSmall, color = com.example.ui.theme.VibeDesignTokens.Color.TextMuted)
                    Text(summary.providerName.ifBlank { summary.providerId.ifBlank { "غير محدد" } }, style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text("الطلبات", style = MaterialTheme.typography.labelSmall, color = com.example.ui.theme.VibeDesignTokens.Color.TextMuted)
                    Text(summary.requestsCount.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
                Column {
                    Text("الرموز", style = MaterialTheme.typography.labelSmall, color = com.example.ui.theme.VibeDesignTokens.Color.TextMuted)
                    Text(String.format("%,d", summary.totalTokens), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }

            if (summary.rateLimitHits > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("أخطاء تجاوز الحصة (429): " + summary.rateLimitHits, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun UsageRecordItem(record: TeamUsageRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = when (record.status) {
                            "SUCCESS" -> Color(0xFF2E7D32).copy(alpha = 0.2f)
                            "RATE_LIMITED" -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        },
                        shape = CircleShape
                    ) {
                        Icon(
                            when (record.status) {
                                "SUCCESS" -> Icons.Default.Check
                                "RATE_LIMITED" -> Icons.Default.HourglassTop
                                else -> Icons.Default.Close
                            },
                            null,
                            modifier = Modifier.padding(4.dp).size(14.dp),
                            tint = when (record.status) {
                                "SUCCESS" -> Color(0xFF2E7D32)
                                "RATE_LIMITED" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.outline
                            }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(record.memberName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text("(${record.modelName})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(record.taskDescription, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "الرموز: ${record.tokensInput} إدخال | ${record.tokensOutput} إخراج (المجموع: ${record.totalTokens})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    when {
                        !record.isCostConfirmed -> "تكلفة غير مؤكدة"
                        record.isFree -> "مجاني (مسجل)"
                        else -> String.format("%.4f", record.costUsd) + "$"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        !record.isCostConfirmed -> MaterialTheme.colorScheme.error
                        record.isFree -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyUsageRecords() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.History, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(8.dp))
        Text("لا توجد سجلات قياس موثقة ضمن الفترة المحددة", style = MaterialTheme.typography.bodyMedium)
        Text("لا يمكن استنتاج أن الاستهلاك صفر من غياب السجلات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}
