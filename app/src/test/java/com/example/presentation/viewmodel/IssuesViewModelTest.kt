package com.example.presentation.viewmodel

import com.example.domain.model.Integration
import com.example.domain.model.IntegrationType
import com.example.domain.model.Issue
import com.example.domain.model.Project
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IssuesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeCrashRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeCrashRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `unscoped route loads issues across all projects`() = runTest {
        repository.projects.value = listOf(
            Project(id = "p1", name = "Alpha", ownerId = "u1"),
            Project(id = "p2", name = "Beta", ownerId = "u1")
        )
        repository.issues.value = listOf(Issue(id = "i1", projectId = "p1"), Issue(id = "i2", projectId = "p2"))

        val viewModel = IssuesViewModel(repository)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        assertNull(viewModel.uiState.value.selectedProject)
        assertEquals(setOf("i1", "i2"), viewModel.uiState.value.issues.map { it.id }.toSet())
        assertEquals(1, repository.getAllIssuesCalls)
    }

    @Test
    fun `route project id selects the exact project`() = runTest {
        repository.projects.value = listOf(
            Project(id = "p1", name = "Alpha", ownerId = "u1"),
            Project(id = "p2", name = "Beta", ownerId = "u1")
        )
        repository.issues.value = listOf(Issue(id = "i1", projectId = "p1"), Issue(id = "i2", projectId = "p2"))

        val viewModel = IssuesViewModel(repository, initialProjectId = "p2")
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        assertEquals("p2", viewModel.uiState.value.selectedProject?.id)
        assertEquals(listOf("i2"), viewModel.uiState.value.issues.map { it.id })
        assertEquals(0, repository.getAllIssuesCalls)
    }

    @Test
    fun `unavailable route project does not fall back to another project`() = runTest {
        repository.projects.value = listOf(Project(id = "p1", name = "Alpha", ownerId = "u1"))
        repository.issues.value = listOf(Issue(id = "i1", projectId = "p1"))

        val viewModel = IssuesViewModel(repository, initialProjectId = "p2")
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        assertNull(viewModel.uiState.value.selectedProject)
        assertTrue(viewModel.uiState.value.issues.isEmpty())
        assertEquals(IssuesViewModel.ERROR_PROJECT_UNAVAILABLE, viewModel.uiState.value.error)
        assertEquals(0, repository.getAllIssuesCalls)
    }

    @Test
    fun `all projects view exposes the newest successful integration sync time`() = runTest {
        repository.projects.value = listOf(
            Project(id = "p1", name = "Alpha", ownerId = "u1"),
            Project(id = "p2", name = "Beta", ownerId = "u1")
        )
        repository.integrations.value = listOf(
            Integration(id = "s1", projectId = "p1", name = "Sentry", type = IntegrationType.SENTRY, lastSyncedAt = 100L),
            Integration(id = "s2", projectId = "p2", name = "Bugsnag", type = IntegrationType.BUGSNAG, lastSyncedAt = 300L),
            Integration(id = "qa", projectId = "p2", name = "QA", type = IntegrationType.INTERNAL_QA, lastSyncedAt = 900L)
        )

        val viewModel = IssuesViewModel(repository)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        assertTrue(viewModel.uiState.value.isAllProjectsScope)
        assertEquals(300L, viewModel.uiState.value.lastSyncAt)
    }

    @Test
    fun `project scope reports only that projects successful sync time`() = runTest {
        repository.projects.value = listOf(
            Project(id = "p1", name = "Alpha", ownerId = "u1"),
            Project(id = "p2", name = "Beta", ownerId = "u1")
        )
        repository.integrations.value = listOf(
            Integration(id = "s1", projectId = "p1", name = "Sentry", type = IntegrationType.SENTRY, lastSyncedAt = 900L),
            Integration(id = "s2", projectId = "p2", name = "Bugsnag", type = IntegrationType.BUGSNAG, lastSyncedAt = 300L)
        )

        val viewModel = IssuesViewModel(repository, initialProjectId = "p2")
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        assertFalse(viewModel.uiState.value.isAllProjectsScope)
        assertEquals(300L, viewModel.uiState.value.lastSyncAt)
    }

    @Test
    fun `successful sync refreshes the visible integration timestamp`() = runTest {
        repository.projects.value = listOf(Project(id = "p1", name = "Alpha", ownerId = "u1"))
        repository.integrations.value = listOf(
            Integration(id = "s1", projectId = "p1", name = "Sentry", type = IntegrationType.SENTRY, lastSyncedAt = 100L)
        )
        val viewModel = IssuesViewModel(repository, initialProjectId = "p1")
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        viewModel.syncIssues()

        assertNotNull(viewModel.uiState.value.lastSyncAt)
        assertTrue(viewModel.uiState.value.lastSyncAt!! > 100L)
    }

    @Test
    fun allProjectsIssueStreamFollowsTheCurrentAuthorizedProjectSet() = runTest {
        val project1 = Project(id = "p1", name = "Alpha", ownerId = "u1")
        val project2 = Project(id = "p2", name = "Beta", ownerId = "u1")
        val project3 = Project(id = "p3", name = "Gamma", ownerId = "u1")
        repository.projects.value = listOf(project1, project2)
        repository.issues.value = listOf(
            Issue(id = "i1", projectId = "p1"),
            Issue(id = "i2", projectId = "p2"),
            Issue(id = "i3", projectId = "p3")
        )

        val viewModel = IssuesViewModel(repository)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        assertEquals(setOf("i1", "i2"), viewModel.uiState.value.issues.map { it.id }.toSet())

        repository.projects.value = listOf(project2)
        assertEquals(listOf("i2"), viewModel.uiState.value.issues.map { it.id })

        repository.projects.value = listOf(project2, project3)
        assertEquals(setOf("i2", "i3"), viewModel.uiState.value.issues.map { it.id }.toSet())
    }

    @Test
    fun selectedProjectLabelFollowsTheLatestAuthorizedProjectRecord() = runTest {
        val project = Project(id = "p1", name = "Alpha", ownerId = "u1")
        repository.projects.value = listOf(project)

        val viewModel = IssuesViewModel(repository, initialProjectId = "p1")
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        repository.projects.value = listOf(project.copy(name = "Renamed Alpha"))

        assertEquals("Renamed Alpha", viewModel.uiState.value.selectedProject?.name)
    }

    @Test
    fun selectingAProjectOutsideTheAuthorizedListFailsClosed() = runTest {
        repository.projects.value = listOf(Project(id = "p1", name = "Alpha", ownerId = "u1"))
        val viewModel = IssuesViewModel(repository)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        viewModel.selectProject(Project(id = "p2", name = "Foreign", ownerId = "u2"))

        assertNull(viewModel.uiState.value.selectedProject)
        assertTrue(viewModel.uiState.value.issues.isEmpty())
        assertEquals(IssuesViewModel.ERROR_PROJECT_UNAVAILABLE, viewModel.uiState.value.error)
    }

    @Test
    fun retryAfterSyncFailureRepeatsTheSyncOperation() = runTest {
        val backing = FakeCrashRepository()
        backing.projects.value = listOf(Project(id = "p1", name = "Alpha", ownerId = "u1"))
        var syncAttempts = 0
        val flakyRepository = object : CrashRepository by backing {
            override suspend fun syncCrashes(projectId: String): Result<Unit> {
                syncAttempts++
                return if (syncAttempts == 1) {
                    Result.failure(IllegalStateException("offline"))
                } else {
                    Result.success(Unit)
                }
            }
        }

        val viewModel = IssuesViewModel(flakyRepository, initialProjectId = "p1")
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        viewModel.syncIssues()
        assertEquals(IssuesViewModel.ERROR_SYNC, viewModel.uiState.value.error)

        viewModel.retry()

        assertEquals(2, syncAttempts)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `switching from a project to all projects replaces the scoped issue list`() = runTest {
        repository.projects.value = listOf(
            Project(id = "p1", name = "Alpha", ownerId = "u1"),
            Project(id = "p2", name = "Beta", ownerId = "u1")
        )
        repository.issues.value = listOf(Issue(id = "i1", projectId = "p1"), Issue(id = "i2", projectId = "p2"))

        val viewModel = IssuesViewModel(repository, initialProjectId = "p2")
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
        viewModel.selectAllProjects()

        assertNull(viewModel.uiState.value.selectedProject)
        assertEquals(setOf("i1", "i2"), viewModel.uiState.value.issues.map { it.id }.toSet())
    }
}
