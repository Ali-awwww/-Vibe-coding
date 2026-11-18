package com.example.data.agent.orchestration

import com.example.domain.agent.orchestration.*
import com.example.domain.governance.TeamGovernanceEngine
import com.example.data.governance.TeamGovernanceEngineImpl
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

class DagOrchestrator(
    private val aiModelRepository: AIModelRepository,
    private val teamRepository: AIModelTeamRepository,
    private val taskPlanner: TaskPlanner,
    private val resultEvaluator: ResultEvaluator,
    private val sharedContextStore: SharedContextStore,
    private val responseComposer: ResponseComposer,
    private val governanceEngine: TeamGovernanceEngine? = null
) : ModeOrchestrator {

    override suspend fun execute(
        userId: String,
        team: ModelTeam,
        initialExecution: TeamExecution,
        onUpdate: suspend (TeamExecution) -> Unit
    ): Unit = coroutineScope {
        var execution = initialExecution
        val executionMutex = Mutex()
        val memberStatusMutex = Mutex()
        val gov = governanceEngine ?: TeamGovernanceEngineImpl(teamRepository)
        val maxParallel = if (team.collaborationStyle == CollaborationStyle.SEQUENTIAL) {
            1
        } else {
            team.governance.maxParallelRequests.coerceIn(1, 10)
        }
        val semaphore = Semaphore(maxParallel)

        suspend fun currentSnapshot(): TeamExecution =
            executionMutex.withLock { execution }

        suspend fun updateExecution(
            transform: (TeamExecution) -> TeamExecution
        ): TeamExecution = executionMutex.withLock {
            val updated = transform(execution)
            execution = updated
            onUpdate(updated)
            updated
        }

        suspend fun updateMemberStatus(
            memberId: String,
            status: MemberStatus,
            currentTask: String? = null,
            lastResult: String? = null
        ) {
            memberStatusMutex.withLock {
                teamRepository.updateMemberStatus(
                    team.id,
                    memberId,
                    status,
                    currentTask,
                    lastResult
                ).getOrThrow()
            }
        }

        if (execution.steps.isEmpty()) {
            val plannedSteps = taskPlanner.plan(userId, team, execution.prompt, "").getOrThrow()
            if (plannedSteps.isEmpty()) {
                updateExecution {
                    it.copy(
                        status = ExecutionStatus.FAILED,
                        error = "The task planner returned no executable steps.",
                        completedAt = System.currentTimeMillis()
                    )
                }
                return@coroutineScope
            }
            updateExecution { it.copy(steps = plannedSteps) }
        }

        val interruptedCount = currentSnapshot().steps.count {
            it.status == StepStatus.IN_PROGRESS
        }
        if (interruptedCount > 0) {
            updateExecution { current ->
                var retryBudgetExhausted = false
                val recoveredSteps = current.steps.map { step ->
                    if (step.status != StepStatus.IN_PROGRESS) {
                        step
                    } else if (step.retryCount >= team.governance.maxRetryAttempts) {
                        retryBudgetExhausted = true
                        step.copy(
                            status = StepStatus.FAILED,
                            error = "Interrupted step exceeded the configured retry budget.",
                            completedAt = System.currentTimeMillis()
                        )
                    } else {
                        step.copy(
                            status = StepStatus.PENDING,
                            retryCount = step.retryCount + 1,
                            output = null,
                            error = null,
                            waitReason = null,
                            completedAt = null
                        )
                    }
                }
                current.copy(
                    steps = recoveredSteps,
                    status = if (retryBudgetExhausted) ExecutionStatus.FAILED else current.status,
                    error = if (retryBudgetExhausted) {
                        "One or more interrupted steps exceeded the configured retry budget."
                    } else {
                        current.error
                    },
                    completedAt = if (retryBudgetExhausted) {
                        System.currentTimeMillis()
                    } else {
                        current.completedAt
                    },
                    messages = current.messages + AgentMessage(
                        id = UUID.randomUUID().toString(),
                        fromMemberId = "SYSTEM",
                        toMemberId = "TEAM",
                        content = "Recovery resumed from the last persisted checkpoint. Completed steps are preserved; interrupted provider calls may be retried because their final response may not have been persisted.",
                        type = MessageType.SYSTEM,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        while (true) {
            val snapshot = currentSnapshot()
            if (snapshot.status == ExecutionStatus.FAILED) break

            val completedIds = snapshot.steps
                .filter { it.status == StepStatus.COMPLETED }
                .map { it.id }
                .toSet()
            if (completedIds.size == snapshot.steps.size) break

            val readySteps = snapshot.steps.filter { step ->
                step.status == StepStatus.PENDING &&
                    step.dependsOnIds.all { dependencyId -> dependencyId in completedIds }
            }

            if (readySteps.isEmpty()) {
                val unresolved = snapshot.steps
                    .filter { it.status != StepStatus.COMPLETED }
                    .joinToString("; ") { step ->
                        val missing = step.dependsOnIds.filter { it !in completedIds }
                        step.id + " waits for " + missing.joinToString(", ")
                    }
                updateExecution {
                    it.copy(
                        status = ExecutionStatus.FAILED,
                        error = "No pending step is executable; unresolved dependencies: " + unresolved,
                        completedAt = System.currentTimeMillis()
                    )
                }
                break
            }

            readySteps.map { step ->
                async {
                    semaphore.withPermit {
                        try {
                            val member = team.members.find { it.id == step.memberId }
                                ?: team.members.firstOrNull()
                                ?: throw IllegalStateException("The team has no member assigned to this step.")

                            updateExecution { current ->
                                current.copy(
                                    steps = current.steps.map { currentStep ->
                                        if (currentStep.id == step.id) {
                                            currentStep.copy(
                                                status = StepStatus.IN_PROGRESS,
                                                error = null,
                                                waitReason = null,
                                                startedAt = System.currentTimeMillis()
                                            )
                                        } else {
                                            currentStep
                                        }
                                    }
                                )
                            }
                            updateMemberStatus(member.id, MemberStatus.WORKING, step.task)

                            val contextExecution = currentSnapshot()
                            val rawAgentPrompt = sharedContextStore.prepareAgentContext(
                                member,
                                contextExecution,
                                step
                            )
                            val agentPrompt = gov.maskSecrets(rawAgentPrompt)
                            val targetModel = aiModelRepository.getModelById(userId, member.modelId)
                            val validatedModel = gov.validateMemberCall(
                                team,
                                member,
                                contextExecution,
                                targetModel
                            ).getOrThrow()

                            var activeModel = validatedModel
                            var response = aiModelRepository.generateResponse(
                                userId = userId,
                                modelId = activeModel.id,
                                prompt = agentPrompt,
                                taskType = AITaskType.AGENT_TEAM_TASK
                            )

                            if (response.isFailure && team.governance.autoFallbackToFree) {
                                val quotaError = response.exceptionOrNull() ?: Exception("Quota error")
                                val availableModels = try {
                                    aiModelRepository.getAllAvailableModels(userId, false).first()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    emptyList()
                                }
                                val failoverResult = gov.handleQuotaExhaustion(
                                    userId,
                                    team,
                                    member,
                                    quotaError,
                                    availableModels
                                )
                                if (failoverResult.isSuccess) {
                                    val failover = failoverResult.getOrThrow()
                                    activeModel = failover.replacementModel
                                    updateExecution { current ->
                                        current.copy(
                                            modelFailovers = current.modelFailovers + failover.failoverEvent
                                        )
                                    }
                                    response = aiModelRepository.generateResponse(
                                        userId = userId,
                                        modelId = activeModel.id,
                                        prompt = agentPrompt,
                                        taskType = AITaskType.AGENT_TEAM_TASK
                                    )
                                }
                            }

                            val output = response.getOrThrow()
                            val inputTokens = (agentPrompt.length / 3).coerceAtLeast(15)
                            val outputTokens = (output.length / 3).coerceAtLeast(15)
                            val totalTokens = inputTokens + outputTokens
                            val stepCost = if (team.governance.freeOnly || activeModel.isFree) {
                                0.0
                            } else {
                                (inputTokens / 1_000_000.0 * activeModel.inputPricePer1M) +
                                    (outputTokens / 1_000_000.0 * activeModel.outputPricePer1M)
                            }

                            gov.recordUsage(
                                userId = userId,
                                team = team,
                                member = member,
                                executionId = contextExecution.id,
                                taskDescription = step.task,
                                model = activeModel,
                                inputTokens = inputTokens,
                                outputTokens = outputTokens,
                                cost = stepCost,
                                isSuccess = true,
                                errorMessage = null
                            )

                            val applyUsage: (TeamExecution) -> TeamExecution = { current ->
                                val previous = current.memberUsages.find { it.memberId == member.id }
                                val updatedSummary = if (previous != null) {
                                    previous.copy(
                                        modelId = activeModel.id,
                                        modelName = activeModel.name,
                                        providerId = activeModel.providerId,
                                        providerName = activeModel.providerId.substringBefore(":"),
                                        requestsCount = previous.requestsCount + 1,
                                        inputTokens = previous.inputTokens + inputTokens,
                                        outputTokens = previous.outputTokens + outputTokens,
                                        totalTokens = previous.totalTokens + totalTokens,
                                        costUsd = previous.costUsd + stepCost,
                                        isFreeModel = activeModel.isFree,
                                        isCostConfirmed = false,
                                        lastActiveTime = System.currentTimeMillis()
                                    )
                                } else {
                                    MemberUsageSummary(
                                        memberId = member.id,
                                        memberName = member.name,
                                        role = member.role,
                                        modelId = activeModel.id,
                                        modelName = activeModel.name,
                                        providerId = activeModel.providerId,
                                        providerName = activeModel.providerId.substringBefore(":"),
                                        requestsCount = 1,
                                        inputTokens = inputTokens,
                                        outputTokens = outputTokens,
                                        totalTokens = totalTokens,
                                        costUsd = stepCost,
                                        isFreeModel = activeModel.isFree,
                                        isCostConfirmed = false,
                                        lastActiveTime = System.currentTimeMillis()
                                    )
                                }
                                current.copy(
                                    totalRequests = current.totalRequests + 1,
                                    totalTokens = current.totalTokens + totalTokens,
                                    totalCost = current.totalCost + stepCost,
                                    memberUsages = current.memberUsages
                                        .filterNot { it.memberId == member.id } + updatedSummary
                                )
                            }

                            val evaluation = resultEvaluator.evaluate(
                                userId,
                                member,
                                step.task,
                                output
                            )
                            if (evaluation.isAccepted) {
                                updateExecution { current ->
                                    val withUsage = applyUsage(current)
                                    val newMemory = sharedContextStore.updateMemory(
                                        withUsage,
                                        member,
                                        output
                                    )
                                    withUsage.copy(
                                        steps = withUsage.steps.map { currentStep ->
                                            if (currentStep.id == step.id) {
                                                currentStep.copy(
                                                    status = StepStatus.COMPLETED,
                                                    output = output,
                                                    error = null,
                                                    waitReason = null,
                                                    completedAt = System.currentTimeMillis()
                                                )
                                            } else {
                                                currentStep
                                            }
                                        },
                                        sharedMemory = newMemory
                                    )
                                }
                                updateMemberStatus(
                                    member.id,
                                    MemberStatus.COMPLETED,
                                    lastResult = output
                                )
                            } else if (step.retryCount < team.governance.maxRetryAttempts) {
                                updateExecution { current ->
                                    applyUsage(current).copy(
                                        steps = current.steps.map { currentStep ->
                                            if (currentStep.id == step.id) {
                                                currentStep.copy(
                                                    status = StepStatus.PENDING,
                                                    retryCount = step.retryCount + 1,
                                                    error = evaluation.feedback,
                                                    waitReason = null,
                                                    completedAt = null
                                                )
                                            } else {
                                                currentStep
                                            }
                                        }
                                    )
                                }
                            } else {
                                updateExecution { current ->
                                    applyUsage(current).copy(
                                        steps = current.steps.map { currentStep ->
                                            if (currentStep.id == step.id) {
                                                currentStep.copy(
                                                    status = StepStatus.FAILED,
                                                    error = evaluation.feedback,
                                                    completedAt = System.currentTimeMillis()
                                                )
                                            } else {
                                                currentStep
                                            }
                                        },
                                        status = ExecutionStatus.FAILED,
                                        error = evaluation.feedback,
                                        completedAt = System.currentTimeMillis()
                                    )
                                }
                                updateMemberStatus(
                                    member.id,
                                    MemberStatus.FAILED,
                                    step.task,
                                    evaluation.feedback
                                )
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            val message = error.message?.take(500) ?: "Execution step failed."
                            updateExecution { current ->
                                val completed = current.steps.any {
                                    it.id == step.id && it.status == StepStatus.COMPLETED
                                }
                                current.copy(
                                    steps = if (completed) {
                                        current.steps
                                    } else {
                                        current.steps.map { currentStep ->
                                            if (currentStep.id == step.id) {
                                                currentStep.copy(
                                                    status = StepStatus.FAILED,
                                                    error = message,
                                                    completedAt = System.currentTimeMillis()
                                                )
                                            } else {
                                                currentStep
                                            }
                                        }
                                    },
                                    status = ExecutionStatus.FAILED,
                                    error = message,
                                    completedAt = System.currentTimeMillis()
                                )
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        val finalSnapshot = currentSnapshot()
        if (finalSnapshot.status != ExecutionStatus.FAILED) {
            if (finalSnapshot.steps.any { it.status != StepStatus.COMPLETED }) {
                updateExecution {
                    it.copy(
                        status = ExecutionStatus.FAILED,
                        error = "Execution cannot complete while one or more steps remain unresolved.",
                        completedAt = System.currentTimeMillis()
                    )
                }
            } else {
                val finalResult = responseComposer.compose(userId, team, finalSnapshot).getOrThrow()
                updateExecution {
                    it.copy(
                        status = ExecutionStatus.COMPLETED,
                        finalResult = finalResult,
                        error = null,
                        completedAt = System.currentTimeMillis()
                    )
                }
            }
        }
    }
}
