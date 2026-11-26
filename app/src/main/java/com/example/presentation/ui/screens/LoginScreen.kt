package com.example.presentation.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.domain.model.UserAccount
import com.example.presentation.viewmodel.AuthActionState
import com.example.presentation.viewmodel.AuthDiagnosticInfo
import com.example.presentation.viewmodel.AuthUiMessage
import com.example.presentation.viewmodel.MainViewModel
import com.example.presentation.viewmodel.PasswordRecoveryUiState
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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

internal fun shouldNavigateToDashboardForRestoredSession(
    currentUser: UserAccount?,
    authMode: AuthMode,
    actionState: AuthActionState,
    passwordRecoveryState: PasswordRecoveryUiState
): Boolean =
    currentUser != null &&
        authMode == AuthMode.SIGN_IN &&
        !actionState.isLoading &&
        actionState.errorMessage == null &&
        actionState.successMessage == null &&
        !passwordRecoveryState.isReadyForNewPassword

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
    AuthUiMessage.FIREBASE_NOT_CONFIGURED -> R.string.auth_error_firebase_unconfigured
    AuthUiMessage.GOOGLE_SIGN_IN_CANCELLED -> R.string.auth_error_google_cancelled
    AuthUiMessage.GOOGLE_NO_CREDENTIAL -> R.string.auth_error_google_no_credential
    AuthUiMessage.GOOGLE_CREDENTIAL_UNAVAILABLE -> R.string.auth_error_google_unavailable
    AuthUiMessage.NETWORK_UNAVAILABLE -> R.string.auth_error_network
    AuthUiMessage.NETWORK_DNS_FAILED -> R.string.auth_error_network_dns
    AuthUiMessage.NETWORK_TIMEOUT -> R.string.auth_error_network_timeout
    AuthUiMessage.NETWORK_SSL_FAILED -> R.string.auth_error_network_ssl
    AuthUiMessage.NETWORK_CONNECTION_FAILED -> R.string.auth_error_network_connection
    AuthUiMessage.RECOVERY_LINK_INVALID -> R.string.auth_error_recovery_link_invalid
    AuthUiMessage.RECOVERY_LINK_UNAVAILABLE -> R.string.auth_error_recovery_link_unavailable
    AuthUiMessage.SIGN_IN_SUCCEEDED -> R.string.auth_success_sign_in
    AuthUiMessage.SIGN_UP_SUCCEEDED -> R.string.auth_success_sign_up
    AuthUiMessage.EMAIL_CONFIRMATION_REQUIRED -> R.string.auth_success_email_confirmation
    AuthUiMessage.EMAIL_CONFIRMED_SIGN_IN -> R.string.auth_success_email_confirmed
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun resolveWebClientId(context: Context): String? {
    val resId = context.resources.getIdentifier(
        "default_web_client_id",
        "string",
        context.packageName
    )
    if (resId == 0) return null
    return runCatching { context.getString(resId) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}

@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val actionState by viewModel.actionState.collectAsState()
    val networkSnapshot by viewModel.networkSnapshot.collectAsState()
    val passwordRecoveryState by viewModel.passwordRecoveryState.collectAsState()

    val actionErrorMessage = actionState.errorMessage?.let(::authMessageResource)?.let { stringResource(it) }
    val actionSuccessMessage = actionState.successMessage?.let(::authMessageResource)?.let { stringResource(it) }

    val currentUser by viewModel.currentUser.collectAsState()
    var silentSignInJob by remember { mutableStateOf<Job?>(null) }

    var authMode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var organization by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<AuthFormValidationError?>(null) }

    val authFieldShape = RoundedCornerShape(VibeDesignTokens.Shape.Control)
    val authFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = VibeDesignTokens.Color.TextPrimary,
        unfocusedTextColor = VibeDesignTokens.Color.TextPrimary,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedBorderColor = CyberCyan,
        unfocusedBorderColor = MutedText.copy(alpha = 0.55f),
        errorBorderColor = VibeDesignTokens.Color.Error,
        focusedLabelColor = CyberCyan,
        unfocusedLabelColor = MutedText,
        errorLabelColor = VibeDesignTokens.Color.Error,
        cursorColor = CyberCyan,
        errorCursorColor = VibeDesignTokens.Color.Error
    )

    LaunchedEffect(passwordRecoveryState.linkGeneration, passwordRecoveryState.isReadyForNewPassword) {
        // The initial empty state is not a recovery event; do not overwrite a user's mode choice.
        if (passwordRecoveryState.linkGeneration == 0L && !passwordRecoveryState.isReadyForNewPassword) {
            // A process-restored new-password mode has no recovery token to submit.
            // Preserve sign-up/recovery choices, but fail closed for this stale mode.
            if (authMode == AuthMode.SET_NEW_PASSWORD) {
                authMode = AuthMode.SIGN_IN
            }
            return@LaunchedEffect
        }
        authMode = resolvePasswordRecoveryMode(authMode, passwordRecoveryState.isReadyForNewPassword)
        validationError = null
    }

    LaunchedEffect(authMode) {
        // Passwords are deliberately not saveable and never survive a mode transition.
        password = ""
        confirmPassword = ""
        passwordVisible = false
    }

    LaunchedEffect(actionState.successMessage) {
        if (actionState.successMessage == AuthUiMessage.EMAIL_CONFIRMATION_REQUIRED) {
            password = ""
            confirmPassword = ""
            passwordVisible = false
        }
    }

    val completeAuthentication: (Boolean) -> Unit = { success ->
        if (success) {
            password = ""
            confirmPassword = ""
            passwordVisible = false
            onAuthSuccess()
        }
    }

    LaunchedEffect(
        currentUser,
        authMode,
        actionState.isLoading,
        actionState.errorMessage,
        actionState.successMessage,
        passwordRecoveryState.linkGeneration,
        passwordRecoveryState.isReadyForNewPassword
    ) {
        if (
            shouldNavigateToDashboardForRestoredSession(
                currentUser = currentUser,
                authMode = authMode,
                actionState = actionState,
                passwordRecoveryState = passwordRecoveryState
            )
        ) {
            silentSignInJob?.cancel()
            silentSignInJob = null
            onAuthSuccess()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshNetworkDiagnostics()
        if (currentUser != null) return@LaunchedEffect
        val webClientId = resolveWebClientId(context) ?: return@LaunchedEffect
        val activityContext = context.findActivity() ?: return@LaunchedEffect
        val job = coroutineScope.launch {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setAutoSelectEnabled(true)
                    .setServerClientId(webClientId)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = CredentialManager.create(activityContext)
                    .getCredential(request = request, context = activityContext)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    viewModel.signInWithGoogleIdToken(googleIdTokenCredential.idToken) { success ->
                        if (success) onAuthSuccess()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: NoCredentialException) {
                Log.d("Auth", "No previously authorized Google credential is available.")
            } catch (e: GetCredentialException) {
                Log.w("Auth", "Silent Google Sign-In unavailable (${e.type}): ${e.message}", e)
            } catch (e: Exception) {
                Log.w("Auth", "Silent Google Sign-In skipped: ${e.message}", e)
            }
        }
        silentSignInJob = job
        job.join()
    }

    val submitEmailAuth: () -> Unit = {
        if (!actionState.isLoading) {
            val error = validateAuthForm(
                mode = authMode,
                email = email,
                password = password,
                displayName = displayName,
                confirmPassword = confirmPassword
            )
            validationError = error
            if (error == null) {
                viewModel.clearMessages()
                when (authMode) {
                    AuthMode.SIGN_IN -> viewModel.signIn(email.trim(), password, completeAuthentication)
                    AuthMode.SIGN_UP -> viewModel.signUp(
                        email = email.trim(),
                        password = password,
                        displayName = displayName.trim(),
                        organization = organization.trim(),
                        onComplete = completeAuthentication
                    )
                    AuthMode.RECOVER_PASSWORD -> viewModel.requestPasswordRecovery(email.trim()) { success ->
                        if (success) authMode = AuthMode.SIGN_IN
                    }
                    AuthMode.SET_NEW_PASSWORD -> viewModel.completePasswordRecovery(password, completeAuthentication)
                }
            }
        }
    }

    val launchGoogleSignIn: () -> Unit = {
        if (!actionState.isLoading) {
            silentSignInJob?.cancel()
            silentSignInJob = null
            val webClientId = resolveWebClientId(context)
            val activityContext = context.findActivity()
            if (webClientId == null) {
                viewModel.handleGoogleSignInFailure(
                    IllegalStateException("Firebase default_web_client_id is missing.")
                )
            } else if (activityContext == null) {
                viewModel.handleGoogleSignInFailure(
                    IllegalStateException("Google Sign-In requires an Activity context.")
                )
            } else {
                viewModel.setAuthLoading(true)
                coroutineScope.launch {
                    try {
                        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
                            .build()
                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(signInWithGoogleOption)
                            .build()
                        val result = CredentialManager.create(activityContext)
                            .getCredential(request = request, context = activityContext)
                        val credential = result.credential
                        if (credential is CustomCredential &&
                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                        ) {
                            val googleIdTokenCredential =
                                GoogleIdTokenCredential.createFrom(credential.data)
                            viewModel.signInWithGoogleIdToken(googleIdTokenCredential.idToken) { success ->
                                if (success) onAuthSuccess()
                            }
                        } else {
                            viewModel.handleGoogleSignInFailure(
                                IllegalStateException("Unexpected credential type: ${credential.type}")
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        viewModel.setAuthLoading(false)
                        throw cancelled
                    } catch (e: NoCredentialException) {
                        Log.w("Auth", "No Google credential is available for this account.", e)
                        viewModel.handleGoogleSignInFailure(e)
                    } catch (e: GetCredentialCancellationException) {
                        Log.w("Auth", "Google Sign-In cancelled (${e.type}): ${e.message}", e)
                        viewModel.handleGoogleSignInFailure(e)
                    } catch (e: Exception) {
                        Log.e("Auth", "Google Sign-In failed: ${e.message}", e)
                        viewModel.handleGoogleSignInFailure(e)
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("login_root")
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
                color = CyberCyan
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(if (compactHeight) 16.dp else 24.dp))

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
                    .testTag("auth_form_card")
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
                    modifier = Modifier.padding(if (compactWidth) 16.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val cardTitleRes = when (authMode) {
                        AuthMode.SIGN_IN -> R.string.auth_sign_in_title
                        AuthMode.SIGN_UP -> R.string.auth_sign_up_title
                        AuthMode.RECOVER_PASSWORD -> R.string.auth_recovery_title
                        AuthMode.SET_NEW_PASSWORD -> R.string.auth_new_password_title
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(cardTitleRes),
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

                    Text(
                        text = stringResource(
                            if (authMode == AuthMode.RECOVER_PASSWORD) {
                                R.string.auth_recovery_instructions
                            } else {
                                R.string.auth_google_subtitle
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )

                    val networkSummary = networkSnapshot.transportSummary
                        ?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.auth_diag_unknown)
                    Surface(
                        color = VibeDesignTokens.Color.SpaceBackground.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (networkSnapshot.vpnActive == true) {
                                VibeDesignTokens.Color.Warning.copy(alpha = 0.65f)
                            } else {
                                CyberCyan.copy(alpha = 0.25f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_network_status_strip")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (networkSnapshot.vpnActive == true) Icons.Default.VpnKey else Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (networkSnapshot.vpnActive == true) {
                                        VibeDesignTokens.Color.Warning
                                    } else {
                                        CyberCyan
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${stringResource(R.string.auth_network_status_prefix)} $networkSummary",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMono,
                                    color = VibeDesignTokens.Color.TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.refreshNetworkDiagnostics() },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("refresh_network_diagnostics_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.auth_network_status_prefix),
                                        tint = CyberCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (networkSnapshot.vpnActive == true) {
                                Text(
                                    text = stringResource(R.string.auth_vpn_notice),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VibeDesignTokens.Color.Warning
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = actionErrorMessage != null) {
                        val diagnostic = actionState.diagnostic
                        Surface(
                            color = Color(0x33FF4565),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VibeDesignTokens.Color.Error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_error_banner")
                                .semantics {
                                    liveRegion = LiveRegionMode.Polite
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF7189))
                                    Text(
                                        actionErrorMessage.orEmpty(),
                                        color = Color(0xFFFFC1CC),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (diagnostic != null) {
                                    AuthDiagnosticPanel(diagnostic = diagnostic)
                                }
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

                    if (authMode == AuthMode.SIGN_IN) {
                        Button(
                            onClick = launchGoogleSignIn,
                            enabled = !actionState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("google_sign_in_button"),
                            shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VibeDesignTokens.Color.Cyan,
                                contentColor = VibeDesignTokens.Color.OnCyan,
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
                                        color = VibeDesignTokens.Color.OnCyan,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        stringResource(R.string.auth_loading),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null
                                    )
                                    Text(
                                        stringResource(R.string.auth_google_sign_in),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = CyberCyan.copy(alpha = 0.2f))
                            Text(
                                text = stringResource(R.string.auth_or_email),
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = CyberCyan.copy(alpha = 0.2f))
                        }
                    }

                    if (authMode == AuthMode.SIGN_UP) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = {
                                displayName = it
                                if (validationError?.field == AuthFormField.DISPLAY_NAME) validationError = null
                            },
                            label = { Text(stringResource(R.string.auth_developer_name)) },
                            singleLine = true,
                            shape = authFieldShape,
                            colors = authFieldColors,
                            isError = validationError?.field == AuthFormField.DISPLAY_NAME,
                            supportingText = validationError?.takeIf { it.field == AuthFormField.DISPLAY_NAME }?.let { err ->
                                { Text(stringResource(err.messageRes)) }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("display_name_input")
                        )

                        OutlinedTextField(
                            value = organization,
                            onValueChange = { organization = it },
                            label = { Text(stringResource(R.string.auth_organization_optional)) },
                            singleLine = true,
                            shape = authFieldShape,
                            colors = authFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("organization_input")
                        )
                    }

                    if (authMode != AuthMode.SET_NEW_PASSWORD) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                if (validationError?.field == AuthFormField.EMAIL) validationError = null
                            },
                            label = { Text(stringResource(R.string.auth_email)) },
                            singleLine = true,
                            shape = authFieldShape,
                            colors = authFieldColors,
                            isError = validationError?.field == AuthFormField.EMAIL,
                            supportingText = validationError?.takeIf { it.field == AuthFormField.EMAIL }?.let { err ->
                                { Text(stringResource(err.messageRes)) }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = if (shouldSubmitOnEmailIme(authMode)) ImeAction.Done else ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                onDone = {
                                    if (shouldSubmitOnEmailIme(authMode)) {
                                        focusManager.clearFocus()
                                        submitEmailAuth()
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("email_input")
                        )
                    }

                    if (authMode != AuthMode.RECOVER_PASSWORD) {
                        val passwordLabelRes = if (authMode == AuthMode.SET_NEW_PASSWORD) {
                            R.string.auth_new_password
                        } else {
                            R.string.auth_password
                        }
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                if (validationError?.field == AuthFormField.PASSWORD) validationError = null
                            },
                            label = { Text(stringResource(passwordLabelRes)) },
                            singleLine = true,
                            shape = authFieldShape,
                            colors = authFieldColors,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("auth_password_visibility_button")
                                ) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = stringResource(
                                            if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password
                                        )
                                    )
                                }
                            },
                            isError = validationError?.field == AuthFormField.PASSWORD,
                            supportingText = validationError?.takeIf { it.field == AuthFormField.PASSWORD }?.let { err ->
                                { Text(stringResource(err.messageRes)) }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (authMode == AuthMode.SIGN_IN) ImeAction.Done else ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                onDone = {
                                    if (authMode == AuthMode.SIGN_IN) {
                                        focusManager.clearFocus()
                                        submitEmailAuth()
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input")
                        )
                    }

                    if (authMode == AuthMode.SIGN_UP || authMode == AuthMode.SET_NEW_PASSWORD) {
                        val confirmLabelRes = if (authMode == AuthMode.SET_NEW_PASSWORD) {
                            R.string.auth_confirm_new_password
                        } else {
                            R.string.auth_confirm_password
                        }
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                if (validationError?.field == AuthFormField.CONFIRM_PASSWORD) validationError = null
                            },
                            label = { Text(stringResource(confirmLabelRes)) },
                            singleLine = true,
                            shape = authFieldShape,
                            colors = authFieldColors,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            isError = validationError?.field == AuthFormField.CONFIRM_PASSWORD,
                            supportingText = validationError?.takeIf { it.field == AuthFormField.CONFIRM_PASSWORD }?.let { err ->
                                { Text(stringResource(err.messageRes)) }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    submitEmailAuth()
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_password_input")
                        )
                    }

                    val submitLabelRes = when (authMode) {
                        AuthMode.SIGN_IN -> R.string.auth_submit_sign_in
                        AuthMode.SIGN_UP -> R.string.auth_submit_sign_up
                        AuthMode.RECOVER_PASSWORD -> R.string.auth_submit_recovery
                        AuthMode.SET_NEW_PASSWORD -> R.string.auth_submit_new_password
                    }

                    OutlinedButton(
                        onClick = submitEmailAuth,
                        enabled = !actionState.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("auth_submit_button"),
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = stringResource(submitLabelRes),
                            color = VibeDesignTokens.Color.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (authMode == AuthMode.SIGN_IN) {
                            TextButton(
                                onClick = {
                                    viewModel.clearMessages()
                                    validationError = null
                                    authMode = AuthMode.RECOVER_PASSWORD
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("forgot_password_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.auth_forgot_password),
                                    color = CyberCyan,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            TextButton(
                                onClick = {
                                    viewModel.clearMessages()
                                    validationError = null
                                    authMode = AuthMode.SIGN_UP
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("toggle_auth_mode_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.auth_create_account),
                                    color = CyberViolet,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    if (authMode == AuthMode.SET_NEW_PASSWORD) {
                                        viewModel.cancelPasswordRecovery()
                                    } else {
                                        viewModel.clearMessages()
                                    }
                                    validationError = null
                                    authMode = AuthMode.SIGN_IN
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("back_to_sign_in_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.auth_back_to_sign_in),
                                    color = CyberCyan,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
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
private fun AuthDiagnosticPanel(diagnostic: AuthDiagnosticInfo) {
    val clipboardManager = LocalClipboardManager.current
    var expanded by remember(diagnostic) { mutableStateOf(false) }
    var copied by remember(diagnostic) { mutableStateOf(false) }

    val vpnLabel = when (diagnostic.vpnActive) {
        true -> stringResource(R.string.auth_diag_vpn_active)
        false -> stringResource(R.string.auth_diag_vpn_inactive)
        null -> stringResource(R.string.auth_diag_unknown)
    }
    val networkText = buildString {
        append(diagnostic.transportSummary?.takeIf(String::isNotBlank) ?: stringResource(R.string.auth_diag_unknown))
        if (diagnostic.networkValidated == false) {
            append(" (")
            append(stringResource(R.string.auth_diag_unvalidated_warning))
            append(")")
        }
    }
    val summaryText = buildString {
        append("code=${diagnostic.code}")
        append("\nexception=${diagnostic.exceptionType}")
        diagnostic.credentialErrorType?.let { append("\ntype=$it") }
        diagnostic.errorDetail?.let { append("\ndetail=$it") }
        diagnostic.httpStatusCode?.let { append("\nhttp=$it") }
        if (diagnostic.appPackageName.isNotBlank()) {
            append("\npackage=${diagnostic.appPackageName}")
        }
        diagnostic.signingSha1?.let { append("\nsha1=$it") }
        diagnostic.playServicesStatus?.let { append("\ngms=$it") }
        if (diagnostic.endpointHost.isNotBlank()) {
            append("\nhost=${diagnostic.endpointHost}")
        }
        append("\nvpn=${diagnostic.vpnActive ?: "unknown"}")
        append("\nnetwork=${diagnostic.transportSummary ?: "unknown"}")
        if (diagnostic.networkValidated != null) {
            append("\nvalidated=${diagnostic.networkValidated}")
        }
    }

    Surface(
        color = VibeDesignTokens.Color.SpaceBackground.copy(alpha = 0.85f),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_diagnostic_panel")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("toggle_auth_diagnostics_button")
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.auth_diag_hide else R.string.auth_diag_show
                        ),
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.auth_diag_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(summaryText))
                        copied = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("copy_auth_diagnostics_button")
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = stringResource(
                            if (copied) R.string.auth_diag_copied else R.string.auth_diag_copy
                        ),
                        tint = if (copied) VibeDesignTokens.Color.Success else CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(if (copied) R.string.auth_diag_copied else R.string.auth_diag_copy),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (copied) VibeDesignTokens.Color.Success else CyberCyan
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DiagnosticLine(stringResource(R.string.auth_diag_code_label), diagnostic.code)
                    DiagnosticLine(stringResource(R.string.auth_diag_exception_label), diagnostic.exceptionType)
                    diagnostic.credentialErrorType?.let {
                        DiagnosticLine(stringResource(R.string.auth_diag_type_label), it)
                    }
                    diagnostic.errorDetail?.let {
                        DiagnosticLine(stringResource(R.string.auth_diag_detail_label), it)
                    }
                    diagnostic.httpStatusCode?.let {
                        DiagnosticLine(stringResource(R.string.auth_diag_http_label), it.toString())
                    }
                    if (diagnostic.appPackageName.isNotBlank()) {
                        DiagnosticLine(stringResource(R.string.auth_diag_package_label), diagnostic.appPackageName)
                    }
                    diagnostic.signingSha1?.let {
                        DiagnosticLine(stringResource(R.string.auth_diag_sha1_label), it)
                    }
                    diagnostic.playServicesStatus?.let {
                        DiagnosticLine(stringResource(R.string.auth_diag_gms_label), it)
                    }
                    if (diagnostic.endpointHost.isNotBlank()) {
                        DiagnosticLine(stringResource(R.string.auth_diag_host_label), diagnostic.endpointHost)
                    }
                    DiagnosticLine(stringResource(R.string.auth_diag_vpn_label), vpnLabel)
                    DiagnosticLine(stringResource(R.string.auth_diag_network_label), networkText)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MutedText
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = value,
                fontFamily = JetBrainsMono,
                style = MaterialTheme.typography.labelSmall,
                color = VibeDesignTokens.Color.TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

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
