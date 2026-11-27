package com.example.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavController
import com.example.R
import com.example.domain.model.Issue
import com.example.domain.model.IssueStatus
import com.example.domain.model.Severity
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.IssuesViewModel
import com.example.ui.theme.VibeDesignTokens
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesScreen(
    navController: NavController,
    viewModel: IssuesViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showProjectDialog by remember { mutableStateOf(false) }
    val selectedProject = uiState.selectedProject
    val projectScopeLabel = when {
        uiState.isAllProjectsScope -> stringResource(R.string.issues_all_projects)
        selectedProject != null -> selectedProject.name
        else -> stringResource(R.string.issues_project_unavailable)
    }
    val lastSyncLabel = uiState.lastSyncAt?.let { timestamp ->
        stringResource(
            R.string.issues_last_successful_sync,
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
        )
    } ?: stringResource(R.string.issues_no_successful_sync)
    val refreshContentDescription = stringResource(R.string.issues_refresh_content_description)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = {
                    Column {
                        Text(stringResource(R.string.issues), style = MaterialTheme.typography.titleLarge)
                        Text(
                            projectScopeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            lastSyncLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showProjectDialog = true }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.issues_filter_content_description)
                        )
                    }
                    IconButton(
                        onClick = viewModel::syncIssues,
                        modifier = Modifier.semantics {
                            contentDescription = refreshContentDescription
                        },
                        enabled = uiState.projects.isNotEmpty() && !uiState.isSyncing
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val errorMessage = when (uiState.error) {
                IssuesViewModel.ERROR_PROJECT_UNAVAILABLE -> stringResource(R.string.issues_project_unavailable)
                IssuesViewModel.ERROR_SYNC -> stringResource(R.string.issues_sync_error)
                IssuesViewModel.ERROR_LOAD -> stringResource(R.string.issues_load_error)
                else -> null
            }
            if (errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("issues_error_card"),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = viewModel::retry) {
                            Text(stringResource(R.string.issues_retry))
                        }
                    }
                }
            }
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (
                uiState.issues.isEmpty() &&
                uiState.error != IssuesViewModel.ERROR_LOAD &&
                uiState.error != IssuesViewModel.ERROR_PROJECT_UNAVAILABLE
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("issues_empty_state"),
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                stringResource(R.string.no_issues_found),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.issues_empty_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (uiState.issues.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.issues, key = { it.id }) { issue ->
                        IssueItem(issue = issue) {
                            // Navigate to crashes for this issue
                            navController.navigate(Screen.Crashes(projectId = issue.projectId, applicationId = issue.applicationId))
                        }
                    }
                }
            }
        }

        if (showProjectDialog) {
            AlertDialog(
                onDismissRequest = { showProjectDialog = false },
                title = { Text(stringResource(R.string.select_project)) },
                text = {
                    LazyColumn {
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.issues_all_projects)) },
                                modifier = Modifier
                                    .testTag("issues_scope_all_projects")
                                    .selectable(
                                        selected = uiState.isAllProjectsScope,
                                        role = Role.RadioButton
                                    ) {
                                        viewModel.selectAllProjects()
                                        showProjectDialog = false
                                    },
                                trailingContent = {
                                    if (uiState.isAllProjectsScope) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                        }
                        items(uiState.projects) { project ->
                            ListItem(
                                headlineContent = { Text(project.name) },
                                modifier = Modifier
                                    .testTag("issues_scope_project_" + project.id)
                                    .selectable(
                                        selected = uiState.selectedProject?.id == project.id,
                                        role = Role.RadioButton
                                    ) {
                                        viewModel.selectProject(project)
                                        showProjectDialog = false
                                    },
                                trailingContent = {
                                    if (uiState.selectedProject?.id == project.id) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                        }
                    }
                },
                confirmButton = {}
            )
        }
    }
}

@Composable
fun IssueItem(issue: Issue, onClick: () -> Unit) {
    val severityAccent = when (issue.severity) {
        Severity.LOW -> VibeDesignTokens.Color.Cyan
        Severity.MEDIUM -> VibeDesignTokens.Color.Warning
        Severity.HIGH, Severity.CRITICAL -> VibeDesignTokens.Color.Error
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, severityAccent.copy(alpha = 0.32f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(issue.status)
                Spacer(Modifier.width(8.dp))
                SeverityBadge(issue.severity)
                Spacer(Modifier.weight(1f))
                if (issue.trend != 0f) {
                    val isPositive = issue.trend > 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (isPositive) VibeDesignTokens.Color.Error else VibeDesignTokens.Color.Success,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "${Math.abs(issue.trend).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPositive) VibeDesignTokens.Color.Error else VibeDesignTokens.Color.Success
                        )
                    }
                }
            }
            Text(
                text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(issue.lastOccurrence)),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1
            )
            Text(
                issue.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                issue.subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis
            )
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatItem(
                    label = stringResource(R.string.occurrences),
                    value = issue.occurrenceCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.affected_users),
                    value = issue.affectedUsers.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.source),
                    value = issue.source?.name?.replace("_", " ") ?: stringResource(R.string.issues_source_unknown),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatusBadge(status: IssueStatus) {
    val color = when (status) {
        IssueStatus.NEW -> VibeDesignTokens.Color.Cyan
        IssueStatus.INVESTIGATING -> VibeDesignTokens.Color.Warning
        IssueStatus.RESOLVED -> VibeDesignTokens.Color.Success
        IssueStatus.REOPENED -> VibeDesignTokens.Color.Error
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact)
    ) {
        Text(
            text = when (status) {
                IssueStatus.NEW -> stringResource(R.string.dashboard_issue_status_new)
                IssueStatus.INVESTIGATING -> stringResource(R.string.dashboard_issue_status_investigating)
                IssueStatus.RESOLVED -> stringResource(R.string.dashboard_issue_status_resolved)
                IssueStatus.REOPENED -> stringResource(R.string.dashboard_issue_status_reopened)
            },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
