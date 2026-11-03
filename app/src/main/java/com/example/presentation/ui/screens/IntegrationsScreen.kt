package com.example.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.di.DependencyProvider
import com.example.domain.model.*
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.IntegrationsUiState
import com.example.presentation.viewmodel.IntegrationsViewModel
import com.example.util.SecurityUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationsScreen(
    projectId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: IntegrationsViewModel = viewModel {
        IntegrationsViewModel(
            DependencyProvider.provideCrashRepository(context),
            DependencyProvider.provideAuthRepository(context)
        )
    }
    val uiState by viewModel.getIntegrations(projectId).collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var testWebhookIntegration by remember { mutableStateOf<Integration?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.syncResult, uiState.webhookTestResult) {
        uiState.syncResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncResult()
        }
        uiState.webhookTestResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncResult()
        }
    }

    val canManage = AccessControl.canManageIntegrations(uiState.userRole)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("مصادر الأعطال والتكاملات الآمنة")
                        Text(
                            "مفاتيح API مشفرة بـ AES-GCM • تحقق من تواقيع Webhook",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.GitHubConnect(projectId)) }) {
                        Icon(Icons.Default.Code, contentDescription = "Connect GitHub")
                    }
                    IconButton(onClick = { navController.navigate(Screen.DebugSymbols(projectId)) }) {
                        Icon(Icons.Default.ManageHistory, contentDescription = stringResource(R.string.debug_symbols))
                    }
                    IconButton(onClick = { navController.navigate(Screen.SetupGuide(projectId, "app_unknown")) }) {
                        Icon(Icons.Default.Build, contentDescription = "Setup SDK")
                    }
                    IconButton(onClick = { viewModel.syncCrashes(projectId) }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Now")
                    }
                }
            )
        },
        floatingActionButton = {
            if (canManage) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Integration")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Security Banner
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        "بيانات التكامل مخزنة بتشفير AES-GCM ورموز الوصول محجوبة عن الواجهة لحماية المفاتيح.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading && uiState.integrations.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (uiState.integrations.isEmpty()) {
                    EmptyIntegrationsState(
                        canManage = canManage,
                        onAddClick = { showAddDialog = true },
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.integrations, key = { it.id }) { integration ->
                            SecureIntegrationCard(
                                integration = integration,
                                canManage = canManage,
                                onDelete = { viewModel.deleteIntegration(projectId, integration.id) },
                                onTestWebhook = { testWebhookIntegration = integration }
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddSecureIntegrationDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, type, config ->
                    viewModel.addIntegration(projectId, name, type, config)
                    showAddDialog = false
                }
            )
        }

        testWebhookIntegration?.let { integration ->
            TestWebhookDialog(
                integration = integration,
                onDismiss = { testWebhookIntegration = null },
                onVerify = { payload, signature ->
                    viewModel.testWebhookSignature(projectId, integration.id, payload, signature)
                    testWebhookIntegration = null
                }
            )
        }
    }
}

@Composable
fun SecureIntegrationCard(
    integration: Integration,
    canManage: Boolean,
    onDelete: () -> Unit,
    onTestWebhook: () -> Unit
) {
    var isConfigVisible by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (integration.type) {
                    IntegrationType.FIREBASE_CRASHLYTICS -> Icons.Default.Cloud
                    IntegrationType.SENTRY -> Icons.Default.MonitorHeart
                    IntegrationType.BUGSNAG -> Icons.Default.BugReport
                    else -> Icons.Default.Webhook
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(integration.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(integration.type.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                IntegrationStatusBadge(integration.status)
            }

            // Credential Masking Section
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "بيانات الاتصال ومفتاح API (مشفر):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { isConfigVisible = !isConfigVisible },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                if (isConfigVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isConfigVisible) SecurityUtils.decryptSecret(integration.configJson) else SecurityUtils.maskSecret(integration.configJson),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Webhook details if webhook type
            if (integration.type == IntegrationType.WEBHOOK && integration.webhookSecret.isNotBlank()) {
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("مفتاح توقيع HMAC السري:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(
                                SecurityUtils.maskSecret(integration.webhookSecret),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                        TextButton(onClick = onTestWebhook) {
                            Text("اختبار التوقيع")
                        }
                    }
                }
            }

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "حد الطلبات: 60/دقيقة",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (canManage) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إزالة")
                    }
                }
            }
        }
    }
}

@Composable
fun IntegrationStatusBadge(status: IntegrationStatus) {
    val color = when (status) {
        IntegrationStatus.CONNECTED -> Color(0xFF2E7D32)
        IntegrationStatus.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = when (status) {
                IntegrationStatus.CONNECTED -> "متصل وآمن"
                IntegrationStatus.ERROR -> "خطأ في الصلاحيات"
                else -> status.name
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EmptyIntegrationsState(
    canManage: Boolean,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.LinkOff,
            null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text("لا توجد مصادر أعطال متصلة بعد", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "اربط Firebase Crashlytics أو Sentry أو Bugsnag أو Webhook لتلقي تقارير الأعطال مشفرة.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (canManage) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAddClick) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("ربط مصدر أعطال جديد")
            }
        }
    }
}

@Composable
fun AddSecureIntegrationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, IntegrationType, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(IntegrationType.SENTRY) }
    var config by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ربط وتشفير مصدر أعطال جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المصدر / البيئة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("نوع المصدر:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(IntegrationType.SENTRY, IntegrationType.BUGSNAG, IntegrationType.FIREBASE_CRASHLYTICS, IntegrationType.WEBHOOK).forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.name.take(7)) }
                        )
                    }
                }

                OutlinedTextField(
                    value = config,
                    onValueChange = { config = it },
                    label = { Text("مفاتيح الاتصال (JSON)") },
                    placeholder = {
                        Text(
                            when (type) {
                                IntegrationType.SENTRY -> "{\"org\": \"acme\", \"project\": \"android\", \"token\": \"sntrys_...\"}"
                                IntegrationType.BUGSNAG -> "{\"projectId\": \"12345\", \"token\": \"bugsnag_token_...\"}"
                                IntegrationType.WEBHOOK -> "{\"endpoint\": \"https://api.mybackend.com/webhook\"}"
                                else -> "{\"apiKey\": \"...\"}"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "سيتم تشفير هذه البيانات تلقائيًا بنظام AES-GCM وحفظها بشكل آمن داخل النظام.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank() && config.isNotBlank()) onConfirm(name, type, config) }
            ) {
                Text("حفظ وتشفير")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun TestWebhookDialog(
    integration: Integration,
    onDismiss: () -> Unit,
    onVerify: (payload: String, signature: String) -> Unit
) {
    var testPayload by remember { mutableStateOf("{\"event\": \"crash\", \"error\": \"NullPointerException\"}") }
    var testSignature by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختبار التحقق من توقيع Webhook") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "يتحقق النظام من ترويسة التوقيع (HMAC-SHA256 Signature) ومطابقتها للمفتاح السري المشفر لمنع الطلبات غير الموثوقة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = testPayload,
                    onValueChange = { testPayload = it },
                    label = { Text("حمولة الطلب (Payload)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = testSignature,
                    onValueChange = { testSignature = it },
                    label = { Text("قيمة التوقيع (X-Hub-Signature-256)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onVerify(testPayload, testSignature) }) {
                Text("تحقق الآن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
