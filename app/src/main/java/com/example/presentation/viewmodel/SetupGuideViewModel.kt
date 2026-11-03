package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.SdkIntegrationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SetupGuideUiState(
    val tech: ProjectTechnology? = null,
    val guide: SdkSetupGuide? = null,
    val isScanning: Boolean = false,
    val isVerifying: Boolean = false,
    val verificationResult: Boolean? = null,
    val error: String? = null
)

class SetupGuideViewModel(
    private val repository: SdkIntegrationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupGuideUiState())
    val uiState = _uiState.asStateFlow()

    fun scanProject(fileNames: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, error = null)
            val result = repository.detectTechnology(fileNames)
            _uiState.value = _uiState.value.copy(
                tech = result.tech,
                isScanning = false
            )
        }
    }

    fun selectManualTech(tech: ProjectTechnology) {
        _uiState.value = _uiState.value.copy(tech = tech, guide = null)
    }

    fun generateGuide(service: IntegrationType, projectId: String) {
        val tech = _uiState.value.tech ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            val guide = repository.getSetupGuide(tech, service, projectId)
            _uiState.value = _uiState.value.copy(
                guide = guide,
                isScanning = false
            )
        }
    }

    fun verifyIntegration(applicationId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isVerifying = true, verificationResult = null)
            val result = repository.verifyIntegration(applicationId)
            _uiState.value = _uiState.value.copy(
                isVerifying = false,
                verificationResult = result.getOrNull() ?: false,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    fun resetVerification() {
        _uiState.value = _uiState.value.copy(verificationResult = null, error = null)
    }
}
