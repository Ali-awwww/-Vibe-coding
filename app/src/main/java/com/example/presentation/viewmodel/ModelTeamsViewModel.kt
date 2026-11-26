package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ModelTeamsUiState(
    val isAuthenticated: Boolean = false,
    val isCloudConfigured: Boolean = true,
    val teams: List<ModelTeam> = emptyList(),
    val availableModels: List<AIModel> = emptyList(),
    val connectedProviderIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val error: String? = null,
    val statusMessage: String? = null,
    val isCreating: Boolean = false,
    val newTeamName: String = "",
    val newTeamDescription: String = "",
    val newTeamGoal: String = "",
    val nameError: String? = null,
    val membersError: String? = null,
    val selectedMembers: List<TeamMember> = emptyList(),
    val collaborationStyle: CollaborationStyle = CollaborationStyle.ORCHESTRATED,
    val settings: TeamSettings = TeamSettings(),
    val governance: TeamGovernanceSettings = TeamGovernanceSettings(),
    val pendingDeleteTeam: ModelTeam? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ModelTeamsViewModel(
    private val teamRepository: AIModelTeamRepository,
    private val aiModelRepository: AIModelRepository,
    private val authRepository: AuthRepository,
    isCloudConfigured: Boolean = true
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelTeamsUiState(isCloudConfigured = isCloudConfigured))
    val uiState: StateFlow<ModelTeamsUiState> = _uiState.asStateFlow()

    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    init {
        viewModelScope.launch {
            combine(authRepository.currentUser, refreshTrigger) { user, _ -> user }
                .flatMapLatest { user ->
                    if (user == null) {
                        flowOf(
                            TeamsBundle(
                                isAuthenticated = false,
                                teams = emptyList(),
                                models = emptyList(),
                                connectedProviderIds = emptySet(),
                                error = null
                            )
                        )
                    } else {
                        _uiState.update { it.copy(isAuthenticated = true, isLoading = true, error = null) }
                        combine(
                            teamRepository.getTeams(user.id),
                            aiModelRepository.getAllAvailableModels(user.id),
                            aiModelRepository.getProviders(user.id)
                        ) { teams, models, providers ->
                            val connectedIds = providers
                                .filter { it.status == AIProviderStatus.CONNECTED }
                                .map { it.id }
                                .toSet()
                            TeamsBundle(
                                isAuthenticated = true,
                                teams = teams,
                                models = models,
                                connectedProviderIds = connectedIds,
                                error = null
                            )
                        }.catch { e ->
                            if (e is CancellationException) throw e
                            emit(
                                TeamsBundle(
                                    isAuthenticated = true,
                                    teams = emptyList(),
                                    models = emptyList(),
                                    connectedProviderIds = emptySet(),
                                    error = e.message ?: "تعذّر تحميل بيانات الفرق."
                                )
                            )
                        }
                    }
                }
                .collect { bundle ->
                    _uiState.update { current ->
                        current.copy(
                            isAuthenticated = bundle.isAuthenticated,
                            teams = bundle.teams,
                            availableModels = bundle.models,
                            connectedProviderIds = bundle.connectedProviderIds,
                            isLoading = false,
                            error = bundle.error ?: current.error,
                            pendingDeleteTeam = if (bundle.isAuthenticated) current.pendingDeleteTeam else null,
                            isCreating = if (bundle.isAuthenticated) current.isCreating else false
                        )
                    }
                }
        }
    }

    private fun requireAuthenticatedUserId(): String? {
        val uid = authRepository.getCurrentUserSync()?.id?.takeIf { it.isNotBlank() }
        if (uid == null) {
            _uiState.update { it.copy(error = AUTH_REQUIRED_MESSAGE, isAuthenticated = false) }
            return null
        }
        return uid
    }

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    fun startCreateTeam() {
        if (requireAuthenticatedUserId() == null) return
        _uiState.update {
            it.copy(
                isCreating = true,
                nameError = null,
                membersError = null,
                error = null
            )
        }
    }

    fun updateNewTeamInfo(name: String, description: String, goal: String) {
        _uiState.update {
            it.copy(
                newTeamName = name,
                newTeamDescription = description,
                newTeamGoal = goal,
                nameError = null
            )
        }
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
        val currentState = _uiState.value
        val alreadyAssigned = currentState.selectedMembers.any {
            it.modelId == model.id && it.role == role
        }
        if (alreadyAssigned) {
            _uiState.update {
                it.copy(error = "تمت إضافة هذا النموذج بنفس الدور مسبقاً.")
            }
            return
        }

        val member = TeamMember(
            id = UUID.randomUUID().toString(),
            name = model.name,
            modelId = model.id,
            providerId = model.providerId,
            role = role,
            instructions = "You are a ${role.labelEn}. Assist the team in achieving the goal."
        )
        _uiState.update {
            it.copy(
                selectedMembers = it.selectedMembers + member,
                membersError = null,
                error = null
            )
        }
    }

    fun removeMemberFromNewTeam(memberId: String) {
        _uiState.update {
            it.copy(selectedMembers = it.selectedMembers.filter { member -> member.id != memberId })
        }
    }

    fun saveTeam() {
        val uid = requireAuthenticatedUserId() ?: return
        val state = _uiState.value
        if (state.isMutating) return

        val trimmedName = state.newTeamName.trim()
        val trimmedDescription = state.newTeamDescription.trim()
        val trimmedGoal = state.newTeamGoal.trim()

        if (trimmedName.isBlank()) {
            _uiState.update {
                it.copy(
                    nameError = "اسم الفريق مطلوب.",
                    error = "يرجى إدخال اسم الفريق قبل الحفظ."
                )
            }
            return
        }

        val duplicateExists = state.teams.any {
            it.name.trim().equals(trimmedName, ignoreCase = true)
        }
        if (duplicateExists) {
            _uiState.update {
                it.copy(
                    nameError = "يوجد فريق آخر بنفس الاسم بالفعل.",
                    error = "اختر اسماً مختلفاً للفريق لتجنب التكرار."
                )
            }
            return
        }

        if (state.selectedMembers.isEmpty()) {
            _uiState.update {
                it.copy(
                    membersError = "أضف عضواً واحداً على الأقل من النماذج المتاحة.",
                    error = "يرجى اختيار عضو واحد على الأقل للفريق."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isMutating = true, error = null) }
            val team = ModelTeam(
                id = UUID.randomUUID().toString(),
                name = trimmedName,
                description = trimmedDescription,
                goal = trimmedGoal,
                ownerId = uid,
                leaderMemberId = state.selectedMembers.firstOrNull()?.id,
                members = state.selectedMembers,
                collaborationStyle = state.collaborationStyle,
                settings = state.settings,
                governance = state.governance
            )
            teamRepository.createTeam(team)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            isMutating = false,
                            isLoading = false,
                            newTeamName = "",
                            newTeamDescription = "",
                            newTeamGoal = "",
                            nameError = null,
                            membersError = null,
                            selectedMembers = emptyList(),
                            statusMessage = "تم إنشاء الفريق \"$trimmedName\" وحفظه بنجاح."
                        )
                    }
                    refresh()
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isMutating = false,
                            error = e.message ?: "تعذّر حفظ الفريق."
                        )
                    }
                }
        }
    }

    fun requestDeleteTeam(team: ModelTeam) {
        if (requireAuthenticatedUserId() == null) return
        _uiState.update { it.copy(pendingDeleteTeam = team) }
    }

    fun dismissDeleteTeam() {
        _uiState.update { it.copy(pendingDeleteTeam = null) }
    }

    fun confirmDeleteTeam() {
        val team = _uiState.value.pendingDeleteTeam ?: return
        deleteTeam(team.id)
    }

    fun deleteTeam(teamId: String) {
        if (requireAuthenticatedUserId() == null) return
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, error = null) }
            teamRepository.deleteTeam(teamId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            pendingDeleteTeam = null,
                            statusMessage = "تم حذف الفريق بنجاح."
                        )
                    }
                    refresh()
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            pendingDeleteTeam = null,
                            error = e.message ?: "تعذّر حذف الفريق."
                        )
                    }
                }
        }
    }

    fun useTemplate(template: ModelTeam) {
        val uid = requireAuthenticatedUserId() ?: return
        if (_uiState.value.isMutating) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isMutating = true, error = null) }
            val existingNames = _uiState.value.teams.map { it.name.trim().lowercase() }.toSet()
            var candidateName = template.name.trim()
            if (candidateName.lowercase() in existingNames) {
                var suffix = 2
                while ("$candidateName ($suffix)".lowercase() in existingNames) {
                    suffix++
                }
                candidateName = "$candidateName ($suffix)"
            }

            val now = System.currentTimeMillis()
            val newMembers = template.members.map { member ->
                member.copy(id = UUID.randomUUID().toString())
            }
            val newTeam = template.copy(
                id = UUID.randomUUID().toString(),
                name = candidateName,
                ownerId = uid,
                leaderMemberId = newMembers.firstOrNull()?.id,
                members = newMembers,
                isTemplate = false,
                createdAt = now,
                updatedAt = now
            )
            teamRepository.createTeam(newTeam)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isMutating = false,
                            statusMessage = "تم تجهيز الفريق من القالب: $candidateName"
                        )
                    }
                    refresh()
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isMutating = false,
                            error = e.message ?: "تعذّر إنشاء الفريق من القالب."
                        )
                    }
                }
        }
    }

    fun cancelCreate() {
        _uiState.update {
            it.copy(
                isCreating = false,
                nameError = null,
                membersError = null,
                error = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    companion object {
        const val AUTH_REQUIRED_MESSAGE = "سجّل الدخول بحساب صالح لإدارة فرق النماذج."
    }
}

private data class TeamsBundle(
    val isAuthenticated: Boolean,
    val teams: List<ModelTeam>,
    val models: List<AIModel>,
    val connectedProviderIds: Set<String>,
    val error: String?
)
