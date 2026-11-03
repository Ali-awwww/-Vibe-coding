package com.example.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.di.DependencyProvider
import com.example.domain.model.*
import com.example.presentation.viewmodel.AIModelsUiState
import com.example.presentation.viewmodel.AIModelsViewModel
import com.example.util.SecurityUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIModelsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: AIModelsViewModel = viewModel {
        DependencyProvider.provideAIModelsViewModel(context)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddProviderDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("إعدادات الذكاء الاصطناعي") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddProviderDialog = true }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Add Provider")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. AI Settings (Stats Dashboard)
            item {
                Text("لوحة التحكم", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                AISettingsDashboard(uiState)
            }

            // 2. Cost & Routing Settings
            item {
                Text("إعدادات التكلفة والذكاء", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                AICostSettingsCard(uiState, onFreeOnlyToggle = { viewModel.toggleFreeOnly(it) }, onAutoSelectToggle = { viewModel.toggleAutoSelect(it) })
            }

            // 3. Model Selection
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("اختيار النموذج", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.height(8.dp))
                AIModelSelectionSection(
                    models = uiState.models,
                    selectedId = uiState.defaultModelId,
                    onSelect = { viewModel.setDefaultModel(it) },
                    autoSelect = uiState.autoSelectFree
                )
            }

            // 4. Providers
            item {
                Text("المزودون (BYOK)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (uiState.providers.isEmpty()) {
                    AIProvidersGuide()
                }
            }

            items(uiState.providers) { provider ->
                AIProviderCard(
                    provider = provider,
                    onSync = { viewModel.syncModels(it) },
                    onTest = { viewModel.testConnection(it) },
                    onDelete = { viewModel.deleteProvider(it) }
                )
            }
        }

        if (showAddProviderDialog) {
            AddAIProviderDialog(
                onDismiss = { showAddProviderDialog = false },
                onConfirm = { name, type, key, url ->
                    viewModel.addProvider(name, type, key, url)
                    showAddProviderDialog = false
                }
            )
        }
    }
}

@Composable
fun AISettingsDashboard(uiState: AIModelsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatItem(label = "الطلبات الناجحة", value = uiState.successCount.toString(), color = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                StatItem(label = "الطلبات الفاشلة", value = uiState.failedCount.toString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            }
            
            HorizontalDivider(modifier = Modifier.alpha(0.5f))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("آخر طلب ناجح", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        uiState.lastSuccessAt?.let { java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "لا يوجد",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("التكلفة التقديرية (30 يوم)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$${"%.4f".format(uiState.totalCost)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun AICostSettingsCard(
    uiState: AIModelsUiState,
    onFreeOnlyToggle: (Boolean) -> Unit,
    onAutoSelectToggle: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Free AI Only", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("منع النماذج المدفوعة نهائياً وضمان العمل المجاني.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = uiState.freeOnly, onCheckedChange = onFreeOnlyToggle)
            }
            
            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto Select Free Model", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("اختيار أفضل نموذج مجاني متاح للمهمة تلقائياً.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = uiState.autoSelectFree, onCheckedChange = onAutoSelectToggle)
            }
        }
    }
}

@Composable
fun AIModelSelectionSection(
    models: List<AIModel>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    autoSelect: Boolean
) {
    if (autoSelect) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("الوضع الذكي مفعل: سيتم اختيار النموذج المجاني الأنسب لكل تحليل تلقائياً.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        if (models.isEmpty()) {
            Text("لا توجد نماذج متوفرة. أضف مزوداً أولاً.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                models.forEach { model ->
                    AIModelItem(model = model, isSelected = model.id == selectedId, onSelect = { onSelect(model.id) })
                }
            }
        }
    }
}

@Composable
fun AIModelItem(model: AIModel, isSelected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    if (model.isFree) {
                        Spacer(Modifier.width(8.dp))
                        ModelStatusChip("Free", Color(0xFF2E7D32))
                    }
                }
                Text("${model.apiId} • Context: ${model.contextWindow / 1000}k", style = MaterialTheme.typography.labelSmall)
            }
            if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun AIProvidersGuide() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("كيف تبدأ؟", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            GuideStep("1", "قم بإنشاء حساب على OpenRouter أو Google AI Studio.")
            GuideStep("2", "أنشئ مفتاح API جديد (API Key).")
            GuideStep("3", "أضف المفتاح هنا باستخدام زر الإضافة في الأعلى.")
            
            TextButton(
                onClick = { /* Open external links if needed */ },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("الموقع الرسمي: OpenRouter.ai")
            }
        }
    }
}

@Composable
fun GuideStep(num: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier.size(18.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(num, color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun ModelStatusChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun AIProviderCard(
    provider: AIProvider,
    onSync: (String) -> Unit,
    onTest: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (provider.type) {
                        AIProviderType.GOOGLE_GEMINI -> Icons.Default.AutoAwesome
                        AIProviderType.OPEN_ROUTER -> Icons.Default.Hub
                        else -> Icons.Default.CloudQueue
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(provider.type.name, style = MaterialTheme.typography.bodySmall)
                }
                ProviderStatusBadge(provider.status)
            }

            Text(
                "API Key: ${SecurityUtils.maskSecret(provider.apiKey ?: "")}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onTest(provider.id) }) {
                    Icon(Icons.Default.FactCheck, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("اختبار")
                }
                TextButton(onClick = { onSync(provider.id) }) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تحديث")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onDelete(provider.id) }) {
                    Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ProviderStatusBadge(status: AIProviderStatus) {
    val color = when (status) {
        AIProviderStatus.CONNECTED -> Color(0xFF2E7D32)
        AIProviderStatus.ERROR -> MaterialTheme.colorScheme.error
        AIProviderStatus.RATE_LIMITED -> Color(0xFFF57C00)
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(status.name, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun AddAIProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, AIProviderType, String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AIProviderType.OPEN_ROUTER) }
    var key by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مزود ذكاء اصطناعي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم (مثلاً: My OpenRouter)") }, modifier = Modifier.fillMaxWidth())
                
                Text("نوع المزود:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AIProviderType.values().filter { it != AIProviderType.CUSTOM }.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name) })
                    }
                }

                OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth())
                
                if (type == AIProviderType.CUSTOM) {
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
                }

                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        "سيتم تشفير مفتاح API محلياً قبل حفظه. لا يتم إرسال المفاتيح إلا في طلبات التحليل الفعلية.",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank() && key.isNotBlank()) onConfirm(name, type, key, url.ifBlank { null }) }) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
