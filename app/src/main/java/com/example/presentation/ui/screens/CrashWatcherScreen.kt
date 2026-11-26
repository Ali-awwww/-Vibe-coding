package com.example.presentation.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.R
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.AppFilter
import com.example.presentation.viewmodel.CrashWatcherUiState
import com.example.presentation.viewmodel.CrashWatcherViewModel
import com.example.presentation.viewmodel.MonitoredAppItem
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens
import com.example.util.shizuku.ShizukuManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashWatcherScreen(
    navController: NavController,
    viewModel: CrashWatcherViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val adbCopiedMessage = stringResource(R.string.crash_watcher_adb_command_copied)

    LaunchedEffect(Unit) {
        viewModel.loadInstalledApps(context)
    }

    LaunchedEffect(uiState.userMessage) {
        val msg = uiState.userMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserMessage()
        }
    }

    Scaffold(
        containerColor = VibeDesignTokens.Color.SpaceBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.crash_watcher_title),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.crash_watcher_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMono,
                            color = VibeDesignTokens.Color.Cyan
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("crash_watcher_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.crash_watcher_back),
                            tint = VibeDesignTokens.Color.Cyan
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshShizukuStatus(context) },
                        modifier = Modifier.testTag("crash_watcher_refresh_shizuku_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.crash_watcher_shizuku_check_status),
                            tint = VibeDesignTokens.Color.Cyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VibeDesignTokens.Color.SpacePanel,
                    titleContentColor = VibeDesignTokens.Color.TextPrimary
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("crash_watcher_content"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Shizuku Status & Setup Guide Card
            item {
                ShizukuStatusSection(
                    uiState = uiState,
                    onRequestPermission = { viewModel.requestShizukuPermission() },
                    onCheckStatus = { viewModel.refreshShizukuStatus(context) },
                    onOpenPlayStore = { ShizukuManager.openPlayStore(context) },
                    onOpenGitHub = { ShizukuManager.openGitHub(context) },
                    onCopyCommand = { cmd ->
                        clipboardManager.setText(AnnotatedString(cmd))
                        scope.launch {
                            snackbarHostState.showSnackbar(adbCopiedMessage)
                        }
                    }
                )
            }

            // 2. Monitoring Status & Control Panel
            item {
                MonitoringControlPanel(
                    uiState = uiState,
                    onStartMonitoring = { viewModel.startMonitoring(context) },
                    onStopMonitoring = { viewModel.stopMonitoring(context) }
                )
            }

            // 3. Last Captured Crash Alert Card (if any crash detected during session)
            uiState.lastCapturedCrash?.let { capturedCrash ->
                item {
                    LastCapturedCrashBanner(
                        crash = capturedCrash,
                        onAnalyze = {
                            navController.navigate(Screen.FixAssistant(capturedCrash.id))
                        },
                        onViewDetail = {
                            navController.navigate(Screen.CrashDetail(capturedCrash.id))
                        }
                    )
                }
            }

            // 4. App Picker Header & Filter Controls
            item {
                AppPickerHeader(
                    uiState = uiState,
                    onQueryChange = viewModel::setSearchQuery,
                    onFilterChange = viewModel::setFilter,
                    onSelectAll = viewModel::selectAll
                )
            }

            // 5. App List Items
            if (uiState.isLoadingApps) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = VibeDesignTokens.Color.Cyan)
                    }
                }
            } else if (uiState.appsLoadError) {
                item {
                    CrashWatcherAppsLoadErrorCard(
                        onRetry = { viewModel.loadInstalledApps(context) }
                    )
                }
            } else if (uiState.filteredApps.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
                        border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.crash_watcher_no_apps_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = VibeDesignTokens.Color.TextMuted
                            )
                        }
                    }
                }
            } else {
                items(uiState.filteredApps, key = { it.packageName }) { appItem ->
                    AppPickerRow(
                        item = appItem,
                        onToggle = { viewModel.toggleAppSelection(appItem.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun CrashWatcherAppsLoadErrorCard(onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("crash_watcher_apps_error_card"),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        border = BorderStroke(1.dp, VibeDesignTokens.Color.Error.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = VibeDesignTokens.Color.Error,
                modifier = Modifier.size(30.dp)
            )
            Text(
                text = stringResource(R.string.crash_watcher_apps_load_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = VibeDesignTokens.Color.TextPrimary,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.testTag("crash_watcher_apps_retry_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.crash_watcher_retry))
            }
        }
    }
}

@Composable
private fun ShizukuStatusSection(
    uiState: CrashWatcherUiState,
    onRequestPermission: () -> Unit,
    onCheckStatus: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onOpenGitHub: () -> Unit,
    onCopyCommand: (String) -> Unit
) {
    var expandedGuide by remember { mutableStateOf(!uiState.isShizukuAuthorized) }

    val statusColor = when {
        uiState.isShizukuAuthorized -> VibeDesignTokens.Color.Success
        uiState.isShizukuRunning -> VibeDesignTokens.Color.Warning
        else -> VibeDesignTokens.Color.Error
    }

    val statusLabel = when {
        uiState.isShizukuAuthorized -> stringResource(R.string.crash_watcher_shizuku_status_ok)
        uiState.isShizukuRunning -> stringResource(R.string.crash_watcher_shizuku_status_unauthorized)
        else -> stringResource(R.string.crash_watcher_shizuku_status_dead)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("crash_watcher_shizuku_card"),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(VibeDesignTokens.Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.TextPrimary
                    )
                }

                IconButton(onClick = { expandedGuide = !expandedGuide }) {
                    Icon(
                        imageVector = if (expandedGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.crash_watcher_setup_guide),
                        tint = VibeDesignTokens.Color.TextMuted
                    )
                }
            }

            AnimatedVisibility(visible = expandedGuide || !uiState.isShizukuAuthorized) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = VibeDesignTokens.Color.Cyan.copy(alpha = 0.15f))

                    Text(
                        text = stringResource(R.string.crash_watcher_shizuku_guide_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeDesignTokens.Color.TextMuted
                    )

                    // Step 1: Install
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpaceBackground),
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                        border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.crash_watcher_shizuku_step1_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = VibeDesignTokens.Color.Cyan
                            )
                            Text(
                                text = stringResource(R.string.crash_watcher_shizuku_step1_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = VibeDesignTokens.Color.TextPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onOpenPlayStore,
                                    colors = ButtonDefaults.buttonColors(containerColor = VibeDesignTokens.Color.Cyan),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Shop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Google Play", fontSize = 12.sp, color = VibeDesignTokens.Color.OnCyan)
                                }
                                OutlinedButton(
                                    onClick = onOpenGitHub,
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("GitHub", fontSize = 12.sp, color = VibeDesignTokens.Color.Cyan)
                                }
                            }
                        }
                    }

                    // Step 2: Activate
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpaceBackground),
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                        border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.crash_watcher_shizuku_step2_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = VibeDesignTokens.Color.Cyan
                            )

                            // Option A
                            Text(
                                text = stringResource(R.string.crash_watcher_shizuku_wireless_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = VibeDesignTokens.Color.Success
                            )
                            Text(
                                text = stringResource(R.string.crash_watcher_shizuku_wireless_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = VibeDesignTokens.Color.TextMuted
                            )

                            Spacer(Modifier.height(4.dp))

                            // Option B
                            Text(
                                text = stringResource(R.string.crash_watcher_shizuku_adb_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = VibeDesignTokens.Color.Warning
                            )
                            Text(
                                text = stringResource(R.string.crash_watcher_shizuku_adb_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = VibeDesignTokens.Color.TextMuted
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ShizukuManager.ADB_START_COMMAND,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMono,
                                        color = VibeDesignTokens.Color.Cyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onCopyCommand(ShizukuManager.ADB_START_COMMAND) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = stringResource(R.string.crash_watcher_copy),
                                            tint = VibeDesignTokens.Color.Cyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Step 3: Request Permission
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRequestPermission,
                            enabled = uiState.isShizukuRunning && !uiState.isShizukuAuthorized,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("crash_watcher_request_permission_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VibeDesignTokens.Color.Cyan,
                                contentColor = VibeDesignTokens.Color.OnCyan
                            )
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.crash_watcher_shizuku_request_permission), fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onCheckStatus,
                            modifier = Modifier.testTag("crash_watcher_check_status_button"),
                            border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.crash_watcher_shizuku_check_status), color = VibeDesignTokens.Color.Cyan)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitoringControlPanel(
    uiState: CrashWatcherUiState,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("crash_watcher_control_panel"),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        border = BorderStroke(
            1.dp,
            if (uiState.isMonitoring) VibeDesignTokens.Color.Success.copy(alpha = 0.5f)
            else VibeDesignTokens.Color.Cyan.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(VibeDesignTokens.Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isMonitoring) Icons.Default.Radar else Icons.Default.SensorsOff,
                        contentDescription = null,
                        tint = if (uiState.isMonitoring) VibeDesignTokens.Color.Success else VibeDesignTokens.Color.TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = if (uiState.isMonitoring) stringResource(R.string.crash_watcher_status_active)
                            else stringResource(R.string.crash_watcher_status_idle),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VibeDesignTokens.Color.TextPrimary
                        )
                        if (uiState.isMonitoring) {
                            Text(
                                text = stringResource(
                                    R.string.crash_watcher_monitoring_summary,
                                    uiState.elapsedDurationFormatted,
                                    uiState.capturedCrashesCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetBrainsMono,
                                color = VibeDesignTokens.Color.Success
                            )
                        }
                    }
                }

                Surface(
                    color = (if (uiState.isMonitoring) VibeDesignTokens.Color.Success else VibeDesignTokens.Color.TextMuted).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact)
                ) {
                    Text(
                        text = stringResource(R.string.crash_watcher_apps_selected, uiState.selectedPackages.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.isMonitoring) VibeDesignTokens.Color.Success else VibeDesignTokens.Color.TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (uiState.isMonitoring) {
                Button(
                    onClick = onStopMonitoring,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("crash_watcher_stop_button"),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibeDesignTokens.Color.Error,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.crash_watcher_stop_monitoring), fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onStartMonitoring,
                    enabled = uiState.selectedPackages.isNotEmpty() && uiState.isShizukuAuthorized,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("crash_watcher_start_button"),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibeDesignTokens.Color.Cyan,
                        contentColor = VibeDesignTokens.Color.OnCyan
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.crash_watcher_start_monitoring), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LastCapturedCrashBanner(
    crash: com.example.domain.model.CrashReport,
    onAnalyze: () -> Unit,
    onViewDetail: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("crash_watcher_last_captured_card"),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        border = BorderStroke(1.dp, VibeDesignTokens.Color.Error.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(VibeDesignTokens.Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = VibeDesignTokens.Color.Error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.crash_watcher_new_crash_captured),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.Error
                )
            }

            Text(
                text = "${crash.exceptionType}: ${crash.message}",
                style = MaterialTheme.typography.bodySmall,
                color = VibeDesignTokens.Color.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAnalyze,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("crash_watcher_captured_analyze_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibeDesignTokens.Color.Cyan,
                        contentColor = VibeDesignTokens.Color.OnCyan
                    )
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.crash_watcher_analyze_gemini), fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onViewDetail,
                    modifier = Modifier.testTag("crash_watcher_captured_details_button"),
                    border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.5f))
                ) {
                    Text(stringResource(R.string.crash_watcher_details), color = VibeDesignTokens.Color.Cyan)
                }
            }
        }
    }
}

@Composable
private fun AppPickerHeader(
    uiState: CrashWatcherUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (AppFilter) -> Unit,
    onSelectAll: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.crash_watcher_select_apps),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = VibeDesignTokens.Color.TextPrimary
        )

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.crash_watcher_search_apps)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VibeDesignTokens.Color.Cyan) },
            trailingIcon = {
                if (uiState.searchQuery.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.crash_watcher_clear))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("crash_watcher_search_input"),
            shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VibeDesignTokens.Color.Cyan,
                unfocusedBorderColor = VibeDesignTokens.Color.Cyan.copy(alpha = 0.3f),
                focusedTextColor = VibeDesignTokens.Color.TextPrimary,
                unfocusedTextColor = VibeDesignTokens.Color.TextPrimary
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = uiState.activeFilter == AppFilter.USER_ONLY,
                        onClick = { onFilterChange(AppFilter.USER_ONLY) },
                        label = { Text(stringResource(R.string.crash_watcher_filter_user)) }
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.activeFilter == AppFilter.ALL,
                        onClick = { onFilterChange(AppFilter.ALL) },
                        label = { Text(stringResource(R.string.crash_watcher_filter_all)) }
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.activeFilter == AppFilter.SYSTEM_ONLY,
                        onClick = { onFilterChange(AppFilter.SYSTEM_ONLY) },
                        label = { Text(stringResource(R.string.crash_watcher_filter_system)) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onSelectAll(true) }) {
                    Text(stringResource(R.string.crash_watcher_select_all), fontSize = 12.sp, color = VibeDesignTokens.Color.Cyan)
                }
                TextButton(onClick = { onSelectAll(false) }) {
                    Text(stringResource(R.string.crash_watcher_deselect_all), fontSize = 12.sp, color = VibeDesignTokens.Color.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun AppPickerRow(
    item: MonitoredAppItem,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("app_item_${item.packageName}"),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
        border = BorderStroke(
            1.dp,
            if (item.isSelected) VibeDesignTokens.Color.Cyan
            else VibeDesignTokens.Color.Cyan.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconImage(
                packageName = item.packageName,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = VibeDesignTokens.Color.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Checkbox(
                checked = item.isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = VibeDesignTokens.Color.Cyan,
                    checkmarkColor = VibeDesignTokens.Color.OnCyan
                )
            )
        }
    }
}

@Composable
private fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToBitmap(drawable)
        }.getOrNull()
    }
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Surface(
            color = VibeDesignTokens.Color.Cyan.copy(alpha = 0.12f),
            shape = RoundedCornerShape(8.dp),
            modifier = modifier
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = null,
                    tint = VibeDesignTokens.Color.Cyan,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
