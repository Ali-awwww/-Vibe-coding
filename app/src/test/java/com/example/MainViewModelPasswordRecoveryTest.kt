package com.example

import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.PasswordRecoveryCredentials
import com.example.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelPasswordRecoveryTest {

    @Test
    fun cancellingPasswordSetupClearsOldCredentialsAndAllowsANewRecoveryLink() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = RecordingAuthRepository()
            val viewModel = MainViewModel(repository)

            viewModel.acceptPasswordRecoveryLink(recoveryLink("first-one-time-hash"))
            assertTrue(viewModel.passwordRecoveryState.value.isReadyForNewPassword)

            viewModel.cancelPasswordRecovery()
            assertFalse(viewModel.passwordRecoveryState.value.isReadyForNewPassword)

            // A cancelled recovery must not submit the old one-time credential.
            viewModel.completePasswordRecovery("new-secure-password")
            assertNull(repository.lastCompletedCredentials)

            // The false -> true transition must be observable for a subsequent link.
            viewModel.acceptPasswordRecoveryLink(recoveryLink("second-one-time-hash"))
            assertTrue(viewModel.passwordRecoveryState.value.isReadyForNewPassword)
            viewModel.completePasswordRecovery("another-secure-password")
            advanceUntilIdle()

            assertEquals("second-one-time-hash", repository.lastCompletedCredentials?.tokenHash)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun replacingRecoveryLinkWhilePasswordSetupIsOpenAdvancesUiGeneration() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = MainViewModel(RecordingAuthRepository())

            viewModel.acceptPasswordRecoveryLink(recoveryLink("first-one-time-hash"))
            val firstGeneration = viewModel.passwordRecoveryState.value.linkGeneration
            assertTrue(viewModel.passwordRecoveryState.value.isReadyForNewPassword)

            // This simulates a second deep link arriving without leaving password setup.
            viewModel.acceptPasswordRecoveryLink(recoveryLink("second-one-time-hash"))
            val secondGeneration = viewModel.passwordRecoveryState.value.linkGeneration

            assertTrue(secondGeneration > firstGeneration)
            assertTrue(viewModel.passwordRecoveryState.value.isReadyForNewPassword)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun receivingANewRecoveryLinkCancelsThePreviousInFlightCompletion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = RecordingAuthRepository()
            val viewModel = MainViewModel(repository)
            var staleCallbackInvoked = false

            viewModel.acceptPasswordRecoveryLink(recoveryLink("first-one-time-hash"))
            viewModel.completePasswordRecovery("first-secure-password") {
                staleCallbackInvoked = true
            }
            runCurrent()
            assertTrue(repository.firstAttemptStarted.isCompleted)

            viewModel.acceptPasswordRecoveryLink(recoveryLink("second-one-time-hash"))
            runCurrent()

            assertTrue(repository.firstAttemptCancelled)
            assertFalse(staleCallbackInvoked)
            assertTrue(viewModel.passwordRecoveryState.value.isReadyForNewPassword)

            viewModel.completePasswordRecovery("second-secure-password")
            advanceUntilIdle()

            assertEquals("second-one-time-hash", repository.lastCompletedCredentials?.tokenHash)
            assertTrue(viewModel.passwordRecoveryState.value.isReadyForNewPassword)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun recoveryLink(tokenHash: String) =
        "com.aistudio.crashintelligence.vyfrip://auth/recovery?type=recovery&token_hash=$tokenHash"

    private class RecordingAuthRepository : AuthRepository {
        override val currentUser: Flow<UserAccount?> = flowOf(null)
        var lastCompletedCredentials: PasswordRecoveryCredentials? = null
        val firstAttemptStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        var firstAttemptCancelled = false

        override fun getCurrentUserSync(): UserAccount? = null

        override suspend fun signUp(
            email: String,
            password: String,
            displayName: String,
            organization: String
        ): Result<UserAccount> = Result.failure(UnsupportedOperationException())

        override suspend fun signIn(email: String, password: String): Result<UserAccount> =
            Result.failure(UnsupportedOperationException())

        override suspend fun signOut() = Unit

        override suspend fun requestPasswordRecovery(email: String): Result<Unit> = Result.success(Unit)

        override suspend fun completePasswordRecovery(
            credentials: PasswordRecoveryCredentials,
            newPassword: String
        ): Result<UserAccount> {
            lastCompletedCredentials = credentials
            if (credentials.tokenHash == "first-one-time-hash") {
                firstAttemptStarted.complete(Unit)
                try {
                    awaitCancellation()
                } catch (cancelled: CancellationException) {
                    firstAttemptCancelled = true
                    throw cancelled
                }
            }
            return Result.failure(UnsupportedOperationException("Test capture only"))
        }

        override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
    }
}
