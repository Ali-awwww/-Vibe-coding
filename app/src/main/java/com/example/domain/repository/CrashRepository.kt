package com.example.domain.repository

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class CrashPageCursor(val timestamp: Long, val id: String, val documentPath: String? = null)

data class CrashPage(val crashes: List<CrashReport>, val nextCursor: CrashPageCursor?)

interface CrashRepository {
    fun getProjects(): Flow<List<Project>>
    fun getProjectsForUser(userId: String): Flow<List<Project>>
    suspend fun getProjectById(projectId: String): Project?
    suspend fun addProject(project: Project)
    suspend fun deleteProject(projectId: String, callerUserId: String): Result<Unit>
    
    fun getApplications(projectId: String): Flow<List<Application>>
    fun getAllApplications(): Flow<List<Application>>
    suspend fun addApplication(application: Application)
    suspend fun deleteApplication(applicationId: String)
    
    fun getLatestCrashes(projectId: String? = null, applicationId: String? = null): Flow<List<CrashReport>>

    suspend fun getCrashPage(projectId: String?, applicationId: String?, cursor: CrashPageCursor?, pageSize: Int): CrashPage {
        require(pageSize in 1..100) { "Crash page size must be between 1 and 100." }
        val all = getLatestCrashes(projectId, applicationId).first()
            .sortedWith(compareByDescending<CrashReport> { it.timestamp }.thenByDescending { it.id })
        val start = if (cursor == null) 0 else all.indexOfFirst { it.id == cursor.id }.let { if (it < 0) all.size else it + 1 }
        val fetched = all.drop(start).take(pageSize + 1)
        val page = fetched.take(pageSize)
        val next = if (fetched.size > pageSize) {
            page.lastOrNull()?.let { CrashPageCursor(it.timestamp, it.id) }
        } else {
            null
        }
        return CrashPage(page, next)
    }
    fun getAllCrashes(): Flow<List<CrashReport>>
    fun getSimulatedQaReports(projectId: String? = null, applicationId: String? = null): Flow<List<CrashReport>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
    fun getCrashDetails(crashId: String): Flow<CrashReport?>
    suspend fun addCrash(crash: CrashReport)
    suspend fun updateCrashStatus(crashId: String, isResolved: Boolean)
    
    suspend fun getAIDiagnosis(crashReport: CrashReport): AIDiagnosis

    // Integrations & Secrets
    fun getIntegrations(projectId: String): Flow<List<Integration>>
    suspend fun addIntegration(integration: Integration)
    suspend fun deleteIntegration(integrationId: String, projectId: String, callerUserId: String): Result<Unit>
    suspend fun validateWebhook(projectId: String, integrationId: String, payload: String, signatureHeader: String?): Result<Boolean>
    
    // Issues (Grouped crashes)
    fun getIssues(projectId: String): Flow<List<Issue>>
    fun getAllIssues(): Flow<List<Issue>>
    fun getIssueDetails(issueId: String): Flow<Issue?>
    suspend fun addIssue(issue: Issue)
    suspend fun updateIssueStatus(issueId: String, status: IssueStatus)
    suspend fun getVersionStats(projectId: String, version: String): VersionStats
    suspend fun syncCrashes(projectId: String): Result<Unit>

    // Team Members & Access Control
    fun getProjectMembers(projectId: String): Flow<List<ProjectMember>>
    suspend fun getUserRoleInProject(projectId: String, userId: String): Role?
    suspend fun addProjectMember(projectId: String, email: String, role: Role, callerUserId: String): Result<Unit>
    suspend fun updateMemberRole(projectId: String, userId: String, newRole: Role, callerUserId: String): Result<Unit>
    suspend fun removeProjectMember(projectId: String, userId: String, callerUserId: String): Result<Unit>

    // Data Retention Policy & Security
    suspend fun updateProjectSecurityPolicy(projectId: String, retentionDays: Int, piiScrubbing: Boolean, callerUserId: String): Result<Unit>
    suspend fun purgeOldCrashes(projectId: String, callerUserId: String): Result<Int>

    // Audit Logging
    fun getAuditLogsForProject(projectId: String): Flow<List<AuditLog>>
    fun getRecentAuditLogs(): Flow<List<AuditLog>>
    suspend fun logAuditEvent(auditLog: AuditLog)

    // GitHub & Symbols
    suspend fun addRepositoryMapping(mapping: RepositoryMapping)
    suspend fun saveGitHubConnection(connection: GitHubConnection)

    // AI Fix Assistant
    fun getFixSuggestions(crashId: String): Flow<List<FixSuggestion>>
    suspend fun generateFixSuggestion(crashReport: CrashReport, diagnosis: AIDiagnosis): Result<FixSuggestion>
    suspend fun applyFixAndCreatePR(fixId: String, userId: String): Result<String> // Returns PR URL

    // Crash Fix Verification & Comparison
    fun getAllVerifications(): Flow<List<CrashVerificationRecord>>
    fun getVerificationsByProject(projectId: String): Flow<List<CrashVerificationRecord>>
    fun getVerificationsByCrash(crashId: String): Flow<List<CrashVerificationRecord>>
    suspend fun getVerificationById(id: String): CrashVerificationRecord?
    suspend fun verifyFix(
        crashId: String,
        fixSuggestionId: String? = null,
        newVersion: String,
        buildId: String,
        scenarioName: String,
        simulatedOutcome: VerificationStatus? = null
    ): Result<CrashVerificationRecord>
    suspend fun addVerificationRecord(record: CrashVerificationRecord)

    // Notifications
    fun getNotifications(): Flow<List<SystemNotification>>
    fun getNotificationsForProject(projectId: String): Flow<List<SystemNotification>>
    fun getUnreadNotificationCount(): Flow<Int>
    suspend fun addNotification(notification: SystemNotification)
    suspend fun markNotificationAsRead(id: String)
    suspend fun markAllNotificationsAsRead()
    suspend fun clearNotifications()
}
