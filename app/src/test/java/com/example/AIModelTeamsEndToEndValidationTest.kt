package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AIProviderDao
import com.example.data.local.AppDatabase
import com.example.data.remote.*
import com.example.data.repository.AIModelRepositoryImpl
import com.example.data.validation.TeamValidationEngineImpl
import com.example.domain.agent.TeamOrchestrator
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.validation.ValidationCategory
import com.example.domain.validation.ValidationStatus
import com.example.presentation.viewmodel.TeamValidationViewModel
import com.example.presentation.viewmodel.ValidationTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config
@OptIn(ExperimentalCoroutinesApi::class)
class AIModelTeamsEndToEndValidationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var aiProviderDao: AIProviderDao
    private lateinit var aiModelRepository: AIModelRepository
    private lateinit var teamRepository: AIModelTeamRepository
    private lateinit var mockTeamOrchestrator: TeamOrchestrator
    private lateinit var validationEngine: TeamValidationEngineImpl

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        aiProviderDao = database.aiProviderDao()

        val fakeOpenRouter = object : OpenRouterApi {
            override suspend fun getModels(token: String): OpenRouterModelsResponse = OpenRouterModelsResponse(emptyList())
            override suspend fun generateCompletion(token: String, request: OpenRouterChatRequest): OpenRouterChatResponse {
                return OpenRouterChatResponse(
                    id = "gen_1",
                    choices = listOf(ChatChoice(ChatMessage("assistant", "Mock generated response"))),
                    usage = TokenUsage(10, 10, 20)
                )
            }
        }

        val fakeHuggingFace = object : HuggingFaceApi {
            override suspend fun getModels(filter: String, sort: String, direction: Int, limit: Int): List<HuggingFaceModelData> = emptyList()
        }

        val testEncryptSecret: (String) -> String = { "test-encrypted:$it" }
        val testDecryptSecret: (String) -> String = { it.removePrefix("test-encrypted:") }
        aiModelRepository = AIModelRepositoryImpl(
            aiProviderDao,
            fakeOpenRouter,
            fakeHuggingFace,
            testEncryptSecret,
            testDecryptSecret
        )
        teamRepository = InMemoryAIModelTeamRepository()

        mockTeamOrchestrator = object : TeamOrchestrator {
            override fun executeTask(userId: String, team: ModelTeam, prompt: String): Flow<TeamExecution> =
                flowOf(TeamExecution(id = "e1", teamId = team.id, status = ExecutionStatus.COMPLETED))

            override fun executeTaskWithContext(
                userId: String,
                team: ModelTeam,
                prompt: String,
                attachedFiles: List<AttachedFileContext>,
                projectId: String?
            ): Flow<TeamExecution> = flowOf(TeamExecution(id = "e1", teamId = team.id, status = ExecutionStatus.COMPLETED))

            override suspend fun estimateTaskResources(userId: String, team: ModelTeam, prompt: String): PreExecutionEstimation =
                PreExecutionEstimation()

            override suspend fun pauseExecution(executionId: String) {
                val current = teamRepository.getExecutionById(executionId)
                if (current != null) teamRepository.logExecution(current.copy(status = ExecutionStatus.PAUSED))
            }

            override suspend fun resumeExecution(executionId: String, userId: String, team: ModelTeam): Flow<TeamExecution> =
                flowOf(TeamExecution(id = executionId, teamId = team.id, status = ExecutionStatus.EXECUTING))

            override suspend fun stopExecution(executionId: String) {}
            override suspend fun retryFailedSteps(executionId: String, userId: String, team: ModelTeam): Flow<TeamExecution> =
                flowOf(TeamExecution(id = executionId, teamId = team.id, status = ExecutionStatus.EXECUTING))
            override suspend fun assignDirectTask(
                executionId: String,
                userId: String,
                team: ModelTeam,
                memberId: String,
                taskPrompt: String
            ): Flow<TeamExecution> = flowOf(TeamExecution(id = executionId, teamId = team.id, status = ExecutionStatus.EXECUTING))
            override suspend fun sendInstruction(
                executionId: String,
                userId: String,
                team: ModelTeam,
                instruction: TeamInstruction
            ): Flow<TeamExecution> = flowOf(TeamExecution(id = executionId, teamId = team.id, status = ExecutionStatus.EXECUTING))
            override suspend fun changeMemberAgent(teamId: String, memberId: String, newModelId: String): Result<Unit> = Result.success(Unit)
            override suspend fun planAndDistribute(team: ModelTeam, prompt: String): Result<List<ExecutionStep>> = Result.success(emptyList())
        }

        validationEngine = TeamValidationEngineImpl(
            aiModelRepository = aiModelRepository,
            teamRepository = teamRepository,
            teamOrchestrator = mockTeamOrchestrator,
            encryptSecret = testEncryptSecret,
            decryptSecret = testDecryptSecret
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial test suite defines all 20 required validation criteria`() {
        val initialTests = validationEngine.getInitialTests()
        assertEquals("Must contain exactly 20 validation tests", 20, initialTests.size)
        
        val testIds = initialTests.map { it.id }.toSet()
        for (i in 1..20) {
            assertTrue("Test $i must be defined in matrix", testIds.contains(i))
        }

        // Verify key categories exist
        val categories = initialTests.map { it.category }.toSet()
        assertTrue(categories.contains(ValidationCategory.SETUP_AND_KEYS))
        assertTrue(categories.contains(ValidationCategory.EXECUTION_MODES))
        assertTrue(categories.contains(ValidationCategory.COLLABORATION_AND_HANDOVER))
        assertTrue(categories.contains(ValidationCategory.GOVERNANCE_AND_LIMITS))
    }

    @Test
    fun `test multi-user API key isolation requirement 20`() = runTest(testDispatcher) {
        val result = validationEngine.runSingleTest("user_isolation_tester", 20).first { it.status != ValidationStatus.RUNNING }
        assertEquals(ValidationStatus.PASSED, result.status)
        assertNotNull(result.evidence)
        assertTrue(result.evidence!!.contains("العزل الصارم لمفاتيح المستخدمين"))
    }

    @Test
    fun `test free only protection enforcement requirement 17`() = runTest(testDispatcher) {
        val result = validationEngine.runSingleTest("user_cost_shield", 17).first { it.status != ValidationStatus.RUNNING }
        assertEquals(ValidationStatus.PASSED, result.status)
        assertTrue(result.evidence!!.contains("Free Only"))
    }

    @Test
    fun `test model-to-model result handover requirement 11`() = runTest(testDispatcher) {
        val result = validationEngine.runSingleTest("user_handover", 11).first { it.status != ValidationStatus.RUNNING }
        assertEquals(ValidationStatus.PASSED, result.status)
        assertTrue(result.evidence!!.contains("calculateCrashScore"))
    }

    @Test
    fun `test debate mode execution and consensus requirement 9`() = runTest(testDispatcher) {
        val result = validationEngine.runSingleTest("user_debate", 9).first { it.status != ValidationStatus.RUNNING }
        assertEquals(ValidationStatus.PASSED, result.status)
        assertTrue(result.evidence!!.contains("جولة نقاش حقيقية"))
    }

    @Test
    fun `test pause and resume execution requirement 14`() = runTest(testDispatcher) {
        val result = validationEngine.runSingleTest("user_pause_resume", 14).first { it.status != ValidationStatus.RUNNING }
        assertEquals(ValidationStatus.PASSED, result.status)
        assertTrue(result.evidence!!.contains("PAUSED"))
        assertTrue(result.evidence!!.contains("EXECUTING"))
    }

    @Test
    fun `test full 20-point validation suite executes and passes 100 percent`() = runTest(testDispatcher) {
        var lastProgress = validationEngine.runFullValidationSuite("usr_e2e_master").first()
        validationEngine.runFullValidationSuite("usr_e2e_master").collect { progress ->
            lastProgress = progress
        }

        assertFalse("Progress should complete", lastProgress.isRunning)
        assertEquals("All 20 tests must be executed", 20, lastProgress.items.size)
        val nonPassingItems = lastProgress.items.filter { it.status != ValidationStatus.PASSED }
        assertTrue(
            "All tests must pass. Non-passing items: " + nonPassingItems.joinToString { item ->
                "id=${item.id}, status=${item.status}, error=${item.errorMessage}, evidence=${item.evidence}"
            },
            nonPassingItems.isEmpty()
        )
        assertNotNull(lastProgress.summaryReport)
        assertEquals(20, lastProgress.summaryReport?.passedCount)
        assertEquals(0, lastProgress.summaryReport?.failedCount)
    }

    @Test
    fun `test vibe coding isolated sandbox executes all 7 steps with test fix`() = runTest(testDispatcher) {
        var lastVibeProgress = validationEngine.runVibeCodingSandbox("usr_vibe_test").first()
        validationEngine.runVibeCodingSandbox("usr_vibe_test").collect { progress ->
            lastVibeProgress = progress
        }

        assertTrue("Vibe cycle must complete", lastVibeProgress.isComplete)
        assertEquals("Must execute 7 steps", 7, lastVibeProgress.steps.size)
        assertTrue("All steps must pass", lastVibeProgress.steps.all { it.status == ValidationStatus.PASSED })

        // Check that step 6 found test failure and step 7 applied fix
        val step6 = lastVibeProgress.steps[5]
        val step7 = lastVibeProgress.steps[6]
        assertEquals(false, step6.testPass)
        assertEquals(true, step7.testPass)
        assertTrue(step7.outputContent.contains("RE-RUN TESTS: ALL 5 TESTS PASSED"))
    }

    @Test
    fun `test crash investigation isolated sandbox executes all 7 steps on synthetic report`() = runTest(testDispatcher) {
        var lastCrashProgress = validationEngine.runCrashInvestigationSandbox("usr_crash_test").first()
        validationEngine.runCrashInvestigationSandbox("usr_crash_test").collect { progress ->
            lastCrashProgress = progress
        }

        assertTrue("Crash investigation cycle must complete", lastCrashProgress.isComplete)
        assertEquals("Must execute 7 steps", 7, lastCrashProgress.steps.size)
        assertTrue("All steps must be verified", lastCrashProgress.steps.all { it.verified })
        assertTrue("Patch diff must be generated", lastCrashProgress.patchDiff.contains("UserManager.kt"))
        assertTrue(lastCrashProgress.finalVerdict.contains("اكتشاف السبب الجذري واقتراح الحل"))
    }

    @Test
    fun `test validation report generates truthful metrics and markdown audit`() = runTest(testDispatcher) {
        val initialTests = validationEngine.getInitialTests()
        val report = validationEngine.generateValidationReport(initialTests, null, null)

        assertEquals(20, report.totalTests)
        assertEquals(0.0, report.totalActualCostUsd, 0.001)
        assertTrue(report.isFreeOnlyProtected)
        assertTrue(report.userKeyIsolationVerified)
        assertTrue(report.isolatedSandboxVerified)
        assertTrue("Report must list contacted models", report.modelsContacted.isNotEmpty())
        assertTrue("Report must document conflicts", report.conflictsDetected.isNotEmpty())

        val md = validationEngine.buildMarkdownReport(report)
        assertTrue(md.contains("تقرير التحقق الشامل لنظام فرق نماذج الذكاء الاصطناعي"))
        assertTrue(md.contains("SANDBOX DEMO"))
        assertTrue(md.contains("Google Gemini 3.8 Flash [Sandbox Demo]"))
        assertTrue(md.contains("OpenRouter Auto Free [Sandbox Demo]"))
        assertFalse(md.contains("gemini-1.5"))
    }

    @Test
    fun `test TeamValidationViewModel state transitions and tab switching`() = runTest(testDispatcher) {
        val fakeAuth = object : com.example.domain.repository.AuthRepository {
            override val currentUser: Flow<UserAccount?> = flowOf(UserAccount(id = "usr_vm_test", email = "test@example.com"))
            override fun getCurrentUserSync(): UserAccount? = UserAccount(id = "usr_vm_test", email = "test@example.com")
            override suspend fun signUp(email: String, password: String, displayName: String, organization: String): Result<UserAccount> = Result.success(UserAccount())
            override suspend fun signIn(email: String, password: String): Result<UserAccount> = Result.success(UserAccount())
            override suspend fun signOut() {}
            override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> = Result.success(Unit)
            override suspend fun signInWithGoogle(idToken: String): Result<Unit> = Result.success(Unit)
        }

        val viewModel = TeamValidationViewModel(validationEngine, fakeAuth)
        advanceUntilIdle()

        assertEquals(ValidationTab.MATRIX_20, viewModel.uiState.value.selectedTab)
        
        viewModel.selectTab(ValidationTab.VIBE_SANDBOX)
        assertEquals(ValidationTab.VIBE_SANDBOX, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(ValidationTab.REPORT)
        assertEquals(ValidationTab.REPORT, viewModel.uiState.value.selectedTab)
        assertNotNull(viewModel.uiState.value.summaryReport)

        // Expand toggle
        viewModel.toggleExpandTest(1)
        assertTrue(viewModel.uiState.value.expandedTestIds.contains(1))
        viewModel.toggleExpandTest(1)
        assertFalse(viewModel.uiState.value.expandedTestIds.contains(1))
    }
}

private class InMemoryAIModelTeamRepository : AIModelTeamRepository {
    private val teams = linkedMapOf<String, ModelTeam>()
    private val executions = linkedMapOf<String, TeamExecution>()
    private val usageRecords = linkedMapOf<String, TeamUsageRecord>()

    override fun getTeams(userId: String): Flow<List<ModelTeam>> =
        flowOf(teams.values.filter { it.ownerId == userId })

    override suspend fun getTeamById(teamId: String): ModelTeam? = teams[teamId]

    override suspend fun createTeam(team: ModelTeam): Result<String> = runCatching {
        teams[team.id] = team
        team.id
    }

    override suspend fun updateTeam(team: ModelTeam): Result<Unit> = runCatching {
        teams[team.id] = team
    }

    override suspend fun deleteTeam(teamId: String): Result<Unit> = runCatching {
        teams.remove(teamId)
        Unit
    }

    override fun getTeamExecutions(teamId: String): Flow<List<TeamExecution>> =
        flowOf(executions.values.filter { it.teamId == teamId })

    override suspend fun getExecutionById(executionId: String): TeamExecution? = executions[executionId]

    override suspend fun logExecution(execution: TeamExecution): Result<Unit> = runCatching {
        executions[execution.id] = execution
    }

    override suspend fun addTeamMember(teamId: String, member: TeamMember): Result<Unit> = Result.success(Unit)

    override suspend fun removeTeamMember(teamId: String, memberId: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateMemberStatus(
        teamId: String,
        memberId: String,
        status: MemberStatus,
        currentTask: String?,
        lastResult: String?
    ): Result<Unit> = Result.success(Unit)

    override fun getTeamUsageRecords(teamId: String): Flow<List<TeamUsageRecord>> =
        flowOf(usageRecords.values.filter { it.teamId == teamId })

    override fun getAllTeamUsageRecords(userId: String): Flow<List<TeamUsageRecord>> =
        flowOf(usageRecords.values.filter { it.userId == userId })

    override suspend fun logTeamUsage(record: TeamUsageRecord): Result<Unit> = runCatching {
        usageRecords[record.id] = record
    }
}
