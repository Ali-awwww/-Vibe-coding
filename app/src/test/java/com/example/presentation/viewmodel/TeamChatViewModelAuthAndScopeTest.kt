package com.example.presentation.viewmodel

import com.example.domain.agent.TeamOrchestrator
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeamChatViewModelAuthAndScopeTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authRepo: FakeAuthRepository
    private lateinit var crashRepo: FakeCrashRepository
    private lateinit var teamRepo: RecordingTeamRepo
    private lateinit var orchestrator: RecordingOrchestrator
    private lateinit var aiRepo: StubAiModelRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        authRepo = FakeAuthRepository(null)
        crashRepo = FakeCrashRepository()
        teamRepo = RecordingTeamRepo()
        orchestrator = RecordingOrchestrator()
        aiRepo = StubAiModelRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `unauthenticated initialization skips scoped queries and clears session on signOut`() = runTest(testDispatcher) {
        val team1 = ModelTeam(id = "team-1", name = "User Team", ownerId = "user-1")
        teamRepo.teamsFlow.value = listOf(team1)
        crashRepo.projects.value = listOf(
            Project(id = "p-own", name = "Owned Project", ownerId = "user-1"),
            Project(id = "p-other", name = "Other User Project", ownerId = "user-2")
        )

        val viewModel = TeamChatViewModel(
            initialTeamId = "team-1",
            initialProjectId = null,
            teamRepository = teamRepo,
            teamOrchestrator = orchestrator,
            crashRepository = crashRepo,
            aiModelRepository = aiRepo,
            authRepository = authRepo
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertEquals(0, crashRepo.getProjectsForUserCalls)
        assertTrue(viewModel.uiState.value.teams.isEmpty())
        assertTrue(viewModel.uiState.value.projects.isEmpty())

        // Sign in user-1
        authRepo.userFlow.value = UserAccount(id = "user-1", email = "u1@test.dev")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAuthenticated)
        assertTrue(crashRepo.getProjectsForUserCalls > 0)
        assertEquals(listOf("p-own"), viewModel.uiState.value.projects.map { it.id })
        assertEquals("team-1", viewModel.uiState.value.selectedTeam?.id)

        // Sign out wipes previous team, projects, and active state
        authRepo.signOut()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertTrue(viewModel.uiState.value.teams.isEmpty())
        assertTrue(viewModel.uiState.value.projects.isEmpty())
        assertNull(viewModel.uiState.value.selectedTeam)
        assertNull(viewModel.uiState.value.selectedProject)
    }

    @Test
    fun `blank prompts and unauthenticated sendTask fail cleanly without calling orchestrator`() = runTest(testDispatcher) {
        val team1 = ModelTeam(id = "team-1", name = "Alpha", ownerId = "user-1")
        teamRepo.teamsFlow.value = listOf(team1)
        authRepo.userFlow.value = UserAccount(id = "user-1", email = "u1@test.dev")

        val viewModel = TeamChatViewModel(
            initialTeamId = "team-1",
            initialProjectId = null,
            teamRepository = teamRepo,
            teamOrchestrator = orchestrator,
            crashRepository = crashRepo,
            aiModelRepository = aiRepo,
            authRepository = authRepo
        )
        advanceUntilIdle()

        viewModel.setTaskPrompt("   ")
        viewModel.sendTask(skipEstimationCheck = true)
        assertEquals(0, orchestrator.executeCalls)
        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()
        viewModel.assignDirectTask("mem-1", "   ")
        assertEquals(0, orchestrator.directAssignCalls)
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `switching team loads new execution history and resets unrelated execution`() = runTest(testDispatcher) {
        val team1 = ModelTeam(id = "team-1", name = "Team One", ownerId = "user-1")
        val team2 = ModelTeam(id = "team-2", name = "Team Two", ownerId = "user-1")
        teamRepo.teamsFlow.value = listOf(team1, team2)
        teamRepo.executionsByTeam["team-1"] = MutableStateFlow(
            listOf(TeamExecution(id = "e1", teamId = "team-1", userId = "user-1", prompt = "First", status = ExecutionStatus.COMPLETED))
        )
        teamRepo.executionsByTeam["team-2"] = MutableStateFlow(
            listOf(TeamExecution(id = "e2", teamId = "team-2", userId = "user-1", prompt = "Second", status = ExecutionStatus.COMPLETED))
        )
        authRepo.userFlow.value = UserAccount(id = "user-1", email = "u1@test.dev")

        val viewModel = TeamChatViewModel(
            initialTeamId = "team-1",
            initialProjectId = null,
            teamRepository = teamRepo,
            teamOrchestrator = orchestrator,
            crashRepository = crashRepo,
            aiModelRepository = aiRepo,
            authRepository = authRepo
        )
        advanceUntilIdle()
        assertEquals("e1", viewModel.uiState.value.currentExecution?.id)

        viewModel.selectTeam(team2)
        advanceUntilIdle()
        assertEquals("team-2", viewModel.uiState.value.selectedTeam?.id)
        assertEquals("e2", viewModel.uiState.value.currentExecution?.id)
    }

    private class RecordingTeamRepo : AIModelTeamRepository {
        val teamsFlow = MutableStateFlow<List<ModelTeam>>(emptyList())
        val executionsByTeam = mutableMapOf<String, MutableStateFlow<List<TeamExecution>>>()

        override fun getTeams(userId: String): Flow<List<ModelTeam>> = teamsFlow
        override suspend fun getTeamById(teamId: String): ModelTeam? = teamsFlow.value.find { it.id == teamId }
        override suspend fun createTeam(team: ModelTeam): Result<String> = Result.success(team.id)
        override suspend fun updateTeam(team: ModelTeam): Result<Unit> = Result.success(Unit)
        override suspend fun deleteTeam(teamId: String): Result<Unit> = Result.success(Unit)
        override fun getTeamExecutions(teamId: String): Flow<List<TeamExecution>> =
            executionsByTeam.getOrPut(teamId) { MutableStateFlow(emptyList()) }
        override suspend fun getExecutionById(executionId: String): TeamExecution? = null
        override suspend fun logExecution(execution: TeamExecution): Result<Unit> = Result.success(Unit)
        override suspend fun addTeamMember(teamId: String, member: TeamMember): Result<Unit> = Result.success(Unit)
        override suspend fun removeTeamMember(teamId: String, memberId: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateMemberStatus(teamId: String, memberId: String, status: MemberStatus, currentTask: String?, lastResult: String?): Result<Unit> = Result.success(Unit)
        override fun getTeamUsageRecords(teamId: String): Flow<List<TeamUsageRecord>> = flowOf(emptyList())
        override fun getAllTeamUsageRecords(userId: String): Flow<List<TeamUsageRecord>> = flowOf(emptyList())
        override suspend fun logTeamUsage(record: TeamUsageRecord): Result<Unit> = Result.success(Unit)
    }

    private class RecordingOrchestrator : TeamOrchestrator {
        var executeCalls = 0
        var directAssignCalls = 0

        override fun executeTask(userId: String, team: ModelTeam, prompt: String): Flow<TeamExecution> =
            executeTaskWithContext(userId, team, prompt)

        override fun executeTaskWithContext(
            userId: String,
            team: ModelTeam,
            prompt: String,
            attachedFiles: List<AttachedFileContext>,
            projectId: String?
        ): Flow<TeamExecution> {
            executeCalls++
            return flowOf(TeamExecution(id = "exec-new", teamId = team.id, userId = userId, prompt = prompt, status = ExecutionStatus.COMPLETED))
        }

        override suspend fun pauseExecution(executionId: String) {}
        override suspend fun resumeExecution(executionId: String, userId: String, team: ModelTeam) =
            flowOf(TeamExecution(id = executionId, status = ExecutionStatus.EXECUTING))
        override suspend fun stopExecution(executionId: String) {}
        override suspend fun retryFailedSteps(executionId: String, userId: String, team: ModelTeam) =
            flowOf(TeamExecution(id = executionId, status = ExecutionStatus.EXECUTING))
        override suspend fun assignDirectTask(
            executionId: String,
            userId: String,
            team: ModelTeam,
            memberId: String,
            taskPrompt: String
        ): Flow<TeamExecution> {
            directAssignCalls++
            return flowOf(TeamExecution(id = executionId, teamId = team.id, status = ExecutionStatus.EXECUTING))
        }
        override suspend fun sendInstruction(executionId: String, userId: String, team: ModelTeam, instruction: TeamInstruction) =
            flowOf(TeamExecution(id = executionId, teamId = team.id))
        override suspend fun changeMemberAgent(teamId: String, memberId: String, newModelId: String) = Result.success(Unit)
        override suspend fun planAndDistribute(team: ModelTeam, prompt: String) = Result.success(emptyList<ExecutionStep>())
        override suspend fun estimateTaskResources(userId: String, team: ModelTeam, prompt: String) = PreExecutionEstimation()
    }

    private class StubAiModelRepository : AIModelRepository {
        override fun getProviders(userId: String): Flow<List<AIProvider>> = flowOf(emptyList())
        override suspend fun getProviderById(userId: String, id: String): AIProvider? = null
        override suspend fun addProvider(provider: AIProvider) {}
        override suspend fun deleteProvider(userId: String, id: String) {}
        override suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long) {}
        override fun getModelsByProvider(userId: String, providerId: String): Flow<List<AIModel>> = flowOf(emptyList())
        override fun getAllAvailableModels(userId: String, freeOnly: Boolean): Flow<List<AIModel>> = flowOf(emptyList())
        override suspend fun getModelById(userId: String, id: String): AIModel? = null
        override suspend fun syncModels(userId: String, providerId: String): Result<Unit> = Result.success(Unit)
        override suspend fun testConnection(userId: String, providerId: String): Result<Unit> = Result.success(Unit)
        override fun getRecentUsageLogs(userId: String): Flow<List<AIUsageLog>> = flowOf(emptyList())
        override fun getTotalCost(userId: String, since: Long): Flow<Double?> = flowOf(0.0)
        override suspend fun logUsage(log: AIUsageLog) {}
        override fun getUserSettings(userId: String): Flow<AIUserSettings?> = flowOf(null)
        override suspend fun updateUserSettings(settings: AIUserSettings) {}
        override suspend fun setDefaultModel(userId: String, modelId: String) {}
        override suspend fun getDefaultModel(userId: String): AIModel? = null
        override suspend fun generateResponse(userId: String, modelId: String, prompt: String, systemInstruction: String?, taskId: String?, taskType: AITaskType) = Result.success("")
    }
}
