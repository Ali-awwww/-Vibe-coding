package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.agent.TeamOrchestrator
import com.example.domain.model.*
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.repository.AuthRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TeamDetailUiState(
    val team: ModelTeam? = null,
    val executions: List<TeamExecution> = emptyList(),
    val currentExecution: TeamExecution? = null,
    val preEstimation: PreExecutionEstimation? = null,
    val showEstimationDialog: Boolean = false,
    val showGovernanceDialog: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val taskPrompt: String = ""
)

class TeamDetailViewModel(
    private val teamId: String,
    private val teamRepository: AIModelTeamRepository,
    private val teamOrchestrator: TeamOrchestrator,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamDetailUiState())
    val uiState: StateFlow<TeamDetailUiState> = _uiState.asStateFlow()

    private val currentUser = authRepository.currentUser.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            combine(
                flow { emit(teamRepository.getTeamById(teamId)) },
                teamRepository.getTeamExecutions(teamId)
            ) { team, executions ->
                _uiState.update { it.copy(
                    team = team,
                    executions = executions,
                    isLoading = false
                ) }
            }.collect()
        }
    }

    fun updateTaskPrompt(prompt: String) {
        _uiState.update { it.copy(taskPrompt = prompt) }
    }

    fun requestEstimation() {
        val team = _uiState.value.team ?: return
        val prompt = _uiState.value.taskPrompt
        if (prompt.isBlank()) return

        viewModelScope.launch {
            val est = teamOrchestrator.estimateTaskResources(team, prompt)
            _uiState.update { it.copy(preEstimation = est, showEstimationDialog = true) }
        }
    }

    fun dismissEstimation() {
        _uiState.update { it.copy(showEstimationDialog = false) }
    }

    fun setGovernanceDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showGovernanceDialog = visible) }
    }

    fun updateGovernance(settings: TeamGovernanceSettings) {
        val team = _uiState.value.team ?: return
        viewModelScope.launch {
            val updated = team.copy(governance = settings)
            teamRepository.updateTeam(updated)
            _uiState.update { it.copy(team = updated, showGovernanceDialog = false) }
        }
    }

    fun executeTeamTask() {
        val user = currentUser.value ?: return
        val team = _uiState.value.team ?: return
        val prompt = _uiState.value.taskPrompt
        if (prompt.isBlank()) return

        _uiState.update { it.copy(showEstimationDialog = false) }

        viewModelScope.launch {
            teamOrchestrator.executeTask(user.id, team, prompt).collect { execution ->
                _uiState.update { it.copy(currentExecution = execution) }
            }
        }
    }

    fun addMember(member: TeamMember) {
        viewModelScope.launch {
            teamRepository.addTeamMember(teamId, member)
        }
    }

    fun removeMember(memberId: String) {
        viewModelScope.launch {
            teamRepository.removeTeamMember(teamId, memberId)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
