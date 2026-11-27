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
class DashboardViewModelTest {

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
    fun `signed out user skips repository reads and emits unauthenticated state`() = runTest {
        val viewModel = DashboardViewModel(crashRepo, authRepo, isCloudConfigured = false)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertFalse(state.isAuthenticated)
        assertFalse(state.isCloudConfigured)
        assertEquals(0, crashRepo.getProjectsForUserCalls)
        assertEquals(0, crashRepo.getAllApplicationsCalls)
        assertEquals(0, crashRepo.getAllCrashesCalls)
        assertEquals(0, crashRepo.getAllIssuesCalls)
    }

    @Test
    fun `selecting active project scopes applications issues and crashes`() = runTest {
        val user = UserAccount(id = "user-1", email = "dev@vibe.io", displayName = "Ali")
        authRepo.userFlow.value = user
        crashRepo.projects.value = listOf(
            Project(id = "p1", name = "Alpha", ownerId = "user-1"),
            Project(id = "p2", name = "Beta", ownerId = "user-1")
        )
        crashRepo.applications.value = listOf(
            Application(id = "a1", projectId = "p1", name = "App 1", packageName = "com.a1"),
            Application(id = "a2", projectId = "p2", name = "App 2", packageName = "com.a2"),
            Application(id = "a3", projectId = "p2", name = "App 3", packageName = "com.a3")
        )
        crashRepo.issues.value = listOf(
            Issue(id = "i1", projectId = "p1", title = "NPE"),
            Issue(id = "i2", projectId = "p2", title = "OOM")
        )
        crashRepo.crashes.value = listOf(
            CrashReport(id = "c1", projectId = "p1", exceptionType = "NPE"),
            CrashReport(id = "c2", projectId = "p2", exceptionType = "OOM"),
            CrashReport(id = "c3", projectId = "p2", exceptionType = "ISE")
        )

        val viewModel = DashboardViewModel(crashRepo, authRepo, isCloudConfigured = true)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        val allState = viewModel.uiState.value
        assertTrue(allState.isAuthenticated)
        assertNull(allState.selectedProjectId)
        assertEquals(2, allState.projectCount)
        assertEquals(3, allState.applicationCount)
        assertEquals(2, allState.issueCount)
        assertEquals(3, allState.recentCrashes.size)

        viewModel.selectProject("p2")
        val scopedState = viewModel.uiState.value
        assertEquals("p2", scopedState.selectedProjectId)
        assertEquals(2, scopedState.projectCount)
        assertEquals(2, scopedState.applicationCount)
        assertEquals(1, scopedState.issueCount)
        assertEquals(listOf("c2", "c3"), scopedState.recentCrashes.map { it.id })
    }

    @Test
    fun `crash summary counts all authorized reports and shows the newest ten in order`() = runTest {
        authRepo.userFlow.value = UserAccount(id = "user-1", email = "dev@vibe.io")
        crashRepo.projects.value = listOf(Project(id = "p1", name = "Alpha", ownerId = "user-1"))
        val realCrashes = (0L..11L).map { timestamp ->
            CrashReport(
                id = "crash-$timestamp",
                projectId = "p1",
                timestamp = timestamp
            )
        }
        crashRepo.crashes.value = realCrashes.reversed() + CrashReport(
            id = "qa-only",
            projectId = "p1",
            timestamp = 99L,
            source = IntegrationType.INTERNAL_QA
        )

        val viewModel = DashboardViewModel(crashRepo, authRepo, isCloudConfigured = true)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertEquals(12, state.crashCount)
        assertEquals(10, state.recentCrashes.size)
        assertEquals(
            (11L downTo 2L).map { "crash-$it" },
            state.recentCrashes.map { it.id }
        )
        assertFalse(state.recentCrashes.any { it.id == "qa-only" })
    }

    @Test
    fun `dashboard counts and orders real issues while excluding internal QA`() = runTest {
        authRepo.userFlow.value = UserAccount(id = "user-1", email = "dev@vibe.io")
        crashRepo.projects.value = listOf(Project(id = "p1", name = "Alpha", ownerId = "user-1"))
        val realIssues = (0L..6L).map { timestamp ->
            Issue(id = "issue-$timestamp", projectId = "p1", title = "Issue $timestamp", lastOccurrence = timestamp)
        }
        crashRepo.issues.value = realIssues.reversed() + Issue(
            id = "qa-issue", projectId = "p1", title = "QA issue",
            lastOccurrence = 99L, source = IntegrationType.INTERNAL_QA
        )
        val viewModel = DashboardViewModel(crashRepo, authRepo, isCloudConfigured = true)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertEquals(7, state.issueCount)
        assertEquals(5, state.recentIssues.size)
        assertEquals((6L downTo 2L).map { "issue-$it" }, state.recentIssues.map { it.id })
        assertFalse(state.recentIssues.any { it.id == "qa-issue" })
    }

    @Test
    fun `stream failure emits user safe error and refresh recovers`() = runTest {
        authRepo.userFlow.value = UserAccount(id = "user-1", email = "dev@vibe.io")
        crashRepo.projects.value = listOf(Project(id = "p1", name = "Alpha", ownerId = "user-1"))
        crashRepo.failNextProjectsLoad = IllegalStateException("Firestore HTTP 500 internal trace secret")

        val viewModel = DashboardViewModel(crashRepo, authRepo, isCloudConfigured = true)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        val failedState = viewModel.uiState.value
        assertFalse(failedState.isLoading)
        assertEquals(DashboardViewModel.ERROR_LOAD, failedState.error)
        assertFalse(failedState.error!!.contains("secret"))

        viewModel.refresh()
        val recoveredState = viewModel.uiState.value
        assertNull(recoveredState.error)
        assertEquals(1, recoveredState.projectCount)
    }
}
