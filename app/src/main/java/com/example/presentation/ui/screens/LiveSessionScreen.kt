package com.example.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.navigation.NavController
import com.example.domain.model.*
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.LiveTestingViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSessionScreen(
    sessionId: String,
    navController: NavController,
    viewModel: LiveTestingViewModel
) {
    val session by viewModel.activeSession.collectAsState()
    val events by viewModel.events.collectAsState()
    val listState = rememberLazyListState()
    val agentTransportAvailable = viewModel.isAgentTransportAvailable

    LaunchedEffect(sessionId) {
        viewModel.observeSession(sessionId)
        viewModel.observeEvents(sessionId)
    }

    // Auto-scroll to bottom when new events arrive
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            listState.animateScrollToItem(events.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (agentTransportAvailable) {
                                stringResource(R.string.live_testing_session_title)
                            } else {
                                stringResource(R.string.live_testing_local_record_unverified)
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (agentTransportAvailable) {
                            session?.let {
                                Text(
                                    stringResource(R.string.live_testing_session_build, it.buildId, it.version),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.live_testing_back))
                    }
                },
                actions = {
                    session?.let {
                        if (agentTransportAvailable && it.status != TestSessionStatus.COMPLETED && it.status != TestSessionStatus.FAILED) {
                            TextButton(onClick = { viewModel.stopSession() }) {
                                Text(stringResource(R.string.live_session_end_button), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!agentTransportAvailable) {
                AgentTransportUnavailableNotice(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            } else {
                // Status Header
                session?.let { currentSession ->
                    SessionStatusHeader(currentSession)
                }

                Divider()

                // Events List
                Box(modifier = Modifier.weight(1f)) {
                    if (events.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.live_testing_waiting_events),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(events) { event ->
                                EventItem(event)
                            }

                            if (session?.status == TestSessionStatus.CRASH_DETECTED) {
                                item {
                                    CrashDetectedAlert(
                                        onViewDetails = {
                                            session?.crashId?.let { id ->
                                                navController.navigate(Screen.CrashDetail(id))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (session?.status == TestSessionStatus.APP_RUNNING) {
                    CommandInput(onSendCommand = { cmd -> viewModel.sendCommandToAgent(session!!.id, cmd) })
                }
            }
        }
    }
}

@Composable
fun SessionStatusHeader(session: TestSession) {
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when(session.status) {
                                TestSessionStatus.APP_RUNNING -> MaterialTheme.colorScheme.primary
                                TestSessionStatus.CRASH_DETECTED -> MaterialTheme.colorScheme.error
                                TestSessionStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.outline
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when(session.status) {
                        TestSessionStatus.PREPARING -> stringResource(R.string.live_session_status_preparing)
                        TestSessionStatus.CONNECTED -> stringResource(R.string.live_session_status_connected)
                        TestSessionStatus.APP_RUNNING -> stringResource(R.string.live_session_status_app_running)
                        TestSessionStatus.CRASH_DETECTED -> stringResource(R.string.live_session_status_crash_detected)
                        TestSessionStatus.WAITING_FOR_REPORT -> stringResource(R.string.live_session_status_waiting_report)
                        TestSessionStatus.COMPLETED -> stringResource(R.string.live_session_status_completed)
                        TestSessionStatus.FAILED -> stringResource(R.string.live_session_status_failed)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.live_session_device_os, session.deviceName, session.os),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                stringResource(R.string.live_session_started_at, dateFormat.format(Date(session.startTime))),
                style = MaterialTheme.typography.labelSmall
            )
            session.endTime?.let {
                Text(
                    stringResource(R.string.live_session_ended_at, dateFormat.format(Date(it))),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun EventItem(event: TestEvent) {
    val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    Row(verticalAlignment = Alignment.Top) {
        Text(
            dateFormat.format(Date(event.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
            fontFamily = FontFamily.Monospace
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when(event.type) {
                        TestEventType.APP_START -> Icons.Default.Launch
                        TestEventType.SCREEN_TRANSITION -> Icons.Default.Screenshot
                        TestEventType.SDK_EVENT -> Icons.Default.Info
                        TestEventType.NON_FATAL_ERROR -> Icons.Default.Warning
                        TestEventType.NETWORK_REQUEST -> Icons.Default.Http
                        TestEventType.APP_STATE_CHANGE -> Icons.Default.SwapHoriz
                        TestEventType.ANR -> Icons.Default.Timer
                        TestEventType.CRASH -> Icons.Default.Dangerous
                    },
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = when(event.type) {
                        TestEventType.CRASH, TestEventType.ANR -> MaterialTheme.colorScheme.error
                        TestEventType.NON_FATAL_ERROR -> MaterialTheme.colorScheme.tertiary
                        TestEventType.NETWORK_REQUEST -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    eventTypeLabel(event.type),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                event.details,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (event.type == TestEventType.CRASH) FontFamily.Monospace else FontFamily.Default,
                color = if (event.type == TestEventType.CRASH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                fontSize = if (event.type == TestEventType.CRASH) 12.sp else 14.sp
            )
        }
    }
}

@Composable
private fun eventTypeLabel(type: TestEventType): String = when (type) {
    TestEventType.APP_START -> stringResource(R.string.live_session_event_app_start)
    TestEventType.SCREEN_TRANSITION -> stringResource(R.string.live_session_event_screen_transition)
    TestEventType.SDK_EVENT -> stringResource(R.string.live_session_event_sdk)
    TestEventType.NON_FATAL_ERROR -> stringResource(R.string.live_session_event_non_fatal_error)
    TestEventType.NETWORK_REQUEST -> stringResource(R.string.live_session_event_network_request)
    TestEventType.APP_STATE_CHANGE -> stringResource(R.string.live_session_event_app_state_change)
    TestEventType.ANR -> stringResource(R.string.live_session_event_anr)
    TestEventType.CRASH -> stringResource(R.string.live_session_event_crash)
}

@Composable
fun CrashDetectedAlert(onViewDetails: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dangerous, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.live_session_crash_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.live_session_crash_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onViewDetails,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.live_session_view_crash))
            }
        }
    }
}

@Composable
fun CommandInput(onSendCommand: (String) -> Unit) {
    var command by remember { mutableStateOf("") }
    
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.live_testing_command_placeholder)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { 
                    if (command.isNotBlank()) {
                        onSendCommand(command)
                        command = ""
                    }
                },
                enabled = command.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = stringResource(R.string.live_testing_send_command), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
