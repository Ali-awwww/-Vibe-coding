package com.example.presentation.ui.screens.vibe

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.R
import com.example.domain.model.*
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.VibeStudioViewModel
import com.example.ui.theme.VibeDesignTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBuilderWizardScreen(
    navController: NavController,
    viewModel: VibeStudioViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    val isBuildInProgress = uiState.appBuilderStep == BuildWizardStep.BUILD &&
        uiState.appBuildProgress?.stage != AppBuildStage.FAILED &&
        uiState.appBuildProgress?.stage != AppBuildStage.IDLE &&
        uiState.appBuildProgress != null

    val handleBack: () -> Unit = {
        when {
            isBuildInProgress -> Unit
            uiState.appBuilderStep == BuildWizardStep.CHOOSE_PLATFORM -> navController.popBackStack()
            else -> viewModel.previousWizardStep()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    AppBuilderWizardContent(
        uiState = uiState,
        isBuildInProgress = isBuildInProgress,
        onBackClick = handleBack,
        onDismissError = { viewModel.clearAppBuilderError() },
        onSelectPlatform = { viewModel.selectWizardPlatform(it) },
        onNextFromPlatform = { viewModel.nextFromPlatformStep() },
        onPromptChange = { viewModel.updateAppBuilderPrompt(it) },
        onAnalyze = {
            viewModel.analyzeAppRequirements(
                uiState.appBuilderPrompt,
                uiState.appBuilderPreferredPlatform
            )
        },
        onUpdateBlueprint = { viewModel.updateBlueprint(it) },
        onBuild = { bp -> viewModel.buildApplication(bp) },
        onOpenIde = {
            val built = viewModel.openBuiltWorkspaceInIde()
            if (built != null) {
                navController.navigate(Screen.VibeStudio(initialWorkspaceId = built.id)) {
                    popUpTo<Screen.AppBuilder> { inclusive = true }
                    launchSingleTop = true
                }
            }
        },
        onStartNewBuild = { viewModel.openAppBuilder() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBuilderWizardContent(
    uiState: StudioUiState,
    isBuildInProgress: Boolean,
    onBackClick: () -> Unit,
    onDismissError: () -> Unit,
    onSelectPlatform: (String) -> Unit,
    onNextFromPlatform: () -> Unit,
    onPromptChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onUpdateBlueprint: (AppBlueprint) -> Unit,
    onBuild: (AppBlueprint) -> Unit,
    onOpenIde: () -> Unit,
    onStartNewBuild: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.builder_wizard_title),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(
                                R.string.builder_step_counter,
                                uiState.appBuilderStep.stepNumber,
                                BuildWizardStep.entries.size
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        enabled = !isBuildInProgress,
                        modifier = Modifier.testTag("wizard_topbar_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.builder_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WizardStepProgressBar(currentStep = uiState.appBuilderStep)

            uiState.appBuilderError?.let { errorMsg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wizard_error_banner"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = errorMsg,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        IconButton(onClick = onDismissError) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق التنبيه")
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (uiState.appBuilderStep) {
                    BuildWizardStep.CHOOSE_PLATFORM -> ChoosePlatformStep(
                        selectedPlatform = uiState.appBuilderPreferredPlatform,
                        onSelectPlatform = onSelectPlatform,
                        onNext = onNextFromPlatform
                    )

                    BuildWizardStep.DESCRIBE_APP -> DescribeAppStep(
                        selectedPlatform = uiState.appBuilderPreferredPlatform,
                        prompt = uiState.appBuilderPrompt,
                        isAnalyzing = uiState.isAnalyzingRequirements,
                        onPromptChange = onPromptChange,
                        onBack = onBackClick,
                        onAnalyze = onAnalyze
                    )

                    BuildWizardStep.REVIEW_BLUEPRINT -> {
                        val blueprint = uiState.appBuilderBlueprint
                        if (blueprint != null) {
                            ReviewBlueprintStep(
                                blueprint = blueprint,
                                onUpdateBlueprint = onUpdateBlueprint,
                                onBack = onBackClick,
                                onConfirmBuild = { onBuild(blueprint) }
                            )
                        } else {
                            MissingBlueprintFallback(onBack = onBackClick)
                        }
                    }

                    BuildWizardStep.BUILD -> BuildStep(
                        progress = uiState.appBuildProgress ?: AppBuildProgress(),
                        onBack = onBackClick,
                        onRetry = {
                            uiState.appBuilderBlueprint?.let(onBuild)
                        }
                    )

                    BuildWizardStep.OPEN_IDE -> OpenIdeStep(
                        workspace = uiState.appBuilderBuiltWorkspace,
                        fileCount = uiState.appBuilderBuiltFileCount,
                        onOpenIde = onOpenIde,
                        onStartNewBuild = onStartNewBuild
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardStepProgressBar(currentStep: BuildWizardStep) {
    val steps = listOf(
        BuildWizardStep.CHOOSE_PLATFORM to stringResource(R.string.builder_step_platform),
        BuildWizardStep.DESCRIBE_APP to stringResource(R.string.builder_step_describe),
        BuildWizardStep.REVIEW_BLUEPRINT to stringResource(R.string.builder_step_blueprint),
        BuildWizardStep.BUILD to stringResource(R.string.builder_step_build),
        BuildWizardStep.OPEN_IDE to stringResource(R.string.builder_step_open_ide)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { currentStep.stepNumber / BuildWizardStep.entries.size.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            steps.forEach { (step, label) ->
                val isDone = currentStep.stepNumber > step.stepNumber
                val isActive = currentStep == step
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isDone -> VibeDesignTokens.Color.Success
                            isActive -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isDone) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(
                                    text = "${step.stepNumber}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive || isDone) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingBlueprintFallback(onBack: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "لم يتم العثور على مخطط تحليلي بعد. ارجع لوصف التطبيق ثم أعد التحليل.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("wizard_back_button")
            ) {
                Text(stringResource(R.string.builder_back))
            }
        }
    }
}
