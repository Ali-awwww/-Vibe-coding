package com.example.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
    SIGN_IN, SIGN_UP, RECOVER_PASSWORD
}

@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onAuthSuccess: () -> Unit
) {
    val actionState by viewModel.actionState.collectAsState()
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val emailValid = remember(email) {
        android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    }
    val passwordsMatch = password == confirmPassword
    // Keep the primary action tappable so the form can explain validation failures.
    // Recovery stays disabled until its verified deep-link flow is implemented.
    val canSubmit = !actionState.isLoading && mode != AuthMode.RECOVER_PASSWORD

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
                        Box(Modifier.size(7.dp).clip(CircleShape).background(VibeDesignTokens.Color.Success))
                        Text("AUTH SYSTEM ONLINE", color = CyberCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
                        val message = validationError ?: actionState.errorMessage.orEmpty()
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
                            onValueChange = { displayName = it; validationError = null },
                            label = "اسم المطور",
                            icon = Icons.Default.Person
                        )
                        CyberTextField(
                            value = organization,
                            onValueChange = { organization = it; validationError = null },
                            label = "الفريق أو المؤسسة (اختياري)",
                            icon = Icons.Default.Business
                        )
                    }

                    CyberTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            validationError = null
                            viewModel.clearMessages()
                        },
                        label = "البريد الإلكتروني",
                        icon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email
                    )

                    if (mode == AuthMode.RECOVER_PASSWORD) {
                        Text(
                            text = "استعادة كلمة المرور غير مفعّلة بعد. لن نطلب كلمة مرور جديدة قبل إتمام التحقق من ملكية البريد الإلكتروني وربط رابط الاستعادة بالتطبيق.",
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
                            label = { Text("كلمة المرور") },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = CyberCyan) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (passwordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                                        tint = MutedText
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                            colors = cyberFieldColors()
                        )
                    }

                    if (mode == AuthMode.SIGN_UP) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; validationError = null },
                            label = { Text("تأكيد كلمة المرور") },
                            leadingIcon = { Icon(Icons.Default.LockReset, null, tint = CyberCyan) },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
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
                        onClick = {
                            validationError = when {
                                !emailValid -> "أدخل بريدًا إلكترونيًا صحيحًا."
                                password.isBlank() -> "أدخل كلمة المرور."
                                mode != AuthMode.SIGN_IN && password.length < 8 -> "كلمة المرور يجب أن تتكون من 8 أحرف على الأقل."
                                mode == AuthMode.SIGN_UP && displayName.isBlank() -> "أدخل اسم المطور."
                                mode != AuthMode.SIGN_IN && !passwordsMatch -> "كلمتا المرور غير متطابقتين."
                                else -> null
                            }
                            if (validationError == null) {
                                when (mode) {
                                    AuthMode.SIGN_IN -> viewModel.signIn(email.trim(), password) { success ->
                                        if (success) onAuthSuccess()
                                    }
                                    AuthMode.SIGN_UP -> viewModel.signUp(
                                        email.trim(), password, displayName.trim(), organization.trim()
                                    ) { success -> if (success) onAuthSuccess() }
                                    AuthMode.RECOVER_PASSWORD -> viewModel.resetPassword(email.trim(), password) { success ->
                                        if (success) {
                                            mode = AuthMode.SIGN_IN
                                            password = ""
                                            confirmPassword = ""
                                        }
                                    }
                                }
                            }
                        },
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
                                    AuthMode.RECOVER_PASSWORD -> "استعادة كلمة المرور غير متاحة حاليًا"
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
                            TextButton(onClick = {
                                mode = AuthMode.RECOVER_PASSWORD
                                confirmPassword = ""
                                validationError = null
                                viewModel.clearMessages()
                            }) { Text("نسيت كلمة المرور؟", color = CyberCyan) }

                            TextButton(onClick = {
                                mode = AuthMode.SIGN_UP
                                confirmPassword = ""
                                validationError = null
                                viewModel.clearMessages()
                            }) { Text("إنشاء حساب", color = CyberCyan, fontWeight = FontWeight.Bold) }
                        }
                    } else {
                        TextButton(
                            onClick = {
                                mode = AuthMode.SIGN_IN
                                confirmPassword = ""
                                validationError = null
                                viewModel.clearMessages()
                            },
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
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = CyberCyan) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
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
