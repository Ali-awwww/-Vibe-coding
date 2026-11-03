package com.example.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.presentation.viewmodel.MainViewModel

enum class AuthMode {
    SIGN_IN, SIGN_UP, RECOVER_PASSWORD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onAuthSuccess: () -> Unit
) {
    val actionState by viewModel.actionState.collectAsState()
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }

    var email by remember { mutableStateOf("dev@crashintelligence.local") }
    var password by remember { mutableStateOf("DevPass123!") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(24.dp))

            // Logo & Title
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "بوابة المطورين الآمنة لمراقبة الانهيارات وتتبع الاستقرار",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Card Form Container
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Mode Title
                    Text(
                        text = when (mode) {
                            AuthMode.SIGN_IN -> "تسجيل دخول المطور"
                            AuthMode.SIGN_UP -> "إنشاء حساب مطور جديد"
                            AuthMode.RECOVER_PASSWORD -> "استعادة كلمة المرور"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Error Message Banner
                    AnimatedVisibility(visible = actionState.errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    actionState.errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // Success Message Banner
                    AnimatedVisibility(visible = actionState.successMessage != null) {
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32))
                                Text(
                                    actionState.successMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF1B5E20)
                                )
                            }
                        }
                    }

                    // Fields for SIGN_UP
                    if (mode == AuthMode.SIGN_UP) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("الاسم الكامل / اسم المطور") },
                            leadingIcon = { Icon(Icons.Default.Person, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = organization,
                            onValueChange = { organization = it },
                            label = { Text("المنظمة / اسم الفريق") },
                            leadingIcon = { Icon(Icons.Default.Business, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            viewModel.clearMessages()
                        },
                        label = { Text("البريد الإلكتروني المهني") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            viewModel.clearMessages()
                        },
                        label = {
                            Text(
                                if (mode == AuthMode.RECOVER_PASSWORD) "كلمة المرور الجديدة"
                                else "كلمة المرور"
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    null
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Confirm Password Field (For Sign Up or Reset)
                    if (mode == AuthMode.SIGN_UP || mode == AuthMode.RECOVER_PASSWORD) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("تأكيد كلمة المرور") },
                            leadingIcon = { Icon(Icons.Default.LockReset, null) },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Action Button
                    Button(
                        onClick = {
                            when (mode) {
                                AuthMode.SIGN_IN -> {
                                    viewModel.signIn(email, password) { success ->
                                        if (success) onAuthSuccess()
                                    }
                                }
                                AuthMode.SIGN_UP -> {
                                    if (password != confirmPassword) {
                                        // passwords don't match
                                        return@Button
                                    }
                                    viewModel.signUp(email, password, displayName, organization) { success ->
                                        if (success) onAuthSuccess()
                                    }
                                }
                                AuthMode.RECOVER_PASSWORD -> {
                                    if (password != confirmPassword) {
                                        return@Button
                                    }
                                    viewModel.resetPassword(email, password) { success ->
                                        if (success) {
                                            mode = AuthMode.SIGN_IN
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !actionState.isLoading && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (actionState.isLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                text = when (mode) {
                                    AuthMode.SIGN_IN -> "تسجيل الدخول"
                                    AuthMode.SIGN_UP -> "إنشاء الحساب وبدء الاستخدام"
                                    AuthMode.RECOVER_PASSWORD -> "تحديث كلمة المرور"
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    // Secondary switches
                    if (mode == AuthMode.SIGN_IN) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                mode = AuthMode.RECOVER_PASSWORD
                                viewModel.clearMessages()
                            }) {
                                Text("نسيت كلمة المرور؟", style = MaterialTheme.typography.bodySmall)
                            }

                            TextButton(onClick = {
                                mode = AuthMode.SIGN_UP
                                viewModel.clearMessages()
                            }) {
                                Text("إنشاء حساب مطور", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        TextButton(
                            onClick = {
                                mode = AuthMode.SIGN_IN
                                viewModel.clearMessages()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("العودة إلى تسجيل الدخول", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Demo Account Quick Access Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        email = "dev@crashintelligence.local"
                        password = "DevPass123!"
                        mode = AuthMode.SIGN_IN
                        viewModel.signIn("dev@crashintelligence.local", "DevPass123!") { success ->
                            if (success) onAuthSuccess()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("الدخول السريع كمهندس نظام (Demo)", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
