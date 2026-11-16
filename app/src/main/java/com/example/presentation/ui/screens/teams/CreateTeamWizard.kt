package com.example.presentation.ui.screens.teams

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.*
import com.example.presentation.viewmodel.ModelTeamsUiState
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens

@Composable
internal fun CreateTeamWizard(
    uiState: ModelTeamsUiState,
    onUpdateInfo: (name: String, description: String, goal: String) -> Unit,
    onSelectCollaborationStyle: (CollaborationStyle) -> Unit,
    onUpdateSettings: (TeamSettings) -> Unit,
    onUpdateGovernance: (TeamGovernanceSettings) -> Unit,
    onAddMember: (AIModel, TeamRole) -> Unit,
    onRemoveMember: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("create_team_wizard_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TeamCyberPanel(
                modifier = Modifier.fillMaxWidth(),
                borderColor = VibeDesignTokens.Color.Violet.copy(alpha = 0.5f)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "تهيئة مهمة الفريق الجديد",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.TextPrimary
                    )
                    Text(
                        text = "حدّد هوية الفريق، نمط التعاون، قيود التكلفة، والأدوار المسندة لكل نموذج.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeDesignTokens.Color.TextMuted
                    )
                }
            }
        }

        item {
            TeamCyberPanel(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TeamSectionHeader(title = "الهوية والأهداف", badge = "REQUIRED")

                    OutlinedTextField(
                        value = uiState.newTeamName,
                        onValueChange = { onUpdateInfo(it, uiState.newTeamDescription, uiState.newTeamGoal) },
                        label = { Text("اسم الفريق *") },
                        isError = uiState.nameError != null,
                        supportingText = uiState.nameError?.let { msg -> { Text(msg) } },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("team_name_input")
                    )

                    OutlinedTextField(
                        value = uiState.newTeamDescription,
                        onValueChange = { onUpdateInfo(uiState.newTeamName, it, uiState.newTeamGoal) },
                        label = { Text("وصف الفريق") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("team_description_input")
                    )

                    OutlinedTextField(
                        value = uiState.newTeamGoal,
                        onValueChange = { onUpdateInfo(uiState.newTeamName, uiState.newTeamDescription, it) },
                        label = { Text("هدف الفريق الأساسي") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("team_goal_input")
                    )
                }
            }
        }

        item {
            TeamSectionHeader(title = "نمط التعاون", badge = uiState.collaborationStyle.name)
        }

        item {
            CollaborationStyleSelector(
                selectedStyle = uiState.collaborationStyle,
                onStyleSelected = onSelectCollaborationStyle
            )
        }

        item {
            TeamSettingsSection(
                settings = uiState.settings,
                onSettingsChanged = onUpdateSettings
            )
        }

        item {
            TeamGovernanceCreationSection(
                governance = uiState.governance,
                onGovernanceChanged = onUpdateGovernance
            )
        }

        item {
            TeamSectionHeader(
                title = "أعضاء الفريق المختارون",
                badge = "${uiState.selectedMembers.size}"
            )
            if (uiState.membersError != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = uiState.membersError,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.Error,
                    modifier = Modifier.testTag("team_members_error_text")
                )
            }
        }

        if (uiState.selectedMembers.isEmpty()) {
            item {
                TeamCyberPanel(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = VibeDesignTokens.Color.Grid
                ) {
                    Text(
                        text = "لم يُضَف أي عضو بعد. اختر نموذجاً من القائمة أدناه وحدد دوره.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeDesignTokens.Color.TextMuted
                    )
                }
            }
        } else {
            items(uiState.selectedMembers, key = { it.id }) { member ->
                val isConnected = member.providerId in uiState.connectedProviderIds
                TeamCyberPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selected_member_${member.id}"),
                    borderColor = if (isConnected) VibeDesignTokens.Color.Success.copy(alpha = 0.4f) else VibeDesignTokens.Color.Warning.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = if (isConnected) VibeDesignTokens.Color.Success else VibeDesignTokens.Color.Warning
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = member.name,
                                fontWeight = FontWeight.Bold,
                                color = VibeDesignTokens.Color.TextPrimary
                            )
                            Text(
                                text = "${member.role.labelAr} • ${if (isConnected) "مزود متصل" else "مهيّأ (غير متصل)"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = VibeDesignTokens.Color.TextMuted
                            )
                        }
                        IconButton(
                            onClick = { onRemoveMember(member.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("remove_member_${member.id}")
                        ) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "إزالة العضو", tint = VibeDesignTokens.Color.Error)
                        }
                    }
                }
            }
        }

        item {
            TeamSectionHeader(
                title = "النماذج المتاحة للإضافة",
                badge = "${uiState.availableModels.size}"
            )
        }

        if (uiState.availableModels.isEmpty()) {
            item {
                TeamCyberPanel(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = VibeDesignTokens.Color.Warning.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = "لا توجد نماذج مهيّأة لهذا الحساب. أضف مزود ذكاء اصطناعي من شاشة AIModels أولاً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeDesignTokens.Color.Warning
                    )
                }
            }
        } else {
            items(uiState.availableModels, key = { it.id }) { model ->
                var showRoleDialog by remember { mutableStateOf(false) }
                val isProviderConnected = model.providerId in uiState.connectedProviderIds

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("available_model_card_${model.id}"),
                    onClick = { showRoleDialog = true },
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
                    colors = CardDefaults.cardColors(containerColor = VibeDesignTokens.Color.SpacePanel),
                    border = BorderStroke(1.dp, VibeDesignTokens.Color.Cyan.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = model.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = VibeDesignTokens.Color.TextPrimary
                            )
                            Text(
                                text = model.apiId,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMono,
                                color = VibeDesignTokens.Color.TextMuted
                            )
                        }
                        StatusPill(
                            label = if (isProviderConnected) "متصل" else "مهيّأ",
                            accent = if (isProviderConnected) VibeDesignTokens.Color.Success else VibeDesignTokens.Color.Warning
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "إسناد دور", tint = VibeDesignTokens.Color.Cyan)
                    }
                }

                if (showRoleDialog) {
                    AlertDialog(
                        onDismissRequest = { showRoleDialog = false },
                        containerColor = VibeDesignTokens.Color.SpacePanelRaised,
                        title = {
                            Text(
                                text = "اختر دور ${model.name}",
                                color = VibeDesignTokens.Color.TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                                items(TeamRole.values()) { role ->
                                    TextButton(
                                        onClick = {
                                            onAddMember(model, role)
                                            showRoleDialog = false
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("pick_role_${role.name}")
                                    ) {
                                        Text(
                                            text = role.labelAr,
                                            textAlign = TextAlign.Start,
                                            color = VibeDesignTokens.Color.TextPrimary,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showRoleDialog = false }) {
                                Text("إلغاء", color = VibeDesignTokens.Color.TextMuted)
                            }
                        }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !uiState.isMutating,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("cancel_create_team_button")
                ) {
                    Text("إلغاء")
                }
                Button(
                    onClick = onSave,
                    enabled = !uiState.isMutating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibeDesignTokens.Color.Cyan,
                        contentColor = VibeDesignTokens.Color.OnCyan
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .minimumInteractiveComponentSize()
                        .testTag("save_team_button")
                ) {
                    if (uiState.isMutating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = VibeDesignTokens.Color.OnCyan
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("حفظ الفريق", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
