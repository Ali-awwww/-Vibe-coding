package com.example.data.agent.orchestration

import com.example.domain.agent.orchestration.*
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import kotlinx.coroutines.coroutineScope

class AdaptiveOrchestrator(
    private val aiModelRepository: AIModelRepository,
    private val teamRepository: AIModelTeamRepository,
    private val orchestratorFactory: (CollaborationStyle) -> ModeOrchestrator
) : ModeOrchestrator {

    override suspend fun execute(
        userId: String,
        team: ModelTeam,
        initialExecution: TeamExecution,
        onUpdate: suspend (TeamExecution) -> Unit
    ): Unit = coroutineScope {
        var execution = initialExecution
        
        // 1. Analyze Task to Choose Mode
        teamRepository.logExecution(execution.copy(status = ExecutionStatus.EXECUTING, error = "Analyzing task complexity to select optimal mode..."))
        
        val analysisPrompt = """
            Analyze the following user request and the team composition to select the most effective collaboration style.
            
            REQUEST:
            ${execution.prompt}
            
            TEAM GOAL:
            ${team.goal}
            
            AVAILABLE MODELS:
            ${team.members.joinToString { "${it.name} (${it.role})" }}
            
            CHOICES:
            - SEQUENTIAL: For linear dependencies.
            - PARALLEL: For independent sub-tasks.
            - DEBATE: For complex decisions requiring multiple perspectives.
            - HIERARCHICAL: For large projects needing planning and review.
            - REVIEW_CRITIQUE: For high-quality code implementation and verification.
            - VOTING: For picking the best solution from several options.
            
            RETURN ONLY THE STYLE NAME IN ALL CAPS.
        """.trimIndent()

        val leader = team.members.find { it.id == team.leaderMemberId } ?: team.members.first()
        
        val selectedStyleName = aiModelRepository.generateResponse(
            userId = userId,
            modelId = leader.modelId,
            prompt = analysisPrompt,
            taskType = AITaskType.VIBE_AGENT_PLANNING
        ).getOrNull()?.trim()?.uppercase() ?: "ORCHESTRATED"

        val selectedStyle = try {
            CollaborationStyle.valueOf(selectedStyleName)
        } catch (e: Exception) {
            CollaborationStyle.ORCHESTRATED
        }

        // 2. Delegate to the selected orchestrator
        val delegate = orchestratorFactory(selectedStyle)
        delegate.execute(userId, team, execution, onUpdate)
    }
}
