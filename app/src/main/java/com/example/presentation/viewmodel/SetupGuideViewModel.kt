package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.model.*
import com.example.domain.repository.SdkIntegrationRepository
import com.example.domain.repository.SetupGuideProgressStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SetupGuideUiState(
    val tech: ProjectTechnology? = null,
    val selectedService: IntegrationType = IntegrationType.SENTRY,
    val guide: SdkSetupGuide? = null,
    val isScanning: Boolean = false,
    val isVerifying: Boolean = false,
    val verificationResult: Boolean? = null,
    val error: String? = null,
    val errorResId: Int? = null
)

class SetupGuideViewModel(
    private val repository: SdkIntegrationRepository,
    private val progressStore: SetupGuideProgressStore? = null,
    private val projectId: String? = null,
    private val applicationId: String? = null
) : ViewModel() {

    private val restoredProgress = loadProgress()
    private val _uiState = MutableStateFlow(
        SetupGuideUiState(
            tech = restoredProgress?.tech,
            selectedService = restoredProgress?.service ?: IntegrationType.SENTRY
        )
    )
    val uiState = _uiState.asStateFlow()

    val isVerificationTransportAvailable: Boolean = repository.isVerificationTransportAvailable

    init {
        val restoredTech = restoredProgress?.tech
        val restoredProjectId = projectId
        if (restoredTech != null && restoredTech != ProjectTechnology.UNKNOWN && !restoredProjectId.isNullOrBlank()) {
            generateGuide(_uiState.value.selectedService, restoredProjectId)
        }
    }

    fun scanProject(fileNames: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, error = null, errorResId = null)
            val result = runCatching { repository.detectTechnology(fileNames) }
            result.fold(
                onSuccess = { scan ->
                    val tech = scan.tech.takeUnless { it == ProjectTechnology.UNKNOWN }
                    _uiState.value = _uiState.value.copy(tech = tech, guide = null, isScanning = false)
                    persistSelection(tech, _uiState.value.selectedService)
                },
                onFailure = { failure ->
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        error = failure.message,
                        errorResId = R.string.setup_guide_load_failed
                    )
                }
            )
        }
    }

    fun selectManualTech(tech: ProjectTechnology) {
        val selectedTech = tech.takeUnless { it == ProjectTechnology.UNKNOWN }
        _uiState.value = _uiState.value.copy(
            tech = selectedTech,
            guide = null,
            verificationResult = null,
            error = null,
            errorResId = null,
            isVerifying = false
        )
        persistSelection(selectedTech, _uiState.value.selectedService)
    }

    fun generateGuide(service: IntegrationType, projectId: String) {
        val tech = _uiState.value.tech ?: return
        _uiState.value = _uiState.value.copy(
            selectedService = service,
            isScanning = true,
            guide = null,
            verificationResult = null,
            error = null,
            errorResId = null
        )
        persistSelection(tech, service)

        viewModelScope.launch {
            val result = runCatching { repository.getSetupGuide(tech, service, projectId) }
            result.fold(
                onSuccess = { guide ->
                    _uiState.value = _uiState.value.copy(guide = guide, isScanning = false)
                },
                onFailure = { failure ->
                    _uiState.value = _uiState.value.copy(
                        guide = null,
                        isScanning = false,
                        error = failure.message,
                        errorResId = R.string.setup_guide_load_failed
                    )
                }
            )
        }
    }

    fun verifyIntegration(applicationId: String) {
        if (!isVerificationTransportAvailable) {
            _uiState.value = _uiState.value.copy(
                isVerifying = false,
                verificationResult = false,
                error = null,
                errorResId = R.string.setup_guide_verification_unavailable
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isVerifying = true,
                verificationResult = null,
                error = null,
                errorResId = null
            )
            val result = runCatching { repository.verifyIntegration(applicationId) }
                .getOrElse { failure -> Result.failure(failure) }
            val verified = result.getOrNull()
            _uiState.value = _uiState.value.copy(
                isVerifying = false,
                verificationResult = verified ?: false,
                error = result.exceptionOrNull()?.message,
                errorResId = if (result.isSuccess && verified == false) {
                    R.string.setup_guide_verification_waiting_event
                } else {
                    null
                }
            )
        }
    }

    fun resetVerification() {
        _uiState.value = _uiState.value.copy(
            verificationResult = null,
            error = null,
            errorResId = null
        )
    }

    private fun loadProgress(): com.example.domain.repository.SetupGuideProgress? {
        val savedProjectId = projectId?.takeIf { it.isNotBlank() } ?: return null
        val savedApplicationId = applicationId?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { progressStore?.load(savedProjectId, savedApplicationId) }.getOrNull()
    }

    private fun persistSelection(tech: ProjectTechnology?, service: IntegrationType) {
        val savedProjectId = projectId?.takeIf { it.isNotBlank() } ?: return
        val savedApplicationId = applicationId?.takeIf { it.isNotBlank() } ?: return
        runCatching { progressStore?.save(savedProjectId, savedApplicationId, tech, service) }
    }
}
