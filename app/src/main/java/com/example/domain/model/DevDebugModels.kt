package com.example.domain.model

/**
 * Domain models for the Unified AI Development and Crash Intelligence Loop.
 * Integrates Workspace scaffolding, build tracking, test sessions,
 * crash intake, AI diagnosis, automated workspace patching, and re-test verification.
 */

data class WorkspaceMonitoringProfile(
    val workspaceId: String,
    val projectId: String,
    val applicationId: String,
    val techStack: String,
    val targetPlatform: String,
    val sdkSnippet: String,
    val isSdkConfigured: Boolean,
    val buildSettingsSummary: String
)

data class ActiveTestRunContext(
    val buildId: String,
    val commitSha: String,
    val testSessionId: String,
    val appVersion: String,
    val startedAt: Long,
    val status: TestSessionStatus,
    val eventCount: Int = 0
)

enum class IncidentStatus(val labelAr: String, val labelEn: String) {
    DETECTED("عطل مكتشف", "Crash Detected"),
    DIAGNOSED("تم التحليل الجذري", "Diagnosed"),
    FIX_PROPOSED("اقتراح الإصلاح جاهز", "Fix Proposed"),
    FIX_APPLIED("تم تطبيق الإصلاح في المشروع", "Fix Applied"),
    VERIFIED("تم التحقق من الإصلاح بنجاح", "Fix Verified"),
    REPRODUCED("العطل ما زال يتكرر", "Crash Reproduced")
}

data class ClosedLoopIncident(
    val crashReport: CrashReport,
    val diagnosis: AIDiagnosis? = null,
    val fixSuggestion: FixSuggestion? = null,
    val proposedDiff: FileDiff? = null,
    val verificationRecord: CrashVerificationRecord? = null,
    val faultyFilePath: String? = null,
    val faultyLineNumber: Int? = null,
    val status: IncidentStatus = IncidentStatus.DETECTED
)

data class ClosedLoopVerificationOutcome(
    val isFixed: Boolean,
    val status: VerificationStatus,
    val previousBuildId: String,
    val newBuildId: String,
    val comparisonSummary: String,
    val verificationRecord: CrashVerificationRecord
)

data class DevDebugCenterState(
    val selectedWorkspace: Workspace? = null,
    val monitoringProfile: WorkspaceMonitoringProfile? = null,
    val activeRunContext: ActiveTestRunContext? = null,
    val recentBuildLogs: List<BuildLog> = emptyList(),
    val incidents: List<ClosedLoopIncident> = emptyList(),
    val activeIncident: ClosedLoopIncident? = null,
    val verifications: List<CrashVerificationRecord> = emptyList(),
    val recentCommits: List<WorkspaceGitCommit> = emptyList(),
    val isLoading: Boolean = false,
    val isFixing: Boolean = false,
    val isVerifying: Boolean = false,
    val actionFeedbackMessage: String? = null,
    val error: String? = null
)
