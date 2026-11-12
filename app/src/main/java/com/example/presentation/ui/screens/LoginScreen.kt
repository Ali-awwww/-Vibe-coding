package com.example.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.presentation.viewmodel.AuthUiMessage
import com.example.presentation.viewmodel.MainViewModel
import com.example.ui.theme.VibeDesignTokens

private val SpaceBackground = VibeDesignTokens.Color.SpaceBackground
private val SpacePanel = VibeDesignTokens.Color.SpacePanel
private val CyberCyan = VibeDesignTokens.Color.Cyan
private val CyberViolet = VibeDesignTokens.Color.Violet
private val MutedText = VibeDesignTokens.Color.TextMuted

enum class AuthMode {
    SIGN_IN, SIGN_UP, RECOVER_PASSWORD, SET_NEW_PASSWORD
}

internal enum class AuthFormField {
    EMAIL, PASSWORD, DISPLAY_NAME, CONFIRM_PASSWORD, FORM
}

internal data class AuthFormValidationError(
    val field: AuthFormField,
    val messageRes: Int
)

internal fun resolvePasswordRecoveryMode(
    currentMode: AuthMode,
    isReadyForNewPassword: Boolean
): AuthMode = when {
    isReadyForNewPassword -> AuthMode.SET_NEW_PASSWORD
    currentMode == AuthMode.SET_NEW_PASSWORD -> AuthMode.SIGN_IN
    else -> currentMode
}

internal fun shouldSubmitOnEmailIme(mode: AuthMode): Boolean = mode == AuthMode.RECOVER_PASSWORD

private fun authMessageResource(message: AuthUiMessage): Int = when (message) {
    AuthUiMessage.SIGN_IN_FAILED -> R.string.auth_error_sign_in
    AuthUiMessage.SIGN_UP_FAILED -> R.string.auth_error_sign_up
    AuthUiMessage.PASSWORD_RECOVERY_REQUEST_FAILED -> R.string.auth_error_recovery_request
    AuthUiMessage.PASSWORD_RECOVERY_FAILED -> R.string.auth_error_password_recovery
    AuthUiMessage.PASSWORD_UPDATE_FAILED -> R.string.auth_error_password_update
    AuthUiMessage.NETWORK_UNAVAILABLE -> R.string.auth_error_network
    AuthUiMessage.RECOVERY_LINK_INVALID -> R.string.auth_error_recovery_link_invalid
    AuthUiMessage.RECOVERY_LINK_UNAVAILABLE -> R.string.auth_error_recovery_link_unavailable
    AuthUiMessage.SIGN_IN_SUCCEEDED -> R.string.auth_success_sign_in
    AuthUiMessage.SIGN_UP_SUCCEEDED -> R.string.auth_success_sign_up
    AuthUiMessage.EMAIL_CONFIRMATION_REQUIRED -> R.string.auth_success_email_confirmation
    AuthUiMessage.PASSWORD_RECOVERY_REQUEST_ACCEPTED -> R.string.auth_success_recovery_request
    AuthUiMessage.PASSWORD_UPDATED -> R.string.auth_success_password_updated
}

internal fun validateAuthForm(
    mode: AuthMode,
    email: String,
    password: String,
    displayName: String,
    confirmPassword: String
): AuthFormValidationError? {
    if (mode == AuthMode.RECOVER_PASSWORD) {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            return AuthFormValidationError(AuthFormField.EMAIL, R.string.auth_validation_email)
        }
        return null
    }
    if (mode == AuthMode.SET_NEW_PASSWORD) {
        if (password.length < 8) {
            return AuthFormValidationError(
                AuthFormField.PASSWORD,
                R.string.auth_validation_password_min
            )
        }
        if (password != confirmPassword) {
            return AuthFormValidationError(AuthFormField.CONFIRM_PASSWORD, R.string.auth_validation_password_mismatch)
        }
        return null
    }
    if (mode == AuthMode.SIGN_UP && displayName.isBlank()) {
        return AuthFormValidationError(AuthFormField.DISPLAY_NAME, R.string.auth_validation_name)
    }
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
        return AuthFormValidationError(AuthFormField.EMAIL, R.string.auth_validation_email)
    }
    if (password.isBlank()) {
        return AuthFormValidationError(AuthFormField.PASSWORD, R.string.auth_validation_password_required)
    }
    if (mode == AuthMode.SIGN_UP && password.length < 8) {
        return AuthFormValidationError(
            AuthFormField.PASSWORD,
            R.string.auth_validation_password_min
        )
    }
    if (mode == AuthMode.SIGN_UP && password != confirmPassword) {
        return AuthFormValidationError(AuthFormField.CONFIRM_PASSWORD, R.string.auth_validation_password_mismatch)
    }
    return null
}

@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onAuthSuccess: () -> Unit
) {
    val actionState by viewModel.actionState.collectAsState()
    val passwordRecoveryState by viewModel.passwordRecoveryState.collectAsState()
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<AuthFormValidationError?>(null) }

    val focusManager = LocalFocusManager.current
    val displayNameFocusRequester = remember { FocusRequester() }
    val organizationFocusRequester = remember { FocusRequester() }
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmPasswordFocusRequester = remember { FocusRequester() }

    LaunchedEffect(passwordRecoveryState.isReadyForNewPassword) {
        val nextMode = resolvePasswordRecoveryMode(
            currentMode = mode,
            isReadyForNewPassword = passwordRecoveryState.isReadyForNewPassword
        )
        if (passwordRecoveryState.isReadyForNewPassword) {
            mode = nextMode
            email = ""
            password = ""
            confirmPassword = ""
            passwordVisible = false
            validationError = null
            viewModel.clearMessages()
        } else if (mode == AuthMode.SET_NEW_PASSWORD) {
            // A rejected/expired replacement link must not leave the form in a
            // password-update mode after its one-time credentials were discarded.
            mode = nextMode
            password = ""
            confirmPassword = ""
            passwordVisible = false
            validationError = null
        }
    }

    val canSubmit = !actionState.isLoading

    val emailError = validationError?.takeIf { it.field == AuthFormField.EMAIL }?.let { stringResource(it.messageRes) }
    val passwordError = validationError?.takeIf { it.field == AuthFormField.PASSWORD }?.let { stringResource(it.messageRes) }
    val displayNameError = validationError?.takeIf { it.field == AuthFormField.DISPLAY_NAME }?.let { stringResource(it.messageRes) }
    val confirmPasswordError = validationError?.takeIf { it.field == AuthFormField.CONFIRM_PASSWORD }?.let { stringResource(it.messageRes) }
    val actionErrorMessage = actionState.errorMessage?.let(::authMessageResource)?.let { stringResource(it) }
    val actionSuccessMessage = actionState.successMessage?.let(::authMessageResource)?.let { stringResource(it) }

    val submit: () -> Unit = {
        if (!actionState.isLoading) {
            val error = validateAuthForm(mode, email, password, displayName, confirmPassword)
            validationError = error
            if (error == null) {
                focusManager.clearFocus()
                when (mode) {
                    AuthMode.SIGN_IN -> viewModel.signIn(email.trim(), password) { success ->
                        if (success) onAuthSuccess()
                    }
                    AuthMode.SIGN_UP -> viewModel.signUp(
                        email.trim(), password, displayName.trim(), organization.trim()
                    ) { success ->
                        if (success) onAuthSuccess()
                    }
                    AuthMode.RECOVER_PASSWORD -> viewModel.requestPasswordRecovery(email.trim())
                    AuthMode.SET_NEW_PASSWORD -> viewModel.completePasswordRecovery(password) { success ->
                        if (success) onAuthSuccess()
                    }
                }
            } else {
                when (error.field) {
                    AuthFormField.EMAIL -> emailFocusRequester.requestFocus()
                    AuthFormField.PASSWORD -> passwordFocusRequester.requestFocus()
                    AuthFormField.DISPLAY_NAME -> displayNameFocusRequester.requestFocus()
                    AuthFormField.CONFIRM_PASSWORD -> confirmPasswordFocusRequester.requestFocus()
                    AuthFormField.FORM -> focusManager.clearFocus()
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF09162A), SpaceBackground, Color(0xFF080D1A))
                )
            )
    ) {
        val compactHeight = maxHeight < 760.dp
        val compactWidth = maxWidth < 360.dp
        SpaceGridBackground(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(
                    horizontal = if (compactWidth) 16.dp else 22.dp,
                    vertical = if (compactHeight) 16.dp else 30.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (compactHeight) Arrangement.Top else Arrangement.Center
        ) {
            Spacer(Modifier.height(if (compactHeight) 4.dp else 18.dp))

            Box(
                modifier = Modifier
                    .size(if (compactHeight) 76.dp else 94.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0x6630DDF5), Color(0x229B6BFF), Color.Transparent)
                        )
                    )
                    .border(1.dp, CyberCyan.copy(alpha = 0.75f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = stringResource(R.string.auth_security_logo),
                    tint = CyberCyan,
                    modifier = Modifier.size(if (compactHeight) 38.dp else 46.dp)
                )
            }

            Spacer(Modifier.height(if (compactHeight) 12.dp else 18.dp))
            Text(
                text = "VIBE CODING",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = VibeDesignTokens.Color.TextPrimary
            )
            Text(
                text = stringResource(R.string.auth_brand_eyebrow),
                style = MaterialTheme.typography.labelSmall,
                color = CyberCyan,
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(if (compactHeight) 16.dp else 28.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0x99101B2B),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(CyberCyan.copy(alpha = 0.72f), CyberViolet.copy(alpha = 0.58f), CyberCyan.copy(alpha = 0.28f))
                    )
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(CyberCyan))
                        Text(stringResource(R.string.auth_flow_label), color = CyberCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(stringResource(R.string.auth_identity_step), color = MutedText, style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(CyberCyan.copy(alpha = 0.65f), CyberViolet.copy(alpha = 0.48f), CyberCyan.copy(alpha = 0.18f))),
                        RoundedCornerShape(VibeDesignTokens.Shape.Panel)
                    ),
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
                colors = CardDefaults.cardColors(containerColor = SpacePanel),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(
                                    when (mode) {
                                        AuthMode.SIGN_IN -> R.string.auth_sign_in_title
                                        AuthMode.SIGN_UP -> R.string.auth_sign_up_title
                                        AuthMode.RECOVER_PASSWORD -> R.string.auth_recovery_title
                                        AuthMode.SET_NEW_PASSWORD -> R.string.auth_new_password_title
                                    }
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = VibeDesignTokens.Color.TextPrimary
                            )
                            Text(
                                text = stringResource(R.string.auth_identity_verification),
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan.copy(alpha = 0.85f)
                            )
                        }
                        Surface(
                            color = Color(0x2230DDF5),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f))
                        ) {
                            Text(
                                stringResource(R.string.auth_account_badge),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                color = CyberCyan,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    AnimatedVisibility(visible = validationError != null || actionErrorMessage != null) {
                        val message = validationError?.let { stringResource(it.messageRes) } ?: actionErrorMessage.orEmpty()
                        Surface(
                            color = Color(0x33FF4565),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VibeDesignTokens.Color.Error),
                            modifier = Modifier.fillMaxWidth().semantics {
                                liveRegion = LiveRegionMode.Polite
                            }
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF7189))
                                Text(message, color = Color(0xFFFFC1CC), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    AnimatedVisibility(visible = actionSuccessMessage != null) {
                        Surface(
                            color = Color(0x2236E6A0),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VibeDesignTokens.Color.Success),
                            modifier = Modifier.fillMaxWidth().semantics {
                                liveRegion = LiveRegionMode.Polite
                            }
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = VibeDesignTokens.Color.Success)
                                Text(
                                    actionSuccessMessage.orEmpty(),
                                    color = Color(0xFFB8F8D9),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    if (mode == AuthMode.SIGN_UP) {
                        CyberTextField(
                            value = displayName,
                            onValueChange = {
                                displayName = it
                                validationError = null
                                viewModel.clearMessages()
                            },
                            label = stringResource(R.string.auth_developer_name),
                            icon = Icons.Default.Person,
                            focusRequester = displayNameFocusRequester,
                            imeAction = ImeAction.Next,
                            onNext = { organizationFocusRequester.requestFocus() },
                            enabled = !actionState.isLoading,
                            isError = displayNameError != null,
                            errorMessage = displayNameError
                        )
                        CyberTextField(
                            value = organization,
                            onValueChange = {
                                organization = it
                                validationError = null
                                viewModel.clearMessages()
                            },
                            label = stringResource(R.string.auth_organization_optional),
                            icon = Icons.Default.Business,
                            focusRequester = organizationFocusRequester,
                            imeAction = ImeAction.Next,
                            onNext = { emailFocusRequester.requestFocus() },
                            enabled = !actionState.isLoading
                        )
                    }

                    if (mode != AuthMode.SET_NEW_PASSWORD) CyberTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            validationError = null
                            viewModel.clearMessages()
                        },
                        label = stringResource(R.string.auth_email),
                        icon = Icons.Default.Email,
                        focusRequester = emailFocusRequester,
                        keyboardType = KeyboardType.Email,
                        imeAction = if (mode == AuthMode.RECOVER_PASSWORD) ImeAction.Done else ImeAction.Next,
                        onNext = { passwordFocusRequester.requestFocus() },
                        onDone = {
                            if (shouldSubmitOnEmailIme(mode)) submit() else focusManager.clearFocus()
                        },
                        enabled = !actionState.isLoading,
                        isError = emailError != null,
                        errorMessage = emailError
                    )

                    if (mode == AuthMode.RECOVER_PASSWORD) {
                        Text(
                            text = stringResource(R.string.auth_recovery_instructions),
                            color = MutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                validationError = null
                                viewModel.clearMessages()
                            },
                            enabled = !actionState.isLoading,
                            isError = passwordError != null,
                            supportingText = passwordError?.let { message -> { Text(message) } },
                            label = {
                                Text(
                                    stringResource(
                                        if (mode == AuthMode.SET_NEW_PASSWORD) {
                                            R.string.auth_new_password
                                        } else {
                                            R.string.auth_password
                                        }
                                    )
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = CyberCyan) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    enabled = !actionState.isLoading
                                ) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = stringResource(
                                            if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password
                                        ),
                                        tint = MutedText
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (mode == AuthMode.SIGN_UP || mode == AuthMode.SET_NEW_PASSWORD) ImeAction.Next else ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { confirmPasswordFocusRequester.requestFocus() },
                                onDone = { submit() }
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                                .focusRequester(passwordFocusRequester)
                                .semantics { passwordError?.let { error(it) } },
                            shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                            colors = cyberFieldColors()
                        )
                    }

                    if (mode == AuthMode.SIGN_UP || mode == AuthMode.SET_NEW_PASSWORD) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                validationError = null
                                viewModel.clearMessages()
                            },
                            enabled = !actionState.isLoading,
                            isError = confirmPasswordError != null,
                            supportingText = confirmPasswordError?.let { message -> { Text(message) } },
                            label = {
                                Text(
                                    stringResource(
                                        if (mode == AuthMode.SET_NEW_PASSWORD) {
                                            R.string.auth_confirm_new_password
                                        } else {
                                            R.string.auth_confirm_password
                                        }
                                    )
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.LockReset, null, tint = CyberCyan) },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                                .focusRequester(confirmPasswordFocusRequester)
                                .semantics { confirmPasswordError?.let { error(it) } },
                            shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                            colors = cyberFieldColors()
                        )
                        Text(
                            text = stringResource(R.string.auth_password_requirements),
                            color = MutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = submit,
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VibeDesignTokens.Color.Cyan,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF244052),
                            disabledContentColor = MutedText
                        )
                    ) {
                        if (actionState.isLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    stringResource(R.string.auth_loading),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Text(
                                stringResource(
                                    when (mode) {
                                        AuthMode.SIGN_IN -> R.string.auth_submit_sign_in
                                        AuthMode.SIGN_UP -> R.string.auth_submit_sign_up
                                        AuthMode.RECOVER_PASSWORD -> R.string.auth_submit_recovery
                                        AuthMode.SET_NEW_PASSWORD -> R.string.auth_submit_new_password
                                    }
                                ),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (mode == AuthMode.SIGN_IN) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    mode = AuthMode.RECOVER_PASSWORD
                                    password = ""
                                    confirmPassword = ""
                                    passwordVisible = false
                                    validationError = null
                                    viewModel.clearMessages()
                                },
                                enabled = !actionState.isLoading
                            ) { Text(stringResource(R.string.auth_forgot_password), color = CyberCyan) }

                            TextButton(
                                onClick = {
                                    mode = AuthMode.SIGN_UP
                                    password = ""
                                    confirmPassword = ""
                                    passwordVisible = false
                                    validationError = null
                                    viewModel.clearMessages()
                                },
                                enabled = !actionState.isLoading
                            ) { Text(stringResource(R.string.auth_create_account), color = CyberCyan, fontWeight = FontWeight.Bold) }
                        }
                    } else {
                        TextButton(
                            onClick = {
                                if (mode == AuthMode.SET_NEW_PASSWORD) {
                                    viewModel.cancelPasswordRecovery()
                                    password = ""
                                    passwordVisible = false
                                }
                                mode = AuthMode.SIGN_IN
                                password = ""
                                confirmPassword = ""
                                passwordVisible = false
                                validationError = null
                                viewModel.clearMessages()
                            },
                            enabled = !actionState.isLoading,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) { Text(stringResource(R.string.auth_back_to_sign_in), color = CyberCyan) }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(CyberCyan))
                Text(stringResource(R.string.auth_provider_label), color = MutedText, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CyberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    focusRequester: FocusRequester,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        isError = isError,
        supportingText = errorMessage?.let { message -> { Text(message) } },
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = CyberCyan) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { onNext?.invoke() },
            onDone = { onDone?.invoke() }
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { if (isError && errorMessage != null) error(errorMessage) },
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
        colors = cyberFieldColors()
    )
}

@Composable
private fun cyberFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = VibeDesignTokens.Color.TextPrimary,
    unfocusedTextColor = VibeDesignTokens.Color.TextPrimary,
    focusedContainerColor = VibeDesignTokens.Color.SpaceBackground,
    unfocusedContainerColor = VibeDesignTokens.Color.SpaceBackground,
    focusedBorderColor = VibeDesignTokens.Color.Cyan,
    unfocusedBorderColor = Color(0xFF38536B),
    focusedLabelColor = CyberCyan,
    unfocusedLabelColor = MutedText,
    cursorColor = VibeDesignTokens.Color.Cyan
)

@Composable
private fun SpaceGridBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val spacing = 34.dp.toPx()
        val lineColor = CyberCyan.copy(alpha = 0.075f)
        var x = 0f
        while (x <= size.width) {
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            x += spacing
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            y += spacing
        }
        val center = Offset(size.width * 0.5f, size.height * 0.23f)
        listOf(70.dp, 112.dp, 154.dp).forEachIndexed { index, radius ->
            drawCircle(
                color = if (index % 2 == 0) CyberCyan.copy(alpha = 0.12f) else CyberViolet.copy(alpha = 0.12f),
                radius = radius.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(CyberCyan.copy(alpha = 0.12f), Color.Transparent),
                center = center,
                radius = 250.dp.toPx()
            ),
            radius = 250.dp.toPx(),
            center = center
        )
    }
}
