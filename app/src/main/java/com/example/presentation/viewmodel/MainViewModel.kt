package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.EmailConfirmationRequiredException
import com.example.domain.repository.PasswordRecoveryCredentials
import com.example.presentation.auth.PasswordRecoveryLinkResult
import com.example.presentation.auth.parsePasswordRecoveryLink
import com.example.presentation.auth.isVerifiedEmailConfirmationLink
import java.io.IOException
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
    NETWORK_UNAVAILABLE,
    RECOVERY_LINK_INVALID,
    RECOVERY_LINK_UNAVAILABLE,
    SIGN_IN_SUCCEEDED,
    SIGN_UP_SUCCEEDED,
    EMAIL_CONFIRMATION_REQUIRED,
    EMAIL_CONFIRMED_SIGN_IN,
    PASSWORD_RECOVERY_REQUEST_ACCEPTED,
    PASSWORD_UPDATED
}

data class AuthActionState(
    val isLoading: Boolean = false,
    val errorMessage: AuthUiMessage? = null,
    val successMessage: AuthUiMessage? = null
)

class MainViewModel(
    private val authRepository: AuthRepository
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

    private fun resolveFailureMessage(error: Throwable?, fallback: AuthUiMessage): AuthUiMessage {
        if (error is CancellationException) throw error
        return if (error is IOException) AuthUiMessage.NETWORK_UNAVAILABLE else fallback
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
                _actionState.value = AuthActionState(
                    errorMessage = resolveFailureMessage(
                        result.exceptionOrNull(),
                        AuthUiMessage.PASSWORD_RECOVERY_REQUEST_FAILED
                    )
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
                    _actionState.value = AuthActionState(
                        errorMessage = resolveFailureMessage(
                            result.exceptionOrNull(),
                            AuthUiMessage.PASSWORD_RECOVERY_FAILED
                        )
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
                _actionState.value = AuthActionState(
                    isLoading = false,
                    errorMessage = resolveFailureMessage(
                        result.exceptionOrNull(),
                        AuthUiMessage.SIGN_IN_FAILED
                    )
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
                    _actionState.value = AuthActionState(
                        isLoading = false,
                        errorMessage = resolveFailureMessage(
                            failure,
                            AuthUiMessage.SIGN_UP_FAILED
                        )
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
                _actionState.value = AuthActionState(
                    isLoading = false,
                    errorMessage = resolveFailureMessage(
                        result.exceptionOrNull(),
                        AuthUiMessage.PASSWORD_UPDATE_FAILED
                    )
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
