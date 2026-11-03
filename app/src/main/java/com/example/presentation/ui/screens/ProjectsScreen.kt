package com.example.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.domain.model.*
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.ProjectsUiState
import com.example.presentation.viewmodel.ProjectsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    navController: NavController,
    viewModel: ProjectsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddProjectDialog by remember { mutableStateOf(false) }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showSecurityPolicyDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Projects, 1: Applications, 2: Team, 3: Security, 4: Audit

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message, uiState.error) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotifications()
        }
        uiState.error?.let {
            snackbarHostState.showSnackbar("خطأ: $it")
            viewModel.clearNotifications()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("إدارة المشاريع والتطبيقات")
                        uiState.selectedProject?.let { proj ->
                            Text(
                                text = "${proj.name} • دورك: ${uiState.userRoleInSelectedProject?.name ?: "MEMBER"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddProjectDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New Project")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Project Selector Bar if multiple projects exist
            if (uiState.projects.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = uiState.projects.indexOfFirst { it.id == uiState.selectedProject?.id }.coerceAtLeast(0),
                    edgePadding = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    uiState.projects.forEach { proj ->
                        Tab(
                            selected = uiState.selectedProject?.id == proj.id,
                            onClick = { viewModel.selectProject(proj.id) },
                            text = { Text(proj.name) }
                        )
                    }
                }
            }

            // Sub-tabs for the active project
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("المشاريع") },
                    icon = { Icon(Icons.Default.Folder, null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("التطبيقات") },
                    icon = { Icon(Icons.Default.Apps, null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("أعضاء الفريق") },
                    icon = { Icon(Icons.Default.Group, null) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("الأمان والاحتفاظ") },
                    icon = { Icon(Icons.Default.Shield, null) }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("سجل التدقيق") },
                    icon = { Icon(Icons.Default.History, null) }
                )
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    0 -> ProjectsListTab(
                        uiState = uiState,
                        onSelectProject = { viewModel.selectProject(it.id) },
                        onOpenIntegrations = { navController.navigate(Screen.Integrations(it.id)) },
                        onOpenCrashes = { navController.navigate(Screen.Crashes(projectId = it.id)) }
                    )
                    1 -> ApplicationsTab(
                        uiState = uiState,
                        navController = navController,
                        onAddApplication = { showAddAppDialog = true },
                        onDeleteApp = { viewModel.deleteApplication(it.id) }
                    )
                    2 -> TeamMembersTab(
                        uiState = uiState,
                        onAddMember = { showAddMemberDialog = true },
                        onUpdateRole = { member, role ->
                            uiState.selectedProject?.let { viewModel.updateMemberRole(it.id, member.userId, role) }
                        },
                        onRemoveMember = { member ->
                            uiState.selectedProject?.let { viewModel.removeMember(it.id, member.userId) }
                        }
                    )
                    3 -> SecurityAndRetentionTab(
                        uiState = uiState,
                        onEditPolicy = { showSecurityPolicyDialog = true },
                        onPurgeData = {
                            uiState.selectedProject?.let { viewModel.purgeOldCrashes(it.id) }
                        },
                        onDeleteProject = { showDeleteConfirmDialog = true }
                    )
                    4 -> AuditLogTab(uiState = uiState)
                }
            }
        }

        // Add Project Dialog
        if (showAddProjectDialog) {
            AddProjectSecurityDialog(
                onDismiss = { showAddProjectDialog = false },
                onConfirm = { name, desc, retentionDays, piiScrubbing ->
                    viewModel.addProject(name, desc, retentionDays, piiScrubbing)
                    showAddProjectDialog = false
                }
            )
        }

        // Add Application Dialog
        if (showAddAppDialog && uiState.selectedProject != null) {
            AddApplicationDialog(
                onDismiss = { showAddAppDialog = false },
                onConfirm = { name, platform, pkg ->
                    viewModel.addApplication(uiState.selectedProject!!.id, name, platform, pkg)
                    showAddAppDialog = false
                }
            )
        }

        // Add Member Dialog
        if (showAddMemberDialog) {
            AddMemberDialog(
                onDismiss = { showAddMemberDialog = false },
                onConfirm = { email, role ->
                    uiState.selectedProject?.let { viewModel.addMember(it.id, email, role) }
                    showAddMemberDialog = false
                }
            )
        }

        // Security Policy Dialog
        if (showSecurityPolicyDialog && uiState.selectedProject != null) {
            val proj = uiState.selectedProject!!
            EditSecurityPolicyDialog(
                currentRetention = proj.dataRetentionDays,
                currentPii = proj.piiScrubbingEnabled,
                onDismiss = { showSecurityPolicyDialog = false },
                onConfirm = { days, pii ->
                    viewModel.updateSecurityPolicy(proj.id, days, pii)
                    showSecurityPolicyDialog = false
                }
            )
        }

        // Delete Confirm Dialog
        if (showDeleteConfirmDialog && uiState.selectedProject != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("تأكيد حذف المشروع") },
                text = {
                    Text("هل أنت متأكد من رغبتك في حذف المشروع '${uiState.selectedProject?.name}'؟ سيتم حذف جميع التطبيقات، تقارير الأعطال، والتكاملات نهائيًا ولا يمكن التراجع عن هذه العملية.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            uiState.selectedProject?.let { viewModel.deleteProject(it.id) }
                            showDeleteConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("حذف نهائي")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}

@Composable
fun ProjectsListTab(
    uiState: ProjectsUiState,
    onSelectProject: (Project) -> Unit,
    onOpenIntegrations: (Project) -> Unit,
    onOpenCrashes: (Project) -> Unit
) {
    if (uiState.projects.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Text("لا توجد مشاريع مخصصة لحسابك بعد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("اضغط على زر الإضافة أعلاه لإنشاء مشروع مطور جديد مشفر ومعزول.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.projects, key = { it.id }) { project ->
                val isSelected = uiState.selectedProject?.id == project.id
                Card(
                    onClick = { onSelectProject(project) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(project.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "احتفاظ: ${project.dataRetentionDays} يوم",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (project.description.isNotBlank()) {
                            Text(project.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (project.piiScrubbingEnabled) {
                                    Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            "تنقيح PII نشط",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF2E7D32),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onOpenIntegrations(project) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("مصادر الأعطال")
                                }
                                FilledTonalButton(
                                    onClick = { onOpenCrashes(project) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.BugReport, null, modifier = Modifier.size(16.dp))
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
}

@Composable
fun TeamMembersTab(
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
            Column {
                Text("إدارة الفريق والصلاحيات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("تحديد صلاحيات الوصول حسب الدور في المشروع", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (canManage) {
                Button(onClick = onAddMember) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("إضافة عضو")
                }
            }
        }

        if (uiState.members.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا يوجد أعضاء مضافون بعد في هذا المشروع.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.members, key = { it.userId }) { member ->
                    val isOwner = member.role == Role.OWNER
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    color = when (member.role) {
                                        Role.OWNER -> MaterialTheme.colorScheme.primary
                                        Role.ADMIN -> MaterialTheme.colorScheme.tertiary
                                        Role.DEVELOPER -> MaterialTheme.colorScheme.secondary
                                        Role.VIEWER -> MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            member.displayName.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Column {
                                    Text(member.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Text(member.userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = member.role.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                if (canManage && !isOwner) {
                                    var showMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { showMenu = true }) {
                                            Icon(Icons.Default.MoreVert, null)
                                        }
                                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                            Role.values().filter { it != Role.OWNER }.forEach { roleOption ->
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
                                                text = { Text("إزالة من الفريق", color = MaterialTheme.colorScheme.error) },
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
fun SecurityAndRetentionTab(
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
            Text("يرجى اختيار مشروع لعرض سياسة الأمان الخاصة به.")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Data Retention Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("سياسة الاحتفاظ بالبيانات (Retention Policy)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (canManage) {
                        TextButton(onClick = onEditPolicy) {
                            Text("تعديل")
                        }
                    }
                }

                Text(
                    "يتم الاحتفاظ بتقارير الأعطال لمدة ${project.dataRetentionDays} يومًا. التقارير الأقدم يتم حذفها تلقائيًا لحماية الخصوصية ومطابقة المعايير.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (canManage) {
                    FilledTonalButton(
                        onClick = onPurgeData,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CleaningServices, null)
                        Spacer(Modifier.width(8.dp))
                        Text("تطهير البيانات القديمة يدويًا الآن")
                    }
                }
            }
        }

        // PII Scrubbing Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF2E7D32))
                    Text("تنقيح البيانات الشخصية والحساسة (PII Scrubbing)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Text(
                    "تنقيح وحجب تلقائي لكلمات المرور، رموز التحقق (Auth Tokens)، البريد الإلكتروني، وعناوين IP من رسائل الأخطاء وتتبعات المكدس (Stack Traces) قبل تخزينها.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = if (project.piiScrubbingEnabled) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (project.piiScrubbingEnabled) "الحالة: التنقيح الأمني مفعّل تلقائيًا لكافة الانهيارات" else "الحالة: التنقيح الأمني معطل",
                        color = if (project.piiScrubbingEnabled) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Project Deletion Card (Owner only)
        if (canDelete) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("منطقة الخطر: حذف المشروع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("حذف المشروع يزيل نهائيًا كافة بيانات التطبيقات والأعطال وحسابات الفريق المرتبطة به وفق سياسة الحذف المعتمدة.", style = MaterialTheme.typography.bodySmall)

                    Button(
                        onClick = onDeleteProject,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, null)
                        Spacer(Modifier.width(8.dp))
                        Text("حذف المشروع نهائيًا")
                    }
                }
            }
        }
    }
}

@Composable
fun AuditLogTab(uiState: ProjectsUiState) {
    val logs = uiState.auditLogs
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("سجل التدقيق والأمان (Audit Log)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${logs.size} عمليات مسجلة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد سجلات تدقيق حتى الآن لهذا المشروع.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = log.action,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = dateFormat.format(Date(log.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Text(
                                text = log.details,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            if (log.userEmail.isNotBlank()) {
                                Text(
                                    text = "بواسطة: ${log.userEmail}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddProjectSecurityDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, desc: String, retentionDays: Int, piiScrubbing: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var retentionDays by remember { mutableIntStateOf(90) }
    var piiScrubbing by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء مشروع مطور آمن") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المشروع") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("الوصف") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("فترة الاحتفاظ بالتقارير:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 90, 180).forEach { days ->
                        FilterChip(
                            selected = retentionDays == days,
                            onClick = { retentionDays = days },
                            label = { Text("$days يوم") }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تنقيح البيانات الشخصية (PII)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = piiScrubbing, onCheckedChange = { piiScrubbing = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, description, retentionDays, piiScrubbing)
                    }
                }
            ) {
                Text("إنشاء المشروع")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة عضو إلى الفريق") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("البريد الإلكتروني للمطور") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("صلاحية العضو (Role):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Role.values().filter { it != Role.OWNER }.forEach { role ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(role.name, fontWeight = FontWeight.Bold)
                                Text(
                                    when (role) {
                                        Role.ADMIN -> "إدارة الفريق والتكاملات وحذف التقارير القديمة"
                                        Role.DEVELOPER -> "استعراض الانهيارات وتشخيص الذكاء الاصطناعي وحلها"
                                        Role.VIEWER -> "قراءة ومراقبة لوحات التحكم والتحليلات فقط"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (email.isNotBlank()) onConfirm(email, selectedRole) }) {
                Text("إضافة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

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
        title = { Text("تحديث سياسة الأمان والاحتفاظ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("مدة الاحتفاظ بالبيانات:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 90, 180, 365).forEach { days ->
                        FilterChip(
                            selected = retentionDays == days,
                            onClick = { retentionDays = days },
                            label = { Text("$days يوم") }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تنقيح البيانات الشخصية (PII)")
                    Switch(checked = piiScrubbing, onCheckedChange = { piiScrubbing = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(retentionDays, piiScrubbing) }) {
                Text("حفظ التغييرات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun ApplicationsTab(
    uiState: ProjectsUiState,
    navController: NavController,
    onAddApplication: () -> Unit,
    onDeleteApp: (Application) -> Unit
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
            Column {
                Text("التطبيقات المرتبطة بالمشروع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    uiState.selectedProject?.let { "إدارة تطبيقات Android و iOS في ${it.name}" } ?: "اختر مشروعًا لإدارة تطبيقاته",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canManage && uiState.selectedProject != null) {
                Button(onClick = onAddApplication) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ربط تطبيق")
                }
            }
        }

        if (uiState.applications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AppRegistration, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(16.dp))
                    Text("لا توجد تطبيقات مرتبطة بهذا المشروع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "اضغط على 'ربط تطبيق' لإضافة تطبيقك (Android أو iOS) ومراقبته وتشخيص انهياراته بواسطة الذكاء الاصطناعي.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    if (canManage && uiState.selectedProject != null) {
                        Button(onClick = onAddApplication) {
                            Text("ربط تطبيق جديد الآن")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(uiState.applications, key = { it.id }) { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        color = if (app.platform.equals("Android", ignoreCase = true)) Color(0xFFE8F5E9) else Color(0xFFE3F2FD),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (app.platform.equals("Android", ignoreCase = true)) Icons.Default.Android else Icons.Default.PhoneIphone,
                                                contentDescription = null,
                                                tint = if (app.platform.equals("Android", ignoreCase = true)) Color(0xFF2E7D32) else Color(0xFF1976D2)
                                            )
                                        }
                                    }

                                    Column {
                                        Text(app.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Surface(
                                    color = if (app.platform.equals("Android", ignoreCase = true)) Color(0xFF2E7D32).copy(alpha = 0.1f) else Color(0xFF1976D2).copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        app.platform,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (app.platform.equals("Android", ignoreCase = true)) Color(0xFF2E7D32) else Color(0xFF1976D2),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                            // Status row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        "SDK: ${if (app.sdkSetupStatus == SdkSetupStatus.CONFIGURED) "مُهيّأ ✓" else "غير مُهيّأ"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (app.sdkSetupStatus == SdkSetupStatus.CONFIGURED) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        "Agent: ${if (app.agentConnectionStatus == AgentConnectionStatus.ONLINE) "متصل ●" else "غير متصل ○"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (app.agentConnectionStatus == AgentConnectionStatus.ONLINE) Color(0xFF2E7D32) else Color.Gray
                                    )
                                }

                                if (canManage) {
                                    IconButton(
                                        onClick = { onDeleteApp(app) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            // Quick Actions Flow
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { navController.navigate(Screen.SetupGuide(app.projectId, app.id)) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Build, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("دليل SDK", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = { navController.navigate(Screen.GitHubConnect(app.projectId)) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Code, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("GitHub", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = { navController.navigate(Screen.DebugSymbols(app.projectId)) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("الرموز", fontSize = 11.sp)
                                }

                                FilledTonalButton(
                                    onClick = { navController.navigate(Screen.LiveTesting) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("اختبار", fontSize = 11.sp)
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
fun AddApplicationDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, platform: String, packageName: String) -> Unit
) {
    var appName by remember { mutableStateOf("") }
    var selectedPlatform by remember { mutableStateOf("Android") }
    var packageName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ربط تطبيق جديد بالمشروع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("اسم التطبيق (e.g. My App)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("منصة التطبيق (Platform):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Android", "iOS").forEach { plat ->
                        FilterChip(
                            selected = selectedPlatform == plat,
                            onClick = { selectedPlatform = plat },
                            label = { Text(plat) },
                            leadingIcon = {
                                Icon(
                                    if (plat == "Android") Icons.Default.Android else Icons.Default.PhoneIphone,
                                    null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text(if (selectedPlatform == "Android") "Package Name (e.g. com.company.app)" else "Bundle ID (e.g. com.company.app)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (appName.isNotBlank() && packageName.isNotBlank()) {
                        onConfirm(appName.trim(), selectedPlatform, packageName.trim())
                    }
                },
                enabled = appName.isNotBlank() && packageName.isNotBlank()
            ) {
                Text("ربط التطبيق")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
