package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CrashDao
import com.example.data.local.VibeStudioDao
import com.example.data.repository.VibeStudioRepositoryImpl
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.util.UnifiedDevDebugEngine
import com.example.util.VibeQualityTestingEngine
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
class UnifiedClosedLoopTest {

    private lateinit var database: AppDatabase
    private lateinit var vibeStudioDao: VibeStudioDao
    private lateinit var crashDao: CrashDao
    private lateinit var repository: VibeStudioRepositoryImpl
    private val testWorkspaceId = "test-workspace-closed-loop-99"

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
        ) = Result.success("{}")
    }

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vibeStudioDao = database.vibeStudioDao()
        crashDao = database.crashDao()

        repository = VibeStudioRepositoryImpl(
            vibeStudioDao = vibeStudioDao,
            aiModelRepository = fakeAIModelRepository,
            crashRepository = null,
            gitHubRepository = null,
            crashDao = crashDao
        )

        // Seed test workspace
        vibeStudioDao.insertWorkspace(
            Workspace(
                id = testWorkspaceId,
                name = "E-Commerce Mobile",
                description = "Production Android Kotlin app with integrated Crash Intelligence",
                techStack = "Android (Kotlin)",
                type = WorkspaceType.ANDROID,
                ownerId = "usr_tester_1"
            )
        )

        // Seed sample Kotlin file with potential null crash
        vibeStudioDao.insertFile(
            WorkspaceFile(
                id = "file-main-activity-1",
                workspaceId = testWorkspaceId,
                name = "MainActivity.kt",
                path = "app/src/main/java/com/example/MainActivity.kt",
                content = """
                    package com.example
                    
                    class MainActivity {
                        fun onCreate() {
                            val user = findUserOrNull("123")
                            println(user.name.uppercase())
                        }
                        
                        private fun findUserOrNull(id: String): User? = null
                    }
                """.trimIndent(),
                extension = "kt",
                isFolder = false
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testWorkspaceMonitoringProfileGeneration() = runTest {
        val profileResult = repository.setupWorkspaceMonitoring(testWorkspaceId)
        assertTrue(profileResult.isSuccess)
        val profile = profileResult.getOrThrow()

        assertEquals(testWorkspaceId, profile.workspaceId)
        assertEquals(testWorkspaceId, profile.projectId)
        assertTrue(profile.isSdkConfigured)
        assertTrue(profile.sdkSnippet.contains("CrashIntelligence.init"))

        // Check project & application created in database
        val project = crashDao.getProjectById(testWorkspaceId)
        assertNotNull(project)
        assertEquals("E-Commerce Mobile", project?.name)

        val app = crashDao.getApplicationById(testWorkspaceId)
        assertNotNull(app)
        assertEquals(SdkSetupStatus.CONFIGURED, app?.sdkSetupStatus)
    }

    @Test
    fun testStartTrackedTestSessionWithBuildIdAndCommit() = runTest {
        repository.initGitRepository(testWorkspaceId)

        val sessionResult = repository.startTrackedTestSession(testWorkspaceId, "build-101-alpha")
        assertTrue(sessionResult.isSuccess)
        val session = sessionResult.getOrThrow()

        assertEquals("build-101-alpha", session.buildId)
        assertEquals(TestSessionStatus.APP_RUNNING, session.status)
        assertEquals(testWorkspaceId, session.projectId)

        // Check test event was created
        val events = crashDao.getTestEvents(session.id).first()
        assertTrue(events.isNotEmpty())
        assertEquals(TestEventType.APP_START, events.first().type)
    }

    @Test
    fun testCrashIngestionAndSessionLinkage() = runTest {
        val session = repository.startTrackedTestSession(testWorkspaceId, "build-102").getOrThrow()

        val stackTrace = """
            java.lang.NullPointerException: Attempt to invoke virtual method on a null object reference
                at com.example.MainActivity.onCreate(MainActivity.kt:6)
                at com.example.App.start(App.kt:18)
        """.trimIndent()

        val crashResult = repository.ingestSimulatedOrRuntimeCrash(
            workspaceId = testWorkspaceId,
            testSessionId = session.id,
            exceptionType = "java.lang.NullPointerException",
            message = "Attempt to invoke virtual method on a null object reference",
            stackTrace = stackTrace
        )

        assertTrue(crashResult.isSuccess)
        val crash = crashResult.getOrThrow()
        assertFalse(crash.isResolved)
        assertEquals("java.lang.NullPointerException", crash.exceptionType)

        // Verify test session status transitioned to CRASH_DETECTED
        val updatedSession = crashDao.getTestSessionById(session.id).first()
        assertEquals(TestSessionStatus.CRASH_DETECTED, updatedSession?.status)
        assertEquals(crash.id, updatedSession?.crashId)

        // Verify event logged
        val events = crashDao.getTestEvents(session.id).first()
        assertTrue(events.any { it.type == TestEventType.CRASH })
    }

    @Test
    fun testAIDiagnosisAndCulpritSourceMatching() = runTest {
        val stackTrace = """
            java.lang.NullPointerException: Attempt to invoke virtual method on a null object reference
                at com.example.MainActivity.onCreate(app/src/main/java/com/example/MainActivity.kt:6)
        """.trimIndent()

        val crash = repository.ingestSimulatedOrRuntimeCrash(
            workspaceId = testWorkspaceId,
            testSessionId = null,
            exceptionType = "java.lang.NullPointerException",
            message = "null pointer in getUserProfile",
            stackTrace = stackTrace
        ).getOrThrow()

        val diagResult = repository.diagnoseCrashAndGenerateFix(crash.id)
        assertTrue(diagResult.isSuccess)
        val (diagnosis, fix) = diagResult.getOrThrow()

        assertEquals(crash.id, diagnosis.crashId)
        assertTrue(diagnosis.rootCause.contains("فارغ") || diagnosis.rootCause.contains("Null"))
        assertEquals("app/src/main/java/com/example/MainActivity.kt", fix.filePath)
        assertTrue(fix.suggestedCode.contains("?.") || fix.suggestedCode.contains("null"))
    }

    @Test
    fun testApplyClosedLoopFixCreatesCheckpointAndGitCommit() = runTest {
        repository.initGitRepository(testWorkspaceId)

        val stackTrace = "java.lang.NullPointerException at com.example.MainActivity.onCreate(app/src/main/java/com/example/MainActivity.kt:6)"
        val crash = repository.ingestSimulatedOrRuntimeCrash(
            workspaceId = testWorkspaceId,
            testSessionId = null,
            exceptionType = "java.lang.NullPointerException",
            message = "null user",
            stackTrace = stackTrace
        ).getOrThrow()

        val (_, fix) = repository.diagnoseCrashAndGenerateFix(crash.id).getOrThrow()

        // Apply fix to workspace
        val commitResult = repository.applyClosedLoopFix(fix.id)
        assertTrue(commitResult.isSuccess)
        val commit = commitResult.getOrThrow()

        assertEquals(crash.id, commit.associatedCrashId)

        // Verify checkpoint was created
        val checkpoints = vibeStudioDao.getCheckpoints(testWorkspaceId).first()
        assertTrue(checkpoints.isNotEmpty())

        // Verify file in workspace was updated
        val updatedFile = vibeStudioDao.getFileByPath(testWorkspaceId, fix.filePath)
        assertNotNull(updatedFile)
        assertEquals(fix.suggestedCode, updatedFile?.content)

        // Verify fix status updated to APPLIED
        val storedFix = crashDao.getFixSuggestionById(fix.id)
        assertEquals(FixStatus.APPLIED, storedFix?.status)
    }

    @Test
    fun testClosedLoopVerificationStrictAccuracyPolicy() = runTest {
        val stackTrace = "java.lang.NullPointerException at com.example.MainActivity.kt:6"
        val crash = repository.ingestSimulatedOrRuntimeCrash(
            workspaceId = testWorkspaceId,
            testSessionId = null,
            exceptionType = "java.lang.NullPointerException",
            message = "null pointer",
            stackTrace = stackTrace
        ).getOrThrow()

        val (_, proposedFix) = repository.diagnoseCrashAndGenerateFix(crash.id).getOrThrow()
        val appliedFix = proposedFix.copy(status = FixStatus.APPLIED)

        // A complete, real test run is required for a successful verification.
        val successResult = TestExecutionResult(
            totalTests = 5,
            passed = 5,
            failed = 0,
            durationMs = 120,
            isSuccess = true,
            executionVerified = true
        )

        val outcomePass = UnifiedDevDebugEngine.evaluateVerificationOutcome(
            crashReport = crash,
            fixSuggestion = appliedFix,
            previousBuildId = "build-1",
            newBuildId = "build-2",
            testResult = successResult
        )

        assertTrue(outcomePass.isFixed)
        assertEquals(VerificationStatus.FIX_VERIFIED_IN_TEST, outcomePass.status)

        // Test failing verification outcome
        val failResult = TestExecutionResult(
            totalTests = 5,
            passed = 3,
            failed = 2,
            durationMs = 120,
            isSuccess = false,
            executionVerified = true
        )

        val outcomeFail = UnifiedDevDebugEngine.evaluateVerificationOutcome(
            crashReport = crash,
            fixSuggestion = appliedFix,
            previousBuildId = "build-1",
            newBuildId = "build-3",
            testResult = failResult
        )

        assertFalse(outcomeFail.isFixed)
        assertEquals(VerificationStatus.TEST_FAILED, outcomeFail.status)

        val staticAuditOnly = UnifiedDevDebugEngine.evaluateVerificationOutcome(
            crashReport = crash,
            fixSuggestion = appliedFix,
            previousBuildId = "build-1",
            newBuildId = "static-audit-only",
            testResult = TestExecutionResult(
                totalTests = 5,
                passed = 5,
                failed = 0,
                isSuccess = true,
                executionVerified = false
            )
        )
        assertFalse(staticAuditOnly.isFixed)
        assertEquals(VerificationStatus.INCONCLUSIVE, staticAuditOnly.status)
        assertFalse(staticAuditOnly.verificationRecord.environmentValid)

        val emptyExecutedRun = UnifiedDevDebugEngine.evaluateVerificationOutcome(
            crashReport = crash,
            fixSuggestion = appliedFix,
            previousBuildId = "build-1",
            newBuildId = "empty-run",
            testResult = TestExecutionResult(
                totalTests = 0,
                passed = 0,
                failed = 0,
                isSuccess = true,
                executionVerified = true
            )
        )
        assertFalse(emptyExecutedRun.isFixed)
        assertEquals(VerificationStatus.INCONCLUSIVE, emptyExecutedRun.status)
    }

    @Test
    fun testRetestRejectsUnappliedFixBeforeAnyVerificationMutation() = runTest {
        val crash = repository.ingestSimulatedOrRuntimeCrash(
            workspaceId = testWorkspaceId,
            testSessionId = null,
            exceptionType = "IllegalStateException",
            message = "unapplied fix",
            stackTrace = "at com.example.MainActivity.onCreate(MainActivity.kt:1)"
        ).getOrThrow()
        val (_, fix) = repository.diagnoseCrashAndGenerateFix(crash.id).getOrThrow()

        val result = repository.retestAndVerifyClosedLoop(crash.id, fix.id)

        assertTrue(result.isFailure)
        assertTrue(crashDao.getVerificationsByProject(testWorkspaceId).first().isEmpty())
    }

    @Test
    fun testRetestRejectsFixBelongingToAnotherCrash() = runTest {
        repository.initGitRepository(testWorkspaceId)
        val firstCrash = repository.ingestSimulatedOrRuntimeCrash(
            workspaceId = testWorkspaceId,
            testSessionId = null,
            exceptionType = "IllegalArgumentException",
            message = "first crash",
            stackTrace = "at com.example.MainActivity.onCreate(MainActivity.kt:1)"
        ).getOrThrow()
        val secondCrash = repository.ingestSimulatedOrRuntimeCrash(
            workspaceId = testWorkspaceId,
            testSessionId = null,
            exceptionType = "IllegalStateException",
            message = "second crash",
            stackTrace = "at com.example.MainActivity.onCreate(MainActivity.kt:2)"
        ).getOrThrow()
        val (_, firstFix) = repository.diagnoseCrashAndGenerateFix(firstCrash.id).getOrThrow()
        repository.applyClosedLoopFix(firstFix.id).getOrThrow()

        val result = repository.retestAndVerifyClosedLoop(secondCrash.id, firstFix.id)

        assertTrue(result.isFailure)
        assertTrue(crashDao.getVerificationsByProject(testWorkspaceId).first().isEmpty())
    }

    @Test
    fun testStaticAuditDoesNotMarkDiscoveredUnitTestFileAsExecuted() = runTest {
        val workspace = vibeStudioDao.getWorkspaceById(testWorkspaceId)!!
        val testFile = WorkspaceFile(
            id = "sample-unit-test",
            workspaceId = testWorkspaceId,
            name = "SampleFeatureTest.kt",
            path = "src/test/java/SampleFeatureTest.kt",
            content = "class SampleFeatureTest { fun testFeature() { assert(true) } }",
            extension = "kt",
            isFolder = false
        )

        val report = VibeQualityTestingEngine.executeIntelligentAudit(workspace, listOf(testFile))
        val unitTestCheck = report.testResults.first { it.type == TestType.UNIT_TEST }

        assertEquals(TestStatus.SKIPPED, unitTestCheck.status)
        assertTrue(unitTestCheck.name.contains("لم يتم تشغيل"))
    }

    @Test
    fun testWorkspaceBuildAndTestsDoNotClaimRuntimeExecution() = runTest {
        val buildLogs = repository.runWorkspaceBuild(testWorkspaceId).getOrThrow()
        assertTrue(buildLogs.none { it.type == LogType.SUCCESS })
        assertTrue(buildLogs.any { it.message.contains("فحص ساكن") })

        val testResult = repository.runWorkspaceTests(testWorkspaceId).getOrThrow()
        assertFalse(testResult.executionVerified)
        assertFalse(testResult.isSuccess)
        assertEquals(0, testResult.totalTests)
        assertTrue(testResult.staticChecksTotal > 0)
        assertTrue(testResult.outputLogs.any { it.contains("لم يتم تشغيل") })
    }

    @Test
    fun testFullEndToEndDevelopmentAndCrashIntelligenceLoop() = runTest {
        // Step 1: Monitoring profile
        val profile = repository.setupWorkspaceMonitoring(testWorkspaceId).getOrThrow()
        assertTrue(profile.isSdkConfigured)

        // Step 2: Tracked test session
        val session = repository.startTrackedTestSession(testWorkspaceId, "build-init-v1").getOrThrow()
        assertEquals(TestSessionStatus.APP_RUNNING, session.status)

        // Step 3: Real crash occurs and is ingested
        val stackTrace = "java.lang.NullPointerException: null obj at MainActivity.kt:6"
        val crash = repository.ingestSimulatedOrRuntimeCrash(
            workspaceId = testWorkspaceId,
            testSessionId = session.id,
            exceptionType = "NullPointerException",
            message = "null obj in UserService",
            stackTrace = stackTrace
        ).getOrThrow()

        // Step 4: AI Diagnosis and Fix generation
        val (diagnosis, fix) = repository.diagnoseCrashAndGenerateFix(crash.id).getOrThrow()
        assertNotNull(diagnosis)
        assertNotNull(fix)

        // Step 5: Apply patch to workspace
        repository.initGitRepository(testWorkspaceId)
        val commit = repository.applyClosedLoopFix(fix.id).getOrThrow()
        assertNotNull(commit.id)

        // Step 6: Retest and Verify
        val outcome = repository.retestAndVerifyClosedLoop(crash.id, fix.id).getOrThrow()
        assertNotNull(outcome)
        assertFalse(outcome.isFixed)
        assertEquals(VerificationStatus.INCONCLUSIVE, outcome.status)
        assertFalse(outcome.verificationRecord.environmentValid)

        // Step 7: The applied fix is visible, but the incident remains unverified.
        val state = repository.getDevDebugState(testWorkspaceId).getOrThrow()
        assertEquals(1, state.incidents.size)
        assertEquals(IncidentStatus.FIX_APPLIED, state.incidents.first().status)
    }
}
