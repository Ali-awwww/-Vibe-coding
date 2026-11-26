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
import com.example.domain.repository.GeminiDailyQuotaExceededException
import com.example.util.SecurityUtils
import com.example.util.recordCrashlyticsNonFatal
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class FirebaseAiGenerationOutput(
    val text: String,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null
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

    private data class PlatformProviderState(
        val status: AIProviderStatus,
        val lastSyncAt: Long?
    )

    private val platformProviderStates = MutableStateFlow<Map<String, PlatformProviderState>>(emptyMap())
    private val verifiedProviderConnections = ConcurrentHashMap.newKeySet<Pair<String, String>>()

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

    override fun getProviders(userId: String): Flow<List<AIProvider>> {
        val databaseProviders = aiProviderDao.getProviders(userId)
        if (!isPlatformFirebaseAiEnabled(userId)) return databaseProviders

        return combine(databaseProviders, platformProviderStates) { dbProviders, states ->
            val state = states[userId]
            val platformProvider = FirebaseAiModelCatalog.platformProvider(userId).copy(
                status = state?.status ?: AIProviderStatus.DISCONNECTED,
                lastSyncAt = state?.lastSyncAt
            )
            listOf(platformProvider) + dbProviders.filterNot { it.id == platformProvider.id }
        }
    }

    override suspend fun getProviderById(userId: String, id: String): AIProvider? {
        if (isPlatformFirebaseAiEnabled(userId) && id == FirebaseAiModelCatalog.platformProviderId(userId)) {
            val state = platformProviderStates.value[userId]
            return FirebaseAiModelCatalog.platformProvider(userId).copy(
                status = state?.status ?: AIProviderStatus.DISCONNECTED,
                lastSyncAt = state?.lastSyncAt
            )
        }
        return aiProviderDao.getProviderById(userId, id)
    }

    override suspend fun addProvider(provider: AIProvider) {
        if (provider.type == AIProviderType.FIREBASE_AI_LOGIC) {
            throw UnsupportedOperationException("Built-in Firebase AI Logic provider is managed automatically")
        }
        val encryptedKey = provider.apiKey?.let(encryptSecret)
        val newProvider = if (provider.id.isEmpty()) provider.copy(id = UUID.randomUUID().toString()) else provider
        verifiedProviderConnections.remove(provider.userId to newProvider.id)
        aiProviderDao.insertProvider(
            newProvider.copy(
                apiKey = encryptedKey,
                status = AIProviderStatus.DISCONNECTED,
                lastSyncAt = null
            )
        )
    }

    override suspend fun deleteProvider(userId: String, id: String) {
        if (isPlatformFirebaseAiEnabled(userId) && id == FirebaseAiModelCatalog.platformProviderId(userId)) {
            throw UnsupportedOperationException("Built-in Firebase AI Logic provider cannot be deleted")
        }
        verifiedProviderConnections.remove(userId to id)
        aiProviderDao.deleteModelsByProvider(userId, id)
        aiProviderDao.deleteProvider(userId, id)
    }

    override suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long) {
        val connectionKey = userId to id
        if (status == AIProviderStatus.CONNECTED && connectionKey !in verifiedProviderConnections) {
            throw IllegalStateException("A provider can be marked CONNECTED only after a successful live connection check")
        }
        if (status != AIProviderStatus.CONNECTED) {
            verifiedProviderConnections.remove(connectionKey)
        }

        if (isPlatformFirebaseAiEnabled(userId) && id == FirebaseAiModelCatalog.platformProviderId(userId)) {
            platformProviderStates.update { current ->
                current + (userId to PlatformProviderState(status, syncTime))
            }
            return
        }
        aiProviderDao.updateProviderStatus(userId, id, status, syncTime)
    }

    override fun getModelsByProvider(userId: String, providerId: String): Flow<List<AIModel>> {
        if (isPlatformFirebaseAiEnabled(userId) && providerId == FirebaseAiModelCatalog.platformProviderId(userId)) {
            return flowOf(FirebaseAiModelCatalog.models(providerId))
        }
        return combine(
            aiProviderDao.getModelsByProvider(userId, providerId),
            aiProviderDao.getProviders(userId)
        ) { cachedModels, providers ->
            if (providers.firstOrNull { it.id == providerId }?.type == AIProviderType.GOOGLE_GEMINI) {
                GeminiModelCatalog.models(providerId)
            } else {
                cachedModels
            }
        }
    }

    override fun getAllAvailableModels(userId: String, freeOnly: Boolean): Flow<List<AIModel>> =
        combine(aiProviderDao.getAllAvailableModels(userId), getProviders(userId)) { dbModels, providers ->
            val connectedProviders = providers.filter { it.status == AIProviderStatus.CONNECTED }
            val connectedProviderIds = connectedProviders.mapTo(HashSet()) { it.id }
            val geminiProviderIds = providers
                .filter { it.type == AIProviderType.GOOGLE_GEMINI }
                .mapTo(HashSet()) { it.id }
            val platformProviderId = if (isPlatformFirebaseAiEnabled(userId)) {
                FirebaseAiModelCatalog.platformProviderId(userId)
            } else {
                null
            }
            val managedProviderIds = HashSet(geminiProviderIds).apply {
                platformProviderId?.let(::add)
            }
            val platformModels = platformProviderId?.let(FirebaseAiModelCatalog::models).orEmpty()
            val verifiedGeminiModels = connectedProviders
                .filter { it.type == AIProviderType.GOOGLE_GEMINI }
                .flatMap { provider -> GeminiModelCatalog.models(provider.id) }
            val combined = platformModels + verifiedGeminiModels + dbModels.filterNot {
                it.providerId in managedProviderIds
            }
            combined.filter { it.providerId in connectedProviderIds && (!freeOnly || it.isFree) }
        }

    override suspend fun getModelById(userId: String, id: String): AIModel? {
        if (isPlatformFirebaseAiEnabled(userId)) {
            FirebaseAiModelCatalog.models(FirebaseAiModelCatalog.platformProviderId(userId))
                .firstOrNull { it.id == id }
                ?.let { return it }
        }
        val cachedModel = aiProviderDao.getModelById(userId, id) ?: return null
        val provider = aiProviderDao.getProviderById(userId, cachedModel.providerId) ?: return null
        return if (provider.type == AIProviderType.GOOGLE_GEMINI) {
            GeminiModelCatalog.models(provider.id).firstOrNull { it.id == id }
        } else {
            cachedModel
        }
    }

    override suspend fun syncModels(userId: String, providerId: String): Result<Unit> = runProviderOperation {
        val provider = getProviderById(userId, providerId)
            ?: throw AIProviderOperationException(AIProviderFailureKind.MISCONFIGURED)

        if (provider.type == AIProviderType.FIREBASE_AI_LOGIC) {
            verifyFirebaseAiConnection()
            verifiedProviderConnections.add(userId to providerId)
            updateProviderStatus(userId, providerId, AIProviderStatus.CONNECTED, nowMillis())
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
        verifiedProviderConnections.add(userId to providerId)
        updateProviderStatus(userId, providerId, AIProviderStatus.CONNECTED, nowMillis())
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
                    inputTokens = response.usageMetadata?.promptTokenCount,
                    outputTokens = response.usageMetadata?.candidatesTokenCount
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
            verifiedProviderConnections.add(userId to providerId)
            updateProviderStatus(userId, providerId, AIProviderStatus.CONNECTED, nowMillis())
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
        verifiedProviderConnections.add(userId to providerId)
        updateProviderStatus(userId, providerId, AIProviderStatus.CONNECTED, nowMillis())
    }

    override fun getRecentUsageLogs(userId: String): Flow<List<AIUsageLog>> = aiProviderDao.getRecentUsageLogs(userId)

    override fun getTotalCost(userId: String, since: Long): Flow<Double?> = aiProviderDao.getTotalCost(userId, since)

    override fun hasUnpricedUsageSince(userId: String, since: Long): Flow<Boolean> =
        aiProviderDao.hasUnpricedUsageSince(userId, since)

    override suspend fun logUsage(log: AIUsageLog) {
        aiProviderDao.insertUsageLog(log)
    }

    override fun getUserSettings(userId: String): Flow<AIUserSettings?> = aiProviderDao.getUserSettings(userId)

    override suspend fun updateUserSettings(settings: AIUserSettings) {
        aiProviderDao.insertUserSettings(settings)
    }

    override suspend fun setDefaultModel(userId: String, modelId: String) {
        val availableModel = getAllAvailableModels(userId, freeOnly = false)
            .first()
            .firstOrNull { it.id == modelId }
        requireNotNull(availableModel) {
            "Selected model is unavailable until its provider passes a live connection check"
        }
        val current = aiProviderDao.getUserSettings(userId).first()
        val newSettings = (current ?: AIUserSettings(userId = userId)).copy(
            defaultModelId = modelId,
            lastModified = nowMillis()
        )
        aiProviderDao.insertUserSettings(newSettings)
    }

    override suspend fun getDefaultModel(userId: String): AIModel? {
        val availableModels = getAllAvailableModels(userId, freeOnly = false).first()
        val savedModelId = aiProviderDao.getUserSettings(userId).first()?.defaultModelId
        return if (savedModelId == null) {
            availableModels.firstOrNull { it.isFree }
        } else {
            availableModels.firstOrNull { it.id == savedModelId }
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

            // Check the 30-day soft monthly limit against priced usage only.
            val usageWindowStart = nowMillis() - 30L * 24 * 60 * 60 * 1000L
            val currentMonthCost = aiProviderDao.getTotalCost(userId, usageWindowStart).first() ?: 0.0
            if (currentMonthCost >= settings.usageLimitMonthly && !isFreeOnly) {
                 throw Exception("لقد تجاوزت حد الاستخدام الشهري المخصص (${settings.usageLimitMonthly}$/شهر). يرجى مراجعة إعدادات الأمان أو استخدام النماذج المجانية فقط.")
            }

            // Only providers with a successful live connection check may enter the inference chain.
            val availableModels = getAllAvailableModels(userId, freeOnly = false).first()
            val modelsToTry = mutableListOf<AIModel>()

            if (isAutoSelect) {
                val freeModels = availableModels.filter { it.isFree }
                modelsToTry.addAll(prioritizeModelsForTask(freeModels, taskType))
            } else {
                val requestedModel = availableModels.firstOrNull { it.id == modelId }
                if (requestedModel != null && (!isFreeOnly || requestedModel.isFree)) {
                    modelsToTry.add(requestedModel)
                }

                settings.defaultModelId?.let { defaultId ->
                    if (defaultId != modelId) {
                        availableModels.firstOrNull { it.id == defaultId }?.let { defaultModel ->
                            if (!isFreeOnly || defaultModel.isFree) {
                                modelsToTry.add(defaultModel)
                            }
                        }
                    }
                }
            }

            if (modelsToTry.isEmpty()) {
                throw Exception("No connected AI models are available. Test a provider connection and sync its model catalog first.")
            }

            if (
                modelsToTry.any { !it.isFree } &&
                aiProviderDao.hasUnpricedUsageSince(userId, usageWindowStart).first()
            ) {
                throw IllegalStateException(
                    "توجد طلبات سابقة بتكلفة غير مؤكدة خلال آخر 30 يوماً. أوقفنا النماذج المدفوعة حتى تتوفر بيانات الاستخدام والتسعير؛ يمكنك تفعيل وضع النماذج المجانية المضمونة."
                )
            }

            var lastException: Exception? = null
            for (model in modelsToTry) {
                try {
                    return Result.success(
                        executeInference(userId, model, prompt, systemInstruction, taskId, taskType)
                    )
                } catch (timeout: TimeoutCancellationException) {
                    lastException = timeout
                    if (!model.isFree) break
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    val operationFailure = e.toAIProviderOperationException()
                    lastException = operationFailure
                    val costMayBeUnknown = operationFailure.kind in setOf(
                        AIProviderFailureKind.TIMEOUT,
                        AIProviderFailureKind.NETWORK,
                        AIProviderFailureKind.SERVICE_UNAVAILABLE,
                        AIProviderFailureKind.EMPTY_RESPONSE,
                        AIProviderFailureKind.UNKNOWN
                    )
                    if (!model.isFree && costMayBeUnknown) break
                    if (
                        operationFailure.kind == AIProviderFailureKind.INVALID_CREDENTIALS ||
                        operationFailure.kind == AIProviderFailureKind.ACCESS_DENIED
                    ) {
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

    override suspend fun generateWithFirebaseAiChain(
        userId: String,
        prompt: String,
        systemInstruction: String?,
        taskId: String?,
        taskType: AITaskType
    ): Result<Pair<String, String>> {
        if (prompt.isBlank()) {
            return Result.failure(IllegalArgumentException("Prompt cannot be empty"))
        }
        if (!enablePlatformFirebaseAi && firebaseAiRunner == null) {
            val defaultModel = getDefaultModel(userId)
                ?: return Result.failure(IllegalStateException("Firebase AI Logic is not configured"))
            return generateResponse(
                userId = userId,
                modelId = defaultModel.id,
                prompt = prompt,
                systemInstruction = systemInstruction,
                taskId = taskId,
                taskType = taskType
            ).map { text -> defaultModel.apiId to text }
        }

        val modelChain = listOf(
            GeminiModelCatalog.CONNECTION_MODEL_ID,
            FirebaseAiModelCatalog.LITE_MODEL_ID
        )
        val providerId = FirebaseAiModelCatalog.platformProviderId(userId)
        val catalogByApiId = FirebaseAiModelCatalog.models(providerId).associateBy { it.apiId }

        var lastError: Exception? = null
        for (modelName in modelChain) {
            val modelMeta = catalogByApiId[modelName]
            val modelId = modelMeta?.id ?: "$providerId:$modelName"
            val result = invokeFirebaseAiWithOverloadRetry(
                userId = userId,
                modelId = modelId,
                modelMeta = modelMeta,
                modelName = modelName,
                prompt = prompt,
                systemInstruction = systemInstruction,
                taskId = taskId,
                taskType = taskType
            )
            if (result.isSuccess) {
                return Result.success(modelName to result.getOrThrow())
            }
            val failure = result.exceptionOrNull() as? Exception
                ?: IllegalStateException("Firebase AI Logic failed")
            if (failure is GeminiDailyQuotaExceededException) {
                return Result.failure(failure)
            }
            lastError = failure
        }

        return Result.failure(
            lastError ?: IllegalStateException("Firebase AI Logic models failed")
        )
    }

    private suspend fun invokeFirebaseAiWithOverloadRetry(
        userId: String,
        modelId: String,
        modelMeta: AIModel?,
        modelName: String,
        prompt: String,
        systemInstruction: String?,
        taskId: String?,
        taskType: AITaskType
    ): Result<String> {
        val maxOverloadRetries = 2
        var attempt = 0
        while (true) {
            try {
                val output = invokeFirebaseAi(modelName, prompt, systemInstruction)
                val estimatedCost = modelMeta?.let {
                    estimateCostOrUnknown(it, output.inputTokens, output.outputTokens)
                } ?: AIUsageLog.UNKNOWN_COST
                logUsage(
                    AIUsageLog(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        modelId = modelId,
                        taskId = taskId ?: "",
                        tokensInput = output.inputTokens ?: 0,
                        tokensOutput = output.outputTokens ?: 0,
                        estimatedCost = estimatedCost,
                        taskType = taskType,
                        isSuccess = true
                    )
                )
                return Result.success(output.text)
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException) {
                    logUsage(
                        AIUsageLog(
                            id = UUID.randomUUID().toString(),
                            userId = userId,
                            modelId = modelId,
                            taskId = taskId ?: "",
                            estimatedCost = AIUsageLog.UNKNOWN_COST,
                            taskType = taskType,
                            isSuccess = false,
                            errorMessage = cancelled.message ?: "Request timed out"
                        )
                    )
                    return Result.failure(cancelled)
                }
                throw cancelled
            } catch (e: Exception) {
                if (isDailyQuotaExhausted(e)) {
                    val quotaError = GeminiDailyQuotaExceededException(
                        message = "تم استنفاد الحصة اليومية لـ Gemini؛ تم إيقاف المحاولات.",
                        cause = e
                    )
                    logUsage(
                        AIUsageLog(
                            id = UUID.randomUUID().toString(),
                            userId = userId,
                            modelId = modelId,
                            taskId = taskId ?: "",
                            estimatedCost = 0.0,
                            taskType = taskType,
                            isSuccess = false,
                            errorMessage = quotaError.message
                        )
                    )
                    recordCrashlyticsNonFatal(quotaError, "Gemini daily quota reached on $modelName")
                    return Result.failure(quotaError)
                }

                val isOverload = isHttp503Overload(e)
                if (isOverload && attempt < maxOverloadRetries) {
                    val backoffMs = 800L * (1 shl attempt)
                    attempt += 1
                    rateLimitDelay(backoffMs)
                    continue
                }

                logUsage(
                    AIUsageLog(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        modelId = modelId,
                        taskId = taskId ?: "",
                        estimatedCost = AIUsageLog.UNKNOWN_COST,
                        taskType = taskType,
                        isSuccess = false,
                        errorMessage = e.message
                    )
                )
                recordCrashlyticsNonFatal(e, "Firebase AI Logic error on $modelName")
                return Result.failure(e)
            }
        }
    }

    private fun isDailyQuotaExhausted(error: Throwable): Boolean {
        val causes = generateSequence(error) { it.cause }.toList()
        if (causes.any { it is GeminiDailyQuotaExceededException }) return true
        if (causes.any { it is HttpException && it.code() == 429 }) return true
        return causes.any { cause ->
            val typeName = cause.javaClass.simpleName.lowercase()
            val msg = cause.message.orEmpty().lowercase()
            typeName.contains("quota") ||
                msg.contains("resource_exhausted") ||
                msg.contains("quota") ||
                msg.contains("daily limit") ||
                msg.contains("429")
        }
    }

    private fun isHttp503Overload(error: Throwable): Boolean {
        val causes = generateSequence(error) { it.cause }.toList()
        if (causes.any { it is HttpException && it.code() == 503 }) return true
        return causes.any { cause ->
            val msg = cause.message.orEmpty().lowercase()
            msg.contains("503") ||
                msg.contains("service_unavailable") ||
                msg.contains("overloaded") ||
                msg.contains("high demand") ||
                msg.contains("temporarily unavailable")
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
                    val inputTokens = result.inputTokens
                    val outputTokens = result.outputTokens
                    val estimatedCost = estimateCostOrUnknown(model, inputTokens, outputTokens)
                    logUsage(AIUsageLog(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        modelId = model.id,
                        taskId = taskId ?: "",
                        tokensInput = inputTokens ?: 0,
                        tokensOutput = outputTokens ?: 0,
                        estimatedCost = estimatedCost,
                        taskType = taskType,
                        isSuccess = true
                    ))
                    result.text
                }
                AIProviderType.GOOGLE_GEMINI -> {
                    val apiKey = provider.apiKey?.let(decryptSecret)
                        ?: throw IllegalArgumentException("API Key missing for provider ${provider.name}")
                    val generativeModel = GenerativeModel(
                        modelName = model.apiId,
                        apiKey = apiKey,
                        systemInstruction = systemInstruction?.let { com.google.ai.client.generativeai.type.content { text(it) } }
                    )
                    val response = generativeModel.generateContent(prompt)
                    val text = response.text?.takeIf { it.isNotBlank() }
                        ?: throw Exception("Gemini returned an empty response")
                    val inputTokens = response.usageMetadata?.promptTokenCount
                    val outputTokens = response.usageMetadata?.candidatesTokenCount
                    val estimatedCost = estimateCostOrUnknown(model, inputTokens, outputTokens)

                    logUsage(AIUsageLog(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        modelId = model.id,
                        taskId = taskId ?: "",
                        tokensInput = inputTokens ?: 0,
                        tokensOutput = outputTokens ?: 0,
                        estimatedCost = estimatedCost,
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
                    val inputTokens = response.usage?.prompt_tokens
                    val outputTokens = response.usage?.completion_tokens
                    val estimatedCost = estimateCostOrUnknown(model, inputTokens, outputTokens)

                    logUsage(AIUsageLog(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        modelId = model.id,
                        taskId = taskId ?: "",
                        tokensInput = inputTokens ?: 0,
                        tokensOutput = outputTokens ?: 0,
                        estimatedCost = estimatedCost,
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
                estimatedCost = if (model.isFree) 0.0 else AIUsageLog.UNKNOWN_COST,
                taskType = taskType,
                isSuccess = false,
                errorMessage = timeout.message ?: "Request timed out"
            ))
            throw timeout
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            val failureKind = e.toAIProviderOperationException().kind
            val estimatedCost = if (model.isFree) {
                0.0
            } else {
                when (failureKind) {
                    AIProviderFailureKind.MISCONFIGURED,
                    AIProviderFailureKind.INVALID_CREDENTIALS,
                    AIProviderFailureKind.ACCESS_DENIED,
                    AIProviderFailureKind.QUOTA_EXCEEDED,
                    AIProviderFailureKind.RATE_LIMITED,
                    AIProviderFailureKind.UNSUPPORTED -> 0.0
                    else -> AIUsageLog.UNKNOWN_COST
                }
            }
            logUsage(AIUsageLog(
                id = UUID.randomUUID().toString(),
                userId = userId,
                modelId = model.id,
                taskId = taskId ?: "",
                estimatedCost = estimatedCost,
                taskType = taskType,
                isSuccess = false,
                errorMessage = e.message
            ))
            throw e
        }
    }

    private fun estimateCostOrUnknown(
        model: AIModel,
        inputTokens: Int?,
        outputTokens: Int?
    ): Double {
        if (model.isFree) return 0.0
        if (
            inputTokens == null ||
            outputTokens == null ||
            (inputTokens <= 0 && outputTokens <= 0) ||
            (model.inputPricePer1M <= 0.0 && model.outputPricePer1M <= 0.0)
        ) {
            return AIUsageLog.UNKNOWN_COST
        }
        return AIModelPricing.estimateCost(model, inputTokens, outputTokens)
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

            // Prefer the provider's explicitly free router when auto-selecting free models.
            if (model.apiId == "openrouter/free") score += 25
            if (model.apiId.contains("free", ignoreCase = true)) score += 5
            
            score
        })
    }

    private companion object {
        const val TAG = "AIModelRepository"
        const val FIREBASE_AI_TIMEOUT_MS = 60_000L
    }
}
