package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.EmailConfirmationRequiredException
import com.example.domain.repository.PasswordRecoveryCredentials
import com.example.domain.repository.SupabaseHttpException
import com.example.domain.repository.SupabaseUnconfiguredException
import com.example.presentation.auth.PasswordRecoveryLinkResult
import com.example.presentation.auth.parsePasswordRecoveryLink
import com.example.presentation.auth.isVerifiedEmailConfirmationLink
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PasswordRecoveryUiState(
    val isReadyForNewPassword: Boolean = false,
    /** Changes for every incoming auth link, even when readiness remains true. */
    val linkGeneration: Long = 0L
)

enum class AuthUiMessage {
    SIGN_IN_FAILED,
    SIGN_UP_FAILED,
    PASSWORD_RECOVERY_REQUEST_FAILED,
    PASSWORD_RECOVERY_FAILED,
    PASSWORD_UPDATE_FAILED,
    SUPABASE_NOT_CONFIGURED,
    NETWORK_UNAVAILABLE,
    NETWORK_DNS_FAILED,
    NETWORK_TIMEOUT,
    NETWORK_SSL_FAILED,
    NETWORK_CONNECTION_FAILED,
    RECOVERY_LINK_INVALID,
    RECOVERY_LINK_UNAVAILABLE,
    SIGN_IN_SUCCEEDED,
    SIGN_UP_SUCCEEDED,
    EMAIL_CONFIRMATION_REQUIRED,
    EMAIL_CONFIRMED_SIGN_IN,
    PASSWORD_RECOVERY_REQUEST_ACCEPTED,
    PASSWORD_UPDATED
}

data class AuthNetworkSnapshot(
    val endpointHost: String = "",
    val vpnActive: Boolean? = null,
    val networkValidated: Boolean? = null,
    val transportSummary: String? = null
)

data class AuthDiagnosticInfo(
    val code: String,
    val exceptionType: String,
    val endpointHost: String = "",
    val vpnActive: Boolean? = null,
    val networkValidated: Boolean? = null,
    val transportSummary: String? = null,
    val httpStatusCode: Int? = null
)

data class AuthActionState(
    val isLoading: Boolean = false,
    val errorMessage: AuthUiMessage? = null,
    val successMessage: AuthUiMessage? = null,
    val diagnostic: AuthDiagnosticInfo? = null
)

class MainViewModel(
    private val authRepository: AuthRepository,
    private val networkDiagnosticProvider: () -> AuthNetworkSnapshot = { AuthNetworkSnapshot() }
) : ViewModel() {

    val currentUser: StateFlow<UserAccount?> = authRepository.currentUser
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            authRepository.getCurrentUserSync()
        )

    private val _actionState = MutableStateFlow(AuthActionState())
    val actionState: StateFlow<AuthActionState> = _actionState.asStateFlow()

    private val _passwordRecoveryState = MutableStateFlow(PasswordRecoveryUiState())
    val passwordRecoveryState: StateFlow<PasswordRecoveryUiState> = _passwordRecoveryState.asStateFlow()
    private var pendingPasswordRecovery: PasswordRecoveryCredentials? = null
    private var passwordRecoveryGeneration = 0L
    private var passwordRecoveryLinkGeneration = 0L
    private var passwordRecoveryJob: Job? = null

    private fun resolveFailureState(
        error: Throwable?,
        fallback: AuthUiMessage
    ): AuthActionState {
        if (error is CancellationException) throw error
        val snapshot = runCatching { networkDiagnosticProvider() }.getOrDefault(AuthNetworkSnapshot())
        val causeChain = collectCauseChain(error)

        val unconfigured = causeChain.filterIsInstance<SupabaseUnconfiguredException>().firstOrNull()
        val dnsError = causeChain.filterIsInstance<UnknownHostException>().firstOrNull()
        val timeoutError = causeChain.filterIsInstance<SocketTimeoutException>().firstOrNull()
        val sslError = causeChain.filterIsInstance<SSLException>().firstOrNull()
        val connectionError = causeChain.firstOrNull { it is ConnectException || it is SocketException }
        val genericIoError = causeChain.filterIsInstance<IOException>().firstOrNull()
        val httpError = causeChain.filterIsInstance<SupabaseHttpException>().firstOrNull()

        val message: AuthUiMessage
        val code: String
        val reportedException: Throwable?

        when {
            unconfigured != null -> {
                message = AuthUiMessage.SUPABASE_NOT_CONFIGURED
                code = "SUPABASE_UNCONFIGURED"
                reportedException = unconfigured
            }
            dnsError != null -> {
                message = AuthUiMessage.NETWORK_DNS_FAILED
                code = "DNS_RESOLUTION_FAILED"
                reportedException = dnsError
            }
            timeoutError != null -> {
                message = AuthUiMessage.NETWORK_TIMEOUT
                code = "CONNECTION_TIMEOUT"
                reportedException = timeoutError
            }
            sslError != null -> {
                message = AuthUiMessage.NETWORK_SSL_FAILED
                code = "TLS_HANDSHAKE_FAILED"
                reportedException = sslError
            }
            connectionError != null -> {
                message = AuthUiMessage.NETWORK_CONNECTION_FAILED
                code = "CONNECTION_FAILED"
                reportedException = connectionError
            }
            genericIoError != null -> {
                message = AuthUiMessage.NETWORK_UNAVAILABLE
                code = "NETWORK_IO_ERROR"
                reportedException = genericIoError
            }
            httpError != null -> {
                message = fallback
                code = "AUTH_HTTP_${httpError.statusCode}"
                reportedException = httpError
            }
            else -> {
                message = fallback
                code = "AUTH_REJECTED"
                reportedException = error
            }
        }

        val resolvedHost = unconfigured?.endpointHost?.takeIf(String::isNotBlank)
            ?: snapshot.endpointHost

        return AuthActionState(
            isLoading = false,
            errorMessage = message,
            diagnostic = AuthDiagnosticInfo(
                code = code,
                exceptionType = reportedException?.javaClass?.simpleName ?: "UnknownError",
                endpointHost = resolvedHost,
                vpnActive = snapshot.vpnActive,
                networkValidated = snapshot.networkValidated,
                transportSummary = snapshot.transportSummary,
                httpStatusCode = httpError?.statusCode
            )
        )
    }

    private fun collectCauseChain(root: Throwable?): List<Throwable> {
        val result = mutableListOf<Throwable>()
        var current = root
        var depth = 0
        while (current != null && depth < 6 && current !in result) {
            result += current
            current = current.cause
            depth += 1
        }
        return result
    }

    fun acceptPasswordRecoveryLink(rawLink: String) {
        // A second link can arrive while the form is already in password-setup mode.
        // A distinct generation makes that replacement observable to Compose.
        val linkGeneration = ++passwordRecoveryLinkGeneration
        if (isVerifiedEmailConfirmationLink(rawLink)) {
            invalidatePasswordRecoveryAttempt()
            pendingPasswordRecovery = null
            _passwordRecoveryState.value = PasswordRecoveryUiState(linkGeneration = linkGeneration)
            _actionState.value = AuthActionState(
                successMessage = AuthUiMessage.EMAIL_CONFIRMED_SIGN_IN
            )
            return
        }
        invalidatePasswordRecoveryAttempt()
        when (val parsed = parsePasswordRecoveryLink(rawLink)) {
            is PasswordRecoveryLinkResult.Ready -> {
                pendingPasswordRecovery = parsed.credentials
                _passwordRecoveryState.value = PasswordRecoveryUiState(
                    isReadyForNewPassword = true,
                    linkGeneration = linkGeneration
                )
                _actionState.value = AuthActionState()
            }
            is PasswordRecoveryLinkResult.Rejected -> {
                pendingPasswordRecovery = null
                _passwordRecoveryState.value = PasswordRecoveryUiState(linkGeneration = linkGeneration)
                _actionState.value = AuthActionState(errorMessage = AuthUiMessage.RECOVERY_LINK_INVALID)
            }
        }
    }

    fun cancelPasswordRecovery() {
        invalidatePasswordRecoveryAttempt()
        pendingPasswordRecovery = null
        _passwordRecoveryState.value = PasswordRecoveryUiState(
            linkGeneration = passwordRecoveryLinkGeneration
        )
        _actionState.value = AuthActionState()
    }

    private fun invalidatePasswordRecoveryAttempt() {
        passwordRecoveryGeneration += 1L
        passwordRecoveryJob?.cancel()
        passwordRecoveryJob = null
    }

    fun requestPasswordRecovery(email: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _actionState.value = AuthActionState(isLoading = true)
            val result = authRepository.requestPasswordRecovery(email.trim())
            if (result.isSuccess) {
                _actionState.value = AuthActionState(
                    successMessage = AuthUiMessage.PASSWORD_RECOVERY_REQUEST_ACCEPTED
                )
                onComplete(true)
            } else {
                _actionState.value = resolveFailureState(
                    result.exceptionOrNull(),
                    AuthUiMessage.PASSWORD_RECOVERY_REQUEST_FAILED
                )
                onComplete(false)
            }
        }
    }

    fun completePasswordRecovery(newPassword: String, onComplete: (Boolean) -> Unit = {}) {
        val credentials = pendingPasswordRecovery
        if (credentials == null) {
            _actionState.value = AuthActionState(errorMessage = AuthUiMessage.RECOVERY_LINK_UNAVAILABLE)
            onComplete(false)
            return
        }

        passwordRecoveryJob?.cancel()
        val attemptGeneration = ++passwordRecoveryGeneration
        val attempt = viewModelScope.launch(start = CoroutineStart.LAZY) {
            _actionState.value = AuthActionState(isLoading = true)
            try {
                val result = authRepository.completePasswordRecovery(credentials, newPassword)
                if (attemptGeneration != passwordRecoveryGeneration ||
                    pendingPasswordRecovery != credentials
                ) {
                    return@launch
                }

                if (result.isSuccess) {
                    pendingPasswordRecovery = null
                    passwordRecoveryGeneration += 1L
                    passwordRecoveryJob = null
                    _passwordRecoveryState.value = PasswordRecoveryUiState(
                        linkGeneration = passwordRecoveryLinkGeneration
                    )
                    _actionState.value = AuthActionState(
                        successMessage = AuthUiMessage.PASSWORD_UPDATED
                    )
                    onComplete(true)
                } else {
                    _actionState.value = resolveFailureState(
                        result.exceptionOrNull(),
                        AuthUiMessage.PASSWORD_RECOVERY_FAILED
                    )
                    onComplete(false)
                }
            } finally {
                if (attemptGeneration == passwordRecoveryGeneration) {
                    passwordRecoveryJob = null
                }
            }
        }
        passwordRecoveryJob = attempt
        attempt.start()
    }

    fun signIn(email: String, password: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _actionState.value = AuthActionState(isLoading = true)
            val result = authRepository.signIn(email, password)
            if (result.isSuccess) {
                _actionState.value = AuthActionState(
                    isLoading = false,
                    successMessage = AuthUiMessage.SIGN_IN_SUCCEEDED
                )
                onComplete(true)
            } else {
                _actionState.value = resolveFailureState(
                    result.exceptionOrNull(),
                    AuthUiMessage.SIGN_IN_FAILED
                )
                onComplete(false)
            }
        }
    }

    fun signUp(
        email: String,
        password: String,
        displayName: String,
        organization: String,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _actionState.value = AuthActionState(isLoading = true)
            val result = authRepository.signUp(email, password, displayName, organization)
            if (result.isSuccess) {
                _actionState.value = AuthActionState(
                    isLoading = false,
                    successMessage = AuthUiMessage.SIGN_UP_SUCCEEDED
                )
                onComplete(true)
            } else {
                val failure = result.exceptionOrNull()
                if (failure is EmailConfirmationRequiredException) {
                    // Supabase accepted signup but did not issue a session; stay on auth screen.
                    _actionState.value = AuthActionState(
                        isLoading = false,
                        successMessage = AuthUiMessage.EMAIL_CONFIRMATION_REQUIRED
                    )
                } else {
                    _actionState.value = resolveFailureState(
                        failure,
                        AuthUiMessage.SIGN_UP_FAILED
                    )
                }
                onComplete(false)
            }
        }
    }

    fun resetPassword(email: String, newPass: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _actionState.value = AuthActionState(isLoading = true)
            val result = authRepository.resetPassword(email, newPass)
            if (result.isSuccess) {
                _actionState.value = AuthActionState(
                    isLoading = false,
                    successMessage = AuthUiMessage.PASSWORD_UPDATED
                )
                onComplete(true)
            } else {
                _actionState.value = resolveFailureState(
                    result.exceptionOrNull(),
                    AuthUiMessage.PASSWORD_UPDATE_FAILED
                )
                onComplete(false)
            }
        }
    }

    fun clearMessages() {
        _actionState.value = AuthActionState()
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
