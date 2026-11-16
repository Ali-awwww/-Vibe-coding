package com.example.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.firebase.firestore.PropertyName
import java.util.Date

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val description: String = "",
    val ownerId: String = "",
    val dataRetentionDays: Int = 90,
    val piiScrubbingEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "applications")
data class Application(
    @PrimaryKey val id: String = "",
    val projectId: String = "",
    val name: String = "",
    val platform: String = "Android",
    val packageName: String = "",
    val ownerId: String = "",
    val sdkSetupStatus: SdkSetupStatus = SdkSetupStatus.NOT_CONFIGURED,
    val agentConnectionStatus: AgentConnectionStatus = AgentConnectionStatus.OFFLINE,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "crashes")
data class CrashReport(
    @PrimaryKey val id: String = "",
    val applicationId: String = "",
    val projectId: String = "",
    val exceptionType: String = "",
    val message: String = "",
    val stackTrace: String = "",
    val deviceModel: String = "",
    val osVersion: String = "",
    val appVersion: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    @get:PropertyName("isResolved") @field:PropertyName("isResolved")
    val isResolved: Boolean = false,
    val severity: Severity = Severity.MEDIUM,
    val userId: String = "",
    val source: IntegrationType? = null
)

enum class Severity {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class ErrorCategory {
    NULL_POINTER_EXCEPTION,
    OUT_OF_MEMORY,
    NETWORK_ERROR,
    API_ERROR,
    DATABASE_ERROR,
    ANR,
    UI_THREAD_BLOCKING,
    NATIVE_CRASH,
    MEMORY_LEAK,
    DEPENDENCY_CONFLICT,
    PERMISSION_ERROR,
    RUNTIME_EXCEPTION,
    FATAL_EXCEPTION,
    UNKNOWN_ERROR,
    CUSTOM
}

enum class DiagnosisCertainty {
    CONFIRMED, HIGHLY_LIKELY, POSSIBLE, UNKNOWN
}

enum class FixStatus {
    PROPOSED, APPLIED, PR_CREATED, REJECTED
}

enum class VerificationStatus {
    FIX_VERIFIED_IN_TEST,
    CRASH_REPRODUCED,
    DIFFERENT_CRASH_DETECTED,
    INCONCLUSIVE,
    TEST_FAILED
}

@Entity(tableName = "crash_verifications")
data class CrashVerificationRecord(
    @PrimaryKey val id: String = "",
    val projectId: String = "",
    val applicationId: String = "",
    val crashId: String = "",
    val issueId: String? = null,
    val fixSuggestionId: String? = null,
    val fixCommitSha: String? = null,
    val githubPrUrl: String? = null,
    val previousAppVersion: String = "",
    val newAppVersion: String = "",
    val buildId: String = "",
    val testSessionId: String? = null,
    val scenarioName: String = "",
    val status: VerificationStatus = VerificationStatus.INCONCLUSIVE,
    val comparisonSummary: String = "",
    val oldStackTrace: String = "",
    val newStackTrace: String? = null,
    val testScopeDetails: String = "",
    val verifiedAt: Long = System.currentTimeMillis(),
    val crashExceptionType: String = "",
    val crashMessage: String = "",
    val environmentValid: Boolean = true
)

@Entity(tableName = "fix_suggestions")
data class FixSuggestion(
    @PrimaryKey val id: String = "",
    val crashId: String = "",
    val originalCode: String = "",
    val suggestedCode: String = "",
    val explanation: String = "",
    val potentialSideEffects: String = "",
    val filePath: String = "",
    val branchName: String = "",
    val githubPrUrl: String? = null,
    val status: FixStatus = FixStatus.PROPOSED,
    val testResults: String? = null, // JSON summary of compilation/tests
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_diagnoses")
data class AIDiagnosis(
    @PrimaryKey val crashId: String = "",
    val summary: String = "",
    val rootCause: String = "",
    val certainty: DiagnosisCertainty = DiagnosisCertainty.UNKNOWN,
    val sourceLocation: String = "", // file:line (method)
    val githubUrl: String? = null,
    val technicalEvidence: String = "", // Snippets or observations
    val executionContext: String = "", // OS, App, Device summary
    val whyItHappened: String = "",
    val solution: String = "",
    val suggestedCode: String? = null,
    val missingInformation: String? = null,
    val diffAnalysis: String? = null,
    val confidence: Float = 0f,
    val category: ErrorCategory = ErrorCategory.UNKNOWN_ERROR,
    val modelId: String? = null,
    val modelName: String? = null,
    val providerName: String? = null,
    val reasoningScore: Float? = null, // 0.0 to 1.0
    val codeQualityScore: Float? = null, // 0.0 to 1.0
    val createdAt: Long = System.currentTimeMillis()
)

data class AIDiagnosisResponse(
    val summary: String = "",
    val rootCause: String = "",
    val certainty: String = "UNKNOWN",
    val sourceLocation: String = "Unknown",
    val technicalEvidence: String = "",
    val executionContext: String = "",
    val whyItHappened: String = "",
    val diffAnalysis: String? = null,
    val solution: String = "",
    val suggestedCode: String? = null,
    val missingInformation: String? = null,
    val category: String = "UNKNOWN_ERROR",
    val confidence: Float = 0.5f,
    val reasoningScore: Float? = null,
    val codeQualityScore: Float? = null
)

@Entity(tableName = "ai_providers")
data class AIProvider(
    @PrimaryKey val id: String = "",
    val userId: String = "", // Links provider to a specific user (BYOK)
    val name: String = "",
    val type: AIProviderType = AIProviderType.GOOGLE_GEMINI,
    val status: AIProviderStatus = AIProviderStatus.DISCONNECTED,
    val apiKey: String? = null, // Securely encrypted
    val baseUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long? = null
)

enum class AIProviderType {
    GOOGLE_GEMINI, OPEN_ROUTER, HUGGING_FACE, CUSTOM, FIREBASE_AI_LOGIC
}

enum class AIProviderStatus {
    CONNECTED, DISCONNECTED, ERROR, RATE_LIMITED, UNSUPPORTED
}

@Entity(tableName = "ai_models")
data class AIModel(
    @PrimaryKey val id: String = "", // providerId + apiId
    val providerId: String = "",
    val apiId: String = "",
    val name: String = "",
    val isFree: Boolean = false,
    val contextWindow: Int = 0,
    val inputPricePer1M: Double = 0.0,
    val outputPricePer1M: Double = 0.0,
    val capabilities: List<AICapability> = emptyList(),
    val category: AIModelCategory = AIModelCategory.GENERAL_PURPOSE,
    val isAvailable: Boolean = true,
    val lastSyncAt: Long = System.currentTimeMillis()
)

enum class AICapability {
    CODING, REASONING, LONG_CONTEXT, STREAMING, FUNCTION_CALLING, VISION, AGENTIC
}

enum class AIModelCategory {
    FREE, CODING, REASONING, LONG_CONTEXT, GENERAL_PURPOSE, UNAVAILABLE
}

enum class AITaskType {
    STACK_TRACE_ANALYSIS,
    ROOT_CAUSE_DIAGNOSIS,
    CODE_FIX_SUGGESTION,
    REGRESSION_ANALYSIS,
    LOG_PARSING,
    GENERAL_CHAT,
    VIBE_AGENT_PLANNING,
    VIBE_AGENT_CODING,
    VIBE_AGENT_REVIEW,
    AGENT_TEAM_TASK
}

@Entity(tableName = "ai_usage_logs")
data class AIUsageLog(
    @PrimaryKey val id: String = "",
    val userId: String = "", // Track usage per user
    val modelId: String = "",
    val taskId: String = "", // e.g., crash diagnosis ID
    val tokensInput: Int = 0,
    val tokensOutput: Int = 0,
    val estimatedCost: Double = 0.0,
    val taskType: AITaskType = AITaskType.GENERAL_CHAT,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_user_settings")
data class AIUserSettings(
    @PrimaryKey val userId: String = "",
    val defaultModelId: String? = null,
    val freeOnly: Boolean = true,
    val autoSelectFree: Boolean = true,
    val usageLimitMonthly: Double = 5.0, // Default soft limit
    val privacyConsent: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(tableName = "integrations")
data class Integration(
    @PrimaryKey val id: String = "",
    val projectId: String = "",
    val name: String = "",
    val type: IntegrationType = IntegrationType.WEBHOOK,
    val status: IntegrationStatus = IntegrationStatus.CONNECTED,
    val lastSyncedAt: Long? = null,
    val configJson: String = "{}", // Encrypted or serialized config
    val webhookSecret: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class IntegrationType {
    FIREBASE_CRASHLYTICS, SENTRY, BUGSNAG, WEBHOOK, CUSTOM
}

enum class IntegrationStatus {
    CONNECTED, DISCONNECTED, UNAUTHORIZED, ERROR, UNKNOWN
}

enum class IssueStatus {
    NEW, INVESTIGATING, RESOLVED, REOPENED
}

@Entity(tableName = "issues")
data class Issue(
    @PrimaryKey val id: String = "",
    val projectId: String = "",
    val applicationId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val fingerprint: String = "",
    val severity: Severity = Severity.MEDIUM,
    val status: IssueStatus = IssueStatus.NEW,
    val occurrenceCount: Int = 0,
    val affectedUsers: Int = 0,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastOccurrence: Long = System.currentTimeMillis(),
    val affectedVersions: String = "", // Comma separated versions
    val source: IntegrationType? = null,
    val diagnosisResult: String? = null,
    val trend: Float = 0f, // % change
    val mostAffectedOs: String = "",
    val mostAffectedDevice: String = ""
)

data class VersionStats(
    val version: String,
    val crashCount: Int,
    val affectedUsers: Int,
    val topErrors: List<String>,
    val newErrorsCount: Int,
    val fixedErrorsCount: Int,
    val crashRateChange: Float // Percentage
)

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun fromSeverity(value: Severity): String = value.name

    @TypeConverter
    fun toSeverity(value: String): Severity = Severity.valueOf(value)

    @TypeConverter
    fun fromIntegrationType(value: IntegrationType): String = value.name

    @TypeConverter
    fun toIntegrationType(value: String): IntegrationType = IntegrationType.valueOf(value)

    @TypeConverter
    fun fromIntegrationStatus(value: IntegrationStatus): String = value.name

    @TypeConverter
    fun toIntegrationStatus(value: String): IntegrationStatus = IntegrationStatus.valueOf(value)

    @TypeConverter
    fun fromErrorCategory(value: ErrorCategory): String = value.name

    @TypeConverter
    fun toErrorCategory(value: String): ErrorCategory = ErrorCategory.valueOf(value)

    @TypeConverter
    fun fromIssueStatus(value: IssueStatus): String = value.name

    @TypeConverter
    fun toIssueStatus(value: String): IssueStatus = IssueStatus.valueOf(value)

    @TypeConverter
    fun fromRole(value: Role): String = value.name

    @TypeConverter
    fun toRole(value: String): Role = try { Role.valueOf(value) } catch (e: Exception) { Role.VIEWER }

    @TypeConverter
    fun fromDiagnosisCertainty(value: DiagnosisCertainty): String = value.name

    @TypeConverter
    fun toDiagnosisCertainty(value: String): DiagnosisCertainty = try { DiagnosisCertainty.valueOf(value) } catch (e: Exception) { DiagnosisCertainty.UNKNOWN }

    @TypeConverter
    fun fromTestSessionStatus(value: TestSessionStatus): String = value.name

    @TypeConverter
    fun toTestSessionStatus(value: String): TestSessionStatus = try {
        TestSessionStatus.valueOf(value)
    } catch (e: Exception) {
        TestSessionStatus.FAILED
    }

    @TypeConverter
    fun fromTestEventType(value: TestEventType): String = value.name

    @TypeConverter
    fun toTestEventType(value: String): TestEventType = try {
        TestEventType.valueOf(value)
    } catch (e: Exception) {
        TestEventType.SDK_EVENT
    }

    @TypeConverter
    fun fromSdkSetupStatus(value: SdkSetupStatus): String = value.name

    @TypeConverter
    fun toSdkSetupStatus(value: String): SdkSetupStatus = try {
        SdkSetupStatus.valueOf(value)
    } catch (e: Exception) {
        SdkSetupStatus.NOT_CONFIGURED
    }

    @TypeConverter
    fun fromAgentConnectionStatus(value: AgentConnectionStatus): String = value.name

    @TypeConverter
    fun toAgentConnectionStatus(value: String): AgentConnectionStatus = try {
        AgentConnectionStatus.valueOf(value)
    } catch (e: Exception) {
        AgentConnectionStatus.OFFLINE
    }

    @TypeConverter
    fun fromFixStatus(value: FixStatus): String = value.name

    @TypeConverter
    fun toFixStatus(value: String): FixStatus = try {
        FixStatus.valueOf(value)
    } catch (e: Exception) {
        FixStatus.PROPOSED
    }

    @TypeConverter
    fun fromVerificationStatus(value: VerificationStatus): String = value.name

    @TypeConverter
    fun toVerificationStatus(value: String): VerificationStatus = try {
        VerificationStatus.valueOf(value)
    } catch (e: Exception) {
        VerificationStatus.INCONCLUSIVE
    }

    @TypeConverter
    fun fromNotificationType(value: NotificationType): String = value.name

    @TypeConverter
    fun toNotificationType(value: String): NotificationType = try {
        NotificationType.valueOf(value)
    } catch (e: Exception) {
        NotificationType.CRASH_ALERT
    }

    @TypeConverter
    fun fromAIProviderType(value: AIProviderType): String = value.name

    @TypeConverter
    fun toAIProviderType(value: String): AIProviderType = try {
        AIProviderType.valueOf(value)
    } catch (e: Exception) {
        AIProviderType.GOOGLE_GEMINI
    }

    @TypeConverter
    fun fromAIProviderStatus(value: AIProviderStatus): String = value.name

    @TypeConverter
    fun toAIProviderStatus(value: String): AIProviderStatus = try {
        AIProviderStatus.valueOf(value)
    } catch (e: Exception) {
        AIProviderStatus.DISCONNECTED
    }

    @TypeConverter
    fun fromAICapabilityList(value: List<AICapability>): String = value.joinToString(",") { it.name }

    @TypeConverter
    fun toAICapabilityList(value: String): List<AICapability> = if (value.isEmpty()) emptyList() else value.split(",").map { AICapability.valueOf(it) }

    @TypeConverter
    fun fromAIModelCategory(value: AIModelCategory): String = value.name

    @TypeConverter
    fun toAIModelCategory(value: String): AIModelCategory = try {
        AIModelCategory.valueOf(value)
    } catch (e: Exception) {
        AIModelCategory.GENERAL_PURPOSE
    }

    @TypeConverter
    fun fromWorkspaceType(value: WorkspaceType): String = value.name

    @TypeConverter
    fun toWorkspaceType(value: String): WorkspaceType = try {
        WorkspaceType.valueOf(value)
    } catch (e: Exception) {
        WorkspaceType.WEB_APP
    }

    @TypeConverter
    fun fromLogType(value: LogType): String = value.name

    @TypeConverter
    fun toLogType(value: String): LogType = try {
        LogType.valueOf(value)
    } catch (e: Exception) {
        LogType.INFO
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString("|||")

    @TypeConverter
    fun toStringList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split("|||")

    @TypeConverter
    fun fromExecutionStepList(value: List<ExecutionStep>): String = com.example.util.JsonUtils.toJson(value)

    @TypeConverter
    fun toExecutionStepList(value: String): List<ExecutionStep> = com.example.util.JsonUtils.fromJsonList(value, ExecutionStep::class.java)

    @TypeConverter
    fun fromSharedMemory(value: SharedMemory): String = com.example.util.JsonUtils.toJson(value)

    @TypeConverter
    fun toSharedMemory(value: String): SharedMemory = com.example.util.JsonUtils.fromJson(value, SharedMemory::class.java) ?: SharedMemory()

    @TypeConverter
    fun fromAgentMessageList(value: List<AgentMessage>): String = com.example.util.JsonUtils.toJson(value)

    @TypeConverter
    fun toAgentMessageList(value: String): List<AgentMessage> = com.example.util.JsonUtils.fromJsonList(value, AgentMessage::class.java)

    @TypeConverter
    fun fromTeamSettings(value: TeamSettings): String = com.example.util.JsonUtils.toJson(value)

    @TypeConverter
    fun toTeamSettings(value: String): TeamSettings = com.example.util.JsonUtils.fromJson(value, TeamSettings::class.java) ?: TeamSettings()
}

enum class NotificationType {
    CRASH_ALERT,
    ANR_ALERT,
    FIX_READY,
    PR_CREATED,
    VERIFICATION_PASSED,
    VERIFICATION_FAILED,
    SECURITY_ALERT
}

@Entity(tableName = "notifications")
data class SystemNotification(
    @PrimaryKey val id: String = "",
    val projectId: String = "",
    val title: String = "",
    val message: String = "",
    val type: NotificationType = NotificationType.CRASH_ALERT,
    val targetCrashId: String? = null,
    @get:PropertyName("isRead") @field:PropertyName("isRead")
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

