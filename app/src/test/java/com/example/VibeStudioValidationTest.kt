package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.VibeStudioRepositoryImpl
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.VibeStudioRepository
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
class VibeStudioValidationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var vibeStudioRepository: VibeStudioRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext<Context>()
        
        // Initialize Firebase
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            com.google.firebase.FirebaseApp.initializeApp(context)
        }

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        val fakeAiModelRepo = object : AIModelRepository {
            override fun getProviders(userId: String) = kotlinx.coroutines.flow.flowOf(emptyList<AIProvider>())
            override suspend fun getProviderById(userId: String, id: String) = null
            override suspend fun addProvider(provider: AIProvider) {}
            override suspend fun deleteProvider(userId: String, id: String) {}
            override suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long) {}
            override fun getModelsByProvider(userId: String, providerId: String) = kotlinx.coroutines.flow.flowOf(emptyList<AIModel>())
            override fun getAllAvailableModels(userId: String, freeOnly: Boolean) = kotlinx.coroutines.flow.flowOf(emptyList<AIModel>())
            override suspend fun getModelById(userId: String, id: String) = null
            override suspend fun syncModels(userId: String, providerId: String) = Result.success(Unit)
            override suspend fun testConnection(userId: String, providerId: String) = Result.success(Unit)
            override fun getRecentUsageLogs(userId: String) = kotlinx.coroutines.flow.flowOf(emptyList<AIUsageLog>())
            override fun getTotalCost(userId: String, since: Long) = kotlinx.coroutines.flow.flowOf(0.0)
            override suspend fun logUsage(log: AIUsageLog) {}
            override fun getUserSettings(userId: String) = kotlinx.coroutines.flow.flowOf(null)
            override suspend fun updateUserSettings(settings: AIUserSettings) {}
            override suspend fun setDefaultModel(userId: String, modelId: String) {}
            override suspend fun getDefaultModel(userId: String) = null
            override suspend fun generateResponse(userId: String, modelId: String, prompt: String, systemInstruction: String?, taskId: String?, taskType: AITaskType): Result<String> {
                // Mock response for project generation
                return if (prompt.contains("JSON SCHEMA")) {
                    Result.success("""
                        {
                          "files": [
                            {"path": "README.md", "content": "# Test Project", "isFolder": false},
                            {"path": "src", "content": "", "isFolder": true}
                          ],
                          "plan": "Test plan",
                          "techDecisions": "Test tech"
                        }
                    """.trimIndent())
                } else {
                    Result.success("Mock AI Response")
                }
            }
        }

        vibeStudioRepository = VibeStudioRepositoryImpl(
            vibeStudioDao = database.vibeStudioDao(),
            aiModelRepository = fakeAiModelRepo,
            crashDao = database.crashDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `test workspace lifecycle and file CRUD`() = runTest(testDispatcher) {
        val userId = "user_vibe"
        val workspaceId = UUID.randomUUID().toString()
        val workspace = Workspace(
            id = workspaceId,
            ownerId = userId,
            name = "Test Vibe Project",
            techStack = "React + Tailwind"
        )
        
        // 1. Create Workspace
        val createdId = vibeStudioRepository.createWorkspace(workspace)
        assertEquals(workspaceId, createdId)
        
        // 2. Add File
        val fileId = UUID.randomUUID().toString()
        val file = WorkspaceFile(
            id = fileId,
            workspaceId = workspaceId,
            name = "App.js",
            path = "src/App.js",
            content = "console.log('hello');"
        )
        vibeStudioRepository.saveFile(file)
        
        // 3. Verify File
        val savedFile = vibeStudioRepository.getFileById(fileId)
        assertNotNull(savedFile)
        assertEquals("src/App.js", savedFile?.path)
        
        // 4. Search File
        val searchResults = vibeStudioRepository.searchFiles(workspaceId, "App")
        assertTrue(searchResults.any { it.id == fileId })
        
        // 5. Delete File
        vibeStudioRepository.deleteFile(fileId)
        assertNull(vibeStudioRepository.getFileById(fileId))
    }

    @Test
    fun `test autonomous project generation`() = runTest(testDispatcher) {
        val userId = "user_vibe"
        val workspaceId = "ws_gen_1"
        vibeStudioRepository.createWorkspace(Workspace(id = workspaceId, ownerId = userId, name = "Auto Gen"))
        
        // Trigger generation
        val result = vibeStudioRepository.generateProject(userId, "Create a simple landing page", workspaceId)
        assertTrue(result.isSuccess)
        
        // Verify files were created
        val files = vibeStudioRepository.getFiles(workspaceId).first()
        assertTrue(files.size >= 2)
        assertTrue(files.any { it.path == "README.md" })
        assertTrue(files.any { it.path == "src" && it.isFolder })
        
        // Verify checkpoint
        val checkpoints = vibeStudioRepository.getCheckpoints(workspaceId).first()
        assertEquals(1, checkpoints.size)
    }

    @Test
    fun `test checkpoint and rollback system`() = runTest(testDispatcher) {
        val workspaceId = "ws_rollback"
        vibeStudioRepository.createWorkspace(Workspace(id = workspaceId, ownerId = "user_1"))
        
        // 1. Initial State
        vibeStudioRepository.saveFile(WorkspaceFile(id = "f1", workspaceId = workspaceId, name = "v1.txt", path = "v1.txt", content = "v1"))
        val cp1 = vibeStudioRepository.createCheckpoint(workspaceId, "CP1", "Initial")
        
        // 2. Modify State
        vibeStudioRepository.saveFile(WorkspaceFile(id = "f1", workspaceId = workspaceId, name = "v1.txt", path = "v1.txt", content = "v2"))
        
        // 3. Rollback
        val rollbackResult = vibeStudioRepository.revertToCheckpoint(cp1)
        assertTrue(rollbackResult.isSuccess)
        
        // 4. Verify original content restored
        val restoredFile = vibeStudioRepository.getFileById("f1")
        assertEquals("v1", restoredFile?.content)
    }

    @Test
    fun simulatedCrashKeepsInternalQaProvenanceInTheLocalDatabase() = runTest(testDispatcher) {
        val workspaceId = "ws-simulated-crash"
        vibeStudioRepository.createWorkspace(
            Workspace(id = workspaceId, ownerId = "user-1", name = "Sandbox")
        )

        val result = vibeStudioRepository.recordSimulatedCrash(
            workspaceId = workspaceId,
            exceptionType = "IllegalStateException",
            message = "Simulated failure",
            stackTrace = "at Sandbox.run(Sandbox.kt:1)"
        )

        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(IntegrationType.INTERNAL_QA, report.source)
        assertEquals("[SIMULATED] Simulated failure", report.message)

        val persisted = database.crashDao().getCrashDetails(report.id).first()
        assertNotNull(persisted)
        assertEquals(IntegrationType.INTERNAL_QA, persisted?.source)
        assertEquals(workspaceId, persisted?.projectId)
        assertEquals(workspaceId, persisted?.applicationId)
    }

    @Test
    fun devServerDoesNotReportRunningWithoutAnExecutionBackend() = runTest(testDispatcher) {
        val workspaceId = "ws-runtime-unavailable"
        vibeStudioRepository.createWorkspace(
            Workspace(id = workspaceId, ownerId = "user-1", name = "Runtime test")
        )

        val result = vibeStudioRepository.startDevServer(workspaceId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("مشغل عمليات معزول") == true)
        assertNull(vibeStudioRepository.getSandboxRuntimeState(workspaceId).first())
    }

    @Test
    fun localAgentDoesNotReportConnectedWithoutAnAuthenticatedTransport() = runTest(testDispatcher) {
        val workspaceId = "ws-agent-unavailable"
        vibeStudioRepository.createWorkspace(
            Workspace(id = workspaceId, ownerId = "user-1", name = "Agent test")
        )

        val result = vibeStudioRepository.connectLocalAgent(
            workspaceId = workspaceId,
            endpoint = "http://localhost:8974",
            token = "test-token"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("بروتوكول النقل") == true)
        assertNull(vibeStudioRepository.getSandboxRuntimeState(workspaceId).first())
    }

}

