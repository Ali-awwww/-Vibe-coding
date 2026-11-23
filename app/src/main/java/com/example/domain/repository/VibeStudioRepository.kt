package com.example.domain.repository

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

interface VibeStudioRepository {
    // Workspace Management
    fun getWorkspaces(userId: String): Flow<List<Workspace>>
    suspend fun getWorkspaceById(id: String): Workspace?
    suspend fun createWorkspace(workspace: Workspace): String
    suspend fun deleteWorkspace(id: String)
    suspend fun updateWorkspaceAccess(id: String)

    // File Operations
    fun getFiles(workspaceId: String): Flow<List<WorkspaceFile>>
    suspend fun getFileById(id: String): WorkspaceFile?
    suspend fun getFileByPath(workspaceId: String, path: String): WorkspaceFile?
    suspend fun saveFile(file: WorkspaceFile)
    suspend fun deleteFile(id: String)
    suspend fun createFolder(workspaceId: String, name: String, parentPath: String): String
    suspend fun searchFiles(workspaceId: String, query: String): List<WorkspaceFile>
    suspend fun retrieveRelevantFiles(workspaceId: String, instruction: String, maxFiles: Int = 4): List<WorkspaceFile>

    // AI Generation & Autonomous Assistant
    suspend fun generateProject(
        userId: String,
        description: String,
        workspaceId: String
    ): Result<Unit>

    suspend fun suggestCode(
        userId: String,
        workspaceId: String,
        filePath: String,
        instruction: String
    ): Result<String>

    suspend fun planTask(
        userId: String,
        workspaceId: String,
        prompt: String
    ): Result<ExecutionPlan>

    suspend fun executePlan(
        userId: String,
        workspaceId: String,
        plan: ExecutionPlan,
        onStepProgress: ((PlanStep) -> Unit)? = null
    ): Result<ExecutionSummary>

    suspend fun executeInstruction(
        userId: String,
        workspaceId: String,
        instruction: String,
        mode: AgentExecutionMode,
        onStepProgress: ((PlanStep) -> Unit)? = null
    ): Result<AgentExecutionResult>

    suspend fun executeTeamInstruction(
        userId: String,
        workspaceId: String,
        teamId: String,
        memberId: String?,
        instruction: String,
        mode: AgentExecutionMode
    ): Result<AgentExecutionResult>

    suspend fun reviewWorkspaceCode(
        userId: String,
        workspaceId: String,
        targetFilePath: String? = null
    ): Result<List<CodeIssue>>

    suspend fun autoHealFailure(
        userId: String,
        workspaceId: String,
        errorLogs: String
    ): Result<ExecutionSummary>

    suspend fun runWorkspaceBuild(workspaceId: String): Result<List<BuildLog>>
    suspend fun runWorkspaceTests(workspaceId: String): Result<TestExecutionResult>
    suspend fun runIntelligentQualityAudit(workspaceId: String): Result<QualityAuditReport>
    suspend fun startAutoDebug(workspaceId: String, currentReport: QualityAuditReport, attempt: Int): Result<AutoDebugProposal>
    suspend fun applyAutoDebugFix(workspaceId: String, proposal: AutoDebugProposal): Result<QualityAuditReport>
    suspend fun sendFailureToCrashIntelligence(workspaceId: String, testCase: TestCaseResult): Result<CrashReport>

    // Checkpoints & Rollback
    fun getCheckpoints(workspaceId: String): Flow<List<WorkspaceCheckpoint>>
    suspend fun createCheckpoint(workspaceId: String, title: String, description: String): String
    suspend fun revertToCheckpoint(checkpointId: String): Result<Unit>
    suspend fun deleteCheckpoint(id: String)

    // Context & Logs
    fun getProjectContext(workspaceId: String): Flow<ProjectContext?>
    suspend fun updateProjectContext(context: ProjectContext)
    fun getBuildLogs(workspaceId: String): Flow<List<BuildLog>>
    suspend fun addBuildLog(log: BuildLog)
    suspend fun clearLogs(workspaceId: String)

    // Secure Workspace Sandbox & Live Preview
    fun getSandboxRuntimeState(workspaceId: String): Flow<SandboxRuntimeState?>
    suspend fun startDevServer(workspaceId: String): Result<SandboxRuntimeState>
    suspend fun stopDevServer(workspaceId: String): Result<Unit>
    suspend fun restartDevServer(workspaceId: String): Result<SandboxRuntimeState>
    suspend fun executeTerminalCommand(workspaceId: String, command: String): Result<TerminalCommandResult>
    fun getTerminalHistory(workspaceId: String): Flow<List<TerminalCommandResult>>
    fun getBuildOperations(workspaceId: String): Flow<List<BuildOperation>>
    suspend fun generateLivePreview(workspaceId: String): Result<String>
    suspend fun killStuckProcesses(workspaceId: String): Result<Unit>
    suspend fun connectLocalAgent(workspaceId: String, endpoint: String, token: String): Result<LocalAgentStatus>
    suspend fun disconnectLocalAgent(workspaceId: String): Result<Unit>

    // AI App Builder - Natural Language to Working Application
    suspend fun analyzeAppRequirements(
        userId: String,
        prompt: String,
        preferredPlatform: String? = null
    ): Result<AppBlueprint>

    suspend fun buildApplicationFromBlueprint(
        userId: String,
        blueprint: AppBlueprint,
        onProgress: ((AppBuildProgress) -> Unit)? = null
    ): Result<Workspace>

    // Git & Version Control
    fun getGitCommits(workspaceId: String): Flow<List<WorkspaceGitCommit>>
    fun getGitBranches(workspaceId: String): Flow<List<WorkspaceGitBranch>>
    fun getTaskReviews(workspaceId: String): Flow<List<WorkspaceTaskReview>>
    suspend fun getGitRepositoryState(workspaceId: String): Result<GitRepositoryState>
    suspend fun initGitRepository(workspaceId: String, defaultBranch: String = "main"): Result<WorkspaceGitCommit>
    suspend fun commitChanges(
        workspaceId: String,
        message: String,
        author: String = "Developer",
        associatedTaskId: String? = null,
        associatedBuildId: String? = null,
        associatedCrashId: String? = null,
        associatedTestResultId: String? = null,
        overrideSecretWarning: Boolean = false
    ): Result<WorkspaceGitCommit>
    suspend fun createBranch(workspaceId: String, branchName: String, fromBranch: String? = null): Result<WorkspaceGitBranch>
    suspend fun checkoutBranch(workspaceId: String, branchName: String): Result<Unit>
    suspend fun deleteBranch(workspaceId: String, branchName: String): Result<Unit>
    suspend fun restoreToCommit(workspaceId: String, commitSha: String): Result<Unit>
    suspend fun scanWorkingTreeSecrets(workspaceId: String): Result<List<SecretAuditWarning>>
    suspend fun getWorkingTreeDiff(workspaceId: String): Result<List<FileDiff>>
    suspend fun approveTaskReview(reviewId: String, commitMessage: String? = null): Result<WorkspaceGitCommit>
    suspend fun revertTaskReview(reviewId: String): Result<Unit>
    suspend fun branchTaskReview(reviewId: String, newBranchName: String): Result<WorkspaceGitBranch>
    suspend fun pushToGitHub(workspaceId: String, userId: String, repoOwner: String, repoName: String, branchName: String): Result<String>
    suspend fun pullFromGitHub(workspaceId: String, userId: String, repoOwner: String, repoName: String, branchName: String): Result<List<WorkspaceFile>>
    suspend fun createGitHubPullRequest(workspaceId: String, userId: String, request: GitHubPrRequest): Result<String>

    // Unified AI Development & Crash Intelligence Closed-Loop
    suspend fun setupWorkspaceMonitoring(workspaceId: String): Result<WorkspaceMonitoringProfile>
    suspend fun getWorkspaceMonitoringProfile(workspaceId: String): WorkspaceMonitoringProfile?
    suspend fun startTrackedTestSession(workspaceId: String, buildId: String? = null): Result<TestSession>
    suspend fun recordSimulatedCrash(
        workspaceId: String,
        exceptionType: String,
        message: String,
        stackTrace: String
    ): Result<CrashReport>
    suspend fun diagnoseCrashAndGenerateFix(crashId: String): Result<Pair<AIDiagnosis, FixSuggestion>>
    suspend fun applyClosedLoopFix(fixId: String): Result<WorkspaceGitCommit>
    suspend fun retestAndVerifyClosedLoop(crashId: String, fixId: String): Result<ClosedLoopVerificationOutcome>
    suspend fun getDevDebugState(workspaceId: String): Result<DevDebugCenterState>

    // Governance & Security
    suspend fun getWorkspace(id: String): Workspace?
    suspend fun saveActivityLog(log: AgentActivityLog)
    fun getActivityLogs(workspaceId: String): Flow<List<AgentActivityLog>>
    suspend fun savePendingApproval(approval: PendingApproval)
    fun getPendingApproval(id: String): Flow<PendingApproval?>
    fun getPendingApprovals(workspaceId: String): Flow<List<PendingApproval>>
    suspend fun updateApprovalStatus(id: String, status: ApprovalStatus)
    suspend fun deletePendingApproval(id: String)
    suspend fun getDefaultModel(userId: String): AIModel?
}
