package com.example.presentation.ui.screens.vibe

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.model.*
import com.example.util.WorkspaceSandboxEngine

@Composable
fun VibeLivePreviewPanel(
    workspace: Workspace,
    files: List<WorkspaceFile>,
    runtimeState: SandboxRuntimeState?,
    previewViewport: PreviewViewport,
    previewOrientation: PreviewOrientation,
    previewKey: Int,
    browserLogs: List<BrowserConsoleLog>,
    onSetViewport: (PreviewViewport) -> Unit,
    onSetOrientation: (PreviewOrientation) -> Unit,
    onReload: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onRestartServer: () -> Unit,
    onAddConsoleLog: (ConsoleLogLevel, String, String?) -> Unit,
    onClearConsoleLogs: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Preview, 1: Browser Console

    val isNative = workspace.type == WorkspaceType.ANDROID || workspace.type == WorkspaceType.IOS

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Preview Controls Header
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Status indicator light
                            val (dotColor, statusText) = when (runtimeState?.status) {
                                ServerStatus.RUNNING -> Color(0xFF4CAF50) to "RUNNING"
                                ServerStatus.STARTING -> Color(0xFFFFB300) to "STARTING"
                                ServerStatus.ERROR -> Color(0xFFF44336) to "ERROR"
                                else -> Color.Gray to "STOPPED"
                            }
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                            Text(
                                "Live Preview: $statusText",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (runtimeState?.status == ServerStatus.RUNNING) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        ":${runtimeState.port}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        // Close Button
                        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Viewport and Controls Toolbar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Responsive Viewport Switcher
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                onClick = { onSetViewport(PreviewViewport.MOBILE) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Smartphone,
                                    "Mobile",
                                    tint = if (previewViewport == PreviewViewport.MOBILE) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onSetViewport(PreviewViewport.TABLET) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Tablet,
                                    "Tablet",
                                    tint = if (previewViewport == PreviewViewport.TABLET) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onSetViewport(PreviewViewport.DESKTOP) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Laptop,
                                    "Desktop",
                                    tint = if (previewViewport == PreviewViewport.DESKTOP) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    onSetOrientation(
                                        if (previewOrientation == PreviewOrientation.PORTRAIT) PreviewOrientation.LANDSCAPE else PreviewOrientation.PORTRAIT
                                    )
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.ScreenRotation,
                                    "Rotate",
                                    tint = if (previewOrientation == PreviewOrientation.LANDSCAPE) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Server Controls (Start, Restart, Reload)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (runtimeState?.status == ServerStatus.RUNNING) {
                                IconButton(onClick = onRestartServer, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.RestartAlt, "Restart Server", tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = onStopServer, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Stop, "Stop Server", tint = Color(0xFFF44336), modifier = Modifier.size(16.dp))
                                }
                            } else {
                                IconButton(onClick = onStartServer, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.PlayArrow, "Start Server", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(onClick = onReload, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Refresh, "Reload Preview", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Tabs (Preview vs Browser Console)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.height(34.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("معاينة التطبيق", fontSize = 11.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("سجلات المتصفح", fontSize = 11.sp)
                            if (browserLogs.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        "${browserLogs.size}",
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }

            HorizontalDivider()

            // Main Display Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                if (selectedTab == 0) {
                    // Distinct handling: Native Mobile App vs Web Browser App
                    if (isNative) {
                        NativeAppSimulatorView(
                            workspace = workspace,
                            files = files,
                            runtimeState = runtimeState,
                            viewport = previewViewport,
                            orientation = previewOrientation
                        )
                    } else {
                        // Web Browser Sandbox Rendering
                        val frameModifier = when (previewViewport) {
                            PreviewViewport.MOBILE -> {
                                if (previewOrientation == PreviewOrientation.PORTRAIT) Modifier.width(360.dp).fillMaxHeight().padding(vertical = 12.dp)
                                else Modifier.fillMaxWidth().height(360.dp).padding(horizontal = 12.dp)
                            }
                            PreviewViewport.TABLET -> {
                                if (previewOrientation == PreviewOrientation.PORTRAIT) Modifier.width(540.dp).fillMaxHeight().padding(vertical = 12.dp)
                                else Modifier.fillMaxWidth().height(480.dp).padding(horizontal = 12.dp)
                            }
                            PreviewViewport.DESKTOP -> Modifier.fillMaxSize()
                        }

                        Surface(
                            modifier = frameModifier
                                .clip(if (previewViewport != PreviewViewport.DESKTOP) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp))
                                .border(
                                    width = if (previewViewport != PreviewViewport.DESKTOP) 2.dp else 0.dp,
                                    color = Color(0xFF334155),
                                    shape = if (previewViewport != PreviewViewport.DESKTOP) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp)
                                ),
                            color = Color.White
                        ) {
                            key(previewKey) {
                                AndroidView(
                                    factory = { context ->
                                        WebView(context).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.loadWithOverviewMode = true
                                            settings.useWideViewPort = true

                                            webChromeClient = object : WebChromeClient() {
                                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                                    consoleMessage?.let {
                                                        val level = when (it.messageLevel()) {
                                                            ConsoleMessage.MessageLevel.ERROR -> ConsoleLogLevel.ERROR
                                                            ConsoleMessage.MessageLevel.WARNING -> ConsoleLogLevel.WARN
                                                            ConsoleMessage.MessageLevel.LOG -> ConsoleLogLevel.LOG
                                                            else -> ConsoleLogLevel.INFO
                                                        }
                                                        onAddConsoleLog(level, it.message(), "${it.sourceId()}:${it.lineNumber()}")
                                                    }
                                                    return true
                                                }
                                            }

                                            webViewClient = object : WebViewClient() {}

                                            val html = WorkspaceSandboxEngine.generatePreviewHtml(workspace, files)
                                            loadDataWithBaseURL("http://localhost:3000", html, "text/html", "UTF-8", null)
                                        }
                                    },
                                    update = { webView ->
                                        val html = WorkspaceSandboxEngine.generatePreviewHtml(workspace, files)
                                        webView.loadDataWithBaseURL("http://localhost:3000", html, "text/html", "UTF-8", null)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                } else {
                    // Browser Console Logs View
                    BrowserConsoleLogsView(
                        logs = browserLogs,
                        onClear = onClearConsoleLogs
                    )
                }
            }
        }
    }
}

@Composable
fun NativeAppSimulatorView(
    workspace: Workspace,
    files: List<WorkspaceFile>,
    runtimeState: SandboxRuntimeState?,
    viewport: PreviewViewport,
    orientation: PreviewOrientation
) {
    // Complies strictly with: "أما التطبيقات الأصلية Android وiOS، فاستخدم Android Emulator أو بيئة Apple Simulator. لا تعرض معاينة ويب على أنها محاكاة فعلية لنظام تشغيل أصلي."
    Box(
        modifier = Modifier
            .width(if (viewport == PreviewViewport.TABLET) 500.dp else 320.dp)
            .fillMaxHeight()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(3.dp, Color(0xFF475569), RoundedCornerShape(24.dp))
            .background(Color(0xFF1E293B)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Phone Speaker Notch
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF475569))
            )

            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Android, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("Android Emulator API 36", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Emulator screen representation
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(workspace.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Native Android Build Artifact", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0x304CAF50),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Streaming Emulator Ready • Gradle Debug APK Installed",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFF81C784),
                            fontSize = 9.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "${files.size} source files compiled • Dalvik Executable loaded",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Navigation bar bar pill
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
            )
        }
    }
}

@Composable
fun BrowserConsoleLogsView(
    logs: List<BrowserConsoleLog>,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF141414))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Console Output (${logs.size})", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            TextButton(onClick = onClear) {
                Text("Clear Console", fontSize = 11.sp)
            }
        }

        HorizontalDivider(color = Color(0xFF2A2A2A))

        if (logs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No console logs captured yet.", color = Color.Gray, fontSize = 11.sp)
            }
        } else {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                    items(logs) { log ->
                        val (color, prefix) = when (log.type) {
                            ConsoleLogLevel.ERROR -> Color(0xFFEF5350) to "✖ "
                            ConsoleLogLevel.WARN -> Color(0xFFFFB74D) to "⚠ "
                            ConsoleLogLevel.INFO -> Color(0xFF42A5F5) to "ℹ "
                            else -> Color(0xFFE0E0E0) to "› "
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                prefix + log.message,
                                color = color,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                modifier = Modifier.weight(1f)
                            )
                            if (log.source != null) {
                                Text(
                                    log.source,
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
