package com.example.data.agent

import com.example.data.agent.orchestration.*
import com.example.domain.agent.VibeAgentOrchestrator
import com.example.domain.agent.orchestration.*
import com.example.domain.governance.*
import com.example.domain.model.*
import com.example.domain.repository.*
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
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

class TeamOrchestratorExecutionFixtureTest {

    @Test
    fun debateExecutionPersistsProposalsCritiquesAndConsensus() = runTest {
        val provider = DeterministicAIModelRepository()
        val repository = RecordingTeamRepository()
        val orchestrator = createOrchestrator(provider, repository)

        val emitted = orchestrator.executeTask("owner-1", sampleTeam(), "Design a safe API").toList()
        val completed = emitted.last()
        val persisted = repository.executions[completed.id]

        assertEquals(ExecutionStatus.COMPLETED, completed.status)
        assertNotNull(persisted)
        assertEquals(completed, persisted)
        assertEquals("decision:leader-model", persisted?.finalResult)

        val proposals = persisted?.messages.orEmpty().filter { it.type == MessageType.REGULAR }
        val critiques = persisted?.messages.orEmpty().filter { it.type == MessageType.CRITIQUE }
        val consensus = persisted?.messages.orEmpty().filter { it.type == MessageType.CONSENSUS }
        assertEquals(2, proposals.size)
        assertEquals(2, critiques.size)
        assertEquals(1, consensus.size)
        assertTrue(proposals.any { it.fromMemberId == "member-a" && it.content == "proposal:model-a" })
        assertTrue(proposals.any { it.fromMemberId == "member-b" && it.content == "proposal:model-b" })
        assertTrue(critiques.any { it.fromMemberId == "member-a" && it.content == "critique:model-a" })
        assertTrue(critiques.any { it.fromMemberId == "member-b" && it.content == "critique:model-b" })
        assertTrue(repository.snapshots.any { snapshot ->
            snapshot.messages.any { it.content == "proposal:model-a" } &&
                snapshot.messages.any { it.content == "proposal:model-b" }
        })
        assertEquals(5, provider.calls.size)
    }

    @Test
    fun providerFailureIsPersistedAsFailedWithoutConsensus() = runTest {
        val provider = DeterministicAIModelRepository(failingModelId = "model-b")
        val repository = RecordingTeamRepository()
        val emitted = createOrchestrator(provider, repository)
            .executeTask("owner-1", sampleTeam(), "Design a safe API")
            .toList()

        val finalState = emitted.last()
        assertEquals(ExecutionStatus.FAILED, finalState.status)
        assertTrue(finalState.error?.contains("deterministic provider failure") == true)
        assertFalse(finalState.messages.any { it.type == MessageType.CONSENSUS })
        assertEquals(finalState, repository.executions[finalState.id])
    }

    @Test
    fun debateWithoutParticipantsFailsInsteadOfCompletingAnEmptyConsensus() = runTest {
        val provider = DeterministicAIModelRepository()
        val repository = RecordingTeamRepository()
        val leaderOnlyTeam = sampleTeam().let { it.copy(members = listOf(it.members.first())) }

        val emitted = createOrchestrator(provider, repository)
            .executeTask("owner-1", leaderOnlyTeam, "Design a safe API")
            .toList()

        assertEquals(ExecutionStatus.FAILED, emitted.last().status)
        assertTrue(emitted.last().error?.contains("at least one non-leader participant") == true)
        assertTrue(provider.calls.isEmpty())
    }

    @Test
    fun initialExecutionMustBePersistedBeforeAnyProviderCall() = runTest {
        val provider = DeterministicAIModelRepository()
        val repository = RecordingTeamRepository(failOnLogCall = 1)

        val failure = try {
            createOrchestrator(provider, repository)
                .executeTask("owner-1", sampleTeam(), "Design a safe API")
                .toList()
            null
        } catch (error: Throwable) {
            error
        }

        assertNotNull(failure)
        assertTrue(provider.calls.isEmpty())
        assertTrue(repository.snapshots.isEmpty())
    }

    @Test
    fun failedFinalPersistenceNeverEmitsACompletedExecution() = runTest {
        val provider = DeterministicAIModelRepository()
        val repository = RecordingTeamRepository(failOnLogCall = 4)
        val emitted = createOrchestrator(provider, repository)
            .executeTask("owner-1", sampleTeam(), "Design a safe API")
            .toList()

        assertFalse(emitted.any { it.status == ExecutionStatus.COMPLETED })
        assertEquals(ExecutionStatus.FAILED, emitted.last().status)
        assertTrue(emitted.last().error?.contains("execution persistence failed") == true)
        assertNull(emitted.last().finalResult)
        assertEquals(emitted.last(), repository.executions[emitted.last().id])
    }

    private fun createOrchestrator(
        provider: DeterministicAIModelRepository,
        repository: RecordingTeamRepository
    ): TeamOrchestratorImpl {
        val unusedVibeAgent = object : VibeAgentOrchestrator {
            override suspend fun processInstruction(
                userId: String,
                workspaceId: String,
                instruction: String,
                mode: AgentExecutionMode
            ): Result<AgentExecutionResult> =
                Result.failure(UnsupportedOperationException("Not used by the debate fixture."))

            override suspend fun generateContextSummary(workspaceId: String): String = "unused"
        }

        return TeamOrchestratorImpl(
            aiModelRepository = provider,
            teamRepository = repository,
            vibeRepository = unusedVibeRepository(),
            orchestratorProvider = { unusedVibeAgent },
            taskPlanner = object : TaskPlanner {
                override suspend fun plan(
                    userId: String,
                    team: ModelTeam,
                    prompt: String,
                    projectContext: String
                ): Result<List<ExecutionStep>> =
                    Result.failure(UnsupportedOperationException("Not used by the debate fixture."))
            },
            sharedContextStore = object : SharedContextStore {
                override fun prepareAgentContext(
                    member: TeamMember,
                    execution: TeamExecution,
                    currentStep: ExecutionStep
                ): String = "unused"

                override fun updateMemory(
                    execution: TeamExecution,
                    member: TeamMember,
                    output: String,
                    type: MemoryType
                ): SharedMemory = execution.sharedMemory
            },
            resultEvaluator = object : ResultEvaluator {
                override suspend fun evaluate(
                    userId: String,
                    member: TeamMember,
                    task: String,
                    output: String
                ): EvaluationResult = EvaluationResult(isAccepted = true)
            },
            responseComposer = object : ResponseComposer {
                override suspend fun compose(
                    userId: String,
                    team: ModelTeam,
                    execution: TeamExecution
                ): Result<String> = Result.success("unused")
            },
            governanceEngine = TestGovernanceEngine()
        )
    }

    private fun unusedVibeRepository(): VibeStudioRepository =
        Proxy.newProxyInstance(
            VibeStudioRepository::class.java.classLoader,
            arrayOf(VibeStudioRepository::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "toString" -> "unused VibeStudioRepository"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        } as VibeStudioRepository

    private fun sampleTeam(): ModelTeam {
        val leader = TeamMember(
            id = "leader",
            name = "Leader",
            modelId = "leader-model",
            role = TeamRole.TEAM_LEADER
        )
        val first = TeamMember(
            id = "member-a",
            name = "Architect",
            modelId = "model-a",
            role = TeamRole.SOFTWARE_ARCHITECT
        )
        val second = TeamMember(
            id = "member-b",
            name = "Reviewer",
            modelId = "model-b",
            role = TeamRole.CODE_REVIEWER
        )
        return ModelTeam(
            id = "team-1",
            name = "Debate fixture",
            goal = "Produce a safe design",
            ownerId = "owner-1",
            leaderMemberId = leader.id,
            members = listOf(leader, first, second),
            collaborationStyle = CollaborationStyle.DEBATE,
            governance = TeamGovernanceSettings(freeOnly = true)
        )
    }

    private class TestGovernanceEngine : TeamGovernanceEngine {
        override fun maskSecrets(text: String): String = text

        override suspend fun estimateResources(
            team: ModelTeam,
            prompt: String,
            availableModels: List<AIModel>
        ): PreExecutionEstimation = PreExecutionEstimation(isFreeOnly = team.governance.freeOnly)

        override suspend fun validateExecutionStart(
            team: ModelTeam,
            currentExecution: TeamExecution,
            availableModels: List<AIModel>
        ): Result<Unit> = Result.success(Unit)

        override suspend fun validateMemberCall(
            team: ModelTeam,
            member: TeamMember,
            currentExecution: TeamExecution,
            targetModel: AIModel?
        ): Result<AIModel> = Result.failure(UnsupportedOperationException("Not used by the debate fixture."))

        override suspend fun handleQuotaExhaustion(
            userId: String,
            team: ModelTeam,
            failedMember: TeamMember,
            error: Throwable,
            availableModels: List<AIModel>
        ): Result<ModelFailoverResult> =
            Result.failure(UnsupportedOperationException("Not used by the debate fixture."))

        override suspend fun recordUsage(
            userId: String,
            team: ModelTeam,
            member: TeamMember,
            executionId: String,
            taskDescription: String,
            model: AIModel,
            inputTokens: Int,
            outputTokens: Int,
            cost: Double,
            isSuccess: Boolean,
            errorMessage: String?,
            quotaRemaining: String?
        ): TeamUsageRecord = TeamUsageRecord()
    }

    private data class ProviderCall(
        val modelId: String,
        val prompt: String,
        val taskType: AITaskType
    )

    private class DeterministicAIModelRepository(
        private val failingModelId: String? = null
    ) : AIModelRepository {
        val calls = CopyOnWriteArrayList<ProviderCall>()

        override fun getProviders(userId: String): Flow<List<AIProvider>> = flowOf(emptyList())
        override suspend fun getProviderById(userId: String, id: String): AIProvider? = null
        override suspend fun addProvider(provider: AIProvider) = Unit
        override suspend fun deleteProvider(userId: String, id: String) = Unit
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
        override suspend fun syncModels(userId: String, providerId: String): Result<Unit> = Result.success(Unit)
        override suspend fun testConnection(userId: String, providerId: String): Result<Unit> = Result.success(Unit)
        override fun getRecentUsageLogs(userId: String): Flow<List<AIUsageLog>> = flowOf(emptyList())
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
        ): Result<String> {
            calls += ProviderCall(modelId, prompt, taskType)
            if (modelId == failingModelId) {
                return Result.failure(IllegalStateException("deterministic provider failure"))
            }
            val output = when {
                prompt.contains("Provide your best solution independently.") -> "proposal:" + modelId
                prompt.contains("Critique these and suggest improvements") -> "critique:" + modelId
                prompt.contains("definitive final answer or solution.") -> "decision:" + modelId
                else -> "unexpected:" + modelId
            }
            return Result.success(output)
        }
    }

    private class RecordingTeamRepository(
        private val failOnLogCall: Int? = null
    ) : AIModelTeamRepository {
        val executions = linkedMapOf<String, TeamExecution>()
        val snapshots = mutableListOf<TeamExecution>()
        private var logCallCount = 0

        override fun getTeams(userId: String): Flow<List<ModelTeam>> = flowOf(emptyList())
        override suspend fun getTeamById(teamId: String): ModelTeam? = null
        override suspend fun createTeam(team: ModelTeam): Result<String> = Result.success(team.id)
        override suspend fun updateTeam(team: ModelTeam): Result<Unit> = Result.success(Unit)
        override suspend fun deleteTeam(teamId: String): Result<Unit> = Result.success(Unit)

        override fun getTeamExecutions(teamId: String): Flow<List<TeamExecution>> =
            flowOf(snapshots.filter { it.teamId == teamId })

        override suspend fun getExecutionById(executionId: String): TeamExecution? = executions[executionId]

        override suspend fun logExecution(execution: TeamExecution): Result<Unit> {
            logCallCount += 1
            if (logCallCount == failOnLogCall) {
                return Result.failure(IllegalStateException("execution persistence failed"))
            }
            snapshots += execution
            executions[execution.id] = execution
            return Result.success(Unit)
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

        override fun getTeamUsageRecords(teamId: String): Flow<List<TeamUsageRecord>> = flowOf(emptyList())
        override fun getAllTeamUsageRecords(userId: String): Flow<List<TeamUsageRecord>> = flowOf(emptyList())
        override suspend fun logTeamUsage(record: TeamUsageRecord): Result<Unit> = Result.success(Unit)
    }
}
