package com.example.presentation.viewmodel

import com.example.domain.model.CrashReport
import com.example.domain.model.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CrashesViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeCrashRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeCrashRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun filtersKeepRouteScopeAndClearOnlyOptionalFilters() = runTest {
        repository.crashes.value = listOf(
            CrashReport(
                id = "new", projectId = "p1", applicationId = "a1",
                exceptionType = "NullPointerException", message = "needle",
                timestamp = 200L, severity = Severity.HIGH,
                appVersion = "2.0", osVersion = "Android 14"
            ),
            CrashReport(
                id = "old", projectId = "p1", applicationId = "a1",
                exceptionType = "IllegalStateException", message = "older report",
                timestamp = 100L
            ),
            CrashReport(
                id = "other-project", projectId = "p2", applicationId = "a2",
                exceptionType = "NullPointerException", message = "needle",
                timestamp = 300L
            )
        )
        val viewModel = CrashesViewModel(
            repository = repository,
            initialProjectId = "p1",
            initialAppId = "a1",
            initialExceptionType = "NullPointerException",
            initialAppVersion = "2.0",
            initialSeverity = "HIGH",
            initialOsName = "Android"
        )
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("new"), viewModel.uiState.value.filteredCrashes.map { it.id })
        assertEquals(1, repository.getLatestCrashesCalls)
        assertEquals("p1", repository.lastLatestCrashProjectId)
        assertEquals("a1", repository.lastLatestCrashApplicationId)
        viewModel.updateSearchQuery("not present")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.filteredCrashes.isEmpty())

        viewModel.clearFilters()
        advanceUntilIdle()
        assertEquals("p1", viewModel.uiState.value.filter.projectId)
        assertEquals("a1", viewModel.uiState.value.filter.applicationId)
        assertEquals(listOf("new", "old"), viewModel.uiState.value.filteredCrashes.map { it.id })
    }

    @Test
    fun loadFailureIsDistinctFromEmptyStateAndRetryResubscribes() = runTest {
        repository.failNextLatestCrashesLoad = IllegalStateException("offline")
        val viewModel = CrashesViewModel(repository = repository, initialProjectId = "p1")
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("offline", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.filteredCrashes.isEmpty())

        repository.crashes.value = listOf(CrashReport(id = "recovered", projectId = "p1"))
        viewModel.refresh()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertEquals(listOf("recovered"), viewModel.uiState.value.filteredCrashes.map { it.id })
    }
    @Test
    fun paginationLoadsMoreAndSearchResetsTheVisiblePage() = runTest {
        repository.crashes.value = (0 until 60).map { index ->
            CrashReport(
                id = "crash-$index",
                projectId = "p1",
                applicationId = "a1",
                exceptionType = "IllegalStateException",
                message = "Crash $index",
                timestamp = index.toLong()
            )
        } + CrashReport(
            id = "foreign",
            projectId = "p2",
            applicationId = "a2",
            message = "Crash 1000",
            timestamp = 1_000L
        )

        val viewModel = CrashesViewModel(
            repository = repository,
            initialProjectId = "p1",
            initialAppId = "a1"
        )
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(60, viewModel.uiState.value.filteredCrashes.size)
        assertEquals(25, viewModel.uiState.value.visibleCrashes.size)
        assertTrue(viewModel.uiState.value.canLoadMore)

        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(50, viewModel.uiState.value.visibleCrashes.size)
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(60, viewModel.uiState.value.visibleCrashes.size)
        assertFalse(viewModel.uiState.value.canLoadMore)

        viewModel.updateSearchQuery("Crash 59")
        advanceUntilIdle()
        assertEquals(listOf("crash-59"), viewModel.uiState.value.visibleCrashes.map { it.id })
        assertEquals(0, viewModel.uiState.value.currentPage)
        assertEquals(1, repository.getLatestCrashesCalls)
    }
}
