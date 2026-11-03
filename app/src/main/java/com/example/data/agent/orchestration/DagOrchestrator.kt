package com.example.data.agent.orchestration

import com.example.domain.agent.orchestration.*
import com.example.domain.governance.TeamGovernanceEngine
import com.example.data.governance.TeamGovernanceEngineImpl
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
        val gov = governanceEngine ?: TeamGovernanceEngineImpl(teamRepository)
        val maxParallel = team.governance.maxParallelRequests.coerceIn(1, 10)
        val semaphore = Semaphore(maxParallel)
        
        // 1. Planning (if not already planned)
        if (execution.steps.isEmpty()) {
            val planResult = taskPlanner.plan(userId, team, execution.prompt, "")
            val steps = planResult.getOrThrow()
            execution = execution.copy(steps = steps)
            onUpdate(execution)
        }

        // 2. Execution Loop
        val completedSteps = mutableSetOf<String>()
        val inProgressSteps = mutableSetOf<String>()

        while (completedSteps.size < execution.steps.size && execution.status != ExecutionStatus.FAILED) {
            val readySteps = execution.steps.filter { step ->
                !completedSteps.contains(step.id) && 
                !inProgressSteps.contains(step.id) &&
                step.dependsOnIds.all { completedSteps.contains(it) }
            }

            if (readySteps.isEmpty() && inProgressSteps.isEmpty()) break

            readySteps.forEach { step ->
                inProgressSteps.add(step.id)
                launch {
                    semaphore.withPermit {
                        try {
                            val member = team.members.find { it.id == step.memberId } ?: team.members.first()
                            
                            updateStepStatus(execution, step.id, StepStatus.IN_PROGRESS) {
                                execution = it
                                onUpdate(execution)
                            }
                            teamRepository.updateMemberStatus(team.id, member.id, MemberStatus.WORKING, step.task)

                            // Prepare and sanitize prompt context
                            val rawAgentPrompt = sharedContextStore.prepareAgentContext(member, execution, step)
                            val agentPrompt = gov.maskSecrets(rawAgentPrompt)

                            // Pre-call validation (Free Only, limits)
                            val targetModel = aiModelRepository.getModelById(member.modelId)
                            val validatedModel = gov.validateMemberCall(team, member, execution, targetModel).getOrThrow()

                            var activeModelId = validatedModel.id
                            var response = aiModelRepository.generateResponse(
                                userId = userId,
                                modelId = activeModelId,
                                prompt = agentPrompt,
                                taskType = AITaskType.AGENT_TEAM_TASK
                            )

                            // If rate-limited / quota exhausted, attempt free failover
                            if (response.isFailure && team.governance.autoFallbackToFree) {
                                val err = response.exceptionOrNull() ?: Exception("Quota error")
                                val allModels = try { aiModelRepository.getAllAvailableModels(false).first() } catch (_: Exception) { emptyList() }
                                val failoverResult = gov.handleQuotaExhaustion(userId, team, member, err, allModels)
                                if (failoverResult.isSuccess) {
                                    val failover = failoverResult.getOrThrow()
                                    activeModelId = failover.replacementModel.id
                                    execution = execution.copy(
                                        modelFailovers = execution.modelFailovers + failover.failoverEvent
                                    )
                                    onUpdate(execution)
                                    // Retry with fallback model
                                    response = aiModelRepository.generateResponse(
                                        userId = userId,
                                        modelId = activeModelId,
                                        prompt = agentPrompt,
                                        taskType = AITaskType.AGENT_TEAM_TASK
                                    )
                                }
                            }

                            val output = response.getOrThrow()

                            // Usage tracking and accounting
                            val inTokens = (agentPrompt.length / 3).coerceAtLeast(15)
                            val outTokens = (output.length / 3).coerceAtLeast(15)
                            val totalTokens = inTokens + outTokens
                            val stepCost = if (team.governance.freeOnly || validatedModel.isFree) 0.0 else {
                                ((inTokens / 1_000_000.0) * validatedModel.inputPricePer1M) +
                                ((outTokens / 1_000_000.0) * validatedModel.outputPricePer1M)
                            }

                            gov.recordUsage(
                                userId = userId,
                                team = team,
                                member = member,
                                executionId = execution.id,
                                taskDescription = step.task,
                                model = validatedModel,
                                inputTokens = inTokens,
                                outputTokens = outTokens,
                                cost = stepCost,
                                isSuccess = true,
                                errorMessage = null
                            )

                            val existingSummary = execution.memberUsages.find { it.memberId == member.id }
                            val updatedSummary = if (existingSummary != null) {
                                existingSummary.copy(
                                    requestsCount = existingSummary.requestsCount + 1,
                                    inputTokens = existingSummary.inputTokens + inTokens,
                                    outputTokens = existingSummary.outputTokens + outTokens,
                                    totalTokens = existingSummary.totalTokens + totalTokens,
                                    costUsd = existingSummary.costUsd + stepCost,
                                    lastActiveTime = System.currentTimeMillis()
                                )
                            } else {
                                MemberUsageSummary(
                                    memberId = member.id,
                                    memberName = member.name,
                                    role = member.role,
                                    modelId = activeModelId,
                                    modelName = validatedModel.name,
                                    providerId = member.providerId,
                                    providerName = member.providerId.substringBefore(":"),
                                    requestsCount = 1,
                                    inputTokens = inTokens,
                                    outputTokens = outTokens,
                                    totalTokens = totalTokens,
                                    costUsd = stepCost,
                                    isFreeModel = validatedModel.isFree,
                                    lastActiveTime = System.currentTimeMillis()
                                )
                            }

                            val newMemberUsages = execution.memberUsages.filter { it.memberId != member.id } + updatedSummary
                            execution = execution.copy(
                                totalRequests = execution.totalRequests + 1,
                                totalTokens = execution.totalTokens + totalTokens,
                                totalCost = execution.totalCost + stepCost,
                                memberUsages = newMemberUsages
                            )

                            val evaluation = resultEvaluator.evaluate(userId, member, step.task, output)
                            
                            if (evaluation.isAccepted) {
                                val newMemory = sharedContextStore.updateMemory(execution, member, output)
                                updateStepResult(execution, step.id, StepStatus.COMPLETED, output) {
                                    execution = it.copy(sharedMemory = newMemory)
                                    onUpdate(execution)
                                }
                                teamRepository.updateMemberStatus(team.id, member.id, MemberStatus.COMPLETED, lastResult = output)
                                completedSteps.add(step.id)
                            } else {
                                if (step.retryCount < team.governance.maxRetryAttempts) {
                                    updateStepStatus(execution, step.id, StepStatus.PENDING, retryCount = step.retryCount + 1) {
                                        execution = it
                                        onUpdate(execution)
                                    }
                                } else {
                                    updateStepStatus(execution, step.id, StepStatus.FAILED, error = evaluation.feedback) {
                                        execution = it.copy(status = ExecutionStatus.FAILED)
                                        onUpdate(execution)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            updateStepStatus(execution, step.id, StepStatus.FAILED, error = e.message) {
                                execution = it.copy(status = ExecutionStatus.FAILED)
                                onUpdate(execution)
                            }
                        } finally {
                            inProgressSteps.remove(step.id)
                        }
                    }
                }
            }
            delay(500)
        }

        // 3. Final Composition
        if (execution.status != ExecutionStatus.FAILED) {
            val finalResult = responseComposer.compose(userId, team, execution).getOrThrow()
            execution = execution.copy(
                status = ExecutionStatus.COMPLETED,
                finalResult = finalResult,
                completedAt = System.currentTimeMillis()
            )
            onUpdate(execution)
        }
    }

    private suspend fun updateStepStatus(
        execution: TeamExecution,
        stepId: String,
        status: StepStatus,
        retryCount: Int? = null,
        error: String? = null,
        update: suspend (TeamExecution) -> Unit
    ) {
        val updatedSteps = execution.steps.map {
            if (it.id == stepId) it.copy(
                status = status, 
                retryCount = retryCount ?: it.retryCount,
                error = error ?: it.error
            ) else it
        }
        update(execution.copy(steps = updatedSteps))
    }

    private suspend fun updateStepResult(
        execution: TeamExecution,
        stepId: String,
        status: StepStatus,
        output: String,
        update: suspend (TeamExecution) -> Unit
    ) {
        val updatedSteps = execution.steps.map {
            if (it.id == stepId) it.copy(status = status, output = output, completedAt = System.currentTimeMillis()) else it
        }
        update(execution.copy(steps = updatedSteps))
    }
}
