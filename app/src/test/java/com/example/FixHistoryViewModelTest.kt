package com.example

import com.example.domain.model.CrashVerificationRecord
import com.example.domain.repository.CrashRepository
import com.example.presentation.viewmodel.FixHistoryViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class FixHistoryViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadFailureClearsLoadingAndRetryRecovers() = runTest(mainDispatcher) {
        val requests = AtomicInteger()
        val repository = repositoryReturning {
            when (requests.incrementAndGet()) {
                1 -> flow { throw IOException("history offline") }
                else -> flowOf(emptyList())
            }
        }
        val viewModel = FixHistoryViewModel(repository)

        // Loading is initiated by the screen, not by ViewModel construction.
        assertEquals(0, requests.get())

        viewModel.loadData()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.error.orEmpty().contains("history offline"))

        viewModel.loadData()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.filteredVerifications.isEmpty())
        assertEquals(2, requests.get())
    }

    @Test
    fun switchingProjectCancelsPreviousVerificationCollector() = runTest(mainDispatcher) {
        val firstCollectionStarted = CompletableDeferred<Unit>()
        val firstCollectionCancelled = CompletableDeferred<Unit>()
        val repository = repositoryReturning(projectFlow = { projectId ->
            if (projectId == "project-a") {
                flow {
                    firstCollectionStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        firstCollectionCancelled.complete(Unit)
                    }
                }
            } else {
                flowOf(emptyList())
            }
        })
        val viewModel = FixHistoryViewModel(repository)

        viewModel.loadData("project-a")
        runCurrent()
        firstCollectionStarted.await()

        viewModel.selectProject("project-b")
        advanceUntilIdle()
        firstCollectionCancelled.await()

        assertEquals("project-b", viewModel.uiState.value.selectedProjectId)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun emptyCompletingStreamDoesNotLeaveLoadingActive() = runTest(mainDispatcher) {
        val viewModel = FixHistoryViewModel(repositoryReturning { emptyFlow() })

        viewModel.loadData()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    private fun repositoryReturning(
        allFlow: () -> Flow<List<CrashVerificationRecord>> = { emptyFlow() },
        projectFlow: (String) -> Flow<List<CrashVerificationRecord>> = { emptyFlow() }
    ): CrashRepository =
        Proxy.newProxyInstance(
            CrashRepository::class.java.classLoader,
            arrayOf(CrashRepository::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "getAllVerifications" -> allFlow()
                "getVerificationsByProject" -> projectFlow(args?.firstOrNull() as String)
                "toString" -> "FixHistoryCrashRepositoryTestDouble"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> throw AssertionError("Unexpected CrashRepository call: ${method.name}")
            }
        } as CrashRepository
}
