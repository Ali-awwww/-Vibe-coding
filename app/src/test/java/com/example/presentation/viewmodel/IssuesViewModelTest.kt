package com.example.presentation.viewmodel

import com.example.domain.model.Issue
import com.example.domain.model.Project
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
