package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FixAssistantUiState(
    val crash: CrashReport? = null,
    val diagnosis: AIDiagnosis? = null,
    val suggestions: List<FixSuggestion> = emptyList(),
    val verifications: List<CrashVerificationRecord> = emptyList(),
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val isCreatingPr: Boolean = false,
    val isVerifying: Boolean = false,
    val verificationResult: CrashVerificationRecord? = null,
    val error: String? = null,
    val prUrl: String? = null
)

class FixAssistantViewModel(
    private val repository: CrashRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FixAssistantUiState())
    val uiState: StateFlow<FixAssistantUiState> = _uiState.asStateFlow()

    fun loadCrashAndDiagnosis(crashId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            repository.getCrashDetails(crashId).first()?.let { crash ->
                val diagnosis = repository.getAIDiagnosis(crash)
                _uiState.update { it.copy(crash = crash, diagnosis = diagnosis) }
                
                launch {
                    repository.getFixSuggestions(crashId).collect { suggestions ->
                        _uiState.update { it.copy(suggestions = suggestions) }
                    }
                }

                launch {
                    repository.getVerificationsByCrash(crashId).collect { verifications ->
                        _uiState.update { it.copy(verifications = verifications, isLoading = false) }
                    }
                }
            }
        }
    }
    
    fun setDiagnosis(diagnosis: AIDiagnosis) {
        _uiState.update { it.copy(diagnosis = diagnosis) }
    }

    fun generateFix() {
        val crash = _uiState.value.crash ?: return
        val diagnosis = _uiState.value.diagnosis ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            val result = repository.generateFixSuggestion(crash, diagnosis)
            _uiState.update { it.copy(isGenerating = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun applyFixAndCreatePr(fixId: String, userId: String) {
        if (userId.isBlank()) {
            _uiState.update { it.copy(error = "Authentication is required before creating a Pull Request.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingPr = true, error = null) }
            val result = repository.applyFixAndCreatePR(fixId, userId)
            _uiState.update { 
                it.copy(
                    isCreatingPr = false, 
                    prUrl = result.getOrNull(), 
                    error = result.exceptionOrNull()?.message 
                ) 
            }
        }
    }

    fun verifyFix(
        newVersion: String,
        buildId: String,
        scenarioName: String,
        simulatedOutcome: VerificationStatus? = null
    ) {
        val crash = _uiState.value.crash ?: return
        val activeFix = _uiState.value.suggestions.firstOrNull()
        
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifying = true, error = null) }
            val result = repository.verifyFix(
                crashId = crash.id,
                fixSuggestionId = activeFix?.id,
                newVersion = newVersion,
                buildId = buildId,
                scenarioName = scenarioName,
                simulatedOutcome = simulatedOutcome
            )
            _uiState.update { 
                it.copy(
                    isVerifying = false,
                    verificationResult = result.getOrNull(),
                    error = result.exceptionOrNull()?.message
                ) 
            }
        }
    }
}
