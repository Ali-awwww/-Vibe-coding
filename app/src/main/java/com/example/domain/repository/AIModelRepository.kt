package com.example.domain.repository

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

interface AIModelRepository {
    fun getProviders(userId: String): Flow<List<AIProvider>>
    suspend fun getProviderById(userId: String, id: String): AIProvider?
    suspend fun addProvider(provider: AIProvider)
    suspend fun deleteProvider(userId: String, id: String)
    suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long)

    fun getModelsByProvider(providerId: String): Flow<List<AIModel>>
    fun getAllAvailableModels(freeOnly: Boolean = false): Flow<List<AIModel>>
    suspend fun getModelById(id: String): AIModel?
    suspend fun syncModels(userId: String, providerId: String): Result<Unit>
    
    suspend fun testConnection(userId: String, providerId: String): Result<Unit>
    
    fun getRecentUsageLogs(userId: String): Flow<List<AIUsageLog>>
    fun getTotalCost(userId: String, since: Long): Flow<Double?>
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
}
