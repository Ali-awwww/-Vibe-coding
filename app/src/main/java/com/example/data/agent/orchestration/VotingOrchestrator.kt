package com.example.data.agent.orchestration

import com.example.domain.agent.orchestration.ModeOrchestrator

import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import java.util.UUID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class VotingOrchestrator(
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
        val participants = team.members.filter { it.role != TeamRole.TEAM_LEADER }
        val leader = team.members.find { it.id == team.leaderMemberId } ?: team.members.first()

        // 1. Independent Solutions
        val solutions = mutableListOf<String>()
        participants.forEach { member ->
            launch {
                teamRepository.updateMemberStatus(team.id, member.id, MemberStatus.WORKING, "Generating solution variant")
                val response = aiModelRepository.generateResponse(
                    userId = userId,
                    modelId = member.modelId,
                    prompt = "Solve the following task independently: ${execution.prompt}",
                    taskType = AITaskType.AGENT_TEAM_TASK
                )
                solutions.add("Solution from ${member.name}:\n${response.getOrThrow()}")
                teamRepository.updateMemberStatus(team.id, member.id, MemberStatus.COMPLETED)
            }
        }
        
        while (solutions.size < participants.size) { kotlinx.coroutines.delay(500) }

        // 2. Technical Evaluation & Selection
        teamRepository.updateMemberStatus(team.id, leader.id, MemberStatus.WORKING, "Evaluating and voting on best solution")
        val evaluationPrompt = """
            Compare the following solutions for the task: "${execution.prompt}"
            
            ${solutions.joinToString("\n\n---\n\n")}
            
            Select the best solution based on technical correctness, completeness, and engineering standards.
            Explain why it's better than others.
        """.trimIndent()

        val finalResponse = aiModelRepository.generateResponse(
            userId = userId,
            modelId = leader.modelId,
            prompt = evaluationPrompt,
            taskType = AITaskType.VIBE_AGENT_REVIEW
        )

        execution.copy(
            status = ExecutionStatus.COMPLETED,
            finalResult = finalResponse.getOrThrow(),
            completedAt = System.currentTimeMillis()
        ).also { onUpdate(it) }
    }
}
