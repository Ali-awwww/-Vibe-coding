package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class AIModelsUiState(
    val providers: List<AIProvider> = emptyList(),
    val models: List<AIModel> = emptyList(),
    val selectedProvider: AIProvider? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
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

    init {
        viewModelScope.launch {
            currentUser.filterNotNull().flatMapLatest { user ->
                val userId = user.id
                val settingsFlow = aiModelRepository.getUserSettings(userId)
                
                combine(
                    aiModelRepository.getProviders(userId),
                    settingsFlow,
                    aiModelRepository.getRecentUsageLogs(userId),
                    aiModelRepository.getTotalCost(userId, System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L)
                ) { providers, settings, logs, cost ->
                    val freeOnly = settings?.freeOnly ?: true
                    val models = aiModelRepository.getAllAvailableModels(userId, freeOnly).first()
                    
                    AIModelsUiState(
                        providers = providers,
                        models = models,
                        recentLogs = logs,
                        totalCost = cost ?: 0.0,
                        defaultModelId = settings?.defaultModelId ?: models.firstOrNull { it.isFree }?.id,
                        freeOnly = freeOnly,
                        autoSelectFree = settings?.autoSelectFree ?: true,
                        successCount = logs.count { it.isSuccess },
                        failedCount = logs.count { !it.isSuccess },
                        lastSuccessAt = logs.firstOrNull { it.isSuccess }?.timestamp
                    )
                }
            }.collect { state ->
                _uiState.update { it.copy(
                    providers = state.providers,
                    models = state.models,
                    recentLogs = state.recentLogs,
                    totalCost = state.totalCost,
                    defaultModelId = state.defaultModelId,
                    freeOnly = state.freeOnly,
                    autoSelectFree = state.autoSelectFree,
                    successCount = state.successCount,
                    failedCount = state.failedCount,
                    lastSuccessAt = state.lastSuccessAt
                ) }
            }
        }
    }

    fun addProvider(name: String, type: AIProviderType, apiKey: String, baseUrl: String? = null) {
        val userId = currentUser.value?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val provider = AIProvider(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    name = name,
                    type = type,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    status = AIProviderStatus.DISCONNECTED
                )
                aiModelRepository.addProvider(provider)
                syncModels(provider.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun deleteProvider(providerId: String) {
        val userId = currentUser.value?.id ?: return
        viewModelScope.launch {
            aiModelRepository.deleteProvider(userId, providerId)
        }
    }

    fun syncModels(providerId: String) {
        val userId = currentUser.value?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            aiModelRepository.syncModels(userId, providerId)
                .onFailure { e -> _uiState.update { it.copy(error = "Sync failed: ${e.message}") } }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun testConnection(providerId: String) {
        val userId = currentUser.value?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            aiModelRepository.testConnection(userId, providerId)
                .onSuccess { 
                    _uiState.update { it.copy(error = "Connection successful!") }
                    aiModelRepository.updateProviderStatus(userId, providerId, AIProviderStatus.CONNECTED, System.currentTimeMillis())
                }
                .onFailure { e -> 
                    _uiState.update { it.copy(error = "Connection failed: ${e.message}") }
                    aiModelRepository.updateProviderStatus(userId, providerId, AIProviderStatus.ERROR, System.currentTimeMillis())
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun setDefaultModel(modelId: String) {
        val userId = currentUser.value?.id ?: return
        viewModelScope.launch {
            aiModelRepository.setDefaultModel(userId, modelId)
        }
    }

    fun toggleFreeOnly(freeOnly: Boolean) {
        val userId = currentUser.value?.id ?: return
        viewModelScope.launch {
            val current = aiModelRepository.getUserSettings(userId).first()
            aiModelRepository.updateUserSettings((current ?: AIUserSettings(userId = userId)).copy(
                freeOnly = freeOnly,
                lastModified = System.currentTimeMillis()
            ))
        }
    }

    fun toggleAutoSelect(autoSelect: Boolean) {
        val userId = currentUser.value?.id ?: return
        viewModelScope.launch {
            val current = aiModelRepository.getUserSettings(userId).first()
            aiModelRepository.updateUserSettings((current ?: AIUserSettings(userId = userId)).copy(
                autoSelectFree = autoSelect,
                lastModified = System.currentTimeMillis()
            ))
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
