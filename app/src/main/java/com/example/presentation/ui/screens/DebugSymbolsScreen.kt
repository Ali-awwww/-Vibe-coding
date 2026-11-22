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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.R
import com.example.domain.model.*
import com.example.presentation.viewmodel.DebugSymbolsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSymbolsScreen(projectId: String, navController: NavController, viewModel: DebugSymbolsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showUploadDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_symbols_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.debug_symbols_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showUploadDialog = true }) {
                Icon(Icons.Default.UploadFile, contentDescription = stringResource(R.string.debug_symbols_upload))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.debug_symbols_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            uiState.errorResId?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (uiState.isLoading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.files.isEmpty()) {
                EmptyDebugSymbolsState()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    items(uiState.files, key = { it.id }) { file ->
                        DebugSymbolItem(file = file, onDelete = { viewModel.deleteFile(file.id) })
                    }
                }
            }
        }
    }

    if (showUploadDialog) {
        UploadSymbolDialog(
            applications = uiState.applications,
            isUploading = uiState.isUploading,
            errorResId = uiState.errorResId,
            onDismiss = { if (!uiState.isUploading) showUploadDialog = false },
            onUpload = { appId, type, version, buildId, fileName, content ->
                viewModel.uploadFile(appId, type, version, buildId, null, fileName, content) { saved ->
                    if (saved) showUploadDialog = false
                }
            }
        )
    }
}

@Composable
fun DebugSymbolItem(file: DebugSymbolFile, onDelete: () -> Unit) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val statusText = when (file.status) {
        SymbolicationStatus.PENDING -> stringResource(R.string.debug_symbols_status_pending)
        SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED -> stringResource(R.string.debug_symbols_status_success)
        SymbolicationStatus.PARTIALLY_SYMBOLICATED -> stringResource(R.string.debug_symbols_status_partial)
        SymbolicationStatus.MISSING_DEBUG_SYMBOLS -> stringResource(R.string.debug_symbols_status_missing)
        else -> stringResource(R.string.debug_symbols_status_failed)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (file.type) {
                    SymbolFileType.IOS_DSYM -> Icons.Default.PhoneIphone
                    SymbolFileType.ANDROID_PROGUARD, SymbolFileType.ANDROID_R8 -> Icons.Default.Android
                    else -> Icons.Default.Description
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.fileName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.debug_symbols_version_type, file.appVersion, file.type.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.debug_symbols_uploaded_at, dateFormat.format(Date(file.uploadTimestamp))),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.debug_symbols_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun EmptyDebugSymbolsState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
        Text(stringResource(R.string.debug_symbols_empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.debug_symbols_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun UploadSymbolDialog(
    applications: List<Application>,
    isUploading: Boolean,
    errorResId: Int?,
    onDismiss: () -> Unit,
    onUpload: (String, SymbolFileType, String, String?, String, String) -> Unit
) {
    var selectedAppId by remember(applications) { mutableStateOf(applications.firstOrNull()?.id ?: "") }
    var type by remember { mutableStateOf(SymbolFileType.ANDROID_R8) }
    var version by remember { mutableStateOf("") }
    var buildId by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.debug_symbols_upload_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (applications.isEmpty()) {
                    Text(stringResource(R.string.debug_symbols_no_apps), color = MaterialTheme.colorScheme.error)
                } else {
                    Text(stringResource(R.string.debug_symbols_select_app))
                    applications.forEach { app ->
                        FilterChip(
                            selected = selectedAppId == app.id,
                            onClick = { selectedAppId = app.id },
                            label = { Text(app.name) },
                            enabled = !isUploading
                        )
                    }
                }

                Text(stringResource(R.string.debug_symbols_supported_types))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(SymbolFileType.ANDROID_R8, SymbolFileType.ANDROID_PROGUARD).forEach { symbolType ->
                        FilterChip(
                            selected = type == symbolType,
                            onClick = { type = symbolType },
                            label = { Text(if (symbolType == SymbolFileType.ANDROID_R8) "R8" else "ProGuard") },
                            enabled = !isUploading
                        )
                    }
                }

                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text(stringResource(R.string.debug_symbols_app_version)) },
                    singleLine = true,
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = buildId,
                    onValueChange = { buildId = it },
                    label = { Text(stringResource(R.string.debug_symbols_build_id_optional)) },
                    singleLine = true,
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text(stringResource(R.string.debug_symbols_file_name)) },
                    singleLine = true,
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.debug_symbols_mapping_content)) },
                    placeholder = { Text("com.example.MainActivity -> a:") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    enabled = !isUploading
                )
                Text(
                    stringResource(R.string.debug_symbols_local_storage_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                errorResId?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpload(selectedAppId, type, version.trim(), buildId.trim().ifBlank { null }, fileName.trim(), content) },
                enabled = selectedAppId.isNotBlank() && version.isNotBlank() && fileName.isNotBlank() && content.isNotBlank() && !isUploading
            ) {
                if (isUploading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.debug_symbols_upload_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUploading) { Text(stringResource(R.string.debug_symbols_cancel)) }
        }
    )
}
