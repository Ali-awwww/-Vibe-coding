package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.EmailConfirmationRequiredException
import com.example.domain.repository.PasswordRecoveryCredentials
import com.example.presentation.auth.PasswordRecoveryLinkResult
import com.example.presentation.auth.parsePasswordRecoveryLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PasswordRecoveryUiState(
    val isReadyForNewPassword: Boolean = false
)

data class AuthActionState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
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

    fun acceptPasswordRecoveryLink(rawLink: String) {
        when (val parsed = parsePasswordRecoveryLink(rawLink)) {
            is PasswordRecoveryLinkResult.Ready -> {
                pendingPasswordRecovery = parsed.credentials
                _passwordRecoveryState.value = PasswordRecoveryUiState(isReadyForNewPassword = true)
                _actionState.value = AuthActionState()
            }
            is PasswordRecoveryLinkResult.Rejected -> {
                pendingPasswordRecovery = null
                _passwordRecoveryState.value = PasswordRecoveryUiState()
                _actionState.value = AuthActionState(errorMessage = parsed.message)
            }
        }
    }

    fun cancelPasswordRecovery() {
        pendingPasswordRecovery = null
        _passwordRecoveryState.value = PasswordRecoveryUiState()
        _actionState.value = AuthActionState()
    }

    fun requestPasswordRecovery(email: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _actionState.value = AuthActionState(isLoading = true)
            val result = authRepository.requestPasswordRecovery(email.trim())
            if (result.isSuccess) {
                _actionState.value = AuthActionState(
                    successMessage = "إذا كان البريد مرتبطًا بحساب، فسيصلك رابط آمن لاستعادة كلمة المرور."
                )
                onComplete(true)
            } else {
                _actionState.value = AuthActionState(
                    errorMessage = result.exceptionOrNull()?.message ?: "تعذر طلب استعادة كلمة المرور."
                )
                onComplete(false)
            }
        }
    }

    fun completePasswordRecovery(newPassword: String, onComplete: (Boolean) -> Unit = {}) {
        val credentials = pendingPasswordRecovery
        if (credentials == null) {
            _actionState.value = AuthActionState(errorMessage = "رابط الاستعادة غير متاح. اطلب رابطًا جديدًا.")
            onComplete(false)
            return
        }
        viewModelScope.launch {
            _actionState.value = AuthActionState(isLoading = true)
            val result = authRepository.completePasswordRecovery(credentials, newPassword)
            if (result.isSuccess) {
                pendingPasswordRecovery = null
                _passwordRecoveryState.value = PasswordRecoveryUiState()
                _actionState.value = AuthActionState(
                    successMessage = "تم تحديث كلمة المرور والتحقق من الحساب بنجاح."
                )
                onComplete(true)
            } else {
                _actionState.value = AuthActionState(
                    errorMessage = result.exceptionOrNull()?.message ?: "تعذر تحديث كلمة المرور."
                )
                onComplete(false)
            }
        }
    }

    fun signIn(email: String, password: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _actionState.value = AuthActionState(isLoading = true)
            val result = authRepository.signIn(email, password)
            if (result.isSuccess) {
                _actionState.value = AuthActionState(isLoading = false, successMessage = "تم تسجيل الدخول بنجاح")
                onComplete(true)
            } else {
                _actionState.value = AuthActionState(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "فشل تسجيل الدخول"
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
                _actionState.value = AuthActionState(isLoading = false, successMessage = "تم إنشاء الحساب بنجاح")
                onComplete(true)
            } else {
                val failure = result.exceptionOrNull()
                if (failure is EmailConfirmationRequiredException) {
                    // Supabase accepted signup but did not issue a session; stay on auth screen.
                    _actionState.value = AuthActionState(
                        isLoading = false,
                        successMessage = failure.message
                            ?: "تم قبول طلب إنشاء الحساب. تحقق من بريدك الإلكتروني لتأكيده."
                    )
                } else {
                    _actionState.value = AuthActionState(
                        isLoading = false,
                        errorMessage = failure?.message ?: "فشل إنشاء الحساب"
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
                _actionState.value = AuthActionState(isLoading = false, successMessage = "تم تحديث كلمة المرور للحساب الحالي.")
                onComplete(true)
            } else {
                _actionState.value = AuthActionState(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "تعذر تغيير كلمة المرور"
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
