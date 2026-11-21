package com.example.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.R
import com.example.di.DependencyProvider
import com.example.domain.model.AIProvider
import com.example.domain.model.AIProviderType
import com.example.domain.model.AIProviderStatus
import com.example.presentation.ui.screens.aimodels.*
import com.example.presentation.viewmodel.AIModelsUiState
import com.example.presentation.viewmodel.AIModelsViewModel
import com.example.ui.theme.VibeDesignTokens

@Composable
fun AIModelsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: AIModelsViewModel = viewModel {
        DependencyProvider.provideAIModelsViewModel(context)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AIModelsScreenContent(
        uiState = uiState,
        onNavigateBack = { navController.popBackStack() },
        onAddProvider = { name, type, key, url -> viewModel.addProvider(name, type, key, url) },
        onDeleteProvider = { viewModel.deleteProvider(it) },
        onSyncProvider = { viewModel.syncModels(it) },
        onTestProvider = { viewModel.testConnection(it) },
        onSelectDefaultModel = { viewModel.setDefaultModel(it) },
        onToggleFreeOnly = { viewModel.toggleFreeOnly(it) },
        onToggleAutoSelect = { viewModel.toggleAutoSelect(it) },
        onRefresh = { viewModel.refresh() },
        onClearFeedback = { viewModel.clearFeedback() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AIModelsScreenContent(
    uiState: AIModelsUiState,
    onNavigateBack: () -> Unit,
    onAddProvider: (String, AIProviderType, String, String?) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onSyncProvider: (String) -> Unit,
    onTestProvider: (String) -> Unit,
    onSelectDefaultModel: (String) -> Unit,
    onToggleFreeOnly: (Boolean) -> Unit,
    onToggleAutoSelect: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onClearFeedback: () -> Unit
) {
    var showAddProviderDialog by remember { mutableStateOf(false) }
    var providerPendingDeletion by remember { mutableStateOf<AIProvider?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val feedback = uiState.feedbackMessageResId?.let { stringResource(it) }
        ?: (uiState.error ?: uiState.message)

    LaunchedEffect(feedback) {
        if (feedback != null) {
            snackbarHostState.showSnackbar(feedback)
            onClearFeedback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF09162A), VibeDesignTokens.Color.SpaceBackground, Color(0xFF080D1A))
                )
            )
    ) {
        VibeSpaceBackdrop(Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = VibeDesignTokens.Color.SpacePanel,
                        titleContentColor = VibeDesignTokens.Color.TextPrimary,
                        navigationIconContentColor = VibeDesignTokens.Color.Cyan,
                        actionIconContentColor = VibeDesignTokens.Color.Cyan
                    ),
                    title = {
                        Text(
                            text = stringResource(R.string.ai_models_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ai_models_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ai_models_back))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showAddProviderDialog = true },
                            enabled = uiState.isAuthenticated && !uiState.isLoading,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ai_models_add_provider_button")
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = stringResource(R.string.ai_models_add_provider))
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize()
                        .testTag("ai_models_lazy_column"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (!uiState.isAuthenticated && !uiState.isLoading) {
                        item {
                            AIAuthRequiredCard()
                        }
                    } else {
                        item {
                            VibeSectionHeader(title = stringResource(R.string.ai_models_dashboard), badge = stringResource(R.string.ai_models_dashboard_badge))
                            Spacer(Modifier.height(10.dp))
                            AISettingsDashboard(uiState)
                        }

                        item {
                            VibeSectionHeader(title = stringResource(R.string.ai_models_cost_settings))
                            Spacer(Modifier.height(10.dp))
                            AICostSettingsCard(
                                uiState = uiState,
                                onFreeOnlyToggle = onToggleFreeOnly,
                                onAutoSelectToggle = onToggleAutoSelect,
                                enabled = uiState.isAuthenticated && !uiState.isLoading
                            )
                        }

                        item {
                            VibeSectionHeader(
                                title = stringResource(R.string.ai_models_select_model),
                                badge = "${uiState.models.size}",
                                isLoading = uiState.isLoading
                            )
                            Spacer(Modifier.height(10.dp))
                            AIModelSelectionSection(
                                models = uiState.models,
                                selectedId = uiState.defaultModelId,
                                onSelect = onSelectDefaultModel,
                                autoSelect = uiState.autoSelectFree && uiState.freeOnly,
                                freeOnly = uiState.freeOnly,
                                hasProviders = uiState.providers.isNotEmpty(),
                                hasConnectedProviders = uiState.providers.any { it.status == AIProviderStatus.CONNECTED },
                                isLoading = uiState.isLoading,
                                enabled = uiState.isAuthenticated && !uiState.isLoading
                            )
                        }

                        item {
                            VibeSectionHeader(
                                title = stringResource(R.string.ai_models_providers),
                                badge = "${uiState.providers.size}"
                            )
                            Spacer(Modifier.height(10.dp))
                            if (uiState.providers.isEmpty()) {
                                if (uiState.isLoading) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = VibeDesignTokens.Color.Cyan
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = stringResource(R.string.ai_models_loading_providers),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = VibeDesignTokens.Color.TextMuted
                                        )
                                    }
                                } else {
                                    AIProvidersGuide()
                                }
                            }
                        }

                        items(uiState.providers, key = { it.id }) { provider ->
                            AIProviderCard(
                                provider = provider,
                                onSync = onSyncProvider,
                                onTest = onTestProvider,
                                onDelete = { providerPendingDeletion = provider },
                                isLoading = uiState.isLoading
                            )
                        }
                    }
                }
            }

            if (showAddProviderDialog) {
                AddAIProviderDialog(
                    onDismiss = { showAddProviderDialog = false },
                    onConfirm = { name, type, key, url ->
                        onAddProvider(name, type, key, url)
                        showAddProviderDialog = false
                    }
                )
            }

            if (uiState.isLoadError) {
                AlertDialog(
                    onDismissRequest = {},
                    containerColor = VibeDesignTokens.Color.SpacePanelRaised,
                    titleContentColor = VibeDesignTokens.Color.TextPrimary,
                    textContentColor = VibeDesignTokens.Color.TextMuted,
                    title = { Text(stringResource(R.string.ai_models_load_error_title)) },
                    text = { Text(stringResource(R.string.ai_models_load_error_body)) },
                    confirmButton = {
                        TextButton(onClick = onRefresh, enabled = !uiState.isLoading) {
                            Text(stringResource(R.string.ai_models_retry), color = VibeDesignTokens.Color.Cyan)
                        }
                    }
                )
            }

            providerPendingDeletion?.let { provider ->
                AlertDialog(
                    onDismissRequest = { providerPendingDeletion = null },
                    containerColor = VibeDesignTokens.Color.SpacePanelRaised,
                    titleContentColor = VibeDesignTokens.Color.TextPrimary,
                    textContentColor = VibeDesignTokens.Color.TextMuted,
                    title = { Text(stringResource(R.string.ai_models_delete_provider_title)) },
                    text = {
                        Text(stringResource(R.string.ai_models_delete_provider_message, provider.name))
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onDeleteProvider(provider.id)
                                providerPendingDeletion = null
                            },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.testTag("delete_provider_confirm_button")
                        ) { Text(stringResource(R.string.ai_models_delete), color = VibeDesignTokens.Color.Error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { providerPendingDeletion = null }) {
                            Text(stringResource(R.string.ai_models_cancel), color = VibeDesignTokens.Color.TextMuted)
                        }
                    }
                )
            }
        }
    }
}
