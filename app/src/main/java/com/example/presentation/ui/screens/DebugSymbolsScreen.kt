package com.example.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.domain.model.*
import com.example.presentation.viewmodel.DebugSymbolsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSymbolsScreen(
    projectId: String,
    navController: NavController,
    viewModel: DebugSymbolsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var showUploadDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ملفات التصحيح (Debug Symbols)") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showUploadDialog = true }) {
                Icon(Icons.Default.UploadFile, contentDescription = "Upload")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "إدارة ملفات ProGuard, R8, dSYM و Source Maps لفك حجب الـ Stack Trace.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.files.isEmpty()) {
                EmptyDebugSymbolsState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.files) { file ->
                        DebugSymbolItem(
                            file = file,
                            onDelete = { viewModel.deleteFile(file.id) }
                        )
                    }
                }
            }
        }
    }

    if (showUploadDialog) {
        UploadSymbolDialog(
            applications = uiState.applications,
            onDismiss = { showUploadDialog = false },
            onUpload = { appId, type, version, buildId, fileName, content ->
                viewModel.uploadFile(appId, type, version, buildId, null, fileName, content)
                showUploadDialog = false
            }
        )
    }
}

@Composable
fun DebugSymbolItem(file: DebugSymbolFile, onDelete: () -> Unit) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when(file.type) {
                        SymbolFileType.IOS_DSYM -> Icons.Default.PhoneIphone
                        SymbolFileType.ANDROID_PROGUARD, SymbolFileType.ANDROID_R8 -> Icons.Default.Android
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(file.fileName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    "إصدار: ${file.appVersion} | نوع: ${file.type.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "تم الرفع: ${dateFormat.format(Date(file.uploadTimestamp))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun EmptyDebugSymbolsState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
        Spacer(Modifier.height(16.dp))
        Text("لا توجد ملفات تصحيح", style = MaterialTheme.typography.headlineSmall)
        Text(
            "ارفع ملفات الـ mapping لتمكين فك حجب الانهيارات.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun UploadSymbolDialog(
    applications: List<Application>,
    onDismiss: () -> Unit,
    onUpload: (String, SymbolFileType, String, String?, String, String) -> Unit
) {
    var selectedAppId by remember { mutableStateOf(applications.firstOrNull()?.id ?: "") }
    var customAppId by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(SymbolFileType.ANDROID_R8) }
    var version by remember { mutableStateOf("") }
    var buildId by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    val finalAppId = if (selectedAppId.isNotBlank()) selectedAppId else customAppId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("رفع ملف تصحيح جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (applications.isNotEmpty()) {
                    Text("التطبيق المستهدف:", style = MaterialTheme.typography.bodySmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        applications.forEach { app ->
                            FilterChip(
                                selected = selectedAppId == app.id,
                                onClick = { selectedAppId = app.id },
                                label = { Text(app.name) }
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = customAppId,
                        onValueChange = { customAppId = it },
                        label = { Text("معرف التطبيق (e.g. com.example.app)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("نوع الملف:")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SymbolFileType.entries.take(3).forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.name.split("_").last()) }
                        )
                    }
                }

                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("إصدار التطبيق (e.g. 1.2.0)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("اسم الملف (e.g. mapping.txt)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("محتوى الملف (Simulation / Symbols)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpload(finalAppId.ifBlank { "app_default" }, type, version, buildId.ifBlank { null }, fileName, content) },
                enabled = version.isNotBlank() && fileName.isNotBlank() && finalAppId.isNotBlank()
            ) {
                Text("رفع")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

