package com.example.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.di.DependencyProvider
import com.example.presentation.ui.screens.validation.CrashSandboxTab
import com.example.presentation.ui.screens.validation.ValidationMatrixTab
import com.example.presentation.ui.screens.validation.ValidationReportTab
import com.example.presentation.ui.screens.validation.VibeSandboxTab
import com.example.presentation.viewmodel.TeamValidationViewModel
import com.example.presentation.viewmodel.ValidationTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamValidationCenterScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: TeamValidationViewModel = viewModel {
        DependencyProvider.provideTeamValidationViewModel(context)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.exportSuccessMessage) {
        uiState.exportSuccessMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "مركز التحقق من فرق الذكاء الاصطناعي",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "E2E AI Model Teams Validation Center",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("validation_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.runAllMatrixTests() },
                        enabled = !uiState.matrixProgress.isRunning,
                        modifier = Modifier.testTag("top_bar_run_validation")
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = "Run All Tests")
                    }
                    IconButton(
                        onClick = { viewModel.exportMarkdownReport(context) },
                        modifier = Modifier.testTag("top_bar_export_report")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export Report")
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
            // Scrollable Tab Row
            PrimaryScrollableTabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                ValidationTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = tab.labelAr,
                                fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            when (tab) {
                                ValidationTab.MATRIX_20 -> Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                ValidationTab.VIBE_SANDBOX -> Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                                ValidationTab.CRASH_SANDBOX -> Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                                ValidationTab.REPORT -> Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }
            }

            // Tab Content
            when (uiState.selectedTab) {
                ValidationTab.MATRIX_20 -> ValidationMatrixTab(uiState, viewModel)
                ValidationTab.VIBE_SANDBOX -> VibeSandboxTab(uiState, viewModel)
                ValidationTab.CRASH_SANDBOX -> CrashSandboxTab(uiState, viewModel)
                ValidationTab.REPORT -> ValidationReportTab(uiState, viewModel)
            }
        }
    }
}
