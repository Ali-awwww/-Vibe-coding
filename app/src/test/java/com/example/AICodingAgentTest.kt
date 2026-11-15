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
class AICodingAgentTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: VibeStudioDao
    private lateinit var crashRepository: com.example.domain.repository.CrashRepository
    private lateinit var repository: VibeStudioRepositoryImpl

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

        override suspend fun generateResponse(
            userId: String,
            modelId: String,
            prompt: String,
            systemInstruction: String?,
            taskId: String?,
            taskType: AITaskType
        ): Result<String> {
            return if (prompt.contains("JSON SCHEMA") && prompt.contains("intent")) {
                Result.success("""
                    {
                      "intent": "إضافة نظام تسجيل دخول",
                      "detectedTech": "Kotlin / Compose",
                      "affectedFiles": ["src/ui/LoginScreen.kt", "src/auth/AuthManager.kt"],
                      "steps": [
                        {
                          "order": 1,
                          "title": "فحص بنية المشروع",
                          "description": "فحص البنية للتأكد من عدم وجود تعارضات",
                          "targetFiles": ["src/ui/LoginScreen.kt"],
                          "actionType": "INSPECT"
                        },
                        {
                          "order": 2,
                          "title": "إنشاء واجهة تسجيل الدخول",
                          "description": "إنشاء كومبوننت شاشة الدخول وحقول الإدخال",
                          "targetFiles": ["src/ui/LoginScreen.kt"],
                          "actionType": "CREATE_FILE"
                        },
                        {
                          "order": 3,
                          "title": "ربط منطق المصادقة والتحقق",
                          "description": "كتابة منطق التحقق من اسم المستخدم وكلمة المرور",
                          "targetFiles": ["src/auth/AuthManager.kt"],
                          "actionType": "CREATE_FILE"
                        }
                      ]
                    }
                """.trimIndent())
            } else if (prompt.contains("Principal Code Reviewer")) {
                Result.success("""
                    {
                      "issues": [
                        {
                          "filePath": "src/auth/AuthManager.kt",
                          "line": 15,
                          "severity": "WARNING",
                          "title": "Missing password complexity validation",
                          "description": "Passwords under 6 characters are accepted",
                          "recommendation": "Add minimum length validation of 8 characters"
                        }
                      ],
                      "summary": "1 warning identified"
                    }
                """.trimIndent())
            } else {
                Result.success("""
                    {
                      "filePath": "src/ui/LoginScreen.kt",
                      "action": "CREATE",
                      "content": "// Production ready LoginScreen\npackage com.example.ui\nfun LoginScreen() {}\n",
                      "explanation": "Created login UI component"
                    }
                """.trimIndent())
            }
        }
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.vibeStudioDao()
        val crashDao = database.crashDao()
        crashRepository = object : com.example.domain.repository.CrashRepository {
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
            override suspend fun getVersionStats(projectId: String, version: String) = VersionStats(
                version = version,
                crashCount = 0,
                affectedUsers = 0,
                topErrors = emptyList(),
                newErrorsCount = 0,
                fixedErrorsCount = 0,
                crashRateChange = 0f
            )
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
            override suspend fun generateFixSuggestion(crashReport: CrashReport, diagnosis: AIDiagnosis) = Result.success(FixSuggestion(crashId = crashReport.id, originalCode = "", suggestedCode = "", explanation = "test fix"))
            override suspend fun applyFixAndCreatePR(fixId: String, userId: String) = Result.success("https://github.com/test/repo/pull/1")
            override fun getAllVerifications() = flowOf(emptyList<CrashVerificationRecord>())
            override fun getVerificationsByProject(projectId: String) = flowOf(emptyList<CrashVerificationRecord>())
            override fun getVerificationsByCrash(crashId: String) = flowOf(emptyList<CrashVerificationRecord>())
            override suspend fun getVerificationById(id: String): CrashVerificationRecord? = null
            override suspend fun verifyFix(crashId: String, fixSuggestionId: String?, newVersion: String, buildId: String, scenarioName: String, simulatedOutcome: VerificationStatus?) = Result.success(CrashVerificationRecord(crashId = crashId, newAppVersion = newVersion, buildId = buildId, scenarioName = scenarioName))
            override suspend fun addVerificationRecord(record: CrashVerificationRecord) {}
            override fun getNotifications() = flowOf(emptyList<SystemNotification>())
            override fun getNotificationsForProject(projectId: String) = flowOf(emptyList<SystemNotification>())
            override fun getUnreadNotificationCount() = flowOf(0)
            override suspend fun addNotification(notification: SystemNotification) {}
            override suspend fun markNotificationAsRead(id: String) {}
            override suspend fun markAllNotificationsAsRead() {}
            override suspend fun clearNotifications() {}
        }
        repository = VibeStudioRepositoryImpl(dao, fakeAIModelRepository, crashRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `DiffUtils calculates additions deletions and diff lines correctly`() {
        val oldCode = "line1\nline2\nline3"
        val newCode = "line1\nline2_modified\nline3\nline4_added"

        val diff = DiffUtils.computeDiff("src/App.kt", oldCode, newCode)

        assertEquals("src/App.kt", diff.path)
        assertEquals(DiffChangeType.MODIFIED, diff.changeType)
        assertTrue("Additions should be greater than 0", diff.additions > 0)
        assertTrue("Deletions should be greater than 0", diff.deletions > 0)
        assertTrue("Should have diff lines", diff.diffLines.isNotEmpty())
    }

    @Test
    fun `DiffUtils accurately tags newly created and deleted files`() {
        val createdDiff = DiffUtils.computeDiff("src/NewFile.kt", "", "class NewFile")
        assertEquals(DiffChangeType.CREATED, createdDiff.changeType)
        assertEquals(1, createdDiff.additions)
        assertEquals(0, createdDiff.deletions)

        val deletedDiff = DiffUtils.computeDiff("src/OldFile.kt", "class OldFile", "")
        assertEquals(DiffChangeType.DELETED, deletedDiff.changeType)
        assertEquals(0, deletedDiff.additions)
        assertEquals(1, deletedDiff.deletions)
    }

    @Test
    fun `recovery checkpoint creates snapshot and restores exact file state`() = runTest {
        val workspaceId = UUID.randomUUID().toString()
        dao.insertWorkspace(Workspace(id = workspaceId, name = "Test Workspace"))

        val file1 = WorkspaceFile(
            id = "f1",
            workspaceId = workspaceId,
            name = "App.kt",
            path = "src/App.kt",
            content = "original code"
        )
        dao.insertFile(file1)

        // 1. Create Checkpoint
        val checkpointId = repository.createCheckpoint(workspaceId, "Before Refactor", "Safe snapshot")
        assertNotNull(checkpointId)

        // 2. Corrupt or modify files
        dao.insertFile(file1.copy(content = "corrupted bad code"))
        val currentFile = dao.getFileById("f1")
        assertEquals("corrupted bad code", currentFile?.content)

        // 3. Rollback to checkpoint
        val revertResult = repository.revertToCheckpoint(checkpointId)
        assertTrue("Revert should succeed", revertResult.isSuccess)

        // 4. Verify original code restored
        val restoredFile = dao.getFileByPath(workspaceId, "src/App.kt")
        assertNotNull(restoredFile)
        assertEquals("original code", restoredFile?.content)
    }

    @Test
    fun `retrieveRelevantFiles returns files matching intent keywords`() = runTest {
        val workspaceId = UUID.randomUUID().toString()
        dao.insertWorkspace(Workspace(id = workspaceId, name = "Test Workspace"))

        dao.insertFile(WorkspaceFile(id = "1", workspaceId = workspaceId, name = "AuthService.kt", path = "src/auth/AuthService.kt", content = "class AuthService"))
        dao.insertFile(WorkspaceFile(id = "2", workspaceId = workspaceId, name = "Theme.kt", path = "src/theme/Theme.kt", content = "val ButtonColor = Blue"))
        dao.insertFile(WorkspaceFile(id = "3", workspaceId = workspaceId, name = "Database.kt", path = "src/db/Database.kt", content = "class Database"))

        // When asking for styling / color
        val styleFiles = repository.retrieveRelevantFiles(workspaceId, "غيّر لون الزر إلى الأزرق", maxFiles = 2)
        assertTrue("Theme file should be returned for style request", styleFiles.any { it.path.contains("theme") })

        // When asking for login / auth
        val authFiles = repository.retrieveRelevantFiles(workspaceId, "أضف نظام تسجيل دخول", maxFiles = 2)
        assertTrue("Auth file should be returned for login request", authFiles.any { it.path.contains("auth") })
    }

    @Test
    fun `planTask breaks down user request into structured steps`() = runTest {
        val workspaceId = UUID.randomUUID().toString()
        dao.insertWorkspace(Workspace(id = workspaceId, name = "Login Project", techStack = "Kotlin / Compose"))

        val planResult = repository.planTask("user_1", workspaceId, "أضف نظام تسجيل دخول")
        assertTrue("Plan should succeed", planResult.isSuccess)

        val plan = planResult.getOrThrow()
        assertEquals("إضافة نظام تسجيل دخول", plan.intent)
        assertEquals(3, plan.steps.size)
        assertEquals("فحص بنية المشروع", plan.steps[0].title)
        assertEquals(StepStatus.PENDING, plan.steps[0].status)
    }

    @Test
    fun `sensitive action detection gates file deletions`() = runTest {
        val workspaceId = UUID.randomUUID().toString()
        dao.insertWorkspace(Workspace(id = workspaceId, name = "Test Workspace"))

        val result = repository.executeInstruction(
            userId = "user_1",
            workspaceId = workspaceId,
            instruction = "احذف ملف src/auth/OldAuth.kt",
            mode = AgentExecutionMode.AGENT
        )

        assertTrue(result.isSuccess)
        val agentResult = result.getOrThrow()
        assertNotNull("Sensitive action must be flagged", agentResult.pendingAction)
        assertEquals(SensitiveActionType.DELETE_FILE, agentResult.pendingAction?.type)
        assertEquals("src/auth/OldAuth.kt", agentResult.pendingAction?.targetPath)
    }

    @Test
    fun `reviewWorkspaceCode identifies code quality and security issues`() = runTest {
        val workspaceId = UUID.randomUUID().toString()
        dao.insertWorkspace(Workspace(id = workspaceId, name = "Review Workspace"))
        dao.insertFile(WorkspaceFile(id = "f_auth", workspaceId = workspaceId, name = "AuthManager.kt", path = "src/auth/AuthManager.kt", content = "fun login() {}"))

        val reviewResult = repository.reviewWorkspaceCode("user_1", workspaceId)
        assertTrue(reviewResult.isSuccess)

        val issues = reviewResult.getOrThrow()
        assertEquals(1, issues.size)
        assertEquals(IssueSeverity.WARNING, issues[0].severity)
        assertEquals("Missing password complexity validation", issues[0].title)
    }

    @Test
    fun `detectTechRuntime correctly detects NextJS, Vite, Python, Flutter and Android`() {
        val workspace = Workspace(id = "ws_test", name = "Test App")

        // 1. Next.js
        val nextFiles = listOf(
            WorkspaceFile(id = "1", workspaceId = "ws_test", name = "package.json", path = "package.json", content = "{\"dependencies\": {\"next\": \"14.0.0\", \"react\": \"18.2.0\"}}")
        )
        val nextRuntime = com.example.util.WorkspaceSandboxEngine.detectTechRuntime(workspace, nextFiles)
        assertTrue(nextRuntime.name.contains("Next.js"))
        assertEquals("npm run dev", nextRuntime.command)
        assertEquals(3000, nextRuntime.defaultPort)
        assertTrue(nextRuntime.isBrowserPreviewSupported)

        // 2. Vite React
        val viteFiles = listOf(
            WorkspaceFile(id = "2", workspaceId = "ws_test", name = "package.json", path = "package.json", content = "{\"devDependencies\": {\"vite\": \"5.2.0\"}}")
        )
        val viteRuntime = com.example.util.WorkspaceSandboxEngine.detectTechRuntime(workspace, viteFiles)
        assertTrue(viteRuntime.name.contains("React (Vite)"))
        assertEquals(5173, viteRuntime.defaultPort)

        // 3. Python FastAPI
        val pyFiles = listOf(
            WorkspaceFile(id = "3", workspaceId = "ws_test", name = "main.py", path = "main.py", content = "from fastapi import FastAPI\napp = FastAPI()")
        )
        val pyRuntime = com.example.util.WorkspaceSandboxEngine.detectTechRuntime(workspace, pyFiles)
        assertTrue(pyRuntime.name.contains("FastAPI"))
        assertEquals(8000, pyRuntime.defaultPort)

        // 4. Flutter
        val flutterFiles = listOf(
            WorkspaceFile(id = "4", workspaceId = "ws_test", name = "pubspec.yaml", path = "pubspec.yaml", content = "name: my_flutter_app\nenvironment:\n  sdk: '>=3.0.0'")
        )
        val flutterRuntime = com.example.util.WorkspaceSandboxEngine.detectTechRuntime(workspace, flutterFiles)
        assertTrue(flutterRuntime.name.contains("Flutter"))
        assertTrue(flutterRuntime.isNativeApp)
        assertTrue(flutterRuntime.isBrowserPreviewSupported)

        // 5. Android Native
        val androidFiles = listOf(
            WorkspaceFile(id = "5", workspaceId = "ws_test", name = "build.gradle.kts", path = "build.gradle.kts", content = "plugins { id(\"com.android.application\") }")
        )
        val androidRuntime = com.example.util.WorkspaceSandboxEngine.detectTechRuntime(workspace, androidFiles)
        assertTrue(androidRuntime.name.contains("Android"))
        assertTrue(androidRuntime.isNativeApp)
        assertFalse(androidRuntime.isBrowserPreviewSupported)
    }

    @Test
    fun `executeSandboxCommand enforces sandbox security and blocks escape attempts`() {
        val workspace = Workspace(id = "ws_sec", name = "Security Sandbox")
        val files = emptyList<WorkspaceFile>()

        // Host root deletion attempt
        val res1 = com.example.util.WorkspaceSandboxEngine.executeSandboxCommand(workspace, files, "rm -rf /")
        assertEquals(126, res1.result.exitCode)
        assertFalse(res1.result.isSuccess)
        assertTrue(res1.result.stderr.contains("SECURITY_VIOLATION"))

        // Path traversal to host secrets attempt
        val res2 = com.example.util.WorkspaceSandboxEngine.executeSandboxCommand(workspace, files, "cat ../../etc/passwd")
        assertEquals(126, res2.result.exitCode)
        assertFalse(res2.result.isSuccess)
        assertTrue(res2.result.stderr.contains("SECURITY_VIOLATION"))

        // Sudo privilege escalation attempt
        val res3 = com.example.util.WorkspaceSandboxEngine.executeSandboxCommand(workspace, files, "sudo apt-get install rootkit")
        assertEquals(126, res3.result.exitCode)
        assertFalse(res3.result.isSuccess)
    }

    @Test
    fun `sanitizeOutput redacts sensitive API keys and tokens`() {
        val raw = "Loaded key AIzaSyD0123456789abcdefghijklmnopqrstuv and GitHub ghp_1234567890abcdefghijklmnopqrstuvwxyz and OpenAI sk-abcdefghijklmnopqrstuvwxyz1234567890"
        val sanitized = com.example.util.WorkspaceSandboxEngine.sanitizeOutput(raw)

        assertFalse("Gemini key must be redacted", sanitized.contains("AIzaSyD0123456789abcdefghijklmnopqrstuv"))
        assertTrue(sanitized.contains("[REDACTED_GEMINI_KEY]"))
        assertFalse("GitHub token must be redacted", sanitized.contains("ghp_1234567890abcdefghijklmnopqrstuvwxyz"))
        assertTrue(sanitized.contains("[REDACTED_GITHUB_TOKEN]"))
        assertFalse("API key must be redacted", sanitized.contains("sk-abcdefghijklmnopqrstuvwxyz1234567890"))
        assertTrue(sanitized.contains("[REDACTED_API_KEY]"))
    }

    @Test
    fun `executeTerminalCommand in repository executes command and updates files and logs`() = runTest {
        val workspaceId = UUID.randomUUID().toString()
        dao.insertWorkspace(Workspace(id = workspaceId, name = "Term Project"))
        dao.insertFile(WorkspaceFile(
            id = "pkg_1",
            workspaceId = workspaceId,
            name = "package.json",
            path = "package.json",
            content = "{\n  \"name\": \"test-app\"\n}"
        ))

        // 1. Touch file
        val touchResult = repository.executeTerminalCommand(workspaceId, "touch src/components/Header.tsx")
        assertTrue(touchResult.isSuccess)
        val createdFile = dao.getFileByPath(workspaceId, "src/components/Header.tsx")
        assertNotNull("File created via touch must exist in DB", createdFile)

        // 2. Install package
        val installResult = repository.executeTerminalCommand(workspaceId, "npm install axios")
        assertTrue(installResult.isSuccess)
        val updatedPkg = dao.getFileByPath(workspaceId, "package.json")
        assertNotNull(updatedPkg)
        assertTrue("package.json must contain axios", updatedPkg!!.content.contains("axios"))

        // 3. Verify terminal history
        val history = repository.getTerminalHistory(workspaceId).first()
        assertEquals(2, history.size)
    }

    @Test
    fun `dev server lifecycle correctly allocates port, handles start, restart, and stop`() = runTest {
        val workspaceId = UUID.randomUUID().toString()
        dao.insertWorkspace(Workspace(id = workspaceId, name = "Dev Lifecycle Project"))
        dao.insertFile(WorkspaceFile(
            id = "pkg_dev",
            workspaceId = workspaceId,
            name = "package.json",
            path = "package.json",
            content = "{\"dependencies\": {\"vite\": \"5.0.0\"}}"
        ))

        // Start Dev Server
        val startRes = repository.startDevServer(workspaceId)
        assertTrue(startRes.isSuccess)
        val startState = startRes.getOrThrow()
        assertEquals(ServerStatus.RUNNING, startState.status)
        assertEquals(5173, startState.port)
        assertNotNull(startState.activeProcessId)

        // Restart Dev Server
        val restartRes = repository.restartDevServer(workspaceId)
        assertTrue(restartRes.isSuccess)
        val restartState = restartRes.getOrThrow()
        assertEquals(ServerStatus.RUNNING, restartState.status)

        // Stop Dev Server
        val stopRes = repository.stopDevServer(workspaceId)
        assertTrue(stopRes.isSuccess)
        val stopState = repository.getSandboxRuntimeState(workspaceId).first()
        assertNotNull(stopState)
        assertEquals(ServerStatus.STOPPED, stopState!!.status)
    }

    @Test
    fun `connectLocalAgent and disconnectLocalAgent establish workstation bridge`() = runTest {
        val workspaceId = UUID.randomUUID().toString()
        dao.insertWorkspace(Workspace(id = workspaceId, name = "Bridge Project"))

        // Connect Local Agent
        val connectRes = repository.connectLocalAgent(workspaceId, "http://localhost:8974", "secret_token_123")
        assertTrue(connectRes.isSuccess)
        assertEquals(LocalAgentStatus.CONNECTED, connectRes.getOrThrow())

        // Disconnect
        val disconnectRes = repository.disconnectLocalAgent(workspaceId)
        assertTrue(disconnectRes.isSuccess)
    }

    @Test
    fun `generateLivePreview produces HTML with console bridge and combined CSS`() = runTest {
        val workspaceId = UUID.randomUUID().toString()
        dao.insertWorkspace(Workspace(id = workspaceId, name = "Preview Project"))
        dao.insertFile(WorkspaceFile(
            id = "html_1",
            workspaceId = workspaceId,
            name = "index.html",
            path = "index.html",
            content = "<!DOCTYPE html><html><head><title>App</title></head><body><h1>Hello</h1></body></html>"
        ))
        dao.insertFile(WorkspaceFile(
            id = "css_1",
            workspaceId = workspaceId,
            name = "style.css",
            path = "style.css",
            content = "body { background: #000; color: #fff; }",
            extension = "css"
        ))

        val previewRes = repository.generateLivePreview(workspaceId)
        assertTrue(previewRes.isSuccess)
        val html = previewRes.getOrThrow()

        assertTrue("Should include combined CSS", html.contains("background: #000"))
        assertTrue("Should inject console bridge", html.contains("originalLog.apply"))
    }

    @Test
    fun `analyzeAppRequirements correctly extracts e-commerce specifications`() = runTest {
        val prompt = "أنشئ متجرًا إلكترونيًا لعرض المنتجات وإدارة الطلبات وحساب المستخدمين"
        val res = repository.analyzeAppRequirements("user_1", prompt)
        assertTrue(res.isSuccess)

        val bp = res.getOrThrow()
        assertTrue(bp.appType.contains("متجر") || bp.appType.contains("Commerce"))
        assertTrue(bp.coreFeatures.any { it.contains("كتالوج") || it.contains("منتجات") })
        assertTrue(bp.coreFeatures.any { it.contains("سلة") || it.contains("طلبات") })
        assertTrue(bp.screens.any { it.route == "/" })
        assertTrue(bp.screens.any { it.route.contains("cart") })
        assertTrue(bp.databaseSchema.any { it.name == "Product" })
        assertTrue(bp.databaseSchema.any { it.name == "Order" })
        assertTrue(bp.userRoles.any { it.contains("Admin") || it.contains("مدير") })
        assertTrue(bp.isCrashIntelligenceIntegrated)
        assertTrue(bp.securityRequirements.isNotEmpty())
    }

    @Test
    fun `analyzeAppRequirements correctly extracts task manager with Flutter specifications`() = runTest {
        val prompt = "أنشئ تطبيقًا لإدارة المهام باستخدام Flutter"
        val res = repository.analyzeAppRequirements("user_1", prompt)
        assertTrue(res.isSuccess)

        val bp = res.getOrThrow()
        assertTrue(bp.targetPlatform.contains("Flutter"))
        assertTrue(bp.suggestedTechStack.contains("Flutter"))
        assertTrue(bp.screens.any { it.name.contains("المهام") || it.name.contains("Kanban") })
        assertTrue(bp.databaseSchema.any { it.name == "Task" })
    }

    @Test
    fun `buildApplicationFromBlueprint creates complete workspace with code files, persistence, checkpoint, and dev server`() = runTest {
        val prompt = "أنشئ متجرًا إلكترونيًا لعرض المنتجات وإدارة الطلبات وحساب المستخدمين"
        val bp = repository.analyzeAppRequirements("user_1", prompt).getOrThrow()

        val progressStages = mutableListOf<AppBuildStage>()
        val result = repository.buildApplicationFromBlueprint("user_1", bp) { progress ->
            progressStages.add(progress.stage)
        }

        assertTrue("Build application should succeed", result.isSuccess)
        val workspace = result.getOrThrow()
        assertNotNull(workspace.id)

        // 1. Verify workspace stored in database
        val savedWorkspace = dao.getWorkspaceById(workspace.id)
        assertNotNull(savedWorkspace)
        assertEquals(bp.name, savedWorkspace!!.name)

        // 2. Verify scaffolded files exist in database
        val files = dao.getAllFilesSync(workspace.id)
        assertTrue("Should generate at least 5 files", files.size >= 5)

        val pkgJson = files.find { it.path == "package.json" }
        assertNotNull("package.json must be generated", pkgJson)

        val appTsx = files.find { it.path == "src/App.tsx" }
        assertNotNull("src/App.tsx must be generated", appTsx)
        assertTrue("App.tsx should contain app title", appTsx!!.content.contains(bp.name))

        val dataService = files.find { it.path == "src/services/dataService.ts" }
        assertNotNull("Data service must be generated", dataService)

        val crashReporter = files.find { it.path == "src/services/crashReporter.ts" }
        assertNotNull("Crash reporter must be generated", crashReporter)
        assertTrue("Crash reporter must hook into Crash Intelligence", crashReporter!!.content.contains("CrashIntelligence"))

        // 3. Verify checkpoints
        val checkpoints = dao.getCheckpoints(workspace.id).first()
        assertTrue("Initial checkpoint must be created", checkpoints.isNotEmpty())

        // 4. Verify progress stages reached
        assertTrue(progressStages.contains(AppBuildStage.SCAFFOLDING_FILES))
        assertTrue(progressStages.contains(AppBuildStage.CREATING_UI_COMPONENTS))
        assertTrue(progressStages.contains(AppBuildStage.WIRING_DATA_SERVICES))
        assertTrue(progressStages.contains(AppBuildStage.COMPLETED))

        // 5. Verify sandbox server started
        val runtime = repository.getSandboxRuntimeState(workspace.id).first()
        assertNotNull(runtime)
        assertEquals(ServerStatus.RUNNING, runtime!!.status)
    }

    // ========================================================================
    // AI Testing & Quality Assurance Engine Unit Tests
    // ========================================================================

    @Test
    fun `VibeQualityTestingEngine selects test suites strictly based on tech stack`() {
        // Web project
        val webWorkspace = Workspace(id = "w1", name = "Web Store", techStack = "React + Vite + TypeScript")
        val webFiles = listOf(
            WorkspaceFile(id = "f1", workspaceId = "w1", name = "package.json", path = "package.json", content = "{}"),
            WorkspaceFile(id = "f2", workspaceId = "w1", name = "routes.ts", path = "src/api/routes.ts", content = "export function api() {}")
        )
        val webSuites = com.example.util.VibeQualityTestingEngine.selectSuitesForTechStack(webWorkspace, webFiles)
        assertTrue(webSuites.contains(TestType.SYNTAX_CHECK))
        assertTrue(webSuites.contains(TestType.TYPE_CHECK))
        assertTrue(webSuites.contains(TestType.LINTING))
        assertTrue(webSuites.contains(TestType.API_CONTRACT_TEST))
        assertTrue(webSuites.contains(TestType.BUILD_TEST))

        // Flutter project
        val flutterWorkspace = Workspace(id = "w2", name = "Mobile App", techStack = "Flutter Mobile")
        val flutterFiles = listOf(
            WorkspaceFile(id = "f3", workspaceId = "w2", name = "pubspec.yaml", path = "pubspec.yaml", content = "name: app")
        )
        val flutterSuites = com.example.util.VibeQualityTestingEngine.selectSuitesForTechStack(flutterWorkspace, flutterFiles)
        assertTrue(flutterSuites.contains(TestType.SYNTAX_CHECK))
        assertTrue(flutterSuites.contains(TestType.UI_RENDER_TEST))
        assertFalse("Flutter should not run Node API contract tests", flutterSuites.contains(TestType.API_CONTRACT_TEST))
    }

    @Test
    fun `auditSyntax correctly catches JSON syntax errors with line numbers`() {
        val workspace = Workspace(id = "w_json", name = "JSON Audit", techStack = "Web")
        val badJsonFile = WorkspaceFile(
            id = "f_bad_json",
            workspaceId = "w_json",
            name = "config.json",
            path = "config.json",
            extension = "json",
            content = "{\n  \"name\": \"crash\",\n  \"invalid\": ,\n}"
        )

        val report = com.example.util.VibeQualityTestingEngine.executeIntelligentAudit(workspace, listOf(badJsonFile))
        assertTrue(report.failedTests > 0)
        assertEquals(ProductionReadiness.NOT_READY, report.readiness)

        val failedTest = report.testResults.find { it.targetFile == "config.json" }
        assertNotNull(failedTest)
        assertEquals(TestStatus.FAILED, failedTest!!.status)
        assertTrue(failedTest.errorMessage!!.contains("JSON"))

        val errorFinding = report.findings.find { it.filePath == "config.json" }
        assertNotNull(errorFinding)
        assertEquals(IssueSeverity.ERROR, errorFinding!!.severity)
    }

    @Test
    fun `auditSyntax detects unbalanced curly braces in source code`() {
        val workspace = Workspace(id = "w_braces", name = "Brace Audit", techStack = "TypeScript")
        val badCode = WorkspaceFile(
            id = "f_code",
            workspaceId = "w_braces",
            name = "App.tsx",
            path = "src/App.tsx",
            extension = "tsx",
            content = "export function App() {\n  if (true) {\n    return <div>Hello</div>;\n  \n}" // Missing 1 closing brace
        )

        val report = com.example.util.VibeQualityTestingEngine.executeIntelligentAudit(workspace, listOf(badCode))
        val syntaxFail = report.testResults.find { it.type == TestType.SYNTAX_CHECK && it.status == TestStatus.FAILED }
        assertNotNull("Should detect unbalanced braces", syntaxFail)
        assertTrue(syntaxFail!!.errorMessage!!.contains("أقواس متعرجة"))
    }

    @Test
    fun `auditTypesAndImports detects missing local imported files`() {
        val workspace = Workspace(id = "w_imports", name = "Import Audit", techStack = "React + TypeScript")
        val appFile = WorkspaceFile(
            id = "f_app",
            workspaceId = "w_imports",
            name = "App.tsx",
            path = "src/App.tsx",
            extension = "tsx",
            content = "import React from 'react';\nimport { MissingButton } from './components/MissingButton';\nexport function App() {}"
        )

        val report = com.example.util.VibeQualityTestingEngine.executeIntelligentAudit(workspace, listOf(appFile))
        val brokenImportTest = report.testResults.find { it.type == TestType.TYPE_CHECK && it.status == TestStatus.FAILED }
        assertNotNull("Should fail type check for missing import file", brokenImportTest)
        assertTrue(brokenImportTest!!.errorMessage!!.contains("MissingButton"))

        val brokenImportFinding = report.findings.find { it.category == QualityCategory.LOGICAL_ERROR }
        assertNotNull(brokenImportFinding)
        assertEquals(IssueSeverity.ERROR, brokenImportFinding!!.severity)
    }

    @Test
    fun `auditSecurityAndPerformance catches hardcoded secrets and dangerous eval`() {
        val workspace = Workspace(id = "w_sec", name = "Security Audit", techStack = "Node.js")
        val vulnerableFile = WorkspaceFile(
            id = "f_sec",
            workspaceId = "w_sec",
            name = "auth.ts",
            path = "src/auth.ts",
            extension = "ts",
            content = """
                const openaiKey = "sk-12345678901234567890123456";
                function runDynamic(code: string) {
                    eval(code);
                }
            """.trimIndent()
        )

        val report = com.example.util.VibeQualityTestingEngine.executeIntelligentAudit(workspace, listOf(vulnerableFile))
        val secretFinding = report.findings.find { it.title.contains("OpenAI") || it.title.contains("مفتاح") }
        assertNotNull("Must detect hardcoded secret", secretFinding)
        assertEquals(IssueSeverity.ERROR, secretFinding!!.severity)

        val evalFinding = report.findings.find { it.title.contains("eval") }
        assertNotNull("Must detect eval execution", evalFinding)
        assertEquals(IssueSeverity.ERROR, evalFinding!!.severity)
    }

    @Test
    fun `auditDeadCodeAndDependencies identifies unreferenced orphan files`() {
        val workspace = Workspace(id = "w_dead", name = "Dead Code Audit", techStack = "React")
        val mainFile = WorkspaceFile(
            id = "f_main",
            workspaceId = "w_dead",
            name = "App.tsx",
            path = "src/App.tsx",
            extension = "tsx",
            content = "export function App() { return <div>App</div>; }"
        )
        val orphanFile = WorkspaceFile(
            id = "f_orphan",
            workspaceId = "w_dead",
            name = "AbandonedHelper.ts",
            path = "src/utils/AbandonedHelper.ts",
            extension = "ts",
            content = "export function oldHelper() { return 42; }"
        )

        val report = com.example.util.VibeQualityTestingEngine.executeIntelligentAudit(workspace, listOf(mainFile, orphanFile))
        val deadFileFinding = report.findings.find { it.category == QualityCategory.DEAD_CODE }
        assertNotNull("Should detect unreferenced orphan file", deadFileFinding)
        assertTrue(deadFileFinding!!.description.contains("AbandonedHelper.ts"))
    }

    @Test
    fun `AI Auto Debug strictly enforces maximum 3 attempts and prevents infinite loop`() {
        val workspace = Workspace(id = "w_loop", name = "Loop Test", techStack = "Web")
        val report = QualityAuditReport(
            id = "rep1",
            workspaceId = "w_loop",
            testResults = listOf(
                TestCaseResult(
                    id = "t1",
                    suiteName = "Syntax",
                    name = "Test 1",
                    status = TestStatus.FAILED,
                    targetFile = "src/App.tsx",
                    errorMessage = "Syntax error"
                )
            ),
            failedTests = 1
        )
        val files = listOf(
            WorkspaceFile(id = "f_app", workspaceId = "w_loop", name = "App.tsx", path = "src/App.tsx", content = "code")
        )

        // Attempt 1 -> allowed
        val proposal1 = com.example.util.VibeQualityTestingEngine.diagnoseFailureAndProposeFix(workspace, files, report, 1)
        assertFalse(proposal1.isStuckOrNoProgress)
        assertEquals(1, proposal1.attempt)

        // Attempt 4 -> halted by safety limit
        val proposal4 = com.example.util.VibeQualityTestingEngine.diagnoseFailureAndProposeFix(workspace, files, report, 4)
        assertTrue("Attempt > 3 must trigger loop prevention", proposal4.isStuckOrNoProgress)
        assertTrue(proposal4.diagnosedRootCause.contains("تجاوز الحد الأقصى"))
    }

    @Test
    fun `Testing Engine integrates with Crash Intelligence on test crash`() = runTest {
        val workspaceId = UUID.randomUUID().toString()
        dao.insertWorkspace(Workspace(id = workspaceId, name = "Crash Integration Test", ownerId = "user_qa"))

        val testCase = TestCaseResult(
            id = "test_crash_1",
            suiteName = "جناح اختبارات تكامل قاعدة البيانات",
            name = "NullPointerException in user profile loader",
            type = TestType.INTEGRATION_TEST,
            status = TestStatus.FAILED,
            targetFile = "src/services/userService.ts",
            lineNumber = 42,
            errorMessage = "NullPointerException: Cannot read property 'profile' of null",
            stackTrace = "NullPointerException: at userService.ts:42\n at App.tsx:18"
        )

        // Send failure to Crash Intelligence
        val crashResult = repository.sendFailureToCrashIntelligence(workspaceId, testCase)
        assertTrue("Crash export must succeed", crashResult.isSuccess)

        val crashReport = crashResult.getOrThrow()
        assertEquals(workspaceId, crashReport.applicationId)
        assertEquals(workspaceId, crashReport.projectId)
        assertEquals(Severity.CRITICAL, crashReport.severity)
        assertTrue(crashReport.message.contains("NullPointerException"))

        // Verify stored in crash repository
        val crashes = crashRepository.getAllCrashes().first()
        assertTrue("Crash must be saved in database", crashes.any { it.id == crashReport.id })
    }
}

