package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import com.example.domain.model.*
import com.example.domain.repository.CrashRepository
import com.example.domain.repository.SymbolicationRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CrashDetailUiState(
    val crash: CrashReport? = null,
    val diagnosis: AIDiagnosis? = null,
    val symbolication: SymbolicationReport? = null,
    val isLoading: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isSymbolicating: Boolean = false,
    val teamExecution: TeamExecution? = null,
    val isInvestigatingWithTeam: Boolean = false,
    val showTeamDialog: Boolean = false,
    val error: String? = null
)

class CrashDetailViewModel(
    private val repository: CrashRepository,
    private val symbolicationRepository: SymbolicationRepository,
    private val teamOrchestrator: com.example.domain.agent.TeamOrchestrator? = null,
    private val authRepository: com.example.domain.repository.AuthRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CrashDetailUiState())
    val uiState: StateFlow<CrashDetailUiState> = _uiState.asStateFlow()

    fun loadCrash(crashId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getCrashDetails(crashId).collect { crash ->
                _uiState.update { it.copy(crash = crash, isLoading = false) }
                if (crash != null) {
                    symbolicateCrash()
                }
            }
        }
    }

    fun symbolicateCrash() {
        val crash = _uiState.value.crash ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSymbolicating = true) }
            val result = symbolicationRepository.symbolicate(crash)
            _uiState.update { 
                it.copy(
                    symbolication = result.getOrNull(),
                    isSymbolicating = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun analyzeCrash() {
        val crash = _uiState.value.crash ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true) }
            try {
                val diagnosis = repository.getAIDiagnosis(crash)
                _uiState.update { it.copy(diagnosis = diagnosis, isAnalyzing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAnalyzing = false, error = e.message) }
            }
        }
    }

    fun resolveCrash() {
        val crashId = _uiState.value.crash?.id ?: return
        viewModelScope.launch {
            repository.updateCrashStatus(crashId, true)
        }
    }

    fun setShowTeamDialog(show: Boolean) {
        _uiState.update { it.copy(showTeamDialog = show) }
    }

    fun investigateWithCrashTeam(orchestrator: com.example.domain.agent.TeamOrchestrator? = null) {
        val crash = _uiState.value.crash ?: return
        val orch = orchestrator ?: teamOrchestrator
        if (orch == null) {
            _uiState.update {
                it.copy(isInvestigatingWithTeam = false, error = "خدمة فريق التحقيق غير مهيأة.")
            }
            return
        }

        viewModelScope.launch {
            val userId = authenticatedUserIdOrNull(authRepository?.currentUser?.firstOrNull())
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
            val prompt = "crashId: ${crash.id}\n${crash.exceptionType}: ${crash.message}\n${crash.stackTrace}"

            try {
                orch.executeTask(userId, TeamTemplates.CRASH_INVESTIGATION_TEAM, prompt).collect { exec ->
                    _uiState.update {
                        it.copy(
                            teamExecution = exec,
                            isInvestigatingWithTeam = exec.status == ExecutionStatus.EXECUTING
                        )
                    }
                    if (exec.status == ExecutionStatus.COMPLETED && exec.finalResult != null) {
                        // Refresh diagnosis in background
                        repository.getAIDiagnosis(crash)
                    }
                }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(isInvestigatingWithTeam = false) }
                throw cancelled
            } catch (e: Exception) {
                _uiState.update { it.copy(isInvestigatingWithTeam = false, error = e.message) }
            }
        }
    }
}
