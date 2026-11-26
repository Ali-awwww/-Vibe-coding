package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.remote.HuggingFaceApi
import com.example.data.remote.HuggingFaceModelData
import com.example.data.remote.OpenRouterApi
import com.example.data.remote.OpenRouterChatRequest
import com.example.data.remote.OpenRouterChatResponse
import com.example.data.remote.OpenRouterModelsResponse
import com.example.domain.model.AITaskType
import com.example.domain.repository.GeminiDailyQuotaExceededException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config
class AIModelRepositoryImplFirebaseChainTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `primary gemini-3_8-flash succeeds without falling back`() = runTest {
        val calledModels = mutableListOf<String>()
        val repo = createRepository { modelName, _, _ ->
            calledModels += modelName
            FirebaseAiGenerationOutput(text = "Root cause fixed", inputTokens = 12, outputTokens = 8)
        }

        val result = repo.generateWithFirebaseAiChain(
            userId = "user-1",
            prompt = "Analyze NPE",
            taskType = AITaskType.ROOT_CAUSE_DIAGNOSIS
        )

        assertTrue(result.isSuccess)
        assertEquals(GeminiModelCatalog.CONNECTION_MODEL_ID, result.getOrThrow().first)
        assertEquals("Root cause fixed", result.getOrThrow().second)
        assertEquals(listOf(GeminiModelCatalog.CONNECTION_MODEL_ID), calledModels)
    }

    @Test
    fun `http 503 overload retries primary then falls back to gemini-3_5-flash-lite`() = runTest {
        val calledModels = mutableListOf<String>()
        val repo = createRepository { modelName, _, _ ->
            calledModels += modelName
            if (modelName == GeminiModelCatalog.CONNECTION_MODEL_ID) {
                throw IllegalStateException("HTTP 503 Service Unavailable")
            }
            FirebaseAiGenerationOutput(text = "Lite response", inputTokens = 10, outputTokens = 6)
        }

        val result = repo.generateWithFirebaseAiChain(
            userId = "user-1",
            prompt = "Suggest fix",
            taskType = AITaskType.CODE_FIX_SUGGESTION
        )

        assertTrue(result.isSuccess)
        assertEquals(FirebaseAiModelCatalog.LITE_MODEL_ID, result.getOrThrow().first)
        assertEquals("Lite response", result.getOrThrow().second)
        assertEquals(
            listOf(
                GeminiModelCatalog.CONNECTION_MODEL_ID,
                GeminiModelCatalog.CONNECTION_MODEL_ID,
                GeminiModelCatalog.CONNECTION_MODEL_ID,
                FirebaseAiModelCatalog.LITE_MODEL_ID
            ),
            calledModels
        )
    }

    @Test
    fun `daily quota exhaustion aborts immediately without retries or fallback`() = runTest {
        val calledModels = mutableListOf<String>()
        val repo = createRepository { modelName, _, _ ->
            calledModels += modelName
            throw IllegalStateException("429 RESOURCE_EXHAUSTED: Quota exceeded for quota metric")
        }

        val result = repo.generateWithFirebaseAiChain(
            userId = "user-1",
            prompt = "Suggest fix",
            taskType = AITaskType.CODE_FIX_SUGGESTION
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GeminiDailyQuotaExceededException)
        assertEquals(listOf(GeminiModelCatalog.CONNECTION_MODEL_ID), calledModels)
    }

    private fun createRepository(
        runner: suspend (modelName: String, prompt: String, systemInstruction: String?) -> FirebaseAiGenerationOutput
    ): AIModelRepositoryImpl {
        val openRouterApi = object : OpenRouterApi {
            override suspend fun getModels(token: String) = OpenRouterModelsResponse(emptyList())
            override suspend fun generateCompletion(token: String, request: OpenRouterChatRequest) =
                OpenRouterChatResponse("id", emptyList(), null)
        }
        val huggingFaceApi = object : HuggingFaceApi {
            override suspend fun getModels(
                filter: String,
                sort: String,
                direction: Int,
                limit: Int
            ): List<HuggingFaceModelData> = emptyList()
        }
        return AIModelRepositoryImpl(
            aiProviderDao = database.aiProviderDao(),
            openRouterApi = openRouterApi,
            huggingFaceApi = huggingFaceApi,
            encryptSecret = { it },
            decryptSecret = { it },
            enablePlatformFirebaseAi = true,
            firebaseAiRunner = runner,
            rateLimitDelay = { /* no-op in unit tests */ }
        )
    }
}
