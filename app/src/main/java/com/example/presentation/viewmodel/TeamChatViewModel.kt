package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.agent.TeamOrchestrator
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.CrashRepository
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

class TeamChatViewModel(
    private val initialTeamId: String?,
    private val initialProjectId: String?,
    private val teamRepository: AIModelTeamRepository,
    private val teamOrchestrator: TeamOrchestrator,
    private val crashRepository: CrashRepository,
    private val aiModelRepository: AIModelRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamChatUiState())
    val uiState: StateFlow<TeamChatUiState> = _uiState.asStateFlow()

    private var executionJob: Job? = null
    private var timerJob: Job? = null

    private fun getUserId(): String? = authRepository.getCurrentUserSync()?.id?.takeIf { it.isNotBlank() }

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val userId = getUserId() ?: run {
            _uiState.update { it.copy(error = "سجّل الدخول بحساب صالح لتنفيذ عمليات الفريق.") }
            return
        } ?: run {
            _uiState.update { it.copy(error = "سجّل الدخول بحساب صالح للوصول إلى محادثات الفريق.") }
            return
        }

        viewModelScope.launch {
            // Load teams
            launch {
                teamRepository.getTeams(userId).collect { teamsList ->
                    _uiState.update { current ->
                        val selected = if (initialTeamId != null) {
                            teamsList.find { it.id == initialTeamId } ?: teamsList.firstOrNull()
                        } else {
                            current.selectedTeam ?: teamsList.firstOrNull()
                        }
                        current.copy(
                            teams = teamsList,
                            selectedTeam = selected
                        )
                    }
                    val selected = _uiState.value.selectedTeam
                    if (selected != null) {
                        loadTeamExecutions(selected.id)
                    }
                }
            }

            // Load projects
            launch {
                crashRepository.getProjects().collect { projectsList ->
                    _uiState.update { current ->
                        val selected = if (initialProjectId != null) {
                            projectsList.find { it.id == initialProjectId } ?: projectsList.firstOrNull()
                        } else {
                            current.selectedProject ?: projectsList.firstOrNull()
                        }
                        current.copy(
                            projects = projectsList,
                            selectedProject = selected
                        )
                    }
                }
            }

            // Load available models for switching & comparison
            launch {
                aiModelRepository.getAllAvailableModels(userId, false).collect { models ->
                    _uiState.update { it.copy(availableModels = models) }
                }
            }
        }
    }

    private fun loadTeamExecutions(teamId: String) {
        viewModelScope.launch {
            teamRepository.getTeamExecutions(teamId).collect { history ->
                _uiState.update { current ->
                    val latest = history.firstOrNull()
                    val activeExec = if (current.currentExecution?.teamId == teamId) {
                        current.currentExecution
                    } else latest

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

    fun selectTeam(team: ModelTeam) {
        _uiState.update { it.copy(selectedTeam = team) }
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
        val newFile = AttachedFileContext(
            name = name.ifBlank { "snippet.txt" },
            path = path.ifBlank { "/src/$name" },
            content = content,
            sizeBytes = if (sizeBytes > 0) sizeBytes else content.toByteArray().size.toLong(),
            fileType = fileType
        )
        _uiState.update {
            it.copy(
                attachedFiles = it.attachedFiles + newFile,
                showAttachmentDialog = false,
                statusMessage = "تم إرفاق الملف: $name"
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
        val prompt = _uiState.value.taskPrompt
        if (prompt.isBlank()) return

        val userId = getUserId() ?: return
        viewModelScope.launch {
            val est = teamOrchestrator.estimateTaskResources(userId, team, prompt)
            _uiState.update { it.copy(preEstimation = est, showEstimationDialog = true) }
        }
    }

    fun dismissEstimation() {
        _uiState.update { it.copy(showEstimationDialog = false) }
    }

    fun sendTask(skipEstimationCheck: Boolean = false) {
        val team = _uiState.value.selectedTeam ?: return
        val prompt = _uiState.value.taskPrompt.trim()
        if (prompt.isBlank()) {
            _uiState.update { it.copy(error = "يرجى كتابة نص المهمة أولاً") }
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
                activeTab = TeamChatTab.LIVE_COLLABORATION
            )
        }

        val userId = getUserId() ?: run {
            _uiState.update { it.copy(error = "سجّل الدخول بحساب صالح لتنفيذ عمليات الفريق.") }
            return
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
            ).collect { execution ->
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
        val execId = _uiState.value.currentExecution?.id ?: return
        viewModelScope.launch {
            teamOrchestrator.pauseExecution(execId)
            _uiState.update { it.copy(isPaused = true, isExecuting = false, statusMessage = "تم إيقاف عمل الفريق مؤقتًا") }
            stopTimer()
        }
    }

    fun resumeTeam() {
        val exec = _uiState.value.currentExecution ?: return
        val team = _uiState.value.selectedTeam ?: return
        val userId = getUserId() ?: run {
            _uiState.update { it.copy(error = "سجّل الدخول بحساب صالح لتنفيذ عمليات الفريق.") }
            return
        }

        _uiState.update { it.copy(isPaused = false, isExecuting = true, statusMessage = "جارٍ استكمال مهام الفريق") }
        startTimer()

        executionJob?.cancel()
        executionJob = viewModelScope.launch {
            teamOrchestrator.resumeExecution(exec.id, userId, team).collect { updated ->
                _uiState.update { current ->
                    current.copy(
                        currentExecution = updated,
                        isExecuting = updated.status == ExecutionStatus.EXECUTING,
                        isPaused = updated.status == ExecutionStatus.PAUSED
                    )
                }
                if (updated.status == ExecutionStatus.COMPLETED || updated.status == ExecutionStatus.FAILED) {
                    stopTimer()
                }
            }
        }
    }

    fun stopTeam() {
        val execId = _uiState.value.currentExecution?.id ?: return
        viewModelScope.launch {
            teamOrchestrator.stopExecution(execId)
            executionJob?.cancel()
            stopTimer()
            _uiState.update {
                it.copy(
                    isExecuting = false,
                    isPaused = false,
                    statusMessage = "تم إيقاف تنفيذ الفريق كليًا"
                )
            }
        }
    }

    fun retryFailedTasks() {
        val exec = _uiState.value.currentExecution ?: return
        val team = _uiState.value.selectedTeam ?: return
        val userId = getUserId() ?: run {
            _uiState.update { it.copy(error = "سجّل الدخول بحساب صالح لتنفيذ عمليات الفريق.") }
            return
        }

        _uiState.update { it.copy(isExecuting = true, isPaused = false, statusMessage = "جارٍ إعادة تشغيل المهام غير المكتملة") }
        startTimer()

        executionJob?.cancel()
        executionJob = viewModelScope.launch {
            teamOrchestrator.retryFailedSteps(exec.id, userId, team).collect { updated ->
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
        val userId = getUserId() ?: run {
            _uiState.update { it.copy(error = "سجّل الدخول بحساب صالح لتنفيذ عمليات الفريق.") }
            return
        }
        val executionId = exec?.id ?: java.util.UUID.randomUUID().toString()

        _uiState.update { it.copy(showAssignDialog = false, isExecuting = true, statusMessage = "جارٍ إسناد المهمة للعضو") }

        viewModelScope.launch {
            teamOrchestrator.assignDirectTask(executionId, userId, team, memberId, taskPrompt).collect { updated ->
                _uiState.update { it.copy(currentExecution = updated, isExecuting = false) }
            }
        }
    }

    fun sendInstruction(instruction: TeamInstruction) {
        val exec = _uiState.value.currentExecution ?: return
        val team = _uiState.value.selectedTeam ?: return
        val userId = getUserId() ?: run {
            _uiState.update { it.copy(error = "سجّل الدخول بحساب صالح لتنفيذ عمليات الفريق.") }
            return
        }

        _uiState.update { it.copy(showInstructionDialog = false, isExecuting = true, statusMessage = "جارٍ إرسال التعليمات ومعالجتها") }

        viewModelScope.launch {
            teamOrchestrator.sendInstruction(exec.id, userId, team, instruction).collect { updated ->
                _uiState.update { it.copy(currentExecution = updated, isExecuting = false) }
            }
        }
    }

    fun changeMemberAgent(memberId: String, newModelId: String) {
        val team = _uiState.value.selectedTeam ?: return
        viewModelScope.launch {
            val result = teamOrchestrator.changeMemberAgent(team.id, memberId, newModelId)
            if (result.isSuccess) {
                val updatedTeam = teamRepository.getTeamById(team.id)
                _uiState.update {
                    it.copy(
                        selectedTeam = updatedTeam,
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
    }
}
