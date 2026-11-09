package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.VibeStudioRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

internal fun authenticatedUserIdOrNull(user: UserAccount?): String? =
    user?.id?.takeIf { it.isNotBlank() }

class VibeStudioViewModel(
    private val repository: VibeStudioRepository,
    private val teamRepository: com.example.domain.repository.AIModelTeamRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val _workspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()

    init {
        loadWorkspaces()
        loadTeams()
    }

    private suspend fun requireAuthenticatedUserId(): String? {
        val userId = authenticatedUserIdOrNull(authRepository.currentUser.firstOrNull())
        if (userId == null) {
            _uiState.update { it.copy(error = "سجّل الدخول بحساب صالح لمتابعة هذا الإجراء.") }
        }
        return userId
    }

    private fun loadTeams() {
        viewModelScope.launch {
            authRepository.currentUser
                .map(::authenticatedUserIdOrNull)
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    if (userId == null) flowOf(emptyList<ModelTeam>())
                    else teamRepository.getTeams(userId)
                }
                .collect { teams -> _uiState.update { it.copy(availableTeams = teams) } }
        }
    }

    private fun loadWorkspaces() {
        viewModelScope.launch {
            authRepository.currentUser
                .map(::authenticatedUserIdOrNull)
                .distinctUntilChanged()
                .onEach {
                    _workspaces.value = emptyList()
                    // Clear all workspace-scoped state when the authenticated account changes.
                    _uiState.value = StudioUiState()
                }
                .flatMapLatest { userId ->
                    if (userId == null) flowOf(emptyList<Workspace>())
                    else repository.getWorkspaces(userId)
                }
                .collect { workspaces -> _workspaces.value = workspaces }
        }
    }

    fun selectWorkspace(workspace: Workspace?) {
        viewModelScope.launch {
            if (workspace == null) {
                _uiState.update { it.copy(activeWorkspace = null, openFiles = emptyList(), selectedFileId = null) }
                return@launch
            }

            val userId = requireAuthenticatedUserId() ?: return@launch
            _uiState.update { it.copy(isLoading = true, activeWorkspace = workspace, error = null) }
            repository.updateWorkspaceAccess(workspace.id)
            
            // Collect files
            launch {
                repository.getFiles(workspace.id).collect { files ->
                    _uiState.update { state ->
                        // Re-open selected file with updated content if modified
                        val updatedOpenFiles = state.openFiles.map { openFile ->
                            files.find { it.id == openFile.id } ?: openFile
                        }
                        state.copy(
                            fileTree = buildFileTree(files),
                            openFiles = updatedOpenFiles
                        )
                    }
                }
            }

            // Collect logs
            launch {
                repository.getBuildLogs(workspace.id).collect { logs ->
                    _uiState.update { it.copy(logs = logs) }
                }
            }

            // Collect checkpoints
            launch {
                repository.getCheckpoints(workspace.id).collect { checkpoints ->
                    _uiState.update { it.copy(checkpoints = checkpoints) }
                }
            }

            // Collect Sandbox Runtime State
            launch {
                repository.getSandboxRuntimeState(workspace.id).collect { state ->
                    _uiState.update { it.copy(runtimeState = state) }
                }
            }

            // Collect Terminal command history
            launch {
                repository.getTerminalHistory(workspace.id).collect { history ->
                    _uiState.update { it.copy(terminalResults = history) }
                }
            }

            // Collect Build Operations
            launch {
                repository.getBuildOperations(workspace.id).collect { operations ->
                    _uiState.update { it.copy(buildOperations = operations) }
                }
            }

            // Collect Git Commits
            launch {
                repository.getGitCommits(workspace.id).collect { commits ->
                    _uiState.update { it.copy(gitCommits = commits) }
                    refreshWorkingTreeState(workspace.id)
                }
            }

            // Collect Git Branches
            launch {
                repository.getGitBranches(workspace.id).collect { branches ->
                    val currentBranch = branches.find { it.isCurrent }?.name ?: branches.firstOrNull()?.name ?: "main"
                    _uiState.update {
                        it.copy(
                            gitBranches = branches,
                            isGitInitialized = branches.isNotEmpty(),
                            currentBranchName = currentBranch
                        )
                    }
                    refreshWorkingTreeState(workspace.id)
                }
            }

            // Collect Task Reviews
            launch {
                repository.getTaskReviews(workspace.id).collect { reviews ->
                    val pending = reviews.firstOrNull { it.status == "PENDING" }
                    _uiState.update {
                        it.copy(
                            activeTaskReview = pending,
                            isTaskReviewOpen = pending != null
                        )
                    }
                }
            }

            // Collect Agent Activity Logs
            launch {
                repository.getActivityLogs(workspace.id).collect { logs ->
                    _uiState.update { it.copy(agentActivityLogs = logs) }
                }
            }

            // Collect Pending Approvals
            launch {
                repository.getPendingApprovals(workspace.id).collect { approvals ->
                    _uiState.update { 
                        it.copy(
                            pendingApprovals = approvals,
                            isApprovalQueueOpen = approvals.isNotEmpty()
                        ) 
                    }
                }
            }

            // Collect Agent Model Status
            launch {
                val defaultModel = repository.getDefaultModel(userId)
                _uiState.update { it.copy(
                    activeAgentModel = defaultModel?.name ?: "Auto (Free)",
                    agentIsFree = defaultModel?.isFree ?: true,
                    agentCapabilities = defaultModel?.capabilities ?: emptyList()
                ) }
            }

            // Seed initial greeting message
            if (_uiState.value.chatMessages.isEmpty()) {
                val welcomeMessage = AgentChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = AgentMessageSender.AGENT,
                    content = "مرحباً بك في AI Vibe Studio! مساحة العمل معزولة تماماً وتتضمن خادم تطوير حقيقي، نافذة أوامر Terminal، ومعاينة حية ذكية.",
                    timestamp = System.currentTimeMillis()
                )
                _uiState.update { it.copy(chatMessages = listOf(welcomeMessage)) }
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun buildFileTree(files: List<WorkspaceFile>): List<VibeFileNode> {
        val rootFiles = files.filter { !it.path.contains("/") }
        return rootFiles.map { file ->
            val childFiles = files.filter {
                it.path.startsWith("${file.path}/") &&
                it.path.substringAfter("${file.path}/").count { c -> c == '/' } == 0
            }
            VibeFileNode(file, children = childFiles.map { VibeFileNode(it) })
        }
    }

    fun createNewProject(name: String, description: String, type: WorkspaceType, techStack: String) {
        viewModelScope.launch {
            val userId = requireAuthenticatedUserId() ?: return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }
            val workspace = Workspace(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description,
                type = type,
                techStack = techStack,
                ownerId = userId
            )
            repository.createWorkspace(workspace)
            selectWorkspace(workspace)
            
            // Generate project with AI
            repository.generateProject(userId, description, workspace.id)
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
            
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun openFile(file: WorkspaceFile) {
        _uiState.update { state ->
            if (state.openFiles.any { it.id == file.id }) {
                state.copy(selectedFileId = file.id)
            } else {
                state.copy(openFiles = state.openFiles + file, selectedFileId = file.id)
            }
        }
    }

    fun closeFile(fileId: String) {
        _uiState.update { state ->
            val newList = state.openFiles.filter { it.id != fileId }
            val newSelected = if (state.selectedFileId == fileId) newList.lastOrNull()?.id else state.selectedFileId
            state.copy(openFiles = newList, selectedFileId = newSelected)
        }
    }

    fun selectTeam(team: ModelTeam?) {
        _uiState.update { it.copy(activeTeam = team, selectedMemberId = null) }
    }

    fun selectTeamMember(memberId: String?) {
        _uiState.update { it.copy(selectedMemberId = memberId) }
    }

    fun updateFileContent(fileId: String, newContent: String) {
        viewModelScope.launch {
            val file = _uiState.value.openFiles.find { it.id == fileId } ?: return@launch
            val updated = file.copy(content = newContent)
            repository.saveFile(updated)
            _uiState.update {
                it.copy(
                    openFiles = it.openFiles.map { f -> if (f.id == fileId) updated else f },
                    previewKey = it.previewKey + 1
                )
            }
        }
    }

    fun setExecutionMode(mode: AgentExecutionMode) {
        _uiState.update { it.copy(executionMode = mode) }
    }

    fun sendMessage(instruction: String) {
        if (instruction.isBlank()) return
        val workspace = _uiState.value.activeWorkspace ?: return

        viewModelScope.launch {
            val userId = requireAuthenticatedUserId() ?: return@launch
            val currentMode = _uiState.value.executionMode

            // Add user message to chat
            val userMsg = AgentChatMessage(
                id = UUID.randomUUID().toString(),
                sender = AgentMessageSender.USER,
                content = instruction,
                timestamp = System.currentTimeMillis(),
                mode = currentMode
            )
            _uiState.update {
                it.copy(
                    chatMessages = it.chatMessages + userMsg,
                    isLoading = true,
                    error = null
                )
            }

            repository.addBuildLog(BuildLog(
                id = UUID.randomUUID().toString(),
                workspaceId = workspace.id,
                message = "[$currentMode]: $instruction",
                type = LogType.TERMINAL_INPUT
            ))

            val result = if (_uiState.value.activeTeam != null) {
                repository.executeTeamInstruction(
                    userId = userId,
                    workspaceId = workspace.id,
                    teamId = _uiState.value.activeTeam!!.id,
                    memberId = _uiState.value.selectedMemberId,
                    instruction = instruction,
                    mode = currentMode
                )
            } else {
                repository.executeInstruction(
                    userId = userId,
                    workspaceId = workspace.id,
                    instruction = instruction,
                    mode = currentMode,
                    onStepProgress = { step ->
                        _uiState.update { state ->
                            val currentPlan = state.activePlan
                            if (currentPlan != null) {
                                val updatedSteps = currentPlan.steps.map { if (it.id == step.id) step else it }
                                state.copy(activePlan = currentPlan.copy(steps = updatedSteps))
                            } else state
                        }
                    }
                )
            }

            result.onSuccess { execResult ->
                val sender = if (_uiState.value.activeTeam != null) {
                    if (_uiState.value.selectedMemberId != null) AgentMessageSender.TEAM_MEMBER else AgentMessageSender.AGENT
                } else AgentMessageSender.AGENT
                
                val senderName = if (_uiState.value.activeTeam != null) {
                    if (_uiState.value.selectedMemberId != null) {
                        _uiState.value.activeTeam?.members?.find { it.id == _uiState.value.selectedMemberId }?.name
                    } else "Team Leader"
                } else "AI Agent"

                val agentMsg = AgentChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = sender,
                    senderName = senderName,
                    content = execResult.responseMessage,
                    timestamp = System.currentTimeMillis(),
                    mode = execResult.mode,
                    plan = execResult.plan,
                    summary = execResult.summary,
                    reviewIssues = execResult.reviewIssues
                )

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        chatMessages = state.chatMessages + agentMsg,
                        activePlan = execResult.plan ?: state.activePlan,
                        currentExecutionSummary = execResult.summary ?: state.currentExecutionSummary,
                        currentDiffs = execResult.summary?.changedFiles ?: state.currentDiffs,
                        reviewIssues = execResult.reviewIssues.ifEmpty { state.reviewIssues },
                        pendingSensitiveAction = execResult.pendingAction,
                        isDiffViewerOpen = execResult.summary?.changedFiles?.isNotEmpty() == true,
                        isReviewIssuesOpen = execResult.reviewIssues.isNotEmpty()
                    )
                }
            }.onFailure { err ->
                val errorMsg = AgentChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = AgentMessageSender.AGENT,
                    content = "حدث خطأ أثناء معالجة الطلب: ${err.message}. تم إيقاف العملية لحماية الكود.",
                    timestamp = System.currentTimeMillis(),
                    mode = currentMode,
                    isError = true
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = err.message,
                        chatMessages = it.chatMessages + errorMsg
                    )
                }
            }
        }
    }

    fun executeActivePlan() {
        val plan = _uiState.value.activePlan ?: return
        val workspace = _uiState.value.activeWorkspace ?: return

        viewModelScope.launch {
            val userId = requireAuthenticatedUserId() ?: return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.executePlan(
                userId = userId,
                workspaceId = workspace.id,
                plan = plan,
                onStepProgress = { step ->
                    _uiState.update { state ->
                        val currentPlan = state.activePlan
                        if (currentPlan != null) {
                            val updatedSteps = currentPlan.steps.map { if (it.id == step.id) step else it }
                            state.copy(activePlan = currentPlan.copy(steps = updatedSteps))
                        } else state
                    }
                }
            ).onSuccess { summary ->
                val successMsg = AgentChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = AgentMessageSender.AGENT,
                    content = summary.summaryText,
                    timestamp = System.currentTimeMillis(),
                    mode = AgentExecutionMode.AGENT,
                    plan = plan.copy(status = PlanStatus.COMPLETED),
                    summary = summary,
                    reviewIssues = summary.detectedIssues
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentExecutionSummary = summary,
                        currentDiffs = summary.changedFiles,
                        reviewIssues = summary.detectedIssues,
                        chatMessages = it.chatMessages + successMsg,
                        isDiffViewerOpen = summary.changedFiles.isNotEmpty()
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun confirmSensitiveAction(approved: Boolean) {
        val action = _uiState.value.pendingSensitiveAction ?: return
        val workspace = _uiState.value.activeWorkspace ?: return

        viewModelScope.launch {
            if (approved) {
                when (action.type) {
                    SensitiveActionType.DELETE_FILE -> {
                        val file = repository.getFileByPath(workspace.id, action.targetPath)
                        if (file != null) {
                            repository.deleteFile(file.id)
                            closeFile(file.id)
                            repository.addBuildLog(BuildLog(
                                id = UUID.randomUUID().toString(),
                                workspaceId = workspace.id,
                                message = "تمت الموافقة وحذف الملف: ${action.targetPath}",
                                type = LogType.WARNING
                            ))
                        }
                    }
                    SensitiveActionType.RESET_WORKSPACE -> {
                        repository.clearLogs(workspace.id)
                    }
                }
            } else {
                repository.addBuildLog(BuildLog(
                    id = UUID.randomUUID().toString(),
                    workspaceId = workspace.id,
                    message = "تم إلغاء الإجراء الحساس بناءً على رغبة المستخدم.",
                    type = LogType.INFO
                ))
            }
            _uiState.update { it.copy(pendingSensitiveAction = null) }
        }
    }

    fun createManualCheckpoint(title: String, description: String) {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            repository.createCheckpoint(workspace.id, title, description)
            repository.addBuildLog(BuildLog(
                id = UUID.randomUUID().toString(),
                workspaceId = workspace.id,
                message = "تم إنشاء نقطة استعادة يدوية: $title",
                type = LogType.SUCCESS
            ))
        }
    }

    fun revertToCheckpoint(checkpointId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.revertToCheckpoint(checkpointId).onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isCheckpointsOpen = false,
                        openFiles = emptyList(),
                        selectedFileId = null
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun deleteCheckpoint(checkpointId: String) {
        viewModelScope.launch {
            repository.deleteCheckpoint(checkpointId)
        }
    }

    fun toggleTestingPanel(isOpen: Boolean? = null) {
        _uiState.update { state ->
            val target = isOpen ?: !state.isTestingPanelOpen
            state.copy(isTestingPanelOpen = target)
        }
        if (_uiState.value.isTestingPanelOpen && _uiState.value.qualityReport == null) {
            runQualityAudit()
        }
    }

    fun setTestingTab(tab: TestingTab) {
        _uiState.update { it.copy(activeTestingTab = tab) }
    }

    fun runTests() {
        toggleTestingPanel(true)
        runQualityAudit()
    }

    fun runQualityAudit() {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAuditRunning = true, isLoading = true) }
            repository.runIntelligentQualityAudit(workspace.id).onSuccess { report ->
                repository.runWorkspaceTests(workspace.id).onSuccess { tests ->
                    _uiState.update {
                        it.copy(
                            isAuditRunning = false,
                            isLoading = false,
                            qualityReport = report,
                            testResults = tests
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isAuditRunning = false, isLoading = false, error = err.message) }
            }
        }
    }

    fun startAutoDebug() {
        val workspace = _uiState.value.activeWorkspace ?: return
        val currentReport = _uiState.value.qualityReport ?: return

        val nextAttempt = _uiState.value.autoDebugState.attemptCount + 1
        if (nextAttempt > _uiState.value.autoDebugState.maxAttempts) {
            _uiState.update {
                it.copy(
                    autoDebugState = it.autoDebugState.copy(
                        isDebugging = false,
                        isFinished = true,
                        finalStatus = "توقف التصحيح الذاتي لتجاوز الحد الأقصى للمحاولات (3 محاولات) لمنع الحلقات اللانهائية."
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    autoDebugState = it.autoDebugState.copy(
                        isDebugging = true,
                        attemptCount = nextAttempt,
                        currentStepMessage = "جاري تحليل الخطأ ومطابقة الكود المصدر (المحاولة $nextAttempt من 3)..."
                    ),
                    activeTestingTab = TestingTab.AUTO_DEBUG
                )
            }

            repository.startAutoDebug(workspace.id, currentReport, nextAttempt).onSuccess { proposal ->
                _uiState.update { state ->
                    state.copy(
                        autoDebugState = state.autoDebugState.copy(
                            isDebugging = false,
                            activeProposal = proposal,
                            currentStepMessage = if (proposal.isStuckOrNoProgress)
                                "توقف التصحيح: ${proposal.explanation}"
                            else
                                "تم إعداد اقتراح الإصلاح للمحاولة $nextAttempt بنجاح."
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        autoDebugState = it.autoDebugState.copy(
                            isDebugging = false,
                            finalStatus = "فشل بدء التصحيح: ${err.message}"
                        )
                    )
                }
            }
        }
    }

    fun applyAutoDebugFix() {
        val workspace = _uiState.value.activeWorkspace ?: return
        val proposal = _uiState.value.autoDebugState.activeProposal ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    autoDebugState = it.autoDebugState.copy(
                        isDebugging = true,
                        currentStepMessage = "جاري تطبيق التعديل على الملفات وإعادة الفحص..."
                    )
                )
            }

            repository.applyAutoDebugFix(workspace.id, proposal).onSuccess { newReport ->
                repository.runWorkspaceTests(workspace.id).onSuccess { tests ->
                    val isFixed = newReport.failedTests == 0 && newReport.confirmedErrorsCount == 0
                    _uiState.update {
                        it.copy(
                            qualityReport = newReport,
                            testResults = tests,
                            autoDebugState = it.autoDebugState.copy(
                                isDebugging = false,
                                activeProposal = null,
                                isFinished = isFixed || it.autoDebugState.attemptCount >= it.autoDebugState.maxAttempts,
                                finalStatus = if (isFixed) "نجح الإصلاح الذاتي! التطبيق الآن مستقر ومطابق لمعايير الجودة."
                                else "تم تطبيق الإصلاح وما زالت بعض الفحوصات تحتاج لمراجعة."
                            )
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        autoDebugState = it.autoDebugState.copy(
                            isDebugging = false,
                            finalStatus = "فشل تطبيق الإصلاح: ${err.message}"
                        )
                    )
                }
            }
        }
    }

    fun cancelAutoDebug() {
        _uiState.update {
            it.copy(
                autoDebugState = AutoDebugState(),
                activeTestingTab = TestingTab.OVERVIEW
            )
        }
    }

    fun sendFailureToCrashIntelligence(testCase: TestCaseResult) {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            repository.sendFailureToCrashIntelligence(workspace.id, testCase).onSuccess { crash ->
                _uiState.update {
                    it.copy(
                        activeTestingTab = TestingTab.CRASH_INTELLIGENCE,
                        chatMessages = it.chatMessages + AgentChatMessage(
                            id = UUID.randomUUID().toString(),
                            sender = AgentMessageSender.SYSTEM,
                            content = "تم تصدير العطل بنجاح إلى Crash Intelligence (معرف: ${crash.id.take(8)}) وتم ربطه بمحرك التشخيص.",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message) }
            }
        }
    }

    fun retestAfterFix() {
        runQualityAudit()
    }


    fun runBuild() {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.runWorkspaceBuild(workspace.id).onSuccess {
                _uiState.update { it.copy(isLoading = false, isPreviewOpen = true) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun searchWorkspace(query: String) {
        val workspace = _uiState.value.activeWorkspace ?: return
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            val results = repository.searchFiles(workspace.id, query)
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    fun toggleDiffViewer(show: Boolean) {
        _uiState.update { it.copy(isDiffViewerOpen = show) }
    }

    fun toggleCheckpoints(show: Boolean) {
        _uiState.update { it.copy(isCheckpointsOpen = show) }
    }

    fun toggleReviewIssues(show: Boolean) {
        _uiState.update { it.copy(isReviewIssuesOpen = show) }
    }

    fun toggleSearch(show: Boolean) {
        _uiState.update { it.copy(isSearchOpen = show) }
    }

    fun togglePanel(panel: StudioPanel) {
        _uiState.update { state ->
            when (panel) {
                StudioPanel.CHAT -> state.copy(isChatOpen = !state.isChatOpen)
                StudioPanel.EXPLORER -> state.copy(isExplorerOpen = !state.isExplorerOpen)
                StudioPanel.PREVIEW -> state.copy(isPreviewOpen = !state.isPreviewOpen)
                StudioPanel.TERMINAL -> state.copy(isTerminalOpen = !state.isTerminalOpen)
                StudioPanel.DIFF -> state.copy(isDiffViewerOpen = !state.isDiffViewerOpen)
                StudioPanel.CHECKPOINTS -> state.copy(isCheckpointsOpen = !state.isCheckpointsOpen)
                StudioPanel.PROBLEMS -> state.copy(isReviewIssuesOpen = !state.isReviewIssuesOpen)
                StudioPanel.CONSOLE -> state.copy(isConsoleOpen = !state.isConsoleOpen)
                StudioPanel.RESOURCES -> state.copy(isResourceModalOpen = !state.isResourceModalOpen)
                else -> state
            }
        }
    }

    // Dev Server & Process Management
    fun startDevServer() {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.startDevServer(workspace.id).onSuccess { runtime ->
                _uiState.update { it.copy(isLoading = false, runtimeState = runtime, isPreviewOpen = true) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun stopDevServer() {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            repository.stopDevServer(workspace.id)
        }
    }

    fun restartDevServer() {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.restartDevServer(workspace.id).onSuccess { runtime ->
                _uiState.update { it.copy(isLoading = false, runtimeState = runtime, previewKey = it.previewKey + 1) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun executeTerminalCommand(cmd: String) {
        if (cmd.isBlank()) return
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            repository.executeTerminalCommand(workspace.id, cmd)
        }
    }

    fun killStuckProcesses() {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            repository.killStuckProcesses(workspace.id)
        }
    }

    // Live Preview Viewport & Console Logs
    fun setPreviewViewport(viewport: PreviewViewport) {
        _uiState.update { it.copy(previewViewport = viewport) }
    }

    fun setPreviewOrientation(orientation: PreviewOrientation) {
        _uiState.update { it.copy(previewOrientation = orientation) }
    }

    fun reloadPreview() {
        _uiState.update { it.copy(previewKey = it.previewKey + 1) }
    }

    fun addBrowserConsoleLog(type: ConsoleLogLevel, message: String, source: String? = null) {
        val newLog = BrowserConsoleLog(
            id = UUID.randomUUID().toString(),
            type = type,
            message = message,
            source = source,
            timestamp = System.currentTimeMillis()
        )
        _uiState.update { it.copy(browserLogs = it.browserLogs + newLog) }
    }

    fun clearBrowserConsoleLogs() {
        _uiState.update { it.copy(browserLogs = emptyList()) }
    }

    // Local Agent Bridge
    fun connectLocalAgent(endpoint: String, token: String) {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            repository.connectLocalAgent(workspace.id, endpoint, token).onSuccess {
                _uiState.update {
                    it.copy(
                        localAgentConfig = it.localAgentConfig.copy(isEnabled = true, endpoint = endpoint, authToken = token),
                        isLocalAgentModalOpen = false
                    )
                }
            }
        }
    }

    fun disconnectLocalAgent() {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            repository.disconnectLocalAgent(workspace.id)
            _uiState.update { it.copy(localAgentConfig = it.localAgentConfig.copy(isEnabled = false)) }
        }
    }

    fun toggleLocalAgentModal(show: Boolean) {
        _uiState.update { it.copy(isLocalAgentModalOpen = show) }
    }

    fun toggleResourceModal(show: Boolean) {
        _uiState.update { it.copy(isResourceModalOpen = show) }
    }

    fun togglePortsModal(show: Boolean) {
        _uiState.update { it.copy(isPortsModalOpen = show) }
    }

    fun toggleConsole(show: Boolean) {
        _uiState.update { it.copy(isConsoleOpen = show) }
    }

    // AI App Builder - Natural Language to Working Application
    fun openAppBuilder(initialPrompt: String = "") {
        _uiState.update {
            it.copy(
                isAppBuilderOpen = true,
                appBuilderPrompt = initialPrompt,
                appBuilderBlueprint = null,
                isAnalyzingRequirements = false,
                appBuildProgress = null
            )
        }
    }

    fun closeAppBuilder() {
        _uiState.update {
            it.copy(
                isAppBuilderOpen = false,
                isAnalyzingRequirements = false,
                appBuildProgress = null
            )
        }
    }

    fun updateAppBuilderPrompt(prompt: String) {
        _uiState.update { it.copy(appBuilderPrompt = prompt) }
    }

    fun updateAppBuilderPlatform(platform: String) {
        _uiState.update { it.copy(appBuilderPreferredPlatform = platform) }
    }

    fun analyzeAppRequirements(prompt: String, preferredPlatform: String? = null) {
        viewModelScope.launch {
            val userId = requireAuthenticatedUserId() ?: return@launch
            _uiState.update { it.copy(isAnalyzingRequirements = true, error = null) }
            repository.analyzeAppRequirements(userId, prompt, preferredPlatform)
                .onSuccess { blueprint ->
                    _uiState.update {
                        it.copy(
                            isAnalyzingRequirements = false,
                            appBuilderBlueprint = blueprint,
                            appBuilderPrompt = prompt
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isAnalyzingRequirements = false,
                            error = "فشل تحليل المتطلبات: ${error.message}"
                        )
                    }
                }
        }
    }

    fun updateBlueprint(blueprint: AppBlueprint) {
        _uiState.update { it.copy(appBuilderBlueprint = blueprint) }
    }

    fun buildApplication(blueprint: AppBlueprint) {
        viewModelScope.launch {
            val userId = requireAuthenticatedUserId() ?: return@launch
            _uiState.update {
                it.copy(
                    appBuildProgress = AppBuildProgress(
                        stage = AppBuildStage.ANALYZING_REQUIREMENTS,
                        progressPercent = 0.1f,
                        currentTask = "تهيئة بيئة العمل وهيكلة المشروع..."
                    )
                )
            }

            repository.buildApplicationFromBlueprint(userId, blueprint) { progress ->
                _uiState.update { it.copy(appBuildProgress = progress) }
            }.onSuccess { createdWorkspace ->
                _uiState.update {
                    it.copy(
                        isAppBuilderOpen = false,
                        appBuildProgress = null,
                        activeWorkspace = createdWorkspace
                    )
                }
                selectWorkspace(createdWorkspace)
                togglePanel(StudioPanel.PREVIEW)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        appBuildProgress = AppBuildProgress(
                            stage = AppBuildStage.FAILED,
                            progressPercent = 0f,
                            currentTask = "فشل في بناء التطبيق: ${error.message}"
                        ),
                        error = error.message
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Git & Version Control Actions
    // -------------------------------------------------------------

    private fun refreshWorkingTreeState(workspaceId: String) {
        viewModelScope.launch {
            val diffs = repository.getWorkingTreeDiff(workspaceId).getOrDefault(emptyList())
            val secrets = repository.scanWorkingTreeSecrets(workspaceId).getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    uncommittedDiffs = diffs,
                    secretWarnings = secrets
                )
            }
        }
    }

    fun toggleVersionControl(open: Boolean? = null) {
        val next = open ?: !_uiState.value.isVersionControlOpen
        _uiState.update { it.copy(isVersionControlOpen = next) }
        val workspace = _uiState.value.activeWorkspace
        if (next && workspace != null) {
            refreshWorkingTreeState(workspace.id)
        }
    }

    fun setGitTab(tab: GitVersionControlTab) {
        _uiState.update { it.copy(activeGitTab = tab) }
    }

    fun initGitRepository() {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.initGitRepository(workspace.id)
                .onSuccess { commit ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isGitInitialized = true,
                            currentBranchName = "main",
                            gitOperationSuccessMessage = "تمت تهيئة مستودع Git بنجاح (الالتزام الأول: ${commit.id.take(8)})"
                        )
                    }
                    refreshWorkingTreeState(workspace.id)
                }.onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun commitChanges(
        message: String,
        author: String = "Developer",
        overrideSecretWarning: Boolean = false
    ) {
        val workspace = _uiState.value.activeWorkspace ?: return
        if (message.isBlank()) {
            _uiState.update { it.copy(error = "رسالة الالتزام لا يمكن أن تكون فارغة.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.commitChanges(
                workspaceId = workspace.id,
                message = message,
                author = author,
                overrideSecretWarning = overrideSecretWarning
            ).onSuccess { commit ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        gitOperationSuccessMessage = "تم إنشاء الالتزام بنجاح: ${commit.id.take(8)}"
                    )
                }
                refreshWorkingTreeState(workspace.id)
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun createBranch(name: String, checkout: Boolean = true) {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.createBranch(workspace.id, name)
                .onSuccess { branch ->
                    if (checkout) {
                        repository.checkoutBranch(workspace.id, branch.name)
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentBranchName = if (checkout) branch.name else it.currentBranchName,
                            gitOperationSuccessMessage = "تم إنشاء الفرع '${branch.name}' بنجاح."
                        )
                    }
                    refreshWorkingTreeState(workspace.id)
                }.onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun checkoutBranch(name: String) {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.checkoutBranch(workspace.id, name)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentBranchName = name,
                            gitOperationSuccessMessage = "تم الانتقال إلى الفرع '$name'."
                        )
                    }
                    refreshWorkingTreeState(workspace.id)
                }.onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun deleteBranch(name: String) {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.deleteBranch(workspace.id, name)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            gitOperationSuccessMessage = "تم حذف الفرع '$name'."
                        )
                    }
                }.onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun restoreToCommit(commitSha: String) {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.restoreToCommit(workspace.id, commitSha)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            gitOperationSuccessMessage = "تمت استعادة الملفات بنجاح إلى الالتزام ${commitSha.take(8)}."
                        )
                    }
                    refreshWorkingTreeState(workspace.id)
                }.onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun approveTaskReview(reviewId: String, commitMessage: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.approveTaskReview(reviewId, commitMessage)
                .onSuccess { commit ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isTaskReviewOpen = false,
                            activeTaskReview = null,
                            gitOperationSuccessMessage = "تم اعتماد التغييرات وإنشاء التزام: ${commit.id.take(8)}"
                        )
                    }
                    _uiState.value.activeWorkspace?.id?.let { refreshWorkingTreeState(it) }
                }.onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun revertTaskReview(reviewId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.revertTaskReview(reviewId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isTaskReviewOpen = false,
                            activeTaskReview = null,
                            gitOperationSuccessMessage = "تم التراجع عن تغييرات المهمة واستعادة النقطة السابقة."
                        )
                    }
                    _uiState.value.activeWorkspace?.id?.let { refreshWorkingTreeState(it) }
                }.onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun branchTaskReview(reviewId: String, newBranchName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.branchTaskReview(reviewId, newBranchName)
                .onSuccess { branch ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isTaskReviewOpen = false,
                            activeTaskReview = null,
                            currentBranchName = branch.name,
                            gitOperationSuccessMessage = "تم تحويل التغييرات إلى الفرع '${branch.name}' وحماية الفرع الرئيسي."
                        )
                    }
                    _uiState.value.activeWorkspace?.id?.let { refreshWorkingTreeState(it) }
                }.onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun dismissTaskReview() {
        _uiState.update { it.copy(isTaskReviewOpen = false) }
    }

    fun pushToGitHub(repoOwner: String, repoName: String) {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            val userId = requireAuthenticatedUserId() ?: return@launch
            _uiState.update { it.copy(isPushPulling = true, error = null) }

            repository.pushToGitHub(
                workspaceId = workspace.id,
                userId = userId,
                repoOwner = repoOwner,
                repoName = repoName,
                branchName = _uiState.value.currentBranchName
            ).onSuccess { prUrl ->
                _uiState.update {
                    it.copy(
                        isPushPulling = false,
                        gitOperationSuccessMessage = "تم دفع التغييرات بنجاح إلى GitHub: $prUrl"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isPushPulling = false, error = err.message) }
            }
        }
    }

    fun pullFromGitHub(repoOwner: String, repoName: String) {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            val userId = requireAuthenticatedUserId() ?: return@launch
            _uiState.update { it.copy(isPushPulling = true, error = null) }

            repository.pullFromGitHub(
                workspaceId = workspace.id,
                userId = userId,
                repoOwner = repoOwner,
                repoName = repoName,
                branchName = _uiState.value.currentBranchName
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isPushPulling = false,
                        gitOperationSuccessMessage = "تم سحب وتحديث الفرع '${it.currentBranchName}' من GitHub."
                    )
                }
                refreshWorkingTreeState(workspace.id)
            }.onFailure { err ->
                _uiState.update { it.copy(isPushPulling = false, error = err.message) }
            }
        }
    }

    fun createGitHubPullRequest(title: String, head: String, base: String, body: String) {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            val userId = requireAuthenticatedUserId() ?: return@launch
            _uiState.update { it.copy(isPushPulling = true, error = null) }

            repository.createGitHubPullRequest(
                workspaceId = workspace.id,
                userId = userId,
                request = GitHubPrRequest(title = title, headBranch = head, baseBranch = base, body = body)
            ).onSuccess { prUrl ->
                _uiState.update {
                    it.copy(
                        isPushPulling = false,
                        gitOperationSuccessMessage = "تم إنشاء طلب السحب (PR) بنجاح: $prUrl"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isPushPulling = false, error = err.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearGitOperationMessage() {
        _uiState.update { it.copy(gitOperationSuccessMessage = null) }
    }

    fun toggleGovernance(show: Boolean) {
        _uiState.update { it.copy(isGovernanceOpen = show) }
    }

    fun toggleApprovalQueue(show: Boolean) {
        _uiState.update { it.copy(isApprovalQueueOpen = show) }
    }

    fun updateWorkspacePermission(level: AgentPermissionLevel) {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            val updated = workspace.copy(permissionLevel = level)
            repository.createWorkspace(updated)
            _uiState.update { it.copy(activeWorkspace = updated) }
            
            repository.addBuildLog(BuildLog(
                id = UUID.randomUUID().toString(),
                workspaceId = workspace.id,
                message = "تم تغيير مستوى صلاحيات الوكيل إلى: ${level.name}",
                type = LogType.WARNING
            ))
        }
    }

    fun handleApproval(approvalId: String, status: ApprovalStatus) {
        viewModelScope.launch {
            repository.updateApprovalStatus(approvalId, status)
            if (status == ApprovalStatus.REJECTED) {
                repository.deletePendingApproval(approvalId)
            }
        }
    }
}
