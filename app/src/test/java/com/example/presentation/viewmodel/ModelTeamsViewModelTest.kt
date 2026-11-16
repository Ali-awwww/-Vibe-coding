package com.example.presentation.viewmodel

import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
class ModelTeamsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authRepo: FakeAuthRepository
    private lateinit var teamRepo: FakeReactiveTeamRepository
    private lateinit var aiRepo: FakeTeamsAIModelRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        authRepo = FakeAuthRepository(UserAccount(id = "user-1", email = "dev@vibe.test", displayName = "Dev"))
        teamRepo = FakeReactiveTeamRepository()
        aiRepo = FakeTeamsAIModelRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `unauthenticated state blocks team mutations and clears previous user data on signOut`() = runTest(testDispatcher) {
        teamRepo.seedTeam(
            ModelTeam(
                id = "team-1",
                name = "User 1 Team",
                ownerId = "user-1",
                members = listOf(TeamMember(id = "m1", name = "Coder", modelId = "mod-1", providerId = "prov-conn"))
            )
        )
        aiRepo.providersFlow.value = listOf(
            AIProvider(id = "prov-conn", userId = "user-1", name = "OpenRouter", status = AIProviderStatus.CONNECTED)
        )
        aiRepo.modelsFlow.value = listOf(
            AIModel(id = "mod-1", providerId = "prov-conn", apiId = "free/coder", name = "Free Coder", isFree = true)
        )

        val viewModel = ModelTeamsViewModel(teamRepo, aiRepo, authRepo)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAuthenticated)
        assertEquals(1, viewModel.uiState.value.teams.size)
        assertTrue(viewModel.uiState.value.connectedProviderIds.contains("prov-conn"))

        authRepo.signOut()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertTrue(viewModel.uiState.value.teams.isEmpty())
        assertTrue(viewModel.uiState.value.availableModels.isEmpty())
        assertTrue(viewModel.uiState.value.connectedProviderIds.isEmpty())

        viewModel.startCreateTeam()
        assertEquals(ModelTeamsViewModel.AUTH_REQUIRED_MESSAGE, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isCreating)
    }

    @Test
    fun `saveTeam validates blank name duplicate name empty members and preserves draft on repository failure`() = runTest(testDispatcher) {
        teamRepo.seedTeam(ModelTeam(id = "existing", name = "Existing Team", ownerId = "user-1"))
        val model = AIModel(id = "mod-1", providerId = "prov-conn", apiId = "free/coder", name = "Free Coder", isFree = true)
        aiRepo.modelsFlow.value = listOf(model)

        val viewModel = ModelTeamsViewModel(teamRepo, aiRepo, authRepo)
        advanceUntilIdle()

        viewModel.startCreateTeam()
        viewModel.updateNewTeamInfo("   ", "desc", "goal")
        viewModel.saveTeam()
        assertNotNull(viewModel.uiState.value.nameError)

        viewModel.updateNewTeamInfo("  existing team ", "desc", "goal")
        viewModel.saveTeam()
        assertNotNull(viewModel.uiState.value.nameError)

        viewModel.updateNewTeamInfo("  New Alpha Team  ", "desc", "goal")
        viewModel.saveTeam()
        assertNotNull(viewModel.uiState.value.membersError)

        viewModel.addMemberToNewTeam(model, TeamRole.CODE_REVIEWER)
        assertEquals(1, viewModel.uiState.value.selectedMembers.size)

        // Duplicate (modelId, role) rejected
        viewModel.addMemberToNewTeam(model, TeamRole.CODE_REVIEWER)
        assertEquals(1, viewModel.uiState.value.selectedMembers.size)
        assertNotNull(viewModel.uiState.value.error)

        // Repository write failure preserves draft
        teamRepo.failNextWrite = IllegalStateException("Firestore unavailable")
        viewModel.saveTeam()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCreating)
        assertEquals("  New Alpha Team  ", viewModel.uiState.value.newTeamName)
        assertEquals("Firestore unavailable", viewModel.uiState.value.error)

        // Successful save trims name and clears draft
        viewModel.saveTeam()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isCreating)
        assertEquals("", viewModel.uiState.value.newTeamName)
        assertTrue(viewModel.uiState.value.teams.any { it.name == "New Alpha Team" && it.ownerId == "user-1" })
    }

    @Test
    fun `useTemplate generates unique team name on collisions and delete requires confirmation`() = runTest(testDispatcher) {
        val template = TeamTemplates.CRASH_INVESTIGATION_TEAM
        teamRepo.seedTeam(ModelTeam(id = "t-existing", name = template.name, ownerId = "user-1"))

        val viewModel = ModelTeamsViewModel(teamRepo, aiRepo, authRepo)
        advanceUntilIdle()

        viewModel.useTemplate(template)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.teams.any { it.name == "${template.name} (2)" })

        val teamToDelete = viewModel.uiState.value.teams.first { it.id == "t-existing" }
        viewModel.requestDeleteTeam(teamToDelete)
        assertEquals("t-existing", viewModel.uiState.value.pendingDeleteTeam?.id)

        viewModel.confirmDeleteTeam()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingDeleteTeam)
        assertFalse(viewModel.uiState.value.teams.any { it.id == "t-existing" })
    }

    private class FakeReactiveTeamRepository : AIModelTeamRepository {
        private val teamsState = MutableStateFlow<List<ModelTeam>>(emptyList())
        private val executionsState = MutableStateFlow<List<TeamExecution>>(emptyList())
        var failNextWrite: Throwable? = null

        fun seedTeam(team: ModelTeam) {
            teamsState.value = teamsState.value + team
        }

        override fun getTeams(userId: String): Flow<List<ModelTeam>> =
            teamsState.map { list -> list.filter { it.ownerId == userId } }

        override suspend fun getTeamById(teamId: String): ModelTeam? =
            teamsState.value.find { it.id == teamId }

        override suspend fun createTeam(team: ModelTeam): Result<String> {
            failNextWrite?.let { failNextWrite = null; return Result.failure(it) }
            teamsState.value = teamsState.value + team
            return Result.success(team.id)
        }

        override suspend fun updateTeam(team: ModelTeam): Result<Unit> {
            failNextWrite?.let { failNextWrite = null; return Result.failure(it) }
            teamsState.value = teamsState.value.map { if (it.id == team.id) team else it }
            return Result.success(Unit)
        }

        override suspend fun deleteTeam(teamId: String): Result<Unit> {
            failNextWrite?.let { failNextWrite = null; return Result.failure(it) }
            teamsState.value = teamsState.value.filterNot { it.id == teamId }
            return Result.success(Unit)
        }

        override fun getTeamExecutions(teamId: String): Flow<List<TeamExecution>> =
            executionsState.map { list -> list.filter { it.teamId == teamId } }

        override suspend fun getExecutionById(executionId: String): TeamExecution? = null
        override suspend fun logExecution(execution: TeamExecution): Result<Unit> = Result.success(Unit)
        override suspend fun addTeamMember(teamId: String, member: TeamMember): Result<Unit> = Result.success(Unit)
        override suspend fun removeTeamMember(teamId: String, memberId: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateMemberStatus(teamId: String, memberId: String, status: MemberStatus, currentTask: String?, lastResult: String?): Result<Unit> = Result.success(Unit)
        override fun getTeamUsageRecords(teamId: String): Flow<List<TeamUsageRecord>> = flowOf(emptyList())
        override fun getAllTeamUsageRecords(userId: String): Flow<List<TeamUsageRecord>> = flowOf(emptyList())
        override suspend fun logTeamUsage(record: TeamUsageRecord): Result<Unit> = Result.success(Unit)
    }

    private class FakeTeamsAIModelRepository : AIModelRepository {
        val providersFlow = MutableStateFlow<List<AIProvider>>(emptyList())
        val modelsFlow = MutableStateFlow<List<AIModel>>(emptyList())

        override fun getProviders(userId: String): Flow<List<AIProvider>> =
            providersFlow.map { list -> list.filter { it.userId == userId } }

        override suspend fun getProviderById(userId: String, id: String): AIProvider? = null
        override suspend fun addProvider(provider: AIProvider) {}
        override suspend fun deleteProvider(userId: String, id: String) {}
        override suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long) {}
        override fun getModelsByProvider(userId: String, providerId: String): Flow<List<AIModel>> = flowOf(emptyList())
        override fun getAllAvailableModels(userId: String, freeOnly: Boolean): Flow<List<AIModel>> = modelsFlow
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
        override suspend fun generateResponse(userId: String, modelId: String, prompt: String, systemInstruction: String?, taskId: String?, taskType: AITaskType): Result<String> = Result.success("ok")
    }
}
