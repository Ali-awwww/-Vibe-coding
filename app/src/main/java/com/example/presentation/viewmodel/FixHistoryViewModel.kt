package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    private var verificationJob: Job? = null
    private var loadGeneration = 0L

    /**
     * The screen owns initial loading. Repeated calls replace the previous collector,
     * so project changes and retries cannot accumulate subscriptions.
     */
    fun loadData(initialProjectId: String? = null) {
        val generation = ++loadGeneration
        val projectChanged = _uiState.value.selectedProjectId != initialProjectId

        _uiState.update { state ->
            state.copy(
                selectedProjectId = initialProjectId,
                verifications = if (projectChanged) emptyList() else state.verifications,
                filteredVerifications = if (projectChanged) emptyList() else state.filteredVerifications,
                isLoading = true,
                error = null
            )
        }

        verificationJob?.cancel()
        verificationJob = viewModelScope.launch {
            var receivedEmission = false
            try {
                val source = if (initialProjectId != null) {
                    repository.getVerificationsByProject(initialProjectId)
                } else {
                    repository.getAllVerifications()
                }

                source.collect { list ->
                    receivedEmission = true
                    _uiState.update { state ->
                        if (generation != loadGeneration) {
                            state
                        } else {
                            state.copy(
                                verifications = list,
                                filteredVerifications = applyFilter(list, state.selectedStatus),
                                isLoading = false,
                                error = null
                            )
                        }
                    }
                }

                if (!receivedEmission) {
                    _uiState.update { state ->
                        if (generation == loadGeneration) state.copy(isLoading = false) else state
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update { state ->
                    if (generation != loadGeneration) {
                        state
                    } else {
                        state.copy(
                            isLoading = false,
                            error = failure.message ?: "تعذر تحميل سجل التحقق. حاول مرة أخرى."
                        )
                    }
                }
            }
        }
    }

    fun selectProject(projectId: String?) {
        loadData(projectId)
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
            try {
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update {
                    it.copy(
                        isVerifying = false,
                        error = failure.message ?: "تعذر إعادة التحقق من الإصلاح."
                    )
                }
            }
        }
    }

    private fun applyFilter(
        list: List<CrashVerificationRecord>,
        status: VerificationStatus?
    ): List<CrashVerificationRecord> {
        return if (status == null) list else list.filter { it.status == status }
    }
}
