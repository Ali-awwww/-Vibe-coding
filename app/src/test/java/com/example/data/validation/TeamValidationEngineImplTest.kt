package com.example.data.validation

import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.validation.ValidationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamValidationEngineImplTest {

    @Test
    fun fullSuiteOnlyPassesExecutedChecksAndCleansTemporaryRecords() = runTest {
        val providers = FakeAIModelRepository()
        val teams = FakeAIModelTeamRepository()
        val engine = engine(providers, teams)

        val finalProgress = engine.runFullValidationSuite("owner-1").toList().last()

        assertFalse(finalProgress.isRunning)
        assertTrue(teams.records.isEmpty())
        assertTrue(providers.records.isEmpty())
        assertEquals(6, finalProgress.summaryReport?.passedCount)
        assertEquals(14, finalProgress.summaryReport?.skippedCount)
        assertFalse(finalProgress.summaryReport?.isFreeOnlyProtected ?: true)
        assertTrue(finalProgress.summaryReport?.userKeyIsolationVerified == true)
        assertFalse(finalProgress.summaryReport?.backendUserIsolationVerified ?: true)
        assertFalse(finalProgress.summaryReport?.actualCostMeasured ?: true)
        assertFalse(finalProgress.summaryReport?.isolatedSandboxVerified ?: true)
        assertTrue(finalProgress.summaryReport?.modelsContacted.isNullOrEmpty())

        val results = finalProgress.items.associateBy { it.id }
        listOf(1, 2, 3, 5, 6, 20).forEach { id ->
            assertEquals("test $id", ValidationStatus.PASSED, results[id]?.status)
        }
        (4..19).filter { it !in setOf(5, 6) }.forEach { id ->
            assertEquals("test $id", ValidationStatus.SKIPPED, results[id]?.status)
            assertNotNull("test $id must explain why it was skipped", results[id]?.errorMessage)
        }
    }

    @Test
    fun temporaryTeamIsDeletedAfterSuccessfulRead() = runTest {
        val teams = FakeAIModelTeamRepository()
        val result = engine(FakeAIModelRepository(), teams)
            .runSingleTest("owner-1", 1)
            .toList()
            .last()

        assertEquals(ValidationStatus.PASSED, result.status)
        assertTrue(teams.records.isEmpty())
    }

    @Test
    fun temporaryProviderRecordsAreDeletedAfterPersistenceCheck() = runTest {
        val providers = FakeAIModelRepository()
        val result = engine(providers, FakeAIModelTeamRepository())
            .runSingleTest("owner-1", 2)
            .toList()
            .last()

        assertEquals(ValidationStatus.PASSED, result.status)
        assertTrue(providers.records.isEmpty())
    }

    @Test
    fun connectivityCheckSkipsWhenNoProviderIsConfigured() = runTest {
        val result = engine(FakeAIModelRepository(), FakeAIModelTeamRepository())
            .runSingleTest("owner-1", 4)
            .toList()
            .last()

        assertEquals(ValidationStatus.SKIPPED, result.status)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun orchestrationCheckDoesNotPassFromInMemoryFixtures() = runTest {
        val result = engine(FakeAIModelRepository(), FakeAIModelTeamRepository())
            .runSingleTest("owner-1", 9)
            .toList()
            .last()

        assertEquals(ValidationStatus.SKIPPED, result.status)
        assertNull(result.evidence)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun sandboxFlowsReportUnavailableInsteadOfSyntheticSuccess() = runTest {
        val engine = engine(FakeAIModelRepository(), FakeAIModelTeamRepository())

        val vibe = engine.runVibeCodingSandbox("owner-1").toList().last()
        val crash = engine.runCrashInvestigationSandbox("owner-1").toList().last()

        assertFalse(vibe.isAvailable)
        assertFalse(vibe.isComplete)
        assertTrue(vibe.steps.isEmpty())
        assertNotNull(vibe.blockedReason)
        assertFalse(crash.isAvailable)
        assertFalse(crash.isComplete)
        assertTrue(crash.steps.isEmpty())
        assertTrue(crash.crashSummary.isBlank())
        assertTrue(crash.patchDiff.isBlank())
        assertNotNull(crash.blockedReason)
    }

    @Test
    fun reportDefaultsNeverClaimUnverifiedSecurityOrCost() = runTest {
        val report = engine(FakeAIModelRepository(), FakeAIModelTeamRepository())
            .generateValidationReport(emptyList(), null, null)

        assertFalse(report.actualCostMeasured)
        assertFalse(report.isFreeOnlyProtected)
        assertFalse(report.userKeyIsolationVerified)
        assertFalse(report.backendUserIsolationVerified)
        assertFalse(report.isolatedSandboxVerified)
        assertTrue(report.modelsContacted.isEmpty())
        assertTrue(report.conflictsDetected.isEmpty())
        assertTrue(report.rateLimitsObserved.isEmpty())
        assertTrue(report.markdownReport.contains("NOT VERIFIED"))
        assertFalse(report.markdownReport.contains("ALL CHECKS PASSED"))
    }

    private fun engine(
        providers: FakeAIModelRepository,
        teams: FakeAIModelTeamRepository
    ) = TeamValidationEngineImpl(
        aiModelRepository = providers,
        teamRepository = teams,
        encryptSecret = { "encrypted:$it" },
        decryptSecret = { it.removePrefix("encrypted:") }
    )

    private class FakeAIModelRepository : AIModelRepository {
        val records = linkedMapOf<String, AIProvider>()

        override fun getProviders(userId: String): Flow<List<AIProvider>> =
            flowOf(records.values.filter { it.userId == userId })

        override suspend fun getProviderById(userId: String, id: String): AIProvider? =
            records[id]?.takeIf { it.userId == userId }

        override suspend fun addProvider(provider: AIProvider) {
            records[provider.id] = provider
        }

        override suspend fun deleteProvider(userId: String, id: String) {
            if (records[id]?.userId == userId) records.remove(id)
        }

        override suspend fun updateProviderStatus(
            userId: String,
            id: String,
            status: AIProviderStatus,
            syncTime: Long
        ) = Unit

        override fun getModelsByProvider(userId: String, providerId: String): Flow<List<AIModel>> =
            flowOf(emptyList())

        override fun getAllAvailableModels(userId: String, freeOnly: Boolean): Flow<List<AIModel>> =
            flowOf(emptyList())

        override suspend fun getModelById(userId: String, id: String): AIModel? = null

        override suspend fun syncModels(userId: String, providerId: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun testConnection(userId: String, providerId: String): Result<Unit> =
            if (getProviderById(userId, providerId) != null) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Provider not found"))
            }

        override fun getRecentUsageLogs(userId: String): Flow<List<AIUsageLog>> =
            flowOf(emptyList())

        override fun getTotalCost(userId: String, since: Long): Flow<Double?> = flowOf(null)

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
        ): Result<String> = Result.success("test response")
    }

    private class FakeAIModelTeamRepository : AIModelTeamRepository {
        val records = linkedMapOf<String, ModelTeam>()

        override fun getTeams(userId: String): Flow<List<ModelTeam>> =
            flowOf(records.values.filter { it.ownerId == userId })

        override suspend fun getTeamById(teamId: String): ModelTeam? = records[teamId]

        override suspend fun createTeam(team: ModelTeam): Result<String> {
            records[team.id] = team
            return Result.success(team.id)
        }

        override suspend fun updateTeam(team: ModelTeam): Result<Unit> {
            records[team.id] = team
            return Result.success(Unit)
        }

        override suspend fun deleteTeam(teamId: String): Result<Unit> {
            records.remove(teamId)
            return Result.success(Unit)
        }

        override fun getTeamExecutions(teamId: String): Flow<List<TeamExecution>> =
            flowOf(emptyList())

        override suspend fun getExecutionById(executionId: String): TeamExecution? = null

        override suspend fun logExecution(execution: TeamExecution): Result<Unit> =
            Result.success(Unit)

        override suspend fun addTeamMember(teamId: String, member: TeamMember): Result<Unit> =
            Result.success(Unit)

        override suspend fun removeTeamMember(teamId: String, memberId: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun updateMemberStatus(
            teamId: String,
            memberId: String,
            status: MemberStatus,
            currentTask: String?,
            lastResult: String?
        ): Result<Unit> = Result.success(Unit)

        override fun getTeamUsageRecords(teamId: String): Flow<List<TeamUsageRecord>> =
            flowOf(emptyList())

        override fun getAllTeamUsageRecords(userId: String): Flow<List<TeamUsageRecord>> =
            flowOf(emptyList())

        override suspend fun logTeamUsage(record: TeamUsageRecord): Result<Unit> =
            Result.success(Unit)
    }
}
