package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.DeviceAgentInfo
import com.example.data.repository.LiveAgentTransport
import com.example.data.repository.LiveTestingRepositoryImpl
import com.example.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
class LiveTestingRealFlowTest {

    private lateinit var db: AppDatabase
    private lateinit var repoScope: CoroutineScope

    private class ConfigurableTransport(
        override val isTransportAvailable: Boolean = true,
        var readyResult: Result<DeviceAgentInfo> = Result.success(
            DeviceAgentInfo("pixel-9", "Pixel 9", "Android 15")
        ),
        var logcatLines: List<String> = listOf("11-27 10:00:00.000 100 100 I com.example.live: healthy")
    ) : LiveAgentTransport {
        val executedCommands = mutableListOf<String>()

        override suspend fun checkReadiness(packageName: String): Result<DeviceAgentInfo> {
            executedCommands.add("checkReadiness:$packageName")
            return readyResult
        }

        override suspend fun dumpLogcat(packageName: String): Result<List<String>> {
            executedCommands.add("dumpLogcat:$packageName")
            return Result.success(logcatLines)
        }

        override suspend fun clearLogcat(): Result<Unit> {
            executedCommands.add("clearLogcat")
            return Result.success(Unit)
        }

        override suspend fun launchPackage(packageName: String): Result<String> {
            executedCommands.add("launch:$packageName")
            return Result.success("Launched $packageName")
        }

        override suspend fun forceStopPackage(packageName: String): Result<String> {
            executedCommands.add("forceStop:$packageName")
            return Result.success("Force stopped $packageName")
        }
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        repoScope.cancel()
        db.close()
    }

    private suspend fun seedApp(): Application {
        db.crashDao().insertProject(Project(id = "proj-lt", name = "LT", ownerId = "u1"))
        val app = Application(
            id = "app-lt",
            projectId = "proj-lt",
            name = "Live App",
            packageName = "com.example.live"
        )
        db.crashDao().insertApplication(app)
        return app
    }

    @Test
    fun connectAndStartSessionMarkOfflineWhenShizukuNotReady() = runBlocking {
        seedApp()
        val transport = ConfigurableTransport(
            readyResult = Result.failure(IllegalStateException("Shizuku inactive"))
        )
        val repo = LiveTestingRepositoryImpl(db.crashDao(), transport, repoScope)

        val connectResult = repo.connectToAgent("app-lt")
        assertTrue(connectResult.isFailure)
        assertEquals(AgentConnectionStatus.OFFLINE, db.crashDao().getApplicationById("app-lt")?.agentConnectionStatus)

        val startResult = repo.startTestSession(
            TestSession(id = "s-fail", projectId = "proj-lt", applicationId = "app-lt", buildId = "1", version = "1.0")
        )
        assertTrue(startResult.isFailure)
    }

    @Test
    fun allowlistedCommandsExecuteAndRejectShellInjection() = runBlocking {
        seedApp()
        val transport = ConfigurableTransport()
        val repo = LiveTestingRepositoryImpl(db.crashDao(), transport, repoScope)

        assertTrue(repo.connectToAgent("app-lt").isSuccess)
        assertEquals(AgentConnectionStatus.ONLINE, db.crashDao().getApplicationById("app-lt")?.agentConnectionStatus)

        val session = TestSession(
            id = "sess-cmd",
            projectId = "proj-lt",
            applicationId = "app-lt",
            buildId = "6",
            version = "1.0.6"
        )
        val startRes = repo.startTestSession(session)
        if (startRes.isFailure) {
            val ex = startRes.exceptionOrNull()
            ex?.printStackTrace()
            fail("Start failed: ${ex?.javaClass?.name}: ${ex?.message}")
        }
        assertTrue(startRes.isSuccess)

        assertTrue(repo.sendCommandToAgent("sess-cmd", "ping").isSuccess)
        assertTrue(repo.sendCommandToAgent("sess-cmd", "clear_logs").isSuccess)
        assertTrue(repo.sendCommandToAgent("sess-cmd", "launch").isSuccess)
        assertTrue(repo.sendCommandToAgent("sess-cmd", "force_stop").isSuccess)

        val rejected1 = repo.sendCommandToAgent("sess-cmd", "ping; rm -rf /")
        val rejected2 = repo.sendCommandToAgent("sess-cmd", "sh -c id")
        assertTrue(rejected1.isFailure)
        assertTrue(rejected1.exceptionOrNull() is IllegalArgumentException)
        assertTrue(rejected2.isFailure)

        assertTrue(transport.executedCommands.contains("clearLogcat"))
        assertTrue(transport.executedCommands.contains("launch:com.example.live"))
        assertTrue(transport.executedCommands.contains("forceStop:com.example.live"))

        assertTrue(repo.endTestSession("sess-cmd", TestSessionStatus.COMPLETED).isSuccess)
        assertEquals(
            TestSessionStatus.COMPLETED,
            db.crashDao().getTestSessionById("sess-cmd").first()?.status
        )
    }

    @Test
    fun dumpLogsDetectsCrashAndCorrelatesRealCrashReport() = runBlocking {
        seedApp()
        val transport = ConfigurableTransport()
        val repo = LiveTestingRepositoryImpl(db.crashDao(), transport, repoScope)

        val session = TestSession(
            id = "sess-crash",
            projectId = "proj-lt",
            applicationId = "app-lt",
            buildId = "6",
            version = "1.0.6"
        )
        val startRes = repo.startTestSession(session)
        if (startRes.isFailure) {
            val ex = startRes.exceptionOrNull()
            ex?.printStackTrace()
            fail("Start failed: ${ex?.javaClass?.name}: ${ex?.message}")
        }
        assertTrue(startRes.isSuccess)

        transport.logcatLines = listOf(
            "11-27 10:05:00.000  3000  3000 E AndroidRuntime: FATAL EXCEPTION: main",
            "11-27 10:05:00.001  3000  3000 E AndroidRuntime: Process: com.example.live, PID: 3000",
            "11-27 10:05:00.002  3000  3000 E AndroidRuntime: java.lang.RuntimeException: Live boom",
            "11-27 10:05:00.003  3000  3000 E AndroidRuntime: \tat com.example.live.Player.play(Player.kt:99)"
        )

        val dumpResult = repo.sendCommandToAgent("sess-crash", "dump_logs")
        assertTrue(dumpResult.isSuccess)

        val updatedSession = db.crashDao().getTestSessionById("sess-crash").first()
        assertEquals(TestSessionStatus.CRASH_DETECTED, updatedSession?.status)
        assertNotNull(updatedSession?.crashId)

        val crashes = db.crashDao().getCrashesByProject("proj-lt").first()
        assertEquals(1, crashes.size)
        assertEquals("java.lang.RuntimeException", crashes.first().exceptionType)
        assertEquals(IntegrationType.ON_DEVICE_WATCHER, crashes.first().source)
        assertFalse(crashes.first().isSimulatedQaReport)

        val events = db.crashDao().getTestEvents("sess-crash").first()
        assertTrue(events.any { it.type == TestEventType.CRASH })
    }
}
