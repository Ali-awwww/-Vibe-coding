package com.example.data.agent

import com.example.data.agent.orchestration.*
import com.example.domain.agent.VibeAgentOrchestrator
import com.example.domain.agent.orchestration.*
import com.example.domain.governance.*
import com.example.domain.model.*
import com.example.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
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
        assertEquals(1, repository.maxConcurrentStatusWrites)
    }

    @Test
    fun pauseAndResumeWaitsAtAPersistedCheckpointWithoutRepeatingCompletedWork() = runTest {
        val proposalCalls = AtomicInteger()
        val proposalsStarted = CompletableDeferred<Unit>()
        val releaseProposals = CompletableDeferred<Unit>()
        val provider = DeterministicAIModelRepository(
            beforeResponse = { call ->
                if (call.prompt.contains("Provide your best solution independently.")) {
                    if (proposalCalls.incrementAndGet() == 2) proposalsStarted.complete(Unit)
                    releaseProposals.await()
                }
            }
        )
        val repository = RecordingTeamRepository()
        val orchestrator = createOrchestrator(provider, repository)
        val team = sampleTeam()
        val updates = Channel<TeamExecution>(Channel.UNLIMITED)
        val executionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            orchestrator.executeTask("owner-1", team, "Design a safe API").collect { updates.send(it) }
        }

        val initial = updates.receive()
        proposalsStarted.await()
        orchestrator.pauseExecution(initial.id)
        releaseProposals.complete(Unit)

        val paused = withTimeout(5_000) {
            var event: TeamExecution
            do {
                event = updates.receive()
            } while (event.status != ExecutionStatus.PAUSED)
            event
        }

        assertEquals(ExecutionStatus.PAUSED, repository.executions[initial.id]?.status)
        assertEquals(2, paused.messages.count { it.type == MessageType.REGULAR })
        assertEquals(2, provider.calls.size)

        val resumed = orchestrator.resumeExecution(initial.id, "owner-1", team).toList().last()
        assertEquals(ExecutionStatus.EXECUTING, resumed.status)

        val completed = withTimeout(5_000) {
            var event: TeamExecution
            do {
                event = updates.receive()
            } while (event.status != ExecutionStatus.COMPLETED)
            event
        }
        executionJob.join()

        assertEquals(5, provider.calls.size)
        assertEquals(2, provider.calls.count { it.prompt.contains("Provide your best solution independently.") })
        assertEquals(2, completed.messages.count { it.type == MessageType.CRITIQUE })
        assertEquals(1, completed.messages.count { it.type == MessageType.CONSENSUS })
        assertTrue(repository.snapshots.any { it.status == ExecutionStatus.PAUSED })
        assertEquals(completed, repository.executions[completed.id])
        updates.close()
    }

    @Test
    fun pausedExecutionCannotBeRestartedWhenItsActiveWorkerHasGoneAway() = runTest {
        val proposalCalls = AtomicInteger()
        val proposalsStarted = CompletableDeferred<Unit>()
        val releaseProposals = CompletableDeferred<Unit>()
        val provider = DeterministicAIModelRepository(
            beforeResponse = { call ->
                if (call.prompt.contains("Provide your best solution independently.")) {
                    if (proposalCalls.incrementAndGet() == 2) proposalsStarted.complete(Unit)
                    releaseProposals.await()
                }
            }
        )
        val repository = RecordingTeamRepository()
        val orchestrator = createOrchestrator(provider, repository)
        val team = sampleTeam()
        val updates = Channel<TeamExecution>(Channel.UNLIMITED)
        val executionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            orchestrator.executeTask("owner-1", team, "Design a safe API").collect { updates.send(it) }
        }

        val initial = updates.receive()
        proposalsStarted.await()
        orchestrator.pauseExecution(initial.id)
        releaseProposals.complete(Unit)
        withTimeout(5_000) {
            var event: TeamExecution
            do {
                event = updates.receive()
            } while (event.status != ExecutionStatus.PAUSED)
        }
        executionJob.cancelAndJoin()

        val foreignFailure = try {
            orchestrator.resumeExecution(initial.id, "another-user", team).toList()
            null
        } catch (error: Throwable) {
            error
        }
        assertTrue(foreignFailure is IllegalStateException)
        assertTrue(foreignFailure?.message?.contains("not owned") == true)

        val failure = try {
            orchestrator.resumeExecution(initial.id, "owner-1", team).toList()
            null
        } catch (error: Throwable) {
            error
        }

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("Process recovery is currently supported only") == true)
        assertEquals(ExecutionStatus.PAUSED, repository.executions[initial.id]?.status)
        assertEquals(2, provider.calls.size)
        updates.close()
    }

    @Test
    fun sequentialExecutionPausesAndResumesFromAChildCoroutineCheckpoint() = runTest {
        val providerStarted = CompletableDeferred<Unit>()
        val releaseProvider = CompletableDeferred<Unit>()
        val provider = DeterministicAIModelRepository(
            beforeResponse = {
                providerStarted.complete(Unit)
                releaseProvider.await()
            }
        )
        val repository = RecordingTeamRepository()
        val planner = object : TaskPlanner {
            override suspend fun plan(
                userId: String,
                team: ModelTeam,
                prompt: String,
                projectContext: String
            ): Result<List<ExecutionStep>> = Result.success(
                listOf(
                    ExecutionStep(
                        id = "step-1",
                        memberId = "member-a",
                        memberName = "Architect",
                        role = TeamRole.SOFTWARE_ARCHITECT.name,
                        task = prompt,
                        input = prompt
                    )
                )
            )
        }
        val contextStore = object : SharedContextStore {
            override fun prepareAgentContext(
                member: TeamMember,
                execution: TeamExecution,
                currentStep: ExecutionStep
            ): String = "fixture context"

            override fun updateMemory(
                execution: TeamExecution,
                member: TeamMember,
                output: String,
                type: MemoryType
            ): SharedMemory = execution.sharedMemory
        }
        val evaluator = object : ResultEvaluator {
            override suspend fun evaluate(
                userId: String,
                member: TeamMember,
                task: String,
                output: String
            ): EvaluationResult = EvaluationResult(isAccepted = true)
        }
        val composer = object : ResponseComposer {
            override suspend fun compose(
                userId: String,
                team: ModelTeam,
                execution: TeamExecution
            ): Result<String> = Result.success("sequential result")
        }
        val governance = TestGovernanceEngine { member ->
            AIModel(
                id = member.modelId,
                providerId = "fixture",
                apiId = member.modelId,
                name = member.name,
                isFree = true
            )
        }
        val orchestrator = createOrchestrator(
            provider = provider,
            repository = repository,
            taskPlanner = planner,
            sharedContextStore = contextStore,
            resultEvaluator = evaluator,
            responseComposer = composer,
            governanceEngine = governance
        )
        val team = sampleTeam().copy(collaborationStyle = CollaborationStyle.SEQUENTIAL)
        val updates = Channel<TeamExecution>(Channel.UNLIMITED)
        val executionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            orchestrator.executeTask("owner-1", team, "Implement a bounded worker").collect { updates.send(it) }
        }

        val initial = updates.receive()
        providerStarted.await()
        orchestrator.pauseExecution(initial.id)
        releaseProvider.complete(Unit)

        val paused = withTimeout(5_000) {
            var event: TeamExecution
            do {
                event = updates.receive()
            } while (event.status != ExecutionStatus.PAUSED)
            event
        }
        assertEquals(ExecutionStatus.PAUSED, repository.executions[initial.id]?.status)
        assertEquals(StepStatus.COMPLETED, paused.steps.single().status)
        assertEquals(1, provider.calls.size)

        val resumed = orchestrator.resumeExecution(initial.id, "owner-1", team).toList().last()
        assertEquals(ExecutionStatus.EXECUTING, resumed.status)

        val completed = withTimeout(5_000) {
            var event: TeamExecution
            do {
                event = updates.receive()
            } while (event.status != ExecutionStatus.COMPLETED)
            event
        }
        executionJob.join()

        assertEquals(1, provider.calls.size)
        assertEquals(StepStatus.COMPLETED, completed.steps.single().status)
        assertEquals("sequential result", completed.finalResult)
        updates.close()
    }

    @Test
    fun sequentialDagRecoveryResumesPersistedStepsAfterOrchestratorRecreation() = runTest {
        val firstStepCalls = AtomicInteger()
        val secondStepCalls = AtomicInteger()
        val secondStepStarted = CompletableDeferred<Unit>()
        val releaseInterruptedAttempt = CompletableDeferred<Unit>()
        val provider = DeterministicAIModelRepository(
            beforeResponse = { call ->
                when (call.prompt) {
                    "step-one" -> firstStepCalls.incrementAndGet()
                    "step-two" -> {
                        if (secondStepCalls.incrementAndGet() == 1) {
                            secondStepStarted.complete(Unit)
                            releaseInterruptedAttempt.await()
                        }
                    }
                }
            }
        )
        val repository = RecordingTeamRepository()
        val planner = object : TaskPlanner {
            override suspend fun plan(
                userId: String,
                team: ModelTeam,
                prompt: String,
                projectContext: String
            ): Result<List<ExecutionStep>> = Result.success(
                listOf(
                    ExecutionStep(
                        id = "step-one",
                        memberId = "member-a",
                        memberName = "Architect",
                        role = TeamRole.SOFTWARE_ARCHITECT.name,
                        task = "step-one",
                        input = "step-one"
                    ),
                    ExecutionStep(
                        id = "step-two",
                        memberId = "member-b",
                        memberName = "Reviewer",
                        role = TeamRole.CODE_REVIEWER.name,
                        task = "step-two",
                        input = "step-two",
                        dependsOnIds = listOf("step-one")
                    )
                )
            )
        }
        val contextStore = object : SharedContextStore {
            override fun prepareAgentContext(
                member: TeamMember,
                execution: TeamExecution,
                currentStep: ExecutionStep
            ): String = currentStep.task

            override fun updateMemory(
                execution: TeamExecution,
                member: TeamMember,
                output: String,
                type: MemoryType
            ): SharedMemory = execution.sharedMemory
        }
        val evaluator = object : ResultEvaluator {
            override suspend fun evaluate(
                userId: String,
                member: TeamMember,
                task: String,
                output: String
            ): EvaluationResult = EvaluationResult(isAccepted = true)
        }
        val composer = object : ResponseComposer {
            override suspend fun compose(
                userId: String,
                team: ModelTeam,
                execution: TeamExecution
            ): Result<String> = Result.success("recovered sequential result")
        }
        val governance = TestGovernanceEngine { member ->
            AIModel(
                id = member.modelId,
                providerId = "fixture",
                apiId = member.modelId,
                name = member.name,
                isFree = true
            )
        }
        val team = sampleTeam().copy(
            collaborationStyle = CollaborationStyle.SEQUENTIAL,
            governance = TeamGovernanceSettings(
                freeOnly = true,
                maxParallelRequests = 3,
                maxRetryAttempts = 2
            )
        )
        val firstOrchestrator = createOrchestrator(
            provider,
            repository,
            taskPlanner = planner,
            sharedContextStore = contextStore,
            resultEvaluator = evaluator,
            responseComposer = composer,
            governanceEngine = governance
        )
        val updates = Channel<TeamExecution>(Channel.UNLIMITED)
        val executionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            firstOrchestrator.executeTask("owner-1", team, "Recover a persisted task").collect {
                updates.send(it)
            }
        }

        val initial = updates.receive()
        withTimeout(5_000) { secondStepStarted.await() }
        val interrupted = checkNotNull(repository.executions[initial.id])
        assertEquals(ExecutionStatus.EXECUTING, interrupted.status)
        assertEquals(StepStatus.COMPLETED, interrupted.steps.single { it.id == "step-one" }.status)
        assertEquals(StepStatus.IN_PROGRESS, interrupted.steps.single { it.id == "step-two" }.status)

        executionJob.cancelAndJoin()
        releaseInterruptedAttempt.complete(Unit)

        val recoveredOrchestrator = createOrchestrator(
            provider,
            repository,
            taskPlanner = planner,
            sharedContextStore = contextStore,
            resultEvaluator = evaluator,
            responseComposer = composer,
            governanceEngine = governance
        )
        val recoveredEvents = withTimeout(10_000) {
            recoveredOrchestrator.resumeExecution(initial.id, "owner-1", team).toList()
        }
        val completed = recoveredEvents.last()

        assertEquals(ExecutionStatus.COMPLETED, completed.status)
        assertEquals(1, firstStepCalls.get())
        assertEquals(2, secondStepCalls.get())
        assertEquals(0, completed.steps.single { it.id == "step-one" }.retryCount)
        assertEquals(1, completed.steps.single { it.id == "step-two" }.retryCount)
        assertTrue(completed.messages.any { it.content.contains("Process recovery restarted") })
        assertEquals(completed, repository.executions[initial.id])
        updates.close()
    }

    @Test
    fun parallelDagWorkersPublishAllStepUpdatesAndSerializeTeamStatusWrites() = runTest {
        val started = AtomicInteger()
        val bothProvidersStarted = CompletableDeferred<Unit>()
        val releaseProviders = CompletableDeferred<Unit>()
        val provider = DeterministicAIModelRepository(
            beforeResponse = {
                if (started.incrementAndGet() == 2) bothProvidersStarted.complete(Unit)
                releaseProviders.await()
            }
        )
        val repository = RecordingTeamRepository()
        val planner = object : TaskPlanner {
            override suspend fun plan(
                userId: String,
                team: ModelTeam,
                prompt: String,
                projectContext: String
            ): Result<List<ExecutionStep>> = Result.success(
                listOf(
                    ExecutionStep(
                        id = "parallel-a",
                        memberId = "member-a",
                        memberName = "Architect",
                        role = TeamRole.SOFTWARE_ARCHITECT.name,
                        task = "parallel-a",
                        input = "parallel-a"
                    ),
                    ExecutionStep(
                        id = "parallel-b",
                        memberId = "member-b",
                        memberName = "Reviewer",
                        role = TeamRole.CODE_REVIEWER.name,
                        task = "parallel-b",
                        input = "parallel-b"
                    )
                )
            )
        }
        val contextStore = object : SharedContextStore {
            override fun prepareAgentContext(
                member: TeamMember,
                execution: TeamExecution,
                currentStep: ExecutionStep
            ): String = currentStep.task

            override fun updateMemory(
                execution: TeamExecution,
                member: TeamMember,
                output: String,
                type: MemoryType
            ): SharedMemory = execution.sharedMemory
        }
        val evaluator = object : ResultEvaluator {
            override suspend fun evaluate(
                userId: String,
                member: TeamMember,
                task: String,
                output: String
            ): EvaluationResult = EvaluationResult(isAccepted = true)
        }
        val composer = object : ResponseComposer {
            override suspend fun compose(
                userId: String,
                team: ModelTeam,
                execution: TeamExecution
            ): Result<String> = Result.success("parallel result")
        }
        val governance = TestGovernanceEngine { member ->
            AIModel(
                id = member.modelId,
                providerId = "fixture",
                apiId = member.modelId,
                name = member.name,
                isFree = true
            )
        }
        val team = sampleTeam().copy(
            collaborationStyle = CollaborationStyle.PARALLEL,
            governance = TeamGovernanceSettings(
                freeOnly = true,
                maxParallelRequests = 2
            )
        )
        val orchestrator = createOrchestrator(
            provider,
            repository,
            taskPlanner = planner,
            sharedContextStore = contextStore,
            resultEvaluator = evaluator,
            responseComposer = composer,
            governanceEngine = governance
        )
        val updates = Channel<TeamExecution>(Channel.UNLIMITED)
        val executionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            orchestrator.executeTask("owner-1", team, "Run independent work").collect {
                updates.send(it)
            }
        }

        updates.receive()
        withTimeout(5_000) { bothProvidersStarted.await() }
        releaseProviders.complete(Unit)
        val completed = withTimeout(10_000) {
            var event: TeamExecution
            do {
                event = updates.receive()
            } while (event.status != ExecutionStatus.COMPLETED)
            event
        }
        executionJob.join()

        assertEquals(2, completed.steps.count { it.status == StepStatus.COMPLETED })
        assertEquals(2, provider.calls.size)
        assertEquals(1, repository.maxConcurrentStatusWrites)
        assertTrue(repository.snapshots.any { snapshot ->
            snapshot.steps.count { it.status == StepStatus.COMPLETED } == 2
        })
        assertEquals(completed, repository.executions[completed.id])
        updates.close()
    }

    @Test
    fun dagWithUnresolvedDependencyCycleFailsInsteadOfReportingCompletion() = runTest {
        val provider = DeterministicAIModelRepository()
        val repository = RecordingTeamRepository()
        val planner = object : TaskPlanner {
            override suspend fun plan(
                userId: String,
                team: ModelTeam,
                prompt: String,
                projectContext: String
            ): Result<List<ExecutionStep>> = Result.success(
                listOf(
                    ExecutionStep(
                        id = "cycle-a",
                        memberId = "member-a",
                        memberName = "Architect",
                        role = TeamRole.SOFTWARE_ARCHITECT.name,
                        task = "cycle-a",
                        dependsOnIds = listOf("cycle-b")
                    ),
                    ExecutionStep(
                        id = "cycle-b",
                        memberId = "member-b",
                        memberName = "Reviewer",
                        role = TeamRole.CODE_REVIEWER.name,
                        task = "cycle-b",
                        dependsOnIds = listOf("cycle-a")
                    )
                )
            )
        }
        val team = sampleTeam().copy(collaborationStyle = CollaborationStyle.SEQUENTIAL)

        val emitted = createOrchestrator(
            provider,
            repository,
            taskPlanner = planner
        ).executeTask("owner-1", team, "Reject a cyclic plan").toList()

        assertEquals(ExecutionStatus.FAILED, emitted.last().status)
        assertTrue(emitted.last().error?.contains("No pending step is executable") == true)
        assertTrue(emitted.none { it.status == ExecutionStatus.COMPLETED })
        assertTrue(provider.calls.isEmpty())
        assertEquals(emitted.last(), repository.executions[emitted.last().id])
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
        repository: RecordingTeamRepository,
        taskPlanner: TaskPlanner? = null,
        sharedContextStore: SharedContextStore? = null,
        resultEvaluator: ResultEvaluator? = null,
        responseComposer: ResponseComposer? = null,
        governanceEngine: TeamGovernanceEngine = TestGovernanceEngine()
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
            taskPlanner = taskPlanner ?: object : TaskPlanner {
                override suspend fun plan(
                    userId: String,
                    team: ModelTeam,
                    prompt: String,
                    projectContext: String
                ): Result<List<ExecutionStep>> =
                    Result.failure(UnsupportedOperationException("Not used by the debate fixture."))
            },
            sharedContextStore = sharedContextStore ?: object : SharedContextStore {
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
            resultEvaluator = resultEvaluator ?: object : ResultEvaluator {
                override suspend fun evaluate(
                    userId: String,
                    member: TeamMember,
                    task: String,
                    output: String
                ): EvaluationResult = EvaluationResult(isAccepted = true)
            },
            responseComposer = responseComposer ?: object : ResponseComposer {
                override suspend fun compose(
                    userId: String,
                    team: ModelTeam,
                    execution: TeamExecution
                ): Result<String> = Result.success("unused")
            },
            governanceEngine = governanceEngine
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

    private class TestGovernanceEngine(
        private val modelForMember: (TeamMember) -> AIModel? = { null }
    ) : TeamGovernanceEngine {
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
        ): Result<AIModel> {
            val model = modelForMember(member)
                ?: return Result.failure(UnsupportedOperationException("Not used by the debate fixture."))
            return Result.success(model)
        }

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
        private val failingModelId: String? = null,
        private val beforeResponse: suspend (ProviderCall) -> Unit = {}
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
            val call = ProviderCall(modelId, prompt, taskType)
            calls += call
            beforeResponse(call)
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
        private val activeStatusWrites = AtomicInteger()
        var maxConcurrentStatusWrites: Int = 0
            private set

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
        ): Result<Unit> {
            val active = activeStatusWrites.incrementAndGet()
            synchronized(this) {
                maxConcurrentStatusWrites = maxOf(maxConcurrentStatusWrites, active)
            }
            return try {
                yield()
                Result.success(Unit)
            } finally {
                activeStatusWrites.decrementAndGet()
            }
        }

        override fun getTeamUsageRecords(teamId: String): Flow<List<TeamUsageRecord>> = flowOf(emptyList())
        override fun getAllTeamUsageRecords(userId: String): Flow<List<TeamUsageRecord>> = flowOf(emptyList())
        override suspend fun logTeamUsage(record: TeamUsageRecord): Result<Unit> = Result.success(Unit)
    }
}
