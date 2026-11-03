package com.example.presentation.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.di.DependencyProvider
import com.example.domain.model.*
import com.example.domain.model.TeamTemplates
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.ModelTeamsUiState
import com.example.presentation.viewmodel.ModelTeamsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelTeamsScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: ModelTeamsViewModel = viewModel {
        DependencyProvider.provideModelTeamsViewModel(context)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("فرق نماذج الذكاء الاصطناعي") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isCreating) {
                            viewModel.cancelCreate()
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.TeamValidation) }) {
                        Icon(Icons.Default.Verified, contentDescription = "Team Validation Center")
                    }
                    IconButton(onClick = { navController.navigate(Screen.TeamChat()) }) {
                        Icon(Icons.Default.Chat, contentDescription = "Team Chat")
                    }
                    IconButton(onClick = { viewModel.startCreateTeam() }) {
                        Icon(Icons.Default.GroupAdd, contentDescription = "Create Team")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.startCreateTeam() }) {
                Icon(Icons.Default.Add, contentDescription = "New Team")
            }
        }
    ) { innerPadding ->
        if (uiState.isCreating) {
            androidx.activity.compose.BackHandler {
                viewModel.cancelCreate()
            }
        }
        Column(modifier = Modifier.padding(innerPadding)) {
            if (uiState.isCreating) {
                CreateTeamWizard(uiState, viewModel)
            } else {
                TeamsListSection(
                    uiState = uiState,
                    onTeamClick = { navController.navigate(Screen.TeamDetail(it.id)) },
                    onUsageClick = { navController.navigate(Screen.TeamUsage(it.id)) },
                    onDelete = { viewModel.deleteTeam(it) },
                    onTemplateClick = { viewModel.useTemplate(it) }
                )
            }
        }
    }
}

@Composable
fun TeamsListSection(
    uiState: ModelTeamsUiState,
    onTeamClick: (ModelTeam) -> Unit,
    onUsageClick: (ModelTeam) -> Unit,
    onDelete: (String) -> Unit,
    onTemplateClick: (ModelTeam) -> Unit
) {
    if (uiState.teams.isEmpty() && !uiState.isLoading) {
        EmptyTeamsState()
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("قوالب الفرق (Team Templates)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            item {
                TeamCard(
                    team = TeamTemplates.CRASH_INVESTIGATION_TEAM,
                    onClick = { onTemplateClick(TeamTemplates.CRASH_INVESTIGATION_TEAM) },
                    onDelete = {},
                    isTemplate = true
                )
            }
            item {
                TeamCard(
                    team = TeamTemplates.VIBE_DEVELOPMENT_TEAM,
                    onClick = { onTemplateClick(TeamTemplates.VIBE_DEVELOPMENT_TEAM) },
                    onDelete = {},
                    isTemplate = true
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text("فرقي (My Teams)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            items(uiState.teams) { team ->
                TeamCard(
                    team = team,
                    onClick = { onTeamClick(team) },
                    onUsageClick = { onUsageClick(team) },
                    onDelete = { onDelete(team.id) }
                )
            }
        }
    }
}

@Composable
fun TeamCard(
    team: ModelTeam,
    onClick: () -> Unit,
    onUsageClick: () -> Unit = {},
    onDelete: () -> Unit,
    isTemplate: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isTemplate) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isTemplate) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isTemplate) Icons.Default.AutoAwesome else Icons.Default.Groups, null, tint = if (isTemplate) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (isTemplate) {
                        Surface(color = MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(4.dp)) {
                            Text("قالب جاهز (Template)", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    } else if (team.governance.freeOnly) {
                        Surface(color = Color(0xFF1B5E20).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                            Text("Free Only (مجاني)", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (!isTemplate) {
                    IconButton(onClick = onUsageClick) {
                        Icon(Icons.Default.QueryStats, "Usage", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("استخدام القالب", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(team.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                team.members.take(3).forEach { member ->
                    MemberBadge(member)
                }
                if (team.members.size > 3) {
                    Text("+${team.members.size - 3}", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (!isTemplate) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("أنشئت في: ${java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date(team.createdAt))}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun MemberBadge(member: TeamMember) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = CircleShape
    ) {
        Text(
            member.role.labelAr.take(1),
            modifier = Modifier.padding(4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EmptyTeamsState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.GroupWork, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text("لا توجد فرق ذكاء اصطناعي بعد", style = MaterialTheme.typography.titleMedium)
        Text("ابدأ بإنشاء فريقك الأول لمهمة برمجية معقدة.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CreateTeamWizard(uiState: ModelTeamsUiState, viewModel: ModelTeamsViewModel) {
    var name by remember { mutableStateOf(uiState.newTeamName) }
    var description by remember { mutableStateOf(uiState.newTeamDescription) }
    var goal by remember { mutableStateOf(uiState.newTeamGoal) }
    
    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("إنشاء فريق جديد", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        
        item {
            OutlinedTextField(value = name, onValueChange = { name = it; viewModel.updateNewTeamInfo(it, description, goal) }, label = { Text("اسم الفريق") }, modifier = Modifier.fillMaxWidth())
        }
        
        item {
            OutlinedTextField(value = description, onValueChange = { description = it; viewModel.updateNewTeamInfo(name, it, goal) }, label = { Text("وصف الفريق") }, modifier = Modifier.fillMaxWidth())
        }
        
        item {
            OutlinedTextField(value = goal, onValueChange = { goal = it; viewModel.updateNewTeamInfo(name, description, it) }, label = { Text("هدف الفريق الأساسي") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        }
        
        item {
            Text("نمط التعاون (Collaboration Style)", style = MaterialTheme.typography.titleMedium)
        }
        
        item {
            CollaborationStyleSelector(
                selectedStyle = uiState.collaborationStyle,
                onStyleSelected = { viewModel.updateCollaborationStyle(it) }
            )
        }
        
        item {
            TeamSettingsSection(
                settings = uiState.settings,
                onSettingsChanged = { viewModel.updateTeamSettings(it) }
            )
        }

        item {
            TeamGovernanceCreationSection(
                governance = uiState.governance,
                onGovernanceChanged = { viewModel.updateTeamGovernance(it) }
            )
        }
        
        item {
            Text("أعضاء الفريق المختارين (${uiState.selectedMembers.size})", style = MaterialTheme.typography.titleMedium)
        }
        
        items(uiState.selectedMembers) { member ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(member.name, fontWeight = FontWeight.Bold)
                        Text(member.role.labelAr, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { viewModel.removeMemberFromNewTeam(member.id) }) {
                        Icon(Icons.Default.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        
        item {
            Text("إضافة عضو (النماذج المتاحة)", style = MaterialTheme.typography.titleMedium)
        }
        
        items(uiState.availableModels) { model ->
            var showRoleDialog by remember { mutableStateOf(false) }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showRoleDialog = true }
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(model.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(model.apiId, style = MaterialTheme.typography.labelSmall)
                    }
                    Icon(Icons.Default.Add, null)
                }
            }
            
            if (showRoleDialog) {
                AlertDialog(
                    onDismissRequest = { showRoleDialog = false },
                    title = { Text("اختر دور العضو") },
                    text = {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            items(TeamRole.values()) { role ->
                                TextButton(
                                    onClick = {
                                        viewModel.addMemberToNewTeam(model, role)
                                        showRoleDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(role.labelAr, textAlign = androidx.compose.ui.text.style.TextAlign.Start, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showRoleDialog = false }) {
                            Text("إلغاء")
                        }
                    }
                )
            }
        }
        
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.cancelCreate() }, modifier = Modifier.weight(1f)) {
                    Text("إلغاء")
                }
                Button(onClick = { viewModel.saveTeam() }, modifier = Modifier.weight(1f)) {
                    Text("حفظ الفريق")
                }
            }
        }
    }
}

@Composable
fun CollaborationStyleSelector(
    selectedStyle: CollaborationStyle,
    onStyleSelected: (CollaborationStyle) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CollaborationStyle.values().forEach { style ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedStyle == style) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onStyleSelected(style) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selectedStyle == style, onClick = { onStyleSelected(style) })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(style.labelAr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(style.labelEn, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun TeamSettingsSection(
    settings: TeamSettings,
    onSettingsChanged: (TeamSettings) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("إعدادات الفريق (Advanced Settings)", style = MaterialTheme.typography.titleSmall)
            
            Column {
                Text("عدد جولات المراجعة (Max Review Rounds): ${settings.maxReviewRounds}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = settings.maxReviewRounds.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(maxReviewRounds = it.toInt())) },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }
            
            Column {
                Text("الطلبات المتزامنة (Max Concurrent): ${settings.maxConcurrentRequests}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = settings.maxConcurrentRequests.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(maxConcurrentRequests = it.toInt())) },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("التوقف عند الفشل (Stop on failure)", modifier = Modifier.weight(1f))
                Switch(checked = settings.stopOnFailure, onCheckedChange = { onSettingsChanged(settings.copy(stopOnFailure = it)) })
            }
        }
    }
}

@Composable
fun TeamGovernanceCreationSection(
    governance: TeamGovernanceSettings,
    onGovernanceChanged: (TeamGovernanceSettings) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("إعدادات التكلفة والحوكمة (Cost & Free Control)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            Surface(
                color = if (governance.freeOnly) Color(0xFF1B5E20).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("النماذج المجانية فقط (Free Only)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("يمنع استدعاء النماذج المدفوعة أو التحويل إليها تلقائياً.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = governance.freeOnly,
                        onCheckedChange = { onGovernanceChanged(governance.copy(freeOnly = it)) }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("التبديل التلقائي لبديل مجاني عند نفاد الحصة (429)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = governance.autoFallbackToFree,
                    onCheckedChange = { onGovernanceChanged(governance.copy(autoFallbackToFree = it)) }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("توزيع الأحمال على المزودين المتصلين (Load Balancing)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = governance.enableLoadBalancing,
                    onCheckedChange = { onGovernanceChanged(governance.copy(enableLoadBalancing = it)) }
                )
            }
        }
    }
}
