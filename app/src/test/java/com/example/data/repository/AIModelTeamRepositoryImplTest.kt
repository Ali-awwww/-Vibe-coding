package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AIProviderDao
import com.example.data.local.AppDatabase
import com.example.domain.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class AIModelTeamRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var aiProviderDao: AIProviderDao
    private var activeUserId: String? = "user-a"

    private lateinit var repository: AIModelTeamRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        aiProviderDao = database.aiProviderDao()
        repository = AIModelTeamRepositoryImpl(
            context = context,
            authRepository = null,
            firestore = null,
            firebaseAuth = null,
            aiProviderDaoOverride = aiProviderDao,
            currentUserIdProvider = { activeUserId }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getTeams reacts dynamically to create update and delete without resubscribing`() = runTest {
        val emissions = mutableListOf<List<ModelTeam>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.getTeams("user-a").collect { emissions.add(it) }
        }

        val initialTeam = ModelTeam(
            id = "t-1",
            name = "Alpha Squad",
            ownerId = "user-a",
            members = listOf(TeamMember(id = "m-1", name = "Planner", modelId = "m1", role = TeamRole.TEAM_LEADER))
        )
        assertTrue(repository.createTeam(initialTeam).isSuccess)
        assertEquals(1, emissions.last().size)
        assertEquals("Alpha Squad", emissions.last().first().name)

        assertTrue(repository.updateTeam(initialTeam.copy(name = "Alpha Squad v2")).isSuccess)
        assertEquals("Alpha Squad v2", emissions.last().first().name)

        assertTrue(repository.deleteTeam("t-1").isSuccess)
        assertTrue(emissions.last().isEmpty())

        job.cancel()
    }

    @Test
    fun `cross-user read update and delete are rejected with SecurityException`() = runTest {
        activeUserId = "user-a"
        val team = ModelTeam(id = "t-shared", name = "Private Team", ownerId = "user-a")
        assertTrue(repository.createTeam(team).isSuccess)

        activeUserId = "user-b"

        var getTeamsFailed = false
        try {
            repository.getTeams("user-a").first()
        } catch (_: SecurityException) {
            getTeamsFailed = true
        }
        assertTrue("Reading another user's teams must fail", getTeamsFailed)

        var getByIdFailed = false
        try {
            repository.getTeamById("t-shared")
        } catch (_: SecurityException) {
            getByIdFailed = true
        }
        assertTrue("Reading another user's team by ID must fail", getByIdFailed)

        val updateResult = repository.updateTeam(team.copy(ownerId = "", name = "Hijacked"))
        assertTrue(updateResult.isFailure)
        assertTrue(updateResult.exceptionOrNull() is SecurityException)

        val deleteResult = repository.deleteTeam("t-shared")
        assertTrue(deleteResult.isFailure)
        assertTrue(deleteResult.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `unauthenticated identity fails closed on repository mutations and reads`() = runTest {
        activeUserId = null

        val createResult = repository.createTeam(ModelTeam(id = "t-unauth", name = "No User"))
        assertTrue(createResult.isFailure)
        assertTrue(createResult.exceptionOrNull() is IllegalStateException)

        var failedRead = false
        try {
            repository.getTeams("user-a").first()
        } catch (_: IllegalStateException) {
            failedRead = true
        }
        assertTrue(failedRead)
    }

    @Test
    fun `logExecution updates getTeamExecutions flow and isolates by current user`() = runTest {
        activeUserId = "user-a"
        val emissions = mutableListOf<List<TeamExecution>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.getTeamExecutions("team-1").collect { emissions.add(it) }
        }

        val exec = TeamExecution(
            id = "exec-1",
            teamId = "team-1",
            userId = "user-a",
            prompt = "Fix crash",
            status = ExecutionStatus.COMPLETED
        )
        assertTrue(repository.logExecution(exec).isSuccess)
        assertEquals(1, emissions.last().size)
        assertEquals("exec-1", emissions.last().first().id)

        activeUserId = "user-b"
        assertTrue(repository.getTeamExecutions("team-1").first().isEmpty())

        job.cancel()
    }

    @Test
    fun `getTeamUsageRecords filters rows by active user identity`() = runTest {
        aiProviderDao.insertTeamUsageRecord(
            TeamUsageRecord(
                id = "u-1",
                teamId = "team-1",
                executionId = "exec-1",
                userId = "user-a",
                memberId = "m1",
                memberName = "Member A",
                modelId = "model-1",
                modelName = "Model 1",
                providerId = "or",
                providerName = "OpenRouter",
                taskDescription = "Audit",
                totalTokens = 100
            )
        )
        aiProviderDao.insertTeamUsageRecord(
            TeamUsageRecord(
                id = "u-2",
                teamId = "team-1",
                executionId = "exec-2",
                userId = "user-b",
                memberId = "m2",
                memberName = "Member B",
                modelId = "model-2",
                modelName = "Model 2",
                providerId = "or",
                providerName = "OpenRouter",
                taskDescription = "Other",
                totalTokens = 200
            )
        )

        activeUserId = "user-a"
        val userARecords = repository.getTeamUsageRecords("team-1").first()
        assertEquals(1, userARecords.size)
        assertEquals("u-1", userARecords.first().id)
    }
}
