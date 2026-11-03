package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FixHistoryUiState(
    val verifications: List<CrashVerificationRecord> = emptyList(),
    val filteredVerifications: List<CrashVerificationRecord> = emptyList(),
    val projects: List<Project> = emptyList(),
    val selectedProjectId: String? = null,
    val selectedStatus: VerificationStatus? = null,
    val selectedRecord: CrashVerificationRecord? = null,
    val isLoading: Boolean = false,
    val isVerifying: Boolean = false,
    val error: String? = null
)

class FixHistoryViewModel(
    private val repository: CrashRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FixHistoryUiState(isLoading = true))
    val uiState: StateFlow<FixHistoryUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData(initialProjectId: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, selectedProjectId = initialProjectId) }

            launch {
                repository.getProjects().collect { projects ->
                    _uiState.update { it.copy(projects = projects) }
                }
            }

            launch {
                val flow = if (initialProjectId != null) {
                    repository.getVerificationsByProject(initialProjectId)
                } else {
                    repository.getAllVerifications()
                }

                flow.collect { list ->
                    _uiState.update { state ->
                        state.copy(
                            verifications = list,
                            filteredVerifications = applyFilter(list, state.selectedStatus),
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    fun selectProject(projectId: String?) {
        _uiState.update { it.copy(selectedProjectId = projectId, isLoading = true) }
        viewModelScope.launch {
            val flow = if (projectId != null) {
                repository.getVerificationsByProject(projectId)
            } else {
                repository.getAllVerifications()
            }
            flow.collect { list ->
                _uiState.update { state ->
                    state.copy(
                        verifications = list,
                        filteredVerifications = applyFilter(list, state.selectedStatus),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun filterByStatus(status: VerificationStatus?) {
        _uiState.update { state ->
            state.copy(
                selectedStatus = status,
                filteredVerifications = applyFilter(state.verifications, status)
            )
        }
    }

    fun selectRecord(record: CrashVerificationRecord?) {
        _uiState.update { it.copy(selectedRecord = record) }
    }

    fun reVerifyFix(
        crashId: String,
        fixSuggestionId: String?,
        newVersion: String,
        buildId: String,
        scenarioName: String,
        simulatedOutcome: VerificationStatus? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifying = true, error = null) }
            val result = repository.verifyFix(
                crashId = crashId,
                fixSuggestionId = fixSuggestionId,
                newVersion = newVersion,
                buildId = buildId,
                scenarioName = scenarioName,
                simulatedOutcome = simulatedOutcome
            )
            _uiState.update { state ->
                state.copy(
                    isVerifying = false,
                    selectedRecord = result.getOrNull(),
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    private fun applyFilter(list: List<CrashVerificationRecord>, status: VerificationStatus?): List<CrashVerificationRecord> {
        return if (status == null) list else list.filter { it.status == status }
    }
}
