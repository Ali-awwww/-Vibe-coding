package com.example

import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.EmailConfirmationRequiredException
import com.example.domain.repository.PasswordRecoveryCredentials
import com.example.presentation.viewmodel.AuthUiMessage
import com.example.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class MainViewModelAuthMessageTest {

    @Test
    fun signInFailureUsesLocalizedKeyInsteadOfProviderMessage() = runMainViewModelTest {
        val repository = RecordingAuthRepository().apply {
            signInResult = Result.failure(IllegalStateException("raw Supabase response detail"))
        }
        val viewModel = MainViewModel(repository)
        var completed: Boolean? = null

        viewModel.signIn("person@example.com", "incorrect-password") { completed = it }
        advanceUntilIdle()

        assertEquals(AuthUiMessage.SIGN_IN_FAILED, viewModel.actionState.value.errorMessage)
        assertNull(viewModel.actionState.value.successMessage)
        assertFalse(viewModel.actionState.value.toString().contains("raw Supabase response detail"))
        assertEquals(false, completed)
    }

    @Test
    fun networkFailureUsesSharedLocalizedNetworkMessage() = runMainViewModelTest {
        val repository = RecordingAuthRepository().apply {
            signInResult = Result.failure(IOException("socket detail"))
        }
        val viewModel = MainViewModel(repository)

        viewModel.signIn("person@example.com", "password") {}
        advanceUntilIdle()

        assertEquals(AuthUiMessage.NETWORK_UNAVAILABLE, viewModel.actionState.value.errorMessage)
    }

    @Test
    fun signupEmailConfirmationIsAResultMessageNotAProviderString() = runMainViewModelTest {
        val repository = RecordingAuthRepository().apply {
            signUpResult = Result.failure(
                EmailConfirmationRequiredException("raw repository confirmation text")
            )
        }
        val viewModel = MainViewModel(repository)
        var completed: Boolean? = null

        viewModel.signUp("person@example.com", "password123", "Developer", "") { completed = it }
        advanceUntilIdle()

        assertEquals(AuthUiMessage.EMAIL_CONFIRMATION_REQUIRED, viewModel.actionState.value.successMessage)
        assertNull(viewModel.actionState.value.errorMessage)
        assertEquals(false, completed)
    }

    @Test
    fun signupFailureDoesNotExposeProviderDetailsInUiState() = runMainViewModelTest {
        val providerDetail = "raw Supabase signup response detail"
        val repository = RecordingAuthRepository().apply {
            signUpResult = Result.failure(IllegalStateException(providerDetail))
        }
        val viewModel = MainViewModel(repository)

        viewModel.signUp("person@example.com", "password123", "Developer", "") {}
        advanceUntilIdle()

        assertEquals(AuthUiMessage.SIGN_UP_FAILED, viewModel.actionState.value.errorMessage)
        assertFalse(viewModel.actionState.value.toString().contains(providerDetail))
    }

    @Test
    fun recoveryRequestTrimsEmailAndUsesNonEnumeratingLocalizedSuccess() = runMainViewModelTest {
        val repository = RecordingAuthRepository()
        val viewModel = MainViewModel(repository)

        viewModel.requestPasswordRecovery(" person@example.com ")
        advanceUntilIdle()

        assertEquals("person@example.com", repository.requestedRecoveryEmail)
        assertEquals(
            AuthUiMessage.PASSWORD_RECOVERY_REQUEST_ACCEPTED,
            viewModel.actionState.value.successMessage
        )
    }

    @Test
    fun recoveryRequestNetworkFailureUsesLocalizedNetworkMessage() = runMainViewModelTest {
        val repository = RecordingAuthRepository().apply {
            recoveryRequestResult = Result.failure(IOException("socket detail"))
        }
        val viewModel = MainViewModel(repository)

        viewModel.requestPasswordRecovery("person@example.com")
        advanceUntilIdle()

        assertEquals(AuthUiMessage.NETWORK_UNAVAILABLE, viewModel.actionState.value.errorMessage)
    }

    @Test
    fun rejectedRecoveryLinkUsesLocalizedMessageKey() {
        val viewModel = MainViewModel(RecordingAuthRepository())

        viewModel.acceptPasswordRecoveryLink("https://example.com/not-a-recovery-link")

        assertEquals(AuthUiMessage.RECOVERY_LINK_INVALID, viewModel.actionState.value.errorMessage)
    }

    @Test
    fun missingRecoveryCredentialsUsesLocalizedMessageKey() {
        val viewModel = MainViewModel(RecordingAuthRepository())

        viewModel.completePasswordRecovery("new-password")

        assertEquals(AuthUiMessage.RECOVERY_LINK_UNAVAILABLE, viewModel.actionState.value.errorMessage)
    }

    @Test
    fun recoveryCompletionDoesNotExposeProviderFailureText() = runMainViewModelTest {
        val repository = RecordingAuthRepository().apply {
            recoveryCompletionResult = Result.failure(
                IllegalStateException("raw Supabase password update detail")
            )
        }
        val viewModel = MainViewModel(repository)
        viewModel.acceptPasswordRecoveryLink(
            "com.aistudio.crashintelligence.vyfrip://auth/recovery?type=recovery&token_hash=one-time-hash"
        )

        viewModel.completePasswordRecovery("new-password")
        advanceUntilIdle()

        assertEquals(AuthUiMessage.PASSWORD_RECOVERY_FAILED, viewModel.actionState.value.errorMessage)
    }

    @Test
    fun resetPasswordFailureUsesOperationSpecificLocalizedMessage() = runMainViewModelTest {
        val repository = RecordingAuthRepository().apply {
            resetPasswordResult = Result.failure(IllegalStateException("raw provider detail"))
        }
        val viewModel = MainViewModel(repository)

        viewModel.resetPassword("person@example.com", "new-password") {}
        advanceUntilIdle()

        assertEquals(AuthUiMessage.PASSWORD_UPDATE_FAILED, viewModel.actionState.value.errorMessage)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun runMainViewModelTest(block: suspend TestScope.() -> Unit) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        block()
    } finally {
        Dispatchers.resetMain()
    }
}

private class RecordingAuthRepository : AuthRepository {
    override val currentUser: Flow<UserAccount?> = flowOf(null)

    var signInResult: Result<UserAccount> = Result.failure(IllegalStateException("sign-in failed"))
    var signUpResult: Result<UserAccount> = Result.failure(IllegalStateException("sign-up failed"))
    var recoveryRequestResult: Result<Unit> = Result.success(Unit)
    var recoveryCompletionResult: Result<UserAccount> =
        Result.failure(IllegalStateException("password recovery failed"))
    var resetPasswordResult: Result<Unit> = Result.failure(IllegalStateException("password update failed"))
    var requestedRecoveryEmail: String? = null

    override fun getCurrentUserSync(): UserAccount? = null

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        organization: String
    ): Result<UserAccount> = signUpResult

    override suspend fun signIn(email: String, password: String): Result<UserAccount> = signInResult

    override suspend fun signOut() = Unit

    override suspend fun requestPasswordRecovery(email: String): Result<Unit> {
        requestedRecoveryEmail = email
        return recoveryRequestResult
    }

    override suspend fun completePasswordRecovery(
        credentials: PasswordRecoveryCredentials,
        newPassword: String
    ): Result<UserAccount> = recoveryCompletionResult

    override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> =
        resetPasswordResult

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())
}
