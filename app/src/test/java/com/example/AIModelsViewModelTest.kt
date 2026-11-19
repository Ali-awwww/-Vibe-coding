package com.example

import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.PasswordRecoveryCredentials
import com.example.presentation.viewmodel.AIModelsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AIModelsViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(mainDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun signOutClearsPreviouslyLoadedProviderAndModelData() = runTest(mainDispatcher) {
        val users = MutableStateFlow<UserAccount?>(UserAccount(id = "user-a"))
        val provider = AIProvider(id = "provider-a", userId = "user-a", name = "Account A", status = AIProviderStatus.CONNECTED)
        val model = AIModel(id = "model-a", providerId = "provider-a", name = "Model A", isFree = true)
        val repository = FakeAIModelRepository().apply {
            providersByUser["user-a"] = listOf(provider)
            modelsByUser["user-a"] = listOf(model)
        }
        val viewModel = AIModelsViewModel(repository, FakeAuthRepository(users))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isAuthenticated)
        assertEquals(listOf(provider), viewModel.uiState.value.providers)
        assertEquals(listOf(model), viewModel.uiState.value.models)

        users.value = null
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertTrue(viewModel.uiState.value.providers.isEmpty())
        assertTrue(viewModel.uiState.value.models.isEmpty())
        assertNull(viewModel.uiState.value.defaultModelId)
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.addProvider("OpenRouter", AIProviderType.OPEN_ROUTER, "sk-test")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.error.orEmpty().contains("تسجيل الدخول"))
    }

    @Test
    fun switchingAccountsReplacesPreviousAccountData() = runTest(mainDispatcher) {
        val users = MutableStateFlow<UserAccount?>(UserAccount(id = "user-a"))
        val providerA = AIProvider(id = "provider-a", userId = "user-a", name = "Account A", status = AIProviderStatus.CONNECTED)
        val providerB = AIProvider(id = "provider-b", userId = "user-b", name = "Account B", status = AIProviderStatus.CONNECTED)
        val modelA = AIModel(id = "model-a", providerId = "provider-a", name = "Model A", isFree = true)
        val modelB = AIModel(id = "model-b", providerId = "provider-b", name = "Model B", isFree = true)
        val repository = FakeAIModelRepository().apply {
            providersByUser["user-a"] = listOf(providerA)
            providersByUser["user-b"] = listOf(providerB)
            modelsByUser["user-a"] = listOf(modelA)
            modelsByUser["user-b"] = listOf(modelB)
        }
        val viewModel = AIModelsViewModel(repository, FakeAuthRepository(users))
        advanceUntilIdle()
        assertEquals(listOf(providerA), viewModel.uiState.value.providers)

        users.value = UserAccount(id = "user-b")
        advanceUntilIdle()
        assertEquals(listOf(providerB), viewModel.uiState.value.providers)
        assertEquals(listOf(modelB), viewModel.uiState.value.models)
        assertFalse(viewModel.uiState.value.models.any { it.id == modelA.id })
    }

    @Test
    fun providerTestFailureMarksErrorWithoutExposingProviderDiagnostics() = runTest(mainDispatcher) {
        val users = MutableStateFlow<UserAccount?>(UserAccount(id = "user-a"))
        val repository = FakeAIModelRepository().apply {
            connectionResult = Result.failure(IOException("secret provider response"))
        }
        val viewModel = AIModelsViewModel(repository, FakeAuthRepository(users))
        advanceUntilIdle()
        viewModel.testConnection("provider-a")
        advanceUntilIdle()

        assertEquals(AIProviderStatus.ERROR, repository.statusUpdates.single().status)
        assertTrue(viewModel.uiState.value.error.orEmpty().contains("تعذر اختبار اتصال المزود"))
        assertFalse(viewModel.uiState.value.error.orEmpty().contains("secret provider response"))
        assertNull(viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isLoading)
    }



    @Test
    fun disconnectedProviderModelsAreNotExposedForSelection() = runTest(mainDispatcher) {
        val users = MutableStateFlow<UserAccount?>(UserAccount(id = "user-a"))
        val provider = AIProvider(
            id = "provider-a",
            userId = "user-a",
            name = "Disconnected",
            status = AIProviderStatus.DISCONNECTED
        )
        val model = AIModel(id = "model-a", providerId = provider.id, name = "Model A", isFree = true)
        val repository = FakeAIModelRepository().apply {
            providersByUser["user-a"] = listOf(provider)
            modelsByUser["user-a"] = listOf(model)
        }
        val viewModel = AIModelsViewModel(repository, FakeAuthRepository(users))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.models.isEmpty())
        assertNull(viewModel.uiState.value.defaultModelId)
    }

    @Test
    fun providerTestSuccessMarksConnectedOnlyAfterRepositorySuccess() = runTest(mainDispatcher) {
        val users = MutableStateFlow<UserAccount?>(UserAccount(id = "user-a"))
        val provider = AIProvider(id = "provider-a", userId = "user-a", name = "Provider A")
        val repository = FakeAIModelRepository().apply {
            providersByUser["user-a"] = listOf(provider)
            connectionResult = Result.success(Unit)
        }
        val viewModel = AIModelsViewModel(repository, FakeAuthRepository(users))
        advanceUntilIdle()

        viewModel.testConnection(provider.id)
        advanceUntilIdle()

        assertEquals(AIProviderStatus.CONNECTED, repository.statusUpdates.single().status)
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.message.orEmpty().contains("اتصال"))
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun failedModelSyncDoesNotLeaveProviderConnected() = runTest(mainDispatcher) {
        val users = MutableStateFlow<UserAccount?>(UserAccount(id = "user-a"))
        val repository = FakeAIModelRepository().apply {
            syncResult = Result.failure(IOException("catalog request failed"))
        }
        val viewModel = AIModelsViewModel(repository, FakeAuthRepository(users))
        advanceUntilIdle()
        viewModel.syncModels("provider-a")
        advanceUntilIdle()

        assertEquals(AIProviderStatus.ERROR, repository.statusUpdates.single().status)
        assertTrue(viewModel.uiState.value.error.orEmpty().contains("تعذر تحديث النماذج"))
        assertNull(viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun providerFailuresShowDistinctSafeMessagesAndRateLimitStatus() = runTest(mainDispatcher) {
        val cases = listOf(
            AIProviderFailureKind.INVALID_CREDENTIALS to "مفتاح API",
            AIProviderFailureKind.QUOTA_EXCEEDED to "حصته",
            AIProviderFailureKind.RATE_LIMITED to "حد الطلبات",
            AIProviderFailureKind.TIMEOUT to "مهلة الاتصال",
            AIProviderFailureKind.EMPTY_RESPONSE to "محتوى صالح"
        )

        cases.forEach { (kind, expectedMessage) ->
            val users = MutableStateFlow<UserAccount?>(UserAccount(id = "user-a"))
            val repository = FakeAIModelRepository().apply {
                connectionResult = Result.failure(AIProviderOperationException(kind))
            }
            val viewModel = AIModelsViewModel(repository, FakeAuthRepository(users))
            advanceUntilIdle()

            viewModel.testConnection("provider-a")
            advanceUntilIdle()

            assertTrue("Expected a safe message for $kind", viewModel.uiState.value.error.orEmpty().contains(expectedMessage))
            val expectedStatus = if (kind == AIProviderFailureKind.RATE_LIMITED) {
                AIProviderStatus.RATE_LIMITED
            } else {
                AIProviderStatus.ERROR
            }
            assertEquals(expectedStatus, repository.statusUpdates.single().status)
            assertFalse(viewModel.uiState.value.error.orEmpty().contains(kind.name))
        }
    }

    @Test
    fun unsupportedProviderFailureUsesNonOperationalStatus() = runTest(mainDispatcher) {
        val users = MutableStateFlow<UserAccount?>(UserAccount(id = "user-a"))
        val repository = FakeAIModelRepository().apply {
            connectionResult = Result.failure(AIProviderOperationException(AIProviderFailureKind.UNSUPPORTED))
        }
        val viewModel = AIModelsViewModel(repository, FakeAuthRepository(users))
        advanceUntilIdle()

        viewModel.testConnection("legacy-provider")
        advanceUntilIdle()

        assertEquals(AIProviderStatus.UNSUPPORTED, repository.statusUpdates.single().status)
        assertTrue(viewModel.uiState.value.error.orEmpty().contains("غير مدعوم"))
    }

    @Test
    fun providerLoadFailureCanBeRetried() = runTest(mainDispatcher) {
        val users = MutableStateFlow<UserAccount?>(UserAccount(id = "user-a"))
        val repository = FakeAIModelRepository().apply { failProviderLoad = true }
        val viewModel = AIModelsViewModel(repository, FakeAuthRepository(users))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoadError)

        repository.failProviderLoad = false
        viewModel.refresh()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoadError)
        assertNull(viewModel.uiState.value.error)
    }

    private class FakeAuthRepository(private val users: MutableStateFlow<UserAccount?>) : AuthRepository {
        override val currentUser: Flow<UserAccount?> = users
        override fun getCurrentUserSync(): UserAccount? = users.value
        override suspend fun signUp(email: String, password: String, displayName: String, organization: String) =
            Result.failure<UserAccount>(UnsupportedOperationException())
        override suspend fun signIn(email: String, password: String) =
            Result.failure<UserAccount>(UnsupportedOperationException())
        override suspend fun signOut() { users.value = null }
        override suspend fun requestPasswordRecovery(email: String) =
            Result.failure<Unit>(UnsupportedOperationException())
        override suspend fun completePasswordRecovery(credentials: PasswordRecoveryCredentials, newPassword: String) =
            Result.failure<UserAccount>(UnsupportedOperationException())
        override suspend fun resetPassword(email: String, newPassword: String) =
            Result.failure<Unit>(UnsupportedOperationException())
        override suspend fun signInWithGoogle(idToken: String) =
            Result.failure<Unit>(UnsupportedOperationException())
    }

    private class FakeAIModelRepository : AIModelRepository {
        val providersByUser = mutableMapOf<String, List<AIProvider>>()
        val modelsByUser = mutableMapOf<String, List<AIModel>>()
        val statusUpdates = mutableListOf<StatusUpdate>()
        var connectionResult: Result<Unit> = Result.success(Unit)
        var syncResult: Result<Unit> = Result.success(Unit)
        var failProviderLoad = false

        override fun getProviders(userId: String): Flow<List<AIProvider>> =
            if (failProviderLoad) flow { throw IOException("private database diagnostic") }
            else flowOf(providersByUser[userId].orEmpty())
        override suspend fun getProviderById(userId: String, id: String): AIProvider? =
            providersByUser[userId].orEmpty().firstOrNull { it.id == id }
        override suspend fun addProvider(provider: AIProvider) {
            providersByUser[provider.userId] = providersByUser[provider.userId].orEmpty() + provider
        }
        override suspend fun deleteProvider(userId: String, id: String) {
            providersByUser[userId] = providersByUser[userId].orEmpty().filterNot { it.id == id }
        }
        override suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long) {
            statusUpdates += StatusUpdate(userId, id, status)
            providersByUser[userId] = providersByUser[userId].orEmpty().map {
                if (it.id == id) it.copy(status = status, lastSyncAt = syncTime) else it
            }
        }
        override fun getModelsByProvider(userId: String, providerId: String): Flow<List<AIModel>> =
            flowOf(modelsByUser[userId].orEmpty().filter { it.providerId == providerId })
        override fun getAllAvailableModels(userId: String, freeOnly: Boolean): Flow<List<AIModel>> {
            val connectedProviderIds = providersByUser[userId].orEmpty()
                .filter { it.status == AIProviderStatus.CONNECTED }
                .mapTo(HashSet()) { it.id }
            return flowOf(
                modelsByUser[userId].orEmpty().filter {
                    it.providerId in connectedProviderIds && it.isAvailable && (!freeOnly || it.isFree)
                }
            )
        }
        override suspend fun getModelById(userId: String, id: String): AIModel? =
            modelsByUser[userId].orEmpty().firstOrNull { it.id == id }
        override suspend fun syncModels(userId: String, providerId: String): Result<Unit> = syncResult
        override suspend fun testConnection(userId: String, providerId: String): Result<Unit> = connectionResult
        override fun getRecentUsageLogs(userId: String): Flow<List<AIUsageLog>> = flowOf(emptyList())
        override fun getTotalCost(userId: String, since: Long): Flow<Double?> = flowOf(0.0)
        override suspend fun logUsage(log: AIUsageLog) = Unit
        override fun getUserSettings(userId: String): Flow<AIUserSettings?> = flowOf(AIUserSettings(userId = userId))
        override suspend fun updateUserSettings(settings: AIUserSettings) = Unit
        override suspend fun setDefaultModel(userId: String, modelId: String) = Unit
        override suspend fun getDefaultModel(userId: String): AIModel? = null
        override suspend fun generateResponse(
            userId: String, modelId: String, prompt: String, systemInstruction: String?, taskId: String?, taskType: AITaskType
        ): Result<String> = Result.success("test")

        data class StatusUpdate(val userId: String, val providerId: String, val status: AIProviderStatus)
    }
}
