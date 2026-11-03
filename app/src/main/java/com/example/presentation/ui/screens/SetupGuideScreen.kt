package com.example.presentation.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.domain.model.*
import com.example.presentation.viewmodel.SetupGuideViewModel
import com.example.presentation.viewmodel.SetupGuideUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupGuideScreen(
    projectId: String,
    applicationId: String,
    navController: NavController,
    viewModel: SetupGuideViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedService by remember { mutableStateOf(IntegrationType.FIREBASE_CRASHLYTICS) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دليل إعداد SDK") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (uiState.tech == null) {
                TechnologySelection(
                    onTechSelected = { viewModel.selectManualTech(it) },
                    onScanProject = { viewModel.scanProject(listOf("MainActivity.kt", "build.gradle.kts", "AndroidManifest.xml")) }
                )
            } else {
                GuideContent(
                    uiState = uiState,
                    selectedService = selectedService,
                    onServiceSelected = { 
                        selectedService = it
                        viewModel.generateGuide(it, projectId)
                    },
                    onVerify = { viewModel.verifyIntegration(applicationId) },
                    onResetTech = { viewModel.selectManualTech(ProjectTechnology.UNKNOWN) }
                )
            }
        }
    }
}

@Composable
fun TechnologySelection(
    onTechSelected: (ProjectTechnology) -> Unit,
    onScanProject: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
        Icon(Icons.Default.Code, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("اكتشاف تقنية المشروع", style = MaterialTheme.typography.headlineSmall)
        Text("اختر التقنية يدويًا أو دعنا نكتشفها لك", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(Modifier.height(24.dp))
        
        Button(onClick = onScanProject, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Search, null)
            Spacer(Modifier.width(8.dp))
            Text("اكتشاف تلقائي (Auto Detect)")
        }
        
        Spacer(Modifier.height(16.dp))
        Text("أو اختر يدويًا:")
        Spacer(Modifier.height(8.dp))
        
        val technologies = listOf(
            ProjectTechnology.ANDROID_KOTLIN to "Android (Kotlin)",
            ProjectTechnology.ANDROID_JAVA to "Android (Java)",
            ProjectTechnology.IOS_SWIFT to "iOS (Swift)",
            ProjectTechnology.FLUTTER to "Flutter",
            ProjectTechnology.REACT_NATIVE to "React Native"
        )
        
        technologies.forEach { (tech, label) ->
            OutlinedButton(onClick = { onTechSelected(tech) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(label)
            }
        }
    }
}

@Composable
fun GuideContent(
    uiState: SetupGuideUiState,
    selectedService: IntegrationType,
    onServiceSelected: (IntegrationType) -> Unit,
    onVerify: () -> Unit,
    onResetTech: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("التقنية المكتشفة: ", style = MaterialTheme.typography.bodyMedium)
            Text(uiState.tech?.name ?: "غير معروف", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onResetTech) { Text("تغيير") }
        }

        Spacer(Modifier.height(16.dp))
        Text("اختر خدمة المراقبة:", style = MaterialTheme.typography.titleMedium)
        
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(IntegrationType.FIREBASE_CRASHLYTICS, IntegrationType.SENTRY, IntegrationType.BUGSNAG).forEach { service ->
                FilterChip(
                    selected = selectedService == service,
                    onClick = { onServiceSelected(service) },
                    label = { Text(service.name.split("_").last().lowercase().capitalize()) }
                )
            }
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        if (uiState.guide == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (uiState.isScanning) {
                    CircularProgressIndicator()
                } else {
                    Button(onClick = { onServiceSelected(selectedService) }) {
                        Text("عرض خطوات التكامل")
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(uiState.guide.steps) { step ->
                    StepCard(step)
                }
                
                item {
                    TroubleshootingSection(uiState.guide.troubleshooting)
                }

                item {
                    AutomatedChangesSection(
                        onApprove = { /* In a real app, this would apply the snippets via an agent or PR */ }
                    )
                }

                item {
                    VerificationSection(
                        isVerifying = uiState.isVerifying,
                        result = uiState.verificationResult,
                        onVerify = onVerify
                    )
                }
            }
        }
    }
}

@Composable
fun StepCard(step: IntegrationStep) {
    val clipboardManager = LocalClipboardManager.current
    
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(step.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            Text(step.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            
            step.fileToModify?.let {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {
                    Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }

            step.codeSnippet?.let { code ->
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Column {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Code Snippet", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(code)) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.ContentCopy, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(code, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TroubleshootingSection(issues: List<String>) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("حل المشكلات الشائعة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            issues.forEach { issue ->
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    Text("• ", fontWeight = FontWeight.Bold)
                    Text(issue, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun AutomatedChangesSection(onApprove: () -> Unit) {
    var approved by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoFixHigh, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("تطبيق التعديلات تلقائيًا", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                "يمكننا محاولة تعديل ملفات مشروعك (build.gradle, AndroidManifest) تلقائيًا لإضافة الـ SDK.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            if (!approved) {
                Button(
                    onClick = { 
                        approved = true
                        onApprove()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("عرض التعديلات المقترحة والموافقة")
                }
            } else {
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تم إرسال التعديلات المقترحة إلى Local Agent", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                    }
                }
            }
        }
    }
}

@Composable
fun VerificationSection(
    isVerifying: Boolean,
    result: Boolean?,
    onVerify: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("اختبار الاتصال", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("بعد إكمال الخطوات، قم بتشغيل تطبيقك وتحقق من نجاح الربط.", style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            
            Spacer(Modifier.height(16.dp))
            
            if (isVerifying) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else if (result == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32))
                    Spacer(Modifier.width(8.dp))
                    Text("تم التحقق بنجاح! SDK يعمل بشكل صحيح.", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                }
            } else if (result == false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("فشل التحقق. لم يتم استلام أي تقرير اختبار.", color = MaterialTheme.colorScheme.error)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Button(onClick = onVerify, enabled = !isVerifying) {
                Text("بدء التحقق (Check Integration)")
            }
        }
    }
}

private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
