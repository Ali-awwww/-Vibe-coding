package com.example.data.agent.orchestration

import com.example.domain.agent.orchestration.*
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import java.util.UUID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class DebateOrchestrator(
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

        // Round 1: Independent Propositions
        val initialProposals = mutableListOf<String>()
        participants.forEach { member ->
            launch {
                teamRepository.updateMemberStatus(team.id, member.id, MemberStatus.WORKING, "Formulating initial proposal")
                val response = aiModelRepository.generateResponse(
                    userId = userId,
                    modelId = member.modelId,
                    prompt = "Goal: ${team.goal}\nTask: ${execution.prompt}\nProvide your best solution independently.",
                    taskType = AITaskType.AGENT_TEAM_TASK
                )
                val output = response.getOrThrow()
                initialProposals.add("${member.name}: $output")
                teamRepository.updateMemberStatus(team.id, member.id, MemberStatus.WAITING, "Proposal submitted")
            }
        }
        
        // Wait for proposals
        while (initialProposals.size < participants.size) { kotlinx.coroutines.delay(500) }
        
        val round1Summary = initialProposals.joinToString("\n---\n")
        execution = execution.copy(
            messages = execution.messages + AgentMessage(
                id = UUID.randomUUID().toString(),
                fromMemberId = "SYSTEM",
                content = "Round 1 Proposals collected.",
                timestamp = System.currentTimeMillis()
            )
        )
        onUpdate(execution)

        // Round 2: Critique & Rebuttal
        val critiques = mutableListOf<String>()
        participants.forEach { member ->
            launch {
                teamRepository.updateMemberStatus(team.id, member.id, MemberStatus.WORKING, "Critiquing other proposals")
                val response = aiModelRepository.generateResponse(
                    userId = userId,
                    modelId = member.modelId,
                    prompt = "Here are the proposals from the team:\n$round1Summary\nCritique these and suggest improvements or defend your stance.",
                    taskType = AITaskType.AGENT_TEAM_TASK
                )
                critiques.add("${member.name} Critique: ${response.getOrThrow()}")
            }
        }
        
        while (critiques.size < participants.size) { kotlinx.coroutines.delay(500) }
        
        // Round 3: Consensus (Leader Decides)
        teamRepository.updateMemberStatus(team.id, leader.id, MemberStatus.WORKING, "Synthesizing debate results")
        val finalDebateTranscript = "Proposals:\n$round1Summary\n\nCritiques:\n${critiques.joinToString("\n")}"
        
        val finalResponse = aiModelRepository.generateResponse(
            userId = userId,
            modelId = leader.modelId,
            prompt = "Review the following debate and provide the definitive final answer or solution.\n\n$finalDebateTranscript",
            taskType = AITaskType.AGENT_TEAM_TASK
        )
        
        execution.copy(
            status = ExecutionStatus.COMPLETED,
            finalResult = finalResponse.getOrThrow(),
            completedAt = System.currentTimeMillis()
        ).also { onUpdate(it) }
    }
}
