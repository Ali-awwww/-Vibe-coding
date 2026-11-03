package com.example.domain.validation

import kotlinx.coroutines.flow.Flow

interface TeamValidationEngine {
    fun getInitialTests(): List<ValidationTestItem>
    fun runFullValidationSuite(userId: String): Flow<TeamValidationProgress>
    fun runSingleTest(userId: String, testId: Int): Flow<ValidationTestItem>
    fun runVibeCodingSandbox(userId: String): Flow<VibeCodingSandboxProgress>
    fun runCrashInvestigationSandbox(userId: String): Flow<CrashInvestigationSandboxProgress>
    suspend fun generateValidationReport(
        testItems: List<ValidationTestItem>,
        vibeProgress: VibeCodingSandboxProgress?,
        crashProgress: CrashInvestigationSandboxProgress?
    ): TeamValidationSummaryReport
    fun buildMarkdownReport(report: TeamValidationSummaryReport): String
}
