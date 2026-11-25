package com.example.presentation.viewmodel

import com.example.domain.model.CrashReport
import com.example.domain.model.DebugSymbolFile
import com.example.domain.model.SymbolicationReport
import com.example.domain.model.SymbolicationStatus
import com.example.domain.repository.CrashRepository
import com.example.domain.repository.SymbolicationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CrashDetailViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun missingCrashProducesExplicitNotFoundState() = runTest {
        val symbolication = RecordingSymbolicationRepository()
        val viewModel = CrashDetailViewModel(
            repository = TestCrashRepository(flowOf(null)),
            symbolicationRepository = symbolication
        )

        viewModel.loadCrash("missing")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.notFound)
        assertFalse(viewModel.uiState.value.loadError)
        assertNull(viewModel.uiState.value.crash)
        assertEquals(0, symbolication.calls)
    }

    @Test
    fun loadFailureCanBeRetriedWithoutLeavingTheScreenBlank() = runTest {
        val crash = testCrash()
        var shouldFail = true
        val details = flow<CrashReport?> {
            if (shouldFail) throw IllegalStateException("offline")
            emit(crash)
        }
        val symbolication = RecordingSymbolicationRepository()
        val viewModel = CrashDetailViewModel(
            repository = TestCrashRepository(details),
            symbolicationRepository = symbolication
        )

        viewModel.loadCrash(crash.id)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.notFound)

        shouldFail = false
        viewModel.loadCrash(crash.id)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.loadError)
        assertEquals(crash.id, viewModel.uiState.value.crash?.id)
        assertEquals(1, symbolication.calls)
    }

    @Test
    fun liveCrashUpdatesDoNotRepeatSymbolicationWithoutNewStackEvidence() = runTest {
        val crash = testCrash()
        val details = MutableStateFlow<CrashReport?>(crash)
        val symbolication = RecordingSymbolicationRepository()
        val viewModel = CrashDetailViewModel(
            repository = TestCrashRepository(details),
            symbolicationRepository = symbolication
        )

        viewModel.loadCrash(crash.id)
        advanceUntilIdle()
        assertEquals(1, symbolication.calls)

        details.value = crash.copy(isResolved = true)
        advanceUntilIdle()
        assertEquals(1, symbolication.calls)

        details.value = crash.copy(stackTrace = "at Example.updated(Example.kt:2)")
        advanceUntilIdle()
        assertEquals(2, symbolication.calls)
    }

    @Test
    fun changingCrashIdLoadsTheRequestedRecordAndIgnoresOldRouteUpdates() = runTest {
        val firstCrash = testCrash()
        val secondCrash = testCrash().copy(id = "crash-2", message = "Second crash")
        val firstDetails = MutableStateFlow<CrashReport?>(firstCrash)
        val secondDetails = MutableStateFlow<CrashReport?>(secondCrash)
        val requestedIds = mutableListOf<String>()
        val repository = object : CrashRepository by FakeCrashRepository() {
            override fun getCrashDetails(crashId: String): Flow<CrashReport?> {
                requestedIds += crashId
                return when (crashId) {
                    firstCrash.id -> firstDetails
                    secondCrash.id -> secondDetails
                    else -> flowOf(null)
                }
            }
        }
        val viewModel = CrashDetailViewModel(repository, RecordingSymbolicationRepository())

        viewModel.loadCrash(firstCrash.id)
        advanceUntilIdle()
        assertEquals(firstCrash.id, viewModel.uiState.value.crash?.id)

        viewModel.loadCrash(secondCrash.id)
        advanceUntilIdle()
        firstDetails.value = firstCrash.copy(message = "Stale update from previous route")
        advanceUntilIdle()

        assertEquals(listOf(firstCrash.id, secondCrash.id), requestedIds)
        assertEquals(secondCrash.id, viewModel.uiState.value.crash?.id)
        assertEquals(secondCrash.message, viewModel.uiState.value.crash?.message)
    }

    @Test
    fun thrownSymbolicationFailureIsVisibleAndRetryable() = runTest {
        val crash = testCrash()
        val symbolication = RecordingSymbolicationRepository().apply {
            throwFailure = IllegalStateException("symbolication offline")
        }
        val viewModel = CrashDetailViewModel(
            repository = TestCrashRepository(flowOf(crash)),
            symbolicationRepository = symbolication
        )

        viewModel.loadCrash(crash.id)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSymbolicating)
        assertEquals("symbolication offline", viewModel.uiState.value.symbolicationError)

        symbolication.throwFailure = null
        viewModel.symbolicateCrash()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSymbolicating)
        assertEquals(2, symbolication.calls)
        assertEquals(SymbolicationStatus.MISSING_DEBUG_SYMBOLS, viewModel.uiState.value.symbolication?.status)
        assertNull(viewModel.uiState.value.symbolicationError)
    }

    @Test
    fun initialStateWaitsForRouteLoadToBegin() = runTest {
        val viewModel = CrashDetailViewModel(
            repository = TestCrashRepository(flowOf(null)),
            symbolicationRepository = RecordingSymbolicationRepository()
        )

        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.crash)
    }

    @Test
    fun resolveWriteFailureIsNotSilentlyIgnored() = runTest {
        val repository = TestCrashRepository(flowOf(testCrash())).apply {
            resolveFailure = IllegalStateException("write denied")
        }
        val viewModel = CrashDetailViewModel(repository, RecordingSymbolicationRepository())

        viewModel.loadCrash("crash-1")
        advanceUntilIdle()
        viewModel.resolveCrash()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isResolving)
        assertEquals("write denied", viewModel.uiState.value.error)
    }

    @Test
    fun analysisCancellationIsRethrownAndLoadingStateIsReleased() = runTest {
        val repository = TestCrashRepository(flowOf(testCrash())).apply {
            diagnosisFailure = CancellationException("cancelled")
        }
        val viewModel = CrashDetailViewModel(repository, RecordingSymbolicationRepository())

        viewModel.loadCrash("crash-1")
        advanceUntilIdle()
        viewModel.analyzeCrash()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAnalyzing)
        assertNull(viewModel.uiState.value.error)
    }

    private fun testCrash() = CrashReport(
        id = "crash-1",
        projectId = "project-1",
        exceptionType = "IllegalStateException",
        message = "Missing state",
        stackTrace = "at Example.run(Example.kt:1)"
    )

    private class TestCrashRepository(
        private val details: Flow<CrashReport?>
    ) : CrashRepository by FakeCrashRepository() {
        var resolveFailure: Throwable? = null
        var diagnosisFailure: Throwable? = null

        override fun getCrashDetails(crashId: String): Flow<CrashReport?> = details

        override suspend fun updateCrashStatus(crashId: String, isResolved: Boolean) {
            resolveFailure?.let { throw it }
        }

        override suspend fun getAIDiagnosis(crashReport: CrashReport): com.example.domain.model.AIDiagnosis {
            diagnosisFailure?.let { throw it }
            return com.example.domain.model.AIDiagnosis(crashId = crashReport.id)
        }
    }

    private class RecordingSymbolicationRepository : SymbolicationRepository {
        var calls = 0
        var throwFailure: Throwable? = null

        override fun getDebugSymbols(projectId: String): Flow<List<DebugSymbolFile>> = flowOf(emptyList())
        override suspend fun uploadDebugSymbol(file: DebugSymbolFile): Result<Unit> = Result.success(Unit)
        override suspend fun deleteDebugSymbol(id: String): Result<Unit> = Result.success(Unit)

        override suspend fun symbolicate(crashReport: CrashReport): Result<SymbolicationReport> {
            calls++
            throwFailure?.let { throw it }
            return Result.success(
                SymbolicationReport(
                    status = SymbolicationStatus.MISSING_DEBUG_SYMBOLS,
                    frames = emptyList()
                )
            )
        }
    }
}
