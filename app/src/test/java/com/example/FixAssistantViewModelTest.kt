package com.example

import androidx.lifecycle.ViewModel
import com.example.domain.model.CrashReport
import com.example.domain.repository.CrashRepository
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
        assertTrue(viewModel.uiState.value.error.orEmpty().contains("لم يتم العثور"))
    }

    @Test
    fun `null crash record ends loading instead of leaving a spinner active`() = runTest(mainDispatcher) {
        val viewModel = FixAssistantViewModel(repositoryReturning(flowOf(null)))

        viewModel.loadCrashAndDiagnosis("missing-crash")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.crash)
        assertTrue(viewModel.uiState.value.error.orEmpty().isNotBlank())
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
        assertTrue(viewModel.uiState.value.error.orEmpty().contains("offline lookup"))
    }

    private fun repositoryReturning(crashFlow: Flow<CrashReport?>): CrashRepository =
        Proxy.newProxyInstance(
            CrashRepository::class.java.classLoader,
            arrayOf(CrashRepository::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "getCrashDetails" -> crashFlow
                "toString" -> "FixAssistantCrashRepositoryTestDouble"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> throw AssertionError("Unexpected CrashRepository call: ${method.name}")
            }
        } as CrashRepository
}
