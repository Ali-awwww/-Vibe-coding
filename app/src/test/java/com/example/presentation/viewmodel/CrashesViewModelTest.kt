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
        assertEquals(1, repository.getCrashPageCalls)
        assertEquals("p1", repository.lastCrashPageProjectId)
        assertEquals("a1", repository.lastCrashPageApplicationId)
        assertNull(repository.lastCrashPageCursorId)
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
        repository.failNextCrashPageLoad = IllegalStateException("offline")
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
        assertEquals(2, repository.getCrashPageCalls)
    }

    @Test
    fun applyFiltersUpdatesTheScopedCrashResults() = runTest {
        repository.crashes.value = listOf(
            CrashReport(
                id = "match", projectId = "p1", applicationId = "a1",
                exceptionType = "NullPointerException", appVersion = "2.0",
                severity = Severity.HIGH, osVersion = "Android 14"
            ),
            CrashReport(
                id = "wrong-severity", projectId = "p1", applicationId = "a1",
                exceptionType = "NullPointerException", appVersion = "2.0",
                severity = Severity.MEDIUM, osVersion = "Android 14"
            ),
            CrashReport(
                id = "wrong-version", projectId = "p1", applicationId = "a1",
                exceptionType = "NullPointerException", appVersion = "1.0",
                severity = Severity.HIGH, osVersion = "Android 14"
            ),
            CrashReport(
                id = "wrong-os", projectId = "p1", applicationId = "a1",
                exceptionType = "NullPointerException", appVersion = "2.0",
                severity = Severity.HIGH, osVersion = "iOS"
            )
        )
        val viewModel = CrashesViewModel(repository = repository, initialProjectId = "p1", initialAppId = "a1")
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.applyFilters("NullPointerException", "2.0", "HIGH", "Android")
        advanceUntilIdle()

        assertEquals(listOf("match"), viewModel.uiState.value.filteredCrashes.map { it.id })
        assertEquals(1, repository.getCrashPageCalls)
        viewModel.clearFilters()
        advanceUntilIdle()
        assertEquals("p1", viewModel.uiState.value.filter.projectId)
        assertEquals("a1", viewModel.uiState.value.filter.applicationId)
        assertEquals(4, viewModel.uiState.value.filteredCrashes.size)
    }

    @Test
    fun paginationLoadsRepositoryPagesAndSearchUsesLoadedRecords() = runTest {
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

        assertEquals(25, viewModel.uiState.value.filteredCrashes.size)
        assertEquals(25, viewModel.uiState.value.visibleCrashes.size)
        assertTrue(viewModel.uiState.value.canLoadMore)
        assertEquals(1, repository.getCrashPageCalls)

        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(50, viewModel.uiState.value.visibleCrashes.size)
        assertTrue(viewModel.uiState.value.canLoadMore)
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(60, viewModel.uiState.value.visibleCrashes.size)
        assertFalse(viewModel.uiState.value.canLoadMore)
        assertEquals(3, repository.getCrashPageCalls)
        assertEquals("crash-10", repository.lastCrashPageCursorId)

        viewModel.updateSearchQuery("Crash 59")
        advanceUntilIdle()
        assertEquals(listOf("crash-59"), viewModel.uiState.value.visibleCrashes.map { it.id })
        assertEquals(3, repository.getCrashPageCalls)
    }

    @Test
    fun exactHundredCrashBoundaryDoesNotRequestAnEmptyFifthPage() = runTest {
        repository.crashes.value = (0 until 100).map { index ->
            CrashReport(
                id = "crash-$index",
                projectId = "p1",
                applicationId = "a1",
                message = "Crash $index",
                timestamp = index.toLong()
            )
        }
        val viewModel = CrashesViewModel(
            repository = repository,
            initialProjectId = "p1",
            initialAppId = "a1"
        )
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        repeat(3) {
            viewModel.loadMore()
            advanceUntilIdle()
        }

        assertEquals(100, viewModel.uiState.value.visibleCrashes.size)
        assertFalse(viewModel.uiState.value.canLoadMore)
        assertEquals(4, repository.getCrashPageCalls)

        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(4, repository.getCrashPageCalls)
    }

    @Test
    fun oneHundredAndFirstCrashRemainsReachableAfterFourFullPages() = runTest {
        repository.crashes.value = (0 until 101).map { index ->
            CrashReport(
                id = "crash-$index",
                projectId = "p1",
                applicationId = "a1",
                message = "Crash $index",
                timestamp = index.toLong()
            )
        }
        val viewModel = CrashesViewModel(
            repository = repository,
            initialProjectId = "p1",
            initialAppId = "a1"
        )
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        repeat(4) {
            viewModel.loadMore()
            advanceUntilIdle()
        }

        assertEquals(101, viewModel.uiState.value.visibleCrashes.size)
        assertEquals("crash-100", viewModel.uiState.value.visibleCrashes.first().id)
        assertEquals("crash-0", viewModel.uiState.value.visibleCrashes.last().id)
        assertFalse(viewModel.uiState.value.canLoadMore)
        assertEquals(5, repository.getCrashPageCalls)
    }

    @Test
    fun failedNextPageCanBeRetriedWithoutDroppingLoadedCrashes() = runTest {
        repository.crashes.value = (0 until 26).map { index ->
            CrashReport(
                id = "page-$index",
                projectId = "p1",
                applicationId = "a1",
                timestamp = index.toLong()
            )
        }
        val viewModel = CrashesViewModel(repository = repository, initialProjectId = "p1", initialAppId = "a1")
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(25, viewModel.uiState.value.crashes.size)
        assertTrue(viewModel.uiState.value.canLoadMore)

        repository.failNextCrashPageLoad = IllegalStateException("page offline")
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(25, viewModel.uiState.value.crashes.size)
        assertEquals("page offline", viewModel.uiState.value.loadMoreError)
        assertTrue(viewModel.uiState.value.canLoadMore)

        viewModel.loadMore()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.loadMoreError)
        assertFalse(viewModel.uiState.value.canLoadMore)
        assertEquals(26, viewModel.uiState.value.crashes.size)
        assertEquals("page-25", viewModel.uiState.value.crashes.first().id)
        assertEquals("page-0", viewModel.uiState.value.crashes.last().id)
    }

    @Test
    fun hasConnectedCrashSourceIsFalseWhenNoCrashesAndNoIntegrationsExist() = runTest {
        val viewModel = CrashesViewModel(repository = repository, initialProjectId = "p1")
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.crashes.isEmpty())
        assertFalse(viewModel.uiState.value.hasConnectedCrashSource)
    }
}
