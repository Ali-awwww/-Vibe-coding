package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.CrashRepository
import com.example.domain.repository.SymbolicationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CrashDetailUiState(
    val crash: CrashReport? = null,
    val diagnosis: AIDiagnosis? = null,
    val symbolication: SymbolicationReport? = null,
    val isLoading: Boolean = true,
    val isAnalyzing: Boolean = false,
    val isSymbolicating: Boolean = false,
    val teamExecution: TeamExecution? = null,
    val isInvestigatingWithTeam: Boolean = false,
    val showTeamDialog: Boolean = false,
    val error: String? = null,
    val notFound: Boolean = false,
    val loadError: Boolean = false,
    val isResolving: Boolean = false,
    val symbolicationError: String? = null
)

class CrashDetailViewModel(
    private val repository: CrashRepository,
    private val symbolicationRepository: SymbolicationRepository,
    private val teamOrchestrator: com.example.domain.agent.TeamOrchestrator? = null,
    private val authRepository: com.example.domain.repository.AuthRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CrashDetailUiState())
    val uiState: StateFlow<CrashDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var symbolicationJob: Job? = null
    private var analysisJob: Job? = null
    private var resolveJob: Job? = null
    private var investigationJob: Job? = null
    private var loadGeneration = 0
    private var symbolicationGeneration = 0
    private var symbolicationFingerprint: Pair<String, String>? = null

    fun loadCrash(crashId: String) {
        val generation = ++loadGeneration
        loadJob?.cancel()
        symbolicationGeneration++
        symbolicationJob?.cancel()
        analysisJob?.cancel()
        resolveJob?.cancel()
        investigationJob?.cancel()
        symbolicationFingerprint = null
        _uiState.value = CrashDetailUiState(isLoading = true)

        loadJob = viewModelScope.launch {
            var receivedSnapshot = false
            try {
                repository.getCrashDetails(crashId).collect { crash ->
                    if (generation != loadGeneration) return@collect
                    receivedSnapshot = true

                    if (crash == null) {
                        symbolicationGeneration++
                        symbolicationJob?.cancel()
                        symbolicationJob = null
                        symbolicationFingerprint = null
                        _uiState.update {
                            it.copy(
                                crash = null,
                                isLoading = false,
                                isSymbolicating = false,
                                symbolication = null,
                                symbolicationError = null,
                                notFound = true,
                                loadError = false
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                crash = crash,
                                isLoading = false,
                                notFound = false,
                                loadError = false
                            )
                        }
                        val fingerprint = crash.id to crash.stackTrace
                        if (fingerprint != symbolicationFingerprint) {
                            symbolicationFingerprint = fingerprint
                            startSymbolication(crash, generation)
                        }
                    }
                }

                if (!receivedSnapshot && generation == loadGeneration) {
                    _uiState.update { it.copy(isLoading = false, notFound = true, loadError = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generation == loadGeneration) {
                    _uiState.update { current ->
                        if (current.crash == null) {
                            current.copy(isLoading = false, notFound = false, loadError = true)
                        } else {
                            current.copy(
                                isLoading = false,
                                error = failure.message ?: "Crash details could not be refreshed."
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startSymbolication(crash: CrashReport, expectedLoadGeneration: Int) {
        val requestGeneration = ++symbolicationGeneration
        symbolicationJob?.cancel()
        _uiState.update {
            it.copy(symbolication = null, isSymbolicating = true, symbolicationError = null, error = null)
        }

        symbolicationJob = viewModelScope.launch {
            try {
                val result = symbolicationRepository.symbolicate(crash)
                if (expectedLoadGeneration != loadGeneration ||
                    requestGeneration != symbolicationGeneration
                ) return@launch

                _uiState.update { current ->
                    val currentCrash = current.crash
                    if (currentCrash == null || currentCrash.id != crash.id || currentCrash.stackTrace != crash.stackTrace) {
                        current
                    } else {
                        current.copy(
                            symbolication = result.getOrNull(),
                            isSymbolicating = false,
                            symbolicationError = result.exceptionOrNull()?.message
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                if (expectedLoadGeneration == loadGeneration &&
                    requestGeneration == symbolicationGeneration
                ) {
                    _uiState.update { it.copy(isSymbolicating = false) }
                }
                throw cancelled
            } catch (failure: Exception) {
                if (expectedLoadGeneration == loadGeneration &&
                    requestGeneration == symbolicationGeneration
                ) {
                    _uiState.update {
                        it.copy(isSymbolicating = false, symbolicationError = failure.message ?: "Symbolication failed.")
                    }
                }
            }
        }
    }

    fun symbolicateCrash() {
        val crash = _uiState.value.crash ?: return
        symbolicationFingerprint = crash.id to crash.stackTrace
        startSymbolication(crash, loadGeneration)
    }

    fun analyzeCrash() {
        val crash = _uiState.value.crash ?: return
        if (_uiState.value.isAnalyzing) return
        val generation = loadGeneration
        analysisJob?.cancel()
        _uiState.update { it.copy(isAnalyzing = true, error = null) }

        analysisJob = viewModelScope.launch {
            try {
                val diagnosis = repository.getAIDiagnosis(crash)
                if (generation == loadGeneration) {
                    _uiState.update { it.copy(diagnosis = diagnosis, isAnalyzing = false) }
                }
            } catch (cancelled: CancellationException) {
                if (generation == loadGeneration) {
                    _uiState.update { it.copy(isAnalyzing = false) }
                }
                throw cancelled
            } catch (failure: Exception) {
                if (generation == loadGeneration) {
                    _uiState.update {
                        it.copy(isAnalyzing = false, error = failure.message ?: "Crash analysis failed.")
                    }
                }
            }
        }
    }

    fun resolveCrash() {
        val crashId = _uiState.value.crash?.id ?: return
        if (_uiState.value.isResolving) return
        val generation = loadGeneration
        resolveJob?.cancel()
        _uiState.update { it.copy(isResolving = true, error = null) }

        resolveJob = viewModelScope.launch {
            try {
                repository.updateCrashStatus(crashId, true)
                if (generation == loadGeneration) {
                    _uiState.update { it.copy(isResolving = false) }
                }
            } catch (cancelled: CancellationException) {
                if (generation == loadGeneration) {
                    _uiState.update { it.copy(isResolving = false) }
                }
                throw cancelled
            } catch (failure: Exception) {
                if (generation == loadGeneration) {
                    _uiState.update {
                        it.copy(
                            isResolving = false,
                            error = failure.message ?: "The crash could not be marked as resolved."
                        )
                    }
                }
            }
        }
    }

    fun setShowTeamDialog(show: Boolean) {
        _uiState.update { it.copy(showTeamDialog = show) }
    }

    fun investigateWithCrashTeam(orchestrator: com.example.domain.agent.TeamOrchestrator? = null) {
        val crash = _uiState.value.crash ?: return
        if (_uiState.value.isInvestigatingWithTeam) return
        val orch = orchestrator ?: teamOrchestrator
        if (orch == null) {
            _uiState.update {
                it.copy(isInvestigatingWithTeam = false, error = "خدمة فريق التحقيق غير مهيأة.")
            }
            return
        }

        val generation = loadGeneration
        investigationJob?.cancel()
        investigationJob = viewModelScope.launch {
            try {
                val userId = authenticatedUserIdOrNull(authRepository?.currentUser?.firstOrNull())
                if (generation != loadGeneration) return@launch
                if (userId == null) {
                    _uiState.update {
                        it.copy(
                            isInvestigatingWithTeam = false,
                            error = "سجّل الدخول بحساب صالح لبدء تحقيق الفريق."
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(isInvestigatingWithTeam = true, showTeamDialog = true, error = null)
                }
                val prompt = "crashId: " + crash.id + "\n" +
                    crash.exceptionType + ": " + crash.message + "\n" + crash.stackTrace

                orch.executeTask(userId, TeamTemplates.CRASH_INVESTIGATION_TEAM, prompt).collect { exec ->
                    if (generation != loadGeneration) return@collect
                    _uiState.update {
                        it.copy(
                            teamExecution = exec,
                            isInvestigatingWithTeam = exec.status == ExecutionStatus.EXECUTING
                        )
                    }
                    if (exec.status == ExecutionStatus.COMPLETED && exec.finalResult != null) {
                        repository.getAIDiagnosis(crash)
                    }
                }
            } catch (cancelled: CancellationException) {
                if (generation == loadGeneration) {
                    _uiState.update { it.copy(isInvestigatingWithTeam = false) }
                }
                throw cancelled
            } catch (failure: Exception) {
                if (generation == loadGeneration) {
                    _uiState.update {
                        it.copy(
                            isInvestigatingWithTeam = false,
                            error = failure.message ?: "Team investigation failed."
                        )
                    }
                }
            }
        }
    }
}
