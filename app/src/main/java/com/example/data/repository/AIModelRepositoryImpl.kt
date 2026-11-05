package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.AIProviderDao
import com.example.data.remote.OpenRouterApi
import com.example.data.remote.HuggingFaceApi
import com.example.data.remote.OpenRouterChatRequest
import com.example.data.remote.ChatMessage
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.util.SecurityUtils
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class AIModelRepositoryImpl(
    private val aiProviderDao: AIProviderDao,
    private val openRouterApi: OpenRouterApi,
    private val huggingFaceApi: HuggingFaceApi
) : AIModelRepository {

    override fun getProviders(userId: String): Flow<List<AIProvider>> = aiProviderDao.getProviders(userId)

    override suspend fun getProviderById(userId: String, id: String): AIProvider? = aiProviderDao.getProviderById(userId, id)

    override suspend fun addProvider(provider: AIProvider) {
        val encryptedKey = provider.apiKey?.let { SecurityUtils.encryptSecret(it) }
        val newProvider = if (provider.id.isEmpty()) provider.copy(id = UUID.randomUUID().toString()) else provider
        aiProviderDao.insertProvider(newProvider.copy(apiKey = encryptedKey))
    }

    override suspend fun deleteProvider(userId: String, id: String) {
        aiProviderDao.deleteModelsByProvider(id)
        aiProviderDao.deleteProvider(id)
    }

    override suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long) {
        aiProviderDao.updateProviderStatus(userId, id, status, syncTime)
    }

    override fun getModelsByProvider(providerId: String): Flow<List<AIModel>> = 
        aiProviderDao.getModelsByProvider(providerId)

    override fun getAllAvailableModels(freeOnly: Boolean): Flow<List<AIModel>> = 
        aiProviderDao.getAllAvailableModels().map { models ->
            if (freeOnly) models.filter { it.isFree } else models
        }

    override suspend fun getModelById(id: String): AIModel? = aiProviderDao.getModelById(id)

    override suspend fun syncModels(userId: String, providerId: String): Result<Unit> = runCatching {
        val provider = aiProviderDao.getProviderById(userId, providerId) ?: throw Exception("Provider not found or not owned by user")
        val apiKey = provider.apiKey?.let { SecurityUtils.decryptSecret(it) }
            ?.takeIf { it.isNotBlank() } ?: throw Exception("API key is missing")

        // Verify credentials with a real inference request before marking the provider connected.
        testConnection(userId, providerId).getOrThrow()

        val models = when (provider.type) {
            AIProviderType.OPEN_ROUTER -> {
                val response = openRouterApi.getModels("Bearer $apiKey")
                val fetchedModels = response.data.map { data ->
                    AIModel(
                        id = "${provider.id}:${data.id}",
                        providerId = provider.id,
                        apiId = data.id,
                        name = data.name,
                        isFree = data.pricing.prompt == "0" && data.pricing.completion == "0",
                        contextWindow = data.context_length,
                        inputPricePer1M = data.pricing.prompt.toDoubleOrNull()?.let { it * 1_000_000 } ?: 0.0,
                        outputPricePer1M = data.pricing.completion.toDoubleOrNull()?.let { it * 1_000_000 } ?: 0.0,
                        capabilities = inferCapabilities(data),
                        category = if (data.pricing.prompt == "0") AIModelCategory.FREE else AIModelCategory.GENERAL_PURPOSE
                    )
                }
                
                // Add the virtual "openrouter/free" model for intelligent routing
                val virtualFreeModel = AIModel(
                    id = "${provider.id}:openrouter/free",
                    providerId = provider.id,
                    apiId = "openrouter/free",
                    name = "OpenRouter Auto Free",
                    isFree = true,
                    contextWindow = 8192, // Conservative default
                    capabilities = listOf(AICapability.REASONING, AICapability.CODING, AICapability.AGENTIC),
                    category = AIModelCategory.FREE
                )
                
                fetchedModels + virtualFreeModel
            }
            AIProviderType.HUGGING_FACE -> {
                throw UnsupportedOperationException("Hugging Face inference is not configured yet. This provider is intentionally unavailable.")
                response.map { data ->
                    AIModel(
                        id = "${provider.id}:${data.modelId}",
                        providerId = provider.id,
                        apiId = data.modelId,
                        name = data.modelId.substringAfter("/"),
                        isFree = true,
                        capabilities = listOf(AICapability.REASONING),
                        category = AIModelCategory.FREE
                    )
                }
            }
            AIProviderType.GOOGLE_GEMINI -> {
                listOf(
                    AIModel(
                        id = "${provider.id}:gemini-1.5-flash",
                        providerId = provider.id,
                        apiId = "gemini-1.5-flash",
                        name = "Gemini 1.5 Flash",
                        isFree = true,
                        contextWindow = 1000000,
                        capabilities = listOf(AICapability.CODING, AICapability.REASONING, AICapability.LONG_CONTEXT, AICapability.VISION, AICapability.AGENTIC),
                        category = AIModelCategory.FREE
                    ),
                    AIModel(
                        id = "${provider.id}:gemini-1.5-pro",
                        providerId = provider.id,
                        apiId = "gemini-1.5-pro",
                        name = "Gemini 1.5 Pro",
                        isFree = false,
                        contextWindow = 2000000,
                        capabilities = listOf(AICapability.CODING, AICapability.REASONING, AICapability.LONG_CONTEXT, AICapability.VISION),
                        category = AIModelCategory.REASONING
                    )
                )
            }
            else -> emptyList()
        }

        if (models.isEmpty()) throw Exception("Provider returned no supported models")
        aiProviderDao.deleteModelsByProvider(providerId)
        aiProviderDao.insertModels(models)
        aiProviderDao.updateProviderStatus(userId, providerId, AIProviderStatus.CONNECTED, System.currentTimeMillis())
    }

    private fun inferCapabilities(data: com.example.data.remote.OpenRouterModelData): List<AICapability> {
        val list = mutableListOf<AICapability>()
        if (data.id.contains("code") || data.name.contains("coding", ignoreCase = true)) {
            list.add(AICapability.CODING)
        }
        if (data.context_length > 32000) {
            list.add(AICapability.LONG_CONTEXT)
        }
        return list
    }

    override suspend fun testConnection(userId: String, providerId: String): Result<Unit> = runCatching {
        val provider = aiProviderDao.getProviderById(userId, providerId) ?: throw Exception("Provider not found")
        val apiKey = provider.apiKey?.let { SecurityUtils.decryptSecret(it) } ?: throw Exception("API Key missing")

        when (provider.type) {
            AIProviderType.GOOGLE_GEMINI -> {
                val model = GenerativeModel("gemini-1.5-flash", apiKey)
                model.generateContent("test")
            }
            AIProviderType.OPEN_ROUTER -> {
                val response = openRouterApi.generateCompletion(
                    "Bearer $apiKey",
                    OpenRouterChatRequest(
                        model = "google/gemini-flash-1.5",
                        messages = listOf(ChatMessage("user", "hi"))
                    )
                )
                if (response.choices.firstOrNull()?.message?.content.isNullOrBlank()) {
                    throw Exception("Provider returned an empty test response")
                }
            }
            else -> throw UnsupportedOperationException("Connection test is not implemented for ${provider.type}")
        }
    }

    override fun getRecentUsageLogs(userId: String): Flow<List<AIUsageLog>> = aiProviderDao.getRecentUsageLogs(userId)

    override fun getTotalCost(userId: String, since: Long): Flow<Double?> = aiProviderDao.getTotalCost(userId, since)

    override suspend fun logUsage(log: AIUsageLog) {
        aiProviderDao.insertUsageLog(log)
    }

    override fun getUserSettings(userId: String): Flow<AIUserSettings?> = aiProviderDao.getUserSettings(userId)

    override suspend fun updateUserSettings(settings: AIUserSettings) {
        aiProviderDao.insertUserSettings(settings)
    }

    override suspend fun setDefaultModel(userId: String, modelId: String) {
        val current = aiProviderDao.getUserSettings(userId).first()
        val newSettings = (current ?: AIUserSettings(userId = userId)).copy(
            defaultModelId = modelId,
            lastModified = System.currentTimeMillis()
        )
        aiProviderDao.insertUserSettings(newSettings)
    }

    override suspend fun getDefaultModel(userId: String): AIModel? {
        val settings = aiProviderDao.getUserSettings(userId).first()
        val modelId = settings?.defaultModelId
        
        return if (modelId != null) {
            aiProviderDao.getModelById(modelId)
        } else {
            aiProviderDao.getAllAvailableModels().first().firstOrNull { it.isFree }
        }
    }

    override suspend fun generateResponse(
        userId: String,
        modelId: String,
        prompt: String,
        systemInstruction: String?,
        taskId: String?,
        taskType: AITaskType
    ): Result<String> = runCatching {
        if (prompt.isBlank()) throw IllegalArgumentException("Prompt cannot be empty")
        
        val settings = aiProviderDao.getUserSettings(userId).first() ?: AIUserSettings(userId = userId)
        val isFreeOnly = settings.freeOnly
        val isAutoSelect = settings.autoSelectFree

        // Check soft monthly limit
        val currentMonthCost = aiProviderDao.getTotalCost(userId, System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L).first() ?: 0.0
        if (currentMonthCost >= settings.usageLimitMonthly && !isFreeOnly) {
             throw Exception("لقد تجاوزت حد الاستخدام الشهري المخصص (${settings.usageLimitMonthly}$/شهر). يرجى مراجعة إعدادات الأمان أو استخدام النماذج المجانية فقط.")
        }

        // Build list of potential models to try (Failover Chain)
        val modelsToTry = mutableListOf<AIModel>()
        
        if (isAutoSelect) {
            // Intelligent routing: Start with best free models for this task
            val allModels = aiProviderDao.getAllAvailableModels().first()
            val freeModels = allModels.filter { it.isFree }
            val prioritized = prioritizeModelsForTask(freeModels, taskType)
            modelsToTry.addAll(prioritized)
        } else {
            // Manual selection: Specific model requested (if it exists and respects Free Only)
            val requestedModel = aiProviderDao.getModelById(modelId)
            if (requestedModel != null && (!isFreeOnly || requestedModel.isFree)) {
                modelsToTry.add(requestedModel)
            }

            // Default model from settings
            settings.defaultModelId?.let { defId ->
                if (defId != modelId) {
                    aiProviderDao.getModelById(defId)?.let { defModel ->
                        if (!isFreeOnly || defModel.isFree) {
                            modelsToTry.add(defModel)
                        }
                    }
                }
            }
        }

        if (modelsToTry.isEmpty()) {
            throw Exception("No available models found. Check your AI Provider settings and API keys.")
        }

        var lastException: Exception? = null
        for (model in modelsToTry) {
            try {
                return@runCatching executeInference(userId, model, prompt, systemInstruction, taskId, taskType)
            } catch (e: Exception) {
                lastException = e
                // If it's a 401/403 (Auth), we might want to stop, but for 429/5xx (Quota/Server), we failover
                if (e.message?.contains("401") == true || e.message?.contains("403") == true || e.message?.contains("Invalid API Key", ignoreCase = true) == true) {
                    // Only retry if we have other models to try
                    continue
                }
            }
        }
        
        throw lastException ?: Exception("Inference failed for all attempted models")
    }

    private suspend fun executeInference(
        userId: String,
        model: AIModel,
        prompt: String,
        systemInstruction: String?,
        taskId: String?,
        taskType: AITaskType
    ): String {
        val provider = aiProviderDao.getProviderById(userId, model.providerId) 
            ?: throw Exception("Provider not found for model ${model.name}")
        val apiKey = provider.apiKey?.let { SecurityUtils.decryptSecret(it) } 
            ?: throw Exception("API Key missing for provider ${provider.name}")

        return try {
            when (provider.type) {
                AIProviderType.GOOGLE_GEMINI -> {
                    val generativeModel = GenerativeModel(
                        modelName = model.apiId,
                        apiKey = apiKey,
                        systemInstruction = systemInstruction?.let { com.google.ai.client.generativeai.type.content { text(it) } }
                    )
                    val response = generativeModel.generateContent(prompt)
                    val text = response.text?.takeIf { it.isNotBlank() }
                        ?: throw Exception("Gemini returned an empty response")
                    
                    logUsage(AIUsageLog(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        modelId = model.id,
                        taskId = taskId ?: "",
                        tokensInput = response.usageMetadata?.promptTokenCount ?: 0,
                        tokensOutput = response.usageMetadata?.candidatesTokenCount ?: 0,
                        estimatedCost = 0.0,
                        taskType = taskType,
                        isSuccess = true
                    ))
                    text
                }
                AIProviderType.OPEN_ROUTER -> {
                    val response = openRouterApi.generateCompletion(
                        "Bearer $apiKey",
                        OpenRouterChatRequest(
                            model = model.apiId,
                            messages = listOfNotNull(
                                systemInstruction?.let { ChatMessage("system", it) },
                                ChatMessage("user", prompt)
                            )
                        )
                    )
                    val text = response.choices.firstOrNull()?.message?.content
                        ?.takeIf { it.isNotBlank() }
                        ?: throw Exception("OpenRouter returned an empty response")
                    
                    logUsage(AIUsageLog(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        modelId = model.id,
                        taskId = taskId ?: "",
                        tokensInput = response.usage?.prompt_tokens ?: 0,
                        tokensOutput = response.usage?.completion_tokens ?: 0,
                        estimatedCost = ((response.usage?.prompt_tokens ?: 0) * model.inputPricePer1M / 1_000_000.0) +
                                        ((response.usage?.completion_tokens ?: 0) * model.outputPricePer1M / 1_000_000.0),
                        taskType = taskType,
                        isSuccess = true
                    ))
                    text
                }
                else -> throw UnsupportedOperationException("Provider ${provider.type} inference not implemented")
            }
        } catch (e: Exception) {
            logUsage(AIUsageLog(
                id = UUID.randomUUID().toString(),
                userId = userId,
                modelId = model.id,
                taskId = taskId ?: "",
                taskType = taskType,
                isSuccess = false,
                errorMessage = e.message
            ))
            throw e
        }
    }

    private fun prioritizeModelsForTask(models: List<AIModel>, taskType: AITaskType): List<AIModel> {
        return models.sortedWith(compareByDescending<AIModel> { model ->
            var score = 0
            // Prioritize specific capabilities based on task
            when (taskType) {
                AITaskType.VIBE_AGENT_CODING, AITaskType.CODE_FIX_SUGGESTION -> {
                    if (model.capabilities.contains(AICapability.CODING)) score += 15
                    if (model.apiId.contains("deepseek-coder", ignoreCase = true)) score += 10
                    if (model.apiId.contains("qwen", ignoreCase = true)) score += 5
                }
                AITaskType.VIBE_AGENT_PLANNING, AITaskType.ROOT_CAUSE_DIAGNOSIS -> {
                    if (model.capabilities.contains(AICapability.REASONING)) score += 12
                    if (model.apiId.contains("gemini-1.5-flash", ignoreCase = true)) score += 8
                }
                AITaskType.VIBE_AGENT_REVIEW -> {
                    if (model.capabilities.contains(AICapability.REASONING)) score += 10
                    if (model.capabilities.contains(AICapability.CODING)) score += 5
                }
                AITaskType.REGRESSION_ANALYSIS -> {
                    if (model.capabilities.contains(AICapability.LONG_CONTEXT)) score += 10
                }
                else -> {}
            }
            
            // Prioritize Agentic models for agent tasks
            if (taskType.name.contains("AGENT") && model.capabilities.contains(AICapability.AGENTIC)) {
                score += 15
            }

            // Prefer OpenRouter free router as a robust starting point
            if (model.apiId == "openrouter/free") score += 25
            if (model.apiId.contains("free", ignoreCase = true)) score += 5
            
            score
        })
    }
}
