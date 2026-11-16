package com.example.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName
import java.util.Date

enum class TeamRole(val labelAr: String, val labelEn: String) {
    TEAM_LEADER("قائد الفريق", "Team Leader"),
    SOFTWARE_ARCHITECT("مهندس معماري", "Software Architect"),
    SENIOR_DEVELOPER("مطور أول", "Senior Developer"),
    FRONTEND_DEVELOPER("مطور واجهة أمامية", "Frontend Developer"),
    BACKEND_DEVELOPER("مطور خلفية", "Backend Developer"),
    DATABASE_ENGINEER("مهندس قواعد بيانات", "Database Engineer"),
    CODE_REVIEWER("مراجع كود", "Code Reviewer"),
    SECURITY_AUDITOR("مدقق أمني", "Security Auditor"),
    QA_ENGINEER("مهندس جودة", "QA Engineer"),
    DEBUGGING_SPECIALIST("أخصائي تصحيح أخطاء", "Debugging Specialist"),
    PERFORMANCE_ENGINEER("مهندس أداء", "Performance Engineer"),
    DOCUMENTATION_WRITER("كاتب توثيق", "Documentation Writer"),
    RESEARCH_AGENT("وكيل بحث", "Research Agent"),
    CRASH_ANALYST("محلل انهيارات", "Crash Analyst"),
    STACK_TRACE_SPECIALIST("أخصائي تتبع المكدس", "Stack Trace Specialist"),
    SOURCE_CODE_INVESTIGATOR("محقق الكود المصدري", "Source Code Investigator"),
    ROOT_CAUSE_ANALYST("محلل السبب الجذري", "Root Cause Analyst"),
    FIX_DEVELOPER("مطور الحلول", "Fix Developer"),
    CUSTOM("دور مخصص", "Custom")
}

enum class CollaborationStyle(val labelAr: String, val labelEn: String) {
    SEQUENTIAL("تتابعي", "Sequential"),
    PARALLEL("متوازي", "Parallel"),
    DEBATE("نقاشي", "Debate"),
    HIERARCHICAL("هرمي", "Hierarchical"),
    REVIEW_CRITIQUE("مراجعة ونقد", "Review & Critique"),
    VOTING("تصويت", "Voting"),
    ADAPTIVE("تكيفي", "Adaptive"),
    ORCHESTRATED("منسق (DAG)", "Orchestrated"),
    VIBE_DEVELOPMENT("بناء التطبيقات (Vibe Dev)", "Vibe Development"),
    CRASH_INVESTIGATION("تحقيق في الانهيارات (Crash Investigation)", "Crash Investigation")
}

data class TeamGovernanceSettings(
    val freeOnly: Boolean = true,
    val maxTeamCostUsd: Double = 5.0,
    val maxTotalTokens: Int = 100_000,
    val maxApiRequests: Int = 50,
    val maxParallelRequests: Int = 3,
    val maxRetryAttempts: Int = 2,
    val maxExecutionTimeMinutes: Int = 10,
    val enableLoadBalancing: Boolean = false,
    val autoFallbackToFree: Boolean = true,
    val notifyOnQuotaExhaustion: Boolean = true
)

data class TeamSettings(
    val maxReviewRounds: Int = 2,
    val maxConcurrentRequests: Int = 3,
    val debateTerminationCondition: String = "Consensus or Max Rounds",
    val acceptanceThreshold: Float = 0.8f,
    val timeoutMinutes: Int = 5,
    val stopOnFailure: Boolean = true
)

enum class MemberStatus {
    IDLE, ASSIGNED, WORKING, WAITING, COMPLETED, FAILED, RATE_LIMITED
}

data class TeamMember(
    val id: String = "",
    val name: String = "",
    val modelId: String = "",
    val providerId: String = "",
    val specificApiKeyId: String? = null,
    val role: TeamRole = TeamRole.SENIOR_DEVELOPER,
    val customRoleName: String? = null,
    val instructions: String = "",
    val maxTokens: Int = 2048,
    val temperature: Float = 0.7f,
    val status: MemberStatus = MemberStatus.IDLE,
    val currentTask: String? = null,
    val lastResult: String? = null,
    val lastActiveAt: Long = System.currentTimeMillis(),
    val requestsCount: Int = 0,
    val tokensUsed: Int = 0,
    val costUsd: Double = 0.0,
    val isCostConfirmed: Boolean = true,
    val rateLimitErrors: Int = 0,
    val quotaRemaining: String? = null
)

@Entity(tableName = "model_teams")
data class ModelTeam(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val description: String = "",
    val goal: String = "",
    val ownerId: String = "",
    val leaderMemberId: String? = null,
    val members: List<TeamMember> = emptyList(),
    val collaborationStyle: CollaborationStyle = CollaborationStyle.ORCHESTRATED,
    val settings: TeamSettings = TeamSettings(),
    val governance: TeamGovernanceSettings = TeamGovernanceSettings(),
    val usageLimitMonthly: Double = 10.0,
    @get:PropertyName("isPublic") @field:PropertyName("isPublic")
    val isPublic: Boolean = false,
    @get:PropertyName("isTemplate") @field:PropertyName("isTemplate")
    val isTemplate: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class MemberUsageSummary(
    val memberId: String = "",
    val memberName: String = "",
    val role: TeamRole = TeamRole.SENIOR_DEVELOPER,
    val modelId: String = "",
    val modelName: String = "",
    val providerId: String = "",
    val providerName: String = "",
    val requestsCount: Int = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
    val costUsd: Double = 0.0,
    val isFreeModel: Boolean = true,
    val isCostConfirmed: Boolean = true,
    val rateLimitHits: Int = 0,
    val quotaStatus: String? = null,
    val lastActiveTime: Long = System.currentTimeMillis()
)

data class ModelFailoverEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val memberId: String = "",
    val memberName: String = "",
    val originalModelId: String = "",
    val replacementModelId: String = "",
    val replacementModelName: String = "",
    val reason: String = "",
    val isFree: Boolean = true
)

data class PreExecutionEstimation(
    val estimatedTasksCount: Int = 0,
    val estimatedCallsCount: Int = 0,
    val estimatedContextTokens: Int = 0,
    val estimatedMinCostUsd: Double = 0.0,
    val estimatedMaxCostUsd: Double = 0.0,
    val isFreeOnly: Boolean = true,
    val reliabilityStatus: String = "HIGH",
    val disclaimerText: String = "الاستهلاك الفعلي والتكلفة الحقيقية تعتمد على حجم ردود النماذج وعمليات الأدوات."
)

@Entity(tableName = "team_executions")
data class TeamExecution(
    @PrimaryKey val id: String = "",
    val teamId: String = "",
    val userId: String = "",
    val prompt: String = "",
    val status: ExecutionStatus = ExecutionStatus.PENDING,
    val steps: List<ExecutionStep> = emptyList(),
    val sharedMemory: SharedMemory = SharedMemory(),
    val messages: List<AgentMessage> = emptyList(),
    val finalResult: String? = null,
    val error: String? = null,
    val totalTokens: Int = 0,
    val totalCost: Double = 0.0,
    val totalRequests: Int = 0,
    val memberUsages: List<MemberUsageSummary> = emptyList(),
    val modelFailovers: List<ModelFailoverEvent> = emptyList(),
    val governanceEnforced: Boolean = true,
    val freeOnlyActive: Boolean = true,
    val preExecutionEstimation: PreExecutionEstimation? = null,
    val attachedFiles: List<AttachedFileContext> = emptyList(),
    val consensusPoints: List<String> = emptyList(),
    val disagreementPoints: List<String> = emptyList(),
    val projectId: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

data class AttachedFileContext(
    val name: String = "",
    val path: String = "",
    val content: String = "",
    val sizeBytes: Long = 0,
    val fileType: String = "text/plain"
)

enum class ExecutionStatus {
    PENDING, EXECUTING, PAUSED, COMPLETED, FAILED, CANCELLED
}

data class ExecutionStep(
    val id: String = "",
    val memberId: String = "",
    val memberName: String = "",
    val role: String = "",
    val task: String = "",
    val status: StepStatus = StepStatus.PENDING,
    val input: String = "",
    val output: String? = null,
    val error: String? = null,
    val waitReason: String? = null,
    val dependsOnIds: List<String> = emptyList(),
    val retryCount: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

data class SharedMemory(
    val entries: List<MemoryEntry> = emptyList(),
    val approvedDecisions: List<String> = emptyList(),
    val technicalConstraints: List<String> = emptyList()
)

enum class MemoryType {
    FACT, SUGGESTION, ASSUMPTION, DECISION
}

data class MemoryEntry(
    val id: String = "",
    val sourceMemberId: String = "",
    val content: String = "",
    val type: MemoryType = MemoryType.FACT,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageType {
    REGULAR, INSTRUCTION, CRITIQUE, REVIEW, SYSTEM, USER_PROMPT, CONSENSUS, DISAGREEMENT
}

data class AgentMessage(
    val id: String = "",
    val fromMemberId: String = "",
    val toMemberId: String = "",
    val content: String = "",
    val relatedStepId: String? = null,
    val type: MessageType = MessageType.REGULAR,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class TeamInstruction {
    data class ToLeader(val text: String) : TeamInstruction()
    data class ToMember(val memberId: String, val text: String) : TeamInstruction()
    data class BroadcastAll(val text: String) : TeamInstruction()
    data class RequestReview(val stepId: String? = null, val critiqueFocus: String = "") : TeamInstruction()
    data class RetryStep(val stepId: String) : TeamInstruction()
}

