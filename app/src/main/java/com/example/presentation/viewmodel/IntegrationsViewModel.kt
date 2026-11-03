package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Integration
import com.example.domain.model.IntegrationType
import com.example.domain.model.Role
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class IntegrationsUiState(
    val integrations: List<Integration> = emptyList(),
    val userRole: Role? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val syncResult: String? = null,
    val webhookTestResult: String? = null
)

class IntegrationsViewModel(
    private val repository: CrashRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _syncResult = MutableStateFlow<String?>(null)
    private val _webhookTestResult = MutableStateFlow<String?>(null)
    private val _userRole = MutableStateFlow<Role?>(null)

    fun getIntegrations(projectId: String): StateFlow<IntegrationsUiState> {
        val user = authRepository.getCurrentUserSync()
        if (user != null) {
            viewModelScope.launch {
                _userRole.value = repository.getUserRoleInProject(projectId, user.id)
            }
        }

        return combine(
            repository.getIntegrations(projectId),
            _syncResult,
            _webhookTestResult,
            _userRole
        ) { integrations, sync, webhookTest, role ->
            IntegrationsUiState(
                integrations = integrations,
                userRole = role,
                syncResult = sync,
                webhookTestResult = webhookTest,
                isLoading = false
            )
        }
        .onStart { emit(IntegrationsUiState(isLoading = true)) }
        .catch { e -> emit(IntegrationsUiState(error = e.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IntegrationsUiState())
    }

    fun addIntegration(projectId: String, name: String, type: IntegrationType, configJson: String) {
        viewModelScope.launch {
            repository.addIntegration(
                Integration(
                    projectId = projectId,
                    name = name,
                    type = type,
                    configJson = configJson
                )
            )
            _syncResult.value = "تم تشفير بيانات الاتصال بـ AES-GCM وحفظ التكامل بنجاح."
        }
    }

    fun deleteIntegration(projectId: String, integrationId: String) {
        val user = authRepository.getCurrentUserSync() ?: return
        viewModelScope.launch {
            val result = repository.deleteIntegration(integrationId, projectId, user.id)
            if (result.isSuccess) {
                _syncResult.value = "تمت إزالة التكامل بنجاح."
            } else {
                _syncResult.value = "فشل حذف التكامل: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun testWebhookSignature(projectId: String, integrationId: String, payload: String, signature: String) {
        viewModelScope.launch {
            val result = repository.validateWebhook(projectId, integrationId, payload, signature)
            if (result.isSuccess && result.getOrDefault(false)) {
                _webhookTestResult.value = "نجح التحقق: توقيع HMAC-SHA256 صالح وموثوق ومعدل الطلبات ضمن الحدود."
            } else {
                _webhookTestResult.value = "فشل التحقق: التوقيع غير صالح أو تم تجاوز معدل الطلبات (Rate limit)."
            }
        }
    }

    fun syncCrashes(projectId: String) {
        viewModelScope.launch {
            _syncResult.value = "جاري الاتصال والتحقق من الصلاحيات والمزامنة..."
            val result = repository.syncCrashes(projectId)
            if (result.isSuccess) {
                _syncResult.value = "اكتملت المزامنة الآمنة بنجاح مع تنقيح البيانات الحساسة."
            } else {
                _syncResult.value = "فشلت المزامنة: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearSyncResult() {
        _syncResult.value = null
        _webhookTestResult.value = null
    }
}
