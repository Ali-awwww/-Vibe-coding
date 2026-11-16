package com.example.presentation.ui.screens.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.Application
import com.example.domain.model.Project
import com.example.domain.model.Role
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddProjectSecurityDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, desc: String, retentionDays: Int, piiScrubbing: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var retentionDays by remember { mutableIntStateOf(90) }
    var piiScrubbing by remember { mutableStateOf(true) }
    val canSubmit = name.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("add_project_dialog"),
        containerColor = VibeDesignTokens.Color.SpacePanelRaised,
        titleContentColor = VibeDesignTokens.Color.TextPrimary,
        textContentColor = VibeDesignTokens.Color.TextMuted,
        title = { Text("إنشاء مشروع مطور آمن", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المشروع") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_project_name_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("الوصف (اختياري)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_project_desc_input")
                )

                Text(
                    text = "فترة الاحتفاظ بالتقارير:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.TextPrimary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(30, 60, 90, 180).forEach { days ->
                        FilterChip(
                            selected = retentionDays == days,
                            onClick = { retentionDays = days },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                            label = { Text("$days يوم", fontFamily = JetBrainsMono) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تنقيح البيانات الشخصية (PII)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VibeDesignTokens.Color.TextPrimary
                    )
                    Switch(checked = piiScrubbing, onCheckedChange = { piiScrubbing = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isNotEmpty()) {
                        onConfirm(trimmedName, description.trim(), retentionDays, piiScrubbing)
                    }
                },
                enabled = canSubmit,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("add_project_confirm_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeDesignTokens.Color.Cyan,
                    contentColor = VibeDesignTokens.Color.OnCyan
                )
            ) {
                Text("إنشاء المشروع", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("إلغاء", color = VibeDesignTokens.Color.TextMuted)
            }
        }
    )
}

@Composable
fun AddApplicationDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, platform: String, packageName: String) -> Unit
) {
    var appName by remember { mutableStateOf("") }
    var selectedPlatform by remember { mutableStateOf("Android") }
    var packageName by remember { mutableStateOf("") }
    val canSubmit = appName.trim().isNotEmpty() && packageName.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("add_application_dialog"),
        containerColor = VibeDesignTokens.Color.SpacePanelRaised,
        titleContentColor = VibeDesignTokens.Color.TextPrimary,
        textContentColor = VibeDesignTokens.Color.TextMuted,
        title = { Text("ربط تطبيق جديد بالمشروع", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("اسم التطبيق (e.g. My App)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_app_name_input")
                )

                Text(
                    text = "منصة التطبيق (Platform):",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    color = VibeDesignTokens.Color.TextPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Android", "iOS").forEach { plat ->
                        FilterChip(
                            selected = selectedPlatform == plat,
                            onClick = { selectedPlatform = plat },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("add_app_platform_${plat.lowercase()}"),
                            label = { Text(plat) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (plat == "Android") Icons.Default.Android else Icons.Default.PhoneIphone,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = {
                        Text(
                            if (selectedPlatform == "Android") {
                                "Package Name (e.g. com.company.app)"
                            } else {
                                "Bundle ID (e.g. com.company.app)"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_app_package_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = appName.trim()
                    val trimmedPkg = packageName.trim()
                    if (trimmedName.isNotEmpty() && trimmedPkg.isNotEmpty()) {
                        onConfirm(trimmedName, selectedPlatform, trimmedPkg)
                    }
                },
                enabled = canSubmit,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("add_app_confirm_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeDesignTokens.Color.Cyan,
                    contentColor = VibeDesignTokens.Color.OnCyan
                )
            ) {
                Text("ربط التطبيق", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("إلغاء", color = VibeDesignTokens.Color.TextMuted)
            }
        }
    )
}

@Composable
fun AddMemberDialog(
    onDismiss: () -> Unit,
    onConfirm: (email: String, role: Role) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(Role.DEVELOPER) }
    val trimmedEmail = email.trim()
    val canSubmit = trimmedEmail.isNotEmpty() && trimmedEmail.contains("@")

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("add_member_dialog"),
        containerColor = VibeDesignTokens.Color.SpacePanelRaised,
        titleContentColor = VibeDesignTokens.Color.TextPrimary,
        textContentColor = VibeDesignTokens.Color.TextMuted,
        title = { Text("إضافة عضو إلى الفريق", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("البريد الإلكتروني للمطور") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_member_email_input")
                )

                Text(
                    text = "صلاحية العضو (Role):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.TextPrimary
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Role.entries.filter { it != Role.OWNER }.forEach { role ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable { selectedRole = role }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = role.name,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JetBrainsMono,
                                    color = VibeDesignTokens.Color.TextPrimary
                                )
                                Text(
                                    text = when (role) {
                                        Role.ADMIN -> "إدارة الفريق والتكاملات وحذف التقارير القديمة"
                                        Role.DEVELOPER -> "استعراض الانهيارات وتشخيص الذكاء الاصطناعي وحلها"
                                        Role.VIEWER -> "قراءة ومراقبة لوحات التحكم والتحليلات فقط"
                                        Role.OWNER -> ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VibeDesignTokens.Color.TextMuted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canSubmit) {
                        onConfirm(trimmedEmail, selectedRole)
                    }
                },
                enabled = canSubmit,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("add_member_confirm_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeDesignTokens.Color.Cyan,
                    contentColor = VibeDesignTokens.Color.OnCyan
                )
            ) {
                Text("إضافة", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("إلغاء", color = VibeDesignTokens.Color.TextMuted)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSecurityPolicyDialog(
    currentRetention: Int,
    currentPii: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (days: Int, pii: Boolean) -> Unit
) {
    var retentionDays by remember { mutableIntStateOf(currentRetention) }
    var piiScrubbing by remember { mutableStateOf(currentPii) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("edit_security_policy_dialog"),
        containerColor = VibeDesignTokens.Color.SpacePanelRaised,
        titleContentColor = VibeDesignTokens.Color.TextPrimary,
        textContentColor = VibeDesignTokens.Color.TextMuted,
        title = { Text("تحديث سياسة الأمان والاحتفاظ", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "مدة الاحتفاظ بالبيانات:",
                    fontWeight = FontWeight.Bold,
                    color = VibeDesignTokens.Color.TextPrimary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(30, 60, 90, 180, 365).forEach { days ->
                        FilterChip(
                            selected = retentionDays == days,
                            onClick = { retentionDays = days },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                            label = { Text("$days يوم", fontFamily = JetBrainsMono) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تنقيح البيانات الشخصية (PII)",
                        color = VibeDesignTokens.Color.TextPrimary
                    )
                    Switch(checked = piiScrubbing, onCheckedChange = { piiScrubbing = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(retentionDays, piiScrubbing) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("edit_security_confirm_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeDesignTokens.Color.Cyan,
                    contentColor = VibeDesignTokens.Color.OnCyan
                )
            ) {
                Text("حفظ التغييرات", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("إلغاء", color = VibeDesignTokens.Color.TextMuted)
            }
        }
    )
}

@Composable
internal fun DeleteProjectConfirmDialog(
    project: Project,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("delete_project_dialog"),
        containerColor = VibeDesignTokens.Color.SpacePanelRaised,
        titleContentColor = VibeDesignTokens.Color.TextPrimary,
        textContentColor = VibeDesignTokens.Color.TextMuted,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = VibeDesignTokens.Color.Error) },
        title = { Text("تأكيد حذف المشروع", fontWeight = FontWeight.Bold) },
        text = {
            Text("هل أنت متأكد من رغبتك في حذف المشروع '${project.name}'؟ سيتم حذف جميع التطبيقات، تقارير الأعطال، والتكاملات نهائيًا ولا يمكن التراجع عن هذه العملية.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_project_confirm_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeDesignTokens.Color.Error,
                    contentColor = VibeDesignTokens.Color.TextPrimary
                )
            ) {
                Text("حذف نهائي", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("إلغاء", color = VibeDesignTokens.Color.TextMuted)
            }
        }
    )
}

@Composable
internal fun DeleteApplicationConfirmDialog(
    application: Application,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("delete_app_dialog"),
        containerColor = VibeDesignTokens.Color.SpacePanelRaised,
        titleContentColor = VibeDesignTokens.Color.TextPrimary,
        textContentColor = VibeDesignTokens.Color.TextMuted,
        icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = VibeDesignTokens.Color.Error) },
        title = { Text("حذف التطبيق", fontWeight = FontWeight.Bold) },
        text = {
            Text("سيتم إلغاء ربط التطبيق '${application.name}' (${application.packageName}) من هذا المشروع. هل تريد المتابعة؟")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_app_confirm_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeDesignTokens.Color.Error,
                    contentColor = VibeDesignTokens.Color.TextPrimary
                )
            ) {
                Text("تأكيد الحذف", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("إلغاء", color = VibeDesignTokens.Color.TextMuted)
            }
        }
    )
}
