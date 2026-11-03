package com.example.data.agent.orchestration

import com.example.domain.agent.orchestration.ModeOrchestrator

import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import java.util.UUID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class CritiqueOrchestrator(
    private val aiModelRepository: AIModelRepository,
    private val teamRepository: AIModelTeamRepository
) : ModeOrchestrator {

    override suspend fun execute(
        userId: String,
        team: ModelTeam,
        initialExecution: TeamExecution,
        onUpdate: suspend (TeamExecution) -> Unit
    ): Unit = coroutineScope {
        var execution = initialExecution
        val worker = team.members.find { it.role == TeamRole.SENIOR_DEVELOPER || it.role == TeamRole.BACKEND_DEVELOPER } ?: team.members.first()
        val reviewer = team.members.find { it.role == TeamRole.CODE_REVIEWER || it.role == TeamRole.SECURITY_AUDITOR } ?: team.members.last()

        // 1. Initial Implementation
        teamRepository.updateMemberStatus(team.id, worker.id, MemberStatus.WORKING, "Generating initial implementation")
        val workerResponse = aiModelRepository.generateResponse(
            userId = userId,
            modelId = worker.modelId,
            prompt = "Task: ${execution.prompt}\nImplement the solution.",
            taskType = AITaskType.AGENT_TEAM_TASK
        ).getOrThrow()
        teamRepository.updateMemberStatus(team.id, worker.id, MemberStatus.WAITING, "Implementation ready for review")

        // 2. Independent Review & Critique
        teamRepository.updateMemberStatus(team.id, reviewer.id, MemberStatus.WORKING, "Reviewing and critiquing implementation")
        val critiqueResponse = aiModelRepository.generateResponse(
            userId = userId,
            modelId = reviewer.modelId,
            prompt = "Initial Task: ${execution.prompt}\nImplementation to Review:\n$workerResponse\n\nReview this implementation for errors, vulnerabilities, and quality. Suggest specific improvements.",
            taskType = AITaskType.VIBE_AGENT_REVIEW
        ).getOrThrow()
        teamRepository.updateMemberStatus(team.id, reviewer.id, MemberStatus.COMPLETED, lastResult = critiqueResponse)

        // 3. Final Revision based on Critique
        teamRepository.updateMemberStatus(team.id, worker.id, MemberStatus.WORKING, "Refining implementation based on critique")
        val finalResponse = aiModelRepository.generateResponse(
            userId = userId,
            modelId = worker.modelId,
            prompt = "Here is the critique of your implementation:\n$critiqueResponse\n\nProvide the final refined solution addressing all points.",
            taskType = AITaskType.AGENT_TEAM_TASK
        ).getOrThrow()
        teamRepository.updateMemberStatus(team.id, worker.id, MemberStatus.COMPLETED, lastResult = finalResponse)

        execution.copy(
            status = ExecutionStatus.COMPLETED,
            finalResult = finalResponse,
            completedAt = System.currentTimeMillis()
        ).also { onUpdate(it) }
    }
}
