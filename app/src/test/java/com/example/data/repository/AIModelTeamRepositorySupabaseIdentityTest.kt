package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.remote.supabase.SupabaseAuthUser
import com.example.data.remote.supabase.SupabaseIdentityLinkProvider
import com.example.data.remote.supabase.SupabaseModelTeamRow
import com.example.data.remote.supabase.SupabaseModelTeamWrite
import com.example.data.remote.supabase.SupabaseTeamDataSource
import com.example.data.remote.supabase.SupabaseTeamExecutionRow
import com.example.data.remote.supabase.SupabaseTeamExecutionWrite
import com.example.data.remote.supabase.SupabaseTeamPostgrestApi
import com.example.data.remote.supabase.SupabaseSessionStore
import com.example.data.remote.supabase.SupabaseStoredSession
import com.example.domain.model.ModelTeam
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.PasswordRecoveryCredentials
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AIModelTeamRepositorySupabaseIdentityTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var sessionStore: SupabaseSessionStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionStore = SupabaseSessionStore(
            context = context,
            encryptSecret = { "test-encrypted:$it" },
            decryptSecret = { it.removePrefix("test-encrypted:") }
        )
        sessionStore.clear()
        sessionStore.write(
            SupabaseStoredSession(
                accessToken = "supabase-access",
                refreshToken = "supabase-refresh",
                expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + 3_600L,
                user = SupabaseAuthUser(id = "supabase-user", email = "owner@example.com")
            )
        )
    }

    @After
    fun tearDown() {
        sessionStore.clear()
        database.close()
    }

    @Test
    fun linkedSupabaseSubjectOwnsRemoteTeamWhilePresentationKeepsFirebaseUid() = runTest {
        val api = RecordingTeamApi()
        val supabaseAuth = FakeSupabaseAuthRepository(
            UserAccount(
                id = "supabase-user",
                email = "owner@example.com",
                displayName = "Owner",
                organization = "",
                createdAt = 1L,
                lastLoginAt = 1L
            )
        )
        val source = SupabaseTeamDataSource(
            api = api,
            authRepository = supabaseAuth,
            sessionStore = sessionStore,
            publishableKey = "publishable-test-key"
        )
        val linkProvider = object : SupabaseIdentityLinkProvider {
            override fun getSupabaseUserId(firebaseUserId: String): String? =
                "supabase-user".takeIf { firebaseUserId == "firebase-user" }
        }
        val repository = AIModelTeamRepositoryImpl(
            context = context,
            authRepository = FakeSupabaseAuthRepository(
                UserAccount(
                    id = "firebase-user",
                    email = "owner@example.com",
                    displayName = "Owner",
                    organization = "",
                    createdAt = 1L,
                    lastLoginAt = 1L
                )
            ),
            firestore = null,
            firebaseAuth = null,
            aiProviderDaoOverride = database.aiProviderDao(),
            currentUserIdProvider = { "firebase-user" },
            supabaseTeamDataSource = source,
            supabaseIdentityLinkProvider = linkProvider,
            enableRemoteBackend = true
        )

        val created = repository.createTeam(
            ModelTeam(id = "team-1", name = "Linked team", ownerId = "firebase-user")
        )
        assertTrue(created.isSuccess)
        assertEquals("team-1", created.getOrThrow())
        assertEquals("supabase-user", api.createdOwnerId)

        val teams = repository.getTeams("firebase-user").first()
        assertEquals(1, teams.size)
        assertEquals("firebase-user", teams.single().ownerId)
        assertEquals("eq.supabase-user", api.lastOwnerIdFilter)
    }

    private class FakeSupabaseAuthRepository(
        private val user: UserAccount
    ) : AuthRepository {
        override val currentUser = flowOf(user)
        override fun getCurrentUserSync(): UserAccount = user
        override suspend fun signUp(email: String, password: String, displayName: String, organization: String) =
            Result.failure<UserAccount>(UnsupportedOperationException())
        override suspend fun signIn(email: String, password: String) =
            Result.failure<UserAccount>(UnsupportedOperationException())
        override suspend fun signOut() = Unit
        override suspend fun resetPassword(email: String, newPassword: String) =
            Result.failure<Unit>(UnsupportedOperationException())
        override suspend fun signInWithGoogle(idToken: String) =
            Result.failure<Unit>(UnsupportedOperationException())
        override suspend fun requestPasswordRecovery(email: String) =
            Result.failure<Unit>(UnsupportedOperationException())
        override suspend fun completePasswordRecovery(
            credentials: PasswordRecoveryCredentials,
            newPassword: String
        ) = Result.failure<UserAccount>(UnsupportedOperationException())
    }

    private class RecordingTeamApi : SupabaseTeamPostgrestApi {
        private val teams = mutableListOf<SupabaseModelTeamRow>()
        var createdOwnerId: String? = null
        var lastOwnerIdFilter: String? = null

        override suspend fun listTeams(
            publishableKey: String,
            bearerToken: String,
            ownerIdFilter: String,
            select: String,
            order: String
        ): List<SupabaseModelTeamRow> {
            lastOwnerIdFilter = ownerIdFilter
            val expectedOwner = ownerIdFilter.removePrefix("eq.")
            return teams.filter { it.ownerId == expectedOwner }
        }

        override suspend fun getTeamById(
            publishableKey: String,
            bearerToken: String,
            idFilter: String,
            ownerIdFilter: String,
            select: String
        ): List<SupabaseModelTeamRow> = teams.filter {
            it.id == idFilter.removePrefix("eq.") &&
                it.ownerId == ownerIdFilter.removePrefix("eq.")
        }

        override suspend fun createTeam(
            publishableKey: String,
            bearerToken: String,
            prefer: String,
            request: SupabaseModelTeamWrite
        ): List<SupabaseModelTeamRow> {
            createdOwnerId = request.ownerId
            val row = SupabaseModelTeamRow(
                id = request.id,
                ownerId = request.ownerId,
                name = request.name,
                description = request.description,
                goal = request.goal,
                leaderMemberId = request.leaderMemberId,
                members = request.members,
                collaborationStyle = request.collaborationStyle,
                settings = request.settings,
                governance = request.governance,
                usageLimitMonthly = request.usageLimitMonthly,
                isPublic = request.isPublic,
                isTemplate = request.isTemplate,
                createdAtMs = request.createdAtMs,
                updatedAtMs = request.updatedAtMs
            )
            teams += row
            return listOf(row)
        }

        override suspend fun updateTeam(
            publishableKey: String,
            bearerToken: String,
            idFilter: String,
            ownerIdFilter: String,
            prefer: String,
            request: com.example.data.remote.supabase.SupabaseModelTeamPatch
        ): List<SupabaseModelTeamRow> = throw UnsupportedOperationException()

        override suspend fun deleteTeam(
            publishableKey: String,
            bearerToken: String,
            idFilter: String,
            ownerIdFilter: String,
            prefer: String
        ): List<SupabaseModelTeamRow> = throw UnsupportedOperationException()

        override suspend fun listTeamExecutions(
            publishableKey: String,
            bearerToken: String,
            teamIdFilter: String,
            userIdFilter: String,
            select: String,
            order: String
        ): List<SupabaseTeamExecutionRow> = throw UnsupportedOperationException()

        override suspend fun getTeamExecutionById(
            publishableKey: String,
            bearerToken: String,
            idFilter: String,
            userIdFilter: String,
            select: String
        ): List<SupabaseTeamExecutionRow> = throw UnsupportedOperationException()

        override suspend fun upsertTeamExecution(
            publishableKey: String,
            bearerToken: String,
            onConflict: String,
            prefer: String,
            request: SupabaseTeamExecutionWrite
        ): List<SupabaseTeamExecutionRow> = throw UnsupportedOperationException()
    }
}
