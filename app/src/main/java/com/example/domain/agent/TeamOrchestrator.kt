package com.example.domain.agent

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

interface TeamOrchestrator {
    /**
     * Executes a task using a specific team.
     * Emits the execution state as it progresses.
     */
    fun executeTask(
        userId: String,
        team: ModelTeam,
        prompt: String
    ): Flow<TeamExecution>

    /**
     * Executes a task with attached files and project context.
     */
    fun executeTaskWithContext(
        userId: String,
        team: ModelTeam,
        prompt: String,
        attachedFiles: List<AttachedFileContext> = emptyList(),
        projectId: String? = null
    ): Flow<TeamExecution>

    suspend fun pauseExecution(executionId: String)
    suspend fun resumeExecution(executionId: String, userId: String, team: ModelTeam): Flow<TeamExecution>
    suspend fun stopExecution(executionId: String)
    suspend fun retryFailedSteps(executionId: String, userId: String, team: ModelTeam): Flow<TeamExecution>
    suspend fun assignDirectTask(
        executionId: String,
        userId: String,
        team: ModelTeam,
        memberId: String,
        taskPrompt: String
    ): Flow<TeamExecution>
    suspend fun sendInstruction(
        executionId: String,
        userId: String,
        team: ModelTeam,
        instruction: TeamInstruction
    ): Flow<TeamExecution>
    suspend fun changeMemberAgent(teamId: String, memberId: String, newModelId: String): Result<Unit>

    /**
     * Analyzes a prompt and distributes sub-tasks to team members.
     */
    suspend fun planAndDistribute(
        team: ModelTeam,
        prompt: String
    ): Result<List<ExecutionStep>>

    /**
     * Estimates required tasks, calls, tokens, and cost prior to execution.
     */
    suspend fun estimateTaskResources(
        team: ModelTeam,
        prompt: String
    ): PreExecutionEstimation
}
