package com.example.presentation.ui.screens.teamchat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamFileAttachmentDialog(
    onDismiss: () -> Unit,
    onAddFile: (name: String, path: String, content: String, sizeBytes: Long, fileType: String) -> Unit
) {
    var selectedPresetIndex by remember { mutableIntStateOf(-1) }
    var fileName by remember { mutableStateOf("FixContext.kt") }
    var filePath by remember { mutableStateOf("/app/src/main/java/com/example/FixContext.kt") }
    var fileContent by remember { mutableStateOf("") }
    var fileType by remember { mutableStateOf("text/x-kotlin") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val presetFiles = remember {
        listOf(
            PresetContextFile(
                name = "CrashReport_NullPointer.log",
                path = "/logs/crashes/crash_20261101_npe.log",
                type = "text/plain",
                content = """Fatal Exception: java.lang.NullPointerException: Attempt to invoke virtual method 'getUserId()' on a null object reference
	at com.example.domain.agent.TeamOrchestratorImpl.executeTask(TeamOrchestratorImpl.kt:66)
	at com.example.presentation.viewmodel.TeamChatViewModel.sendTask(TeamChatViewModel.kt:182)
	at kotlinx.coroutines.flow.SafeFlow.collect(SafeFlow.kt:42)"""
            ),
            PresetContextFile(
                name = "AndroidManifest.xml",
                path = "/app/src/main/AndroidManifest.xml",
                type = "application/xml",
                content = """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <application android:name=".CrashIntelligenceApp" ... />
</manifest>"""
            ),
            PresetContextFile(
                name = "AppDatabase.kt",
                path = "/app/src/main/java/com/example/data/local/AppDatabase.kt",
                type = "text/x-kotlin",
                content = """@Database(entities = [Project::class, CrashReport::class, ModelTeam::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun crashDao(): CrashDao
    abstract fun aiProviderDao(): AIProviderDao
}"""
            ),
            PresetContextFile(
                name = "TeamGovernancePolicy.json",
                path = "/configs/governance_policy.json",
                type = "application/json",
                content = """{
  "freeOnly": true,
  "maxTeamCostUsd": 0.0,
  "maxTotalTokens": 100000,
  "maxParallelRequests": 3,
  "autoFallbackToFree": true
}"""
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("إرفاق ملف أو سياق برمجي مسموح به", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("اختر من ملفات المشروع أو أدخل سياقًا مخصصًا:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(presetFiles) { index, preset ->
                            FilterChip(
                                selected = selectedPresetIndex == index,
                                onClick = {
                                    selectedPresetIndex = index
                                    fileName = preset.name
                                    filePath = preset.path
                                    fileType = preset.type
                                    fileContent = preset.content
                                    validationError = null
                                },
                                label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it; validationError = null },
                        label = { Text("اسم الملف") },
                        modifier = Modifier.fillMaxWidth().testTag("attach_file_name_input"),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = filePath,
                        onValueChange = { filePath = it; validationError = null },
                        label = { Text("مسار الملف في المشروع") },
                        modifier = Modifier.fillMaxWidth().testTag("attach_file_path_input"),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it; validationError = null },
                        label = { Text("محتوى الكود أو السجل البرمجي") },
                        placeholder = { Text("أدخل الكود أو Stack Trace المسموح بإرفاقه...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp)
                            .testTag("attach_file_content_input"),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }

                if (fileContent.isNotEmpty()) {
                    val sizeBytes = fileContent.toByteArray().size
                    val sizeKb = sizeBytes / 1024.0
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("الحجم التقديري:", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    String.format("%.2f KB (%d Bytes)", sizeKb, sizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sizeBytes > 250_000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (validationError != null) {
                    item {
                        Text(
                            text = validationError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fileName.isBlank()) {
                        validationError = "يرجى تحديد اسم الملف"
                        return@Button
                    }
                    if (fileContent.isBlank()) {
                        validationError = "محتوى الملف لا يمكن أن يكون فارغًا"
                        return@Button
                    }
                    val size = fileContent.toByteArray().size.toLong()
                    if (size > 500_000) {
                        validationError = "حجم الملف يتجاوز الحد الأقصى المسموح به (500KB)"
                        return@Button
                    }
                    onAddFile(fileName, filePath, fileContent, size, fileType)
                },
                modifier = Modifier.testTag("confirm_attach_file_button")
            ) {
                Text("إرفاق الملف")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

data class PresetContextFile(
    val name: String,
    val path: String,
    val type: String,
    val content: String
)
