package com.example.domain.validation

import com.example.domain.model.TeamRole

enum class ValidationCategory(val labelAr: String, val labelEn: String) {
    SETUP_AND_KEYS("الإعداد والمفاتيح", "Setup & Keys"),
    EXECUTION_MODES("أنماط التنفيذ", "Execution Modes"),
    COLLABORATION_AND_HANDOVER("التعاون وتمرير المخرجات", "Collaboration & Handover"),
    GOVERNANCE_AND_LIMITS("الحوكمة وحدود الاستهلاك", "Governance & Limits"),
    SANDBOX_VIBE_CODING("بيئة Vibe Coding المعزولة", "Vibe Coding Sandbox"),
    SANDBOX_CRASH_INVESTIGATION("بيئة Crash Investigation المعزولة", "Crash Investigation Sandbox")
}

enum class ValidationStatus {
    PENDING,
    RUNNING,
    PASSED,
    FAILED,
    SKIPPED
}

data class ValidationTestItem(
    val id: Int,
    val nameAr: String,
    val nameEn: String,
    val category: ValidationCategory,
    val status: ValidationStatus = ValidationStatus.PENDING,
    val descriptionAr: String,
    val evidence: String? = null,
    val durationMs: Long = 0,
    val modelsInvolved: List<String> = emptyList(),
    val errorMessage: String? = null
)

data class VibeCodingSandboxStep(
    val stepIndex: Int,
    val nameAr: String,
    val nameEn: String,
    val role: TeamRole,
    val modelId: String,
    val modelName: String,
    val inputContext: String,
    val outputContent: String,
    val status: ValidationStatus = ValidationStatus.PENDING,
    val artifactsProduced: List<String> = emptyList(),
    val testPass: Boolean? = null,
    val durationMs: Long = 0
)

data class CrashInvestigationSandboxStep(
    val stepIndex: Int,
    val nameAr: String,
    val nameEn: String,
    val role: TeamRole,
    val modelId: String,
    val modelName: String,
    val findings: String,
    val evidence: String,
    val status: ValidationStatus = ValidationStatus.PENDING,
    val verified: Boolean = false,
    val durationMs: Long = 0
)

data class ContactedModelSummary(
    val modelId: String,
    val modelName: String,
    val providerId: String,
    val providerName: String,
    val tasksExecuted: List<String>,
    val successCount: Int,
    val failureCount: Int,
    val totalTokens: Int,
    val costUsd: Double,
    val isFree: Boolean,
    val apiKeyMasked: String
)

data class ModelConflictItem(
    val topic: String,
    val modelA: String,
    val modelB: String,
    val claimA: String,
    val claimB: String,
    val resolution: String,
    val resolvedBy: String
)

data class TeamValidationSummaryReport(
    val timestamp: Long = System.currentTimeMillis(),
    val totalTests: Int = 20,
    val passedCount: Int = 0,
    val failedCount: Int = 0,
    val skippedCount: Int = 0,
    val pendingCount: Int = 0,
    val totalDurationMs: Long = 0,
    val modelsContacted: List<ContactedModelSummary> = emptyList(),
    val conflictsDetected: List<ModelConflictItem> = emptyList(),
    val rateLimitsObserved: List<String> = emptyList(),
    val totalActualCostUsd: Double = 0.0,
    val actualCostMeasured: Boolean = false,
    val isFreeOnlyProtected: Boolean = false,
    val userKeyIsolationVerified: Boolean = false,
    val userKeyIsolationScope: String? = null,
    val backendUserIsolationVerified: Boolean = false,
    val isolatedSandboxVerified: Boolean = false,
    val testResults: List<ValidationTestItem> = emptyList(),
    val actionableErrors: List<String> = emptyList(),
    val markdownReport: String = ""
)

data class TeamValidationProgress(
    val currentTestIndex: Int = 0,
    val totalTests: Int = 20,
    val currentTestName: String = "",
    val items: List<ValidationTestItem> = emptyList(),
    val summaryReport: TeamValidationSummaryReport? = null,
    val isRunning: Boolean = false
)

data class VibeCodingSandboxProgress(
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 7,
    val steps: List<VibeCodingSandboxStep> = emptyList(),
    val generatedFiles: Map<String, String> = emptyMap(),
    val testRunLog: String = "",
    val isComplete: Boolean = false,
    val isRunning: Boolean = false,
    val isAvailable: Boolean = false,
    val blockedReason: String? = null,
    val finalVerdict: String = ""
)

data class CrashInvestigationSandboxProgress(
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 7,
    val steps: List<CrashInvestigationSandboxStep> = emptyList(),
    val crashSummary: String = "",
    val patchDiff: String = "",
    val isComplete: Boolean = false,
    val isRunning: Boolean = false,
    val isAvailable: Boolean = false,
    val blockedReason: String? = null,
    val finalVerdict: String = ""
)
