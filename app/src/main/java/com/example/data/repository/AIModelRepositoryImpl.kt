package com.example.data.repository

import android.util.Log
import com.example.data.local.AIProviderDao
import com.example.data.remote.OpenRouterApi
import com.example.data.remote.HuggingFaceApi
import com.example.data.remote.OpenRouterChatRequest
import com.example.data.remote.OpenRouterChatResponse
import com.example.data.remote.ChatMessage
import com.example.data.remote.OpenRouterModelData
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.util.SecurityUtils
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID

internal data class FirebaseAiGenerationOutput(
    val text: String,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0
)

class AIModelRepositoryImpl internal constructor(
    private val aiProviderDao: AIProviderDao,
    private val openRouterApi: OpenRouterApi,
    private val huggingFaceApi: HuggingFaceApi,
    private val encryptSecret: (String) -> String = SecurityUtils::encryptSecret,
    private val decryptSecret: (String) -> String = SecurityUtils::decryptSecret,
    private val enablePlatformFirebaseAi: Boolean = false,
    private val firebaseAiRunner: (suspend (modelName: String, prompt: String, systemInstruction: String?) -> FirebaseAiGenerationOutput)? = null,
    private val rateLimitRetryPolicy: RateLimitRetryPolicy = RateLimitRetryPolicy(),
    private val rateLimitDelay: suspend (Long) -> Unit = { delay(it) },
    private val nowMillis: () -> Long = System::currentTimeMillis
) : AIModelRepository {

    constructor(
        aiProviderDao: AIProviderDao,
        openRouterApi: OpenRouterApi,
        huggingFaceApi: HuggingFaceApi,
        encryptSecret: (String) -> String = SecurityUtils::encryptSecret,
        decryptSecret: (String) -> String = SecurityUtils::decryptSecret,
        enablePlatformFirebaseAi: Boolean = false
    ) : this(
        aiProviderDao = aiProviderDao,
        openRouterApi = openRouterApi,
        huggingFaceApi = huggingFaceApi,
        encryptSecret = encryptSecret,
        decryptSecret = decryptSecret,
        enablePlatformFirebaseAi = enablePlatformFirebaseAi,
        firebaseAiRunner = null
    )

    private val firebaseAiBackend by lazy { Firebase.ai }

    private fun isPlatformFirebaseAiEnabled(userId: String): Boolean =
        enablePlatformFirebaseAi && userId.isNotBlank()

    override fun getProviders(userId: String): Flow<List<AIProvider>> =
        aiProviderDao.getProviders(userId).map { dbProviders ->
            if (!isPlatformFirebaseAiEnabled(userId)) {
                dbProviders
            } else {
                val platformProvider = FirebaseAiModelCatalog.platformProvider(userId)
                listOf(platformProvider) + dbProviders.filterNot { it.id == platformProvider.id }
            }
        }

    override suspend fun getProviderById(userId: String, id: String): AIProvider? {
        if (isPlatformFirebaseAiEnabled(userId) && id == FirebaseAiModelCatalog.platformProviderId(userId)) {
            return FirebaseAiModelCatalog.platformProvider(userId)
        }
        return aiProviderDao.getProviderById(userId, id)
    }

    override suspend fun addProvider(provider: AIProvider) {
        if (provider.type == AIProviderType.FIREBASE_AI_LOGIC) {
            throw UnsupportedOperationException("Built-in Firebase AI Logic provider is managed automatically")
        }
        val encryptedKey = provider.apiKey?.let(encryptSecret)
        val newProvider = if (provider.id.isEmpty()) provider.copy(id = UUID.randomUUID().toString()) else provider
        aiProviderDao.insertProvider(newProvider.copy(apiKey = encryptedKey))
    }

    override suspend fun deleteProvider(userId: String, id: String) {
        if (isPlatformFirebaseAiEnabled(userId) && id == FirebaseAiModelCatalog.platformProviderId(userId)) {
            throw UnsupportedOperationException("Built-in Firebase AI Logic provider cannot be deleted")
        }
        aiProviderDao.deleteModelsByProvider(userId, id)
        aiProviderDao.deleteProvider(userId, id)
    }

    override suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long) {
        if (isPlatformFirebaseAiEnabled(userId) && id == FirebaseAiModelCatalog.platformProviderId(userId)) {
            return
        }
        aiProviderDao.updateProviderStatus(userId, id, status, syncTime)
    }

    override fun getModelsByProvider(userId: String, providerId: String): Flow<List<AIModel>> {
        if (isPlatformFirebaseAiEnabled(userId) && providerId == FirebaseAiModelCatalog.platformProviderId(userId)) {
            return flowOf(FirebaseAiModelCatalog.models(providerId))
        }
        return aiProviderDao.getModelsByProvider(userId, providerId)
    }

    override fun getAllAvailableModels(userId: String, freeOnly: Boolean): Flow<List<AIModel>> =
        aiProviderDao.getAllAvailableModels(userId).map { dbModels ->
            val combined = if (!isPlatformFirebaseAiEnabled(userId)) {
                dbModels
            } else {
                val platformModels = FirebaseAiModelCatalog.models(FirebaseAiModelCatalog.platformProviderId(userId))
                val platformIds = platformModels.mapTo(HashSet()) { it.id }
                platformModels + dbModels.filterNot { it.id in platformIds }
            }
            if (freeOnly) combined.filter { it.isFree } else combined
        }

    override suspend fun getModelById(userId: String, id: String): AIModel? {
        if (isPlatformFirebaseAiEnabled(userId)) {
            FirebaseAiModelCatalog.models(FirebaseAiModelCatalog.platformProviderId(userId))
                .firstOrNull { it.id == id }
                ?.let { return it }
        }
        return aiProviderDao.getModelById(userId, id)
    }

    override suspend fun syncModels(userId: String, providerId: String): Result<Unit> = runProviderOperation {
        val provider = getProviderById(userId, providerId)
            ?: throw AIProviderOperationException(AIProviderFailureKind.MISCONFIGURED)

        if (provider.type == AIProviderType.FIREBASE_AI_LOGIC) {
            verifyFirebaseAiConnection()
            return@runProviderOperation
        }

        val apiKey = provider.apiKey?.let(decryptSecret)
            ?.takeIf { it.isNotBlank() } ?: throw AIProviderOperationException(AIProviderFailureKind.MISCONFIGURED)

        val openRouterModels = when (provider.type) {
            AIProviderType.OPEN_ROUTER -> openRouterApi.getModels("Bearer $apiKey").data.also {
                verifyOpenRouterConnection(apiKey, it)
            }
            AIProviderType.GOOGLE_GEMINI -> {
                verifyGeminiConnection(apiKey)
                emptyList()
            }
            AIProviderType.HUGGING_FACE, AIProviderType.CUSTOM, AIProviderType.FIREBASE_AI_LOGIC ->
                throw AIProviderOperationException(AIProviderFailureKind.UNSUPPORTED)
        }

        val models = when (provider.type) {
            AIProviderType.OPEN_ROUTER -> {
                val fetchedModels = openRouterModels.mapNotNull { data ->
                    val promptPrice = data.pricing.prompt.toValidPricePerToken() ?: return@mapNotNull null
                    val completionPrice = data.pricing.completion.toValidPricePerToken() ?: return@mapNotNull null
                    val isFree = promptPrice == 0.0 && completionPrice == 0.0
                    AIModel(
                        id = "${provider.id}:${data.id}",
                        providerId = provider.id,
                        apiId = data.id,
                        name = data.name,
                        isFree = isFree,
                        contextWindow = data.context_length,
                        inputPricePer1M = promptPrice * 1_000_000.0,
                        outputPricePer1M = completionPrice * 1_000_000.0,
                        capabilities = inferCapabilities(data),
                        category = if (isFree) AIModelCategory.FREE else AIModelCategory.GENERAL_PURPOSE
                    )
                }

                val virtualFreeModel = AIModel(
                    id = "${provider.id}:openrouter/free",
                    providerId = provider.id,
                    apiId = "openrouter/free",
                    name = "OpenRouter Auto Free",
                    isFree = true,
                    contextWindow = 8192,
                    capabilities = listOf(AICapability.REASONING, AICapability.CODING, AICapability.AGENTIC),
                    category = AIModelCategory.FREE
                )

                fetchedModels + virtualFreeModel
            }
            AIProviderType.GOOGLE_GEMINI -> GeminiModelCatalog.models(provider.id)
            AIProviderType.HUGGING_FACE, AIProviderType.CUSTOM, AIProviderType.FIREBASE_AI_LOGIC -> emptyList()
        }

        if (models.isEmpty()) throw AIProviderOperationException(AIProviderFailureKind.EMPTY_RESPONSE)
        aiProviderDao.replaceModelsForProvider(userId, providerId, models)
        aiProviderDao.updateProviderStatus(userId, providerId, AIProviderStatus.CONNECTED, System.currentTimeMillis())
    }

    private suspend fun verifyOpenRouterConnection(apiKey: String, models: List<com.example.data.remote.OpenRouterModelData>) {
        val testModel = models.firstOrNull { it.isFreeModel() }
            ?: models.firstOrNull()
            ?: throw AIProviderOperationException(AIProviderFailureKind.EMPTY_RESPONSE)
        val response = openRouterApi.generateCompletion(
            "Bearer $apiKey",
            OpenRouterChatRequest(
                model = testModel.id,
                messages = listOf(ChatMessage("user", "ping"))
            )
        )
        if (response.choices.firstOrNull()?.message?.content.isNullOrBlank()) {
            throw AIProviderOperationException(AIProviderFailureKind.EMPTY_RESPONSE)
        }
    }

    private suspend fun verifyGeminiConnection(apiKey: String) {
        val response = GenerativeModel(GeminiModelCatalog.CONNECTION_MODEL_ID, apiKey).generateContent("ping")
        if (response.text.isNullOrBlank()) {
            throw IllegalStateException("Provider returned an empty test response")
        }
    }

    private suspend fun verifyFirebaseAiConnection() {
        val result = invokeFirebaseAi(FirebaseAiModelCatalog.DEFAULT_MODEL_ID, "ping", null)
        if (result.text.isBlank()) {
            throw AIProviderOperationException(AIProviderFailureKind.EMPTY_RESPONSE)
        }
    }

    private suspend fun invokeFirebaseAi(
        modelName: String,
        prompt: String,
        systemInstruction: String?
    ): FirebaseAiGenerationOutput {
        return try {
            if (firebaseAiRunner != null) {
                withTimeout(FIREBASE_AI_TIMEOUT_MS) {
                    firebaseAiRunner.invoke(modelName, prompt, systemInstruction)
                }
            } else {
                val model = firebaseAiBackend.generativeModel(
                    modelName = modelName,
                    systemInstruction = systemInstruction?.takeIf { it.isNotBlank() }?.let { instruction ->
                        content { text(instruction) }
                    }
                )
                val response = withTimeout(FIREBASE_AI_TIMEOUT_MS) {
                    model.generateContent(prompt)
                }
                val text = response.text?.takeIf { it.isNotBlank() }
                    ?: throw AIProviderOperationException(AIProviderFailureKind.EMPTY_RESPONSE)
                FirebaseAiGenerationOutput(
                    text = text,
                    inputTokens = response.usageMetadata?.promptTokenCount ?: 0,
                    outputTokens = response.usageMetadata?.candidatesTokenCount ?: 0
                )
            }
        } catch (timeout: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI Logic generation timed out for $modelName", timeout)
            throw AIProviderOperationException(AIProviderFailureKind.TIMEOUT, timeout)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic generation failed for $modelName", e)
            throw e
        }
    }

    private fun OpenRouterModelData.isFreeModel(): Boolean =
        pricing.prompt.toValidPricePerToken() == 0.0 &&
            pricing.completion.toValidPricePerToken() == 0.0

    private fun String.toValidPricePerToken(): Double? =
        toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

    private suspend fun <T> runProviderOperation(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (timeout: TimeoutCancellationException) {
        Result.failure(timeout.toAIProviderOperationException())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure.toAIProviderOperationException())
    }

    private fun Throwable.toAIProviderOperationException(): AIProviderOperationException {
        if (this is AIProviderOperationException) return this
        val causes = generateSequence(this) { it.cause }.toList()
        val httpFailure = causes.filterIsInstance<HttpException>().firstOrNull()
        val statusCode = httpFailure?.code()
        val kind = when {
            this is TimeoutCancellationException || causes.any { it is TimeoutCancellationException } ->
                AIProviderFailureKind.TIMEOUT
            statusCode == 401 -> AIProviderFailureKind.INVALID_CREDENTIALS
            statusCode == 403 -> AIProviderFailureKind.ACCESS_DENIED
            statusCode == 402 -> AIProviderFailureKind.QUOTA_EXCEEDED
            statusCode == 429 -> AIProviderFailureKind.RATE_LIMITED
            statusCode != null && statusCode in 500..599 -> AIProviderFailureKind.SERVICE_UNAVAILABLE
            causes.any { it is SocketTimeoutException } -> AIProviderFailureKind.TIMEOUT
            causes.any { it is IOException } -> AIProviderFailureKind.NETWORK
            this is UnsupportedOperationException -> AIProviderFailureKind.UNSUPPORTED
            this is IllegalArgumentException -> AIProviderFailureKind.MISCONFIGURED
            else -> AIProviderFailureKind.UNKNOWN
        }
        return AIProviderOperationException(kind, this)
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

    override suspend fun testConnection(userId: String, providerId: String): Result<Unit> = runProviderOperation {
        val provider = getProviderById(userId, providerId)
            ?: throw IllegalArgumentException("Provider not found or not owned by user")

        if (provider.type == AIProviderType.FIREBASE_AI_LOGIC) {
            verifyFirebaseAiConnection()
            return@runProviderOperation
        }

        val apiKey = provider.apiKey?.let(decryptSecret)
            ?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("API key is missing")

        when (provider.type) {
            AIProviderType.GOOGLE_GEMINI -> verifyGeminiConnection(apiKey)
            AIProviderType.OPEN_ROUTER ->
                verifyOpenRouterConnection(apiKey, openRouterApi.getModels("Bearer $apiKey").data)
            AIProviderType.HUGGING_FACE, AIProviderType.CUSTOM, AIProviderType.FIREBASE_AI_LOGIC ->
                throw AIProviderOperationException(AIProviderFailureKind.UNSUPPORTED)
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
        requireNotNull(getModelById(userId, modelId)) {
            "Selected model is not owned by the current user"
        }
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
            getModelById(userId, modelId)
        } else {
            getAllAvailableModels(userId, freeOnly = false).first().firstOrNull { it.isFree }
        }
    }

    override suspend fun generateResponse(
        userId: String,
        modelId: String,
        prompt: String,
        systemInstruction: String?,
        taskId: String?,
        taskType: AITaskType
    ): Result<String> {
        return try {
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
                val allModels = getAllAvailableModels(userId, freeOnly = false).first()
                val freeModels = allModels.filter { it.isFree }
                val prioritized = prioritizeModelsForTask(freeModels, taskType)
                modelsToTry.addAll(prioritized)
            } else {
                // Manual selection: Specific model requested (if it exists and respects Free Only)
                val requestedModel = getModelById(userId, modelId)
                if (requestedModel != null && (!isFreeOnly || requestedModel.isFree)) {
                    modelsToTry.add(requestedModel)
                }

                // Default model from settings
                settings.defaultModelId?.let { defId ->
                    if (defId != modelId) {
                        getModelById(userId, defId)?.let { defModel ->
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
                    return Result.success(
                        executeInference(userId, model, prompt, systemInstruction, taskId, taskType)
                    )
                } catch (timeout: TimeoutCancellationException) {
                    lastException = timeout
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    lastException = e
                    // If it's a 401/403 (Auth), we might want to stop, but for 429/5xx (Quota/Server), we failover
                    if (e.message?.contains("401") == true || e.message?.contains("403") == true || e.message?.contains("Invalid API Key", ignoreCase = true) == true) {
                        // Only retry if we have other models to try
                        continue
                    }
                }
            }
            
            Result.failure(lastException ?: Exception("Inference failed for all attempted models"))
        } catch (timeout: TimeoutCancellationException) {
            Result.failure(timeout)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun generateOpenRouterCompletionWithRetry(
        token: String,
        request: OpenRouterChatRequest
    ): OpenRouterChatResponse {
        var retryAttempt = 0
        while (true) {
            try {
                return openRouterApi.generateCompletion(token, request)
            } catch (rateLimit: HttpException) {
                if (rateLimit.code() != 429) throw rateLimit
                retryAttempt += 1
                val waitMillis = rateLimitRetryPolicy.delayBeforeRetry(
                    retryAttempt = retryAttempt,
                    retryAfterHeader = rateLimit.response()?.headers()?.get("Retry-After"),
                    nowMillis = nowMillis()
                ) ?: throw rateLimit
                rateLimitDelay(waitMillis)
            }
        }
    }

    private suspend fun executeInference(
        userId: String,
        model: AIModel,
        prompt: String,
        systemInstruction: String?,
        taskId: String?,
        taskType: AITaskType
    ): String {
        val provider = getProviderById(userId, model.providerId)
            ?: throw Exception("Provider not found for model ${model.name}")

        return try {
            when (provider.type) {
                AIProviderType.FIREBASE_AI_LOGIC -> {
                    val result = invokeFirebaseAi(model.apiId, prompt, systemInstruction)
                    logUsage(AIUsageLog(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        modelId = model.id,
                        taskId = taskId ?: "",
                        tokensInput = result.inputTokens,
                        tokensOutput = result.outputTokens,
                        estimatedCost = 0.0,
                        taskType = taskType,
                        isSuccess = true
                    ))
                    result.text
                }
                AIProviderType.GOOGLE_GEMINI -> {
                    val apiKey = provider.apiKey?.let(decryptSecret)
                        ?: throw Exception("API Key missing for provider ${provider.name}")
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
                        estimatedCost = AIModelPricing.estimateCost(
                            model,
                            response.usageMetadata?.promptTokenCount ?: 0,
                            response.usageMetadata?.candidatesTokenCount ?: 0
                        ),
                        taskType = taskType,
                        isSuccess = true
                    ))
                    text
                }
                AIProviderType.OPEN_ROUTER -> {
                    val apiKey = provider.apiKey?.let(decryptSecret)
                        ?: throw Exception("API Key missing for provider ${provider.name}")
                    val response = generateOpenRouterCompletionWithRetry(
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
                        estimatedCost = AIModelPricing.estimateCost(
                            model,
                            response.usage?.prompt_tokens ?: 0,
                            response.usage?.completion_tokens ?: 0
                        ),
                        taskType = taskType,
                        isSuccess = true
                    ))
                    text
                }
                else -> throw UnsupportedOperationException("Provider ${provider.type} inference not implemented")
            }
        } catch (timeout: TimeoutCancellationException) {
            logUsage(AIUsageLog(
                id = UUID.randomUUID().toString(),
                userId = userId,
                modelId = model.id,
                taskId = taskId ?: "",
                taskType = taskType,
                isSuccess = false,
                errorMessage = timeout.message ?: "Request timed out"
            ))
            throw timeout
        } catch (cancelled: CancellationException) {
            throw cancelled
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
                    if (model.apiId.contains(GeminiModelCatalog.CONNECTION_MODEL_ID, ignoreCase = true)) score += 8
                    if (model.apiId.contains("gemini-3.5-flash-lite", ignoreCase = true)) score += 5
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

            // Prefer OpenRouter free router or built-in Firebase AI Gemini as robust starting points
            if (model.apiId == "openrouter/free") score += 25
            if (model.providerId.startsWith("firebase_ai_platform:")) score += 20
            if (model.apiId.contains("free", ignoreCase = true)) score += 5
            
            score
        })
    }

    private companion object {
        const val TAG = "AIModelRepository"
        const val FIREBASE_AI_TIMEOUT_MS = 60_000L
    }
}
