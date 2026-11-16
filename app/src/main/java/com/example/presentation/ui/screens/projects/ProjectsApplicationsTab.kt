package com.example.presentation.ui.screens.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.*
import com.example.presentation.navigation.Screen
import com.example.presentation.ui.screens.dashboard.DashboardPanel
import com.example.presentation.viewmodel.ProjectsUiState
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens

@Composable
internal fun ApplicationsTab(
    uiState: ProjectsUiState,
    onNavigate: (Screen) -> Unit,
    onAddApplication: () -> Unit,
    onRequestDeleteApp: (Application) -> Unit
) {
    val canManage = AccessControl.canManageIntegrations(uiState.userRoleInSelectedProject)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "التطبيقات المرتبطة بالمشروع",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.TextPrimary
                )
                Text(
                    text = uiState.selectedProject?.let { "إدارة تطبيقات Android و iOS في ${it.name}" }
                        ?: "اختر مشروعًا لإدارة تطبيقاته",
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextMuted
                )
            }

            if (canManage && uiState.selectedProject != null) {
                Button(
                    onClick = onAddApplication,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("add_application_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibeDesignTokens.Color.Cyan,
                        contentColor = VibeDesignTokens.Color.OnCyan
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ربط تطبيق", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (uiState.applications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                DashboardPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("applications_empty_card")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AppRegistration,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = VibeDesignTokens.Color.Cyan
                        )
                        Text(
                            text = "لا توجد تطبيقات مرتبطة بهذا المشروع",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VibeDesignTokens.Color.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "اربط تطبيقك (Android أو iOS) لمراقبة الأعطال وإعداد SDK.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeDesignTokens.Color.TextMuted
                        )
                        if (canManage && uiState.selectedProject != null) {
                            Button(
                                onClick = onAddApplication,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("applications_empty_add_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VibeDesignTokens.Color.Cyan,
                                    contentColor = VibeDesignTokens.Color.OnCyan
                                )
                            ) {
                                Text("ربط تطبيق جديد الآن", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("applications_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(uiState.applications, key = { it.id }) { app ->
                    ApplicationCard(
                        app = app,
                        canManage = canManage,
                        onNavigate = onNavigate,
                        onRequestDelete = { onRequestDeleteApp(app) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApplicationCard(
    app: Application,
    canManage: Boolean,
    onNavigate: (Screen) -> Unit,
    onRequestDelete: () -> Unit
) {
    val isAndroid = app.platform.equals("Android", ignoreCase = true)
    val platformColor = if (isAndroid) VibeDesignTokens.Color.Success else VibeDesignTokens.Color.Cyan
    val sdkConfigured = app.sdkSetupStatus == SdkSetupStatus.CONFIGURED
    val agentOnline = app.agentConnectionStatus == AgentConnectionStatus.ONLINE

    DashboardPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("application_card_${app.id}")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = platformColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                        border = BorderStroke(1.dp, platformColor.copy(alpha = 0.4f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isAndroid) Icons.Default.Android else Icons.Default.PhoneIphone,
                                contentDescription = null,
                                tint = platformColor
                            )
                        }
                    }
                    Column {
                        Text(
                            text = app.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = VibeDesignTokens.Color.TextPrimary
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMono,
                            color = VibeDesignTokens.Color.TextMuted
                        )
                    }
                }
                Surface(
                    color = platformColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                    border = BorderStroke(1.dp, platformColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = app.platform,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        color = platformColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = VibeDesignTokens.Color.Grid)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusChip(
                        text = "SDK: ${if (sdkConfigured) "مُهيّأ" else "غير مُهيّأ"}",
                        color = if (sdkConfigured) VibeDesignTokens.Color.Success else VibeDesignTokens.Color.Error,
                        tag = "application_sdk_status_${app.id}"
                    )
                    StatusChip(
                        text = "Agent: ${if (agentOnline) "متصل" else "غير متصل"}",
                        color = if (agentOnline) VibeDesignTokens.Color.Success else VibeDesignTokens.Color.TextMuted,
                        tag = "application_agent_status_${app.id}"
                    )
                }

                if (canManage) {
                    IconButton(
                        onClick = onRequestDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_application_${app.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف ${app.name}",
                            tint = VibeDesignTokens.Color.Error
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onNavigate(Screen.SetupGuide(app.projectId, app.id)) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                        border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VibeDesignTokens.Color.Cyan)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("دليل SDK", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { onNavigate(Screen.GitHubConnect(app.projectId)) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                        border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VibeDesignTokens.Color.Cyan)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("GitHub", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onNavigate(Screen.DebugSymbols(app.projectId)) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                        border = BorderStroke(1.dp, VibeDesignTokens.Color.Violet.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VibeDesignTokens.Color.TextPrimary)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("الرموز", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(
                        onClick = { onNavigate(Screen.LiveTesting) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = VibeDesignTokens.Color.Cyan.copy(alpha = 0.18f),
                            contentColor = VibeDesignTokens.Color.Cyan
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("اختبار", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
