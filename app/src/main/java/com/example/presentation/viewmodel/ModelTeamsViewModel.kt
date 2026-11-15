package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.repository.AuthRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ModelTeamsUiState(
    val teams: List<ModelTeam> = emptyList(),
    val availableModels: List<AIModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isCreating: Boolean = false,
    val newTeamName: String = "",
    val newTeamDescription: String = "",
    val newTeamGoal: String = "",
    val selectedMembers: List<TeamMember> = emptyList(),
    val collaborationStyle: CollaborationStyle = CollaborationStyle.ORCHESTRATED,
    val settings: TeamSettings = TeamSettings(),
    val governance: TeamGovernanceSettings = TeamGovernanceSettings()
)

class ModelTeamsViewModel(
    private val teamRepository: AIModelTeamRepository,
    private val aiModelRepository: AIModelRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelTeamsUiState())
    val uiState: StateFlow<ModelTeamsUiState> = _uiState.asStateFlow()

    private val currentUser = authRepository.currentUser.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            currentUser.filterNotNull().collectLatest { user ->
                combine(
                    teamRepository.getTeams(user.id),
                    aiModelRepository.getAllAvailableModels(user.id)
                ) { teams, models ->
                    _uiState.update { it.copy(
                        teams = teams,
                        availableModels = models,
                        isLoading = false
                    ) }
                }.collect()
            }
        }
    }

    fun startCreateTeam() {
        _uiState.update { it.copy(isCreating = true) }
    }

    fun updateNewTeamInfo(name: String, description: String, goal: String) {
        _uiState.update { it.copy(newTeamName = name, newTeamDescription = description, newTeamGoal = goal) }
    }

    fun updateCollaborationStyle(style: CollaborationStyle) {
        _uiState.update { it.copy(collaborationStyle = style) }
    }

    fun updateTeamSettings(settings: TeamSettings) {
        _uiState.update { it.copy(settings = settings) }
    }

    fun updateTeamGovernance(governance: TeamGovernanceSettings) {
        _uiState.update { it.copy(governance = governance) }
    }

    fun addMemberToNewTeam(model: AIModel, role: TeamRole) {
        val member = TeamMember(
            id = UUID.randomUUID().toString(),
            name = model.name,
            modelId = model.id,
            providerId = model.providerId,
            role = role,
            instructions = "You are a ${role.labelEn}. Assist the team in achieving the goal."
        )
        _uiState.update { it.copy(selectedMembers = it.selectedMembers + member) }
    }

    fun removeMemberFromNewTeam(memberId: String) {
        _uiState.update { it.copy(selectedMembers = it.selectedMembers.filter { it.id != memberId }) }
    }

    fun saveTeam() {
        val user = currentUser.value ?: return
        val state = _uiState.value
        if (state.newTeamName.isBlank()) {
            _uiState.update { it.copy(error = "Team name is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val team = ModelTeam(
                id = UUID.randomUUID().toString(),
                name = state.newTeamName,
                description = state.newTeamDescription,
                goal = state.newTeamGoal,
                ownerId = user.id,
                leaderMemberId = state.selectedMembers.firstOrNull()?.id,
                members = state.selectedMembers,
                collaborationStyle = state.collaborationStyle,
                settings = state.settings,
                governance = state.governance
            )
            teamRepository.createTeam(team)
                .onSuccess { 
                    _uiState.update { it.copy(isCreating = false, newTeamName = "", newTeamDescription = "", newTeamGoal = "", selectedMembers = emptyList()) }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun deleteTeam(teamId: String) {
        viewModelScope.launch {
            teamRepository.deleteTeam(teamId)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun useTemplate(template: ModelTeam) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val newTeam = template.copy(
                id = UUID.randomUUID().toString(),
                ownerId = user.id,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            teamRepository.createTeam(newTeam)
                .onSuccess { 
                     // Refresh handled by collectLatest in init
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun cancelCreate() {
        _uiState.update { it.copy(isCreating = false) }
    }
}
