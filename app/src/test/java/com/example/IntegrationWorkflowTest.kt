package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.remote.BugsnagApi
import com.example.data.remote.SentryApi
import com.example.data.repository.CrashRepositoryImpl
import com.example.data.repository.SymbolicationRepositoryImpl
import com.example.domain.model.*
import com.example.domain.repository.GitHubRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IntegrationWorkflowTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: CrashRepositoryImpl

    private val stubGitHubRepository = object : GitHubRepository {
        override suspend fun connectGitHub(userId: String, code: String): Result<Unit> = Result.success(Unit)
        override suspend fun connectGitHubWithToken(userId: String, token: String): Result<Unit> = Result.success(Unit)
        override suspend fun disconnectGitHub(userId: String): Result<Unit> = Result.success(Unit)
        override suspend fun getGitHubConnection(userId: String): Result<GitHubConnection?> = Result.success(null)
        override suspend fun fetchRepositories(userId: String): Result<List<GitHubRepo>> = Result.success(emptyList())
        override suspend fun fetchBranches(userId: String, owner: String, repo: String): Result<List<GitHubBranch>> = Result.success(emptyList())
        override suspend fun linkRepositoryToApp(mapping: RepositoryMapping): Result<Unit> = Result.success(Unit)
        override fun getMappingsForProject(projectId: String): Flow<List<RepositoryMapping>> = flowOf(emptyList())
        override suspend fun getMappingForApp(applicationId: String): RepositoryMapping? = null
        override suspend fun fetchFileContent(userId: String, owner: String, repo: String, path: String, ref: String?): Result<String> = Result.success("")
        override suspend fun searchCode(userId: String, owner: String, repo: String, query: String, ref: String?): Result<List<String>> = Result.success(emptyList())
        override suspend fun createBranch(userId: String, owner: String, repo: String, branchName: String, fromRef: String): Result<String> = Result.success("sha-123")
        override suspend fun commitChanges(userId: String, owner: String, repo: String, branchName: String, path: String, content: String, message: String): Result<Unit> = Result.success(Unit)
        override suspend fun createPullRequest(userId: String, owner: String, repo: String, title: String, head: String, base: String, body: String): Result<String> =
            Result.success("https://github.com/$owner/$repo/pull/42")
    }

    private val stubSentryApi = object : SentryApi {
        override suspend fun getIssues(authToken: String, orgSlug: String, projectSlug: String, query: String?) = emptyList<com.example.data.remote.SentryIssue>()
        override suspend fun getIssueEvents(authToken: String, issueId: String) = emptyList<com.example.data.remote.SentryEvent>()
    }

    private val stubBugsnagApi = object : BugsnagApi {
        override suspend fun getErrors(authToken: String, projectId: String, perPage: Int) = emptyList<com.example.data.remote.BugsnagError>()
        override suspend fun getErrorEvents(authToken: String, errorId: String, perPage: Int) = emptyList<com.example.data.remote.BugsnagEvent>()
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val symbolicationRepo = SymbolicationRepositoryImpl(db.crashDao())
        
        val stubAIModelRepository = object : com.example.domain.repository.AIModelRepository {
            override fun getProviders(userId: String): Flow<List<AIProvider>> = flowOf(emptyList())
            override suspend fun getProviderById(userId: String, id: String): AIProvider? = null
            override suspend fun addProvider(provider: AIProvider) {}
            override suspend fun deleteProvider(userId: String, id: String) {}
            override suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long) {}
            override fun getModelsByProvider(providerId: String): Flow<List<AIModel>> = flowOf(emptyList())
            override fun getAllAvailableModels(freeOnly: Boolean): Flow<List<AIModel>> = flowOf(emptyList())
            override suspend fun getModelById(id: String): AIModel? = null
            override suspend fun syncModels(userId: String, providerId: String): Result<Unit> = Result.success(Unit)
            override suspend fun testConnection(userId: String, providerId: String): Result<Unit> = Result.success(Unit)
            override fun getRecentUsageLogs(userId: String): Flow<List<AIUsageLog>> = flowOf(emptyList())
            override fun getTotalCost(userId: String, since: Long): Flow<Double?> = flowOf(0.0)
            override suspend fun logUsage(log: AIUsageLog) {}
            override fun getUserSettings(userId: String): Flow<AIUserSettings?> = flowOf(null)
            override suspend fun updateUserSettings(settings: AIUserSettings) {}
            override suspend fun setDefaultModel(userId: String, modelId: String) {}
            override suspend fun getDefaultModel(userId: String): AIModel? = null
            override suspend fun generateResponse(userId: String, modelId: String, prompt: String, systemInstruction: String?, taskId: String?, taskType: AITaskType): Result<String> = 
                Result.success("{}")
        }

        repository = CrashRepositoryImpl(
            firestore = null,
            crashDao = db.crashDao(),
            sentryApi = stubSentryApi,
            bugsnagApi = stubBugsnagApi,
            githubRepository = stubGitHubRepository,
            symbolicationRepository = symbolicationRepo,
            aiModelRepository = stubAIModelRepository,
            auth = null
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testCrashIngestion_deduplicationAndNotificationDispatch() = runBlocking {
        // Step 1: Create project and application
        val project = Project(id = "proj-100", name = "Test Flight App", description = "CUJ Test Project", ownerId = "usr-dev")
        repository.addProject(project)

        val app = Application(
            id = "app-100",
            projectId = "proj-100",
            name = "Test Flight Mobile",
            packageName = "com.testflight.app",
            platform = "Android"
        )
        repository.addApplication(app)

        // Step 2: Establish an active test session
        val session = TestSession(
            id = "sess-100",
            projectId = "proj-100",
            applicationId = "app-100",
            buildId = "101",
            version = "1.0.0",
            deviceName = "Pixel 8 Pro Emulator",
            os = "Android 14",
            status = TestSessionStatus.APP_RUNNING,
            startTime = System.currentTimeMillis()
        )
        db.crashDao().insertTestSession(session)

        // Step 3: Ingest a Critical Crash report
        val crashReport = CrashReport(
            id = "crash-100",
            projectId = "proj-100",
            applicationId = "app-100",
            exceptionType = "NullPointerException",
            message = "Attempt to invoke virtual method on a null object reference in PaymentProcessor",
            stackTrace = "java.lang.NullPointerException: in PaymentProcessor.kt:42\n\tat com.testflight.app.PaymentProcessor.process(PaymentProcessor.kt:42)",
            appVersion = "1.0.0",
            source = IntegrationType.WEBHOOK,
            severity = Severity.CRITICAL,
            timestamp = System.currentTimeMillis()
        )

        repository.addCrash(crashReport)

        // Verify Issue was auto-created
        val issues = repository.getIssues("proj-100").first()
        assertEquals(1, issues.size)
        assertEquals("NullPointerException", issues.first().title)

        // Verify Test Session was auto-correlated and flagged as CRASH_DETECTED
        val updatedSession = db.crashDao().getTestSessionById("sess-100").first()
        assertNotNull(updatedSession)
        assertEquals(TestSessionStatus.CRASH_DETECTED, updatedSession?.status)
        assertEquals("crash-100", updatedSession?.crashId)

        // Verify Notification was dispatched
        val notifications = repository.getNotifications().first()
        assertTrue(notifications.any { it.type == NotificationType.CRASH_ALERT && it.targetCrashId == "crash-100" })

        // Verify Deduplication: duplicate crash reported within 5 seconds is discarded
        val duplicateCrash = crashReport.copy(id = "crash-101", timestamp = System.currentTimeMillis() + 100)
        repository.addCrash(duplicateCrash)

        val allCrashes = repository.getAllCrashes().first()
        assertEquals(1, allCrashes.size)
    }

    @Test
    fun testANRDetection_notificationAndEventLogging() = runBlocking {
        val project = Project(id = "proj-200", name = "Media Player App", description = "ANR Test", ownerId = "usr-dev")
        repository.addProject(project)

        val anrCrash = CrashReport(
            id = "anr-1",
            projectId = "proj-200",
            applicationId = "app-media",
            exceptionType = "ApplicationNotResponding",
            message = "Application Not Responding: Input dispatching timed out",
            stackTrace = "android.os.ANRError: Input dispatching timed out\n\tat android.os.MessageQueue.nativePollOnce(Native Method)",
            appVersion = "2.0.0",
            source = IntegrationType.WEBHOOK,
            severity = Severity.HIGH,
            timestamp = System.currentTimeMillis()
        )

        repository.addCrash(anrCrash)

        val notifications = repository.getNotifications().first()
        assertTrue(notifications.any { it.type == NotificationType.ANR_ALERT })
    }

    @Test
    fun testFixVerification_statusProgressionAndNotification() = runBlocking {
        val project = Project(id = "proj-300", name = "Banking App", description = "Fix Verification", ownerId = "usr-dev")
        repository.addProject(project)

        val crash = CrashReport(
            id = "crash-bank-1",
            projectId = "proj-300",
            applicationId = "app-bank",
            exceptionType = "IllegalArgumentException",
            message = "Account balance cannot be negative",
            stackTrace = "java.lang.IllegalArgumentException at AccountService.kt:15",
            appVersion = "1.1.0",
            source = IntegrationType.WEBHOOK,
            severity = Severity.HIGH,
            timestamp = System.currentTimeMillis()
        )
        repository.addCrash(crash)

        val fixSuggestion = FixSuggestion(
            id = "fix-301",
            crashId = "crash-bank-1",
            explanation = "Validate balance before deduction",
            filePath = "app/src/main/java/com/bank/AccountService.kt",
            originalCode = "balance -= amount",
            suggestedCode = "require(balance >= amount) { \"Insufficient balance\" }\nbalance -= amount",
            status = FixStatus.PROPOSED
        )
        db.crashDao().insertFixSuggestion(fixSuggestion)

        // Verify fix directly
        val verificationResult = repository.verifyFix(
            crashId = "crash-bank-1",
            fixSuggestionId = "fix-301",
            newVersion = "1.1.1",
            buildId = "build-111",
            scenarioName = "Negative withdrawal test",
            simulatedOutcome = VerificationStatus.FIX_VERIFIED_IN_TEST
        )

        assertTrue(verificationResult.isSuccess)
        val verification = verificationResult.getOrThrow()
        assertEquals(VerificationStatus.FIX_VERIFIED_IN_TEST, verification.status)
        assertEquals("fix-301", verification.fixSuggestionId)

        // Verify Notification was dispatched
        val notifications = repository.getNotifications().first()
        assertTrue(notifications.any { it.type == NotificationType.VERIFICATION_PASSED })
    }


    @Test
    fun testNotificationTrayManagement() = runBlocking {
        val notif1 = SystemNotification(
            id = "notif-1",
            type = NotificationType.SECURITY_ALERT,
            title = "Security Alert",
            message = "API token rotated",
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
        val notif2 = SystemNotification(
            id = "notif-2",
            type = NotificationType.FIX_READY,
            title = "Fix Ready",
            message = "AI Patch suggested for Crash #42",
            timestamp = System.currentTimeMillis(),
            isRead = false
        )

        repository.addNotification(notif1)
        repository.addNotification(notif2)

        var unreadCount = repository.getUnreadNotificationCount().first()
        assertEquals(2, unreadCount)

        repository.markNotificationAsRead("notif-1")
        unreadCount = repository.getUnreadNotificationCount().first()
        assertEquals(1, unreadCount)

        repository.clearNotifications()
        val allNotifs = repository.getNotifications().first()
        assertTrue(allNotifs.isEmpty())
    }
}
