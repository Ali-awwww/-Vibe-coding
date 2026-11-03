package com.example.data.agent.orchestration

import com.example.domain.agent.orchestration.ResponseComposer
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository

class ResponseComposerImpl(
    private val aiModelRepository: AIModelRepository
) : ResponseComposer {

    override suspend fun compose(
        userId: String,
        team: ModelTeam,
        execution: TeamExecution
    ): Result<String> = runCatching {
        val leader = team.members.find { it.id == team.leaderMemberId } ?: team.members.firstOrNull()
            ?: throw Exception("No team members available for composition")

        val summaryOfSteps = execution.steps.joinToString("\n\n") { step ->
            "### Task: ${step.task}\nMember: ${step.memberName} (${step.role})\nResult: ${step.output ?: "FAILED"}"
        }

        val compositionPrompt = """
            Compose a final comprehensive report for the user based on the following AI Model Team execution results.
            
            INITIAL USER REQUEST:
            ${execution.prompt}
            
            TEAM GOAL:
            ${team.goal}
            
            STEP-BY-STEP RESULTS:
            $summaryOfSteps
            
            TECHNICAL CONSTRAINTS:
            ${execution.sharedMemory.technicalConstraints.joinToString("\n")}
            
            APPROVED DECISIONS:
            ${execution.sharedMemory.approvedDecisions.joinToString("\n")}
            
            REPORT REQUIREMENTS:
            1. Summarize what was achieved.
            2. Detail any technical implementation details.
            3. Note any unresolved issues or assumptions made.
            4. Provide instructions on next steps.
            5. Use a professional engineering tone.
        """.trimIndent()

        val response = aiModelRepository.generateResponse(
            userId = userId,
            modelId = leader.modelId,
            prompt = compositionPrompt,
            taskType = AITaskType.GENERAL_CHAT
        )

        response.getOrThrow()
    }
}
