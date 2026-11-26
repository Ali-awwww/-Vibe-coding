package com.example.domain.repository

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GeminiDailyQuotaExceededException(
    message: String = "Gemini daily quota exhausted",
    cause: Throwable? = null
) : IllegalStateException(message, cause)

interface AIModelRepository {
    fun getProviders(userId: String): Flow<List<AIProvider>>
    suspend fun getProviderById(userId: String, id: String): AIProvider?
    suspend fun addProvider(provider: AIProvider)
    suspend fun deleteProvider(userId: String, id: String)
    suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long)

    fun getModelsByProvider(userId: String, providerId: String): Flow<List<AIModel>>
    fun getAllAvailableModels(userId: String, freeOnly: Boolean = false): Flow<List<AIModel>>
    suspend fun getModelById(userId: String, id: String): AIModel?
    suspend fun syncModels(userId: String, providerId: String): Result<Unit>
    
    suspend fun testConnection(userId: String, providerId: String): Result<Unit>
    
    fun getRecentUsageLogs(userId: String): Flow<List<AIUsageLog>>
    fun getTotalCost(userId: String, since: Long): Flow<Double?>
    fun hasUnpricedUsageSince(userId: String, since: Long): Flow<Boolean> =
        getRecentUsageLogs(userId).map { logs ->
            logs.any {
                it.timestamp > since && (
                    it.estimatedCost < 0.0 ||
                        (it.isSuccess && it.estimatedCost == 0.0 && it.modelId.startsWith("firebase_ai_platform:"))
                )
            }
        }
    suspend fun logUsage(log: AIUsageLog)

    // Selection & User Settings management
    fun getUserSettings(userId: String): Flow<AIUserSettings?>
    suspend fun updateUserSettings(settings: AIUserSettings)
    suspend fun setDefaultModel(userId: String, modelId: String)
    suspend fun getDefaultModel(userId: String): AIModel?
    
    // Inference
    suspend fun generateResponse(
        userId: String,
        modelId: String,
        prompt: String,
        systemInstruction: String? = null,
        taskId: String? = null,
        taskType: AITaskType = AITaskType.GENERAL_CHAT
    ): Result<String>

    suspend fun generateWithFirebaseAiChain(
        userId: String,
        prompt: String,
        systemInstruction: String? = null,
        taskId: String? = null,
        taskType: AITaskType = AITaskType.CODE_FIX_SUGGESTION
    ): Result<Pair<String, String>> {
        val defaultModel = getDefaultModel(userId)
            ?: return Result.failure(IllegalStateException("No AI model available"))
        return generateResponse(
            userId = userId,
            modelId = defaultModel.id,
            prompt = prompt,
            systemInstruction = systemInstruction,
            taskId = taskId,
            taskType = taskType
        ).map { text -> defaultModel.apiId to text }
    }
}
