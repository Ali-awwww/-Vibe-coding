package com.example.data.governance

import com.example.domain.governance.*
import com.example.domain.model.*
import com.example.domain.repository.AIModelTeamRepository
import java.util.UUID

class TeamGovernanceEngineImpl(
    private val teamRepository: AIModelTeamRepository
) : TeamGovernanceEngine {

    private val secretPatterns = listOf(
        Regex("(?i)api[_-]?key[:=]\\s*['\"]?([a-zA-Z0-9_-]{20,})['\"]?"),
        Regex("(?i)secret[:=]\\s*['\"]?([a-zA-Z0-9_-]{20,})['\"]?"),
        Regex("(?i)bearer\\s+([a-zA-Z0-9._-]{25,})"),
        Regex("(?i)token[:=]\\s*['\"]?([a-zA-Z0-9._-]{25,})['\"]?"),
        Regex("AIzaSy[0-9A-Za-z_-]{33}"), // Google API Key pattern
        Regex("sk-or-v1-[0-9a-fA-F]{64}") // OpenRouter API Key pattern
    )

    override fun maskSecrets(text: String): String {
        var sanitized = text
        for (pattern in secretPatterns) {
            sanitized = pattern.replace(sanitized) { matchResult ->
                val fullMatch = matchResult.value
                val secretPart = if (matchResult.groupValues.size > 1) matchResult.groupValues[1] else fullMatch
                val masked = if (secretPart.length > 8) {
                    secretPart.take(4) + "••••••••" + secretPart.takeLast(4)
                } else {
                    "••••••••"
                }
                fullMatch.replace(secretPart, masked)
            }
        }
        return sanitized
    }

    override suspend fun estimateResources(
        team: ModelTeam,
        prompt: String,
        availableModels: List<AIModel>
    ): PreExecutionEstimation {
        val memberCount = team.members.size.coerceAtLeast(1)
        val styleMultiplier = when (team.collaborationStyle) {
            CollaborationStyle.DEBATE -> 3
            CollaborationStyle.REVIEW_CRITIQUE -> 2
            CollaborationStyle.CRASH_INVESTIGATION -> 10
            CollaborationStyle.VIBE_DEVELOPMENT -> 7
            else -> 1
        }
        val estimatedTasksCount = (memberCount * styleMultiplier).coerceAtLeast(1)
        val estimatedCallsCount = estimatedTasksCount

        // Approximate token count: 1 token ~= 4 characters in English/Code or 1 token ~= 1-2 chars in Arabic
        val promptTokens = (prompt.length / 3).coerceAtLeast(50)
        val contextPerCall = promptTokens + 400 // Prompt plus expected shared memory buffer
        val totalContextTokens = contextPerCall * estimatedCallsCount

        val isFreeOnly = team.governance.freeOnly
        var totalMinCost = 0.0
        var totalMaxCost = 0.0
        var hasPaidModel = false

        for (member in team.members) {
            val model = availableModels.find { it.id == member.modelId }
            if (model != null && !model.isFree) {
                hasPaidModel = true
                val inCost = (contextPerCall / 1_000_000.0) * model.inputPricePer1M
                val outCost = (member.maxTokens / 1_000_000.0) * model.outputPricePer1M
                totalMinCost += inCost
                totalMaxCost += inCost + outCost
            }
        }

        val reliability = when {
            isFreeOnly || !hasPaidModel -> "CONFIRMED_FREE"
            totalMaxCost > 0.0 -> "VERIFIED_ESTIMATE"
            else -> "APPROXIMATE"
        }

        val disclaimer = if (isFreeOnly || !hasPaidModel) {
            "النماذج المستخدمة مجانية بالكامل (Free 100%). التكلفة المؤكدة: 0.00$ بدون أي رسوم."
        } else {
            "الاستهلاك الفعلي والتكلفة الحقيقية تعتمد على حجم ردود النماذج وعمليات الأدوات. التكلفة لن تتجاوز سقف الفريق: ${team.governance.maxTeamCostUsd}$."
        }

        return PreExecutionEstimation(
            estimatedTasksCount = estimatedTasksCount,
            estimatedCallsCount = estimatedCallsCount,
            estimatedContextTokens = totalContextTokens,
            estimatedMinCostUsd = if (isFreeOnly) 0.0 else totalMinCost,
            estimatedMaxCostUsd = if (isFreeOnly) 0.0 else totalMaxCost,
            isFreeOnly = isFreeOnly,
            reliabilityStatus = reliability,
            disclaimerText = disclaimer
        )
    }

    override suspend fun validateExecutionStart(
        team: ModelTeam,
        currentExecution: TeamExecution,
        availableModels: List<AIModel>
    ): Result<Unit> = runCatching {
        if (team.governance.freeOnly) {
            for (member in team.members) {
                val model = availableModels.find { it.id == member.modelId }
                if (model != null && (!model.isFree || model.inputPricePer1M > 0.0 || model.outputPricePer1M > 0.0)) {
                    throw TeamGovernanceException.PaidModelBlocked(
                        "تم منع تشغيل الفريق لأن العضو '${member.name}' يستخدم نموذجًا مدفوعًا (${model.name}). وضع الفريق الحالي هو 'مجاني فقط (Free Only)' ويمنع استدعاء النماذج المدفوعة."
                    )
                }
            }
        }

        if (currentExecution.totalCost >= team.governance.maxTeamCostUsd && !team.governance.freeOnly) {
            throw TeamGovernanceException.BudgetExceeded(
                "تم تجاوز ميزانية الفريق المحددة (${team.governance.maxTeamCostUsd}$). توقف التنفيذ التلقائي لحماية الرصيد."
            )
        }
    }

    override suspend fun validateMemberCall(
        team: ModelTeam,
        member: TeamMember,
        currentExecution: TeamExecution,
        targetModel: AIModel?
    ): Result<AIModel> = runCatching {
        val model = targetModel ?: throw TeamGovernanceException.PaidModelBlocked(
            "النموذج المحدد للعضو '${member.name}' غير متوفر أو لم يتم مزامنته من المزود."
        )

        // 1. Enforce Free Only
        if (team.governance.freeOnly) {
            if (!model.isFree || model.inputPricePer1M > 0.0 || model.outputPricePer1M > 0.0) {
                throw TeamGovernanceException.PaidModelBlocked(
                    "تم حظر النموذج '${model.name}' لأنه نموذج مدفوع. سياسة الفريق تفرض 'Free Only' وتمنع أي تحويل تلقائي إلى نماذج مدفوعة."
                )
            }
        }

        // 2. Cumulative Execution Limits
        if (currentExecution.totalRequests >= team.governance.maxApiRequests) {
            throw TeamGovernanceException.RequestLimitExceeded(
                "وصل الفريق إلى الحد الأقصى لعدد طلبات API (${team.governance.maxApiRequests} طلب). تم إيقاف التنفيذ لحماية الحصة."
            )
        }

        if (currentExecution.totalTokens >= team.governance.maxTotalTokens) {
            throw TeamGovernanceException.TokenLimitExceeded(
                "تجاوز استهلاك الفريق الحد الأقصى للرموز (${team.governance.maxTotalTokens} رمز)."
            )
        }

        if (currentExecution.totalCost >= team.governance.maxTeamCostUsd && !team.governance.freeOnly) {
            throw TeamGovernanceException.BudgetExceeded(
                "تم بلوغ الحد الأقصى لتكلفة الفريق (${team.governance.maxTeamCostUsd}$)."
            )
        }

        val elapsedMillis = System.currentTimeMillis() - currentExecution.startedAt
        if (elapsedMillis > team.governance.maxExecutionTimeMinutes * 60 * 1000L) {
            throw TeamGovernanceException.ExecutionTimeout(
                "انتهت المهلة الزمنية القصوى المسموحة لتشغيل الفريق (${team.governance.maxExecutionTimeMinutes} دقائق)."
            )
        }

        model
    }

    override suspend fun handleQuotaExhaustion(
        userId: String,
        team: ModelTeam,
        failedMember: TeamMember,
        error: Throwable,
        availableModels: List<AIModel>
    ): Result<ModelFailoverResult> = runCatching {
        val isRateLimit = error.message?.contains("429") == true ||
                error.message?.contains("rate", ignoreCase = true) == true ||
                error.message?.contains("quota", ignoreCase = true) == true ||
                error.message?.contains("limit", ignoreCase = true) == true

        if (!team.governance.autoFallbackToFree) {
            throw TeamGovernanceException.QuotaExhausted(
                "استنفد العضو '${failedMember.name}' حصة المزود. تم إيقاف المهمة لأن التبديل التلقائي غير مفعّل."
            )
        }

        // Look for compatible FREE model from connected models
        val freeCandidates = availableModels.filter { candidate ->
            candidate.isFree &&
                    candidate.id != failedMember.modelId &&
                    candidate.isAvailable
        }.sortedByDescending { model ->
            var score = 0
            if (model.apiId == "openrouter/free") score += 20
            if (model.apiId.contains("flash", ignoreCase = true)) score += 15
            if (model.capabilities.contains(AICapability.CODING)) score += 10
            if (model.capabilities.contains(AICapability.REASONING)) score += 8
            score
        }

        val replacement = freeCandidates.firstOrNull()
            ?: throw TeamGovernanceException.QuotaExhausted(
                "استنفدت حصة النموذج '${failedMember.name}' ولم يتم العثور على نموذج مجاني بديل متوافق. لن يتم استخدام أي نموذج مدفوع بدون إذنك الصريح."
            )

        val failoverEvent = ModelFailoverEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            memberId = failedMember.id,
            memberName = failedMember.name,
            originalModelId = failedMember.modelId,
            replacementModelId = replacement.id,
            replacementModelName = replacement.name,
            reason = "استنفاد حصة المزود (HTTP 429/Rate Limit). تم التحويل التلقائي لنموذج مجاني بديل للحفاظ على سير العمل.",
            isFree = true
        )

        ModelFailoverResult(replacement, failoverEvent)
    }

    override suspend fun recordUsage(
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
        quotaRemaining: String?
    ): TeamUsageRecord {
        val record = TeamUsageRecord(
            id = UUID.randomUUID().toString(),
            userId = userId,
            teamId = team.id,
            teamName = team.name,
            executionId = executionId,
            memberId = member.id,
            memberName = member.name,
            roleName = member.role.labelAr,
            modelId = model.id,
            modelName = model.name,
            providerId = model.providerId,
            providerName = model.providerId.substringBefore(":"),
            specificApiKeyId = member.specificApiKeyId,
            taskId = executionId,
            taskDescription = taskDescription,
            projectId = null,
            tokensInput = inputTokens,
            tokensOutput = outputTokens,
            totalTokens = inputTokens + outputTokens,
            costUsd = if (model.isFree || team.governance.freeOnly) 0.0 else cost,
            isFree = model.isFree || team.governance.freeOnly,
            isCostConfirmed = false,
            status = if (isSuccess) "SUCCESS" else if (errorMessage?.contains("429") == true) "RATE_LIMITED" else "FAILED",
            errorMessage = errorMessage?.let { maskSecrets(it) },
            quotaRemaining = quotaRemaining,
            timestamp = System.currentTimeMillis()
        )

        teamRepository.logTeamUsage(record).getOrThrow()
        return record
    }
}
