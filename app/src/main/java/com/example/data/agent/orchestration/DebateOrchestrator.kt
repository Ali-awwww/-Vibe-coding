package com.example.data.agent.orchestration

import com.example.domain.agent.orchestration.ModeOrchestrator
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
        require(team.members.isNotEmpty()) { "Debate mode requires at least one team member." }

        val leader = team.members.firstOrNull { it.id == team.leaderMemberId }
            ?: team.members.firstOrNull { it.role == TeamRole.TEAM_LEADER }
            ?: throw IllegalArgumentException("Debate mode requires a configured team leader.")
        val participants = team.members.filter {
            it.id != leader.id && it.role != TeamRole.TEAM_LEADER
        }
        require(participants.isNotEmpty()) {
            "Debate mode requires at least one non-leader participant."
        }

        // Round 1: collect independent proposals without shared mutable state or polling.
        val initialProposals = participants.map { member ->
            async {
                teamRepository.updateMemberStatus(
                    team.id,
                    member.id,
                    MemberStatus.WORKING,
                    "Formulating initial proposal"
                ).getOrThrow()

                val output = aiModelRepository.generateResponse(
                    userId = userId,
                    modelId = member.modelId,
                    prompt = "Goal: " + team.goal + "\nTask: " + initialExecution.prompt +
                        "\nProvide your best solution independently.",
                    taskType = AITaskType.AGENT_TEAM_TASK
                ).getOrThrow()
                check(output.isNotBlank()) { "A debate participant returned an empty proposal." }

                teamRepository.updateMemberStatus(
                    team.id,
                    member.id,
                    MemberStatus.WAITING,
                    "Proposal submitted"
                ).getOrThrow()

                AgentMessage(
                    id = UUID.randomUUID().toString(),
                    fromMemberId = member.id,
                    toMemberId = "TEAM",
                    content = output,
                    type = MessageType.REGULAR,
                    timestamp = System.currentTimeMillis()
                )
            }
        }.awaitAll()

        val round1Summary = initialProposals.joinToString("\n---\n") {
            it.fromMemberId + ": " + it.content
        }
        execution = execution.copy(
            messages = execution.messages + initialProposals + AgentMessage(
                id = UUID.randomUUID().toString(),
                fromMemberId = "SYSTEM",
                toMemberId = "TEAM",
                content = "Round 1 proposals collected from " + initialProposals.size + " participants.",
                type = MessageType.SYSTEM,
                timestamp = System.currentTimeMillis()
            )
        )
        onUpdate(execution)

        // Round 2: retain each participant's actual critique in the execution record.
        val critiques = participants.map { member ->
            async {
                teamRepository.updateMemberStatus(
                    team.id,
                    member.id,
                    MemberStatus.WORKING,
                    "Critiquing other proposals"
                ).getOrThrow()

                val output = aiModelRepository.generateResponse(
                    userId = userId,
                    modelId = member.modelId,
                    prompt = "Here are the proposals from the team:\n" + round1Summary +
                        "\nCritique these and suggest improvements or defend your stance.",
                    taskType = AITaskType.AGENT_TEAM_TASK
                ).getOrThrow()
                check(output.isNotBlank()) { "A debate participant returned an empty critique." }

                AgentMessage(
                    id = UUID.randomUUID().toString(),
                    fromMemberId = member.id,
                    toMemberId = "TEAM",
                    content = output,
                    type = MessageType.CRITIQUE,
                    timestamp = System.currentTimeMillis()
                )
            }
        }.awaitAll()

        execution = execution.copy(
            messages = execution.messages + critiques + AgentMessage(
                id = UUID.randomUUID().toString(),
                fromMemberId = "SYSTEM",
                toMemberId = "TEAM",
                content = "Round 2 critiques collected from " + critiques.size + " participants.",
                type = MessageType.SYSTEM,
                timestamp = System.currentTimeMillis()
            )
        )
        onUpdate(execution)

        teamRepository.updateMemberStatus(
            team.id,
            leader.id,
            MemberStatus.WORKING,
            "Synthesizing debate results"
        ).getOrThrow()

        val finalDebateTranscript = "Proposals:\n" + round1Summary +
            "\n\nCritiques:\n" + critiques.joinToString("\n") {
                it.fromMemberId + ": " + it.content
            }
        val finalResponse = aiModelRepository.generateResponse(
            userId = userId,
            modelId = leader.modelId,
            prompt = "Review the following debate and provide the definitive final answer or solution.\n\n" +
                finalDebateTranscript,
            taskType = AITaskType.AGENT_TEAM_TASK
        ).getOrThrow()
        check(finalResponse.isNotBlank()) { "The debate leader returned an empty consensus." }

        participants.forEach { member ->
            teamRepository.updateMemberStatus(
                team.id,
                member.id,
                MemberStatus.COMPLETED,
                "Debate round completed"
            ).getOrThrow()
        }
        teamRepository.updateMemberStatus(
            team.id,
            leader.id,
            MemberStatus.COMPLETED,
            "Debate consensus completed",
            finalResponse.take(150)
        ).getOrThrow()

        execution = execution.copy(
            messages = execution.messages + AgentMessage(
                id = UUID.randomUUID().toString(),
                fromMemberId = leader.id,
                toMemberId = "USER",
                content = finalResponse,
                type = MessageType.CONSENSUS,
                timestamp = System.currentTimeMillis()
            ),
            status = ExecutionStatus.COMPLETED,
            finalResult = finalResponse,
            completedAt = System.currentTimeMillis()
        )
        onUpdate(execution)
    }
}
