package com.example.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "workspaces")
data class Workspace(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val description: String = "",
    val type: WorkspaceType = WorkspaceType.WEB_APP,
    val techStack: String = "",
    val targetPlatform: String = "",
    val ownerId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val permissionLevel: AgentPermissionLevel = AgentPermissionLevel.READ
)

enum class WorkspaceType {
    WEB_SITE, WEB_APP, ANDROID, IOS, FLUTTER, REACT_NATIVE, BACKEND_API, DESKTOP
}

@Entity(
    tableName = "workspace_files",
    foreignKeys = [
        ForeignKey(
            entity = Workspace::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workspaceId", "path"], unique = true)]
)
data class WorkspaceFile(
    @PrimaryKey val id: String = "",
    val workspaceId: String = "",
    val name: String = "",
    val path: String = "", // full path from root, e.g. "src/main/kotlin/App.kt"
    val content: String = "",
    val extension: String = "",
    val isFolder: Boolean = false,
    val parentId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "workspace_context")
data class ProjectContext(
    @PrimaryKey val workspaceId: String = "",
    val goals: String = "",
    val architecturalDecisions: String = "",
    val techStackDetails: String = "",
    val completedTasks: String = "",
    val pendingTasks: String = "",
    val knownIssues: String = "",
    val userInstructions: String = "",
    val aiMemory: String = "" // Serialized JSON of long-term AI context
)

@Entity(tableName = "workspace_build_logs")
data class BuildLog(
    @PrimaryKey val id: String = "",
    val workspaceId: String = "",
    val message: String = "",
    val type: LogType = LogType.INFO,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LogType {
    INFO, WARNING, ERROR, SUCCESS, TERMINAL_INPUT, TERMINAL_OUTPUT
}

@Entity(
    tableName = "workspace_checkpoints",
    foreignKeys = [
        ForeignKey(
            entity = Workspace::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workspaceId"])]
)
data class WorkspaceCheckpoint(
    @PrimaryKey val id: String = "",
    val workspaceId: String = "",
    val title: String = "",
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val filesSnapshotJson: String = "",
    val totalFiles: Int = 0
)

enum class AgentExecutionMode {
    PLAN,   // يخطط للتنفيذ ويعرض الخطوات دون تعديل المشروع
    AGENT,  // ينفذ التعديلات داخل حدود المشروع، مع طلب الموافقة على العمليات الحساسة
    REVIEW  // يراجع الكود والتغييرات ويكتشف الأخطاء المحتملة دون تعديل تلقائي
}

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED
}

data class PlanStep(
    val id: String = "",
    val order: Int = 0,
    val title: String = "",
    val description: String = "",
    val status: StepStatus = StepStatus.PENDING,
    val targetFiles: List<String> = emptyList(),
    val executionNotes: String = ""
)

enum class PlanStatus {
    DRAFT,
    APPROVED,
    EXECUTING,
    COMPLETED,
    FAILED
}

data class ExecutionPlan(
    val id: String = "",
    val workspaceId: String = "",
    val userPrompt: String = "",
    val intent: String = "",
    val detectedTech: String = "",
    val affectedFiles: List<String> = emptyList(),
    val steps: List<PlanStep> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val status: PlanStatus = PlanStatus.DRAFT
)

enum class DiffChangeType {
    CREATED,
    MODIFIED,
    DELETED
}

enum class DiffLineType {
    ADD,
    REMOVE,
    CONTEXT
}

data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLineNum: Int? = null,
    val newLineNum: Int? = null
)

data class FileDiff(
    val path: String,
    val changeType: DiffChangeType,
    val oldContent: String,
    val newContent: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val diffLines: List<DiffLine> = emptyList()
)

enum class IssueSeverity {
    INFO,
    WARNING,
    ERROR,
    SECURITY
}

data class CodeIssue(
    val id: String = "",
    val filePath: String = "",
    val line: Int = 1,
    val severity: IssueSeverity = IssueSeverity.WARNING,
    val title: String = "",
    val description: String = "",
    val recommendation: String = ""
)

data class TestExecutionResult(
    val totalTests: Int = 0,
    val passed: Int = 0,
    val failed: Int = 0,
    val isSuccess: Boolean = true,
    val outputLogs: List<String> = emptyList(),
    val durationMs: Long = 0
)

data class ExecutionSummary(
    val checkpointId: String = "",
    val changedFiles: List<FileDiff> = emptyList(),
    val createdCount: Int = 0,
    val modifiedCount: Int = 0,
    val deletedCount: Int = 0,
    val summaryText: String = "",
    val detectedIssues: List<CodeIssue> = emptyList(),
    val testResults: TestExecutionResult? = null
)

enum class SensitiveActionType {
    DELETE_FILE,
    RESET_WORKSPACE
}

data class PendingSensitiveAction(
    val actionId: String = "",
    val type: SensitiveActionType = SensitiveActionType.DELETE_FILE,
    val targetPath: String = "",
    val reason: String = "",
    val confirmationPrompt: String = ""
)

enum class AgentPermissionLevel {
    READ,    // قراءة الملفات وتحليلها فقط
    EDIT,    // إنشاء الملفات وتعديلها ضمن المشروع
    EXECUTE  // تشغيل أوامر البناء والاختبارات والتشغيل داخل Sandbox
}

@Entity(
    tableName = "agent_activity_logs",
    foreignKeys = [
        ForeignKey(
            entity = Workspace::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workspaceId"])]
)
data class AgentActivityLog(
    @PrimaryKey val id: String = "",
    val workspaceId: String = "",
    val agentId: String = "default_agent",
    val actionType: String = "", // READ, CREATE, MODIFY, DELETE, EXECUTE, GIT_OP
    val target: String = "", // Path or command
    val details: String = "", // Summary or diff
    val timestamp: Long = System.currentTimeMillis(),
    val isUserApproved: Boolean = true,
    val permissionLevelUsed: AgentPermissionLevel = AgentPermissionLevel.READ
)

enum class ApprovalStatus {
    PENDING, APPROVED, REJECTED
}

@Entity(
    tableName = "pending_approvals",
    foreignKeys = [
        ForeignKey(
            entity = Workspace::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workspaceId"])]
)
data class PendingApproval(
    @PrimaryKey val id: String = "",
    val workspaceId: String = "",
    val actionType: String = "",
    val target: String = "",
    val reason: String = "",
    val diffJson: String? = null,
    val command: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: ApprovalStatus = ApprovalStatus.PENDING,
    val requesterAgentId: String = "default_agent"
)

data class AgentExecutionResult(
    val mode: AgentExecutionMode,
    val plan: ExecutionPlan? = null,
    val summary: ExecutionSummary? = null,
    val reviewIssues: List<CodeIssue> = emptyList(),
    val pendingAction: PendingSensitiveAction? = null,
    val pendingApproval: PendingApproval? = null, // Added for governance
    val responseMessage: String = ""
)

enum class AgentMessageSender {
    USER,
    AGENT,
    TEAM_MEMBER,
    SYSTEM
}

data class AgentChatMessage(
    val id: String = "",
    val sender: AgentMessageSender = AgentMessageSender.USER,
    val senderName: String? = null,
    val senderRole: String? = null,
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val mode: AgentExecutionMode = AgentExecutionMode.AGENT,
    val plan: ExecutionPlan? = null,
    val summary: ExecutionSummary? = null,
    val reviewIssues: List<CodeIssue> = emptyList(),
    val isError: Boolean = false
)

data class VibeFileNode(
    val file: WorkspaceFile,
    val children: List<VibeFileNode> = emptyList()
)

enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
    RESTARTING
}

enum class LocalAgentStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SYNCING
}

data class DetectedTechRuntime(
    val name: String = "Web Standard",
    val command: String = "npm run dev",
    val buildCommand: String = "npm run build",
    val testCommand: String = "npm test",
    val configFile: String = "package.json",
    val defaultPort: Int = 3000,
    val isBrowserPreviewSupported: Boolean = true,
    val isNativeApp: Boolean = false,
    val targetPlatform: String = "Web",
    val packageManager: String = "npm"
)

data class SandboxRuntimeState(
    val workspaceId: String = "",
    val status: ServerStatus = ServerStatus.STOPPED,
    val port: Int = 3000,
    val localUrl: String = "http://localhost:3000",
    val detectedRuntime: DetectedTechRuntime = DetectedTechRuntime(),
    val activeProcessId: String? = null,
    val cpuUsagePercent: Float = 14.5f,
    val memoryUsageMb: Int = 128,
    val memoryLimitMb: Int = 512,
    val storageUsageMb: Float = 24.2f,
    val storageLimitMb: Float = 100f,
    val activeProcessesCount: Int = 0,
    val isLocalAgentMode: Boolean = false,
    val localAgentStatus: LocalAgentStatus = LocalAgentStatus.DISCONNECTED,
    val localAgentPort: Int = 8974,
    val startedAt: Long? = null,
    val lastOutput: String = ""
)

enum class ProcessStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    KILLED
}

data class TerminalCommandResult(
    val id: String = "",
    val workspaceId: String = "",
    val command: String = "",
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = 0,
    val durationMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true,
    val status: ProcessStatus = ProcessStatus.COMPLETED
)

enum class BuildOpType {
    DEV_SERVER,
    BUILD,
    TEST,
    LINT,
    DEPENDENCY_INSTALL,
    CUSTOM_COMMAND
}

enum class BuildOpStatus {
    SUCCESS,
    FAILED,
    ABORTED,
    RUNNING
}

data class BuildOperation(
    val id: String = "",
    val workspaceId: String = "",
    val operationType: BuildOpType = BuildOpType.BUILD,
    val command: String = "",
    val status: BuildOpStatus = BuildOpStatus.SUCCESS,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val durationMs: Long? = null,
    val stdout: String = "",
    val stderr: String = "",
    val artifactsCount: Int = 0
)

enum class PreviewViewport {
    MOBILE,   // 375 x 667
    TABLET,   // 768 x 1024
    DESKTOP   // 100% Responsive
}

enum class PreviewOrientation {
    PORTRAIT,
    LANDSCAPE
}

enum class ConsoleLogLevel {
    LOG,
    INFO,
    WARN,
    ERROR
}

data class BrowserConsoleLog(
    val id: String = "",
    val type: ConsoleLogLevel = ConsoleLogLevel.LOG,
    val message: String = "",
    val source: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class LocalAgentConfig(
    val isEnabled: Boolean = false,
    val endpoint: String = "http://localhost:8974",
    val authToken: String = "",
    val localDirectoryPath: String = "",
    val lastSyncAt: Long = 0
)

enum class StudioPanel {
    CHAT, EXPLORER, PREVIEW, TERMINAL, LOGS, PROBLEMS, GIT, TESTING, DIFF, CHECKPOINTS, CONSOLE, RESOURCES
}

data class StudioUiState(
    val activeWorkspace: Workspace? = null,
    val openFiles: List<WorkspaceFile> = emptyList(),
    val selectedFileId: String? = null,
    val isChatOpen: Boolean = true,
    val isExplorerOpen: Boolean = true,
    val isPreviewOpen: Boolean = false,
    val isTerminalOpen: Boolean = false,
    val logs: List<BuildLog> = emptyList(),
    val fileTree: List<VibeFileNode> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    
    // AI Autonomous Agent State
    val executionMode: AgentExecutionMode = AgentExecutionMode.AGENT,
    val activePlan: ExecutionPlan? = null,
    val currentExecutionSummary: ExecutionSummary? = null,
    val currentDiffs: List<FileDiff> = emptyList(),
    val reviewIssues: List<CodeIssue> = emptyList(),
    val checkpoints: List<WorkspaceCheckpoint> = emptyList(),
    val pendingSensitiveAction: PendingSensitiveAction? = null,
    val searchQuery: String = "",
    val searchResults: List<WorkspaceFile> = emptyList(),
    val testResults: TestExecutionResult? = null,
    val chatMessages: List<AgentChatMessage> = emptyList(),
    val isDiffViewerOpen: Boolean = false,
    val isCheckpointsOpen: Boolean = false,
    val isSearchOpen: Boolean = false,
    val isReviewIssuesOpen: Boolean = false,

    // Secure Workspace Execution & Live Preview
    val runtimeState: SandboxRuntimeState? = null,
    val terminalResults: List<TerminalCommandResult> = emptyList(),
    val terminalInput: String = "",
    val browserLogs: List<BrowserConsoleLog> = emptyList(),
    val previewViewport: PreviewViewport = PreviewViewport.DESKTOP,
    val previewOrientation: PreviewOrientation = PreviewOrientation.PORTRAIT,
    val previewKey: Int = 0,
    val buildOperations: List<BuildOperation> = emptyList(),
    val localAgentConfig: LocalAgentConfig = LocalAgentConfig(),
    val isLocalAgentModalOpen: Boolean = false,
    val isResourceModalOpen: Boolean = false,
    val isPortsModalOpen: Boolean = false,
    val isConsoleOpen: Boolean = false,

    // AI App Builder State
    val isAppBuilderOpen: Boolean = false,
    val appBuilderPrompt: String = "",
    val appBuilderPreferredPlatform: String = "Web Full-Stack",
    val appBuilderBlueprint: AppBlueprint? = null,
    val isAnalyzingRequirements: Boolean = false,
    val appBuildProgress: AppBuildProgress? = null,

    // AI Testing & Quality Assurance Engine
    val isTestingPanelOpen: Boolean = false,
    val qualityReport: QualityAuditReport? = null,
    val isAuditRunning: Boolean = false,
    val autoDebugState: AutoDebugState = AutoDebugState(),
    val activeTestingTab: TestingTab = TestingTab.OVERVIEW,

    // Git & Version Control State
    val isVersionControlOpen: Boolean = false,
    val activeGitTab: GitVersionControlTab = GitVersionControlTab.HISTORY,
    val isGitInitialized: Boolean = false,
    val currentBranchName: String = "main",
    val gitBranches: List<WorkspaceGitBranch> = emptyList(),
    val gitCommits: List<WorkspaceGitCommit> = emptyList(),
    val uncommittedDiffs: List<FileDiff> = emptyList(),
    val secretWarnings: List<SecretAuditWarning> = emptyList(),
    val activeTaskReview: WorkspaceTaskReview? = null,
    val isTaskReviewOpen: Boolean = false,
    val linkedGitHubRepo: RepositoryMapping? = null,
    val gitHubConnection: GitHubConnection? = null,
    val isPushPulling: Boolean = false,
    val gitOperationSuccessMessage: String? = null,

    // Governance & Agent Security State
    val agentActivityLogs: List<AgentActivityLog> = emptyList(),
    val pendingApprovals: List<PendingApproval> = emptyList(),
    val isGovernanceOpen: Boolean = false,
    val isApprovalQueueOpen: Boolean = false,

    // Agent Model Status
    val activeAgentModel: String = "Auto (Free)",
    val agentIsFree: Boolean = true,
    val agentCapabilities: List<AICapability> = emptyList(),
    
    // Team Support
    val activeTeam: ModelTeam? = null,
    val availableTeams: List<ModelTeam> = emptyList(),
    val selectedMemberId: String? = null
)

data class AppBlueprint(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val appType: String = "",
    val targetPlatform: String = "Web Full-Stack",
    val suggestedTechStack: String = "Next.js 14 + Tailwind + TypeScript",
    val techStackJustification: String = "",
    val coreFeatures: List<String> = emptyList(),
    val screens: List<BlueprintScreen> = emptyList(),
    val databaseSchema: List<BlueprintEntity> = emptyList(),
    val userRoles: List<String> = emptyList(),
    val externalServices: List<String> = emptyList(),
    val securityRequirements: List<String> = emptyList(),
    val isCrashIntelligenceIntegrated: Boolean = true
)

data class BlueprintScreen(
    val name: String = "",
    val route: String = "",
    val purpose: String = ""
)

data class BlueprintEntity(
    val name: String = "",
    val fields: List<String> = emptyList(),
    val relations: String = ""
)

enum class AppBuildStage {
    IDLE,
    ANALYZING_REQUIREMENTS,
    BLUEPRINT_READY,
    SCAFFOLDING_FILES,
    CREATING_UI_COMPONENTS,
    WIRING_DATA_SERVICES,
    CONFIGURING_CRASH_INTELLIGENCE,
    STARTING_PREVIEW,
    COMPLETED,
    FAILED
}

data class AppBuildProgress(
    val stage: AppBuildStage = AppBuildStage.IDLE,
    val progressPercent: Float = 0f,
    val currentTask: String = ""
)

// ============================================================================
// AI Testing & Quality Assurance Engine Domain Models
// ============================================================================

enum class TestType(val titleAr: String, val titleEn: String) {
    SYNTAX_CHECK("فحص بناء الجمل والتركيب", "Syntax Checks"),
    TYPE_CHECK("فحص الأنواع وتطابق الاستيراد", "Type & Import Checking"),
    LINTING("فحص معايير التنسيق والأنماط", "Linting & Style Guidelines"),
    UNIT_TEST("اختبارات الوحدات البرمجية", "Unit Tests"),
    INTEGRATION_TEST("اختبارات التكامل وتدفق البيانات", "Integration Tests"),
    BUILD_TEST("فحص تجميع الحزم والبناء", "Build & Bundle Tests"),
    API_CONTRACT_TEST("فحص واجهات البرمجة ومسارات API", "API Contract Tests"),
    UI_RENDER_TEST("فحص واجهات العرض والتجاوب", "UI & Responsive Tests")
}

enum class TestStatus {
    PASSED,
    FAILED,
    SKIPPED,
    RUNNING
}

data class TestCaseResult(
    val id: String = "",
    val suiteName: String = "",
    val name: String = "",
    val type: TestType = TestType.SYNTAX_CHECK,
    val status: TestStatus = TestStatus.PASSED,
    val targetFile: String? = null,
    val lineNumber: Int? = null,
    val errorMessage: String? = null,
    val stackTrace: String? = null,
    val durationMs: Long = 0,
    val suggestedFix: String? = null
)

enum class QualityCategory(val titleAr: String, val titleEn: String) {
    LOGICAL_ERROR("أخطاء منطقية مؤكدة", "Logical Errors"),
    SECURITY_VULNERABILITY("ثغرات أمنية وبيانات حساسة", "Security Vulnerabilities"),
    PERFORMANCE_ISSUE("مشاكل الأداء والذاكرة", "Performance & Memory"),
    UNUSED_DEPENDENCY("اعتماديات غير مستخدمة", "Unused Dependencies"),
    DEAD_CODE("ملفات وأكواد غير مستخدمة", "Dead Code & Files"),
    NULL_SAFETY("سلامة القيم الخالية والحالات الفارغة", "Null Safety & Empty States"),
    COMPATIBILITY("التوافق مع المنصات والمعايير", "Platform Compatibility")
}

data class QualityFinding(
    val id: String = "",
    val category: QualityCategory = QualityCategory.LOGICAL_ERROR,
    val severity: IssueSeverity = IssueSeverity.WARNING,
    val filePath: String = "",
    val line: Int = 1,
    val title: String = "",
    val description: String = "",
    val recommendation: String = "",
    val codeSnippet: String? = null
)

enum class ProductionReadiness(val labelAr: String, val labelEn: String) {
    READY("جاهز للإنتاج", "Production Ready"),
    NEEDS_REVIEW("يحتاج مراجعة وتحسينات", "Needs Review"),
    NOT_READY("غير جاهز - توجد أخطاء حرجة", "Not Ready"),
    UNTESTED("لم يتم الفحص بعد", "Untested")
}

data class QualityAuditReport(
    val id: String = "",
    val workspaceId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val techStack: String = "",
    val testResults: List<TestCaseResult> = emptyList(),
    val totalTests: Int = 0,
    val passedTests: Int = 0,
    val failedTests: Int = 0,
    val skippedTests: Int = 0,
    val findings: List<QualityFinding> = emptyList(),
    val confirmedErrorsCount: Int = 0,
    val warningsCount: Int = 0,
    val suggestionsCount: Int = 0,
    val readiness: ProductionReadiness = ProductionReadiness.UNTESTED,
    val summaryMessage: String = "",
    val associatedCrashReportId: String? = null,
    val durationMs: Long = 0
)

data class AutoDebugProposal(
    val attempt: Int = 1,
    val maxAttempts: Int = 3,
    val diagnosedRootCause: String = "",
    val evidenceFiles: List<String> = emptyList(),
    val explanation: String = "",
    val proposedDiffs: List<FileDiff> = emptyList(),
    val associatedError: String = "",
    val isStuckOrNoProgress: Boolean = false
)

data class AutoDebugState(
    val isDebugging: Boolean = false,
    val attemptCount: Int = 0,
    val maxAttempts: Int = 3,
    val currentStepMessage: String = "",
    val activeProposal: AutoDebugProposal? = null,
    val historyAttempts: List<String> = emptyList(),
    val isFinished: Boolean = false,
    val finalStatus: String? = null
)

enum class TestingTab(val titleAr: String, val titleEn: String) {
    OVERVIEW("نظرة عامة", "Overview"),
    TESTS("الاختبارات", "Tests"),
    FINDINGS("الجودة والأمان", "Findings"),
    CRASH_INTELLIGENCE("تكامل الأعطال", "Crash Intelligence"),
    AUTO_DEBUG("التصحيح الذاتي", "Auto Debug")
}

// -------------------------------------------------------------
// Git & Version Control Entities and Models
// -------------------------------------------------------------

@Entity(
    tableName = "workspace_git_commits",
    foreignKeys = [
        ForeignKey(
            entity = Workspace::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workspaceId"]), Index(value = ["branch"])]
)
data class WorkspaceGitCommit(
    @PrimaryKey val id: String = "", // 40-character SHA
    val workspaceId: String = "",
    val branch: String = "main",
    val message: String = "",
    val author: String = "Developer",
    val timestamp: Long = System.currentTimeMillis(),
    val parentCommitSha: String? = null,
    val treeSnapshotJson: String = "",
    val changedFilesCount: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0,
    val associatedTaskId: String? = null,
    val associatedBuildId: String? = null,
    val associatedCrashId: String? = null,
    val associatedTestResultId: String? = null,
    val isPushedToRemote: Boolean = false
)

@Entity(
    tableName = "workspace_git_branches",
    foreignKeys = [
        ForeignKey(
            entity = Workspace::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workspaceId"])]
)
data class WorkspaceGitBranch(
    @PrimaryKey val id: String = "", // "${workspaceId}_${name}"
    val workspaceId: String = "",
    val name: String = "main",
    val headCommitSha: String = "",
    val isDefault: Boolean = false,
    val isCurrent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val createdFromBranch: String? = null
)

@Entity(
    tableName = "workspace_task_reviews",
    foreignKeys = [
        ForeignKey(
            entity = Workspace::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workspaceId"])]
)
data class WorkspaceTaskReview(
    @PrimaryKey val id: String = "",
    val workspaceId: String = "",
    val taskPrompt: String = "",
    val explanation: String = "",
    val checkpointId: String = "",
    val branchName: String = "main",
    val diffsJson: String = "",
    val affectedTestsJson: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, REVERTED, BRANCHED
    val timestamp: Long = System.currentTimeMillis()
)

data class SecretAuditWarning(
    val filePath: String,
    val line: Int,
    val secretType: String,
    val snippet: String,
    val isBlocking: Boolean = true
)

enum class GitVersionControlTab(val titleAr: String, val titleEn: String) {
    HISTORY("سجل الالتزامات (Commits)", "History"),
    BRANCHES("الفروع (Branches)", "Branches"),
    WORKING_TREE("التغييرات غير المثبتة (Stage)", "Working Tree"),
    GITHUB("مزامنة GitHub", "GitHub Sync"),
    CHECKPOINTS("نقاط الاستعادة (Checkpoints)", "Checkpoints")
}

data class GitMergeResult(
    val isSuccess: Boolean,
    val hasConflicts: Boolean,
    val conflictFiles: List<String> = emptyList(),
    val mergedCommitSha: String? = null,
    val message: String = ""
)

data class GitHubPrRequest(
    val title: String,
    val body: String,
    val headBranch: String,
    val baseBranch: String = "main"
)

data class GitRepositoryState(
    val isInitialized: Boolean = false,
    val currentBranch: String = "main",
    val branches: List<WorkspaceGitBranch> = emptyList(),
    val recentCommits: List<WorkspaceGitCommit> = emptyList(),
    val uncommittedDiffs: List<FileDiff> = emptyList(),
    val stagedFiles: List<String> = emptyList(),
    val secretWarnings: List<SecretAuditWarning> = emptyList(),
    val githubConnection: GitHubConnection? = null,
    val linkedRepo: RepositoryMapping? = null,
    val unpushedCommitsCount: Int = 0
)

