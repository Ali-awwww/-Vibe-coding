package com.example.presentation.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.di.DependencyProvider
import com.example.domain.model.*
import com.example.presentation.navigation.Screen
import com.example.presentation.ui.screens.teams.CosmicTeamsBackdrop
import com.example.presentation.ui.screens.teams.CreateTeamWizard
import com.example.presentation.ui.screens.teams.DeleteTeamConfirmDialog
import com.example.presentation.ui.screens.teams.TeamsAuthRequiredCard
import com.example.presentation.ui.screens.teams.TeamsListSection
import com.example.presentation.viewmodel.ModelTeamsUiState
import com.example.presentation.viewmodel.ModelTeamsViewModel
import com.example.ui.theme.VibeDesignTokens

@Composable
fun ModelTeamsScreen(
    navController: NavController,
    initialCreateMode: Boolean = false,
    viewModel: ModelTeamsViewModel = run {
        val context = LocalContext.current
        viewModel { DependencyProvider.provideModelTeamsViewModel(context) }
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialCreateMode, uiState.isAuthenticated) {
        if (initialCreateMode && uiState.isAuthenticated && !uiState.isCreating) {
            viewModel.startCreateTeam()
        }
    }

    ModelTeamsScreenContent(
        uiState = uiState,
        onBack = {
            if (uiState.isCreating && !initialCreateMode) {
                viewModel.cancelCreate()
            } else {
                navController.popBackStack()
            }
        },
        onNavigateToValidation = { navController.navigate(Screen.TeamValidation) },
        onNavigateToChat = { navController.navigate(Screen.TeamChat()) },
        onNavigateToLogin = { navController.navigate(Screen.Login) },
        onNavigateToTeamDetail = { navController.navigate(Screen.TeamDetail(it.id)) },
        onNavigateToTeamUsage = { navController.navigate(Screen.TeamUsage(it.id)) },
        onStartCreateTeam = { viewModel.startCreateTeam() },
        onCancelCreate = {
            if (initialCreateMode) {
                viewModel.cancelCreate()
                navController.popBackStack()
            } else {
                viewModel.cancelCreate()
            }
        },
        onUpdateNewTeamInfo = viewModel::updateNewTeamInfo,
        onUpdateCollaborationStyle = viewModel::updateCollaborationStyle,
        onUpdateTeamSettings = viewModel::updateTeamSettings,
        onUpdateTeamGovernance = viewModel::updateTeamGovernance,
        onAddMember = viewModel::addMemberToNewTeam,
        onRemoveMember = viewModel::removeMemberFromNewTeam,
        onSaveTeam = { viewModel.saveTeam() },
        onUseTemplate = { viewModel.useTemplate(it) },
        onRequestDeleteTeam = { viewModel.requestDeleteTeam(it) },
        onConfirmDeleteTeam = { viewModel.confirmDeleteTeam() },
        onDismissDeleteTeam = { viewModel.dismissDeleteTeam() },
        onRefresh = { viewModel.refresh() },
        onClearError = { viewModel.clearError() },
        onClearStatusMessage = { viewModel.clearStatusMessage() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelTeamsScreenContent(
    uiState: ModelTeamsUiState,
    onBack: () -> Unit,
    onNavigateToValidation: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToTeamDetail: (ModelTeam) -> Unit,
    onNavigateToTeamUsage: (ModelTeam) -> Unit,
    onStartCreateTeam: () -> Unit,
    onCancelCreate: () -> Unit,
    onUpdateNewTeamInfo: (String, String, String) -> Unit,
    onUpdateCollaborationStyle: (CollaborationStyle) -> Unit,
    onUpdateTeamSettings: (TeamSettings) -> Unit,
    onUpdateTeamGovernance: (TeamGovernanceSettings) -> Unit,
    onAddMember: (AIModel, TeamRole) -> Unit,
    onRemoveMember: (String) -> Unit,
    onSaveTeam: () -> Unit,
    onUseTemplate: (ModelTeam) -> Unit,
    onRequestDeleteTeam: (ModelTeam) -> Unit,
    onConfirmDeleteTeam: () -> Unit,
    onDismissDeleteTeam: () -> Unit,
    onRefresh: () -> Unit,
    onClearError: () -> Unit,
    onClearStatusMessage: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            onClearError()
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearStatusMessage()
        }
    }

    if (uiState.isCreating) {
        BackHandler(onBack = onCancelCreate)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CosmicTeamsBackdrop(modifier = Modifier.fillMaxSize())

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = VibeDesignTokens.Color.SpacePanel.copy(alpha = 0.92f),
                        titleContentColor = VibeDesignTokens.Color.TextPrimary,
                        navigationIconContentColor = VibeDesignTokens.Color.TextPrimary,
                        actionIconContentColor = VibeDesignTokens.Color.Cyan
                    ),
                    title = {
                        Text(
                            text = if (uiState.isCreating) "إنشاء فريق ذكاء اصطناعي" else "فرق نماذج الذكاء الاصطناعي",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("teams_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    },
                    actions = {
                        if (uiState.isAuthenticated) {
                            IconButton(
                                onClick = onNavigateToValidation,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("teams_validation_action")
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = "مركز التحقق")
                            }
                            IconButton(
                                onClick = onNavigateToChat,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("teams_chat_action")
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = "محادثة الفريق")
                            }
                            if (!uiState.isCreating) {
                                IconButton(
                                    onClick = onStartCreateTeam,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("teams_create_top_action")
                                ) {
                                    Icon(Icons.Default.GroupAdd, contentDescription = "فريق جديد")
                                }
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (uiState.isAuthenticated && !uiState.isCreating) {
                    FloatingActionButton(
                        onClick = onStartCreateTeam,
                        containerColor = VibeDesignTokens.Color.Cyan,
                        contentColor = VibeDesignTokens.Color.OnCyan,
                        modifier = Modifier.testTag("new_team_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إنشاء فريق جديد")
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                ) {
                    when {
                        !uiState.isAuthenticated -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TeamsAuthRequiredCard(
                                    title = "المصادقة مطلوبة لإدارة الفرق",
                                    subtitle = "سجّل الدخول لعرض فرقك المخصصة، التحقق من مزودي النماذج المتصلين، وتشغيل القوالب.",
                                    onSignIn = onNavigateToLogin,
                                    modifier = Modifier.testTag("model_teams_auth_card")
                                )
                            }
                        }

                        uiState.isCreating -> {
                            CreateTeamWizard(
                                uiState = uiState,
                                onUpdateInfo = onUpdateNewTeamInfo,
                                onSelectCollaborationStyle = onUpdateCollaborationStyle,
                                onUpdateSettings = onUpdateTeamSettings,
                                onUpdateGovernance = onUpdateTeamGovernance,
                                onAddMember = onAddMember,
                                onRemoveMember = onRemoveMember,
                                onCancel = onCancelCreate,
                                onSave = onSaveTeam
                            )
                        }

                        else -> {
                            TeamsListSection(
                                uiState = uiState,
                                onTeamClick = onNavigateToTeamDetail,
                                onUsageClick = onNavigateToTeamUsage,
                                onDeleteRequest = onRequestDeleteTeam,
                                onTemplateClick = onUseTemplate,
                                onStartCreate = onStartCreateTeam,
                                onRefresh = onRefresh,
                                onDismissError = onClearError
                            )
                        }
                    }
                }
            }
        }

        uiState.pendingDeleteTeam?.let { team ->
            DeleteTeamConfirmDialog(
                team = team,
                isMutating = uiState.isMutating,
                onConfirm = onConfirmDeleteTeam,
                onDismiss = onDismissDeleteTeam
            )
        }
    }
}
