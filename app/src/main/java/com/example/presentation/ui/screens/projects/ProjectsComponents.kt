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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Project
import com.example.presentation.ui.screens.dashboard.DashboardPanel
import com.example.presentation.viewmodel.ProjectsUiState
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens

@Composable
internal fun ProjectsAuthRequiredCard(
    onSignIn: () -> Unit
) {
    DashboardPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("projects_auth_required_card"),
        borderColor = VibeDesignTokens.Color.Violet.copy(alpha = 0.55f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = VibeDesignTokens.Color.Violet)
                Text(
                    text = "تسجيل الدخول مطلوب",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.TextPrimary
                )
            }
            Text(
                text = "لإدارة المشاريع والتطبيقات وأعضاء الفريق وسياسات الأمان، يرجى تسجيل الدخول أولاً.",
                style = MaterialTheme.typography.bodySmall,
                color = VibeDesignTokens.Color.TextMuted
            )
            Button(
                onClick = onSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("projects_sign_in_button"),
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeDesignTokens.Color.Cyan,
                    contentColor = VibeDesignTokens.Color.OnCyan
                )
            ) {
                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("تسجيل الدخول", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun ProjectsErrorBanner(
    error: String,
    isCloudConfigured: Boolean,
    onRetry: () -> Unit
) {
    DashboardPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("projects_error_banner"),
        borderColor = VibeDesignTokens.Color.Error.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = VibeDesignTokens.Color.Error)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextPrimary
                )
                if (!isCloudConfigured) {
                    Text(
                        text = "CLOUD UNCONFIGURED • LOCAL CACHE MODE",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        color = VibeDesignTokens.Color.Warning
                    )
                }
            }
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("projects_retry_button"),
                border = BorderStroke(1.dp, VibeDesignTokens.Color.Error.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VibeDesignTokens.Color.Error)
            ) {
                Text("تحديث")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProjectsListTab(
    uiState: ProjectsUiState,
    onSelectProject: (Project) -> Unit,
    onOpenIntegrations: (Project) -> Unit,
    onOpenCrashes: (Project) -> Unit,
    onCreateProject: () -> Unit
) {
    if (uiState.projects.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            DashboardPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("projects_empty_card")
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = VibeDesignTokens.Color.Cyan
                    )
                    Text(
                        text = "لا توجد مشاريع مخصصة لحسابك بعد",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "أنشئ مشروع مطور معزول لإدارة التطبيقات، الأعطال، والفرق.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeDesignTokens.Color.TextMuted
                    )
                    Button(
                        onClick = onCreateProject,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("projects_empty_create_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VibeDesignTokens.Color.Cyan,
                            contentColor = VibeDesignTokens.Color.OnCyan
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إنشاء أول مشروع", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("projects_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(uiState.projects, key = { it.id }) { project ->
            val isSelected = uiState.selectedProject?.id == project.id
            Card(
                onClick = { onSelectProject(project) },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("project_card_${project.id}"),
                shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        VibeDesignTokens.Color.SpacePanelRaised
                    } else {
                        VibeDesignTokens.Color.SpacePanel
                    }
                ),
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) {
                        VibeDesignTokens.Color.Cyan
                    } else {
                        VibeDesignTokens.Color.Cyan.copy(alpha = 0.22f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = VibeDesignTokens.Color.TextPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = VibeDesignTokens.Color.Violet.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                            border = BorderStroke(1.dp, VibeDesignTokens.Color.Violet.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "احتفاظ: ${project.dataRetentionDays} يوم",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMono,
                                color = VibeDesignTokens.Color.TextPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (project.description.isNotBlank()) {
                        Text(
                            text = project.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = VibeDesignTokens.Color.TextMuted
                        )
                    }

                    HorizontalDivider(color = VibeDesignTokens.Color.Grid)

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (project.piiScrubbingEnabled) {
                            Surface(
                                color = VibeDesignTokens.Color.Success.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                                border = BorderStroke(1.dp, VibeDesignTokens.Color.Success.copy(alpha = 0.4f)),
                                modifier = Modifier.semantics(mergeDescendants = true) {}
                            ) {
                                Text(
                                    text = "تنقيح PII نشط",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = VibeDesignTokens.Color.Success,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onOpenIntegrations(project) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("project_integrations_${project.id}"),
                                border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VibeDesignTokens.Color.Cyan)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("مصادر الأعطال")
                            }
                            FilledTonalButton(
                                onClick = { onOpenCrashes(project) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("project_crashes_${project.id}"),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = VibeDesignTokens.Color.Cyan.copy(alpha = 0.18f),
                                    contentColor = VibeDesignTokens.Color.Cyan
                                )
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("الانهيارات")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatusChip(text: String, color: Color, tag: String) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .testTag(tag)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMono,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
