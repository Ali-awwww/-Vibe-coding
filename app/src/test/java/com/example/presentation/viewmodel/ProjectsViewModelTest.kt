package com.example.presentation.viewmodel

import com.example.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepo: FakeAuthRepository
    private lateinit var crashRepo: FakeCrashRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepo = FakeAuthRepository()
        crashRepo = FakeCrashRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `unauthenticated user emits signed out state and blocks project creation`() = runTest {
        val viewModel = ProjectsViewModel(crashRepo, authRepo, isCloudConfigured = false)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        assertFalse(viewModel.uiState.value.isAuthenticated)
        viewModel.addProject("My Project", "Desc")
        assertTrue(crashRepo.projects.value.isEmpty())
    }

    @Test
    fun `addProject trims input, triggers refresh, and prevents blank submissions`() = runTest {
        authRepo.userFlow.value = UserAccount(id = "user-1", email = "dev@vibe.io")
        val viewModel = ProjectsViewModel(crashRepo, authRepo, isCloudConfigured = true)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.addProject("   ", "desc")
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(crashRepo.projects.value.isEmpty())

        viewModel.addProject("  Cosmic App  ", "  Telemetry  ", retentionDays = 60, piiScrubbing = true)
        val state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals(1, state.projects.size)
        assertEquals("Cosmic App", state.projects.first().name)
        assertEquals("Telemetry", state.projects.first().description)
        assertEquals(Role.OWNER, state.userRoleInSelectedProject)
    }

    @Test
    fun `addApplication and deleteApplication reload active project applications via refresh trigger`() = runTest {
        authRepo.userFlow.value = UserAccount(id = "user-1", email = "dev@vibe.io")
        crashRepo.projects.value = listOf(Project(id = "p1", name = "Alpha", ownerId = "user-1"))

        val viewModel = ProjectsViewModel(crashRepo, authRepo, isCloudConfigured = true)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.addApplication("p1", "  Android Client  ", "Android", "  com.vibe.android  ")
        val afterAdd = viewModel.uiState.value
        assertEquals(1, afterAdd.applications.size)
        assertEquals("Android Client", afterAdd.applications.first().name)
        assertEquals("com.vibe.android", afterAdd.applications.first().packageName)

        val appId = afterAdd.applications.first().id
        viewModel.deleteApplication(appId)
        assertTrue(viewModel.uiState.value.applications.isEmpty())
    }

    @Test
    fun `mutation failure surfaces error without crashing`() = runTest {
        authRepo.userFlow.value = UserAccount(id = "user-1", email = "dev@vibe.io")
        crashRepo.failNextWrite = IllegalStateException("Supabase timeout")

        val viewModel = ProjectsViewModel(crashRepo, authRepo, isCloudConfigured = true)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.addProject("Fail Project", "Desc")
        val state = viewModel.uiState.value
        assertFalse(state.isMutating)
        assertEquals("Supabase timeout", state.error)
        assertNull(state.message)
    }
}
