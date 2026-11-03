package com.example.presentation.ui.screens.vibe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BuildLog
import com.example.domain.model.LogType
import com.example.domain.model.TerminalCommandResult

@Composable
fun VibeTerminalPanel(
    logs: List<BuildLog>,
    terminalResults: List<TerminalCommandResult>,
    onSendCommand: (String) -> Unit,
    onClearLogs: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var commandInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size, terminalResults.size) {
        val total = logs.size + terminalResults.size
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().height(230.dp),
        color = Color(0xFF141414),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Terminal Header Bar
            Surface(
                color = Color(0xFF1F1F1F),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Terminal, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                        Text(
                            "Terminal (Isolated Sandbox)",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFFE0E0E0),
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF2E7D32).copy(alpha = 0.3f)
                        ) {
                            Text(
                                "Sandbox Active",
                                color = Color(0xFF81C784),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onClearLogs, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.DeleteSweep, "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "Close", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Quick development commands chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181818))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val quickCommands = listOf("npm run dev", "npm test", "npm run build", "ls", "ps", "ports", "help")
                quickCommands.forEach { cmd ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF2A2A2A),
                        modifier = Modifier.clickable { onSendCommand(cmd) }
                    ) {
                        Text(
                            cmd,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFFB0BEC5),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF2A2A2A))

            // Terminal Output Stream with Strict LTR
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    items(logs) { log ->
                        val (textColor, prefix) = when (log.type) {
                            LogType.TERMINAL_INPUT -> Color(0xFF64B5F6) to "$ "
                            LogType.ERROR -> Color(0xFFE57373) to "[ERR] "
                            LogType.WARNING -> Color(0xFFFFB74D) to "[WARN] "
                            LogType.SUCCESS -> Color(0xFF81C784) to "[OK] "
                            LogType.TERMINAL_OUTPUT -> Color(0xFFE0E0E0) to ""
                            else -> Color(0xFFB0BEC5) to "[INFO] "
                        }

                        Text(
                            text = prefix + log.message,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            color = textColor,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                        )
                    }

                    // Display command exit code badges if available
                    items(terminalResults) { res ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "> [Process: ${res.command}] completed in ${res.durationMs}ms",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = if (res.isSuccess) Color(0x304CAF50) else Color(0x30F44336)
                            ) {
                                Text(
                                    "exit: ${res.exitCode}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = if (res.isSuccess) Color(0xFF81C784) else Color(0xFFE57373),
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF2A2A2A))

            // Command Input Box
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF181818)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$ ", color = Color(0xFF4CAF50), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    TextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        placeholder = { Text("Enter sandbox command (e.g. npm test, ls, ports)...", color = Color.Gray, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color.White, fontSize = 12.sp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    IconButton(
                        onClick = {
                            if (commandInput.isNotBlank()) {
                                onSendCommand(commandInput.trim())
                                commandInput = ""
                            }
                        },
                        enabled = commandInput.isNotBlank(),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Execute", tint = if (commandInput.isNotBlank()) Color(0xFF4CAF50) else Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
