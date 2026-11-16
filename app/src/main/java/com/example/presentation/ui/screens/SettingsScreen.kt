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
import androidx.navigation.NavController
import com.example.presentation.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.di.DependencyProvider
import com.example.util.SeedData
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onSignOut: () -> Unit,
    navController: NavController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showSandboxDialog by remember { mutableStateOf(false) }
    var sandboxLoadedMessage by remember { mutableStateOf<String?>(null) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

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
            Text("حساب المطور النشط", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

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
                                    (currentUser?.displayName?.take(1) ?: "D").uppercase(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                currentUser?.displayName ?: "Developer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                currentUser?.email ?: "dev@crashintelligence.local",
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
                            text = "آخر تسجيل دخول: ${if (currentUser?.lastLoginAt != null) dateFormat.format(Date(currentUser!!.lastLoginAt)) else "الآن"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.VerifiedUser, null, modifier = Modifier.size(14.dp))
                                Text(
                                    "Google / Firebase",
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
                        "تتيح لك البيئة المعزولة فحص وتحليل سيناريوهات الانهيارات الكاملة وحلول الذكاء الاصطناعي دون التأثير على مشروعاتك الفعلية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    sandboxLoadedMessage?.let { msg ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                msg,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Button(
                        onClick = { navController.navigate(com.example.presentation.navigation.Screen.ProductQualityCenter) },
                        modifier = Modifier.fillMaxWidth().testTag("settings_open_quality_center")
                    ) {
                        Icon(Icons.Default.FactCheck, null)
                        Spacer(Modifier.width(8.dp))
                        Text("مركز جودة المنتج وبوابة الاعتماد (Release Gate)")
                    }

                    OutlinedButton(
                        onClick = { showSandboxDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Science, null)
                        Spacer(Modifier.width(8.dp))
                        Text("تحميل مشروع تجريبي معزول للمعاينة")
                    }
                }
            }


            Spacer(Modifier.height(16.dp))

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

        if (showSandboxDialog) {
            AlertDialog(
                onDismissRequest = { showSandboxDialog = false },
                title = { Text("تحميل بيانات المعاينة التجريبية") },
                text = {
                    Text("سيتم إنشاء مشروع معزول وتطبيقات اختبار لتمكين فحص سيناريو الانهيارات وذكاء الإصلاح. هل ترغب في المتابعة؟")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                SeedData.seed(
                                    DependencyProvider.provideCrashRepository(context),
                                    DependencyProvider.provideSymbolicationRepository(context)
                                )
                                sandboxLoadedMessage = "تم تجهيز المشروع التجريبي المعزول بنجاح."
                                showSandboxDialog = false
                            }
                        }
                    ) {
                        Text("تأكيد التحميل")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSandboxDialog = false }) {
                        Text("إلغاء")
                    }
                }
            )
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
