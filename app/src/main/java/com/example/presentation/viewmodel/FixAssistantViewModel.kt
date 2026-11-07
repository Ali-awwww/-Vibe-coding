package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val loadError: String? = null,
    val suggestionsLoadError: String? = null,
    val verificationsLoadError: String? = null,
    val error: String? = null,
    val prUrl: String? = null
)

class FixAssistantViewModel(
    private val repository: CrashRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FixAssistantUiState())
    val uiState: StateFlow<FixAssistantUiState> = _uiState.asStateFlow()

    private var crashLoadJob: Job? = null
    private var suggestionsJob: Job? = null
    private var verificationsJob: Job? = null

    fun loadCrashAndDiagnosis(crashId: String) {
        crashLoadJob?.cancel()
        suggestionsJob?.cancel()
        verificationsJob?.cancel()

        crashLoadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    crash = null,
                    diagnosis = null,
                    suggestions = emptyList(),
                    verifications = emptyList(),
                    isLoading = true,
                    isGenerating = false,
                    isCreatingPr = false,
                    isVerifying = false,
                    verificationResult = null,
                    loadError = null,
                    suggestionsLoadError = null,
                    verificationsLoadError = null,
                    error = null,
                    prUrl = null
                )
            }

            if (crashId.isBlank()) {
                _uiState.update {
                    it.copy(isLoading = false, loadError = "معرّف الانهيار غير صالح.")
                }
                return@launch
            }

            try {
                val crash = repository.getCrashDetails(crashId).firstOrNull()
                if (crash == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = "لم يتم العثور على سجل الانهيار. تحقق من المعرّف ثم أعد المحاولة."
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(crash = crash) }
                val diagnosis = repository.getAIDiagnosis(crash)
                _uiState.update {
                    it.copy(diagnosis = diagnosis, isLoading = false, loadError = null)
                }

                suggestionsJob = viewModelScope.launch {
                    try {
                        repository.getFixSuggestions(crashId).collect { suggestions ->
                            _uiState.update {
                                it.copy(suggestions = suggestions, suggestionsLoadError = null)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        _uiState.update {
                            it.copy(
                                suggestionsLoadError = error.message
                                    ?: "تعذر تحميل اقتراحات الإصلاح."
                            )
                        }
                    }
                }

                verificationsJob = viewModelScope.launch {
                    try {
                        repository.getVerificationsByCrash(crashId).collect { verifications ->
                            _uiState.update {
                                it.copy(verifications = verifications, verificationsLoadError = null)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        _uiState.update {
                            it.copy(
                                verificationsLoadError = error.message
                                    ?: "تعذر تحميل سجلات التحقق."
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = error.message ?: "تعذر تحميل بيانات الانهيار. أعد المحاولة."
                    )
                }
            }
        }
    }

        fun setDiagnosis(diagnosis: AIDiagnosis) {
        _uiState.update { it.copy(diagnosis = diagnosis, loadError = null, error = null) }
    }

    fun generateFix() {
        val state = _uiState.value
        val crash = state.crash
        val diagnosis = state.diagnosis
        if (crash == null || diagnosis == null) {
            _uiState.update {
                it.copy(error = "لا يمكن إنشاء اقتراح قبل تحميل بيانات الانهيار وتشخيصه.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            val result = executeRepositoryResult {
                repository.generateFixSuggestion(crash, diagnosis)
            }
            _uiState.update { current ->
                val suggestion = result.getOrNull()
                current.copy(
                    isGenerating = false,
                    suggestions = if (suggestion == null) current.suggestions
                        else (current.suggestions + suggestion).distinctBy { it.id },
                    suggestionsLoadError = if (suggestion != null) null else current.suggestionsLoadError,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun applyFixAndCreatePr(fixId: String, userId: String) {
        if (userId.isBlank()) {
            _uiState.update {
                it.copy(error = "يلزم تسجيل الدخول بحساب صالح قبل إنشاء Pull Request.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingPr = true, error = null) }
            val result = executeRepositoryResult {
                repository.applyFixAndCreatePR(fixId, userId)
            }
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
        val crash = _uiState.value.crash
        if (crash == null) {
            _uiState.update {
                it.copy(error = "لا يمكن التحقق من الإصلاح قبل تحميل سجل الانهيار.")
            }
            return
        }
        val activeFix = _uiState.value.suggestions.firstOrNull()

        viewModelScope.launch {
            _uiState.update { it.copy(isVerifying = true, error = null) }
            val result = executeRepositoryResult {
                repository.verifyFix(
                    crashId = crash.id,
                    fixSuggestionId = activeFix?.id,
                    newVersion = newVersion,
                    buildId = buildId,
                    scenarioName = scenarioName,
                    simulatedOutcome = simulatedOutcome
                )
            }
            _uiState.update {
                it.copy(
                    isVerifying = false,
                    verificationResult = result.getOrNull(),
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    private suspend fun <T> executeRepositoryResult(
        block: suspend () -> Result<T>
    ): Result<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
