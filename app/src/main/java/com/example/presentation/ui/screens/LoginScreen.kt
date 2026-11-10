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
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    val message: String
)

internal fun validateAuthForm(
    mode: AuthMode,
    email: String,
    password: String,
    displayName: String,
    confirmPassword: String
): AuthFormValidationError? {
    if (mode == AuthMode.RECOVER_PASSWORD) {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            return AuthFormValidationError(AuthFormField.EMAIL, "أدخل بريدًا إلكترونيًا صحيحًا.")
        }
        return null
    }
    if (mode == AuthMode.SET_NEW_PASSWORD) {
        if (password.length < 8) {
            return AuthFormValidationError(
                AuthFormField.PASSWORD,
                "كلمة المرور يجب أن تتكون من 8 أحرف على الأقل."
            )
        }
        if (password != confirmPassword) {
            return AuthFormValidationError(AuthFormField.CONFIRM_PASSWORD, "كلمتا المرور غير متطابقتين.")
        }
        return null
    }
    if (mode == AuthMode.SIGN_UP && displayName.isBlank()) {
        return AuthFormValidationError(AuthFormField.DISPLAY_NAME, "أدخل اسم المطور.")
    }
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
        return AuthFormValidationError(AuthFormField.EMAIL, "أدخل بريدًا إلكترونيًا صحيحًا.")
    }
    if (password.isBlank()) {
        return AuthFormValidationError(AuthFormField.PASSWORD, "أدخل كلمة المرور.")
    }
    if (mode == AuthMode.SIGN_UP && password.length < 8) {
        return AuthFormValidationError(
            AuthFormField.PASSWORD,
            "كلمة المرور يجب أن تتكون من 8 أحرف على الأقل."
        )
    }
    if (mode == AuthMode.SIGN_UP && password != confirmPassword) {
        return AuthFormValidationError(AuthFormField.CONFIRM_PASSWORD, "كلمتا المرور غير متطابقتين.")
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
        if (passwordRecoveryState.isReadyForNewPassword) {
            mode = AuthMode.SET_NEW_PASSWORD
            email = ""
            password = ""
            confirmPassword = ""
            validationError = null
            viewModel.clearMessages()
        }
    }

    val canSubmit = !actionState.isLoading

    val emailError = validationError?.takeIf { it.field == AuthFormField.EMAIL }?.message
    val passwordError = validationError?.takeIf { it.field == AuthFormField.PASSWORD }?.message
    val displayNameError = validationError?.takeIf { it.field == AuthFormField.DISPLAY_NAME }?.message
    val confirmPasswordError = validationError?.takeIf { it.field == AuthFormField.CONFIRM_PASSWORD }?.message

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF09162A), SpaceBackground, Color(0xFF080D1A))
                )
            )
    ) {
        SpaceGridBackground(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 22.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .size(94.dp)
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
                    contentDescription = "شعار الأمان",
                    tint = CyberCyan,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "VIBE CODING",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = VibeDesignTokens.Color.TextPrimary
            )
            Text(
                text = "ENGINEERING ACCESS GATE",
                style = MaterialTheme.typography.labelSmall,
                color = CyberCyan,
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "بوابة آمنة لإدارة مشاريعك وتحليل أعطالها",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

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
                        Text("SECURE AUTH FLOW", color = CyberCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Text("IDENTITY / 01", color = MutedText, style = MaterialTheme.typography.labelSmall)
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
                                text = when (mode) {
                                    AuthMode.SIGN_IN -> "تسجيل دخول المطور"
                                    AuthMode.SIGN_UP -> "إنشاء حساب جديد"
                                    AuthMode.RECOVER_PASSWORD -> "استعادة الوصول"
                                    AuthMode.SET_NEW_PASSWORD -> "تعيين كلمة مرور جديدة"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = VibeDesignTokens.Color.TextPrimary
                            )
                            Text(
                                text = "IDENTITY VERIFICATION",
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
                                "SECURE",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                color = CyberCyan,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    AnimatedVisibility(visible = validationError != null || actionState.errorMessage != null) {
                        val message = validationError?.message ?: actionState.errorMessage.orEmpty()
                        Surface(
                            color = Color(0x33FF4565),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VibeDesignTokens.Color.Error),
                            modifier = Modifier.fillMaxWidth()
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

                    AnimatedVisibility(visible = actionState.successMessage != null) {
                        Surface(
                            color = Color(0x2236E6A0),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VibeDesignTokens.Color.Success),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = VibeDesignTokens.Color.Success)
                                Text(
                                    actionState.successMessage.orEmpty(),
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
                            label = "اسم المطور",
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
                            label = "الفريق أو المؤسسة (اختياري)",
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
                        label = "البريد الإلكتروني",
                        icon = Icons.Default.Email,
                        focusRequester = emailFocusRequester,
                        keyboardType = KeyboardType.Email,
                        imeAction = if (mode == AuthMode.RECOVER_PASSWORD) ImeAction.Done else ImeAction.Next,
                        onNext = { passwordFocusRequester.requestFocus() },
                        onDone = { focusManager.clearFocus() },
                        enabled = !actionState.isLoading,
                        isError = emailError != null,
                        errorMessage = emailError
                    )

                    if (mode == AuthMode.RECOVER_PASSWORD) {
                        Text(
                            text = "أدخل بريدك الإلكتروني. إذا كان مرتبطًا بحساب، فسيصلك رابط آمن لفتح شاشة تعيين كلمة مرور جديدة.",
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
                            label = { Text(if (mode == AuthMode.SET_NEW_PASSWORD) "كلمة المرور الجديدة" else "كلمة المرور") },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = CyberCyan) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    enabled = !actionState.isLoading
                                ) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (passwordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
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
                            label = { Text("تأكيد كلمة المرور الجديدة".takeIf { mode == AuthMode.SET_NEW_PASSWORD } ?: "تأكيد كلمة المرور") },
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
                            text = "يجب ألا تقل كلمة المرور عن 8 أحرف وأن تتطابق حقول التأكيد.",
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                when (mode) {
                                    AuthMode.SIGN_IN -> "دخول إلى مساحة العمل"
                                    AuthMode.SIGN_UP -> "إنشاء الحساب"
                                    AuthMode.RECOVER_PASSWORD -> "إرسال رابط الاستعادة"
                                    AuthMode.SET_NEW_PASSWORD -> "حفظ كلمة المرور الجديدة"
                                },
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
                                    confirmPassword = ""
                                    validationError = null
                                    viewModel.clearMessages()
                                },
                                enabled = !actionState.isLoading
                            ) { Text("نسيت كلمة المرور؟", color = CyberCyan) }

                            TextButton(
                                onClick = {
                                    mode = AuthMode.SIGN_UP
                                    confirmPassword = ""
                                    validationError = null
                                    viewModel.clearMessages()
                                },
                                enabled = !actionState.isLoading
                            ) { Text("إنشاء حساب", color = CyberCyan, fontWeight = FontWeight.Bold) }
                        }
                    } else {
                        TextButton(
                            onClick = {
                                if (mode == AuthMode.SET_NEW_PASSWORD) {
                                    viewModel.cancelPasswordRecovery()
                                }
                                mode = AuthMode.SIGN_IN
                                confirmPassword = ""
                                validationError = null
                                viewModel.clearMessages()
                            },
                            enabled = !actionState.isLoading,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) { Text("العودة لتسجيل الدخول", color = CyberCyan) }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(CyberCyan))
                Text("بوابة المصادقة • اتصال آمن", color = MutedText, style = MaterialTheme.typography.labelSmall)
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
