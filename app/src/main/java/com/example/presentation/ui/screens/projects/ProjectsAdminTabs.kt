package com.example.presentation.ui.screens.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AccessControl
import com.example.domain.model.ProjectMember
import com.example.domain.model.Role
import com.example.presentation.ui.screens.dashboard.DashboardPanel
import com.example.presentation.ui.screens.dashboard.DashboardSectionHeader
import com.example.presentation.viewmodel.ProjectsUiState
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TeamMembersTab(
    uiState: ProjectsUiState,
    onAddMember: () -> Unit,
    onUpdateRole: (ProjectMember, Role) -> Unit,
    onRemoveMember: (ProjectMember) -> Unit
) {
    val canManage = AccessControl.canManageMembers(uiState.userRoleInSelectedProject)

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
                    text = "إدارة الفريق والصلاحيات",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.TextPrimary
                )
                Text(
                    text = "تحديد صلاحيات الوصول حسب الدور في المشروع",
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeDesignTokens.Color.TextMuted
                )
            }

            if (canManage) {
                Button(
                    onClick = onAddMember,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("add_member_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibeDesignTokens.Color.Cyan,
                        contentColor = VibeDesignTokens.Color.OnCyan
                    )
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("إضافة عضو", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (uiState.members.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا يوجد أعضاء مضافون بعد في هذا المشروع.", color = VibeDesignTokens.Color.TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("team_members_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.members, key = { it.userId }) { member ->
                    val isOwner = member.role == Role.OWNER
                    val roleColor = when (member.role) {
                        Role.OWNER -> VibeDesignTokens.Color.Cyan
                        Role.ADMIN -> VibeDesignTokens.Color.Violet
                        Role.DEVELOPER -> VibeDesignTokens.Color.Success
                        Role.VIEWER -> VibeDesignTokens.Color.TextMuted
                    }
                    DashboardPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("member_card_${member.userId}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = roleColor.copy(alpha = 0.2f),
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, roleColor.copy(alpha = 0.5f)),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = member.displayName.ifBlank { member.userEmail }.take(1).uppercase(),
                                            color = roleColor,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = JetBrainsMono
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = member.displayName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = VibeDesignTokens.Color.TextPrimary
                                    )
                                    Text(
                                        text = member.userEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VibeDesignTokens.Color.TextMuted
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StatusChip(
                                    text = member.role.name,
                                    color = roleColor,
                                    tag = "member_role_${member.userId}"
                                )

                                if (canManage && !isOwner) {
                                    var showMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { showMenu = true },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = "خيارات العضو",
                                                tint = VibeDesignTokens.Color.TextMuted
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            Role.entries.filter { it != Role.OWNER }.forEach { roleOption ->
                                                DropdownMenuItem(
                                                    text = { Text("تعيين: ${roleOption.name}") },
                                                    onClick = {
                                                        onUpdateRole(member, roleOption)
                                                        showMenu = false
                                                    }
                                                )
                                            }
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text("إزالة من الفريق", color = VibeDesignTokens.Color.Error) },
                                                onClick = {
                                                    onRemoveMember(member)
                                                    showMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SecurityAndRetentionTab(
    uiState: ProjectsUiState,
    onEditPolicy: () -> Unit,
    onPurgeData: () -> Unit,
    onDeleteProject: () -> Unit
) {
    val project = uiState.selectedProject
    val canManage = AccessControl.canManageProject(uiState.userRoleInSelectedProject)
    val canDelete = AccessControl.canDeleteProject(uiState.userRoleInSelectedProject)

    if (project == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("يرجى اختيار مشروع لعرض سياسة الأمان الخاصة به.", color = VibeDesignTokens.Color.TextMuted)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DashboardPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سياسة الاحتفاظ بالبيانات (Retention)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (canManage) {
                        TextButton(
                            onClick = onEditPolicy,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("security_edit_policy_button")
                        ) {
                            Text("تعديل", color = VibeDesignTokens.Color.Cyan)
                        }
                    }
                }

                Text(
                    text = "يتم الاحتفاظ بتقارير الأعطال لمدة ${project.dataRetentionDays} يومًا. التقارير الأقدم يتم حذفها تلقائيًا لحماية الخصوصية ومطابقة المعايير.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VibeDesignTokens.Color.TextMuted
                )

                if (canManage) {
                    FilledTonalButton(
                        onClick = onPurgeData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("security_purge_data_button"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = VibeDesignTokens.Color.Cyan.copy(alpha = 0.16f),
                            contentColor = VibeDesignTokens.Color.Cyan
                        )
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("تطهير البيانات القديمة يدويًا الآن")
                    }
                }
            }
        }

        DashboardPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = VibeDesignTokens.Color.Success)
                    Text(
                        text = "تنقيح البيانات الشخصية (PII Scrubbing)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.TextPrimary
                    )
                }

                Text(
                    text = "حجب تلقائي لكلمات المرور، رموز التحقق، البريد الإلكتروني، وعناوين IP من رسائل الأخطاء وتتبع المكدس قبل التخزين.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VibeDesignTokens.Color.TextMuted
                )

                val piiColor = if (project.piiScrubbingEnabled) VibeDesignTokens.Color.Success else VibeDesignTokens.Color.Error
                Surface(
                    color = piiColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(VibeDesignTokens.Shape.Compact),
                    border = BorderStroke(1.dp, piiColor.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (project.piiScrubbingEnabled) {
                            "الحالة: التنقيح الأمني مفعّل تلقائيًا لكافة الانهيارات"
                        } else {
                            "الحالة: التنقيح الأمني معطل"
                        },
                        color = piiColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        if (canDelete) {
            DashboardPanel(
                modifier = Modifier.fillMaxWidth(),
                borderColor = VibeDesignTokens.Color.Error.copy(alpha = 0.55f)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "منطقة الخطر: حذف المشروع",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VibeDesignTokens.Color.Error
                    )
                    Text(
                        text = "حذف المشروع يزيل نهائيًا كافة بيانات التطبيقات والأعطال وحسابات الفريق المرتبطة به.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeDesignTokens.Color.TextMuted
                    )
                    Button(
                        onClick = onDeleteProject,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("security_delete_project_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VibeDesignTokens.Color.Error,
                            contentColor = VibeDesignTokens.Color.TextPrimary
                        )
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("حذف المشروع نهائيًا", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AuditLogTab(uiState: ProjectsUiState) {
    val logs = uiState.auditLogs
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DashboardSectionHeader(
            title = "سجل التدقيق والأمان",
            badge = "${logs.size}"
        )

        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد سجلات تدقيق حتى الآن لهذا المشروع.", color = VibeDesignTokens.Color.TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("audit_logs_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    DashboardPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusChip(
                                    text = log.action,
                                    color = VibeDesignTokens.Color.Cyan,
                                    tag = "audit_action_${log.id}"
                                )
                                Text(
                                    text = dateFormat.format(Date(log.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMono,
                                    color = VibeDesignTokens.Color.TextMuted
                                )
                            }
                            Text(
                                text = log.details,
                                style = MaterialTheme.typography.bodyMedium,
                                color = VibeDesignTokens.Color.TextPrimary
                            )
                            if (log.userEmail.isNotBlank()) {
                                Text(
                                    text = "بواسطة: ${log.userEmail}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VibeDesignTokens.Color.TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
