package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AIProviderDao
import com.example.data.local.AppDatabase
import com.example.data.remote.HuggingFaceApi
import com.example.data.remote.OpenRouterModelData
import com.example.data.remote.OpenRouterModelsResponse
import com.example.data.remote.OpenRouterPricing
import com.example.data.remote.OpenRouterApi
import com.example.data.remote.ChatChoice
import com.example.data.remote.ChatMessage
import com.example.data.remote.OpenRouterChatResponse
import com.example.data.remote.TokenUsage
import com.example.domain.model.AIProvider
import com.example.domain.model.AIModel
import com.example.domain.model.AIUserSettings
import com.example.domain.model.AIProviderStatus
import com.example.domain.model.AIProviderFailureKind
import com.example.domain.model.AIProviderOperationException
import com.example.domain.model.AIProviderType
import com.example.domain.model.AITaskType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config
class AIModelRepositoryFirebaseAiTest {

    private lateinit var database: AppDatabase
    private lateinit var aiProviderDao: AIProviderDao
    private lateinit var openRouterApi: OpenRouterApi
    private lateinit var huggingFaceApi: HuggingFaceApi

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        aiProviderDao = database.aiProviderDao()
        openRouterApi = noopProxy()
        huggingFaceApi = noopProxy()
    }

    @After
    fun tearDown() {
        database.close()
    }


    @Test
    fun openRouterSyncMarksConnectedOnlyAfterLiveCompletionProbe() = runBlocking {
        val userId = "openrouter-probe-success"
        val providerId = "openrouter-probe-provider"
        val modelData = OpenRouterModelData(
            id = "probe-model",
            name = "Probe model",
            description = null,
            context_length = 8192,
            pricing = OpenRouterPricing(prompt = "0", completion = "0", request = "0", image = "0"),
            architecture = null
        )
        var probeCount = 0
        val probeApi = Proxy.newProxyInstance(
            OpenRouterApi::class.java.classLoader,
            arrayOf(OpenRouterApi::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getModels" -> OpenRouterModelsResponse(listOf(modelData))
                "generateCompletion" -> {
                    probeCount += 1
                    OpenRouterChatResponse(
                        id = "probe-success",
                        choices = listOf(ChatChoice(ChatMessage("assistant", "pong"))),
                        usage = null
                    )
                }
                else -> throw UnsupportedOperationException("Not used by this test.")
            }
        } as OpenRouterApi
        val repo = AIModelRepositoryImpl(
            aiProviderDao = aiProviderDao,
            openRouterApi = probeApi,
            huggingFaceApi = huggingFaceApi,
            encryptSecret = { it },
            decryptSecret = { it },
            nowMillis = { 4321L }
        )
        repo.addProvider(
            AIProvider(
                id = providerId,
                userId = userId,
                name = "Probe provider",
                type = AIProviderType.OPEN_ROUTER,
                apiKey = "test-key"
            )
        )

        assertEquals(AIProviderStatus.DISCONNECTED, repo.getProviderById(userId, providerId)?.status)
        val result = repo.syncModels(userId, providerId)

        assertTrue(result.isSuccess)
        assertEquals(1, probeCount)
        val provider = repo.getProviderById(userId, providerId)
        assertEquals(AIProviderStatus.CONNECTED, provider?.status)
        assertEquals(4321L, provider?.lastSyncAt)
        assertTrue(repo.getAllAvailableModels(userId, freeOnly = true).first().any { it.providerId == providerId })
    }

    @Test
    fun failedOpenRouterLiveProbeLeavesProviderDisconnected() = runBlocking {
        val userId = "openrouter-probe-failure"
        val providerId = "openrouter-failed-provider"
        val modelData = OpenRouterModelData(
            id = "probe-model",
            name = "Probe model",
            description = null,
            context_length = 8192,
            pricing = OpenRouterPricing(prompt = "0", completion = "0", request = "0", image = "0"),
            architecture = null
        )
        val failingApi = Proxy.newProxyInstance(
            OpenRouterApi::class.java.classLoader,
            arrayOf(OpenRouterApi::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getModels" -> OpenRouterModelsResponse(listOf(modelData))
                "generateCompletion" -> throw IOException("private probe diagnostic")
                else -> throw UnsupportedOperationException("Not used by this test.")
            }
        } as OpenRouterApi
        val repo = AIModelRepositoryImpl(
            aiProviderDao = aiProviderDao,
            openRouterApi = failingApi,
            huggingFaceApi = huggingFaceApi,
            encryptSecret = { it },
            decryptSecret = { it }
        )
        repo.addProvider(
            AIProvider(
                id = providerId,
                userId = userId,
                name = "Failing provider",
                type = AIProviderType.OPEN_ROUTER,
                apiKey = "test-key"
            )
        )

        val result = repo.syncModels(userId, providerId)

        assertTrue(result.isFailure)
        val provider = repo.getProviderById(userId, providerId)
        assertEquals(AIProviderStatus.DISCONNECTED, provider?.status)
        assertNull(provider?.lastSyncAt)
        assertTrue(repo.getAllAvailableModels(userId, freeOnly = false).first().isEmpty())
    }

    @Test
    fun openRouterRetriesOne429AfterRetryAfterCooldownAndPersistsSuccess() = runBlocking {
        val userId = "rate-limit-user"
        val providerId = "rate-limit-openrouter"
        val modelId = "$providerId:test-model"
        var requestCount = 0
        val observedDelays = mutableListOf<Long>()
        val retryingApi = Proxy.newProxyInstance(
            OpenRouterApi::class.java.classLoader,
            arrayOf(OpenRouterApi::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "generateCompletion" -> {
                    requestCount += 1
                    if (requestCount == 1) throw rateLimitException("2")
                    OpenRouterChatResponse(
                        id = "retry-success",
                        choices = listOf(ChatChoice(ChatMessage("assistant", "retried successfully"))),
                        usage = TokenUsage(prompt_tokens = 4, completion_tokens = 3, total_tokens = 7)
                    )
                }
                else -> throw UnsupportedOperationException("Not used by this test.")
            }
        } as OpenRouterApi

        val repo = AIModelRepositoryImpl(
            aiProviderDao = aiProviderDao,
            openRouterApi = retryingApi,
            huggingFaceApi = huggingFaceApi,
            encryptSecret = { it },
            decryptSecret = { it },
            rateLimitRetryPolicy = RateLimitRetryPolicy(
                maxRetries = 1,
                defaultDelayMs = 500L,
                maxDelayMs = 5_000L
            ),
            rateLimitDelay = { observedDelays += it },
            nowMillis = { 1_000L }
        )
        repo.addProvider(
            AIProvider(
                id = providerId,
                userId = userId,
                name = "Rate limit fixture",
                type = AIProviderType.OPEN_ROUTER,
                apiKey = "test-key"
            )
        )
        aiProviderDao.insertModels(
            listOf(
                AIModel(
                    id = modelId,
                    providerId = providerId,
                    apiId = "test-model",
                    name = "Test model",
                    isFree = false,
                    inputPricePer1M = 1.0,
                    outputPricePer1M = 1.0
                )
            )
        )
        aiProviderDao.insertUserSettings(
            AIUserSettings(
                userId = userId,
                defaultModelId = modelId,
                freeOnly = false,
                autoSelectFree = false,
                usageLimitMonthly = 100.0
            )
        )
        repo.updateProviderStatus(userId, providerId, AIProviderStatus.CONNECTED, 1_000L)

        val result = repo.generateResponse(
            userId = userId,
            modelId = modelId,
            prompt = "test rate-limit retry",
            systemInstruction = null,
            taskId = "rate-limit-test",
            taskType = AITaskType.GENERAL_CHAT
        )

        assertTrue(result.isSuccess)
        assertEquals("retried successfully", result.getOrNull())
        assertEquals(2, requestCount)
        assertEquals(listOf(2_000L), observedDelays)
        val usage = repo.getRecentUsageLogs(userId).first()
        assertEquals(1, usage.size)
        assertTrue(usage.single().isSuccess)
        assertEquals(7, usage.single().tokensInput + usage.single().tokensOutput)
    }

    @Test
    fun openRouterRateLimitRetriesStopAtTheConfiguredAttemptLimit() = runBlocking {
        val userId = "rate-limit-bounded-user"
        val providerId = "rate-limit-bounded-openrouter"
        val modelId = "$providerId:test-model"
        var requestCount = 0
        val observedDelays = mutableListOf<Long>()
        val alwaysLimitedApi = Proxy.newProxyInstance(
            OpenRouterApi::class.java.classLoader,
            arrayOf(OpenRouterApi::class.java)
        ) { _, method, _ ->
            if (method.name == "generateCompletion") {
                requestCount += 1
                throw rateLimitException("3")
            }
            throw UnsupportedOperationException("Not used by this test.")
        } as OpenRouterApi
        val repo = AIModelRepositoryImpl(
            aiProviderDao = aiProviderDao,
            openRouterApi = alwaysLimitedApi,
            huggingFaceApi = huggingFaceApi,
            encryptSecret = { it },
            decryptSecret = { it },
            rateLimitRetryPolicy = RateLimitRetryPolicy(maxRetries = 1),
            rateLimitDelay = { observedDelays += it }
        )
        repo.addProvider(
            AIProvider(
                id = providerId,
                userId = userId,
                name = "Bounded rate limit fixture",
                type = AIProviderType.OPEN_ROUTER,
                apiKey = "test-key"
            )
        )
        aiProviderDao.insertModels(
            listOf(AIModel(id = modelId, providerId = providerId, apiId = "test-model", name = "Test model"))
        )
        aiProviderDao.insertUserSettings(
            AIUserSettings(
                userId = userId,
                defaultModelId = modelId,
                freeOnly = false,
                autoSelectFree = false,
                usageLimitMonthly = 100.0
            )
        )
        aiProviderDao.updateProviderStatus(userId, providerId, AIProviderStatus.CONNECTED, 1_000L)

        val result = repo.generateResponse(
            userId = userId,
            modelId = modelId,
            prompt = "bounded retry test",
            systemInstruction = null,
            taskId = "rate-limit-bounded-test",
            taskType = AITaskType.GENERAL_CHAT
        )

        assertTrue(result.isFailure)
        val failure = result.exceptionOrNull()
        assertTrue(failure is AIProviderOperationException)
        assertEquals(AIProviderFailureKind.RATE_LIMITED, (failure as AIProviderOperationException).kind)
        assertEquals(2, requestCount)
        assertEquals(listOf(3_000L), observedDelays)
        val usage = repo.getRecentUsageLogs(userId).first()
        assertEquals(1, usage.size)
        assertTrue(!usage.single().isSuccess)
    }

    private fun rateLimitException(retryAfter: String?): HttpException {
        val rawResponse = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://openrouter.ai/api/v1/chat/completions").build())
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .apply { retryAfter?.let { header("Retry-After", it) } }
            .build()
        val body = "rate limited".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<com.example.data.remote.OpenRouterChatResponse>(body, rawResponse))
    }

    @Test
    fun builtInFirebaseAiCatalogIsVisibleButNotSelectableUntilVerified() = runBlocking {
        val repo = AIModelRepositoryImpl(
            aiProviderDao = aiProviderDao,
            openRouterApi = openRouterApi,
            huggingFaceApi = huggingFaceApi,
            enablePlatformFirebaseAi = true
        )

        val userId = "firebase-user-1"
        val providers = repo.getProviders(userId).first()
        val platformProvider = providers.firstOrNull { it.type == AIProviderType.FIREBASE_AI_LOGIC }
        assertNotNull(platformProvider)
        assertEquals(AIProviderStatus.DISCONNECTED, platformProvider?.status)

        val providerId = FirebaseAiModelCatalog.platformProviderId(userId)
        val catalogModels = repo.getModelsByProvider(userId, providerId).first()
        assertTrue(catalogModels.any {
            it.apiId == "gemini-3.8-flash" && !it.isFree &&
                it.inputPricePer1M > 0.0 && it.outputPricePer1M > 0.0
        })
        assertTrue(catalogModels.any {
            it.apiId == "gemini-3.5-flash-lite" && !it.isFree &&
                it.inputPricePer1M > 0.0 && it.outputPricePer1M > 0.0
        })
        assertTrue(repo.getAllAvailableModels(userId, freeOnly = true).first().isEmpty())
        assertNull(repo.getDefaultModel(userId))

        val defaultModelError = runCatching {
            repo.setDefaultModel(userId, catalogModels.first().id)
        }.exceptionOrNull()
        assertTrue(defaultModelError is IllegalArgumentException)
    }


    @Test
    fun builtInFirebaseAiProviderBecomesConnectedOnlyAfterSuccessfulLiveProbe() = runBlocking {
        var probeCount = 0
        val repo = AIModelRepositoryImpl(
            aiProviderDao = aiProviderDao,
            openRouterApi = openRouterApi,
            huggingFaceApi = huggingFaceApi,
            enablePlatformFirebaseAi = true,
            firebaseAiRunner = { _, _, _ ->
                probeCount += 1
                FirebaseAiGenerationOutput("pong")
            },
            nowMillis = { 1234L }
        )
        val userId = "firebase-probe-success"
        val providerId = FirebaseAiModelCatalog.platformProviderId(userId)

        val before = repo.getProviders(userId).first().single { it.id == providerId }
        assertEquals(AIProviderStatus.DISCONNECTED, before.status)
        assertEquals(null, before.lastSyncAt)

        val result = repo.syncModels(userId, providerId)
        assertTrue(result.isSuccess)
        assertEquals(1, probeCount)

        val after = repo.getProviders(userId).first().single { it.id == providerId }
        assertEquals(AIProviderStatus.CONNECTED, after.status)
        assertEquals(1234L, after.lastSyncAt)
        assertTrue(repo.getAllAvailableModels(userId, freeOnly = false).first().any { it.providerId == providerId })
        assertTrue(repo.getAllAvailableModels(userId, freeOnly = true).first().none { it.providerId == providerId })
        assertNull(repo.getDefaultModel(userId))

        repo.updateProviderStatus(userId, providerId, AIProviderStatus.ERROR, 5678L)
        val failed = repo.getProviderById(userId, providerId)
        assertEquals(AIProviderStatus.ERROR, failed?.status)
        assertEquals(5678L, failed?.lastSyncAt)
    }

    @Test
    fun failedBuiltInFirebaseAiProbeNeverMarksProviderConnected() = runBlocking {
        val repo = AIModelRepositoryImpl(
            aiProviderDao = aiProviderDao,
            openRouterApi = openRouterApi,
            huggingFaceApi = huggingFaceApi,
            enablePlatformFirebaseAi = true,
            firebaseAiRunner = { _, _, _ -> throw IOException("private provider diagnostic") }
        )
        val userId = "firebase-probe-failure"
        val providerId = FirebaseAiModelCatalog.platformProviderId(userId)

        val result = repo.syncModels(userId, providerId)

        assertTrue(result.isFailure)
        val provider = repo.getProviders(userId).first().single { it.id == providerId }
        assertEquals(AIProviderStatus.DISCONNECTED, provider.status)
        assertEquals(null, provider.lastSyncAt)
        assertTrue(repo.getAllAvailableModels(userId, freeOnly = true).first().isEmpty())
        assertNull(repo.getDefaultModel(userId))
    }


    @Test
    fun addProviderCannotForgeConnectedStatusAndDirectConnectedUpdateRequiresProbe() = runBlocking {
        val repo = AIModelRepositoryImpl(
            aiProviderDao = aiProviderDao,
            openRouterApi = openRouterApi,
            huggingFaceApi = huggingFaceApi,
            encryptSecret = { it },
            decryptSecret = { it }
        )
        val userId = "forged-status-user"
        val providerId = "forged-status-provider"
        repo.addProvider(
            AIProvider(
                id = providerId,
                userId = userId,
                name = "Unverified",
                type = AIProviderType.OPEN_ROUTER,
                apiKey = "test-key",
                status = AIProviderStatus.CONNECTED,
                lastSyncAt = 999L
            )
        )

        val stored = repo.getProviderById(userId, providerId)
        assertEquals(AIProviderStatus.DISCONNECTED, stored?.status)
        assertNull(stored?.lastSyncAt)

        val error = runCatching {
            repo.updateProviderStatus(userId, providerId, AIProviderStatus.CONNECTED, 1_000L)
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals(AIProviderStatus.DISCONNECTED, repo.getProviderById(userId, providerId)?.status)
    }

    @Test
    fun builtInPlatformProviderRejectsManualAddAndDeletion() = runBlocking {
        val repo = AIModelRepositoryImpl(
            aiProviderDao = aiProviderDao,
            openRouterApi = openRouterApi,
            huggingFaceApi = huggingFaceApi,
            enablePlatformFirebaseAi = true
        )
        val userId = "firebase-user-2"

        val addError = runCatching {
            repo.addProvider(
                AIProvider(
                    id = "custom-fb",
                    userId = userId,
                    name = "Duplicate",
                    type = AIProviderType.FIREBASE_AI_LOGIC
                )
            )
        }.exceptionOrNull()
        assertTrue(addError is UnsupportedOperationException)

        val deleteError = runCatching {
            repo.deleteProvider(userId, FirebaseAiModelCatalog.platformProviderId(userId))
        }.exceptionOrNull()
        assertTrue(deleteError is UnsupportedOperationException)
    }

    @Test
    fun generateResponseRoutesBuiltInModelAndEstimatesPaidTierUsage() = runBlocking {
        var capturedModelName: String? = null
        var capturedSystemInstruction: String? = null
        val repo = AIModelRepositoryImpl(
            aiProviderDao = aiProviderDao,
            openRouterApi = openRouterApi,
            huggingFaceApi = huggingFaceApi,
            enablePlatformFirebaseAi = true,
            firebaseAiRunner = { modelName, _, systemInstruction ->
                capturedModelName = modelName
                capturedSystemInstruction = systemInstruction
                FirebaseAiGenerationOutput(
                    text = """{"summary":"تم الإصلاح"}""",
                    inputTokens = 140,
                    outputTokens = 35
                )
            }
        )

        val userId = "firebase-user-3"
        val providerId = FirebaseAiModelCatalog.platformProviderId(userId)
        val model = FirebaseAiModelCatalog.models(providerId).first()
        val modelId = model.id
        assertTrue(repo.syncModels(userId, providerId).isSuccess)
        aiProviderDao.insertUserSettings(
            AIUserSettings(
                userId = userId,
                defaultModelId = modelId,
                freeOnly = false,
                autoSelectFree = false,
                usageLimitMonthly = 100.0
            )
        )
        val result = repo.generateResponse(
            userId = userId,
            modelId = modelId,
            prompt = "Diagnose crash",
            systemInstruction = "Crash Expert",
            taskId = "crash-101",
            taskType = AITaskType.ROOT_CAUSE_DIAGNOSIS
        )

        assertTrue(result.isSuccess)
        assertEquals("""{"summary":"تم الإصلاح"}""", result.getOrNull())
        assertEquals("gemini-3.8-flash", capturedModelName)
        assertEquals("Crash Expert", capturedSystemInstruction)

        val logs = repo.getRecentUsageLogs(userId).first()
        assertEquals(1, logs.size)
        assertEquals(140, logs.first().tokensInput)
        assertEquals(35, logs.first().tokensOutput)
        assertEquals(AIModelPricing.estimateCost(model, 140, 35), logs.first().estimatedCost, 0.0000001)
        assertTrue(logs.first().isSuccess)
    }

    @Test
    fun missingFirebaseUsageMetadataIsRecordedAsUnknownAndBlocksFurtherPaidInference() = runBlocking {
        var generationCount = 0
        val repo = AIModelRepositoryImpl(
            aiProviderDao = aiProviderDao,
            openRouterApi = openRouterApi,
            huggingFaceApi = huggingFaceApi,
            enablePlatformFirebaseAi = true,
            firebaseAiRunner = { _, prompt, _ ->
                if (prompt == "ping") {
                    FirebaseAiGenerationOutput("pong")
                } else {
                    generationCount += 1
                    FirebaseAiGenerationOutput("response without usage metadata")
                }
            }
        )
        val userId = "firebase-unpriced-user"
        val providerId = FirebaseAiModelCatalog.platformProviderId(userId)
        val model = FirebaseAiModelCatalog.models(providerId).first()
        assertTrue(repo.syncModels(userId, providerId).isSuccess)
        aiProviderDao.insertUserSettings(
            AIUserSettings(
                userId = userId,
                defaultModelId = model.id,
                freeOnly = false,
                autoSelectFree = false,
                usageLimitMonthly = 100.0
            )
        )

        val first = repo.generateResponse(
            userId = userId,
            modelId = model.id,
            prompt = "first paid request",
            systemInstruction = null,
            taskId = "unpriced-first",
            taskType = AITaskType.GENERAL_CHAT
        )
        assertTrue(first.isSuccess)
        assertEquals(1, generationCount)

        val logs = repo.getRecentUsageLogs(userId).first()
        assertEquals(1, logs.size)
        assertEquals(AIUsageLog.UNKNOWN_COST, logs.single().estimatedCost, 0.0)
        assertEquals(0.0, repo.getTotalCost(userId, 0L).first() ?: 0.0, 0.0001)
        assertTrue(repo.hasUnpricedUsageSince(userId, 0L).first())

        val second = repo.generateResponse(
            userId = userId,
            modelId = model.id,
            prompt = "second paid request",
            systemInstruction = null,
            taskId = "unpriced-second",
            taskType = AITaskType.GENERAL_CHAT
        )
        assertTrue(second.isFailure)
        assertTrue(second.exceptionOrNull()?.message.orEmpty().contains("تكلفة غير مؤكدة"))
        assertEquals("Unpriced usage must stop another paid request before it reaches the provider", 1, generationCount)
    }

    @Test
    fun byokProvidersCoexistWithBuiltInFirebaseAi() = runBlocking {
        val repo = AIModelRepositoryImpl(
            aiProviderDao = aiProviderDao,
            openRouterApi = openRouterApi,
            huggingFaceApi = huggingFaceApi,
            enablePlatformFirebaseAi = true
        )
        val userId = "firebase-user-4"
        repo.addProvider(
            AIProvider(
                id = "byok-openrouter",
                userId = userId,
                name = "My OpenRouter",
                type = AIProviderType.OPEN_ROUTER,
                status = AIProviderStatus.CONNECTED,
                apiKey = "sk-or-test"
            )
        )

        val providers = repo.getProviders(userId).first()
        assertTrue(providers.any { it.type == AIProviderType.FIREBASE_AI_LOGIC })
        assertTrue(providers.any { it.type == AIProviderType.OPEN_ROUTER && it.id == "byok-openrouter" })
    }

    private inline fun <reified T> noopProxy(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ ->
            throw UnsupportedOperationException("Noop proxy")
        } as T
}
