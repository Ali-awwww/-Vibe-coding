package com.example.presentation.ui.screens.teams

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ModelTeam
import com.example.domain.model.TeamMember
import com.example.domain.model.TeamTemplates
import com.example.presentation.viewmodel.ModelTeamsUiState
import com.example.ui.theme.VibeDesignTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TeamsListSection(
    uiState: ModelTeamsUiState,
    onTeamClick: (ModelTeam) -> Unit,
    onUsageClick: (ModelTeam) -> Unit,
    onDeleteRequest: (ModelTeam) -> Unit,
    onTemplateClick: (ModelTeam) -> Unit,
    onStartCreate: () -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("model_teams_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TeamsNetworkStatusBanner(uiState = uiState)
        }

        if (uiState.error != null) {
            item {
                TeamsErrorBanner(
                    message = uiState.error,
                    onRetry = onRefresh,
                    onDismiss = onDismissError
                )
            }
        }

        item {
            TeamSectionHeader(
                title = "قوالب الفرق الجاهزة",
                badge = "TEMPLATES",
                isLoading = uiState.isLoading
            )
        }

        item {
            TeamCard(
                team = TeamTemplates.CRASH_INVESTIGATION_TEAM,
                connectedProviderIds = uiState.connectedProviderIds,
                isMutating = uiState.isMutating,
                onClick = { onTemplateClick(TeamTemplates.CRASH_INVESTIGATION_TEAM) },
                onDelete = {},
                isTemplate = true
            )
        }

        item {
            TeamCard(
                team = TeamTemplates.VIBE_DEVELOPMENT_TEAM,
                connectedProviderIds = uiState.connectedProviderIds,
                isMutating = uiState.isMutating,
                onClick = { onTemplateClick(TeamTemplates.VIBE_DEVELOPMENT_TEAM) },
                onDelete = {},
                isTemplate = true
            )
        }

        item {
            TeamSectionHeader(
                title = "فرقي المخصصة",
                badge = "${uiState.teams.size}",
                isLoading = false
            )
        }

        if (uiState.teams.isEmpty() && !uiState.isLoading) {
            item {
                EmptyTeamsRosterCard(onCreateTeam = onStartCreate)
            }
        } else {
            items(uiState.teams, key = { it.id }) { team ->
                TeamCard(
                    team = team,
                    connectedProviderIds = uiState.connectedProviderIds,
                    isMutating = uiState.isMutating,
                    onClick = { onTeamClick(team) },
                    onUsageClick = { onUsageClick(team) },
                    onDelete = { onDeleteRequest(team) }
                )
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TeamCard(
    team: ModelTeam,
    connectedProviderIds: Set<String>,
    isMutating: Boolean,
    onClick: () -> Unit,
    onUsageClick: () -> Unit = {},
    onDelete: () -> Unit,
    isTemplate: Boolean = false
) {
    val connectedMembersCount = team.members.count {
        it.providerId.isNotBlank() && it.providerId in connectedProviderIds
    }
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isTemplate) "template_card_${team.id}" else "team_card_${team.id}"),
        onClick = onClick,
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(
            containerColor = if (isTemplate) VibeDesignTokens.Color.SpacePanelRaised else VibeDesignTokens.Color.SpacePanel
        ),
        border = BorderStroke(
            1.dp,
            if (isTemplate) VibeDesignTokens.Color.Violet.copy(alpha = 0.45f)
            else VibeDesignTokens.Color.Cyan.copy(alpha = 0.28f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = (if (isTemplate) VibeDesignTokens.Color.Violet else VibeDesignTokens.Color.Cyan).copy(alpha = 0.16f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isTemplate) Icons.Default.AutoAwesome else Icons.Default.Groups,
                            contentDescription = null,
                            tint = if (isTemplate) VibeDesignTokens.Color.Violet else VibeDesignTokens.Color.Cyan
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = team.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.TextPrimary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isTemplate) {
                            StatusPill(label = "قالب جاهز", accent = VibeDesignTokens.Color.Violet)
                        } else {
                            StatusPill(
                                label = if (connectedMembersCount > 0) "متصل ($connectedMembersCount/${team.members.size})" else "مهيّأ فقط (${team.members.size})",
                                accent = if (connectedMembersCount > 0) VibeDesignTokens.Color.Success else VibeDesignTokens.Color.Warning,
                                tag = "team_readiness_${team.id}"
                            )
                            if (team.governance.freeOnly) {
                                StatusPill(label = "مجاني فقط", accent = VibeDesignTokens.Color.Success)
                            }
                        }
                    }
                }

                if (!isTemplate) {
                    IconButton(
                        onClick = onUsageClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("team_usage_${team.id}")
                    ) {
                        Icon(Icons.Default.QueryStats, contentDescription = "إحصائيات استخدام الفريق", tint = VibeDesignTokens.Color.Cyan)
                    }
                    IconButton(
                        onClick = onDelete,
                        enabled = !isMutating,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_team_${team.id}")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الفريق", tint = VibeDesignTokens.Color.Error)
                    }
                } else {
                    Button(
                        onClick = onClick,
                        enabled = !isMutating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VibeDesignTokens.Color.Violet,
                            contentColor = VibeDesignTokens.Color.TextPrimary
                        ),
                        modifier = Modifier.testTag("use_template_${team.id}")
                    ) {
                        Text("استخدام القالب", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (team.description.isNotBlank()) {
                Text(
                    text = team.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                team.members.take(3).forEach { member ->
                    MemberBadge(member = member)
                }
                if (team.members.size > 3) {
                    StatusPill(label = "+${team.members.size - 3} آخرون", accent = VibeDesignTokens.Color.TextMuted)
                }
            }

            if (!isTemplate) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp), tint = VibeDesignTokens.Color.TextMuted)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "أنشئت في: ${dateFormat.format(Date(team.createdAt))} • النمط: ${team.collaborationStyle.labelAr}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VibeDesignTokens.Color.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
internal fun MemberBadge(member: TeamMember) {
    Surface(
        color = VibeDesignTokens.Color.SpacePanelRaised,
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
        border = BorderStroke(1.dp, VibeDesignTokens.Color.Grid)
    ) {
        Text(
            text = member.role.labelAr,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = VibeDesignTokens.Color.TextPrimary
        )
    }
}

@Composable
internal fun EmptyTeamsRosterCard(onCreateTeam: () -> Unit) {
    TeamCyberPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_teams_roster_card"),
        borderColor = VibeDesignTokens.Color.Grid
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.GroupWork,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = VibeDesignTokens.Color.Cyan
            )
            Text(
                text = "لا توجد فرق مخصصة بعد",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VibeDesignTokens.Color.TextPrimary
            )
            Text(
                text = "أنشئ فريقك الخاص أو فعّل أحد القوالب الجاهزة بالأعلى للبدء.",
                style = MaterialTheme.typography.bodySmall,
                color = VibeDesignTokens.Color.TextMuted
            )
            Button(
                onClick = onCreateTeam,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeDesignTokens.Color.Cyan,
                    contentColor = VibeDesignTokens.Color.OnCyan
                ),
                modifier = Modifier.testTag("empty_create_team_button")
            ) {
                Icon(Icons.Default.GroupAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("إنشاء فريق جديد", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun DeleteTeamConfirmDialog(
    team: ModelTeam,
    isMutating: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VibeDesignTokens.Color.SpacePanelRaised,
        title = {
            Text(
                text = "تأكيد حذف الفريق",
                color = VibeDesignTokens.Color.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "سيتم حذف الفريق \"${team.name}\" وتشكيلته الحالية نهائياً. لا يمكن التراجع عن هذا الإجراء.",
                color = VibeDesignTokens.Color.TextMuted
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isMutating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeDesignTokens.Color.Error,
                    contentColor = VibeDesignTokens.Color.TextPrimary
                ),
                modifier = Modifier.testTag("confirm_delete_team_button")
            ) {
                Text("حذف الفريق")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isMutating) {
                Text("إلغاء", color = VibeDesignTokens.Color.TextMuted)
            }
        }
    )
}
