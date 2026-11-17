package com.example.data.remote.supabase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.*
import com.example.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseTeamDataSourceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun listTeamsScopesOwnerAndDecodesNestedTeamSettings() = runTest {
        val api = FakeTeamApi().apply {
            teamRows = listOf(
                SupabaseModelTeamRow(
                    id = "team-1",
                    ownerId = "user-a",
                    name = "Architecture",
                    members = listOf(
                        mapOf(
                            "id" to "member-1",
                            "name" to "Planner",
                            "modelId" to "model-1",
                            "providerId" to "provider-1",
                            "role" to "TEAM_LEADER"
                        )
                    ),
                    settings = mapOf("maxReviewRounds" to 4, "maxConcurrentRequests" to 2),
                    createdAtMs = 1_000L,
                    updatedAtMs = 2_000L
                )
            )
        }
        val source = createSource(api)

        val teams = source.getTeams("user-a")

        assertEquals("eq.user-a", api.lastOwnerFilter)
        assertEquals("publishable-test-key", api.lastPublishableKey)
        assertEquals("Bearer access-token", api.lastBearerToken)
        assertEquals(1, teams.size)
        assertEquals("Architecture", teams.single().name)
        assertEquals("Planner", teams.single().members.single().name)
        assertEquals(TeamRole.TEAM_LEADER, teams.single().members.single().role)
        assertEquals(4, teams.single().settings.maxReviewRounds)
    }

    @Test
    fun createTeamUsesAuthenticatedOwnerAndSerializesNestedMembers() = runTest {
        val api = FakeTeamApi()
        val source = createSource(api)
        val team = ModelTeam(
            name = "Delivery",
            ownerId = "user-a",
            members = listOf(
                TeamMember(
                    id = "member-1",
                    name = "Reviewer",
                    modelId = "model-review",
                    role = TeamRole.CODE_REVIEWER
                )
            )
        )

        val saved = source.createTeam(team)

        assertNotNull(api.createdTeam)
        assertEquals("user-a", api.createdTeam?.ownerId)
        assertEquals(saved.id, api.createdTeam?.id)
        assertEquals("CODE_REVIEWER", api.createdTeam?.members?.single()?.get("role"))
        assertEquals("Delivery", saved.name)
        assertEquals("user-a", saved.ownerId)
    }

    @Test
    fun createTeamRejectsAnotherOwnersIdentityBeforeCallingPostgrest() = runTest {
        val api = FakeTeamApi()
        val source = createSource(api)
        val result = runCatching {
            source.createTeam(ModelTeam(name = "Foreign", ownerId = "user-b"))
        }

        assertTrue(result.exceptionOrNull() is SecurityException)
        assertNull(api.createdTeam)
    }

    @Test
    fun logExecutionUpsertsAuthenticatedUserAndPersistsTypedMessages() = runTest {
        val api = FakeTeamApi()
        val source = createSource(api)
        val execution = TeamExecution(
            id = "execution-1",
            teamId = "team-1",
            userId = "user-a",
            prompt = "Review the crash",
            status = ExecutionStatus.COMPLETED,
            messages = listOf(
                AgentMessage(
                    id = "message-1",
                    fromMemberId = "member-1",
                    content = "Root cause identified",
                    type = MessageType.REVIEW
                )
            ),
            consensusPoints = listOf("Null input is the root cause")
        )

        val saved = source.logExecution(execution)

        assertEquals("user-a", api.executionWrite?.userId)
        assertEquals("id", api.lastConflictTarget)
        assertEquals("resolution=merge-duplicates,return=representation", api.lastPrefer)
        assertEquals("Root cause identified", saved.messages.single().content)
        assertEquals(listOf("Null input is the root cause"), saved.consensusPoints)
    }

    @Test
    fun expiredSessionIsRejectedBeforeAnyPostgrestRequest() = runTest {
        val api = FakeTeamApi()
        val source = createSource(api, expiresAt = 1_050L, now = 1_000L)
        val result = runCatching { source.getTeams("user-a") }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(0, api.requestCount)
    }

    @Test
    fun deleteTeamRequiresARepresentationOfAnActuallyDeletedRow() = runTest {
        val api = FakeTeamApi().apply { deleteRows = emptyList() }
        val source = createSource(api)
        val result = runCatching { source.deleteTeam("team-1") }

        assertTrue(result.exceptionOrNull() is NoSuchElementException)
        assertEquals("eq.user-a", api.lastOwnerFilter)
    }

    private fun createSource(
        api: FakeTeamApi,
        expiresAt: Long = 2_000L,
        now: Long = 1_000L
    ): SupabaseTeamDataSource {
        val store = SupabaseSessionStore(
            context = context,
            encryptSecret = { "test-encrypted:" + it },
            decryptSecret = { it.removePrefix("test-encrypted:") }
        )
        store.clear()
        store.write(
            SupabaseStoredSession(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                expiresAtEpochSeconds = expiresAt,
                user = SupabaseAuthUser(id = "user-a", email = "a@example.test")
            )
        )
        return SupabaseTeamDataSource(
            api = api,
            authRepository = FakeAuthRepository(UserAccount(id = "user-a", email = "a@example.test")),
            sessionStore = store,
            publishableKey = "publishable-test-key",
            nowEpochSeconds = { now }
        )
    }

    private class FakeAuthRepository(private val user: UserAccount?) : AuthRepository {
        override val currentUser: Flow<UserAccount?> = flowOf(user)
        override fun getCurrentUserSync(): UserAccount? = user
        override suspend fun signUp(
            email: String,
            password: String,
            displayName: String,
            organization: String
        ): Result<UserAccount> = Result.failure(UnsupportedOperationException())
        override suspend fun signIn(email: String, password: String): Result<UserAccount> =
            Result.failure(UnsupportedOperationException())
        override suspend fun signOut() = Unit
        override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
    }

    private class FakeTeamApi : SupabaseTeamPostgrestApi {
        var teamRows: List<SupabaseModelTeamRow> = emptyList()
        var deleteRows: List<SupabaseModelTeamRow> = emptyList()
        var createdTeam: SupabaseModelTeamWrite? = null
        var executionWrite: SupabaseTeamExecutionWrite? = null
        var lastOwnerFilter: String? = null
        var lastPublishableKey: String? = null
        var lastBearerToken: String? = null
        var lastConflictTarget: String? = null
        var lastPrefer: String? = null
        var requestCount: Int = 0

        override suspend fun listTeams(
            publishableKey: String,
            bearerToken: String,
            ownerIdFilter: String,
            select: String,
            order: String
        ): List<SupabaseModelTeamRow> {
            record(publishableKey, bearerToken)
            lastOwnerFilter = ownerIdFilter
            return teamRows
        }

        override suspend fun getTeamById(
            publishableKey: String,
            bearerToken: String,
            idFilter: String,
            ownerIdFilter: String,
            select: String
        ): List<SupabaseModelTeamRow> {
            record(publishableKey, bearerToken)
            lastOwnerFilter = ownerIdFilter
            return teamRows.filter { "eq." + it.id == idFilter }
        }

        override suspend fun createTeam(
            publishableKey: String,
            bearerToken: String,
            prefer: String,
            request: SupabaseModelTeamWrite
        ): List<SupabaseModelTeamRow> {
            record(publishableKey, bearerToken)
            createdTeam = request
            lastPrefer = prefer
            return listOf(request.toRow())
        }

        override suspend fun updateTeam(
            publishableKey: String,
            bearerToken: String,
            idFilter: String,
            ownerIdFilter: String,
            prefer: String,
            request: SupabaseModelTeamPatch
        ): List<SupabaseModelTeamRow> {
            record(publishableKey, bearerToken)
            lastOwnerFilter = ownerIdFilter
            lastPrefer = prefer
            return teamRows.filter { "eq." + it.id == idFilter }.map { existing ->
                existing.copy(
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
                    updatedAtMs = request.updatedAtMs
                )
            }
        }

        override suspend fun deleteTeam(
            publishableKey: String,
            bearerToken: String,
            idFilter: String,
            ownerIdFilter: String,
            prefer: String
        ): List<SupabaseModelTeamRow> {
            record(publishableKey, bearerToken)
            lastOwnerFilter = ownerIdFilter
            lastPrefer = prefer
            return deleteRows
        }

        override suspend fun listTeamExecutions(
            publishableKey: String,
            bearerToken: String,
            teamIdFilter: String,
            userIdFilter: String,
            select: String,
            order: String
        ): List<SupabaseTeamExecutionRow> {
            record(publishableKey, bearerToken)
            return emptyList()
        }

        override suspend fun getTeamExecutionById(
            publishableKey: String,
            bearerToken: String,
            idFilter: String,
            userIdFilter: String,
            select: String
        ): List<SupabaseTeamExecutionRow> {
            record(publishableKey, bearerToken)
            return emptyList()
        }

        override suspend fun upsertTeamExecution(
            publishableKey: String,
            bearerToken: String,
            onConflict: String,
            prefer: String,
            request: SupabaseTeamExecutionWrite
        ): List<SupabaseTeamExecutionRow> {
            record(publishableKey, bearerToken)
            executionWrite = request
            lastConflictTarget = onConflict
            lastPrefer = prefer
            return listOf(request.toRow())
        }

        private fun record(key: String, bearer: String) {
            requestCount += 1
            lastPublishableKey = key
            lastBearerToken = bearer
        }

        private fun SupabaseModelTeamWrite.toRow() = SupabaseModelTeamRow(
            id = id,
            ownerId = ownerId,
            name = name,
            description = description,
            goal = goal,
            leaderMemberId = leaderMemberId,
            members = members,
            collaborationStyle = collaborationStyle,
            settings = settings,
            governance = governance,
            usageLimitMonthly = usageLimitMonthly,
            isPublic = isPublic,
            isTemplate = isTemplate,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs
        )

        private fun SupabaseTeamExecutionWrite.toRow() = SupabaseTeamExecutionRow(
            id = id,
            teamId = teamId,
            userId = userId,
            projectId = projectId,
            prompt = prompt,
            status = status,
            steps = steps,
            sharedMemory = sharedMemory,
            messages = messages,
            finalResult = finalResult,
            error = error,
            totalTokens = totalTokens,
            totalCost = totalCost,
            totalRequests = totalRequests,
            memberUsages = memberUsages,
            modelFailovers = modelFailovers,
            governanceEnforced = governanceEnforced,
            freeOnlyActive = freeOnlyActive,
            preExecutionEstimation = preExecutionEstimation,
            attachedFiles = attachedFiles,
            consensusPoints = consensusPoints,
            disagreementPoints = disagreementPoints,
            startedAtMs = startedAtMs,
            completedAtMs = completedAtMs
        )
    }
}
