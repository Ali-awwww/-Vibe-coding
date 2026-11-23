package com.example.presentation.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import com.example.data.util.MappingFileContent
import com.example.data.util.MappingFileReadException
import com.example.data.util.MappingFileReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

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
            val currentErrorResId = uiState.errorResId
            val loadFailed = currentErrorResId == R.string.debug_symbols_load_failed
            if (currentErrorResId != null && (!loadFailed || uiState.files.isNotEmpty())) {
                Text(stringResource(currentErrorResId), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (uiState.isLoading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (loadFailed && uiState.files.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.debug_symbols_load_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.debug_symbols_retry))
                    }
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
            errorResId = uiState.errorResId?.takeUnless { it == R.string.debug_symbols_load_failed },
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedAppId by remember(applications) { mutableStateOf(applications.firstOrNull()?.id ?: "") }
    var type by remember { mutableStateOf(SymbolFileType.ANDROID_R8) }
    var version by remember { mutableStateOf("") }
    var buildId by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<MappingFileContent?>(null) }
    var isReadingFile by remember { mutableStateOf(false) }
    var fileReadErrorResId by remember { mutableStateOf<Int?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedFile = null
            isReadingFile = true
            fileReadErrorResId = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val displayName = context.contentResolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameColumn >= 0 && cursor.moveToFirst()) cursor.getString(nameColumn) else null
                        } ?: throw IOException("The selected document name is unavailable.")

                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw IOException("The selected document cannot be opened.")
                        MappingFileReader.read(displayName, input)
                    }
                }

                result.fold(
                    onSuccess = { selectedFile = it },
                    onFailure = { error ->
                        selectedFile = null
                        fileReadErrorResId = when (error) {
                            is MappingFileReadException -> when (error.reason) {
                                MappingFileReadException.Reason.UNSUPPORTED_EXTENSION -> R.string.debug_symbols_file_unsupported
                                MappingFileReadException.Reason.EMPTY -> R.string.debug_symbols_file_empty
                                MappingFileReadException.Reason.TOO_LARGE -> R.string.debug_symbols_file_too_large
                                MappingFileReadException.Reason.INVALID_TEXT -> R.string.debug_symbols_file_invalid_text
                            }
                            else -> R.string.debug_symbols_file_read_failed
                        }
                    }
                )
                isReadingFile = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isUploading && !isReadingFile) onDismiss() },
        title = { Text(stringResource(R.string.debug_symbols_upload_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (applications.isEmpty()) {
                    Text(stringResource(R.string.debug_symbols_no_apps), color = MaterialTheme.colorScheme.error)
                } else {
                    Text(stringResource(R.string.debug_symbols_select_app))
                    applications.forEach { app ->
                        FilterChip(
                            selected = selectedAppId == app.id,
                            onClick = { selectedAppId = app.id },
                            label = { Text(app.name) },
                            enabled = !isUploading && !isReadingFile
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
                            enabled = !isUploading && !isReadingFile
                        )
                    }
                }

                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text(stringResource(R.string.debug_symbols_app_version)) },
                    singleLine = true,
                    enabled = !isUploading && !isReadingFile,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = buildId,
                    onValueChange = { buildId = it },
                    label = { Text(stringResource(R.string.debug_symbols_build_id_optional)) },
                    singleLine = true,
                    enabled = !isUploading && !isReadingFile,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    enabled = !isUploading && !isReadingFile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.debug_symbols_choose_file))
                }

                if (isReadingFile) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.debug_symbols_reading_file))
                    }
                }

                selectedFile?.let { file ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.debug_symbols_selected_file, file.fileName), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.debug_symbols_file_size, file.byteSize),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                fileReadErrorResId?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                errorResId?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    stringResource(R.string.debug_symbols_local_storage_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedFile?.let { file ->
                        onUpload(
                            selectedAppId,
                            type,
                            version.trim(),
                            buildId.trim().ifBlank { null },
                            file.fileName,
                            file.text
                        )
                    }
                },
                enabled = selectedAppId.isNotBlank() &&
                    version.isNotBlank() &&
                    selectedFile != null &&
                    !isUploading &&
                    !isReadingFile
            ) {
                if (isUploading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.debug_symbols_upload_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUploading && !isReadingFile) {
                Text(stringResource(R.string.debug_symbols_cancel))
            }
        }
    )
}
