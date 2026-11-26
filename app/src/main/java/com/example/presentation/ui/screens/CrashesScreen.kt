package com.example.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.ui.platform.testTag
import com.example.R
import com.example.di.DependencyProvider
import com.example.domain.model.CrashReport
import com.example.domain.model.IntegrationType
import com.example.domain.model.Severity
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.CrashesViewModel
import com.example.ui.theme.VibeDesignTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashesScreen(
    projectId: String?,
    applicationId: String?,
    exceptionType: String? = null,
    appVersion: String? = null,
    severity: String? = null,
    osName: String? = null,
    filterTitle: String? = null,
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: CrashesViewModel = viewModel(
        key = "crashes_${projectId}_${applicationId}_${exceptionType}_${appVersion}_${severity}_${osName}_${filterTitle}"
    ) {
        CrashesViewModel(
            repository = DependencyProvider.provideCrashRepository(context),
            initialProjectId = projectId,
            initialAppId = applicationId,
            initialExceptionType = exceptionType,
            initialAppVersion = appVersion,
            initialSeverity = severity,
            initialOsName = osName,
            initialFilterTitle = filterTitle
        )
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (searchVisible) {
                        TextField(
                            value = uiState.filter.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text(stringResource(R.string.search_crashes_hint)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column {
                            Text(stringResource(R.string.crashes))
                            if (uiState.filter.filterTitle != null) {
                                Text(
                                    text = uiState.filter.filterTitle ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.crashes_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { navController.navigate(Screen.CrashWatcher) },
                        modifier = Modifier.testTag("crashes_open_watcher_button")
                    ) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.crash_watcher_title),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.crashes_filter))
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.crashes_refresh))
                    }
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(
                            if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(if (searchVisible) R.string.crashes_close_search else R.string.crashes_search)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Active filter chips
            val hasActiveFilter = uiState.filter.exceptionType != null ||
                    uiState.filter.appVersion != null ||
                    uiState.filter.severity != null ||
                    uiState.filter.osName != null ||
                    uiState.filter.searchQuery.isNotBlank()

            if (hasActiveFilter) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            Text(
                                stringResource(R.string.active_filters),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        uiState.filter.exceptionType?.let { exc ->
                            item {
                                FilterChip(
                                    selected = true,
                                    onClick = { viewModel.clearSpecificFilter("exception") },
                                    label = { Text(exc) },
                                    trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                        uiState.filter.appVersion?.let { ver ->
                            item {
                                FilterChip(
                                    selected = true,
                                    onClick = { viewModel.clearSpecificFilter("version") },
                                    label = { Text("v$ver") },
                                    trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                        uiState.filter.severity?.let { sev ->
                            item {
                                FilterChip(
                                    selected = true,
                                    onClick = { viewModel.clearSpecificFilter("severity") },
                                    label = { Text(severityLabel(sev)) },
                                    trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                        uiState.filter.osName?.let { os ->
                            item {
                                FilterChip(
                                    selected = true,
                                    onClick = { viewModel.clearSpecificFilter("os") },
                                    label = { Text(os) },
                                    trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                        uiState.filter.searchQuery.takeIf { it.isNotBlank() }?.let { query ->
                            item {
                                FilterChip(
                                    selected = true,
                                    onClick = { viewModel.clearSpecificFilter("search") },
                                    label = { Text(query) },
                                    trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                        item {
                            TextButton(onClick = { viewModel.clearFilters() }) {
                                Text(stringResource(R.string.clear_all))
                            }
                        }
                    }
                }
            }

            // Results count
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.crashes_results_count, uiState.filteredCrashes.size, uiState.crashes.size),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error != null) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Text(stringResource(R.string.crashes_load_error_title),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center)
                        Text(stringResource(R.string.crashes_load_error_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        FilledTonalButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.crashes_retry))
                        }
                    }
                }
            } else if (uiState.filteredCrashes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (hasActiveFilter) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.no_crashes_for_filter),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.no_crashes_filter_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            FilledTonalButton(onClick = { viewModel.clearFilters() }) {
                                Text(stringResource(R.string.clear_filters))
                            }
                        } else {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.crashes_not_connected_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("crashes_not_connected_title")
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.crashes_not_connected_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        navController.navigate(
                                            Screen.SetupGuide(
                                                projectId = projectId ?: "default",
                                                applicationId = applicationId ?: "default"
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("crashes_open_setup_guide_button")
                                ) {
                                    Icon(Icons.Default.Build, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.crashes_open_setup_guide))
                                }
                                Button(
                                    onClick = { navController.navigate(Screen.CrashWatcher) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("crashes_empty_open_watcher_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = VibeDesignTokens.Color.Cyan,
                                        contentColor = VibeDesignTokens.Color.OnCyan
                                    )
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.crash_watcher_title))
                                }
                            }
                        }
                        if (uiState.canLoadMore || uiState.loadMoreError != null) {
                            Spacer(Modifier.height(12.dp))
                            LoadMoreCrashesButton(
                                isLoading = uiState.isLoadingMore,
                                error = uiState.loadMoreError,
                                onClick = { viewModel.loadMore() }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.visibleCrashes, key = { it.id }) { crash ->
                        DetailedCrashCard(
                            crash = crash,
                            onClick = {
                                navController.navigate(Screen.CrashDetail(crash.id))
                            },
                            onAnalyzeClick = {
                                navController.navigate(Screen.FixAssistant(crash.id))
                            }
                        )
                    }
                    if (uiState.canLoadMore || uiState.loadMoreError != null) {
                        item(key = "crashes_load_more") {
                            LoadMoreCrashesButton(
                                isLoading = uiState.isLoadingMore,
                                error = uiState.loadMoreError,
                                onClick = { viewModel.loadMore() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        var exceptionType by rememberSaveable(uiState.filter.exceptionType) {
            mutableStateOf(uiState.filter.exceptionType.orEmpty())
        }
        var appVersion by rememberSaveable(uiState.filter.appVersion) {
            mutableStateOf(uiState.filter.appVersion.orEmpty())
        }
        var osName by rememberSaveable(uiState.filter.osName) {
            mutableStateOf(uiState.filter.osName.orEmpty())
        }
        var severity by rememberSaveable(uiState.filter.severity) {
            mutableStateOf(uiState.filter.severity)
        }
        val severityOptions = listOf("ALL", "CRITICAL", "HIGH", "MEDIUM", "LOW")

        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.crashes_filter_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = exceptionType,
                    onValueChange = { exceptionType = it },
                    label = { Text(stringResource(R.string.crashes_filter_exception)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = appVersion,
                    onValueChange = { appVersion = it },
                    label = { Text(stringResource(R.string.crashes_filter_version)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = osName,
                    onValueChange = { osName = it },
                    label = { Text(stringResource(R.string.crashes_filter_os)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.crashes_filter_severity),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(severityOptions) { option ->
                        val selected = if (option == "ALL") severity == null else severity == option
                        FilterChip(
                            selected = selected,
                            onClick = { severity = option.takeUnless { it == "ALL" } },
                            label = {
                                Text(
                                    if (option == "ALL") stringResource(R.string.crashes_filter_any)
                                    else severityLabel(option)
                                )
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = {
                            viewModel.clearFilters()
                            showFilters = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.crashes_filter_clear))
                    }
                    Button(
                        onClick = {
                            viewModel.applyFilters(exceptionType, appVersion, severity, osName)
                            showFilters = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.crashes_filter_apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadMoreCrashesButton(
    isLoading: Boolean,
    error: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
        FilledTonalButton(
            onClick = onClick,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.crashes_loading_more))
            } else {
                Text(
                    stringResource(
                        if (error != null) R.string.crashes_retry
                        else R.string.crashes_load_more
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailedCrashCard(
    crash: CrashReport,
    onAnalyzeClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val formattedDate = remember(crash.timestamp) {
        crash.timestamp.takeIf { it > 0L }?.let { dateFormat.format(Date(it)) }
    }
    val timestampLabel = formattedDate ?: stringResource(R.string.crashes_unavailable)
    val exceptionLabel = crash.exceptionType.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.crashes_exception_unavailable)
    val deviceLabel = crash.deviceModel.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.crashes_unavailable)
    val osLabel = crash.osVersion.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.crashes_unavailable)
    val versionLabel = crash.appVersion.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.crashes_unavailable)
    val severityAccent = when (crash.severity) {
        Severity.CRITICAL, Severity.HIGH -> VibeDesignTokens.Color.Error
        Severity.MEDIUM -> VibeDesignTokens.Color.Warning
        Severity.LOW -> VibeDesignTokens.Color.Success
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("crashes_detailed_card"),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, severityAccent.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Medium)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Micro)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = severityAccent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                        border = BorderStroke(1.dp, severityAccent.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = severityLabel(crash.severity.name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = exceptionLabel,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = timestampLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = crash.message.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.crashes_message_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = if (crash.message.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            ) {
                Text(
                    text = crash.source?.let { source ->
                        stringResource(R.string.crashes_source_label, crashSourceLabel(source))
                    } ?: stringResource(R.string.crashes_source_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("crashes_card_metadata"),
                horizontalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small),
                verticalArrangement = Arrangement.spacedBy(VibeDesignTokens.Spacing.Small)
            ) {
                Text(
                    text = stringResource(R.string.crashes_device_os, deviceLabel, osLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact)
                ) {
                    Text(
                        text = stringResource(R.string.crashes_version_value, versionLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (crash.isResolved) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact)
                    ) {
                        Text(
                            text = stringResource(R.string.crashes_resolved),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Button(
                onClick = onAnalyzeClick ?: onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("crash_card_analyze_gemini_${crash.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeDesignTokens.Color.Cyan,
                    contentColor = VibeDesignTokens.Color.OnCyan
                ),
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Control)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.crash_watcher_analyze_gemini),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


@Composable
private fun crashSourceLabel(source: IntegrationType?): String = when (source) {
    IntegrationType.FIREBASE_CRASHLYTICS -> stringResource(R.string.crashes_source_firebase_crashlytics)
    IntegrationType.SENTRY -> stringResource(R.string.crashes_source_sentry)
    IntegrationType.BUGSNAG -> stringResource(R.string.crashes_source_bugsnag)
    IntegrationType.WEBHOOK -> stringResource(R.string.crashes_source_webhook)
    IntegrationType.CUSTOM -> stringResource(R.string.crashes_source_custom)
    IntegrationType.INTERNAL_QA -> stringResource(R.string.crashes_source_internal_qa)
    IntegrationType.ON_DEVICE_WATCHER -> stringResource(R.string.crashes_source_on_device_watcher)
    null -> stringResource(R.string.crashes_source_unavailable)
}

@Composable
private fun severityLabel(value: String): String = when (value.uppercase(Locale.ROOT)) {
    "CRITICAL" -> stringResource(R.string.crashes_severity_critical)
    "HIGH" -> stringResource(R.string.crashes_severity_high)
    "MEDIUM" -> stringResource(R.string.crashes_severity_medium)
    "LOW" -> stringResource(R.string.crashes_severity_low)
    else -> value
}
