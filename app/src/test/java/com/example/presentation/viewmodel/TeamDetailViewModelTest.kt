package com.example.presentation.viewmodel

import com.example.domain.agent.TeamOrchestrator
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TeamDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var teamRepository: FakeTeamRepository
    private lateinit var orchestrator: FakeTeamOrchestrator
    private lateinit var aiModelRepository: FakeAIModelRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepository = FakeAuthRepository(UserAccount(id = "user-1", email = "dev@vibe.test"))
        teamRepository = FakeTeamRepository()
        orchestrator = FakeTeamOrchestrator()
        aiModelRepository = FakeAIModelRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun unauthenticatedUserCannotLoadTeamAndStateIsCleared() = runTest(dispatcher) {
        authRepository.userFlow.value = null
        teamRepository.seedTeam(ModelTeam(id = "team-1", name = "Private team", ownerId = "user-1"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.team)
        assertEquals(0, teamRepository.getTeamByIdCalls)
    }

    @Test
    fun authenticatedUserLoadsTeamAndSignOutClearsTeamAndExecutionData() = runTest(dispatcher) {
        val team = ModelTeam(
            id = "team-1",
            name = "Vibe team",
            ownerId = "user-1",
            members = listOf(TeamMember(id = "member-1", name = "Architect"))
        )
        val execution = TeamExecution(
            id = "execution-1",
            teamId = "team-1",
            userId = "user-1",
            status = ExecutionStatus.COMPLETED
        )
        teamRepository.seedTeam(team)
        teamRepository.executions.value = listOf(execution)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAuthenticated)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(team, viewModel.uiState.value.team)
        assertEquals(listOf(execution), viewModel.uiState.value.executions)

        authRepository.signOut()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertNull(viewModel.uiState.value.team)
        assertTrue(viewModel.uiState.value.executions.isEmpty())
        assertNull(viewModel.uiState.value.currentExecution)
    }

    @Test
    fun governanceFailureDoesNotPublishUnsavedSettingsOrCloseDialog() = runTest(dispatcher) {
        val original = TeamGovernanceSettings(freeOnly = true, maxApiRequests = 20)
        val team = ModelTeam(id = "team-1", name = "Vibe team", ownerId = "user-1", governance = original)
        teamRepository.seedTeam(team)
        val viewModel = createViewModel()
        advanceUntilIdle()

        val requested = original.copy(freeOnly = false, maxApiRequests = 5)
        teamRepository.failNextUpdate = IOException("private transport detail")
        viewModel.setGovernanceDialogVisible(true)
        viewModel.updateGovernance(requested)
        advanceUntilIdle()

        assertEquals(original, viewModel.uiState.value.team?.governance)
        assertTrue(viewModel.uiState.value.showGovernanceDialog)
        assertFalse(viewModel.uiState.value.isMutating)
        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.error.orEmpty().contains("private transport detail"))

        viewModel.clearError()
        viewModel.updateGovernance(requested)
        advanceUntilIdle()

        assertEquals(requested, viewModel.uiState.value.team?.governance)
        assertFalse(viewModel.uiState.value.showGovernanceDialog)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun memberMutationsUpdateUiOnlyAfterRepositorySuccess() = runTest(dispatcher) {
        val team = ModelTeam(id = "team-1", name = "Vibe team", ownerId = "user-1")
        teamRepository.seedTeam(team)
        val viewModel = createViewModel()
        advanceUntilIdle()

        val member = TeamMember(id = "member-2", name = "Reviewer")
        teamRepository.failNextAdd = IOException("offline")
        viewModel.addMember(member)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.team?.members.orEmpty().isEmpty())
        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isMutating)

        viewModel.clearError()
        viewModel.addMember(member)
        advanceUntilIdle()
        assertEquals(listOf(member), viewModel.uiState.value.team?.members)

        teamRepository.failNextRemove = IOException("offline")
        viewModel.removeMember(member.id)
        advanceUntilIdle()
        assertEquals(listOf(member), viewModel.uiState.value.team?.members)
        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()
        viewModel.removeMember(member.id)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.team?.members.orEmpty().isEmpty())
    }

    @Test
    fun estimationAndExecutionFailuresAreSurfacedAndLoadingFlagsReset() = runTest(dispatcher) {
        teamRepository.seedTeam(ModelTeam(id = "team-1", name = "Vibe team", ownerId = "user-1"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateTaskPrompt("  Build a feature  ")

        orchestrator.estimationFailure = IOException("offline")
        viewModel.requestEstimation()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEstimating)
        assertFalse(viewModel.uiState.value.showEstimationDialog)
        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()
        orchestrator.executionFailure = IOException("offline")
        viewModel.executeTeamTask()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isExecuting)
        assertNull(viewModel.uiState.value.currentExecution)
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun signOutCancelsInFlightExecutionAndClearsItsResult() = runTest(dispatcher) {
        teamRepository.seedTeam(ModelTeam(id = "team-1", name = "Vibe team", ownerId = "user-1"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        orchestrator.holdExecutionOpen = true
        viewModel.updateTaskPrompt("Run a long task")
        viewModel.executeTeamTask()
        runCurrent()

        assertTrue(viewModel.uiState.value.isExecuting)
        assertNotNull(viewModel.uiState.value.currentExecution)

        authRepository.signOut()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertFalse(viewModel.uiState.value.isExecuting)
        assertNull(viewModel.uiState.value.team)
        assertNull(viewModel.uiState.value.currentExecution)
    }

    @Test
    fun memberCapabilitiesAreLoadedFromTheAuthenticatedUsersModelCatalog() = runTest(dispatcher) {
        val member = TeamMember(
            id = "member-1",
            name = "Engineer",
            modelId = "model-1",
            providerId = "provider-1"
        )
        teamRepository.seedTeam(
            ModelTeam(id = "team-1", name = "Vibe team", ownerId = "user-1", members = listOf(member))
        )
        aiModelRepository.models["user-1" to "model-1"] = AIModel(
            id = "model-1",
            providerId = "provider-1",
            apiId = "coding-model",
            name = "Coding Model",
            capabilities = listOf(AICapability.CODING, AICapability.VISION)
        )

        val viewModel = TeamDetailViewModel(
            "team-1",
            teamRepository,
            orchestrator,
            authRepository,
            aiModelRepository
        )
        advanceUntilIdle()

        assertEquals(
            listOf(AICapability.CODING, AICapability.VISION),
            viewModel.uiState.value.memberCapabilities["member-1"]
        )

        authRepository.userFlow.value = UserAccount(id = "user-2", email = "other@vibe.test")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.memberCapabilities.isEmpty())
    }

    @Test
    fun invalidTeamIdDoesNotCallRepository() = runTest(dispatcher) {
        val viewModel = TeamDetailViewModel("", teamRepository, orchestrator, authRepository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.team)
        assertNotNull(viewModel.uiState.value.loadError)
        assertEquals(0, teamRepository.getTeamByIdCalls)
    }

    private fun createViewModel() =
        TeamDetailViewModel("team-1", teamRepository, orchestrator, authRepository)

    private class FakeTeamRepository : AIModelTeamRepository {
        private val teamState = MutableStateFlow<ModelTeam?>(null)
        val executions = MutableStateFlow<List<TeamExecution>>(emptyList())
        var failNextUpdate: Throwable? = null
        var failNextAdd: Throwable? = null
        var failNextRemove: Throwable? = null
        var getTeamByIdCalls = 0

        fun seedTeam(team: ModelTeam) {
            teamState.value = team
        }

        override fun getTeams(userId: String): Flow<List<ModelTeam>> =
            flowOf(listOfNotNull(teamState.value).filter { it.ownerId == userId })

        override suspend fun getTeamById(teamId: String): ModelTeam? {
            getTeamByIdCalls++
            return teamState.value?.takeIf { it.id == teamId }
        }

        override suspend fun createTeam(team: ModelTeam): Result<String> = Result.success(team.id)

        override suspend fun updateTeam(team: ModelTeam): Result<Unit> {
            failNextUpdate?.let { failure ->
                failNextUpdate = null
                return Result.failure(failure)
            }
            teamState.value = team
            return Result.success(Unit)
        }

        override suspend fun deleteTeam(teamId: String): Result<Unit> = Result.success(Unit)

        override fun getTeamExecutions(teamId: String): Flow<List<TeamExecution>> =
            executions.map { list -> list.filter { it.teamId == teamId } }

        override suspend fun getExecutionById(executionId: String): TeamExecution? = null
        override suspend fun logExecution(execution: TeamExecution): Result<Unit> = Result.success(Unit)

        override suspend fun addTeamMember(teamId: String, member: TeamMember): Result<Unit> {
            failNextAdd?.let { failure ->
                failNextAdd = null
                return Result.failure(failure)
            }
            val team = teamState.value ?: return Result.failure(NoSuchElementException("Team not found"))
            teamState.value = team.copy(members = team.members + member)
            return Result.success(Unit)
        }

        override suspend fun removeTeamMember(teamId: String, memberId: String): Result<Unit> {
            failNextRemove?.let { failure ->
                failNextRemove = null
                return Result.failure(failure)
            }
            val team = teamState.value ?: return Result.failure(NoSuchElementException("Team not found"))
            teamState.value = team.copy(members = team.members.filterNot { it.id == memberId })
            return Result.success(Unit)
        }

        override suspend fun updateMemberStatus(
            teamId: String,
            memberId: String,
            status: MemberStatus,
            currentTask: String?,
            lastResult: String?
        ): Result<Unit> = Result.success(Unit)

        override fun getTeamUsageRecords(teamId: String): Flow<List<TeamUsageRecord>> = flowOf(emptyList())
        override fun getAllTeamUsageRecords(userId: String): Flow<List<TeamUsageRecord>> = flowOf(emptyList())
        override suspend fun logTeamUsage(record: TeamUsageRecord): Result<Unit> = Result.success(Unit)
    }

    private class FakeAIModelRepository : AIModelRepository {
        val models = mutableMapOf<Pair<String, String>, AIModel>()

        override fun getProviders(userId: String): Flow<List<AIProvider>> = flowOf(emptyList())
        override suspend fun getProviderById(userId: String, id: String): AIProvider? = null
        override suspend fun addProvider(provider: AIProvider) = Unit
        override suspend fun deleteProvider(userId: String, id: String) = Unit
        override suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long) = Unit
        override fun getModelsByProvider(userId: String, providerId: String): Flow<List<AIModel>> =
            flowOf(models.filterKeys { it.first == userId }.values.filter { it.providerId == providerId })
        override fun getAllAvailableModels(userId: String, freeOnly: Boolean): Flow<List<AIModel>> =
            flowOf(models.filterKeys { it.first == userId }.values.filter { !freeOnly || it.isFree })
        override suspend fun getModelById(userId: String, id: String): AIModel? = models[userId to id]
        override suspend fun syncModels(userId: String, providerId: String): Result<Unit> = Result.success(Unit)
        override suspend fun testConnection(userId: String, providerId: String): Result<Unit> = Result.success(Unit)
        override fun getRecentUsageLogs(userId: String): Flow<List<AIUsageLog>> = flowOf(emptyList())
        override fun getTotalCost(userId: String, since: Long): Flow<Double?> = flowOf(0.0)
        override suspend fun logUsage(log: AIUsageLog) = Unit
        override fun getUserSettings(userId: String): Flow<AIUserSettings?> = flowOf(null)
        override suspend fun updateUserSettings(settings: AIUserSettings) = Unit
        override suspend fun setDefaultModel(userId: String, modelId: String) = Unit
        override suspend fun getDefaultModel(userId: String): AIModel? = null
        override suspend fun generateResponse(
            userId: String,
            modelId: String,
            prompt: String,
            systemInstruction: String?,
            taskId: String?,
            taskType: AITaskType
        ): Result<String> = Result.success("ok")
    }

    private class FakeTeamOrchestrator : TeamOrchestrator {
        var estimationFailure: Throwable? = null
        var executionFailure: Throwable? = null
        var holdExecutionOpen: Boolean = false

        override fun executeTask(userId: String, team: ModelTeam, prompt: String): Flow<TeamExecution> = flow {
            executionFailure?.let { throw it }
            emit(TeamExecution(id = "execution-1", teamId = team.id, userId = userId, prompt = prompt))
            if (holdExecutionOpen) awaitCancellation()
        }

        override fun executeTaskWithContext(
            userId: String,
            team: ModelTeam,
            prompt: String,
            attachedFiles: List<AttachedFileContext>,
            projectId: String?
        ): Flow<TeamExecution> = executeTask(userId, team, prompt)

        override suspend fun pauseExecution(executionId: String) = Unit

        override suspend fun resumeExecution(
            executionId: String,
            userId: String,
            team: ModelTeam
        ): Flow<TeamExecution> = flowOf(TeamExecution(id = executionId, teamId = team.id, userId = userId))

        override suspend fun stopExecution(executionId: String) = Unit

        override suspend fun retryFailedSteps(
            executionId: String,
            userId: String,
            team: ModelTeam
        ): Flow<TeamExecution> = resumeExecution(executionId, userId, team)

        override suspend fun assignDirectTask(
            executionId: String,
            userId: String,
            team: ModelTeam,
            memberId: String,
            taskPrompt: String
        ): Flow<TeamExecution> = resumeExecution(executionId, userId, team)

        override suspend fun sendInstruction(
            executionId: String,
            userId: String,
            team: ModelTeam,
            instruction: TeamInstruction
        ): Flow<TeamExecution> = resumeExecution(executionId, userId, team)

        override suspend fun changeMemberAgent(teamId: String, memberId: String, newModelId: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun planAndDistribute(team: ModelTeam, prompt: String): Result<List<ExecutionStep>> =
            Result.success(emptyList())

        override suspend fun estimateTaskResources(
            userId: String,
            team: ModelTeam,
            prompt: String
        ): PreExecutionEstimation {
            estimationFailure?.let { throw it }
            return PreExecutionEstimation(estimatedTasksCount = 1)
        }
    }
}
