package com.example.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashRepositoryCancellationTest {
    @Test
    fun cancellableRepositoryBoundaryRethrowsCancellation() = runTest {
        val expected = CancellationException("screen disposed")
        val actual = try {
            runCatchingCancellable<String> { throw expected }
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }

        assertSame(expected, actual)
    }

    @Test
    fun cancellableRepositoryBoundaryKeepsOrdinaryLookupFailuresRecoverable() = runTest {
        val result = runCatchingCancellable<String> {
            throw IllegalStateException("source repository unavailable")
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
