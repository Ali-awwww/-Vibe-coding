package com.example.domain.agent.orchestration

import com.example.domain.model.*

interface TaskPlanner {
    /**
     * Breaks down a user request into a set of dependent execution steps.
     */
    suspend fun plan(
        userId: String,
        team: ModelTeam,
        prompt: String,
        projectContext: String
    ): Result<List<ExecutionStep>>
}

interface SharedContextStore {
    /**
     * Prepares the specific context for an agent based on shared memory and previous steps.
     */
    fun prepareAgentContext(
        member: TeamMember,
        execution: TeamExecution,
        currentStep: ExecutionStep
    ): String

    /**
     * Updates shared memory based on an agent's output.
     */
    fun updateMemory(
        execution: TeamExecution,
        member: TeamMember,
        output: String,
        type: MemoryType = MemoryType.FACT
    ): SharedMemory
}

interface ResultEvaluator {
    /**
     * Evaluates an agent's output against the task requirements.
     */
    suspend fun evaluate(
        userId: String,
        member: TeamMember,
        task: String,
        output: String
    ): EvaluationResult
}

data class EvaluationResult(
    val isAccepted: Boolean,
    val feedback: String? = null,
    val detectedConflicts: List<String> = emptyList(),
    val confidenceScore: Float = 1.0f
)

interface ResponseComposer {
    /**
     * Composes the final summary from all execution steps and shared memory.
     */
    suspend fun compose(
        userId: String,
        team: ModelTeam,
        execution: TeamExecution
    ): Result<String>
}
