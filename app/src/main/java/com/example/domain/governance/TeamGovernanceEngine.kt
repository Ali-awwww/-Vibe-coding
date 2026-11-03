package com.example.domain.governance

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

data class ModelFailoverResult(
    val replacementModel: AIModel,
    val failoverEvent: ModelFailoverEvent
)

sealed class TeamGovernanceException(message: String) : Exception(message) {
    class PaidModelBlocked(message: String) : TeamGovernanceException(message)
    class BudgetExceeded(message: String) : TeamGovernanceException(message)
    class TokenLimitExceeded(message: String) : TeamGovernanceException(message)
    class RequestLimitExceeded(message: String) : TeamGovernanceException(message)
    class ExecutionTimeout(message: String) : TeamGovernanceException(message)
    class QuotaExhausted(message: String) : TeamGovernanceException(message)
    class UnauthorizedAccess(message: String) : TeamGovernanceException(message)
}

interface TeamGovernanceEngine {
    fun maskSecrets(text: String): String

    suspend fun estimateResources(
        team: ModelTeam,
        prompt: String,
        availableModels: List<AIModel>
    ): PreExecutionEstimation

    suspend fun validateExecutionStart(
        team: ModelTeam,
        currentExecution: TeamExecution,
        availableModels: List<AIModel>
    ): Result<Unit>

    suspend fun validateMemberCall(
        team: ModelTeam,
        member: TeamMember,
        currentExecution: TeamExecution,
        targetModel: AIModel?
    ): Result<AIModel>

    suspend fun handleQuotaExhaustion(
        userId: String,
        team: ModelTeam,
        failedMember: TeamMember,
        error: Throwable,
        availableModels: List<AIModel>
    ): Result<ModelFailoverResult>

    suspend fun recordUsage(
        userId: String,
        team: ModelTeam,
        member: TeamMember,
        executionId: String,
        taskDescription: String,
        model: AIModel,
        inputTokens: Int,
        outputTokens: Int,
        cost: Double,
        isSuccess: Boolean,
        errorMessage: String?,
        quotaRemaining: String? = null
    ): TeamUsageRecord
}
