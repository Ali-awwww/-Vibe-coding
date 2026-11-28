package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.remote.BugsnagApi
import com.example.data.remote.BugsnagError
import com.example.data.remote.BugsnagEvent
import com.example.data.remote.SentryApi
import com.example.data.remote.SentryEvent
import com.example.data.remote.SentryIssue
import com.example.data.repository.DiagnosticVerificationTransport
import com.example.data.repository.SdkIntegrationRepositoryImpl
import com.example.domain.model.Application
import com.example.domain.model.CrashReport
import com.example.domain.model.Integration
import com.example.domain.model.IntegrationStatus
import com.example.domain.model.IntegrationType
import com.example.domain.model.Project
import com.example.domain.model.SdkSetupStatus
import com.example.util.SecurityUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config
class VerifyIntegrationRealTest {

    private lateinit var db: AppDatabase

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

    private suspend fun seedProjectAndApp(
        appId: String = "app-1",
        projectId: String = "proj-1",
        packageName: String = "com.example.target"
    ): Application {
        db.crashDao().insertProject(Project(id = projectId, name = "Demo", ownerId = "user-1"))
        val app = Application(
            id = appId,
            projectId = projectId,
            name = "Demo App",
            packageName = packageName
        )
        db.crashDao().insertApplication(app)
        return app
    }

    private fun inactiveTransport() = object : DiagnosticVerificationTransport {
        override val isAvailable: Boolean = true
        override suspend fun isWatcherMonitoring(packageName: String): Boolean = false
        override suspend fun hasRecentLogcatActivity(packageName: String): Boolean = false
    }

    @Test
    fun verifyIntegrationRejectsBlankApplicationId() = runBlocking {
        val repo = SdkIntegrationRepositoryImpl(db.crashDao(), transport = inactiveTransport())
        val result = repo.verifyIntegration("   ")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun verifyIntegrationFailsHonestlyWhenNoSourceOrSignalExists() = runBlocking {
        seedProjectAndApp()
        val repo = SdkIntegrationRepositoryImpl(db.crashDao(), transport = inactiveTransport())

        val result = repo.verifyIntegration("app-1")

        assertTrue(result.isFailure)
        assertEquals(SdkSetupStatus.PENDING, db.crashDao().getApplicationById("app-1")?.sdkSetupStatus)
    }

    @Test
    fun verifyIntegrationSucceedsWhenSentryApiReturnsIssues() = runBlocking {
        seedProjectAndApp()
        val encryptedConfig = SecurityUtils.encryptSecret("""{"token":"snt-tok","org":"my-org","project":"my-app"}""")
        db.crashDao().insertIntegration(
            Integration(
                id = "int-sentry",
                projectId = "proj-1",
                type = IntegrationType.SENTRY,
                configJson = encryptedConfig,
                status = IntegrationStatus.UNKNOWN
            )
        )

        val fakeSentryApi = object : SentryApi {
            override suspend fun getIssues(
                authToken: String,
                orgSlug: String,
                projectSlug: String,
                query: String?
            ): List<SentryIssue> = listOf(
                SentryIssue(
                    id = "101",
                    title = "NullPointerException",
                    culprit = "MainActivity.kt",
                    level = "error",
                    status = "unresolved",
                    firstSeen = "2026-01-01T00:00:00Z",
                    lastSeen = "2026-01-01T00:00:00Z",
                    count = "1",
                    userCount = 1,
                    permalink = null,
                    metadata = null
                )
            )

            override suspend fun getIssueEvents(authToken: String, issueId: String): List<SentryEvent> = emptyList()
        }

        val repo = SdkIntegrationRepositoryImpl(
            crashDao = db.crashDao(),
            sentryApi = fakeSentryApi,
            transport = inactiveTransport()
        )

        val result = repo.verifyIntegration("app-1")

        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
        assertEquals(SdkSetupStatus.CONFIGURED, db.crashDao().getApplicationById("app-1")?.sdkSetupStatus)
        assertEquals(
            IntegrationStatus.CONNECTED,
            db.crashDao().getIntegrations("proj-1").first().first().status
        )
    }

    @Test
    fun verifyIntegrationReturnsFalseWaitingEventWhenSentryConnectedButEmpty() = runBlocking {
        seedProjectAndApp()
        val encryptedConfig = SecurityUtils.encryptSecret("""{"token":"snt-tok","org":"my-org","project":"my-app"}""")
        db.crashDao().insertIntegration(
            Integration(
                id = "int-sentry-empty",
                projectId = "proj-1",
                type = IntegrationType.SENTRY,
                configJson = encryptedConfig
            )
        )

        val emptySentryApi = object : SentryApi {
            override suspend fun getIssues(authToken: String, orgSlug: String, projectSlug: String, query: String?) =
                emptyList<SentryIssue>()
            override suspend fun getIssueEvents(authToken: String, issueId: String) = emptyList<SentryEvent>()
        }

        val repo = SdkIntegrationRepositoryImpl(
            crashDao = db.crashDao(),
            sentryApi = emptySentryApi,
            transport = inactiveTransport()
        )

        val result = repo.verifyIntegration("app-1")

        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrNull())
        assertEquals(SdkSetupStatus.PENDING, db.crashDao().getApplicationById("app-1")?.sdkSetupStatus)
        assertEquals(
            IntegrationStatus.CONNECTED,
            db.crashDao().getIntegrations("proj-1").first().first().status
        )
    }

    @Test
    fun verifyIntegrationMarksUnauthorizedOnSentryHttp401() = runBlocking {
        seedProjectAndApp()
        val encryptedConfig = SecurityUtils.encryptSecret("""{"token":"bad-tok","org":"my-org","project":"my-app"}""")
        db.crashDao().insertIntegration(
            Integration(
                id = "int-sentry-401",
                projectId = "proj-1",
                type = IntegrationType.SENTRY,
                configJson = encryptedConfig
            )
        )

        val failingSentryApi = object : SentryApi {
            override suspend fun getIssues(authToken: String, orgSlug: String, projectSlug: String, query: String?): List<SentryIssue> {
                throw HttpException(
                    Response.error<List<SentryIssue>>(
                        401,
                        "Unauthorized".toResponseBody("text/plain".toMediaTypeOrNull())
                    )
                )
            }
            override suspend fun getIssueEvents(authToken: String, issueId: String) = emptyList<SentryEvent>()
        }

        val repo = SdkIntegrationRepositoryImpl(
            crashDao = db.crashDao(),
            sentryApi = failingSentryApi,
            transport = inactiveTransport()
        )

        val result = repo.verifyIntegration("app-1")

        assertTrue(result.isFailure)
        assertEquals(SdkSetupStatus.ERROR, db.crashDao().getApplicationById("app-1")?.sdkSetupStatus)
        assertEquals(
            IntegrationStatus.UNAUTHORIZED,
            db.crashDao().getIntegrations("proj-1").first().first().status
        )
    }

    @Test
    fun verifyIntegrationSucceedsWhenBugsnagReturnsErrors() = runBlocking {
        seedProjectAndApp()
        val encryptedConfig = SecurityUtils.encryptSecret("""{"token":"bsg-tok","projectId":"proj-bsg"}""")
        db.crashDao().insertIntegration(
            Integration(
                id = "int-bugsnag",
                projectId = "proj-1",
                type = IntegrationType.BUGSNAG,
                configJson = encryptedConfig
            )
        )

        val fakeBugsnagApi = object : BugsnagApi {
            override suspend fun getErrors(authToken: String, projectId: String, perPage: Int): List<BugsnagError> =
                listOf(
                    BugsnagError(
                        id = "err-1",
                        error_class = "IllegalStateException",
                        message = "Bugsnag verified",
                        context = null,
                        last_message = null,
                        first_seen = "2026-01-01T00:00:00Z",
                        last_seen = "2026-01-01T00:00:00Z",
                        events = 2,
                        users = 1,
                        status = "open",
                        severity = "error"
                    )
                )

            override suspend fun getErrorEvents(authToken: String, errorId: String, perPage: Int) =
                emptyList<BugsnagEvent>()
        }

        val repo = SdkIntegrationRepositoryImpl(
            crashDao = db.crashDao(),
            bugsnagApi = fakeBugsnagApi,
            transport = inactiveTransport()
        )

        val result = repo.verifyIntegration("app-1")

        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
        assertEquals(SdkSetupStatus.CONFIGURED, db.crashDao().getApplicationById("app-1")?.sdkSetupStatus)
    }

    @Test
    fun verifyIntegrationSucceedsWhenShizukuWatcherOrLogcatActiveAndIgnoresSimulatedQaReports() = runBlocking {
        seedProjectAndApp()
        db.crashDao().insertCrash(
            CrashReport(
                id = "sim-1",
                projectId = "proj-1",
                applicationId = "app-1",
                exceptionType = "SimulatedException",
                message = "QA synthetic crash",
                stackTrace = "trace",
                source = IntegrationType.WEBHOOK,
                isSimulatedQaReport = true
            )
        )
        val inactiveRepo = SdkIntegrationRepositoryImpl(db.crashDao(), transport = inactiveTransport())
        assertTrue(inactiveRepo.verifyIntegration("app-1").isFailure)

        val activeWatcherTransport = object : DiagnosticVerificationTransport {
            override val isAvailable: Boolean = true
            override suspend fun isWatcherMonitoring(packageName: String): Boolean = packageName == "com.example.target"
            override suspend fun hasRecentLogcatActivity(packageName: String): Boolean = false
        }
        val activeRepo = SdkIntegrationRepositoryImpl(db.crashDao(), transport = activeWatcherTransport)
        val watcherResult = activeRepo.verifyIntegration("app-1")
        assertTrue(watcherResult.isSuccess)
        assertEquals(true, watcherResult.getOrNull())
        assertEquals(SdkSetupStatus.CONFIGURED, db.crashDao().getApplicationById("app-1")?.sdkSetupStatus)
    }

    @Test
    fun verifyIntegrationFailsClosedOnFirebaseCrashlyticsWithoutTelemetry() = runBlocking {
        seedProjectAndApp()
        db.crashDao().insertIntegration(
            Integration(
                id = "int-firebase",
                projectId = "proj-1",
                type = IntegrationType.FIREBASE_CRASHLYTICS,
                status = IntegrationStatus.CONNECTED
            )
        )
        val repo = SdkIntegrationRepositoryImpl(db.crashDao(), transport = inactiveTransport())
        val result = repo.verifyIntegration("app-1")
        assertTrue(result.isFailure)
        assertFalse(db.crashDao().getApplicationById("app-1")?.sdkSetupStatus == SdkSetupStatus.CONFIGURED)
    }
}
