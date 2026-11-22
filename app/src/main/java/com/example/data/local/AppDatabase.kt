package com.example.data.local

import androidx.room.*
import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CrashDao {
    // Projects
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE ownerId = :userId OR id IN (SELECT projectId FROM project_members WHERE userId = :userId) ORDER BY createdAt DESC")
    fun getProjectsForUser(userId: String): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: String): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: String)

    @Query("UPDATE projects SET dataRetentionDays = :retentionDays, piiScrubbingEnabled = :piiScrubbing WHERE id = :projectId")
    suspend fun updateProjectSecurityPolicy(projectId: String, retentionDays: Int, piiScrubbing: Boolean)

    // Applications
    @Query("SELECT * FROM applications WHERE projectId = :projectId")
    fun getApplications(projectId: String): Flow<List<Application>>

    @Query("SELECT * FROM applications")
    fun getAllApplications(): Flow<List<Application>>

    @Query("SELECT * FROM applications WHERE id = :applicationId")
    suspend fun getApplicationById(applicationId: String): Application?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplication(application: Application)

    @Query("DELETE FROM applications WHERE id = :applicationId")
    suspend fun deleteApplication(applicationId: String)

    @Query("DELETE FROM applications WHERE projectId = :projectId")
    suspend fun deleteApplicationsByProject(projectId: String)

    // Crashes
    @Query("SELECT * FROM crashes ORDER BY timestamp DESC LIMIT 50")
    fun getLatestCrashes(): Flow<List<CrashReport>>

    @Query("SELECT * FROM crashes ORDER BY timestamp DESC")
    fun getAllCrashes(): Flow<List<CrashReport>>

    @Query("SELECT * FROM crashes WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getCrashesByProject(projectId: String): Flow<List<CrashReport>>

    @Query("SELECT * FROM crashes WHERE id = :crashId")
    fun getCrashDetails(crashId: String): Flow<CrashReport?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrash(crash: CrashReport)

    @Query("UPDATE crashes SET isResolved = :isResolved WHERE id = :crashId")
    suspend fun updateCrashStatus(crashId: String, isResolved: Boolean)

    @Query("DELETE FROM crashes WHERE id = :crashId")
    suspend fun deleteCrash(crashId: String)

    @Query("DELETE FROM crashes WHERE projectId = :projectId")
    suspend fun deleteCrashesByProject(projectId: String)

    @Query("DELETE FROM crashes WHERE projectId = :projectId AND timestamp < :cutoffTimestamp")
    suspend fun pruneOldCrashes(projectId: String, cutoffTimestamp: Long): Int

    // AI Diagnoses
    @Query("SELECT * FROM ai_diagnoses WHERE crashId = :crashId")
    fun getAIDiagnosis(crashId: String): Flow<AIDiagnosis?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAIDiagnosis(diagnosis: AIDiagnosis)

    // Integrations
    @Query("SELECT * FROM integrations WHERE projectId = :projectId")
    fun getIntegrations(projectId: String): Flow<List<Integration>>

    @Query("SELECT * FROM integrations WHERE id = :id")
    suspend fun getIntegrationById(id: String): Integration?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntegration(integration: Integration)

    @Query("DELETE FROM integrations WHERE id = :integrationId")
    suspend fun deleteIntegration(integrationId: String)

    @Query("DELETE FROM integrations WHERE projectId = :projectId")
    suspend fun deleteIntegrationsByProject(projectId: String)

    // Issues
    @Query("SELECT * FROM issues WHERE projectId = :projectId ORDER BY lastOccurrence DESC")
    fun getIssues(projectId: String): Flow<List<Issue>>

    @Query("SELECT * FROM issues")
    fun getAllIssues(): Flow<List<Issue>>

    @Query("SELECT * FROM issues WHERE id = :issueId")
    fun getIssueDetails(issueId: String): Flow<Issue?>

    @Query("UPDATE issues SET status = :status WHERE id = :issueId")
    suspend fun updateIssueStatus(issueId: String, status: IssueStatus)

    @Query("SELECT * FROM crashes WHERE appVersion = :version")
    suspend fun getCrashesByVersion(version: String): List<CrashReport>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssue(issue: Issue)

    @Query("DELETE FROM issues WHERE projectId = :projectId")
    suspend fun deleteIssuesByProject(projectId: String)

    // User Accounts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserAccount)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserAccount?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserAccount?

    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserAccount>>

    @Query("UPDATE users SET passwordHash = :passwordHash, salt = :salt WHERE email = :email")
    suspend fun updateUserPassword(email: String, passwordHash: String, salt: String)

    @Query("UPDATE users SET lastLoginAt = :loginTime WHERE id = :userId")
    suspend fun updateLastLogin(userId: String, loginTime: Long)

    // Project Members & Access Control
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjectMember(member: ProjectMember)

    @Query("SELECT * FROM project_members WHERE projectId = :projectId")
    fun getProjectMembers(projectId: String): Flow<List<ProjectMember>>

    @Query("SELECT * FROM project_members WHERE projectId = :projectId AND userId = :userId LIMIT 1")
    suspend fun getProjectMember(projectId: String, userId: String): ProjectMember?

    @Query("DELETE FROM project_members WHERE projectId = :projectId AND userId = :userId")
    suspend fun deleteProjectMember(projectId: String, userId: String)

    @Query("UPDATE project_members SET role = :newRole WHERE projectId = :projectId AND userId = :userId")
    suspend fun updateProjectMemberRole(projectId: String, userId: String, newRole: Role)

    @Query("DELETE FROM project_members WHERE projectId = :projectId")
    suspend fun deleteMembersByProject(projectId: String)

    // Audit Logs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLog)

    @Query("SELECT * FROM audit_logs WHERE projectId = :projectId ORDER BY timestamp DESC LIMIT 100")
    fun getAuditLogsForProject(projectId: String): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentAuditLogs(): Flow<List<AuditLog>>

    // Test Sessions
    @Query("SELECT * FROM test_sessions WHERE projectId = :projectId ORDER BY startTime DESC")
    fun getTestSessions(projectId: String): Flow<List<TestSession>>

    @Query("SELECT * FROM test_sessions WHERE id = :sessionId")
    fun getTestSessionById(sessionId: String): Flow<TestSession?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTestSession(session: TestSession)

    @Query("UPDATE test_sessions SET status = :status, endTime = :endTime, crashId = :crashId WHERE id = :sessionId")
    suspend fun updateTestSessionStatus(sessionId: String, status: TestSessionStatus, endTime: Long?, crashId: String?)

    // Test Events
    @Query("SELECT * FROM test_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getTestEvents(sessionId: String): Flow<List<TestEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTestEvent(event: TestEvent)

    @Query("UPDATE applications SET sdkSetupStatus = :status WHERE id = :applicationId")
    suspend fun updateApplicationSdkStatus(applicationId: String, status: SdkSetupStatus)

    @Query("UPDATE applications SET agentConnectionStatus = :status WHERE id = :applicationId")
    suspend fun updateApplicationAgentStatus(applicationId: String, status: AgentConnectionStatus)

    // GitHub Integration
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGitHubConnection(connection: GitHubConnection)

    @Query("SELECT * FROM github_connections WHERE userId = :userId")
    suspend fun getGitHubConnection(userId: String): GitHubConnection?

    @Query("DELETE FROM github_connections WHERE userId = :userId")
    suspend fun deleteGitHubConnection(userId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepositoryMapping(mapping: RepositoryMapping)

    @Query("SELECT * FROM repository_mappings WHERE applicationId = :applicationId")
    suspend fun getRepositoryMapping(applicationId: String): RepositoryMapping?

    @Query("SELECT * FROM repository_mappings WHERE projectId = :projectId")
    fun getRepositoryMappingsForProject(projectId: String): Flow<List<RepositoryMapping>>

    @Query("DELETE FROM repository_mappings WHERE applicationId = :applicationId")
    suspend fun deleteRepositoryMapping(applicationId: String)

    // Debug Symbols
    @Query("SELECT * FROM debug_symbols WHERE projectId = :projectId ORDER BY uploadTimestamp DESC")
    fun getDebugSymbolsForProject(projectId: String): Flow<List<DebugSymbolFile>>

    @Query("SELECT * FROM debug_symbols WHERE applicationId = :applicationId AND appVersion = :version")
    suspend fun getMatchingDebugSymbols(applicationId: String, version: String): List<DebugSymbolFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebugSymbol(file: DebugSymbolFile)

    @Query("DELETE FROM debug_symbols WHERE id = :id")
    suspend fun deleteDebugSymbol(id: String)

    @Query("UPDATE debug_symbols SET status = :status WHERE id = :id")
    suspend fun updateDebugSymbolStatus(id: String, status: SymbolicationStatus)

    // Fix Suggestions
    @Query("SELECT * FROM fix_suggestions WHERE crashId = :crashId")
    fun getFixSuggestions(crashId: String): Flow<List<FixSuggestion>>

    @Query("SELECT * FROM fix_suggestions WHERE id = :id")
    suspend fun getFixSuggestionById(id: String): FixSuggestion?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFixSuggestion(fix: FixSuggestion)

    @Query("UPDATE fix_suggestions SET status = :status, githubPrUrl = :prUrl WHERE id = :id")
    suspend fun updateFixStatus(id: String, status: FixStatus, prUrl: String? = null)

    // Crash Fix Verifications
    @Query("SELECT * FROM crash_verifications ORDER BY verifiedAt DESC")
    fun getAllVerifications(): Flow<List<CrashVerificationRecord>>

    @Query("SELECT * FROM crash_verifications WHERE projectId = :projectId ORDER BY verifiedAt DESC")
    fun getVerificationsByProject(projectId: String): Flow<List<CrashVerificationRecord>>

    @Query("SELECT * FROM crash_verifications WHERE crashId = :crashId ORDER BY verifiedAt DESC")
    fun getVerificationsByCrash(crashId: String): Flow<List<CrashVerificationRecord>>

    @Query("SELECT * FROM crash_verifications WHERE id = :id")
    suspend fun getVerificationById(id: String): CrashVerificationRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerification(record: CrashVerificationRecord)

    @Query("DELETE FROM crash_verifications WHERE id = :id")
    suspend fun deleteVerification(id: String)

    // Notifications
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getNotifications(): Flow<List<SystemNotification>>

    @Query("SELECT * FROM notifications WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getNotificationsForProject(projectId: String): Flow<List<SystemNotification>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun getUnreadNotificationCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: SystemNotification)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: String)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllNotificationsAsRead()

    @Query("DELETE FROM notifications")
    suspend fun clearNotifications()
}

@Dao
interface AIProviderDao {
    // Providers
    @Query("SELECT * FROM ai_providers WHERE userId = :userId ORDER BY createdAt DESC")
    fun getProviders(userId: String): Flow<List<AIProvider>>

    @Query("SELECT * FROM ai_providers WHERE userId = :userId AND id = :id")
    suspend fun getProviderById(userId: String, id: String): AIProvider?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: AIProvider)

    @Query("DELETE FROM ai_providers WHERE id = :id AND userId = :userId")
    suspend fun deleteProvider(userId: String, id: String)

    @Query("UPDATE ai_providers SET status = :status, lastSyncAt = :syncTime WHERE userId = :userId AND id = :id")
    suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long)

    // Models
    @Query("""
        SELECT ai_models.* FROM ai_models
        INNER JOIN ai_providers ON ai_providers.id = ai_models.providerId
        WHERE ai_providers.userId = :userId AND ai_models.providerId = :providerId
        ORDER BY ai_models.name ASC
    """)
    fun getModelsByProvider(userId: String, providerId: String): Flow<List<AIModel>>

    @Query("""
        SELECT ai_models.* FROM ai_models
        INNER JOIN ai_providers ON ai_providers.id = ai_models.providerId
        WHERE ai_providers.userId = :userId AND ai_models.isAvailable = 1
        ORDER BY ai_models.category ASC, ai_models.name ASC
    """)
    fun getAllAvailableModels(userId: String): Flow<List<AIModel>>

    @Query("""
        SELECT ai_models.* FROM ai_models
        INNER JOIN ai_providers ON ai_providers.id = ai_models.providerId
        WHERE ai_providers.userId = :userId AND ai_models.id = :id
        LIMIT 1
    """)
    suspend fun getModelById(userId: String, id: String): AIModel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<AIModel>)

    @Query("""
        DELETE FROM ai_models
        WHERE providerId = :providerId
          AND providerId IN (SELECT id FROM ai_providers WHERE userId = :userId)
    """)
    suspend fun deleteModelsByProvider(userId: String, providerId: String)

    @Transaction
    suspend fun replaceModelsForProvider(userId: String, providerId: String, models: List<AIModel>) {
        require(models.isNotEmpty()) { "Cannot replace a provider catalog with an empty model list" }
        require(models.all { it.providerId == providerId }) { "Every model must belong to the selected provider" }
        check(getProviderById(userId, providerId) != null) { "Provider is not owned by the current user" }
        deleteModelsByProvider(userId, providerId)
        insertModels(models)
    }

    // Usage Logs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageLog(log: AIUsageLog)

    @Query("SELECT * FROM ai_usage_logs WHERE userId = :userId ORDER BY timestamp DESC LIMIT 100")
    fun getRecentUsageLogs(userId: String): Flow<List<AIUsageLog>>

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN estimatedCost >= 0 THEN estimatedCost ELSE 0 END), 0)
        FROM ai_usage_logs
        WHERE userId = :userId AND timestamp > :since
    """)
    fun getTotalCost(userId: String, since: Long): Flow<Double?>

    @Query("""
        SELECT EXISTS(
            SELECT 1
            FROM ai_usage_logs AS usage_log
            LEFT JOIN ai_models AS model ON model.id = usage_log.modelId
            LEFT JOIN ai_providers AS provider
                ON provider.id = model.providerId AND provider.userId = usage_log.userId
            WHERE usage_log.userId = :userId
              AND usage_log.timestamp > :since
              AND (
                  usage_log.estimatedCost < 0
                  OR (
                      usage_log.isSuccess = 1
                      AND usage_log.estimatedCost = 0
                      AND (
                          usage_log.modelId LIKE 'firebase_ai_platform:%'
                          OR model.isFree = 0
                          OR provider.type = 'GOOGLE_GEMINI'
                          OR model.id IS NULL
                      )
                  )
              )
        )
    """)
    fun hasUnpricedUsageSince(userId: String, since: Long): Flow<Boolean>

    // User Settings
    @Query("SELECT * FROM ai_user_settings WHERE userId = :userId")
    fun getUserSettings(userId: String): Flow<AIUserSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserSettings(settings: AIUserSettings)

    // Team Usage Records
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeamUsageRecord(record: TeamUsageRecord)

    @Query("SELECT * FROM team_usage_records WHERE teamId = :teamId ORDER BY timestamp DESC")
    fun getTeamUsageRecords(teamId: String): Flow<List<TeamUsageRecord>>

    @Query("SELECT * FROM team_usage_records WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllTeamUsageRecords(userId: String): Flow<List<TeamUsageRecord>>

    @Query("SELECT * FROM team_usage_records WHERE executionId = :executionId ORDER BY timestamp ASC")
    fun getExecutionUsageRecords(executionId: String): Flow<List<TeamUsageRecord>>
}

@Database(
    entities = [
        Project::class,
        Application::class,
        CrashReport::class,
        AIDiagnosis::class,
        Integration::class,
        Issue::class,
        UserAccount::class,
        ProjectMember::class,
        AuditLog::class,
        TestSession::class,
        TestEvent::class,
        GitHubConnection::class,
        RepositoryMapping::class,
        DebugSymbolFile::class,
        FixSuggestion::class,
        CrashVerificationRecord::class,
        SystemNotification::class,
        AIProvider::class,
        AIModel::class,
        AIUsageLog::class,
        AIUserSettings::class,
        Workspace::class,
        WorkspaceFile::class,
        ProjectContext::class,
        BuildLog::class,
        WorkspaceCheckpoint::class,
        WorkspaceGitCommit::class,
        WorkspaceGitBranch::class,
        WorkspaceTaskReview::class,
        AgentActivityLog::class,
        PendingApproval::class,
        TeamUsageRecord::class
    ],
    version = 20,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun crashDao(): CrashDao
    abstract fun aiProviderDao(): AIProviderDao
    abstract fun vibeStudioDao(): VibeStudioDao
}
