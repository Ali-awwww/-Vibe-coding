package com.example

import com.example.domain.model.AIDiagnosis
import com.example.domain.model.CrashReport
import com.example.domain.model.CrashVerificationRecord
import com.example.domain.model.FixSuggestion
import com.example.domain.repository.CrashRepository
import com.example.domain.repository.GeminiDailyQuotaExceededException
import com.example.presentation.viewmodel.FixAssistantViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class FixAssistantViewModelTest {

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
    fun `empty crash flow ends loading and exposes an actionable error`() = runTest(mainDispatcher) {
        val viewModel = FixAssistantViewModel(repositoryReturning(emptyFlow()))

        viewModel.loadCrashAndDiagnosis("missing-crash")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.crash)
        assertTrue(viewModel.uiState.value.loadError.orEmpty().contains("لم يتم العثور"))
    }

    @Test
    fun `null crash record ends loading instead of leaving a spinner active`() = runTest(mainDispatcher) {
        val viewModel = FixAssistantViewModel(repositoryReturning(flowOf(null)))

        viewModel.loadCrashAndDiagnosis("missing-crash")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.crash)
        assertTrue(viewModel.uiState.value.loadError.orEmpty().isNotBlank())
    }

    @Test
    fun `crash lookup failure is surfaced and clears loading state`() = runTest(mainDispatcher) {
        val viewModel = FixAssistantViewModel(
            repositoryReturning(flow { throw IOException("offline lookup") })
        )

        viewModel.loadCrashAndDiagnosis("crash-123")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.crash)
        assertTrue(viewModel.uiState.value.loadError.orEmpty().contains("offline lookup"))
    }

    @Test
    fun `diagnosis failure is reported as a load error instead of a generation action`() = runTest(mainDispatcher) {
        val viewModel = FixAssistantViewModel(
            repositoryReturning(
                crashFlow = flowOf(CrashReport(id = "crash-123")),
                diagnosisError = IllegalStateException("diagnosis offline")
            )
        )

        viewModel.loadCrashAndDiagnosis("crash-123")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.crash != null)
        assertTrue(
            "Unexpected diagnosis loadError='${viewModel.uiState.value.loadError}'",
            viewModel.uiState.value.loadError.orEmpty().contains("diagnosis offline")
        )
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `suggestions stream failure is kept separate from generation errors`() = runTest(mainDispatcher) {
        val viewModel = FixAssistantViewModel(
            repositoryReturning(
                crashFlow = flowOf(CrashReport(id = "crash-123")),
                suggestionsFlow = flow { throw IOException("suggestions offline") }
            )
        )

        viewModel.loadCrashAndDiagnosis("crash-123")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.suggestionsLoadError.orEmpty().contains("suggestions offline"))
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `verification history stream failure does not poison fix generation state`() = runTest(mainDispatcher) {
        val viewModel = FixAssistantViewModel(
            repositoryReturning(
                crashFlow = flowOf(CrashReport(id = "crash-123")),
                verificationsFlow = flow { throw IOException("history offline") }
            )
        )

        viewModel.loadCrashAndDiagnosis("crash-123")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.verificationsLoadError.orEmpty().contains("history offline"))
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `generateFix failure exposes single honest Arabic unavailable message`() = runTest(mainDispatcher) {
        val viewModel = FixAssistantViewModel(
            repositoryReturning(
                crashFlow = flowOf(CrashReport(id = "crash-123")),
                generateFixError = IOException("raw internal stack or HTTP 503")
            )
        )

        viewModel.loadCrashAndDiagnosis("crash-123")
        advanceUntilIdle()
        viewModel.generateFix()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGenerating)
        assertFalse(viewModel.uiState.value.isDailyQuotaExhausted)
        assertTrue(
            viewModel.uiState.value.error == FixAssistantViewModel.FIX_ASSISTANT_UNAVAILABLE_ERROR
        )
    }

    @Test
    fun `generateFix daily quota exhaustion stops cleanly with honest error and quota flag`() = runTest(mainDispatcher) {
        val viewModel = FixAssistantViewModel(
            repositoryReturning(
                crashFlow = flowOf(CrashReport(id = "crash-123")),
                generateFixError = GeminiDailyQuotaExceededException()
            )
        )

        viewModel.loadCrashAndDiagnosis("crash-123")
        advanceUntilIdle()
        viewModel.generateFix()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGenerating)
        assertTrue(viewModel.uiState.value.isDailyQuotaExhausted)
        assertTrue(
            viewModel.uiState.value.error == FixAssistantViewModel.FIX_ASSISTANT_UNAVAILABLE_ERROR
        )
    }

    private fun repositoryReturning(
        crashFlow: Flow<CrashReport?>,
        diagnosisError: Throwable? = null,
        suggestionsFlow: Flow<List<FixSuggestion>> = emptyFlow(),
        verificationsFlow: Flow<List<CrashVerificationRecord>> = emptyFlow(),
        generateFixError: Throwable? = null
    ): CrashRepository =
        Proxy.newProxyInstance(
            CrashRepository::class.java.classLoader,
            arrayOf(CrashRepository::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "getCrashDetails" -> crashFlow
                "getAIDiagnosis" -> {
                    diagnosisError?.let { throw it }
                    AIDiagnosis(crashId = (args?.firstOrNull() as CrashReport).id)
                }
                "getFixSuggestions" -> suggestionsFlow
                "getVerificationsByCrash" -> verificationsFlow
                method.name.takeIf { it.startsWith("generateFixSuggestion") } -> {
                    // Result is a Kotlin value class; a Java Proxy cannot safely box this suspend return.
                    // Throwing exercises executeRepositoryResult's exception-to-Result boundary.
                    throw (generateFixError ?: IllegalStateException("unavailable"))
                }
                "toString" -> "FixAssistantCrashRepositoryTestDouble"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> throw AssertionError("Unexpected CrashRepository call: ${method.name}")
            }
        } as CrashRepository
}
