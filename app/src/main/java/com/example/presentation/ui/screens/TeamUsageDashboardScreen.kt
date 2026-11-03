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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.di.DependencyProvider
import com.example.domain.model.*
import com.example.presentation.ui.components.TeamGovernanceDialog
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
    var showGovernanceDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("لوحة استهلاك وحوكمة الفريق", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(uiState.team?.name ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showGovernanceDialog = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Governance Settings")
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
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. High-level Consumption Metrics Row
                item {
                    TeamMetricsOverview(uiState)
                }

                // 2. Active Governance Policy Card
                item {
                    GovernanceStatusCard(
                        team = uiState.team,
                        onEditPolicy = { showGovernanceDialog = true }
                    )
                }

                // 3. Failover Alerts (if any)
                if (uiState.failoverEvents.isNotEmpty()) {
                    item {
                        FailoversSection(uiState.failoverEvents)
                    }
                }

                // 4. Per-Member Consumption Breakdown
                item {
                    Text(
                        "استهلاك أعضاء الفريق (Per-Member Breakdown)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Member Filter Chips
                item {
                    MemberFilterChips(
                        members = uiState.memberSummaries,
                        selectedId = uiState.selectedMemberId,
                        onSelect = { viewModel.selectMemberFilter(it) }
                    )
                }

                items(uiState.memberSummaries.filter { uiState.selectedMemberId == null || it.memberId == uiState.selectedMemberId }) { summary ->
                    MemberUsageCard(summary)
                }

                // 5. Operations Audit Log Section
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "سجل العمليات والطلبات (Operations Log)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${uiState.filteredRecords.size} عملية",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("بحث بالمهمة أو النموذج أو العضو...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                if (uiState.filteredRecords.isEmpty()) {
                    item {
                        EmptyUsageRecords()
                    }
                } else {
                    items(uiState.filteredRecords) { record ->
                        UsageRecordItem(record)
                    }
                }
            }
        }
    }

    if (showGovernanceDialog && uiState.team != null) {
        TeamGovernanceDialog(
            currentSettings = uiState.team!!.governance,
            onSave = { updated ->
                viewModel.updateTeamGovernance(updated)
                showGovernanceDialog = false
            },
            onDismiss = { showGovernanceDialog = false }
        )
    }
}

@Composable
private fun TeamMetricsOverview(uiState: TeamUsageUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricTile(
            title = "إجمالي الطلبات",
            value = "${uiState.totalRequests}",
            icon = Icons.Default.Api,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary
        )
        MetricTile(
            title = "إجمالي الرموز",
            value = String.format("%,d", uiState.totalTokens),
            icon = Icons.Default.DataUsage,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.secondary
        )
        MetricTile(
            title = "التكلفة المؤكدة",
            value = if (uiState.team?.governance?.freeOnly == true) "0.00$" else "${String.format("%.3f", uiState.totalCostUsd)}$",
            icon = Icons.Default.MonetizationOn,
            modifier = Modifier.weight(1f),
            color = if (uiState.team?.governance?.freeOnly == true) Color(0xFF2E7D32) else MaterialTheme.colorScheme.tertiary
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
private fun GovernanceStatusCard(team: ModelTeam?, onEditPolicy: () -> Unit) {
    val gov = team?.governance ?: TeamGovernanceSettings()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (gov.freeOnly) Color(0xFF1B5E20).copy(alpha = 0.1f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (gov.freeOnly) Color(0xFF2E7D32).copy(alpha = 0.3f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (gov.freeOnly) Icons.Default.CheckCircle else Icons.Default.Shield,
                        null,
                        tint = if (gov.freeOnly) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (gov.freeOnly) "سياسة الاستخدام المجاني (Free Only): نشطة ومفروضة" else "سياسة الاستهلاك المقيد (Cost Guard)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (gov.freeOnly) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onEditPolicy, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (gov.freeOnly)
                    "كل النماذج داخل الفريق خاضعة لحساب المستخدم ولن يتم استخدام أي نموذج مدفوع أو التحويل التلقائي إليه تحت أي ظرف."
                else
                    "الحد الأقصى للميزانية: ${gov.maxTeamCostUsd}$ | الحد الأقصى للرموز: ${String.format("%,d", gov.maxTotalTokens)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PolicyPill("الحد: ${gov.maxApiRequests} طلب")
                PolicyPill("أقصى تزامن: ${gov.maxParallelRequests}")
                if (gov.autoFallbackToFree) PolicyPill("تبديل مجاني عند 429")
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            summary.role.labelAr.take(1),
                            modifier = Modifier.padding(6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(summary.memberName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(summary.role.labelAr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    color = if (summary.isFreeModel) Color(0xFF1B5E20).copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        if (summary.isFreeModel) "مجاني 0.00$" else "${String.format("%.3f", summary.costUsd)}$",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.isFreeModel) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Details Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("النموذج النشط", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(summary.modelName.ifBlank { summary.modelId }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
                Column {
                    Text("الطلبات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${summary.requestsCount} طلب", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
                Column {
                    Text("الرموز (Tokens)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(String.format("%,d", summary.totalTokens), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }

            if (summary.rateLimitHits > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("أخطاء تجاوز الحصة (429): ${summary.rateLimitHits} مرات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
                    if (record.isFree) "0.00$ (مجاني)" else "${String.format("%.4f", record.costUsd)}$",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (record.isFree) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
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
        Text("لا توجد سجلات استهلاك بعد", style = MaterialTheme.typography.bodyMedium)
        Text("سيتم تسجيل استهلاك النماذج والرموز فور بدء تشغيل الفريق.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}
