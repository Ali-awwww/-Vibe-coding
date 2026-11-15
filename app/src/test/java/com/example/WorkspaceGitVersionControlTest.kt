package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.VibeStudioDao
import com.example.data.repository.VibeStudioRepositoryImpl
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.util.DiffUtils
import com.example.util.WorkspaceGitEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config
class WorkspaceGitVersionControlTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: VibeStudioDao
    private lateinit var repository: VibeStudioRepositoryImpl
    private val testWorkspaceId = "test-workspace-git-101"

    private val fakeAIModelRepository = object : AIModelRepository {
        override fun getProviders(userId: String) = flowOf(emptyList<AIProvider>())
        override suspend fun getProviderById(userId: String, id: String) = null
        override suspend fun addProvider(provider: AIProvider) {}
        override suspend fun deleteProvider(userId: String, id: String) {}
        override suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long) {}
        override fun getModelsByProvider(userId: String, providerId: String) = flowOf(emptyList<AIModel>())
        override fun getAllAvailableModels(userId: String, freeOnly: Boolean) = flowOf(emptyList<AIModel>())
        override suspend fun getModelById(userId: String, id: String) = null
        override suspend fun syncModels(userId: String, providerId: String) = Result.success(Unit)
        override suspend fun testConnection(userId: String, providerId: String) = Result.success(Unit)
        override fun getRecentUsageLogs(userId: String) = flowOf(emptyList<AIUsageLog>())
        override fun getTotalCost(userId: String, since: Long) = flowOf(0.0)
        override suspend fun logUsage(log: AIUsageLog) {}
        override fun getUserSettings(userId: String) = flowOf(AIUserSettings(userId = userId))
        override suspend fun updateUserSettings(settings: AIUserSettings) {}
        override suspend fun setDefaultModel(userId: String, modelId: String) {}
        override suspend fun getDefaultModel(userId: String) = null
        override suspend fun generateResponse(userId: String, modelId: String, prompt: String, systemInstruction: String?, taskId: String?, taskType: AITaskType) = Result.success("{}")
    }

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.vibeStudioDao()
        val crashDao = database.crashDao()

        val mockCrashRepo = object : com.example.domain.repository.CrashRepository {
            val verificationRecords = mutableListOf<CrashVerificationRecord>()

            override fun getProjects() = crashDao.getProjects()
            override fun getProjectsForUser(userId: String) = crashDao.getProjectsForUser(userId)
            override suspend fun getProjectById(projectId: String) = crashDao.getProjectById(projectId)
            override suspend fun addProject(project: Project) = crashDao.insertProject(project)
            override suspend fun deleteProject(projectId: String, callerUserId: String) = Result.success(Unit)
            override fun getApplications(projectId: String) = crashDao.getApplications(projectId)
            override fun getAllApplications() = crashDao.getAllApplications()
            override suspend fun addApplication(application: Application) = crashDao.insertApplication(application)
            override suspend fun deleteApplication(applicationId: String) = crashDao.deleteApplication(applicationId)
            override fun getLatestCrashes(projectId: String?, applicationId: String?) = crashDao.getAllCrashes()
            override fun getAllCrashes() = crashDao.getAllCrashes()
            override fun getCrashDetails(crashId: String) = crashDao.getCrashDetails(crashId)
            override suspend fun addCrash(crash: CrashReport) = crashDao.insertCrash(crash)
            override suspend fun updateCrashStatus(crashId: String, isResolved: Boolean) = crashDao.updateCrashStatus(crashId, isResolved)
            override suspend fun getAIDiagnosis(crashReport: CrashReport): AIDiagnosis = AIDiagnosis(rootCause = "Test Cause")
            override fun getIntegrations(projectId: String) = flowOf(emptyList<Integration>())
            override suspend fun addIntegration(integration: Integration) {}
            override suspend fun deleteIntegration(integrationId: String, projectId: String, callerUserId: String) = Result.success(Unit)
            override suspend fun validateWebhook(projectId: String, integrationId: String, payload: String, signatureHeader: String?) = Result.success(true)
            override fun getIssues(projectId: String) = flowOf(emptyList<Issue>())
            override fun getAllIssues() = flowOf(emptyList<Issue>())
            override fun getIssueDetails(issueId: String) = flowOf(null)
            override suspend fun addIssue(issue: Issue) {}
            override suspend fun updateIssueStatus(issueId: String, status: IssueStatus) {}
            override suspend fun getVersionStats(projectId: String, version: String) = VersionStats(version, 0, 0, emptyList(), 0, 0, 0f)
            override suspend fun syncCrashes(projectId: String) = Result.success(Unit)
            override fun getProjectMembers(projectId: String) = flowOf(emptyList<ProjectMember>())
            override suspend fun getUserRoleInProject(projectId: String, userId: String): Role? = Role.ADMIN
            override suspend fun addProjectMember(projectId: String, email: String, role: Role, callerUserId: String) = Result.success(Unit)
            override suspend fun updateMemberRole(projectId: String, userId: String, newRole: Role, callerUserId: String) = Result.success(Unit)
            override suspend fun removeProjectMember(projectId: String, userId: String, callerUserId: String) = Result.success(Unit)
            override suspend fun updateProjectSecurityPolicy(projectId: String, retentionDays: Int, piiScrubbing: Boolean, callerUserId: String) = Result.success(Unit)
            override suspend fun purgeOldCrashes(projectId: String, callerUserId: String) = Result.success(0)
            override fun getAuditLogsForProject(projectId: String) = flowOf(emptyList<AuditLog>())
            override fun getRecentAuditLogs() = flowOf(emptyList<AuditLog>())
            override suspend fun logAuditEvent(auditLog: AuditLog) {}
            override suspend fun addRepositoryMapping(mapping: RepositoryMapping) {}
            override suspend fun saveGitHubConnection(connection: GitHubConnection) {}
            override fun getFixSuggestions(crashId: String) = flowOf(emptyList<FixSuggestion>())
            override suspend fun generateFixSuggestion(crashReport: CrashReport, diagnosis: AIDiagnosis) = Result.success(FixSuggestion(crashId = crashReport.id))
            override suspend fun applyFixAndCreatePR(fixId: String, userId: String) = Result.success("https://github.com/test/repo/pull/1")
            override fun getAllVerifications() = flowOf(verificationRecords)
            override fun getVerificationsByProject(projectId: String) = flowOf(verificationRecords.filter { it.projectId == projectId })
            override fun getVerificationsByCrash(crashId: String) = flowOf(verificationRecords.filter { it.crashId == crashId })
            override suspend fun getVerificationById(id: String) = verificationRecords.find { it.id == id }
            override suspend fun verifyFix(crashId: String, fixSuggestionId: String?, newVersion: String, buildId: String, scenarioName: String, simulatedOutcome: VerificationStatus?) = Result.success(CrashVerificationRecord(crashId = crashId, newAppVersion = newVersion, buildId = buildId, scenarioName = scenarioName))
            override suspend fun addVerificationRecord(record: CrashVerificationRecord) {
                verificationRecords.add(record)
            }
            override fun getNotifications() = flowOf(emptyList<SystemNotification>())
            override fun getNotificationsForProject(projectId: String) = flowOf(emptyList<SystemNotification>())
            override fun getUnreadNotificationCount() = flowOf(0)
            override suspend fun addNotification(notification: SystemNotification) {}
            override suspend fun markNotificationAsRead(id: String) {}
            override suspend fun markAllNotificationsAsRead() {}
            override suspend fun clearNotifications() {}
        }

        repository = VibeStudioRepositoryImpl(dao, fakeAIModelRepository, mockCrashRepo)

        // Seed initial workspace
        val workspace = Workspace(
            id = testWorkspaceId,
            name = "Test Workspace",
            description = "Git Version Control Test Project",
            type = WorkspaceType.WEB_APP,
            techStack = "React",
            ownerId = "user_git_tester"
        )
        dao.insertWorkspace(workspace)

        // Seed sample files
        dao.insertFiles(listOf(
            WorkspaceFile(
                id = "f1",
                workspaceId = testWorkspaceId,
                name = "App.js",
                path = "src/App.js",
                content = "export default function App() {\n  return <h1>Hello Git</h1>;\n}",
                extension = "js"
            ),
            WorkspaceFile(
                id = "f2",
                workspaceId = testWorkspaceId,
                name = "App.test.js",
                path = "src/App.test.js",
                content = "import App from './App';\ntest('renders', () => {});",
                extension = "js"
            )
        ))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `initGitRepository creates initial commit and main branch`() = runTest {
        val result = repository.initGitRepository(testWorkspaceId, "main")
        assertTrue("initGitRepository should succeed", result.isSuccess)

        val commit = result.getOrThrow()
        assertEquals("main", commit.branch)
        assertEquals(40, commit.id.length) // SHA-1 length
        assertEquals(2, commit.changedFilesCount)

        val branches = repository.getGitBranches(testWorkspaceId).first()
        assertEquals(1, branches.size)
        assertEquals("main", branches[0].name)
        assertTrue("main should be default", branches[0].isDefault)
        assertTrue("main should be current", branches[0].isCurrent)
        assertEquals(commit.id, branches[0].headCommitSha)
    }

    @Test
    fun `commitChanges records modifications and updates HEAD commit`() = runTest {
        repository.initGitRepository(testWorkspaceId).getOrThrow()

        // Modify App.js
        val appFile = dao.getFileByPath(testWorkspaceId, "src/App.js")!!
        dao.insertFile(appFile.copy(content = "export default function App() {\n  return <h2>Hello Version 2</h2>;\n}"))

        val commitResult = repository.commitChanges(
            workspaceId = testWorkspaceId,
            message = "Update headline in App.js",
            author = "Alice Dev"
        )
        assertTrue("Commit should succeed", commitResult.isSuccess)

        val commit = commitResult.getOrThrow()
        assertEquals("Update headline in App.js", commit.message)
        assertEquals("Alice Dev", commit.author)
        assertEquals(1, commit.changedFilesCount)
        assertTrue("Additions should be > 0", commit.additions > 0)

        val branches = repository.getGitBranches(testWorkspaceId).first()
        assertEquals(commit.id, branches.first { it.name == "main" }.headCommitSha)
    }

    @Test
    fun `scanForSecrets detects unencrypted env and API tokens`() = runTest {
        val sensitiveFiles = listOf(
            WorkspaceFile(
                id = "env_file",
                workspaceId = testWorkspaceId,
                name = ".env",
                path = ".env",
                content = "DATABASE_URL=postgres://user:password@localhost/db\nAPI_SECRET=super_secret_12345",
                extension = "env"
            ),
            WorkspaceFile(
                id = "service_file",
                workspaceId = testWorkspaceId,
                name = "secrets.js",
                path = "src/secrets.js",
                content = "const githubToken = 'ghp_123456789012345678901234567890123456';",
                extension = "js"
            )
        )

        val warnings = WorkspaceGitEngine.scanForSecrets(sensitiveFiles)
        assertTrue("Should detect sensitive files and tokens", warnings.isNotEmpty())
        assertTrue("Should detect .env file", warnings.any { it.filePath == ".env" })
        assertTrue("Should detect GitHub Personal Access Token", warnings.any { it.secretType.contains("GitHub") })
    }

    @Test
    fun `commitChanges blocks unencrypted secrets unless explicitly overridden`() = runTest {
        repository.initGitRepository(testWorkspaceId).getOrThrow()

        // Insert hardcoded OpenAI key
        dao.insertFile(WorkspaceFile(
            id = "config_secret",
            workspaceId = testWorkspaceId,
            name = "config.js",
            path = "src/config.js",
            content = "export const key = 'sk-1234567890abcdef1234567890abcdef12';",
            extension = "js"
        ))

        // Attempt commit without override
        val blockedResult = repository.commitChanges(
            workspaceId = testWorkspaceId,
            message = "Add config with secret",
            overrideSecretWarning = false
        )
        assertTrue("Commit should be blocked", blockedResult.isFailure)
        assertTrue("Error should mention security / secrets", blockedResult.exceptionOrNull()?.message?.contains("أسرار") == true)

        // Attempt commit with explicit override
        val allowedResult = repository.commitChanges(
            workspaceId = testWorkspaceId,
            message = "Add config with secret (override)",
            overrideSecretWarning = true
        )
        assertTrue("Commit with override should succeed", allowedResult.isSuccess)
    }

    @Test
    fun `createBranch and checkoutBranch switch working tree snapshot`() = runTest {
        repository.initGitRepository(testWorkspaceId).getOrThrow()

        // Create feature branch
        val branchResult = repository.createBranch(testWorkspaceId, "feature-profile")
        assertTrue("Create branch should succeed", branchResult.isSuccess)
        val branch = branchResult.getOrThrow()
        assertEquals("feature-profile", branch.name)

        // Switch to feature branch
        repository.checkoutBranch(testWorkspaceId, "feature-profile").getOrThrow()

        // Add a file in feature branch
        dao.insertFile(WorkspaceFile(
            id = "profile_file",
            workspaceId = testWorkspaceId,
            name = "Profile.js",
            path = "src/Profile.js",
            content = "export function Profile() { return <div>Profile</div>; }",
            extension = "js"
        ))
        val featureCommit = repository.commitChanges(testWorkspaceId, "Add Profile component").getOrThrow()

        // Switch back to main
        repository.checkoutBranch(testWorkspaceId, "main").getOrThrow()
        val filesOnMain = dao.getAllFilesSync(testWorkspaceId)
        assertFalse("Profile.js should not be on main branch", filesOnMain.any { it.path == "src/Profile.js" })

        // Switch back to feature branch
        repository.checkoutBranch(testWorkspaceId, "feature-profile").getOrThrow()
        val filesOnFeature = dao.getAllFilesSync(testWorkspaceId)
        assertTrue("Profile.js should be restored on feature branch", filesOnFeature.any { it.path == "src/Profile.js" })
    }

    @Test
    fun `restoreToCommit rolls back project files to previous snapshot`() = runTest {
        val initialCommit = repository.initGitRepository(testWorkspaceId).getOrThrow()

        // Commit 2: Change App.js and delete App.test.js
        val appFile = dao.getFileByPath(testWorkspaceId, "src/App.js")!!
        dao.insertFile(appFile.copy(content = "Content V2"))
        val testFile = dao.getFileByPath(testWorkspaceId, "src/App.test.js")!!
        dao.deleteFile(testFile.id)

        val commit2 = repository.commitChanges(testWorkspaceId, "V2 changes").getOrThrow()

        // Restore to initial commit
        val restoreResult = repository.restoreToCommit(testWorkspaceId, initialCommit.id)
        assertTrue("Restore should succeed", restoreResult.isSuccess)

        val restoredFiles = dao.getAllFilesSync(testWorkspaceId)
        val restoredApp = restoredFiles.find { it.path == "src/App.js" }
        assertNotNull(restoredApp)
        assertTrue("Content should be restored to initial", restoredApp!!.content.contains("Hello Git"))
        assertTrue("App.test.js should be restored", restoredFiles.any { it.path == "src/App.test.js" })
    }

    @Test
    fun `detectAffectedTests identifies affected test files when source files change`() {
        val diffs = listOf(
            DiffUtils.computeDiff("src/App.js", "old", "new"),
            DiffUtils.computeDiff("src/components/Header.jsx", "", "header")
        )
        val allFiles = listOf(
            WorkspaceFile(path = "src/App.test.js", name = "App.test.js", content = "import App from './App';"),
            WorkspaceFile(path = "src/components/Header.test.jsx", name = "Header.test.jsx", content = "import Header from './Header';"),
            WorkspaceFile(path = "src/other/Unrelated.test.js", name = "Unrelated.test.js", content = "test('unrelated', () => {});")
        )

        val affected = WorkspaceGitEngine.detectAffectedTests(diffs, allFiles)
        assertEquals(2, affected.size)
        assertTrue("Should include App.test.js", affected.contains("src/App.test.js"))
        assertTrue("Should include Header.test.jsx", affected.contains("src/components/Header.test.jsx"))
        assertFalse("Should not include Unrelated.test.js", affected.contains("src/other/Unrelated.test.js"))
    }
}
