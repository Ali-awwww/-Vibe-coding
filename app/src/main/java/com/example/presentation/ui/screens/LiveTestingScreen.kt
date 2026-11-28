package com.example.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.R
import com.example.domain.model.*
import androidx.compose.ui.res.stringResource
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.LiveTestingViewModel
import com.example.util.shizuku.ShizukuManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTestingScreen(
    navController: NavController,
    viewModel: LiveTestingViewModel
) {
    val projects by viewModel.projects.collectAsState()
    val applications by viewModel.applications.collectAsState()
    val selectedProjectId by viewModel.selectedProjectId.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val operationErrorResId by viewModel.operationErrorResId.collectAsState()
    val operationErrorMessage = operationErrorResId?.let { stringResource(it) } ?: operationError
    val isAgentTransportAvailable = viewModel.isAgentTransportAvailable
    val snackbarHostState = remember { SnackbarHostState() }
    val sessions by viewModel.sessions.collectAsState()

    var showStartDialog by remember { mutableStateOf<Application?>(null) }

    LaunchedEffect(operationErrorMessage) {
        operationErrorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOperationError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.live_testing)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.live_testing_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (!isAgentTransportAvailable) {
                AgentTransportUnavailableNotice(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
            } else {
                LiveShizukuTransportBanner(
                    onOpenCrashWatcher = { navController.navigate(Screen.CrashWatcher) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
            }

            // Project Selector
            Text(
                stringResource(R.string.select_project),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            ScrollableTabRow(
                selectedTabIndex = projects.indexOfFirst { it.id == selectedProjectId }.coerceAtLeast(0),
                edgePadding = 0.dp,
                divider = {}
            ) {
                projects.forEach { project ->
                    Tab(
                        selected = project.id == selectedProjectId,
                        onClick = { viewModel.selectProject(project.id) },
                        text = { Text(project.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (applications.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.AppRegistration,
                    title = stringResource(R.string.live_testing_no_apps_title),
                    description = stringResource(R.string.live_testing_no_apps_description)
                )
            } else {
                Text(
                    stringResource(R.string.live_testing_available_apps),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(applications) { app ->
                        ApplicationCard(
                            application = app,
                            navController = navController,
                            onStartTesting = { showStartDialog = app },
                            onConnectAgent = { viewModel.connectAgent(app.id) },
                            agentTransportAvailable = isAgentTransportAvailable
                        )
                    }

                    if (sessions.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                stringResource(R.string.live_testing_previous_sessions),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        items(sessions) { session ->
                            SessionHistoryItem(
                                session = session,
                                onClick = { navController.navigate(Screen.LiveSession(session.id)) },
                                agentTransportAvailable = isAgentTransportAvailable
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStartDialog != null) {
        StartTestingDialog(
            application = showStartDialog!!,
            onDismiss = { showStartDialog = null },
            onConfirm = { version, buildId ->
                if (isAgentTransportAvailable) {
                    viewModel.startSession(showStartDialog!!, version, buildId)
                }
                showStartDialog = null
            },
            agentTransportAvailable = isAgentTransportAvailable
        )
    }

    val navigateToSessionId by viewModel.navigateToSessionId.collectAsState()
    LaunchedEffect(navigateToSessionId) {
        navigateToSessionId?.let { sessionId ->
            if (isAgentTransportAvailable) {
                viewModel.clearSessionNavigation()
                navController.navigate(Screen.LiveSession(sessionId))
            }
        }
    }
}

@Composable
fun LiveShizukuTransportBanner(
    onOpenCrashWatcher: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = remember { ShizukuManager.isShizukuRunning() }
    val hasPermission = remember { ShizukuManager.checkPermission() }
    val isReady = isRunning && hasPermission
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isReady) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = if (isReady) Icons.Default.VerifiedUser else Icons.Default.SettingsInputAntenna,
                    contentDescription = null,
                    tint = if (isReady) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.live_testing_shizuku_ready),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            isReady -> stringResource(R.string.crash_watcher_shizuku_status_ok)
                            isRunning -> stringResource(R.string.crash_watcher_shizuku_status_unauthorized)
                            else -> stringResource(R.string.crash_watcher_shizuku_status_dead)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (!isReady) {
                OutlinedButton(
                    onClick = onOpenCrashWatcher,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("live_testing_open_watcher_button")
                ) {
                    Icon(Icons.Default.Build, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.live_testing_open_crash_watcher))
                }
            }
        }
    }
}


@Composable
fun AgentTransportUnavailableNotice(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.live_testing_agent_unavailable_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.live_testing_agent_unavailable_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun ApplicationCard(
    application: Application,
    navController: NavController,
    onStartTesting: () -> Unit,
    onConnectAgent: () -> Unit,
    agentTransportAvailable: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        application.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        application.packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                StatusBadge(
                    status = application.platform,
                    color = if (application.platform == "Android") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SettingsInputComponent,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.live_testing_sdk_status, sdkSetupStatusLabel(application.sdkSetupStatus)))
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (agentTransportAvailable && application.agentConnectionStatus == AgentConnectionStatus.ONLINE)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (agentTransportAvailable) stringResource(R.string.live_testing_agent_status, agentConnectionStatusLabel(application.agentConnectionStatus))
                    else stringResource(R.string.live_testing_agent_unavailable_status),
                    modifier = Modifier.testTag("live_testing_agent_status")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { navController.navigate(Screen.SetupGuide(application.projectId, application.id)) }) {
                    Icon(Icons.Default.Build, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.live_testing_setup_sdk))
                }

                if (agentTransportAvailable && application.agentConnectionStatus == AgentConnectionStatus.OFFLINE) {
                    TextButton(
                        onClick = onConnectAgent,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("live_testing_connect_agent_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.live_testing_connect_agent))
                    }
                }
                
                Button(
                    onClick = onStartTesting,
                    enabled = agentTransportAvailable && application.agentConnectionStatus == AgentConnectionStatus.ONLINE,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("live_testing_start_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.live_testing_start_testing))
                }
            }
        }
    }
}

@Composable
fun SessionHistoryItem(
    session: TestSession,
    onClick: () -> Unit,
    agentTransportAvailable: Boolean = false
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (!agentTransportAvailable) Icons.Default.History else when(session.status) {
                        TestSessionStatus.COMPLETED -> Icons.Default.CheckCircle
                        TestSessionStatus.CRASH_DETECTED -> Icons.Default.Error
                        else -> Icons.Default.History
                    },
                    contentDescription = null,
                    tint = if (!agentTransportAvailable) MaterialTheme.colorScheme.onSurfaceVariant else when(session.status) {
                        TestSessionStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        TestSessionStatus.CRASH_DETECTED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.live_testing_session_build, session.buildId, session.version), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(dateFormat.format(Date(session.startTime)), style = MaterialTheme.typography.bodySmall)
            }

            StatusBadge(
                status = if (agentTransportAvailable) session.status.name
                    else stringResource(R.string.live_testing_local_record_unverified),
                color = if (agentTransportAvailable) {
                    when(session.status) {
                        TestSessionStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
                        TestSessionStatus.CRASH_DETECTED -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                } else MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun StartTestingDialog(
    application: Application,
    onDismiss: () -> Unit,
    onConfirm: (version: String, buildId: String) -> Unit,
    agentTransportAvailable: Boolean = false
) {
    var version by remember { mutableStateOf("1.0.0") }
    var buildId by remember { mutableStateOf("B${System.currentTimeMillis().toString().takeLast(6)}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.live_testing_start_session_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.live_testing_application_label, application.name))
                
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text(stringResource(R.string.live_testing_version_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = buildId,
                    onValueChange = { buildId = it },
                    label = { Text(stringResource(R.string.live_testing_build_id_label)) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (!agentTransportAvailable) {
                    Text(
                        stringResource(R.string.live_testing_agent_unavailable_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (agentTransportAvailable && version.isNotBlank() && buildId.isNotBlank()) {
                        onConfirm(version, buildId)
                    }
                },
                enabled = agentTransportAvailable && version.isNotBlank() && buildId.isNotBlank(),
                modifier = Modifier.testTag("start_testing_confirm_button")
            ) {
                Text(stringResource(R.string.live_testing_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.live_testing_cancel))
            }
        }
    )
}

@Composable
private fun sdkSetupStatusLabel(status: SdkSetupStatus): String = when (status) {
    SdkSetupStatus.NOT_CONFIGURED -> stringResource(R.string.live_testing_sdk_not_configured)
    SdkSetupStatus.PENDING -> stringResource(R.string.live_testing_sdk_pending)
    SdkSetupStatus.CONFIGURED -> stringResource(R.string.live_testing_sdk_configured)
    SdkSetupStatus.ERROR -> stringResource(R.string.live_testing_sdk_error)
}

@Composable
private fun agentConnectionStatusLabel(status: AgentConnectionStatus): String = when (status) {
    AgentConnectionStatus.OFFLINE -> stringResource(R.string.live_testing_agent_offline)
    AgentConnectionStatus.ONLINE -> stringResource(R.string.live_testing_agent_online)
    AgentConnectionStatus.BUSY -> stringResource(R.string.live_testing_agent_busy)
}

@Composable
fun StatusBadge(status: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColorFor(color)
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
