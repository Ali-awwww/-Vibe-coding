package com.example.domain.agent

import com.example.domain.model.*

/**
 * Orchestrates the AI Agent lifecycle for Vibe Coding.
 */
interface VibeAgentOrchestrator {
    /**
     * Executes a user instruction by potentially performing multiple model turns and tool executions.
     */
    suspend fun processInstruction(
        userId: String,
        workspaceId: String,
        instruction: String,
        mode: AgentExecutionMode
    ): Result<AgentExecutionResult>

    /**
     * Summarizes the project state to manage context window limits.
     */
    suspend fun generateContextSummary(workspaceId: String): String
}
