package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.agent.TeamOrchestrator
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class TeamChatTab(val labelAr: String, val labelEn: String) {
    CHAT_INPUT("المحادثة والمهمة", "Chat & Task"),
    LIVE_COLLABORATION("مراقبة الفريق الحية", "Live Collaboration"),
    ACTIVITY_LOG("سجل النقاش والتنسيق", "Activity Log"),
    INSTRUCTIONS("إرسال التعليمات", "Instructions"),
    RESULTS_COMPARE("مراجعة النتائج والمقارنة", "Results & Compare")
}

data class TeamChatUiState(
    val isAuthenticated: Boolean = false,
    val isCloudConfigured: Boolean = true,
    val isLoading: Boolean = false,
    val teams: List<ModelTeam> = emptyList(),
    val selectedTeam: ModelTeam? = null,
    val projects: List<Project> = emptyList(),
    val selectedProject: Project? = null,
    val availableModels: List<AIModel> = emptyList(),
    val taskPrompt: String = "",
    val attachedFiles: List<AttachedFileContext> = emptyList(),
    val currentExecution: TeamExecution? = null,
    val executionHistory: List<TeamExecution> = emptyList(),
    val activeTab: TeamChatTab = TeamChatTab.CHAT_INPUT,
    val isExecuting: Boolean = false,
    val isPaused: Boolean = false,
    val executionDurationSeconds: Long = 0,
    val preEstimation: PreExecutionEstimation? = null,
    val showEstimationDialog: Boolean = false,
    val showAttachmentDialog: Boolean = false,
    val showAssignDialog: Boolean = false,
    val showChangeAgentDialog: Boolean = false,
    val showInstructionDialog: Boolean = false,
    val compareMemberAId: String? = null,
    val compareMemberBId: String? = null,
    val statusMessage: String? = null,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class TeamChatViewModel(
    private val initialTeamId: String?,
    private val initialProjectId: String?,
    private val teamRepository: AIModelTeamRepository,
    private val teamOrchestrator: TeamOrchestrator,
    private val crashRepository: CrashRepository,
    private val aiModelRepository: AIModelRepository,
    private val authRepository: AuthRepository,
    isCloudConfigured: Boolean = true
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamChatUiState(isCloudConfigured = isCloudConfigured))
    val uiState: StateFlow<TeamChatUiState> = _uiState.asStateFlow()

    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    private var executionJob: Job? = null
    private var executionHistoryJob: Job? = null
    private var timerJob: Job? = null

    private fun getUserId(): String? = authRepository.getCurrentUserSync()?.id?.takeIf { it.isNotBlank() }

    init {
        observeAuthenticatedWorkspace()
    }

    private fun observeAuthenticatedWorkspace() {
        viewModelScope.launch {
            combine(authRepository.currentUser, refreshTrigger) { user, _ -> user }
                .flatMapLatest { user ->
                    if (user == null) {
                        executionHistoryJob?.cancel()
                        executionJob?.cancel()
                        stopTimer()
                        flowOf(
                            TeamChatBootstrap(
                                isAuthenticated = false,
                                teams = emptyList(),
                                projects = emptyList(),
                                models = emptyList(),
                                error = null
                            )
                        )
                    } else {
                        _uiState.update { it.copy(isAuthenticated = true, isLoading = true, error = null) }
                        combine(
                            teamRepository.getTeams(user.id),
                            crashRepository.getProjectsForUser(user.id),
                            aiModelRepository.getAllAvailableModels(user.id, false)
                        ) { teams, projects, models ->
                            TeamChatBootstrap(
                                isAuthenticated = true,
                                teams = teams,
                                projects = projects,
                                models = models,
                                error = null
                            )
                        }.catch { e ->
                            if (e is CancellationException) throw e
                            emit(
                                TeamChatBootstrap(
                                    isAuthenticated = true,
                                    teams = emptyList(),
                                    projects = emptyList(),
                                    models = emptyList(),
                                    error = e.message ?: "تعذّر تحميل بيانات محادثة الفريق."
                                )
                            )
                        }
                    }
                }
                .collect { bootstrap ->
                    if (!bootstrap.isAuthenticated) {
                        _uiState.update {
                            it.copy(
                                isAuthenticated = false,
                                isLoading = false,
                                teams = emptyList(),
                                selectedTeam = null,
                                projects = emptyList(),
                                selectedProject = null,
                                availableModels = emptyList(),
                                currentExecution = null,
                                executionHistory = emptyList(),
                                isExecuting = false,
                                isPaused = false,
                                executionDurationSeconds = 0
                            )
                        }
                        return@collect
                    }

                    val previousTeamId = _uiState.value.selectedTeam?.id
                    val resolvedTeam = resolveSelectedTeam(
                        teamsList = bootstrap.teams,
                        currentSelectedId = previousTeamId
                    )
                    val resolvedProject = resolveSelectedProject(
                        projectsList = bootstrap.projects,
                        currentSelectedId = _uiState.value.selectedProject?.id
                    )

                    _uiState.update { current ->
                        current.copy(
                            isAuthenticated = true,
                            isLoading = false,
                            teams = bootstrap.teams,
                            selectedTeam = resolvedTeam,
                            projects = bootstrap.projects,
                            selectedProject = resolvedProject,
                            availableModels = bootstrap.models,
                            error = bootstrap.error ?: current.error
                        )
                    }

                    if (resolvedTeam != null && resolvedTeam.id != previousTeamId) {
                        loadTeamExecutions(resolvedTeam.id)
                    } else if (resolvedTeam == null) {
                        executionHistoryJob?.cancel()
                    }
                }
        }
    }

    private fun resolveSelectedTeam(
        teamsList: List<ModelTeam>,
        currentSelectedId: String?
    ): ModelTeam? {
        if (currentSelectedId != null) {
            teamsList.find { it.id == currentSelectedId }?.let { return it }
        }
        if (initialTeamId != null) {
            teamsList.find { it.id == initialTeamId }?.let { return it }
        }
        return teamsList.firstOrNull()
    }

    private fun resolveSelectedProject(
        projectsList: List<Project>,
        currentSelectedId: String?
    ): Project? {
        if (currentSelectedId != null) {
            projectsList.find { it.id == currentSelectedId }?.let { return it }
        }
        if (initialProjectId != null) {
            projectsList.find { it.id == initialProjectId }?.let { return it }
        }
        return projectsList.firstOrNull()
    }

    private fun loadTeamExecutions(teamId: String) {
        executionHistoryJob?.cancel()
        executionHistoryJob = viewModelScope.launch {
            teamRepository.getTeamExecutions(teamId)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _uiState.update { it.copy(error = e.message ?: "تعذّر تحميل سجل تنفيذ الفريق.") }
                }
                .collect { history ->
                    _uiState.update { current ->
                        val latest = history.firstOrNull()
                        val activeExec = if (current.currentExecution?.teamId == teamId) {
                            current.currentExecution
                        } else {
                            latest
                        }

                        current.copy(
                            executionHistory = history,
                            currentExecution = activeExec,
                            isExecuting = activeExec?.status == ExecutionStatus.EXECUTING,
                            isPaused = activeExec?.status == ExecutionStatus.PAUSED
                        )
                    }
                }
        }
    }

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    fun selectTeam(team: ModelTeam) {
        _uiState.update {
            it.copy(
                selectedTeam = team,
                currentExecution = if (it.currentExecution?.teamId == team.id) it.currentExecution else null
            )
        }
        loadTeamExecutions(team.id)
    }

    fun selectProject(project: Project?) {
        _uiState.update { it.copy(selectedProject = project) }
    }

    fun setActiveTab(tab: TeamChatTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setTaskPrompt(prompt: String) {
        _uiState.update { it.copy(taskPrompt = prompt) }
    }

    fun addAttachedFile(name: String, path: String, content: String, sizeBytes: Long, fileType: String = "text/plain") {
        val safeName = name.trim().ifBlank { "snippet.txt" }
        val newFile = AttachedFileContext(
            name = safeName,
            path = path.trim().ifBlank { "/src/$safeName" },
            content = content,
            sizeBytes = if (sizeBytes > 0) sizeBytes else content.toByteArray().size.toLong(),
            fileType = fileType
        )
        _uiState.update {
            it.copy(
                attachedFiles = it.attachedFiles + newFile,
                showAttachmentDialog = false,
                statusMessage = "تم إرفاق الملف: $safeName"
            )
        }
    }

    fun removeAttachedFile(index: Int) {
        _uiState.update {
            val updated = it.attachedFiles.toMutableList()
            if (index in updated.indices) {
                updated.removeAt(index)
            }
            it.copy(attachedFiles = updated)
        }
    }

    fun requestEstimation() {
        val team = _uiState.value.selectedTeam ?: return
        val prompt = _uiState.value.taskPrompt.trim()
        if (prompt.isBlank()) {
            _uiState.update { it.copy(error = "يرجى كتابة نص المهمة قبل تقدير الموارد.") }
            return
        }

        val userId = getUserId() ?: run {
            _uiState.update { it.copy(error = AUTH_REQUIRED_ACTION) }
            return
        }
        viewModelScope.launch {
            runCatching { teamOrchestrator.estimateTaskResources(userId, team, prompt) }
                .onSuccess { est ->
                    _uiState.update { it.copy(preEstimation = est, showEstimationDialog = true) }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update { it.copy(error = e.message ?: "تعذّر تقدير الموارد.") }
                }
        }
    }

    fun dismissEstimation() {
        _uiState.update { it.copy(showEstimationDialog = false) }
    }

    fun sendTask(skipEstimationCheck: Boolean = false) {
        val team = _uiState.value.selectedTeam ?: run {
            _uiState.update { it.copy(error = "يرجى اختيار فريق أولاً.") }
            return
        }
        val prompt = _uiState.value.taskPrompt.trim()
        if (prompt.isBlank()) {
            _uiState.update { it.copy(error = "يرجى كتابة نص المهمة أولاً") }
            return
        }

        val userId = getUserId() ?: run {
            _uiState.update { it.copy(error = AUTH_REQUIRED_ACTION) }
            return
        }

        if (!skipEstimationCheck && _uiState.value.preEstimation == null && team.governance.freeOnly) {
            requestEstimation()
            return
        }

        _uiState.update {
            it.copy(
                showEstimationDialog = false,
                isExecuting = true,
                isPaused = false,
                executionDurationSeconds = 0,
                activeTab = TeamChatTab.LIVE_COLLABORATION,
                error = null
            )
        }

        val attached = _uiState.value.attachedFiles
        val projectId = _uiState.value.selectedProject?.id

        startTimer()

        executionJob?.cancel()
        executionJob = viewModelScope.launch {
            teamOrchestrator.executeTaskWithContext(
                userId = userId,
                team = team,
                prompt = prompt,
                attachedFiles = attached,
                projectId = projectId
            )
                .catch { e ->
                    if (e is CancellationException) throw e
                    stopTimer()
                    _uiState.update {
                        it.copy(
                            isExecuting = false,
                            isPaused = false,
                            error = e.message ?: "فشل تنفيذ مهمة الفريق."
                        )
                    }
                }
                .collect { execution ->
                    _uiState.update { current ->
                        current.copy(
                            currentExecution = execution,
                            isExecuting = execution.status == ExecutionStatus.EXECUTING,
                            isPaused = execution.status == ExecutionStatus.PAUSED
                        )
                    }
                    if (execution.status == ExecutionStatus.COMPLETED || execution.status == ExecutionStatus.FAILED || execution.status == ExecutionStatus.CANCELLED) {
                        stopTimer()
                    }
                }
        }
    }

    fun pauseTeam() {
        val execution = _uiState.value.currentExecution ?: return
        if (execution.status != ExecutionStatus.EXECUTING) return

        viewModelScope.launch {
            try {
                teamOrchestrator.pauseExecution(execution.id)
                _uiState.update { current ->
                    if (current.currentExecution?.id != execution.id) {
                        current
                    } else {
                        current.copy(
                            currentExecution = current.currentExecution?.copy(status = ExecutionStatus.PAUSED),
                            isPaused = true,
                            isExecuting = false,
                            statusMessage = "تم طلب الإيقاف المؤقت؛ ستكتمل الخطوة الجارية أولاً",
                            error = null
                        )
                    }
                }
                stopTimer()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(error = error.message ?: "تعذر إيقاف التنفيذ مؤقتًا.") }
            }
        }
    }

    fun resumeTeam() {
        val exec = _uiState.value.currentExecution ?: return
        if (exec.status != ExecutionStatus.PAUSED) return
        val team = _uiState.value.selectedTeam ?: return
        val userId = getUserId() ?: run {
            _uiState.update { it.copy(error = AUTH_REQUIRED_ACTION) }
            return
        }

        viewModelScope.launch {
            teamOrchestrator.resumeExecution(exec.id, userId, team)
                .catch { error ->
                    if (error is CancellationException) throw error
                    stopTimer()
                    _uiState.update { current ->
                        if (current.currentExecution?.id != exec.id) {
                            current
                        } else {
                            current.copy(
                                isExecuting = false,
                                isPaused = true,
                                error = error.message ?: "تعذر استئناف تنفيذ الفريق."
                            )
                        }
                    }
                }
                .collect { updated ->
                    _uiState.update { current ->
                        if (current.currentExecution?.id != exec.id) {
                            current
                        } else {
                            current.copy(
                                currentExecution = updated,
                                isExecuting = updated.status == ExecutionStatus.EXECUTING,
                                isPaused = updated.status == ExecutionStatus.PAUSED,
                                statusMessage = if (updated.status == ExecutionStatus.EXECUTING) {
                                    "جارٍ استكمال مهام الفريق"
                                } else {
                                    current.statusMessage
                                },
                                error = null
                            )
                        }
                    }
                    if (updated.status == ExecutionStatus.EXECUTING) {
                        startTimer()
                    } else if (isTerminalExecution(updated.status)) {
                        stopTimer()
                    }
                }
        }
    }

    private fun isTerminalExecution(status: ExecutionStatus): Boolean =
        status == ExecutionStatus.COMPLETED ||
            status == ExecutionStatus.FAILED ||
            status == ExecutionStatus.CANCELLED

    fun stopTeam() {
        val execution = _uiState.value.currentExecution ?: return
        if (execution.status != ExecutionStatus.EXECUTING && execution.status != ExecutionStatus.PAUSED) return

        viewModelScope.launch {
            try {
                teamOrchestrator.stopExecution(execution.id)
                executionJob?.cancel()
                stopTimer()
                _uiState.update { current ->
                    if (current.currentExecution?.id != execution.id) {
                        current
                    } else {
                        current.copy(
                            currentExecution = current.currentExecution?.copy(
                                status = ExecutionStatus.CANCELLED,
                                completedAt = System.currentTimeMillis()
                            ),
                            isExecuting = false,
                            isPaused = false,
                            statusMessage = "تم إيقاف تنفيذ الفريق كليًا",
                            error = null
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(error = error.message ?: "تعذر إيقاف تنفيذ الفريق.") }
            }
        }
    }

    fun retryFailedTasks() {
        val exec = _uiState.value.currentExecution ?: return
        val team = _uiState.value.selectedTeam ?: return
        val userId = getUserId() ?: run {
            _uiState.update { it.copy(error = AUTH_REQUIRED_ACTION) }
            return
        }

        _uiState.update { it.copy(isExecuting = true, isPaused = false, statusMessage = "جارٍ إعادة تشغيل المهام غير المكتملة") }
        startTimer()

        executionJob?.cancel()
        executionJob = viewModelScope.launch {
            teamOrchestrator.retryFailedSteps(exec.id, userId, team)
                .catch { e ->
                    if (e is CancellationException) throw e
                    stopTimer()
                    _uiState.update {
                        it.copy(isExecuting = false, error = e.message)
                    }
                }
                .collect { updated ->
                    _uiState.update { current ->
                        current.copy(
                            currentExecution = updated,
                            isExecuting = updated.status == ExecutionStatus.EXECUTING
                        )
                    }
                    if (updated.status == ExecutionStatus.COMPLETED || updated.status == ExecutionStatus.FAILED) {
                        stopTimer()
                    }
                }
        }
    }

    fun assignDirectTask(memberId: String, taskPrompt: String) {
        val exec = _uiState.value.currentExecution
        val team = _uiState.value.selectedTeam ?: return
        val trimmedTask = taskPrompt.trim()
        if (trimmedTask.isBlank()) {
            _uiState.update { it.copy(error = "يرجى كتابة نص المهمة الموجهة.") }
            return
        }
        val userId = getUserId() ?: run {
            _uiState.update { it.copy(error = AUTH_REQUIRED_ACTION) }
            return
        }
        val executionId = exec?.id ?: java.util.UUID.randomUUID().toString()

        _uiState.update { it.copy(showAssignDialog = false, isExecuting = true, statusMessage = "جارٍ إسناد المهمة للعضو") }

        viewModelScope.launch {
            teamOrchestrator.assignDirectTask(executionId, userId, team, memberId, trimmedTask)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _uiState.update { it.copy(isExecuting = false, error = e.message) }
                }
                .collect { updated ->
                    _uiState.update { it.copy(currentExecution = updated, isExecuting = false) }
                }
        }
    }

    fun sendInstruction(instruction: TeamInstruction) {
        val exec = _uiState.value.currentExecution ?: run {
            _uiState.update { it.copy(error = "لا توجد جلسة تنفيذ نشطة لإرسال التعليمات إليها.") }
            return
        }
        val team = _uiState.value.selectedTeam ?: return
        val userId = getUserId() ?: run {
            _uiState.update { it.copy(error = AUTH_REQUIRED_ACTION) }
            return
        }

        _uiState.update { it.copy(showInstructionDialog = false, isExecuting = true, statusMessage = "جارٍ إرسال التعليمات ومعالجتها") }

        viewModelScope.launch {
            teamOrchestrator.sendInstruction(exec.id, userId, team, instruction)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _uiState.update { it.copy(isExecuting = false, error = e.message) }
                }
                .collect { updated ->
                    _uiState.update { it.copy(currentExecution = updated, isExecuting = false) }
                }
        }
    }

    fun changeMemberAgent(memberId: String, newModelId: String) {
        val team = _uiState.value.selectedTeam ?: return
        if (getUserId() == null) {
            _uiState.update { it.copy(error = AUTH_REQUIRED_ACTION) }
            return
        }
        viewModelScope.launch {
            val result = teamOrchestrator.changeMemberAgent(team.id, memberId, newModelId)
            if (result.isSuccess) {
                val updatedTeam = teamRepository.getTeamById(team.id)
                _uiState.update { current ->
                    current.copy(
                        selectedTeam = updatedTeam ?: current.selectedTeam,
                        showChangeAgentDialog = false,
                        statusMessage = "تم تبديل نموذج العضو بنجاح"
                    )
                }
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun setComparisonMembers(memberAId: String?, memberBId: String?) {
        _uiState.update { it.copy(compareMemberAId = memberAId, compareMemberBId = memberBId) }
    }

    fun setShowAttachmentDialog(show: Boolean) {
        _uiState.update { it.copy(showAttachmentDialog = show) }
    }

    fun setShowAssignDialog(show: Boolean) {
        _uiState.update { it.copy(showAssignDialog = show) }
    }

    fun setShowChangeAgentDialog(show: Boolean) {
        _uiState.update { it.copy(showChangeAgentDialog = show) }
    }

    fun setShowInstructionDialog(show: Boolean) {
        _uiState.update { it.copy(showInstructionDialog = show) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(executionDurationSeconds = it.executionDurationSeconds + 1) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        executionJob?.cancel()
        executionHistoryJob?.cancel()
    }

    companion object {
        private const val AUTH_REQUIRED_ACTION = "سجّل الدخول بحساب صالح لتنفيذ عمليات الفريق."
    }
}

private data class TeamChatBootstrap(
    val isAuthenticated: Boolean,
    val teams: List<ModelTeam>,
    val projects: List<Project>,
    val models: List<AIModel>,
    val error: String?
)
