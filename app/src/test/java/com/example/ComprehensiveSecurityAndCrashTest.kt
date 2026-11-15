package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.remote.BugsnagApi
import com.example.data.remote.SentryApi
import com.example.data.repository.CrashRepositoryImpl
import com.example.domain.model.*
import com.example.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
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
@OptIn(ExperimentalCoroutinesApi::class)
class ComprehensiveSecurityAndCrashTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var crashRepository: CrashRepository
    private lateinit var context: Context
    private var activeUserId: String? = null

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext<Context>()
        activeUserId = null
        
        // Initialize Firebase for repository dependencies
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            com.google.firebase.FirebaseApp.initializeApp(context)
        }

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        val fakeSentryApi = object : SentryApi {
            override suspend fun getIssues(authToken: String, orgSlug: String, projectSlug: String, query: String?) = emptyList<com.example.data.remote.SentryIssue>()
            override suspend fun getIssueEvents(authToken: String, issueId: String) = emptyList<com.example.data.remote.SentryEvent>()
        }
        val fakeBugsnagApi = object : BugsnagApi {
            override suspend fun getErrors(authToken: String, projectId: String, perPage: Int) = emptyList<com.example.data.remote.BugsnagError>()
            override suspend fun getErrorEvents(authToken: String, errorId: String, perPage: Int) = emptyList<com.example.data.remote.BugsnagEvent>()
        }
        val fakeGithubRepo = object : GitHubRepository {
            override suspend fun connectGitHub(userId: String, code: String) = Result.success(Unit)
            override suspend fun connectGitHubWithToken(userId: String, token: String) = Result.success(Unit)
            override suspend fun disconnectGitHub(userId: String) = Result.success(Unit)
            override suspend fun getGitHubConnection(userId: String) = Result.success(null)
            override suspend fun fetchRepositories(userId: String) = Result.success(emptyList<GitHubRepo>())
            override suspend fun fetchBranches(userId: String, owner: String, repo: String) = Result.success(emptyList<GitHubBranch>())
            override suspend fun linkRepositoryToApp(mapping: RepositoryMapping) = Result.success(Unit)
            override fun getMappingsForProject(projectId: String) = kotlinx.coroutines.flow.flowOf(emptyList<RepositoryMapping>())
            override suspend fun getMappingForApp(applicationId: String) = null
            override suspend fun fetchFileContent(userId: String, owner: String, repo: String, path: String, ref: String?) = Result.success("")
            override suspend fun searchCode(userId: String, owner: String, repo: String, query: String, ref: String?) = Result.success(emptyList<String>())
            override suspend fun createBranch(userId: String, owner: String, repo: String, branchName: String, fromRef: String) = Result.success("sha")
            override suspend fun commitChanges(userId: String, owner: String, repo: String, branchName: String, path: String, content: String, message: String) = Result.success(Unit)
            override suspend fun createPullRequest(userId: String, owner: String, repo: String, title: String, head: String, base: String, body: String) = Result.success("https://github.com/test/pr/1")
        }
        val fakeSymbolicationRepo = object : SymbolicationRepository {
            override fun getDebugSymbols(projectId: String) = kotlinx.coroutines.flow.flowOf(emptyList<DebugSymbolFile>())
            override suspend fun uploadDebugSymbol(file: DebugSymbolFile) = Result.success(Unit)
            override suspend fun deleteDebugSymbol(id: String) = Result.success(Unit)
            override suspend fun symbolicate(crashReport: CrashReport) = Result.success(
                SymbolicationReport(SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED, emptyList())
            )
        }
        val fakeAiModelRepo = object : AIModelRepository {
            override fun getProviders(userId: String) = kotlinx.coroutines.flow.flowOf(emptyList<AIProvider>())
            override suspend fun getProviderById(userId: String, id: String) = null
            override suspend fun addProvider(provider: AIProvider) {}
            override suspend fun deleteProvider(userId: String, id: String) {}
            override suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long) {}
            override fun getModelsByProvider(providerId: String) = kotlinx.coroutines.flow.flowOf(emptyList<AIModel>())
            override fun getAllAvailableModels(freeOnly: Boolean) = kotlinx.coroutines.flow.flowOf(emptyList<AIModel>())
            override suspend fun getModelById(id: String) = null
            override suspend fun syncModels(userId: String, providerId: String) = Result.success(Unit)
            override suspend fun testConnection(userId: String, providerId: String) = Result.success(Unit)
            override fun getRecentUsageLogs(userId: String) = kotlinx.coroutines.flow.flowOf(emptyList<AIUsageLog>())
            override fun getTotalCost(userId: String, since: Long) = kotlinx.coroutines.flow.flowOf(0.0)
            override suspend fun logUsage(log: AIUsageLog) {}
            override fun getUserSettings(userId: String) = kotlinx.coroutines.flow.flowOf(null)
            override suspend fun updateUserSettings(settings: AIUserSettings) {}
            override suspend fun setDefaultModel(userId: String, modelId: String) {}
            override suspend fun getDefaultModel(userId: String) = null
            override suspend fun generateResponse(userId: String, modelId: String, prompt: String, systemInstruction: String?, taskId: String?, taskType: AITaskType) = Result.success("")
        }

        val fakeAuthRepository = object : AuthRepository {
            override val currentUser: kotlinx.coroutines.flow.Flow<UserAccount?> =
                kotlinx.coroutines.flow.flow { emit(activeUserId?.let { UserAccount(id = it) }) }

            override fun getCurrentUserSync(): UserAccount? =
                activeUserId?.let { UserAccount(id = it) }

            override suspend fun signUp(
                email: String,
                password: String,
                displayName: String,
                organization: String
            ): Result<UserAccount> = Result.failure(UnsupportedOperationException())

            override suspend fun signIn(email: String, password: String): Result<UserAccount> =
                Result.failure(UnsupportedOperationException())

            override suspend fun signOut() {
                activeUserId = null
            }

            override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> =
                Result.failure(UnsupportedOperationException())

            override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
                Result.failure(UnsupportedOperationException())
        }

        // Use local Room storage with a mutable Supabase identity; do not bypass the project identity guard.
        crashRepository = CrashRepositoryImpl(
            firestore = null,
            crashDao = database.crashDao(),
            sentryApi = fakeSentryApi,
            bugsnagApi = fakeBugsnagApi,
            githubRepository = fakeGithubRepo,
            symbolicationRepository = fakeSymbolicationRepo,
            aiModelRepository = fakeAiModelRepo,
            auth = null,
            authRepository = fakeAuthRepository
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `test RBAC AccessControl rules logic`() {
        // Owner checks
        assertTrue(AccessControl.canDeleteProject(Role.OWNER))
        assertTrue(AccessControl.canManageProject(Role.OWNER))
        assertTrue(AccessControl.canManageMembers(Role.OWNER))
        
        // Admin checks
        assertFalse(AccessControl.canDeleteProject(Role.ADMIN))
        assertTrue(AccessControl.canManageProject(Role.ADMIN))
        assertTrue(AccessControl.canPurgeData(Role.ADMIN))
        
        // Developer checks
        assertFalse(AccessControl.canManageMembers(Role.DEVELOPER))
        assertTrue(AccessControl.canResolveCrashes(Role.DEVELOPER))
        
        // Viewer checks
        assertFalse(AccessControl.canResolveCrashes(Role.VIEWER))
        assertFalse(AccessControl.canManageIntegrations(Role.VIEWER))
    }

    @Test
    fun `test multi-tenant isolation - user cannot see other user projects`() = runTest(testDispatcher) {
        val userA = "user_a"
        val userB = "user_b"
        
        val projA = Project(id = "p_a", name = "Project A", ownerId = userA)
        val projB = Project(id = "p_b", name = "Project B", ownerId = userB)

        activeUserId = userA
        crashRepository.addProject(projA)
        activeUserId = userB
        crashRepository.addProject(projB)

        activeUserId = userA
        val projectsForA = crashRepository.getProjectsForUser(userA).first()
        assertEquals(1, projectsForA.size)
        assertEquals("p_a", projectsForA[0].id)

        activeUserId = userB
        val projectsForB = crashRepository.getProjectsForUser(userB).first()
        assertEquals(1, projectsForB.size)
        assertEquals("p_b", projectsForB[0].id)
    }

    @Test
    fun `test crash lifecycle from creation to resolution`() = runTest(testDispatcher) {
        val userId = "dev_1"
        val projectId = "proj_123"
        val appId = "app_android"
        activeUserId = userId

        // 1. Setup Project & App
        crashRepository.addProject(Project(id = projectId, name = "Test Proj", ownerId = userId))
        crashRepository.addApplication(Application(id = appId, projectId = projectId, name = "Android App"))
        
        // 2. Ingest Crash
        val crashId = UUID.randomUUID().toString()
        val report = CrashReport(
            id = crashId,
            projectId = projectId,
            applicationId = appId,
            exceptionType = "NullPointerException",
            message = "Attempt to invoke virtual method on null object reference",
            stackTrace = "at com.example.MainActivity.onCreate(MainActivity.kt:25)",
            severity = Severity.CRITICAL
        )
        crashRepository.addCrash(report)
        
        // 3. Verify Reception
        val crash = crashRepository.getCrashDetails(crashId).first()
        assertNotNull(crash)
        assertEquals("NullPointerException", crash?.exceptionType)
        
        // 4. Resolve Crash
        crashRepository.updateCrashStatus(crashId, true)
        val resolvedCrash = crashRepository.getCrashDetails(crashId).first()
        assertTrue(resolvedCrash?.isResolved == true)
        
        // 5. Verify in resolved filter
        val allCrashes = crashRepository.getLatestCrashes(projectId).first()
        assertTrue(allCrashes.any { it.id == crashId && it.isResolved })
    }

    @Test
    fun `test AES-GCM credential masking and encryption logic`() = runTest(testDispatcher) {
        val rawConfig = "{\"apiKey\": \"secret-12345-key\"}"
        val encrypted = com.example.util.SecurityUtils.encryptSecret(rawConfig)
        
        assertNotEquals(rawConfig, encrypted)
        assertTrue(encrypted.length > rawConfig.length)
        
        val decrypted = com.example.util.SecurityUtils.decryptSecret(encrypted)
        assertEquals(rawConfig, decrypted)
        
        val masked = com.example.util.SecurityUtils.maskSecret(rawConfig)
        assertTrue(masked.contains("••••••••") || masked.contains("********"))
        assertFalse(masked.contains("secret-12345"))
    }
}
