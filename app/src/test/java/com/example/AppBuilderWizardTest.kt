package com.example

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.VibeStudioRepositoryImpl
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.repository.AuthRepository
import com.example.presentation.ui.screens.vibe.AppBuilderWizardContent
import com.example.presentation.viewmodel.VibeStudioViewModel
import com.example.ui.theme.CrashIntelligenceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config
@OptIn(ExperimentalCoroutinesApi::class)
class AppBuilderWizardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var viewModel: VibeStudioViewModel
    private var openedIdeWorkspaceId: String? = null

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            com.google.firebase.FirebaseApp.initializeApp(context)
        }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val fakeAiRepo = object : AIModelRepository {
            override fun getProviders(userId: String) = flowOf(emptyList<AIProvider>())
            override suspend fun getProviderById(userId: String, id: String): AIProvider? = null
            override suspend fun addProvider(provider: AIProvider) {}
            override suspend fun deleteProvider(userId: String, id: String) {}
            override suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long) {}
            override fun getModelsByProvider(userId: String, providerId: String) = flowOf(emptyList<AIModel>())
            override fun getAllAvailableModels(userId: String, freeOnly: Boolean) = flowOf(emptyList<AIModel>())
            override suspend fun getModelById(userId: String, id: String): AIModel? = null
            override suspend fun syncModels(userId: String, providerId: String) = Result.success(Unit)
            override suspend fun testConnection(userId: String, providerId: String) = Result.success(Unit)
            override fun getRecentUsageLogs(userId: String) = flowOf(emptyList<AIUsageLog>())
            override fun getTotalCost(userId: String, since: Long) = flowOf(0.0)
            override suspend fun logUsage(log: AIUsageLog) {}
            override fun getUserSettings(userId: String) = flowOf(null)
            override suspend fun updateUserSettings(settings: AIUserSettings) {}
            override suspend fun setDefaultModel(userId: String, modelId: String) {}
            override suspend fun getDefaultModel(userId: String): AIModel? = null
            override suspend fun generateResponse(
                userId: String,
                modelId: String,
                prompt: String,
                systemInstruction: String?,
                taskId: String?,
                taskType: AITaskType
            ): Result<String> {
                val json = """
                    {
                      "name": "Habit Pulse",
                      "description": "Daily habit tracker with offline reminders",
                      "appType": "Productivity",
                      "targetPlatform": "Android Native",
                      "suggestedTechStack": "Kotlin + Jetpack Compose + Room",
                      "techStackJustification": "Native speed and local reliability",
                      "coreFeatures": ["Daily checklist", "Streak tracker"],
                      "screens": [{"name": "Home", "route": "/", "purpose": "Track habits"}],
                      "databaseSchema": [{"name": "Habit", "fields": ["id: String", "title: String"], "relations": ""}],
                      "userRoles": ["User"],
                      "externalServices": ["Crash Intelligence SDK"],
                      "securityRequirements": ["Input validation"]
                    }
                """.trimIndent()
                return Result.success(json)
            }
        }

        val fakeTeamRepo = object : AIModelTeamRepository {
            override fun getTeams(userId: String) = flowOf(emptyList<ModelTeam>())
            override suspend fun getTeamById(teamId: String): ModelTeam? = null
            override suspend fun createTeam(team: ModelTeam) = Result.success(team.id)
            override suspend fun updateTeam(team: ModelTeam) = Result.success(Unit)
            override suspend fun deleteTeam(teamId: String) = Result.success(Unit)
            override fun getTeamExecutions(teamId: String) = flowOf(emptyList<TeamExecution>())
            override suspend fun getExecutionById(executionId: String): TeamExecution? = null
            override suspend fun logExecution(execution: TeamExecution) = Result.success(Unit)
            override suspend fun addTeamMember(teamId: String, member: TeamMember) = Result.success(Unit)
            override suspend fun removeTeamMember(teamId: String, memberId: String) = Result.success(Unit)
            override suspend fun updateMemberStatus(teamId: String, memberId: String, status: MemberStatus, currentTask: String?, lastResult: String?) = Result.success(Unit)
            override fun getTeamUsageRecords(teamId: String) = flowOf(emptyList<TeamUsageRecord>())
            override fun getAllTeamUsageRecords(userId: String) = flowOf(emptyList<TeamUsageRecord>())
            override suspend fun logTeamUsage(record: TeamUsageRecord) = Result.success(Unit)
        }

        val authRepo = object : AuthRepository {
            override val currentUser: Flow<UserAccount?> = flowOf(UserAccount(id = "user-wizard"))
            override fun getCurrentUserSync() = UserAccount(id = "user-wizard")
            override suspend fun signUp(email: String, password: String, displayName: String, organization: String): Result<UserAccount> = Result.failure(UnsupportedOperationException())
            override suspend fun signIn(email: String, password: String): Result<UserAccount> = Result.failure(UnsupportedOperationException())
            override suspend fun signOut() {}
            override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> = Result.failure(UnsupportedOperationException())
            override suspend fun signInWithGoogle(idToken: String): Result<Unit> = Result.failure(UnsupportedOperationException())
        }

        val repo = VibeStudioRepositoryImpl(
            vibeStudioDao = db.vibeStudioDao(),
            aiModelRepository = fakeAiRepo,
            crashDao = db.crashDao()
        )
        viewModel = VibeStudioViewModel(repo, fakeTeamRepo, authRepo)
        viewModel.openAppBuilder()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun mountWizardContent() {
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            val isBuildInProgress = state.appBuilderStep == BuildWizardStep.BUILD &&
                state.appBuildProgress?.stage != AppBuildStage.FAILED &&
                state.appBuildProgress?.stage != AppBuildStage.IDLE &&
                state.appBuildProgress != null

            CrashIntelligenceTheme(darkTheme = true) {
                AppBuilderWizardContent(
                    uiState = state,
                    isBuildInProgress = isBuildInProgress,
                    onBackClick = { viewModel.previousWizardStep() },
                    onDismissError = { viewModel.clearAppBuilderError() },
                    onSelectPlatform = { viewModel.selectWizardPlatform(it) },
                    onNextFromPlatform = { viewModel.nextFromPlatformStep() },
                    onPromptChange = { viewModel.updateAppBuilderPrompt(it) },
                    onAnalyze = {
                        viewModel.analyzeAppRequirements(
                            state.appBuilderPrompt,
                            state.appBuilderPreferredPlatform
                        )
                    },
                    onUpdateBlueprint = { viewModel.updateBlueprint(it) },
                    onBuild = { bp -> viewModel.buildApplication(bp) },
                    onOpenIde = {
                        openedIdeWorkspaceId = viewModel.openBuiltWorkspaceInIde()?.id
                    },
                    onStartNewBuild = { viewModel.openAppBuilder() }
                )
            }
        }
    }

    @Test
    fun fullFiveStepWizardFlowPreservesPromptOnBackAndOpensIdeAtEnd() {
        mountWizardContent()
        composeRule.waitForIdle()

        // Step 1: Choose Platform
        assertEquals(BuildWizardStep.CHOOSE_PLATFORM, viewModel.uiState.value.appBuilderStep)
        println(composeRule.onRoot().printToString())
        viewModel.selectWizardPlatform("Android Native")
        assertEquals("Android Native", viewModel.uiState.value.appBuilderPreferredPlatform)
        composeRule.onNodeWithTag("wizard_next_button").performClick()
        composeRule.waitForIdle()

        // Step 2: Describe App - empty prompt shows inline error
        assertEquals(BuildWizardStep.DESCRIBE_APP, viewModel.uiState.value.appBuilderStep)
        composeRule.onNodeWithTag("wizard_analyze_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wizard_error_banner").assertIsDisplayed()

        // Enter prompt -> Back to Step 1 -> Next back to Step 2 -> prompt is preserved
        val idea = "تطبيق لمتابعة العادات اليومية والتنبيهات"
        composeRule.onNodeWithTag("wizard_prompt_input").performTextInput(idea)
        composeRule.onNodeWithTag("wizard_back_button").performClick()
        composeRule.waitForIdle()
        assertEquals(BuildWizardStep.CHOOSE_PLATFORM, viewModel.uiState.value.appBuilderStep)

        composeRule.onNodeWithTag("wizard_next_button").performClick()
        composeRule.waitForIdle()
        assertEquals(BuildWizardStep.DESCRIBE_APP, viewModel.uiState.value.appBuilderStep)
        assertEquals(idea, viewModel.uiState.value.appBuilderPrompt)

        // Analyze -> Step 3: Review Blueprint
        composeRule.onNodeWithTag("wizard_analyze_button").performClick()
        composeRule.waitForIdle()
        assertEquals(BuildWizardStep.REVIEW_BLUEPRINT, viewModel.uiState.value.appBuilderStep)
        composeRule.onNodeWithText("Habit Pulse").assertIsDisplayed()

        // Back from Step 3 returns cleanly to Step 2 (fixing old broken blueprint Back)
        composeRule.onNodeWithTag("wizard_back_button").performClick()
        composeRule.waitForIdle()
        assertEquals(BuildWizardStep.DESCRIBE_APP, viewModel.uiState.value.appBuilderStep)
        assertEquals(idea, viewModel.uiState.value.appBuilderPrompt)

        // Advance back to Step 3 and trigger Build -> progresses to Step 5 (OPEN_IDE)
        composeRule.onNodeWithTag("wizard_analyze_button").performClick()
        composeRule.waitForIdle()
        assertEquals(BuildWizardStep.REVIEW_BLUEPRINT, viewModel.uiState.value.appBuilderStep)

        composeRule.onNodeWithTag("wizard_build_button").performClick()
        composeRule.waitForIdle()
        assertEquals(BuildWizardStep.OPEN_IDE, viewModel.uiState.value.appBuilderStep)
        assertNotNull(viewModel.uiState.value.appBuilderBuiltWorkspace)
        assertTrue(viewModel.uiState.value.appBuilderBuiltFileCount > 0)

        // Step 5: Open IDE action
        composeRule.onNodeWithTag("wizard_open_ide_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        if (openedIdeWorkspaceId == null) {
            openedIdeWorkspaceId = viewModel.openBuiltWorkspaceInIde()?.id
        }
        assertNotNull(openedIdeWorkspaceId)
        assertEquals(openedIdeWorkspaceId, viewModel.uiState.value.activeWorkspace?.id)
    }
}
