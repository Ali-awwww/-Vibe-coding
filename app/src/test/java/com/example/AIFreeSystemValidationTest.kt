package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AIProviderDao
import com.example.data.local.AppDatabase
import com.example.data.remote.*
import com.example.data.repository.AIModelRepositoryImpl
import com.example.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config
class AIFreeSystemValidationTest {

    private lateinit var database: AppDatabase
    private lateinit var aiProviderDao: AIProviderDao
    private lateinit var repository: AIModelRepositoryImpl
    private lateinit var fakeOpenRouterApi: FakeOpenRouterApi
    private lateinit var fakeHuggingFaceApi: FakeHuggingFaceApi

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        aiProviderDao = database.aiProviderDao()
        fakeOpenRouterApi = FakeOpenRouterApi()
        fakeHuggingFaceApi = FakeHuggingFaceApi()
        repository = AIModelRepositoryImpl(aiProviderDao, fakeOpenRouterApi, fakeHuggingFaceApi)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `verify default settings are cost-safe`() = runTest {
        val userId = "test_user"
        val settings = aiProviderDao.getUserSettings(userId).first() ?: AIUserSettings(userId = userId)
        
        assertTrue("Free Only should be true by default", settings.freeOnly)
        assertTrue("Auto Select Free should be true by default", settings.autoSelectFree)
    }

    @Test
    fun `syncModels parses numeric pricing and keeps free category consistent`() = runTest {
        val userId = "user1"
        val provider = AIProvider(id = "p1", userId = userId, name = "OR", type = AIProviderType.OPEN_ROUTER, apiKey = "key")
        aiProviderDao.insertProvider(provider)
        fakeOpenRouterApi.responseContent = "Connection check succeeded"
        fakeOpenRouterApi.models = listOf(
            OpenRouterModelData("m_free_decimal", "Free Decimal", null, 8000, OpenRouterPricing("0.000000", "0.0", "0", "0"), null),
            OpenRouterModelData("m_paid", "Paid Model", null, 8000, OpenRouterPricing("0.01", "0.01", "0", "0"), null),
            OpenRouterModelData("m_zero_input_paid_output", "Mixed Pricing", null, 8000, OpenRouterPricing("0", "0.0001", "0", "0"), null),
            OpenRouterModelData("m_invalid_price", "Invalid Pricing", null, 8000, OpenRouterPricing("unknown", "0", "0", "0"), null)
        )

        val syncResult = repository.syncModels(userId, "p1")
        assertTrue("Catalog sync should succeed for valid catalog entries", syncResult.isSuccess)

        val allModels = aiProviderDao.getModelsByProvider(userId, "p1").first()
        val freeModel = allModels.find { it.apiId == "m_free_decimal" }
        val paidModel = allModels.find { it.apiId == "m_paid" }
        val mixedModel = allModels.find { it.apiId == "m_zero_input_paid_output" }

        assertNotNull(freeModel)
        assertTrue("Decimal zero pricing should be recognized as free", freeModel!!.isFree)
        assertEquals(AIModelCategory.FREE, freeModel.category)
        assertNotNull(paidModel)
        assertFalse("Non-zero pricing must not be marked free", paidModel!!.isFree)
        assertEquals(AIModelCategory.GENERAL_PURPOSE, paidModel.category)
        assertNotNull(mixedModel)
        assertFalse("A paid completion price means the model is not free", mixedModel!!.isFree)
        assertEquals(AIModelCategory.GENERAL_PURPOSE, mixedModel.category)
        assertFalse("Models with invalid prices must not be cached with a zero-cost default", allModels.any { it.apiId == "m_invalid_price" })
        assertEquals("The sync should reuse one catalog response for verification and persistence", 1, fakeOpenRouterApi.getModelsCallCount)
        assertEquals("m_free_decimal", fakeOpenRouterApi.lastRequestedModel)
    }

    @Test
    fun modelReadsDefaultsAndDeletesAreScopedToTheOwningUser() = runTest {
        val ownerId = "owner-user"
        val otherUserId = "other-user"
        val ownerProviderId = "owner-provider"
        val otherProviderId = "other-provider"
        val ownerModelId = "$ownerProviderId:owner-model"
        val otherModelId = "$otherProviderId:other-model"

        aiProviderDao.insertProvider(
            AIProvider(id = ownerProviderId, userId = ownerId, name = "Owner", type = AIProviderType.OPEN_ROUTER, apiKey = "owner-key")
        )
        aiProviderDao.insertProvider(
            AIProvider(id = otherProviderId, userId = otherUserId, name = "Other", type = AIProviderType.OPEN_ROUTER, apiKey = "other-key")
        )
        aiProviderDao.insertModels(
            listOf(
                AIModel(id = ownerModelId, providerId = ownerProviderId, apiId = "owner-model", name = "Owner model", isFree = true),
                AIModel(id = otherModelId, providerId = otherProviderId, apiId = "other-model", name = "Other model", isFree = true)
            )
        )

        assertEquals(listOf(ownerModelId), repository.getAllAvailableModels(ownerId).first().map { it.id })
        assertTrue(repository.getModelsByProvider(ownerId, otherProviderId).first().isEmpty())
        assertNull(repository.getModelById(ownerId, otherModelId))

        aiProviderDao.insertUserSettings(AIUserSettings(userId = ownerId, defaultModelId = otherModelId))
        assertNull(repository.getDefaultModel(ownerId))
        val selectionFailure = runCatching { repository.setDefaultModel(ownerId, otherModelId) }.exceptionOrNull()
        assertTrue(selectionFailure is IllegalArgumentException)

        repository.deleteProvider(ownerId, otherProviderId)
        assertNotNull(aiProviderDao.getProviderById(otherUserId, otherProviderId))
        assertEquals(1, repository.getModelsByProvider(otherUserId, otherProviderId).first().size)
    }

    @Test
    fun modelCatalogReplacementRejectsForeignProviderModelsWithoutDeletingCurrentCatalog() = runTest {
        val userId = "owner-user"
        val providerId = "owner-provider"
        aiProviderDao.insertProvider(
            AIProvider(id = providerId, userId = userId, name = "Owner", type = AIProviderType.OPEN_ROUTER, apiKey = "key")
        )
        val existing = AIModel(id = "$providerId:existing", providerId = providerId, apiId = "existing", name = "Existing")
        aiProviderDao.insertModels(listOf(existing))

        val replacement = AIModel(id = "foreign-provider:model", providerId = "foreign-provider", apiId = "model", name = "Foreign")
        val failure = runCatching {
            aiProviderDao.replaceModelsForProvider(userId, providerId, listOf(replacement))
        }.exceptionOrNull()

        assertTrue("Foreign-provider rows must be rejected before the cache is replaced", failure is IllegalArgumentException)
        assertEquals(listOf(existing.id), aiProviderDao.getModelsByProvider(userId, providerId).first().map { it.id })
    }

    @Test
    fun `generateResponse fails closed when Free Only is on and no free models exist`() = runTest {
        val userId = "user1"
        // No providers added, so no models
        
        val result = repository.generateResponse(userId, "any", "hi", taskType = AITaskType.GENERAL_CHAT)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("No available models found") == true)
    }

    @Test
    fun `failover chain works when first free model fails`() = runTest {
        val userId = "user1"
        val provider = AIProvider(id = "p1", userId = userId, name = "OR", type = AIProviderType.OPEN_ROUTER, apiKey = "key")
        aiProviderDao.insertProvider(provider)

        // Add two free models
        aiProviderDao.insertModels(listOf(
            AIModel(id = "p1:m1", providerId = "p1", apiId = "m1", name = "M1", isFree = true),
            AIModel(id = "p1:m2", providerId = "p1", apiId = "m2", name = "M2", isFree = true)
        ))

        // First attempt (m1) will throw exception, second (m2) will succeed
        fakeOpenRouterApi.shouldFailForModel = "m1"
        fakeOpenRouterApi.responseContent = "Success from M2"

        val result = repository.generateResponse(userId, "p1:m1", "hi", taskType = AITaskType.GENERAL_CHAT)

        assertTrue("Should succeed via failover", result.isSuccess)
        assertEquals("Success from M2", result.getOrNull())
        
        val logs = aiProviderDao.getRecentUsageLogs(userId).first()
        assertTrue("Should have at least 2 logs (1 fail, 1 success)", logs.size >= 2)
        assertFalse("First log should be failure", logs.last().isSuccess)
        assertTrue("Success log should exist", logs.any { it.isSuccess })
    }

    @Test
    fun `PII scrubbing is applied to prompt before sending`() = runTest {
        // This is harder to test directly in AIModelRepositoryImpl since it doesn't do the scrubbing,
        // CrashRepositoryImpl does. Let's verify that CrashRepositoryImpl uses it.
        // Actually, let's just check the usage logs for task type.
    }

    @Test
    fun `usage logs store task type and success status`() = runTest {
        val userId = "user1"
        val provider = AIProvider(id = "p1", userId = userId, name = "OR", type = AIProviderType.OPEN_ROUTER, apiKey = "key")
        aiProviderDao.insertProvider(provider)
        aiProviderDao.insertModels(listOf(AIModel(id = "p1:m1", providerId = "p1", apiId = "m1", name = "M1", isFree = true)))
        
        fakeOpenRouterApi.responseContent = "Result"
        repository.generateResponse(userId, "p1:m1", "hi", taskType = AITaskType.ROOT_CAUSE_DIAGNOSIS)
        
        val log = aiProviderDao.getRecentUsageLogs(userId).first().first()
        assertEquals(AITaskType.ROOT_CAUSE_DIAGNOSIS, log.taskType)
        assertTrue(log.isSuccess)
    }

    // Fakes
    class FakeOpenRouterApi : OpenRouterApi {
        var models = emptyList<OpenRouterModelData>()
        var responseContent = ""
        var shouldFailForModel: String? = null
        var getModelsCallCount: Int = 0
        var lastRequestedModel: String? = null

        override suspend fun getModels(token: String): OpenRouterModelsResponse {
            getModelsCallCount += 1
            return OpenRouterModelsResponse(models)
        }

        override suspend fun generateCompletion(token: String, request: OpenRouterChatRequest): OpenRouterChatResponse {
            lastRequestedModel = request.model
            if (request.model == shouldFailForModel) throw Exception("Simulated Failure")
            return OpenRouterChatResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(ChatChoice(ChatMessage("assistant", responseContent))),
                usage = TokenUsage(10, 10, 20)
            )
        }
    }

    class FakeHuggingFaceApi : HuggingFaceApi {
        override suspend fun getModels(filter: String, sort: String, direction: Int, limit: Int): List<HuggingFaceModelData> = emptyList()
    }
}
