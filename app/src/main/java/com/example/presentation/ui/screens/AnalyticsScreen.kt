package com.example.presentation.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.R
import com.example.domain.model.*
import com.example.presentation.navigation.Screen
import com.example.presentation.ui.components.ComparisonCard
import com.example.presentation.ui.components.HorizontalDistributionList
import com.example.presentation.ui.components.OsSplitComparisonCard
import com.example.presentation.ui.components.TimeSeriesBarChart
import com.example.presentation.viewmodel.AnalyticsUiState
import com.example.presentation.viewmodel.AnalyticsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    navController: NavController,
    viewModel: AnalyticsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var timeSeriesGranularity by remember { mutableStateOf("daily") } // "daily", "weekly", "monthly"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analytics_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Filter button
                    val hasFilters = uiState.filter.projectId != null ||
                            uiState.filter.applicationId != null ||
                            uiState.filter.platform != null ||
                            uiState.filter.version != null ||
                            uiState.filter.exceptionType != null ||
                            uiState.filter.severity != null

                    IconButton(onClick = { viewModel.openFilterSheet(true) }) {
                        BadgedBox(
                            badge = {
                                if (hasFilters) {
                                    Badge { Text("!") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filter_dialog_title))
                        }
                    }

                    // Export button
                    IconButton(onClick = { viewModel.openExportDialog(true) }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.export_report))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Quick Period Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(TimePeriod.values()) { period ->
                    val label = when (period) {
                        TimePeriod.TODAY -> stringResource(R.string.period_today)
                        TimePeriod.LAST_7_DAYS -> stringResource(R.string.period_7d)
                        TimePeriod.LAST_30_DAYS -> stringResource(R.string.period_30d)
                        TimePeriod.LAST_90_DAYS -> stringResource(R.string.period_90d)
                        TimePeriod.ALL_TIME -> stringResource(R.string.period_all)
                    }
                    FilterChip(
                        selected = uiState.filter.timePeriod == period,
                        onClick = { viewModel.setTimePeriod(period) },
                        label = { Text(label) }
                    )
                }
            }

            // Tabs Row
            PrimaryTabRow(
                selectedTabIndex = uiState.selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.setTab(0) },
                    text = { Text(stringResource(R.string.tab_charts)) },
                    icon = { Icon(Icons.Default.BarChart, null) }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.setTab(1) },
                    text = { Text(stringResource(R.string.tab_comparison)) },
                    icon = { Icon(Icons.Default.CompareArrows, null) }
                )
                Tab(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.setTab(2) },
                    text = { Text(stringResource(R.string.tab_versions)) },
                    icon = { Icon(Icons.Default.Layers, null) }
                )
                Tab(
                    selected = uiState.selectedTab == 3,
                    onClick = { viewModel.setTab(3) },
                    text = { Text(stringResource(R.string.tab_stability)) },
                    icon = { Icon(Icons.Default.Security, null) }
                )
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // KPI Overview Metric Cards (Always visible across tabs for quick context)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total Crashes
                        ElevatedCard(
                            onClick = {
                                navController.navigate(
                                    Screen.Crashes(
                                        projectId = uiState.filter.projectId,
                                        applicationId = uiState.filter.applicationId,
                                        filterTitle = "All Crashes in Period"
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    stringResource(R.string.total_crashes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${uiState.dashboardData.totalCrashes}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    stringResource(R.string.drill_down_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1
                                )
                            }
                        }

                        // Affected Users
                        ElevatedCard(
                            onClick = {
                                navController.navigate(
                                    Screen.Crashes(
                                        projectId = uiState.filter.projectId,
                                        applicationId = uiState.filter.applicationId,
                                        filterTitle = "Crashes by Users"
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    stringResource(R.string.affected_users_count),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${uiState.dashboardData.affectedUsersCount}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "مستخدم متأثر",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // Crash-Free Users %
                        ElevatedCard(
                            onClick = { viewModel.setTab(3) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    stringResource(R.string.crash_free_users),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                val pct = uiState.dashboardData.stabilityMetrics.crashFreeUsersPercentage
                                Text(
                                    String.format(Locale.US, "%.1f%%", pct),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    "مؤشر الاستقرار",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    // Empty state warning if no records exist for the selected filter
                    if (uiState.filteredCrashes.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.no_data_for_period),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.no_data_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { viewModel.clearAllFilters() }) {
                                    Text(stringResource(R.string.clear_filters))
                                }
                            }
                        }
                    }

                    // Tab Content Switching
                    when (uiState.selectedTab) {
                        0 -> ChartsAndBreakdownTab(
                            uiState = uiState,
                            navController = navController,
                            timeSeriesGranularity = timeSeriesGranularity,
                            onGranularityChange = { timeSeriesGranularity = it }
                        )
                        1 -> TemporalComparisonTab(
                            uiState = uiState,
                            navController = navController
                        )
                        2 -> VersionComparisonTab(
                            uiState = uiState,
                            viewModel = viewModel,
                            navController = navController
                        )
                        3 -> StabilityIndicatorsTab(
                            uiState = uiState
                        )
                    }
                }
            }
        }
    }

    // Filter BottomSheet
    if (uiState.isFilterSheetOpen) {
        AnalyticsFilterModalSheet(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { viewModel.openFilterSheet(false) }
        )
    }

    // Export Dialog
    if (uiState.isExportDialogOpen) {
        ExportReportDialog(
            context = context,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { viewModel.openExportDialog(false) }
        )
    }
}

@Composable
fun ChartsAndBreakdownTab(
    uiState: AnalyticsUiState,
    navController: NavController,
    timeSeriesGranularity: String,
    onGranularityChange: (String) -> Unit
) {
    val data = uiState.dashboardData

    // 1. Time Series Chart (Daily / Weekly / Monthly)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (timeSeriesGranularity) {
                        "weekly" -> stringResource(R.string.weekly_crashes)
                        "monthly" -> stringResource(R.string.monthly_crashes)
                        else -> stringResource(R.string.daily_crashes)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Granularity selector
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = { onGranularityChange("daily") },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (timeSeriesGranularity == "daily") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("يومي", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(
                        onClick = { onGranularityChange("weekly") },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (timeSeriesGranularity == "weekly") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("أسبوعي", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(
                        onClick = { onGranularityChange("monthly") },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (timeSeriesGranularity == "monthly") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("شهري", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            val points = when (timeSeriesGranularity) {
                "weekly" -> data.weeklyTimeSeries
                "monthly" -> data.monthlyTimeSeries
                else -> data.dailyTimeSeries
            }

            TimeSeriesBarChart(
                points = points,
                onPointClick = { point ->
                    navController.navigate(
                        Screen.Crashes(
                            projectId = uiState.filter.projectId,
                            applicationId = uiState.filter.applicationId,
                            filterTitle = "Crashes for ${point.label}"
                        )
                    )
                }
            )

            Text(
                stringResource(R.string.drill_down_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // 2. OS Breakdown (Android vs iOS)
    val androidCount = data.osDistribution.find { it.name.contains("Android", true) }?.count ?: 0
    val iosCount = data.osDistribution.find { it.name.contains("iOS", true) }?.count ?: 0
    val otherCount = data.osDistribution.find { it.name.contains("Other", true) }?.count ?: 0

    OsSplitComparisonCard(
        androidCount = androidCount,
        iosCount = iosCount,
        otherCount = otherCount,
        onAndroidClick = {
            navController.navigate(
                Screen.Crashes(
                    projectId = uiState.filter.projectId,
                    applicationId = uiState.filter.applicationId,
                    osName = "Android",
                    filterTitle = "Android Crashes"
                )
            )
        },
        onIosClick = {
            navController.navigate(
                Screen.Crashes(
                    projectId = uiState.filter.projectId,
                    applicationId = uiState.filter.applicationId,
                    osName = "iOS",
                    filterTitle = "iOS Crashes"
                )
            )
        }
    )

    // 3. Error Distribution by Exception Type
    if (data.exceptionDistribution.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.exception_distribution),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDistributionList(
                    items = data.exceptionDistribution,
                    barColor = MaterialTheme.colorScheme.primary,
                    onItemClick = { item ->
                        navController.navigate(
                            Screen.Crashes(
                                projectId = uiState.filter.projectId,
                                applicationId = uiState.filter.applicationId,
                                exceptionType = item.name,
                                filterTitle = item.name
                            )
                        )
                    }
                )
            }
        }
    }

    // 4. Crashes by App Version
    if (data.versionDistribution.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.version_distribution),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDistributionList(
                    items = data.versionDistribution,
                    barColor = MaterialTheme.colorScheme.tertiary,
                    onItemClick = { item ->
                        val cleanVersion = item.name.removePrefix("v")
                        navController.navigate(
                            Screen.Crashes(
                                projectId = uiState.filter.projectId,
                                applicationId = uiState.filter.applicationId,
                                appVersion = cleanVersion,
                                filterTitle = "Version $cleanVersion"
                            )
                        )
                    }
                )
            }
        }
    }

    // 5. Crashes by Device Model
    if (data.deviceDistribution.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.device_distribution),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDistributionList(
                    items = data.deviceDistribution,
                    barColor = MaterialTheme.colorScheme.secondary,
                    onItemClick = { item ->
                        navController.navigate(
                            Screen.Crashes(
                                projectId = uiState.filter.projectId,
                                applicationId = uiState.filter.applicationId,
                                filterTitle = "Device: ${item.name}"
                            )
                        )
                    }
                )
            }
        }
    }

    // 6. Crashes by OS Version
    if (data.osVersionDistribution.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.os_version_distribution),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDistributionList(
                    items = data.osVersionDistribution,
                    barColor = MaterialTheme.colorScheme.outline,
                    onItemClick = { item ->
                        navController.navigate(
                            Screen.Crashes(
                                projectId = uiState.filter.projectId,
                                applicationId = uiState.filter.applicationId,
                                osName = item.name,
                                filterTitle = "OS: ${item.name}"
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun TemporalComparisonTab(
    uiState: AnalyticsUiState,
    navController: NavController
) {
    val data = uiState.dashboardData

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.tab_comparison),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Today vs Yesterday
        ComparisonCard(
            title = stringResource(R.string.today_vs_yesterday),
            comparison = data.todayVsYesterday,
            onClick = {
                navController.navigate(
                    Screen.Crashes(
                        projectId = uiState.filter.projectId,
                        applicationId = uiState.filter.applicationId,
                        filterTitle = "Today vs Yesterday"
                    )
                )
            }
        )

        // Week vs Previous Week
        ComparisonCard(
            title = stringResource(R.string.week_vs_last_week),
            comparison = data.weekVsPreviousWeek,
            onClick = {
                navController.navigate(
                    Screen.Crashes(
                        projectId = uiState.filter.projectId,
                        applicationId = uiState.filter.applicationId,
                        filterTitle = "Week Comparison"
                    )
                )
            }
        )

        // Month vs Previous Month
        ComparisonCard(
            title = stringResource(R.string.month_vs_last_month),
            comparison = data.monthVsPreviousMonth,
            onClick = {
                navController.navigate(
                    Screen.Crashes(
                        projectId = uiState.filter.projectId,
                        applicationId = uiState.filter.applicationId,
                        filterTitle = "Month Comparison"
                    )
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionComparisonTab(
    uiState: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    navController: NavController
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.version_comparison),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Version selection row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.version1,
                onValueChange = { viewModel.setVersion1(it) },
                label = { Text(stringResource(R.string.version_a)) },
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = uiState.version2,
                onValueChange = { viewModel.setVersion2(it) },
                label = { Text(stringResource(R.string.version_b)) },
                modifier = Modifier.weight(1f)
            )
        }

        if (uiState.availableVersions.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Text(
                        "إصدارات متاحة:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(uiState.availableVersions) { ver ->
                    SuggestionChip(
                        onClick = {
                            if (uiState.version1.isBlank() || uiState.version1 == ver) {
                                viewModel.setVersion1(ver)
                            } else {
                                viewModel.setVersion2(ver)
                            }
                        },
                        label = { Text("v$ver") }
                    )
                }
            }
        }

        val s1 = uiState.version1Stats
        val s2 = uiState.version2Stats

        if (s1 != null && s2 != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Header
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("المعيار", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold)
                        Text("v${s1.version}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        Text("v${s2.version}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()

                    // Crash Count Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate(
                                    Screen.Crashes(
                                        appVersion = s2.version,
                                        filterTitle = "Crashes for v${s2.version}"
                                    )
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.total_crashes), modifier = Modifier.weight(1.2f))
                        Text("${s1.crashCount}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text("${s2.crashCount}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    }

                    // Affected Users Row
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.affected_users_count), modifier = Modifier.weight(1.2f))
                        Text("${s1.affectedUsers}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text("${s2.affectedUsers}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    }

                    // New Errors
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("أنواع أخطاء جديدة", modifier = Modifier.weight(1.2f))
                        Text("${s1.newErrorsCount}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text("${s2.newErrorsCount}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    }

                    // Rate Change
                    val isRateGood = s2.crashRateChange <= 0f
                    val rateColor = if (isRateGood) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    val sign = if (s2.crashRateChange > 0) "+" else ""
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("تغير معدل الانهيار", modifier = Modifier.weight(1.2f))
                        Text("-", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text(
                            "$sign${String.format(Locale.US, "%.1f%%", s2.crashRateChange)}",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = rateColor
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("أشهر أخطاء الإصدار (v${s2.version}):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    if (s2.topErrors.isEmpty()) {
                        Text("لا توجد أخطاء مسجلة لهذا الإصدار", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        s2.topErrors.forEach { err ->
                            Text("• $err", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StabilityIndicatorsTab(
    uiState: AnalyticsUiState
) {
    val metrics = uiState.dashboardData.stabilityMetrics

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.tab_stability),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // 1. Crash-Free Users Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.crash_free_users),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = String.format(Locale.US, "%.1f%%", metrics.crashFreeUsersPercentage),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )

                // Progress Bar
                LinearProgressIndicator(
                    progress = { (metrics.crashFreeUsersPercentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF2E7D32),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "إجمالي المستخدمين المقدر: ${metrics.estimatedUsersWithTelemetry}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "المتأثرون: ${metrics.affectedUsers}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. Methodology Explanation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("طريقة الحساب والفترة الزمنية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = metrics.methodologyNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 3. Crash-Free Sessions & Live Telemetry Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SyncDisabled, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(stringResource(R.string.crash_free_sessions), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = metrics.dataSourceNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsFilterModalSheet(
    uiState: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.filter_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { viewModel.clearAllFilters() }) {
                    Text(stringResource(R.string.clear_all))
                }
            }

            // Platform Filter
            Text(stringResource(R.string.platform), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.filter.platform == null,
                    onClick = { viewModel.setPlatform(null) },
                    label = { Text(stringResource(R.string.all_platforms)) }
                )
                FilterChip(
                    selected = uiState.filter.platform == "Android",
                    onClick = { viewModel.setPlatform("Android") },
                    label = { Text("Android") }
                )
                FilterChip(
                    selected = uiState.filter.platform == "iOS",
                    onClick = { viewModel.setPlatform("iOS") },
                    label = { Text("iOS") }
                )
            }

            // Projects Filter
            if (uiState.projects.isNotEmpty()) {
                Text(stringResource(R.string.projects), fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = uiState.filter.projectId == null,
                            onClick = { viewModel.setProject(null) },
                            label = { Text("جميع المشاريع") }
                        )
                    }
                    items(uiState.projects) { proj ->
                        FilterChip(
                            selected = uiState.filter.projectId == proj.id,
                            onClick = { viewModel.setProject(proj.id) },
                            label = { Text(proj.name) }
                        )
                    }
                }
            }

            // Applications Filter
            if (uiState.applications.isNotEmpty()) {
                Text(stringResource(R.string.select_application), fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = uiState.filter.applicationId == null,
                            onClick = { viewModel.setApplication(null) },
                            label = { Text("جميع التطبيقات") }
                        )
                    }
                    items(uiState.applications) { app ->
                        FilterChip(
                            selected = uiState.filter.applicationId == app.id,
                            onClick = { viewModel.setApplication(app.id) },
                            label = { Text(app.name) }
                        )
                    }
                }
            }

            // Severity Filter
            Text(stringResource(R.string.severity_level), fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = uiState.filter.severity == null,
                        onClick = { viewModel.setSeverityFilter(null) },
                        label = { Text(stringResource(R.string.all_severities)) }
                    )
                }
                items(Severity.values()) { sev ->
                    FilterChip(
                        selected = uiState.filter.severity == sev,
                        onClick = { viewModel.setSeverityFilter(sev) },
                        label = { Text(sev.name) }
                    )
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.apply_filter))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ExportReportDialog(
    context: Context,
    uiState: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_report), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("اختر الصيغة لتصدير تقارير وبيانات الأعطال الفعلية ومشاركتها:")

                FilledTonalButton(
                    onClick = { viewModel.exportPdf(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.export_pdf))
                }

                FilledTonalButton(
                    onClick = { viewModel.exportCsv(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.TableChart, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.export_csv))
                }

                FilledTonalButton(
                    onClick = { viewModel.exportJson(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Code, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.export_json))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
