package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AIProviderDao
import com.example.data.local.AppDatabase
import com.example.data.remote.HuggingFaceApi
import com.example.data.remote.OpenRouterApi
import com.example.domain.model.AIProvider
import com.example.domain.model.AIProviderStatus
import com.example.domain.model.AIProviderType
import com.example.domain.model.AITaskType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

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
    fun builtInFirebaseAiProviderAndFreeModelsAreAvailableByDefault() = runBlocking {
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
        assertEquals(AIProviderStatus.CONNECTED, platformProvider?.status)

        val freeModels = repo.getAllAvailableModels(userId, freeOnly = true).first()
        assertTrue(freeModels.any { it.apiId == "gemini-3.8-flash" && it.isFree })
        assertTrue(freeModels.any { it.apiId == "gemini-3.5-flash-lite" && it.isFree })

        val defaultModel = repo.getDefaultModel(userId)
        assertEquals(FirebaseAiModelCatalog.DEFAULT_MODEL_ID, defaultModel?.apiId)
        assertEquals(FirebaseAiModelCatalog.platformProviderId(userId), defaultModel?.providerId)
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
    fun generateResponseRoutesBuiltInModelThroughFirebaseAiRunnerAndLogsZeroCostUsage() = runBlocking {
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
        val modelId = FirebaseAiModelCatalog.models(FirebaseAiModelCatalog.platformProviderId(userId)).first().id
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
        assertEquals(0.0, logs.first().estimatedCost, 0.0001)
        assertTrue(logs.first().isSuccess)
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
