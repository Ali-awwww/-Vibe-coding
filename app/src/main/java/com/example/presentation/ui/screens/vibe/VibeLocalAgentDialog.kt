package com.example.presentation.ui.screens.vibe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.LocalAgentConfig

@Composable
fun VibeLocalAgentDialog(
    config: LocalAgentConfig,
    onConnect: (endpoint: String, token: String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    var endpoint by remember { mutableStateOf(config.endpoint.ifBlank { "http://localhost:8974" }) }
    var token by remember { mutableStateOf(config.authToken.ifBlank { "vibe_bridge_sec_8974" }) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Computer, null, tint = MaterialTheme.colorScheme.primary)
                        Text("جسر التشغيل المحلي (Local Agent)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null)
                    }
                }

                Text(
                    "يتيح لك تشغيل وبناء المشروع على جهاز الكمبيوتر الخاص بك مباشرةً عبر وكيل محلي معزول، بدلاً من البيئة السحابية.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(Modifier.height(12.dp))

                // Connection Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (config.isEnabled) Color(0x304CAF50) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("حالة الاتصال بالوكيل المحلي:", style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (config.isEnabled) "متصل بنجاح (Connected to Workstation)" else "غير متصل (Using Cloud Sandbox)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (config.isEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (config.isEnabled) Icons.Default.Link else Icons.Default.LinkOff,
                            null,
                            tint = if (config.isEnabled) Color(0xFF2E7D32) else Color.Gray
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("عنوان خادم الوكيل المحلي (Endpoint)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !config.isEnabled
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("رمز المصادقة الآمن (Auth Token)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !config.isEnabled
                )

                Spacer(Modifier.height(12.dp))

                // Security Note
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "أمان محكم: صلاحيات الوكيل المحلي مقيدة حصراً بمجلد المشروع المختار، ولا يملك أية صلاحيات خارج مسار العمل.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Actions
                if (config.isEnabled) {
                    Button(
                        onClick = {
                            onDisconnect()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("فصل الاتصال والعودة للتشغيل السحابي")
                    }
                } else {
                    Button(
                        onClick = {
                            if (endpoint.isNotBlank() && token.isNotBlank()) {
                                onConnect(endpoint, token)
                                onDismiss()
                            }
                        },
                        enabled = endpoint.isNotBlank() && token.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ربط وتفعيل الوكيل المحلي")
                    }
                }
            }
        }
    }
}
