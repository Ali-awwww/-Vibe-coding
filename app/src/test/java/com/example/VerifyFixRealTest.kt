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
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AuthRepository
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
@Config
class VerifyFixRealTest {

    private lateinit var db: AppDatabase
    private var logcatLinesToReturn: List<String>? = null

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createRepository(logcatReader: ((Set<String>) -> List<String>)? = null): CrashRepositoryImpl {
        val stubGitHub = object : GitHubRepository {
            override suspend fun connectGitHub(userId: String, code: String) = Result.success(Unit)
            override suspend fun connectGitHubWithToken(userId: String, token: String) = Result.success(Unit)
            override suspend fun disconnectGitHub(userId: String) = Result.success(Unit)
            override suspend fun getGitHubConnection(userId: String): Result<GitHubConnection?> = Result.success(null)
            override suspend fun fetchRepositories(userId: String) = Result.success(emptyList<GitHubRepo>())
            override suspend fun fetchBranches(userId: String, owner: String, repo: String) = Result.success(emptyList<GitHubBranch>())
            override suspend fun linkRepositoryToApp(mapping: RepositoryMapping) = Result.success(Unit)
            override fun getMappingsForProject(projectId: String) = flowOf(emptyList<RepositoryMapping>())
            override suspend fun getMappingForApp(applicationId: String): RepositoryMapping? = null
            override suspend fun fetchFileContent(userId: String, owner: String, repo: String, path: String, ref: String?) = Result.success("")
            override suspend fun searchCode(userId: String, owner: String, repo: String, query: String, ref: String?) = Result.success(emptyList<String>())
            override suspend fun createBranch(userId: String, owner: String, repo: String, branchName: String, fromRef: String) = Result.success("sha")
            override suspend fun commitChanges(userId: String, owner: String, repo: String, branchName: String, path: String, content: String, message: String) = Result.success(Unit)
            override suspend fun createPullRequest(userId: String, owner: String, repo: String, title: String, head: String, base: String, body: String) = Result.success("")
        }
        val stubAi = object : AIModelRepository {
            override fun getProviders(userId: String) = flowOf(emptyList<AIProvider>())
            override suspend fun getProviderById(userId: String, id: String): AIProvider? = null
            override suspend fun addProvider(provider: AIProvider) {}
            override suspend fun deleteProvider(userId: String, id: String) {}
            override suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long) {}
            override fun getModelsByProvider(userId: String, providerId: String) = flowOf(emptyList<AIModel>())
            override fun getAllAvailableModels(userId: String, freeOnly: Boolean) = flowOf(emptyList<AIModel>())
            override suspend fun getModelById(userId: String, id: String): AIModel? = null
            override suspend fun syncModels(userId: String, providerId: String) = Result.success(Unit)
            override suspend fun testConnection(userId: String, providerId: String) = Result.success(Unit)
            override fun getRecentUsageLogs(userId: String) = flowOf(emptyList<AIUsageLog>())
            override fun getTotalCost(userId: String, since: Long) = flowOf(0.0)
            override suspend fun logUsage(log: AIUsageLog) {}
            override fun getUserSettings(userId: String) = flowOf(null)
            override suspend fun updateUserSettings(settings: AIUserSettings) {}
            override suspend fun setDefaultModel(userId: String, modelId: String) {}
            override suspend fun getDefaultModel(userId: String): AIModel? = null
            override suspend fun generateResponse(userId: String, modelId: String, prompt: String, systemInstruction: String?, taskId: String?, taskType: AITaskType) = Result.success("{}")
        }
        val authRepo = object : AuthRepository {
            override val currentUser: Flow<UserAccount?> = flowOf(UserAccount(id = "user-1"))
            override fun getCurrentUserSync(): UserAccount = UserAccount(id = "user-1")
            override suspend fun signUp(email: String, password: String, displayName: String, organization: String): Result<UserAccount> = Result.failure(UnsupportedOperationException())
            override suspend fun signIn(email: String, password: String): Result<UserAccount> = Result.failure(UnsupportedOperationException())
            override suspend fun signOut() {}
            override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> = Result.failure(UnsupportedOperationException())
            override suspend fun signInWithGoogle(idToken: String): Result<Unit> = Result.failure(UnsupportedOperationException())
        }
        return CrashRepositoryImpl(
            firestore = null,
            crashDao = db.crashDao(),
            sentryApi = object : SentryApi {
                override suspend fun getIssues(authToken: String, orgSlug: String, projectSlug: String, query: String?) = emptyList<com.example.data.remote.SentryIssue>()
                override suspend fun getIssueEvents(authToken: String, issueId: String) = emptyList<com.example.data.remote.SentryEvent>()
            },
            bugsnagApi = object : BugsnagApi {
                override suspend fun getErrors(authToken: String, projectId: String, perPage: Int) = emptyList<com.example.data.remote.BugsnagError>()
                override suspend fun getErrorEvents(authToken: String, errorId: String, perPage: Int) = emptyList<com.example.data.remote.BugsnagEvent>()
            },
            githubRepository = stubGitHub,
            symbolicationRepository = SymbolicationRepositoryImpl(db.crashDao()),
            aiModelRepository = stubAi,
            auth = null,
            authRepository = authRepo,
            currentUserIdProvider = { "user-1" },
            vibeStudioDao = db.vibeStudioDao(),
            liveLogcatLineReader = logcatReader
        )
    }

    private suspend fun seedBaseCrash(repo: CrashRepositoryImpl): CrashReport {
        repo.addProject(Project(id = "proj-vf", name = "Shop App", ownerId = "user-1"))
        repo.addApplication(
            Application(id = "app-vf", projectId = "proj-vf", name = "Shop", packageName = "com.example.shop")
        )
        val crash = CrashReport(
            id = "crash-orig",
            projectId = "proj-vf",
            applicationId = "app-vf",
            exceptionType = "java.lang.NullPointerException",
            message = "Null user cart",
            stackTrace = "java.lang.NullPointerException: Null user cart\n\tat com.example.shop.Cart.checkout(Cart.kt:21)",
            appVersion = "1.0.5",
            source = IntegrationType.WEBHOOK
        )
        repo.addCrash(crash)
        return crash
    }

    @Test
    fun verifyFixIgnoresSimulatedOutcomeAndReturnsInconclusiveWithoutRealEvidence() = runBlocking {
        val repo = createRepository(logcatReader = null)
        seedBaseCrash(repo)

        val result = repo.verifyFix(
            crashId = "crash-orig",
            fixSuggestionId = null,
            newVersion = "1.0.6",
            buildId = "b-6",
            scenarioName = "Checkout flow",
            simulatedOutcome = VerificationStatus.FIX_VERIFIED_IN_TEST
        )

        assertTrue(result.isSuccess)
        val record = result.getOrThrow()
        assertEquals(VerificationStatus.INCONCLUSIVE, record.status)
        assertFalse(record.environmentValid)
        assertFalse(repo.getCrashDetails("crash-orig").first()!!.isResolved)
        assertFalse(repo.getNotifications().first().any { it.type == NotificationType.VERIFICATION_PASSED })
    }

    @Test
    fun verifyFixDetectsCrashReproducedFromNewVersionTelemetry() = runBlocking {
        val repo = createRepository(logcatReader = null)
        seedBaseCrash(repo)

        repo.addCrash(
            CrashReport(
                id = "crash-new-same",
                projectId = "proj-vf",
                applicationId = "app-vf",
                exceptionType = "NullPointerException",
                message = "Still crashing in 1.0.6",
                stackTrace = "java.lang.NullPointerException at Cart.kt:25",
                appVersion = "1.0.6",
                source = IntegrationType.ON_DEVICE_WATCHER,
                timestamp = System.currentTimeMillis() + 6000
            )
        )

        val result = repo.verifyFix("crash-orig", null, "1.0.6", "b-6", "Checkout flow")
        assertTrue(result.isSuccess)
        val record = result.getOrThrow()
        assertEquals(VerificationStatus.CRASH_REPRODUCED, record.status)
        assertTrue(record.environmentValid)
        assertTrue(record.newStackTrace?.contains("Cart.kt:25") == true)
        assertTrue(repo.getNotifications().first().any { it.type == NotificationType.VERIFICATION_FAILED })
    }

    @Test
    fun verifyFixDetectsDifferentCrashFromLiveLogcat() = runBlocking {
        val logLines = listOf(
            "11-27 10:00:00.000  2000  2000 E AndroidRuntime: FATAL EXCEPTION: main",
            "11-27 10:00:00.001  2000  2000 E AndroidRuntime: Process: com.example.shop, PID: 2000",
            "11-27 10:00:00.002  2000  2000 E AndroidRuntime: java.lang.IllegalStateException: State invalid",
            "11-27 10:00:00.003  2000  2000 E AndroidRuntime: \tat com.example.shop.Cart.open(Cart.kt:12)"
        )
        val repo = createRepository(logcatReader = { logLines })
        seedBaseCrash(repo)

        val result = repo.verifyFix("crash-orig", null, "1.0.6", "b-6", "Logcat check")
        assertTrue(result.isSuccess)
        val record = result.getOrThrow()
        assertEquals(VerificationStatus.DIFFERENT_CRASH_DETECTED, record.status)
        assertTrue(record.newStackTrace?.contains("IllegalStateException") == true)
    }

    @Test
    fun verifyFixDetectsTestFailedOnFailedSession() = runBlocking {
        val repo = createRepository(logcatReader = null)
        seedBaseCrash(repo)
        db.crashDao().insertTestSession(
            TestSession(
                id = "sess-fail",
                projectId = "proj-vf",
                applicationId = "app-vf",
                buildId = "b-6",
                version = "1.0.6",
                status = TestSessionStatus.FAILED
            )
        )
        db.crashDao().insertTestEvent(
            TestEvent(
                id = "ev-err",
                sessionId = "sess-fail",
                type = TestEventType.NON_FATAL_ERROR,
                details = "Assertion failed in Cart test"
            )
        )

        val result = repo.verifyFix("crash-orig", null, "1.0.6", "b-6", "Cart regression")
        assertTrue(result.isSuccess)
        val record = result.getOrThrow()
        assertEquals(VerificationStatus.TEST_FAILED, record.status)
        assertEquals("sess-fail", record.testSessionId)
    }

    @Test
    fun verifyFixPassesAndResolvesCrashWithCleanCompletedSession() = runBlocking {
        val repo = createRepository(logcatReader = { listOf("11-27 10:01:00.000 100 100 I com.example.shop: ok") })
        seedBaseCrash(repo)
        db.crashDao().insertTestSession(
            TestSession(
                id = "sess-pass",
                projectId = "proj-vf",
                applicationId = "app-vf",
                buildId = "b-6",
                version = "1.0.6",
                status = TestSessionStatus.COMPLETED
            )
        )
        db.crashDao().insertTestEvent(
            TestEvent(
                id = "ev-ok",
                sessionId = "sess-pass",
                type = TestEventType.APP_START,
                details = "App started cleanly"
            )
        )

        val result = repo.verifyFix("crash-orig", null, "1.0.6", "b-6", "Checkout pass")
        assertTrue(result.isSuccess)
        val record = result.getOrThrow()
        assertEquals(VerificationStatus.FIX_VERIFIED_IN_TEST, record.status)
        assertTrue(record.environmentValid)
        assertTrue(repo.getCrashDetails("crash-orig").first()!!.isResolved)
        assertTrue(repo.getNotifications().first().any { it.type == NotificationType.VERIFICATION_PASSED })
    }

    @Test
    fun addVerificationRecordAcceptsRealEvidenceAndRejectsBlankRecords() = runBlocking {
        val repo = createRepository(logcatReader = null)
        seedBaseCrash(repo)

        val valid = CrashVerificationRecord(
            id = "ver-real",
            projectId = "proj-vf",
            crashId = "crash-orig",
            newAppVersion = "1.0.6",
            testSessionId = "sess-123",
            comparisonSummary = "Verified clean in session sess-123",
            status = VerificationStatus.FIX_VERIFIED_IN_TEST
        )
        repo.addVerificationRecord(valid)
        assertEquals(1, repo.getVerificationsByCrash("crash-orig").first().size)

        val forged = CrashVerificationRecord(
            id = "ver-forged",
            projectId = "proj-vf",
            crashId = "crash-orig",
            status = VerificationStatus.FIX_VERIFIED_IN_TEST
        )
        val rejected = runCatching { repo.addVerificationRecord(forged) }
        assertTrue(rejected.isFailure)
        assertTrue(rejected.exceptionOrNull() is IllegalArgumentException)
    }
}
