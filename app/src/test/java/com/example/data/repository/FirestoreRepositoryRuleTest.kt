package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.AppDatabase
import com.example.data.remote.BugsnagApi
import com.example.data.remote.HuggingFaceApi
import com.example.data.remote.OpenRouterApi
import com.example.data.remote.SentryApi
import com.example.domain.model.Application
import com.example.domain.model.CrashReport
import com.example.domain.model.ModelTeam
import com.example.domain.model.Severity
import com.example.domain.model.SystemNotification
import com.example.domain.model.TeamExecution
import com.example.domain.model.TeamUsageRecord
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.GitHubRepository
import com.example.domain.repository.SymbolicationRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.UUID

class FirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var authState: MutableStateFlow<UserAccount?>
    private lateinit var fakeAuthRepo: AuthRepository
    private lateinit var crashRepo: CrashRepositoryImpl
    private lateinit var teamRepo: AIModelTeamRepositoryImpl

    @Before
    fun setUpRepositories() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        authState = MutableStateFlow(null)
        fakeAuthRepo = object : AuthRepository {
            override val currentUser: Flow<UserAccount?> = authState
            override fun getCurrentUserSync(): UserAccount? = authState.value
            override suspend fun signUp(email: String, password: String, displayName: String, organization: String): Result<UserAccount> =
                Result.failure(UnsupportedOperationException())
            override suspend fun signIn(email: String, password: String): Result<UserAccount> =
                Result.failure(UnsupportedOperationException())
            override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
                Result.failure(UnsupportedOperationException())
            override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> =
                Result.failure(UnsupportedOperationException())
            override suspend fun signOut() {
                authState.value = null
            }
        }

        val sentryApi = noopProxy<SentryApi>()
        val bugsnagApi = noopProxy<BugsnagApi>()
        val openRouterApi = noopProxy<OpenRouterApi>()
        val huggingFaceApi = noopProxy<HuggingFaceApi>()
        val aiModelRepo = AIModelRepositoryImpl(database.aiProviderDao(), openRouterApi, huggingFaceApi)
        val gitHubRepo = GitHubRepositoryImpl(database.crashDao(), noopProxy<GitHubApiProxyTarget>())
        val symbolRepo = SymbolicationRepositoryImpl(database.crashDao())

        crashRepo = CrashRepositoryImpl(
            firestore = firestore,
            crashDao = database.crashDao(),
            sentryApi = sentryApi,
            bugsnagApi = bugsnagApi,
            githubRepository = gitHubRepo,
            symbolicationRepository = symbolRepo,
            aiModelRepository = aiModelRepo,
            auth = auth,
            authRepository = fakeAuthRepo,
            currentUserIdProvider = { auth.currentUser?.uid }
        )

        teamRepo = AIModelTeamRepositoryImpl(
            context = context,
            authRepository = fakeAuthRepo,
            firestore = firestore,
            firebaseAuth = auth,
            aiProviderDaoOverride = database.aiProviderDao(),
            currentUserIdProvider = { auth.currentUser?.uid }
        )
    }

    @After
    fun closeDatabase() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun crashRepository_ownerCrudAndCollectionGroupQueries_succeedUnderRules() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        authState.value = UserAccount(id = aliceUid, email = ALICE_EMAIL)

        val projectId = "proj-${UUID.randomUUID()}"
        val appId = "app-${UUID.randomUUID()}"
        val crashId = "crash-${UUID.randomUUID()}"
        val notifId = "notif-${UUID.randomUUID()}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            crashRepo.addProject(
                com.example.domain.model.Project(
                    id = projectId,
                    name = "Emulator Project",
                    description = "Rule verification",
                    ownerId = aliceUid
                )
            )
            crashRepo.addApplication(
                Application(
                    id = appId,
                    projectId = projectId,
                    name = "Android App",
                    ownerId = aliceUid
                )
            )
            crashRepo.addCrash(
                CrashReport(
                    id = crashId,
                    projectId = projectId,
                    applicationId = appId,
                    exceptionType = "IllegalStateException",
                    message = "State failed",
                    stackTrace = "App.kt:18",
                    severity = Severity.HIGH,
                    userId = aliceUid
                )
            )
            crashRepo.updateCrashStatus(crashId, true)
            crashRepo.addNotification(
                SystemNotification(
                    id = notifId,
                    projectId = projectId,
                    title = "Alert",
                    message = "High severity crash",
                    isRead = false
                )
            )
            crashRepo.markNotificationAsRead(notifId)
        }

        val projects = withTimeout(FLOW_TIMEOUT_MS) {
            crashRepo.getProjects().first { list -> list.any { it.id == projectId } }
        }
        assertTrue(projects.any { it.id == projectId })

        val crashes = withTimeout(FLOW_TIMEOUT_MS) {
            crashRepo.getLatestCrashes(projectId = null, applicationId = null).first { list -> list.any { it.id == crashId } }
        }
        assertTrue(crashes.any { it.id == crashId && it.isResolved })

        val detail = withTimeout(FLOW_TIMEOUT_MS) {
            crashRepo.getCrashDetails(crashId).first { it != null }
        }
        assertNotNull(detail)
        assertEquals(true, detail?.isResolved)

        val issues = withTimeout(FLOW_TIMEOUT_MS) {
            crashRepo.getAllIssues().first { list -> list.any { it.projectId == projectId } }
        }
        assertTrue(issues.any { it.projectId == projectId })

        val unreadCount = withTimeout(FLOW_TIMEOUT_MS) {
            crashRepo.getUnreadNotificationCount().first()
        }
        assertEquals(0, unreadCount)
    }

    @Test
    fun crashRepository_crossUserRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        authState.value = UserAccount(id = aliceUid, email = ALICE_EMAIL)

        val projectId = "proj-${UUID.randomUUID()}"
        val appId = "app-${UUID.randomUUID()}"
        val crashId = "crash-${UUID.randomUUID()}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            crashRepo.addProject(com.example.domain.model.Project(id = projectId, name = "Alice Private", ownerId = aliceUid))
            crashRepo.addApplication(Application(id = appId, projectId = projectId, name = "Alice App", ownerId = aliceUid))
            crashRepo.addCrash(
                CrashReport(
                    id = crashId,
                    projectId = projectId,
                    applicationId = appId,
                    exceptionType = "RuntimeException",
                    message = "Private",
                    userId = aliceUid
                )
            )
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        authState.value = UserAccount(id = bobUid, email = BOB_EMAIL)

        val directFailure = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("projects").document(projectId)
                    .get().await()
            }
        }.exceptionOrNull()
        assertNotNull(directFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            directFailure!!.unwrapFirestoreException()?.code
        )

        val groupFailure = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                firestore.collectionGroup("crashes")
                    .whereEqualTo("userId", aliceUid)
                    .snapshots()
                    .first()
            }
        }.exceptionOrNull()
        assertNotNull(groupFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            groupFailure!!.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun firestoreReads_unauthenticatedCaller_failWithPermissionDenied() = runBlocking {
        auth.signOut()
        authState.value = null

        val directFailure = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                firestore.collection("users").document("someone")
                    .collection("projects").get(Source.SERVER).await()
            }
        }.exceptionOrNull()
        assertNotNull("Expected unauthenticated Firestore read to fail, got: $directFailure", directFailure)
        val firestoreCode = directFailure!!.unwrapFirestoreException()?.code
        assumeTrue(
            "Firestore emulator is unavailable; skipping rules assertion",
            firestoreCode != FirebaseFirestoreException.Code.UNAVAILABLE
        )
        assertEquals(
            "Unexpected Firestore failure: $directFailure",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            firestoreCode
        )
    }

    @Test
    fun aiModelTeamRepository_ownerCrudAndCrossUserIsolation_enforcedByRules() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        authState.value = UserAccount(id = aliceUid, email = ALICE_EMAIL)

        val teamId = "team-${UUID.randomUUID()}"
        val execId = "exec-${UUID.randomUUID()}"
        val usageId = "usage-${UUID.randomUUID()}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            teamRepo.createTeam(ModelTeam(id = teamId, name = "Rule Test Team", ownerId = aliceUid)).getOrThrow()
            teamRepo.logExecution(
                TeamExecution(id = execId, teamId = teamId, userId = aliceUid, prompt = "Inspect crash")
            ).getOrThrow()
            teamRepo.logTeamUsage(
                TeamUsageRecord(
                    id = usageId,
                    userId = aliceUid,
                    teamId = teamId,
                    modelId = "firebase_ai::gemini-3.8-flash"
                )
            ).getOrThrow()
        }

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) { teamRepo.getTeamById(teamId) }
        assertNotNull(fetched)
        assertEquals("Rule Test Team", fetched?.name)

        val bobUid = signInTestUser(BOB_EMAIL)
        authState.value = UserAccount(id = bobUid, email = BOB_EMAIL)

        val crossUserError = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("model_teams").document(teamId)
                    .get().await()
            }
        }.exceptionOrNull()
        assertNotNull(crossUserError)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            crossUserError!!.unwrapFirestoreException()?.code
        )
    }

    private inline fun <reified T> noopProxy(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ ->
            throw UnsupportedOperationException("Noop proxy")
        } as T

    private interface GitHubApiProxyTarget : com.example.data.remote.GitHubApi

    private companion object {
        const val ALICE_EMAIL = "alice.rule@test.com"
        const val BOB_EMAIL = "bob.rule@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
