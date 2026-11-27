package com.example.presentation.viewmodel

import com.example.domain.model.*
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.CrashRepository
import com.example.domain.repository.CrashPage
import com.example.domain.repository.CrashPageCursor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FakeAuthRepository(initialUser: UserAccount? = null) : AuthRepository {
    val userFlow = MutableStateFlow(initialUser)
    override val currentUser: Flow<UserAccount?> = userFlow
    override fun getCurrentUserSync(): UserAccount? = userFlow.value

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        organization: String
    ): Result<UserAccount> = Result.failure(UnsupportedOperationException())

    override suspend fun signIn(email: String, password: String): Result<UserAccount> =
        Result.failure(UnsupportedOperationException())

    override suspend fun signOut() {
        userFlow.value = null
    }

    override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())
}

class FakeCrashRepository : CrashRepository {
    var projects = MutableStateFlow<List<Project>>(emptyList())
    var applications = MutableStateFlow<List<Application>>(emptyList())
    var crashes = MutableStateFlow<List<CrashReport>>(emptyList())
    var issues = MutableStateFlow<List<Issue>>(emptyList())
    var integrations = MutableStateFlow<List<Integration>>(emptyList())
    var members = MutableStateFlow<List<ProjectMember>>(emptyList())
    var auditLogs = MutableStateFlow<List<AuditLog>>(emptyList())
    var notifications = MutableStateFlow<List<SystemNotification>>(emptyList())

    var failNextProjectsLoad: Throwable? = null
    var failNextCrashesLoad: Throwable? = null
    var failNextLatestCrashesLoad: Throwable? = null
    var failNextCrashPageLoad: Throwable? = null
    var failNextWrite: Throwable? = null

    var getAllApplicationsCalls = 0
    var getAllCrashesCalls = 0
    var getLatestCrashesCalls = 0
    var getCrashPageCalls = 0
    var lastCrashPageProjectId: String? = null
    var lastCrashPageApplicationId: String? = null
    var lastCrashPageCursorId: String? = null
    var lastLatestCrashProjectId: String? = null
    var lastLatestCrashApplicationId: String? = null
    var getAllIssuesCalls = 0
    var getProjectsForUserCalls = 0

    override fun getProjects(): Flow<List<Project>> = projects

    override fun getProjectsForUser(userId: String): Flow<List<Project>> {
        getProjectsForUserCalls++
        val error = failNextProjectsLoad
        if (error != null) {
            failNextProjectsLoad = null
            return flow { throw error }
        }
        return flowOf(projects.value.filter { it.ownerId == userId || members.value.any { m -> m.projectId == it.id && m.userId == userId } })
    }

    override suspend fun getProjectById(projectId: String): Project? =
        projects.value.find { it.id == projectId }

    override suspend fun addProject(project: Project) {
        failNextWrite?.let { failNextWrite = null; throw it }
        projects.value = projects.value + project
    }

    override suspend fun deleteProject(projectId: String, callerUserId: String): Result<Unit> {
        failNextWrite?.let { failNextWrite = null; return Result.failure(it) }
        projects.value = projects.value.filterNot { it.id == projectId }
        return Result.success(Unit)
    }

    override fun getApplications(projectId: String): Flow<List<Application>> =
        flowOf(applications.value.filter { it.projectId == projectId })

    override fun getAllApplications(): Flow<List<Application>> {
        getAllApplicationsCalls++
        return applications
    }

    override suspend fun addApplication(application: Application) {
        failNextWrite?.let { failNextWrite = null; throw it }
        applications.value = applications.value + application
    }

    override suspend fun deleteApplication(applicationId: String) {
        failNextWrite?.let { failNextWrite = null; throw it }
        applications.value = applications.value.filterNot { it.id == applicationId }
    }

    override fun getLatestCrashes(projectId: String?, applicationId: String?): Flow<List<CrashReport>> {
        getLatestCrashesCalls++
        lastLatestCrashProjectId = projectId
        lastLatestCrashApplicationId = applicationId
        val error = failNextLatestCrashesLoad
        if (error != null) {
            failNextLatestCrashesLoad = null
            return flow { throw error }
        }
        return crashes.map { records ->
            records.filter { crash ->
                !crash.isSimulatedQa() &&
                    (projectId == null || crash.projectId == projectId) &&
                    (applicationId == null || crash.applicationId == applicationId)
            }.sortedByDescending { it.timestamp }.take(100)
        }
    }

    override suspend fun getCrashPage(
        projectId: String?,
        applicationId: String?,
        cursor: CrashPageCursor?,
        pageSize: Int
    ): CrashPage {
        getCrashPageCalls++
        lastCrashPageProjectId = projectId
        lastCrashPageApplicationId = applicationId
        lastCrashPageCursorId = cursor?.id
        val error = failNextCrashPageLoad
        if (error != null) {
            failNextCrashPageLoad = null
            throw error
        }
        val matching = crashes.value.filter { crash ->
            !crash.isSimulatedQa() &&
                (projectId == null || crash.projectId == projectId) &&
                (applicationId == null || crash.applicationId == applicationId)
        }.sortedWith(compareByDescending<CrashReport> { it.timestamp }.thenByDescending { it.id })
        val startIndex = if (cursor == null) {
            0
        } else {
            matching.indexOfFirst { it.id == cursor.id }.let { index ->
                if (index < 0) matching.size else index + 1
            }
        }
        val fetched = matching.drop(startIndex).take(pageSize + 1)
        val page = fetched.take(pageSize)
        val nextCursor = if (fetched.size > pageSize) {
            page.lastOrNull()?.let { CrashPageCursor(it.timestamp, it.id) }
        } else {
            null
        }
        return CrashPage(page, nextCursor)
    }

    override fun getAllCrashes(): Flow<List<CrashReport>> {
        getAllCrashesCalls++
        val error = failNextCrashesLoad
        if (error != null) {
            failNextCrashesLoad = null
            return flow { throw error }
        }
        return crashes.map { records -> records.filterNot { it.isSimulatedQa() } }
    }

    override fun getSimulatedQaReports(projectId: String?, applicationId: String?): Flow<List<CrashReport>> {
        return crashes.map { records ->
            records.filter { crash ->
                crash.isSimulatedQa() &&
                    (projectId == null || crash.projectId == projectId) &&
                    (applicationId == null || crash.applicationId == applicationId)
            }.sortedByDescending { it.timestamp }
        }
    }

    override fun getCrashDetails(crashId: String): Flow<CrashReport?> = flowOf(null)
    override suspend fun addCrash(crash: CrashReport) {}
    override suspend fun updateCrashStatus(crashId: String, isResolved: Boolean) {}
    override suspend fun getAIDiagnosis(crashReport: CrashReport): AIDiagnosis = AIDiagnosis()
    override fun getIntegrations(projectId: String): Flow<List<Integration>> =
        integrations.map { records -> records.filter { it.projectId == projectId } }
    override suspend fun addIntegration(integration: Integration) {}
    override suspend fun deleteIntegration(integrationId: String, projectId: String, callerUserId: String): Result<Unit> = Result.success(Unit)
    override suspend fun validateWebhook(projectId: String, integrationId: String, payload: String, signatureHeader: String?): Result<Boolean> = Result.success(true)
    override fun getIssues(projectId: String): Flow<List<Issue>> = flowOf(issues.value.filter { it.projectId == projectId })

    override fun getAllIssues(): Flow<List<Issue>> {
        getAllIssuesCalls++
        return issues
    }

    override fun getIssueDetails(issueId: String): Flow<Issue?> = flowOf(null)
    override suspend fun addIssue(issue: Issue) {}
    override suspend fun updateIssueStatus(issueId: String, status: IssueStatus) {}
    override suspend fun getVersionStats(projectId: String, version: String): VersionStats =
        VersionStats(version, 0, 0, emptyList(), 0, 0, 0f)
    override suspend fun syncCrashes(projectId: String): Result<Unit> {
        val syncedAt = System.currentTimeMillis()
        integrations.value = integrations.value.map { integration ->
            if (integration.projectId == projectId && integration.type != IntegrationType.INTERNAL_QA) {
                integration.copy(lastSyncedAt = syncedAt)
            } else {
                integration
            }
        }
        return Result.success(Unit)
    }

    override fun getProjectMembers(projectId: String): Flow<List<ProjectMember>> =
        flowOf(members.value.filter { it.projectId == projectId })

    override suspend fun getUserRoleInProject(projectId: String, userId: String): Role? = null

    override suspend fun addProjectMember(projectId: String, email: String, role: Role, callerUserId: String): Result<Unit> {
        failNextWrite?.let { failNextWrite = null; return Result.failure(it) }
        members.value = members.value + ProjectMember(projectId = projectId, userId = email, userEmail = email, displayName = email, role = role)
        return Result.success(Unit)
    }

    override suspend fun updateMemberRole(projectId: String, userId: String, newRole: Role, callerUserId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeProjectMember(projectId: String, userId: String, callerUserId: String): Result<Unit> = Result.success(Unit)
    override suspend fun updateProjectSecurityPolicy(projectId: String, retentionDays: Int, piiScrubbing: Boolean, callerUserId: String): Result<Unit> = Result.success(Unit)
    override suspend fun purgeOldCrashes(projectId: String, callerUserId: String): Result<Int> = Result.success(0)
    override fun getAuditLogsForProject(projectId: String): Flow<List<AuditLog>> =
        flowOf(auditLogs.value.filter { it.projectId == projectId })
    override fun getRecentAuditLogs(): Flow<List<AuditLog>> = auditLogs
    override suspend fun logAuditEvent(auditLog: AuditLog) {}
    override suspend fun addRepositoryMapping(mapping: RepositoryMapping) {}
    override suspend fun saveGitHubConnection(connection: GitHubConnection) {}
    override fun getFixSuggestions(crashId: String): Flow<List<FixSuggestion>> = flowOf(emptyList())
    override suspend fun generateFixSuggestion(crashReport: CrashReport, diagnosis: AIDiagnosis): Result<FixSuggestion> = Result.failure(UnsupportedOperationException())
    override suspend fun applyFixAndCreatePR(fixId: String, userId: String): Result<String> = Result.failure(UnsupportedOperationException())
    override fun getAllVerifications(): Flow<List<CrashVerificationRecord>> = flowOf(emptyList())
    override fun getVerificationsByProject(projectId: String): Flow<List<CrashVerificationRecord>> = flowOf(emptyList())
    override fun getVerificationsByCrash(crashId: String): Flow<List<CrashVerificationRecord>> = flowOf(emptyList())
    override suspend fun getVerificationById(id: String): CrashVerificationRecord? = null
    override suspend fun verifyFix(crashId: String, fixSuggestionId: String?, newVersion: String, buildId: String, scenarioName: String, simulatedOutcome: VerificationStatus?): Result<CrashVerificationRecord> = Result.failure(UnsupportedOperationException())
    override suspend fun addVerificationRecord(record: CrashVerificationRecord) {}
    override fun getNotifications(): Flow<List<SystemNotification>> = notifications
    override fun getNotificationsForProject(projectId: String): Flow<List<SystemNotification>> = notifications
    override fun getUnreadNotificationCount(): Flow<Int> = flowOf(notifications.value.count { !it.isRead })
    override suspend fun addNotification(notification: SystemNotification) {}
    override suspend fun markNotificationAsRead(id: String) {}
    override suspend fun markAllNotificationsAsRead() {}
    override suspend fun clearNotifications() {}
}
