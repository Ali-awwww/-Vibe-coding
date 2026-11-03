package com.example.data.agent.orchestration

import com.example.domain.agent.orchestration.*
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import java.util.UUID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class HierarchicalOrchestrator(
    private val aiModelRepository: AIModelRepository,
    private val teamRepository: AIModelTeamRepository,
    private val taskPlanner: TaskPlanner,
    private val responseComposer: ResponseComposer,
    private val sharedContextStore: SharedContextStore
) : ModeOrchestrator {

    override suspend fun execute(
        userId: String,
        team: ModelTeam,
        initialExecution: TeamExecution,
        onUpdate: suspend (TeamExecution) -> Unit
    ): Unit = coroutineScope {
        var execution = initialExecution
        val leader = team.members.find { it.id == team.leaderMemberId } ?: team.members.first()
        
        // 1. Leader Plans
        teamRepository.updateMemberStatus(team.id, leader.id, MemberStatus.WORKING, "Planning hierarchical execution")
        val planResult = taskPlanner.plan(userId, team, execution.prompt, "Hierarchical Mode")
        val steps = planResult.getOrThrow()
        
        execution = execution.copy(steps = steps)
        onUpdate(execution)

        // 2. Distributed Execution
        val semaphore = Semaphore(team.settings.maxConcurrentRequests)
        val completedSteps = mutableSetOf<String>()
        val inProgressSteps = mutableSetOf<String>()

        while (completedSteps.size < execution.steps.size && execution.status != ExecutionStatus.FAILED) {
            val readySteps = execution.steps.filter { step ->
                !completedSteps.contains(step.id) && 
                !inProgressSteps.contains(step.id) &&
                step.dependsOnIds.all { completedSteps.contains(it) }
            }

            readySteps.forEach { step ->
                inProgressSteps.add(step.id)
                launch {
                    semaphore.withPermit {
                        try {
                            val member = team.members.find { it.id == step.memberId } ?: team.members.first()
                            
                            val updatedStepsInProgress = execution.steps.map {
                                if (it.id == step.id) it.copy(status = StepStatus.IN_PROGRESS) else it
                            }
                            execution = execution.copy(steps = updatedStepsInProgress)
                            onUpdate(execution)
                            
                            teamRepository.updateMemberStatus(team.id, member.id, MemberStatus.WORKING, step.task)

                            val agentPrompt = sharedContextStore.prepareAgentContext(member, execution, step)
                            val response = aiModelRepository.generateResponse(
                                userId = userId,
                                modelId = member.modelId,
                                prompt = agentPrompt,
                                taskType = AITaskType.AGENT_TEAM_TASK
                            )

                            val output = response.getOrThrow()
                            val newMemory = sharedContextStore.updateMemory(execution, member, output)
                            
                            val updatedStepsCompleted = execution.steps.map {
                                if (it.id == step.id) it.copy(status = StepStatus.COMPLETED, output = output, completedAt = System.currentTimeMillis()) else it
                            }
                            execution = execution.copy(steps = updatedStepsCompleted, sharedMemory = newMemory)
                            onUpdate(execution)
                            
                            teamRepository.updateMemberStatus(team.id, member.id, MemberStatus.COMPLETED, lastResult = output)
                            completedSteps.add(step.id)
                        } catch (e: Exception) {
                            val updatedStepsFailed = execution.steps.map {
                                if (it.id == step.id) it.copy(status = StepStatus.FAILED, error = e.message) else it
                            }
                            execution = execution.copy(steps = updatedStepsFailed, status = ExecutionStatus.FAILED)
                            onUpdate(execution)
                        } finally {
                            inProgressSteps.remove(step.id)
                        }
                    }
                }
            }
            if (readySteps.isEmpty() && inProgressSteps.isEmpty()) break
            kotlinx.coroutines.delay(500)
        }

        // 3. Leader Reviews and Composes Final Output
        if (execution.status != ExecutionStatus.FAILED) {
            teamRepository.updateMemberStatus(team.id, leader.id, MemberStatus.WORKING, "Reviewing all results and composing final report")
            val finalResult = responseComposer.compose(userId, team, execution).getOrThrow()
            
            execution = execution.copy(
                status = ExecutionStatus.COMPLETED,
                finalResult = finalResult,
                completedAt = System.currentTimeMillis()
            )
            onUpdate(execution)
            teamRepository.updateMemberStatus(team.id, leader.id, MemberStatus.COMPLETED, "Report finalized")
        }
    }
}
