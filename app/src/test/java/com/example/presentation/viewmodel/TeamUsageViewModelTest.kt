package com.example.presentation.viewmodel

import com.example.domain.model.*
import com.example.domain.repository.AIModelTeamRepository
import java.io.IOException
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
class TeamUsageViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var teamRepository: FakeTeamRepository
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepository = FakeAuthRepository(UserAccount(id = "user-1", email = "dev@vibe.test"))
        teamRepository = FakeTeamRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun missingTelemetryIsNotConvertedIntoMemberOrZeroUsageClaims() = runTest(dispatcher) {
        teamRepository.seedTeam(
            ModelTeam(
                id = "team-1",
                name = "Vibe team",
                ownerId = "user-1",
                members = listOf(TeamMember(id = "member-1", name = "Engineer"))
            )
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.hasMeasuredUsage)
        assertTrue(viewModel.uiState.value.memberSummaries.isEmpty())
        assertTrue(viewModel.uiState.value.usageRecords.isEmpty())
        assertNull(viewModel.uiState.value.lastMeasuredAt)
    }

    @Test
    fun aggregatesMeasuredRecordsAndSeparatesUnconfirmedCost() = runTest(dispatcher) {
        teamRepository.seedTeam(
            ModelTeam(
                id = "team-1",
                name = "Vibe team",
                ownerId = "user-1",
                governance = TeamGovernanceSettings(freeOnly = true),
                members = listOf(TeamMember(id = "member-1", name = "Engineer"))
            )
        )
        teamRepository.usageRecords.value = listOf(
            record(
                id = "usage-1",
                modelId = "free-model",
                modelName = "Free Model",
                providerId = "provider-a",
                providerName = "Provider A",
                isFree = true,
                isCostConfirmed = true,
                costUsd = 0.0,
                tokensInput = 10,
                tokensOutput = 5,
                totalTokens = 15,
                timestamp = now - 1_000
            ),
            record(
                id = "usage-2",
                modelId = "paid-model",
                modelName = "Paid Model",
                providerId = "provider-b",
                providerName = "Provider B",
                isFree = false,
                isCostConfirmed = false,
                costUsd = 0.025,
                tokensInput = 20,
                tokensOutput = 5,
                totalTokens = 25,
                status = "SUCCESS",
                timestamp = now - 500
            ),
            record(
                id = "usage-3",
                modelId = "free-model",
                modelName = "Free Model",
                providerId = "provider-a",
                providerName = "Provider A",
                isFree = true,
                isCostConfirmed = true,
                costUsd = 0.0,
                tokensInput = 0,
                tokensOutput = 0,
                totalTokens = 0,
                status = "RATE_LIMITED",
                timestamp = now - 250
            )
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val summary = state.memberSummaries.single()
        assertTrue(state.hasMeasuredUsage)
        assertEquals(3, state.totalRequests)
        assertEquals(40, state.totalTokens)
        assertEquals(0.0, state.totalCostUsd, 0.0001)
        assertEquals(0.025, state.unconfirmedCostUsd, 0.0001)
        assertTrue(state.hasConfirmedCost)
        assertTrue(state.hasPaidUsageUnderFreeOnly)
        assertFalse(summary.isFreeModel)
        assertFalse(summary.isCostConfirmed)
        assertEquals("عدة نماذج", summary.modelName)
        assertEquals("عدة مزودين", summary.providerName)
        assertEquals(1, state.rateLimitHits)
    }

    @Test
    fun blockedPaidAttemptIsNotReportedAsSuccessfulPaidUsage() = runTest(dispatcher) {
        teamRepository.seedTeam(
            ModelTeam(
                id = "team-1",
                name = "Vibe team",
                ownerId = "user-1",
                governance = TeamGovernanceSettings(freeOnly = true)
            )
        )
        teamRepository.usageRecords.value = listOf(
            record(
                id = "blocked-paid",
                isFree = false,
                isCostConfirmed = true,
                costUsd = 0.0,
                tokensInput = 0,
                tokensOutput = 0,
                totalTokens = 0,
                status = "BLOCKED_PAID"
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasMeasuredUsage)
        assertTrue(viewModel.uiState.value.memberSummaries.isEmpty())
        assertFalse(viewModel.uiState.value.hasPaidUsageUnderFreeOnly)
    }

    @Test
    fun dateRangeFiltersMeasuredRecordsAndDerivedTotals() = runTest(dispatcher) {
        teamRepository.seedTeam(ModelTeam(id = "team-1", name = "Vibe team", ownerId = "user-1"))
        teamRepository.usageRecords.value = listOf(
            record(id = "old", timestamp = now - 8L * 24 * 60 * 60 * 1000),
            record(id = "recent", timestamp = now - 2L * 24 * 60 * 60 * 1000)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.totalRequests)
        viewModel.updatePeriod(TeamUsagePeriod.LAST_7_DAYS)

        assertEquals(1, viewModel.uiState.value.totalRequests)
        assertEquals(listOf("recent"), viewModel.uiState.value.filteredRecords.map { it.id })
        assertEquals(TeamUsagePeriod.LAST_7_DAYS, viewModel.uiState.value.selectedPeriod)
    }

    @Test
    fun signedOutAndForeignTeamStatesExposeNoUsageRecords() = runTest(dispatcher) {
        teamRepository.seedTeam(ModelTeam(id = "team-1", name = "Private", ownerId = "user-2"))
        teamRepository.usageRecords.value = listOf(record(id = "foreign-record"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.team)
        assertTrue(viewModel.uiState.value.usageRecords.isEmpty())
        assertFalse(viewModel.uiState.value.hasMeasuredUsage)

        authRepository.signOut()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertTrue(viewModel.uiState.value.usageRecords.isEmpty())
        assertNull(viewModel.uiState.value.team)
    }

    @Test
    fun failedGovernanceWriteDoesNotPublishOrCloseSuccessfulCallback() = runTest(dispatcher) {
        val original = TeamGovernanceSettings(maxApiRequests = 20)
        teamRepository.seedTeam(
            ModelTeam(id = "team-1", name = "Vibe team", ownerId = "user-1", governance = original)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        var saved = false
        val requested = original.copy(maxApiRequests = 5)
        teamRepository.failNextUpdate = IOException("private backend detail")

        viewModel.updateTeamGovernance(requested) { saved = true }
        advanceUntilIdle()

        assertEquals(original, viewModel.uiState.value.team?.governance)
        assertFalse(saved)
        assertFalse(viewModel.uiState.value.isMutating)
        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()
        viewModel.updateTeamGovernance(requested) { saved = true }
        advanceUntilIdle()

        assertEquals(requested, viewModel.uiState.value.team?.governance)
        assertTrue(saved)
        assertNull(viewModel.uiState.value.error)
    }

    private fun createViewModel() = TeamUsageViewModel(
        teamId = "team-1",
        teamRepository = teamRepository,
        authRepository = authRepository,
        clock = { now }
    )

    private fun record(
        id: String,
        modelId: String = "model-1",
        modelName: String = "Model",
        providerId: String = "provider-1",
        providerName: String = "Provider",
        isFree: Boolean = true,
        isCostConfirmed: Boolean = true,
        costUsd: Double = 0.0,
        tokensInput: Int = 10,
        tokensOutput: Int = 5,
        totalTokens: Int = tokensInput + tokensOutput,
        status: String = "SUCCESS",
        timestamp: Long = now - 1_000
    ) = TeamUsageRecord(
        id = id,
        userId = "user-1",
        teamId = "team-1",
        memberId = "member-1",
        memberName = "Engineer",
        roleName = TeamRole.SENIOR_DEVELOPER.name,
        modelId = modelId,
        modelName = modelName,
        providerId = providerId,
        providerName = providerName,
        tokensInput = tokensInput,
        tokensOutput = tokensOutput,
        totalTokens = totalTokens,
        costUsd = costUsd,
        isFree = isFree,
        isCostConfirmed = isCostConfirmed,
        status = status,
        timestamp = timestamp
    )

    private class FakeTeamRepository : AIModelTeamRepository {
        private val teamState = MutableStateFlow<ModelTeam?>(null)
        val usageRecords = MutableStateFlow<List<TeamUsageRecord>>(emptyList())
        val executions = MutableStateFlow<List<TeamExecution>>(emptyList())
        var failNextUpdate: Throwable? = null

        fun seedTeam(team: ModelTeam) {
            teamState.value = team
        }

        override fun getTeams(userId: String): Flow<List<ModelTeam>> =
            teamState.map { team -> listOfNotNull(team).filter { it.ownerId == userId } }

        override suspend fun getTeamById(teamId: String): ModelTeam? =
            teamState.value?.takeIf { it.id == teamId }

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
            executions.map { rows -> rows.filter { it.teamId == teamId } }

        override suspend fun getExecutionById(executionId: String): TeamExecution? = null
        override suspend fun logExecution(execution: TeamExecution): Result<Unit> = Result.success(Unit)
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
            usageRecords.map { rows -> rows.filter { it.teamId == teamId } }

        override fun getAllTeamUsageRecords(userId: String): Flow<List<TeamUsageRecord>> =
            usageRecords.map { rows -> rows.filter { it.userId == userId } }

        override suspend fun logTeamUsage(record: TeamUsageRecord): Result<Unit> = Result.success(Unit)
    }
}
