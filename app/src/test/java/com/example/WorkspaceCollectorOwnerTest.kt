package com.example

import com.example.presentation.viewmodel.WorkspaceCollectorOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceCollectorOwnerTest {

    @Test
    fun startingAnotherWorkspaceCancelsAllCollectorsFromThePreviousWorkspace() = runTest {
        val owner = WorkspaceCollectorOwner(this)
        val firstStarted = CompletableDeferred<Unit>()
        val firstCancelled = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val secondCancelled = CompletableDeferred<Unit>()

        owner.start {
            firstStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                firstCancelled.complete(Unit)
            }
        }
        runCurrent()
        firstStarted.await()

        owner.start {
            secondStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                secondCancelled.complete(Unit)
            }
        }
        runCurrent()

        firstCancelled.await()
        secondStarted.await()
        assertTrue(firstCancelled.isCompleted)

        owner.cancel()
        runCurrent()
        secondCancelled.await()
        assertTrue(secondCancelled.isCompleted)
    }

    @Test
    fun leavingWorkspaceCancelsItsActiveCollectors() = runTest {
        val owner = WorkspaceCollectorOwner(this)
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()

        owner.start {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
        runCurrent()
        started.await()

        owner.cancel()
        runCurrent()

        cancelled.await()
        assertTrue(cancelled.isCompleted)
    }
}
