package com.example

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.EmailConfirmationRequiredException
import com.example.domain.repository.FirebaseAuthNotConfiguredException
import com.example.domain.repository.PasswordRecoveryCredentials
import com.example.presentation.viewmodel.AuthNetworkSnapshot
import com.example.presentation.viewmodel.AuthUiMessage
import com.example.presentation.viewmodel.MainViewModel
import com.google.firebase.auth.FirebaseAuthException
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class MainViewModelAuthMessageTest {

    @Test
    fun signInFailureUsesLocalizedKeyInsteadOfProviderMessage() = runMainViewModelTest {
        val repository = RecordingAuthRepository().apply {
            signInResult = Result.failure(IllegalStateException("raw Firebase response detail"))
        }
        val viewModel = MainViewModel(repository)
        var completed: Boolean? = null

        viewModel.signIn("person@example.com", "incorrect-password") { completed = it }
        advanceUntilIdle()

        assertEquals(AuthUiMessage.SIGN_IN_FAILED, viewModel.actionState.value.errorMessage)
        assertNull(viewModel.actionState.value.successMessage)
        assertFalse(viewModel.actionState.value.toString().contains("raw Firebase response detail"))
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
        val providerDetail = "raw Firebase signup response detail"
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
                IllegalStateException("raw Firebase password update detail")
            )
        }
        val viewModel = MainViewModel(repository)
        viewModel.acceptPasswordRecoveryLink(
            "com.aistudio.crashintelligence.vyfrip://auth/recovery?mode=resetPassword&oobCode=one-time-oob"
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

    @Test
    fun unconfiguredFirebaseProducesConfigurationMessageAndDiagnostic() = runMainViewModelTest {
        val repository = RecordingAuthRepository().apply {
            signInResult = Result.failure(FirebaseAuthNotConfiguredException())
        }
        val viewModel = MainViewModel(repository) {
            AuthNetworkSnapshot(
                endpointHost = "firebaseauth.googleapis.com",
                vpnActive = true,
                networkValidated = true,
                transportSummary = "VPN + Cellular"
            )
        }

        viewModel.signIn("person@example.com", "password") {}
        advanceUntilIdle()

        val state = viewModel.actionState.value
        assertEquals(AuthUiMessage.FIREBASE_NOT_CONFIGURED, state.errorMessage)
        assertEquals("FIREBASE_UNCONFIGURED", state.diagnostic?.code)
        assertEquals("firebaseauth.googleapis.com", state.diagnostic?.endpointHost)
        assertEquals(true, state.diagnostic?.vpnActive)
    }

    @Test
    fun dnsTimeoutSslAndConnectionFailuresProduceSpecificDiagnosticCodes() = runMainViewModelTest {
        val snapshot = AuthNetworkSnapshot(
            endpointHost = "firebaseauth.googleapis.com",
            vpnActive = true,
            networkValidated = true,
            transportSummary = "VPN + Wi-Fi"
        )

        val cases = listOf(
            UnknownHostException("Unable to resolve host") to (AuthUiMessage.NETWORK_DNS_FAILED to "DNS_RESOLUTION_FAILED"),
            SocketTimeoutException("timeout") to (AuthUiMessage.NETWORK_TIMEOUT to "CONNECTION_TIMEOUT"),
            SSLHandshakeException("handshake failed") to (AuthUiMessage.NETWORK_SSL_FAILED to "TLS_HANDSHAKE_FAILED"),
            ConnectException("connection refused") to (AuthUiMessage.NETWORK_CONNECTION_FAILED to "CONNECTION_FAILED")
        )

        for ((exception, expected) in cases) {
            val repository = RecordingAuthRepository().apply {
                signInResult = Result.failure(exception)
            }
            val viewModel = MainViewModel(repository) { snapshot }
            viewModel.signIn("person@example.com", "password") {}
            advanceUntilIdle()

            val state = viewModel.actionState.value
            assertEquals(expected.first, state.errorMessage)
            assertEquals(expected.second, state.diagnostic?.code)
            assertEquals("firebaseauth.googleapis.com", state.diagnostic?.endpointHost)
            assertEquals("VPN + Wi-Fi", state.diagnostic?.transportSummary)
        }
    }

    @Test
    fun firebaseAuthFailurePreservesErrorCodeWithoutExposingRawUiMessage() = runMainViewModelTest {
        val repository = RecordingAuthRepository().apply {
            signInResult = Result.failure(
                FirebaseAuthException("ERROR_INVALID_CREDENTIAL", "raw invalid login credentials")
            )
        }
        val viewModel = MainViewModel(repository)

        viewModel.signIn("person@example.com", "password") {}
        advanceUntilIdle()

        val state = viewModel.actionState.value
        assertEquals(AuthUiMessage.SIGN_IN_FAILED, state.errorMessage)
        assertEquals("FIREBASE_ERROR_INVALID_CREDENTIAL", state.diagnostic?.code)
        assertTrue(state.diagnostic?.exceptionType == "FirebaseAuthException")
    }

    @Test
    fun missingGoogleCredentialUsesDedicatedLocalizedMessageAndDiagnostic() {
        val viewModel = MainViewModel(RecordingAuthRepository())

        viewModel.handleGoogleSignInFailure(NoCredentialException("No Google credential is available."))

        val state = viewModel.actionState.value
        assertEquals(AuthUiMessage.GOOGLE_NO_CREDENTIAL, state.errorMessage)
        assertEquals("GOOGLE_NO_CREDENTIAL", state.diagnostic?.code)
        assertEquals("NoCredentialException", state.diagnostic?.exceptionType)
    }

    @Test
    fun credentialCancellationIncludesCredentialDetailAndSigningTelemetry() = runMainViewModelTest {
        val repository = RecordingAuthRepository()
        val viewModel = MainViewModel(repository) {
            AuthNetworkSnapshot(
                endpointHost = "firebaseauth.googleapis.com",
                vpnActive = false,
                networkValidated = true,
                transportSummary = "Wi-Fi",
                appPackageName = "com.aistudio.crashintelligence.vyfrip",
                signingSha1 = "DA:AB:68:6E:5A:74:7D:03:B1:74:9D:8A:CE:40:F0:D7:A6:CA:41:B8",
                playServicesStatus = "AVAILABLE"
            )
        }

        viewModel.handleGoogleSignInFailure(
            GetCredentialCancellationException("activity is cancelled by the user.")
        )

        val state = viewModel.actionState.value
        assertEquals(AuthUiMessage.GOOGLE_SIGN_IN_CANCELLED, state.errorMessage)
        assertEquals("GOOGLE_SIGN_IN_CANCELLED", state.diagnostic?.code)
        assertEquals("GetCredentialCancellationException", state.diagnostic?.exceptionType)
        assertEquals(
            "android.credentials.GetCredentialException.TYPE_USER_CANCELED",
            state.diagnostic?.credentialErrorType
        )
        assertEquals("activity is cancelled by the user.", state.diagnostic?.errorDetail)
        assertEquals("com.aistudio.crashintelligence.vyfrip", state.diagnostic?.appPackageName)
        assertEquals(
            "DA:AB:68:6E:5A:74:7D:03:B1:74:9D:8A:CE:40:F0:D7:A6:CA:41:B8",
            state.diagnostic?.signingSha1
        )
        assertEquals("AVAILABLE", state.diagnostic?.playServicesStatus)
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
