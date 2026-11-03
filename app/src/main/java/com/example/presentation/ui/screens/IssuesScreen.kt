package com.example.presentation.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.R
import com.example.domain.model.Issue
import com.example.domain.model.IssueStatus
import com.example.domain.model.Severity
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.IssuesViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesScreen(
    navController: NavController,
    viewModel: IssuesViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var showProjectDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.issues), style = MaterialTheme.typography.titleLarge)
                        uiState.selectedProject?.let {
                            Text(it.name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showProjectDialog = true }) {
                        Icon(Icons.Default.FilterList, null)
                    }
                    IconButton(onClick = { viewModel.syncIssues() }) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Refresh, null)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.issues.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_issues_found))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.issues) { issue ->
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
                        items(uiState.projects) { project ->
                            ListItem(
                                headlineContent = { Text(project.name) },
                                modifier = Modifier.clickable {
                                    viewModel.selectProject(project)
                                    showProjectDialog = false
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            null,
                            tint = if (isPositive) Color(0xFFF44336) else Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "${Math.abs(issue.trend).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPositive) Color(0xFFF44336) else Color(0xFF4CAF50)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(issue.lastOccurrence)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(issue.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                issue.subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatItem(label = stringResource(R.string.occurrences), value = issue.occurrenceCount.toString())
                StatItem(label = stringResource(R.string.affected_users), value = issue.affectedUsers.toString())
                StatItem(label = stringResource(R.string.source), value = issue.source?.name?.replace("_", " ") ?: "UNKNOWN")
            }
        }
    }
}

@Composable
fun StatusBadge(status: IssueStatus) {
    val color = when (status) {
        IssueStatus.NEW -> Color(0xFF2196F3)
        IssueStatus.INVESTIGATING -> Color(0xFFFF9800)
        IssueStatus.RESOLVED -> Color(0xFF4CAF50)
        IssueStatus.REOPENED -> Color(0xFFF44336)
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = status.name,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
