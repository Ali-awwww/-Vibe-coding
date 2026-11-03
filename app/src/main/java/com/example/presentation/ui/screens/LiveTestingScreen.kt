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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.R
import com.example.domain.model.*
import androidx.compose.ui.res.stringResource
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.LiveTestingViewModel
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
    val sessions by viewModel.sessions.collectAsState()

    var showStartDialog by remember { mutableStateOf<Application?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.live_testing)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    title = "لا توجد تطبيقات",
                    description = "أضف تطبيقًا إلى هذا المشروع لبدء الاختبار المباشر."
                )
            } else {
                Text(
                    "التطبيقات المتاحة",
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
                            onConnectAgent = { viewModel.connectAgent(app.id) }
                        )
                    }

                    if (sessions.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                "جلسات الاختبار السابقة",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        items(sessions) { session ->
                            SessionHistoryItem(
                                session = session,
                                onClick = { navController.navigate(Screen.LiveSession(session.id)) }
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
                viewModel.startSession(showStartDialog!!, version, buildId)
                showStartDialog = null
                // We'll navigate to LiveSessionScreen once the session is created
                // For now, let's assume we can navigate directly if we have the ID or wait for state change
            }
        )
    }

    // Effect to navigate when active session starts
    val activeSession by viewModel.activeSession.collectAsState()
    LaunchedEffect(activeSession) {
        activeSession?.let {
            if (it.status == TestSessionStatus.PREPARING) {
                navController.navigate(Screen.LiveSession(it.id))
            }
        }
    }
}

@Composable
fun ApplicationCard(
    application: Application,
    navController: NavController,
    onStartTesting: () -> Unit,
    onConnectAgent: () -> Unit
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
                Text("SDK: ${application.sdkSetupStatus}")
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (application.agentConnectionStatus == AgentConnectionStatus.ONLINE) 
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Agent: ${application.agentConnectionStatus}")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { navController.navigate(Screen.SetupGuide(application.projectId, application.id)) }) {
                    Icon(Icons.Default.Build, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إعداد SDK")
                }

                if (application.agentConnectionStatus == AgentConnectionStatus.OFFLINE) {
                    TextButton(onClick = onConnectAgent) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ربط العميل (Connect Agent)")
                    }
                }
                
                Button(
                    onClick = onStartTesting,
                    enabled = application.agentConnectionStatus == AgentConnectionStatus.ONLINE
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("بدء الاختبار")
                }
            }
        }
    }
}

@Composable
fun SessionHistoryItem(
    session: TestSession,
    onClick: () -> Unit
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
                    imageVector = when(session.status) {
                        TestSessionStatus.COMPLETED -> Icons.Default.CheckCircle
                        TestSessionStatus.CRASH_DETECTED -> Icons.Default.Error
                        else -> Icons.Default.History
                    },
                    contentDescription = null,
                    tint = when(session.status) {
                        TestSessionStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        TestSessionStatus.CRASH_DETECTED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text("Build: ${session.buildId} (${session.version})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(dateFormat.format(Date(session.startTime)), style = MaterialTheme.typography.bodySmall)
            }

            StatusBadge(
                status = session.status.name,
                color = when(session.status) {
                    TestSessionStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
                    TestSessionStatus.CRASH_DETECTED -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        }
    }
}

@Composable
fun StartTestingDialog(
    application: Application,
    onDismiss: () -> Unit,
    onConfirm: (version: String, buildId: String) -> Unit
) {
    var version by remember { mutableStateOf("1.0.0") }
    var buildId by remember { mutableStateOf("B${System.currentTimeMillis().toString().takeLast(6)}") }
    var triggerCrash by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بدء جلسة اختبار جديدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("التطبيق: ${application.name}")
                
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("إصدار التطبيق") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = buildId,
                    onValueChange = { buildId = it },
                    label = { Text("رقم البناء (Build ID)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = triggerCrash,
                        onCheckedChange = { triggerCrash = it }
                    )
                    Text("محاكاة انهيار (Simulate Crash)")
                }
                
                Text(
                    "ملاحظة: تأكد من أن تطبيقك يحتوي على SDK الخاص بـ Crash Intelligence ومرتبط بالعميل المحلي.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { 
                val finalBuildId = if (triggerCrash) "crash-$buildId" else buildId
                onConfirm(version, finalBuildId) 
            }) {
                Text("بدء")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
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
