package com.example.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.example.presentation.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.ui.platform.testTag


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onSignOut: () -> Unit,
    navController: NavController
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showSignOutDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val profileName = currentUser?.displayName?.takeIf(String::isNotBlank)
        ?: currentUser?.email?.substringBefore('@')?.takeIf(String::isNotBlank)
        ?: stringResource(
            if (currentUser == null) R.string.settings_profile_not_signed_in
            else R.string.settings_profile_name_unavailable
        )
    val profileEmail = currentUser?.email?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.settings_profile_email_unavailable)
    val profileInitial = currentUser?.displayName?.takeIf(String::isNotBlank)?.firstOrNull()?.uppercaseChar()?.toString()
        ?: currentUser?.email?.takeIf(String::isNotBlank)?.firstOrNull()?.uppercaseChar()?.toString()
        ?: "—"
    val lastLoginValue = currentUser?.lastLoginAt?.takeIf { it > 0L }
        ?.let { dateFormat.format(Date(it)) }
        ?: stringResource(R.string.settings_last_login_unavailable)
    val sessionStatus = stringResource(
        if (currentUser != null) R.string.settings_authenticated_session
        else R.string.settings_no_active_session
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعدادات الحساب والأمان") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card
            Text(stringResource(R.string.settings_profile_section_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    profileInitial,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                profileName,
                                modifier = Modifier.testTag("settings_profile_name"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                profileEmail,
                                modifier = Modifier.testTag("settings_profile_email"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!currentUser?.organization.isNullOrBlank()) {
                                Text(
                                    "المنظمة: ${currentUser?.organization}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_last_login, lastLoginValue),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("settings_last_login"),
                            color = MaterialTheme.colorScheme.outline
                        )

                        Surface(
                            color = if (currentUser != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(if (currentUser != null) Icons.Default.VerifiedUser else Icons.Default.Info, null, modifier = Modifier.size(14.dp))
                                Text(
                                    sessionStatus,
                                    modifier = Modifier.testTag("settings_session_status"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Security & Privacy System Status
            Text("حالة نظام الأمان والتشفير", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SecurityStatusRow(
                        title = "تشفير مفاتيح API",
                        description = "مشفرة بنظام AES-GCM (256-bit) عند التخزين",
                        statusText = "نشط",
                        isGood = true
                    )
                    HorizontalDivider()
                    SecurityStatusRow(
                        title = "عزل بيانات المطورين",
                        description = "كل مطور وفريق يصل لمشروعاته المصرح بها فقط",
                        statusText = "محمي",
                        isGood = true
                    )
                    HorizontalDivider()
                    SecurityStatusRow(
                        title = "تنقيح البيانات الشخصية (PII)",
                        description = "حجب تلقائي لكلمات المرور والرموز والبريد وIP",
                        statusText = "مفعّل",
                        isGood = true
                    )
                    HorizontalDivider()
                    SecurityStatusRow(
                        title = "التحقق من توقيع Webhooks",
                        description = "التحقق من توقيعات HMAC-SHA256 الصادرة من المصادر",
                        statusText = "مطبق",
                        isGood = true
                    )
                    HorizontalDivider()
                    SecurityStatusRow(
                        title = "حماية معدل الطلبات (Rate Limiting)",
                        description = "حد أقصى 60 طلب/دقيقة لمنع هجمات الحرمان من الخدمة",
                        statusText = "مفعّل",
                        isGood = true
                    )
                }
            }

            // Developer Sandbox
            Text("بيئة الاختبار والتطوير (Sandbox)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "تحميل بيانات الاختبار معطّل حالياً؛ لم تُجهّز قاعدة بيانات محلية منفصلة عن حسابك. لن تُدرج سجلات تجريبية في مشاريعك أو قاعدة بياناتك الفعلية.",
                        modifier = Modifier.fillMaxWidth().testTag("settings_sandbox_disabled"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { navController.navigate(com.example.presentation.navigation.Screen.ProductQualityCenter) },
                        modifier = Modifier.fillMaxWidth().testTag("settings_open_quality_center")
                    ) {
                        Icon(Icons.Default.FactCheck, null)
                        Spacer(Modifier.width(8.dp))
                        Text("مركز جودة المنتج وبوابة الاعتماد (Release Gate)")
                    }

                }
            }


            Spacer(Modifier.height(16.dp))

            // تطبيقاتنا
            Button(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://apps-showcase.pages.dev")).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }) }, modifier = Modifier.fillMaxWidth().testTag("settings_open_our_apps")) { Icon(Icons.Default.Apps, null); Spacer(Modifier.width(8.dp)); Text("تطبيقاتنا") }
            // Sign Out Button
            Button(
                onClick = { showSignOutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("تسجيل الخروج الآمن")
            }
        }

        // Sign out confirm dialog
        if (showSignOutDialog) {
            AlertDialog(
                onDismissRequest = { showSignOutDialog = false },
                title = { Text("تسجيل الخروج") },
                text = { Text("هل تريد إنهاء جلستك الحالية وتسجيل الخروج؟") },
                confirmButton = {
                    Button(
                        onClick = {
                            showSignOutDialog = false
                            onSignOut()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("تسجيل الخروج")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutDialog = false }) { Text("إلغاء") }
                }
            )
        }
    }
}

@Composable
fun SecurityStatusRow(
    title: String,
    description: String,
    statusText: String,
    isGood: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Surface(
            color = if (isGood) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    if (isGood) Icons.Default.CheckCircle else Icons.Default.Warning,
                    null,
                    tint = if (isGood) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    statusText,
                    color = if (isGood) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
