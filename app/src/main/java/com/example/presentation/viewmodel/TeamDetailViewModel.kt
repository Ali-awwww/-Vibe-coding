package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.agent.TeamOrchestrator
import com.example.domain.model.*
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.repository.AuthRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TeamDetailUiState(
    val userId: String? = null,
    val isAuthenticated: Boolean = false,
    val team: ModelTeam? = null,
    val executions: List<TeamExecution> = emptyList(),
    val currentExecution: TeamExecution? = null,
    val preEstimation: PreExecutionEstimation? = null,
    val showEstimationDialog: Boolean = false,
    val showGovernanceDialog: Boolean = false,
    val isLoading: Boolean = true,
    val isEstimating: Boolean = false,
    val isExecuting: Boolean = false,
    val isMutating: Boolean = false,
    val loadError: String? = null,
    val error: String? = null,
    val taskPrompt: String = ""
)

private sealed interface TeamDetailLoadResult {
    data class Loading(val userId: String) : TeamDetailLoadResult
    data object Unauthenticated : TeamDetailLoadResult
    data class Loaded(
        val userId: String,
        val team: ModelTeam?,
        val executions: List<TeamExecution>
    ) : TeamDetailLoadResult
    data class Failed(val cause: Throwable) : TeamDetailLoadResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class TeamDetailViewModel(
    private val teamId: String,
    private val teamRepository: AIModelTeamRepository,
    private val teamOrchestrator: TeamOrchestrator,
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        const val AUTH_REQUIRED_MESSAGE = "يجب تسجيل الدخول بحساب صالح لعرض الفريق أو تعديله."
        private const val INVALID_TEAM_MESSAGE = "معرف الفريق غير صالح."
        private const val TEAM_NOT_FOUND_MESSAGE = "لم يتم العثور على الفريق أو لم تعد لديك صلاحية الوصول إليه."
    }

    private val _uiState = MutableStateFlow(TeamDetailUiState())
    val uiState: StateFlow<TeamDetailUiState> = _uiState.asStateFlow()
    private val currentUser = authRepository.currentUser.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        if (teamId.isBlank()) {
            _uiState.value = TeamDetailUiState(isLoading = false, loadError = INVALID_TEAM_MESSAGE)
        } else {
            observeTeam()
        }
    }

    private fun observeTeam() {
        viewModelScope.launch {
            authRepository.currentUser
                .distinctUntilChanged()
                .flatMapLatest { account ->
                    val userId = account?.id?.trim()?.takeIf(String::isNotEmpty)
                    if (userId == null) {
                        flowOf(TeamDetailLoadResult.Unauthenticated)
                    } else {
                        refreshRequests
                            .onStart { emit(Unit) }
                            .flatMapLatest { loadTeamFlow(userId) }
                    }
                }
                .collect(::applyLoadResult)
        }
    }

    private fun loadTeamFlow(userId: String): Flow<TeamDetailLoadResult> =
        combine(
            flow { emit(teamRepository.getTeamById(teamId)) },
            teamRepository.getTeamExecutions(teamId)
        ) { team, executions ->
            TeamDetailLoadResult.Loaded(userId, team, executions)
        }
            .onStart { emit(TeamDetailLoadResult.Loading(userId)) }
            .catch { cause ->
                if (cause is CancellationException) throw cause
                emit(TeamDetailLoadResult.Failed(cause))
            }

    private fun applyLoadResult(result: TeamDetailLoadResult) {
        when (result) {
            is TeamDetailLoadResult.Loading -> {
                _uiState.update { current ->
                    val accountChanged = current.userId != result.userId
                    current.copy(
                        userId = result.userId,
                        isAuthenticated = true,
                        isLoading = true,
                        team = if (accountChanged) null else current.team,
                        executions = if (accountChanged) emptyList() else current.executions,
                        currentExecution = if (accountChanged) null else current.currentExecution,
                        loadError = null,
                        error = null
                    )
                }
            }

            TeamDetailLoadResult.Unauthenticated -> {
                _uiState.value = TeamDetailUiState(
                    isAuthenticated = false,
                    isLoading = false,
                    loadError = AUTH_REQUIRED_MESSAGE
                )
            }

            is TeamDetailLoadResult.Loaded -> {
                _uiState.update { current ->
                    val activeExecution = current.currentExecution
                        ?.takeIf { existing -> result.executions.any { it.id == existing.id } }
                        ?: result.executions.firstOrNull {
                            it.status == ExecutionStatus.EXECUTING || it.status == ExecutionStatus.PAUSED
                        }
                    current.copy(
                        userId = result.userId,
                        isAuthenticated = true,
                        team = result.team,
                        executions = result.executions,
                        currentExecution = activeExecution,
                        isLoading = false,
                        loadError = if (result.team == null) TEAM_NOT_FOUND_MESSAGE else null
                    )
                }
            }

            is TeamDetailLoadResult.Failed -> {
                val message = userFacingError(result.cause, "تعذر تحميل بيانات الفريق. حاول إعادة المحاولة.")
                _uiState.update { current ->
                    current.copy(
                        isAuthenticated = true,
                        isLoading = false,
                        loadError = if (current.team == null) message else null,
                        error = if (current.team != null) message else null
                    )
                }
            }
        }
    }

    fun refresh() {
        if (!_uiState.value.isAuthenticated) {
            _uiState.update { it.copy(error = AUTH_REQUIRED_MESSAGE) }
            return
        }
        _uiState.update { it.copy(isLoading = true, loadError = null, error = null) }
        refreshRequests.tryEmit(Unit)
    }

    fun updateTaskPrompt(prompt: String) {
        _uiState.update { it.copy(taskPrompt = prompt) }
    }

    fun requestEstimation() {
        val userId = authenticatedUserId() ?: return
        val team = _uiState.value.team ?: return
        val prompt = _uiState.value.taskPrompt.trim()
        if (prompt.isEmpty()) {
            _uiState.update { it.copy(error = "أدخل وصف المهمة أولاً.") }
            return
        }
        if (_uiState.value.isEstimating) return

        viewModelScope.launch {
            _uiState.update { it.copy(isEstimating = true, error = null) }
            try {
                val estimation = teamOrchestrator.estimateTaskResources(userId, team, prompt)
                _uiState.update { it.copy(preEstimation = estimation, showEstimationDialog = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Throwable) {
                _uiState.update {
                    it.copy(error = userFacingError(cause, "تعذر تقدير موارد المهمة. حاول مجدداً."))
                }
            } finally {
                _uiState.update { it.copy(isEstimating = false) }
            }
        }
    }

    fun dismissEstimation() {
        _uiState.update { it.copy(showEstimationDialog = false, preEstimation = null) }
    }

    fun setGovernanceDialogVisible(visible: Boolean) {
        _uiState.update { current ->
            current.copy(showGovernanceDialog = visible && current.team != null && current.isAuthenticated)
        }
    }

    fun updateGovernance(settings: TeamGovernanceSettings) {
        val team = _uiState.value.team ?: return
        updateTeamMutation(
            fallback = "تعذر حفظ إعدادات الحوكمة. لم يتم تغييرها.",
            mutation = { teamRepository.updateTeam(team.copy(governance = settings)) },
            transform = { it.copy(governance = settings) },
            closeGovernanceDialog = true
        )
    }

    fun executeTeamTask() {
        val userId = authenticatedUserId() ?: return
        val team = _uiState.value.team ?: return
        val prompt = _uiState.value.taskPrompt.trim()
        if (prompt.isEmpty()) {
            _uiState.update { it.copy(error = "أدخل وصف المهمة أولاً.") }
            return
        }
        if (_uiState.value.isExecuting) return

        _uiState.update { it.copy(showEstimationDialog = false, error = null, isExecuting = true) }
        viewModelScope.launch {
            try {
                teamOrchestrator.executeTask(userId, team, prompt).collect { execution ->
                    _uiState.update { it.copy(currentExecution = execution) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Throwable) {
                _uiState.update {
                    it.copy(error = userFacingError(cause, "تعذر بدء تنفيذ المهمة. لم يتم تأكيد بدء التنفيذ."))
                }
            } finally {
                _uiState.update { it.copy(isExecuting = false) }
            }
        }
    }

    fun addMember(member: TeamMember) {
        if (member.id.isBlank()) {
            _uiState.update { it.copy(error = "تعذر إضافة عضو بلا معرّف صالح.") }
            return
        }
        updateTeamMutation(
            fallback = "تعذرت إضافة العضو إلى الفريق.",
            mutation = { teamRepository.addTeamMember(teamId, member) },
            transform = { team ->
                if (team.members.any { it.id == member.id }) team
                else team.copy(members = team.members + member)
            }
        )
    }

    fun removeMember(memberId: String) {
        if (memberId.isBlank()) return
        updateTeamMutation(
            fallback = "تعذر حذف العضو من الفريق.",
            mutation = { teamRepository.removeTeamMember(teamId, memberId) },
            transform = { team -> team.copy(members = team.members.filterNot { it.id == memberId }) }
        )
    }

    private fun updateTeamMutation(
        fallback: String,
        mutation: suspend () -> Result<Unit>,
        transform: (ModelTeam) -> ModelTeam,
        closeGovernanceDialog: Boolean = false
    ) {
        if (authenticatedUserId() == null || _uiState.value.isMutating) return

        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, error = null) }
            try {
                mutation().getOrThrow()
                _uiState.update { current ->
                    current.copy(
                        team = current.team?.let(transform),
                        showGovernanceDialog = if (closeGovernanceDialog) false else current.showGovernanceDialog
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Throwable) {
                _uiState.update { it.copy(error = userFacingError(cause, fallback)) }
            } finally {
                _uiState.update { it.copy(isMutating = false) }
            }
        }
    }

    private fun authenticatedUserId(): String? {
        val userId = currentUser.value?.id?.trim()?.takeIf(String::isNotEmpty)
        if (userId == null) _uiState.update { it.copy(error = AUTH_REQUIRED_MESSAGE) }
        return userId
    }

    private fun userFacingError(cause: Throwable, fallback: String): String = when (cause) {
        is SecurityException -> "لا تملك صلاحية الوصول إلى هذا الفريق أو تعديل بياناته."
        is NoSuchElementException -> TEAM_NOT_FOUND_MESSAGE
        is IOException -> "تعذر الاتصال بالخدمة. تحقق من الاتصال ثم أعد المحاولة."
        else -> fallback
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
