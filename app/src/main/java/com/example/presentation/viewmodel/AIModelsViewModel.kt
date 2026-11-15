package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class AIModelsUiState(
    val providers: List<AIProvider> = emptyList(),
    val models: List<AIModel> = emptyList(),
    val selectedProvider: AIProvider? = null,
    val isLoading: Boolean = false,
    val isLoadError: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val defaultModelId: String? = null,
    val freeOnly: Boolean = true,
    val autoSelectFree: Boolean = true,
    val recentLogs: List<AIUsageLog> = emptyList(),
    val totalCost: Double = 0.0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val lastSuccessAt: Long? = null
)

class AIModelsViewModel(
    private val aiModelRepository: AIModelRepository,
    private val authRepository: com.example.domain.repository.AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIModelsUiState())
    val uiState: StateFlow<AIModelsUiState> = _uiState.asStateFlow()
    private val currentUser = authRepository.currentUser.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val refreshTrigger = MutableStateFlow(0)
    private val activeOperations = MutableStateFlow(0)
    private var observedUserId: String? = null

    init {
        viewModelScope.launch {
            currentUser
                .map { user -> user?.takeIf { it.id.isNotBlank() } }
                .distinctUntilChangedBy { it?.id }
                .flatMapLatest { user ->
                    if (user == null) {
                        flowOf(AIModelsUiState())
                    } else {
                        refreshTrigger.flatMapLatest {
                            val userId = user.id
                            val source = combine(
                                aiModelRepository.getProviders(userId),
                                aiModelRepository.getUserSettings(userId),
                                aiModelRepository.getRecentUsageLogs(userId),
                                aiModelRepository.getTotalCost(
                                    userId,
                                    System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
                                )
                            ) { providers, settings, logs, cost ->
                                val freeOnly = settings?.freeOnly ?: true
                                val models = aiModelRepository.getAllAvailableModels(userId, freeOnly).first()
                                val defaultModelId = settings?.defaultModelId
                                    ?.takeIf { savedId -> models.any { it.id == savedId } }
                                    ?: models.firstOrNull { it.isFree }?.id
                                AIModelsUiState(
                                    providers = providers,
                                    models = models,
                                    recentLogs = logs,
                                    totalCost = cost ?: 0.0,
                                    defaultModelId = defaultModelId,
                                    freeOnly = freeOnly,
                                    autoSelectFree = settings?.autoSelectFree ?: true,
                                    successCount = logs.count { it.isSuccess },
                                    failedCount = logs.count { !it.isSuccess },
                                    lastSuccessAt = logs.firstOrNull { it.isSuccess }?.timestamp
                                )
                            }
                                .onStart { emit(AIModelsUiState(isLoading = true)) }
                                .catch { emit(AIModelsUiState(isLoadError = true, error = LOAD_ERROR)) }

                            combine(source, activeOperations) { state, count ->
                                state.copy(isLoading = state.isLoading || count > 0)
                            }
                        }
                    }
                }
                .collect { state ->
                    val activeUserId = currentUserId()
                    if (activeUserId != observedUserId) {
                        observedUserId = activeUserId
                        _uiState.value = state
                    } else {
                        _uiState.update { current ->
                            state.copy(error = current.error, message = current.message)
                        }
                    }
                }
        }
    }

    fun refresh() {
        refreshTrigger.update { it + 1 }
    }

    fun addProvider(name: String, type: AIProviderType, apiKey: String, baseUrl: String? = null) {
        val userId = currentUserId() ?: return
        val normalizedName = name.trim()
        val normalizedKey = apiKey.trim()
        if (normalizedName.isEmpty() || normalizedKey.isEmpty()) {
            _uiState.update { it.copy(error = INPUT_ERROR, message = null) }
            return
        }
        viewModelScope.launch {
            runOperation(
                userId,
                "تم حفظ المزود ومزامنة نماذجه بنجاح.",
                "تعذر حفظ المزود أو مزامنة نماذجه. تحقق من المفتاح والاتصال."
            ) {
                val provider = AIProvider(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    name = normalizedName,
                    type = type,
                    apiKey = normalizedKey,
                    baseUrl = baseUrl?.trim()?.takeIf { it.isNotEmpty() },
                    status = AIProviderStatus.DISCONNECTED
                )
                aiModelRepository.addProvider(provider)
                val result = aiModelRepository.syncModels(userId, provider.id)
                if (result.isFailure) {
                    markProviderError(userId, provider.id)
                    throw result.exceptionOrNull() ?: IllegalStateException()
                }
            }
        }
    }

    fun deleteProvider(providerId: String) {
        val userId = currentUserId() ?: return
        viewModelScope.launch {
            runOperation(userId, "تم حذف المزود ونماذجه.", "تعذر حذف المزود. أعد المحاولة.") {
                aiModelRepository.deleteProvider(userId, providerId)
            }
        }
    }

    fun syncModels(providerId: String) {
        val userId = currentUserId() ?: return
        viewModelScope.launch {
            runOperation(userId, "تم تحديث قائمة النماذج.", "تعذر تحديث النماذج. تحقق من اتصال المزود.") {
                val result = aiModelRepository.syncModels(userId, providerId)
                if (result.isFailure) {
                    markProviderError(userId, providerId)
                    throw result.exceptionOrNull() ?: IllegalStateException()
                }
            }
        }
    }

    fun testConnection(providerId: String) {
        val userId = currentUserId() ?: return
        viewModelScope.launch {
            runOperation(userId, "نجح اختبار الاتصال الفعلي بالمزود.", "تعذر اختبار اتصال المزود. تحقق من المفتاح والاتصال.") {
                val result = aiModelRepository.testConnection(userId, providerId)
                if (result.isFailure) {
                    markProviderError(userId, providerId)
                    throw result.exceptionOrNull() ?: IllegalStateException()
                }
                aiModelRepository.updateProviderStatus(
                    userId, providerId, AIProviderStatus.CONNECTED, System.currentTimeMillis()
                )
            }
        }
    }

    fun setDefaultModel(modelId: String) {
        val userId = currentUserId() ?: return
        viewModelScope.launch {
            runOperation(userId, "تم حفظ النموذج الافتراضي.", "تعذر حفظ النموذج الافتراضي.") {
                aiModelRepository.setDefaultModel(userId, modelId)
            }
        }
    }

    fun toggleFreeOnly(freeOnly: Boolean) {
        val userId = currentUserId() ?: return
        viewModelScope.launch {
            runOperation(userId, "تم تحديث إعداد النماذج المجانية.", "تعذر حفظ إعداد النماذج المجانية.") {
                val current = aiModelRepository.getUserSettings(userId).first()
                aiModelRepository.updateUserSettings(
                    (current ?: AIUserSettings(userId = userId)).copy(
                        freeOnly = freeOnly,
                        lastModified = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun toggleAutoSelect(autoSelect: Boolean) {
        val userId = currentUserId() ?: return
        viewModelScope.launch {
            runOperation(userId, "تم تحديث إعداد الاختيار الذكي.", "تعذر حفظ إعداد الاختيار الذكي.") {
                val current = aiModelRepository.getUserSettings(userId).first()
                aiModelRepository.updateUserSettings(
                    (current ?: AIUserSettings(userId = userId)).copy(
                        autoSelectFree = autoSelect,
                        lastModified = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(error = null, message = null) }
    }

    fun clearError() = clearFeedback()

    private fun currentUserId(): String? = currentUser.value?.id?.takeIf { it.isNotBlank() }
    private fun isCurrentUser(userId: String): Boolean = currentUserId() == userId

    private suspend fun markProviderError(userId: String, providerId: String) {
        try {
            aiModelRepository.updateProviderStatus(
                userId, providerId, AIProviderStatus.ERROR, System.currentTimeMillis()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Preserve the original provider-operation failure for the user.
        }
    }

    private suspend fun runOperation(
        userId: String,
        successMessage: String,
        failureMessage: String,
        action: suspend () -> Unit
    ) {
        activeOperations.update { it + 1 }
        try {
            action()
            if (isCurrentUser(userId)) {
                _uiState.update { it.copy(error = null, message = successMessage) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (isCurrentUser(userId)) {
                _uiState.update { it.copy(error = failureMessage, message = null) }
            }
        } finally {
            activeOperations.update { (it - 1).coerceAtLeast(0) }
        }
    }

    private companion object {
        const val LOAD_ERROR = "تعذر تحميل إعدادات مزودي الذكاء الاصطناعي."
        const val INPUT_ERROR = "أدخل اسم المزود ومفتاح API."
    }
}
