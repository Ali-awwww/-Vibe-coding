package com.example.presentation.ui.screens.vibe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.window.Dialog
import com.example.domain.model.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun VibeVersionControlDialog(
    uiState: StudioUiState,
    onTabSelected: (GitVersionControlTab) -> Unit,
    onInitRepo: () -> Unit,
    onCommit: (message: String, author: String, overrideSecrets: Boolean) -> Unit,
    onCreateBranch: (name: String, checkout: Boolean) -> Unit,
    onCheckoutBranch: (name: String) -> Unit,
    onDeleteBranch: (name: String) -> Unit,
    onRestoreCommit: (commitSha: String) -> Unit,
    onPushToGitHub: (owner: String, repo: String) -> Unit,
    onPullFromGitHub: (owner: String, repo: String) -> Unit,
    onCreatePullRequest: (title: String, head: String, base: String, body: String) -> Unit,
    onCreateCheckpoint: (title: String, description: String) -> Unit,
    onRevertCheckpoint: (checkpointId: String) -> Unit,
    onDeleteCheckpoint: (checkpointId: String) -> Unit,
    onClearMessage: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.AccountTree, null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(
                                        "إدارة الإصدارات وGit (Version Control)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                "⎇ ${uiState.currentBranchName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        if (uiState.uncommittedDiffs.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFF57C00).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "تعديلات غير مثبتة: ${uiState.uncommittedDiffs.size}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFE65100),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "المستودع نظيف (Clean)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF2E7D32),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, null)
                            }
                        }

                        // Success / Error alerts
                        if (uiState.gitOperationSuccessMessage != null) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE8F5E9),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        uiState.gitOperationSuccessMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Medium
                                    )
                                    IconButton(onClick = onClearMessage, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp), tint = Color(0xFF2E7D32))
                                    }
                                }
                            }
                        }
                    }
                }

                // Tabs
                ScrollableTabRow(
                    selectedTabIndex = uiState.activeGitTab.ordinal,
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    GitVersionControlTab.values().forEach { tab ->
                        Tab(
                            selected = uiState.activeGitTab == tab,
                            onClick = { onTabSelected(tab) },
                            text = {
                                Text(
                                    tab.titleAr,
                                    fontWeight = if (uiState.activeGitTab == tab) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                // Body content based on active tab
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                    when (uiState.activeGitTab) {
                        GitVersionControlTab.HISTORY -> HistoryTab(
                            isInitialized = uiState.isGitInitialized,
                            commits = uiState.gitCommits,
                            dateFormat = dateFormat,
                            onInitRepo = onInitRepo,
                            onRestoreCommit = onRestoreCommit
                        )
                        GitVersionControlTab.BRANCHES -> BranchesTab(
                            branches = uiState.gitBranches,
                            currentBranch = uiState.currentBranchName,
                            onCreateBranch = onCreateBranch,
                            onCheckoutBranch = onCheckoutBranch,
                            onDeleteBranch = onDeleteBranch
                        )
                        GitVersionControlTab.WORKING_TREE -> WorkingTreeTab(
                            diffs = uiState.uncommittedDiffs,
                            secrets = uiState.secretWarnings,
                            onCommit = onCommit
                        )
                        GitVersionControlTab.GITHUB -> GitHubSyncTab(
                            commits = uiState.gitCommits,
                            currentBranch = uiState.currentBranchName,
                            isPushPulling = uiState.isPushPulling,
                            onPush = onPushToGitHub,
                            onPull = onPullFromGitHub,
                            onCreatePR = onCreatePullRequest
                        )
                        GitVersionControlTab.CHECKPOINTS -> CheckpointsTab(
                            checkpoints = uiState.checkpoints,
                            dateFormat = dateFormat,
                            onCreateCheckpoint = onCreateCheckpoint,
                            onRevert = onRevertCheckpoint,
                            onDelete = onDeleteCheckpoint
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Sub-tabs
// -------------------------------------------------------------

@Composable
private fun HistoryTab(
    isInitialized: Boolean,
    commits: List<WorkspaceGitCommit>,
    dateFormat: SimpleDateFormat,
    onInitRepo: () -> Unit,
    onRestoreCommit: (String) -> Unit
) {
    if (!isInitialized) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.AccountTree, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("مستودع Git غير مهيأ بعد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "قم بتهيئة مستودع Git لحفظ تاريخ المشروع والتنقل بين النسخ البرمجية.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onInitRepo) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("تهيئة مستودع Git الآن")
            }
        }
    } else if (commits.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("لا توجد التزامات (Commits) سابقة مسجلة في هذا الفرع.", color = MaterialTheme.colorScheme.outline)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(commits) { commit ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            commit.id.take(8),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0x20757575)
                                ) {
                                    Text(
                                        commit.branch,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                if (commit.isPushedToRemote) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0x302E7D32)) {
                                        Text("مرفوع لـ Remote", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }

                            Text(
                                dateFormat.format(Date(commit.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(commit.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("بواسطة: ${commit.author}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("+${commit.additions} / -${commit.deletions}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                Text("(${commit.changedFilesCount} ملف)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }

                            OutlinedButton(
                                onClick = { onRestoreCommit(commit.id) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("استعادة", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Linked Crash Report or Task
                        if (commit.associatedCrashId != null) {
                            Spacer(Modifier.height(4.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0x20F44336)) {
                                Text(
                                    "مرتبط بعطل: ${commit.associatedCrashId.take(12)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFD32F2F),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
private fun BranchesTab(
    branches: List<WorkspaceGitBranch>,
    currentBranch: String,
    onCreateBranch: (String, Boolean) -> Unit,
    onCheckoutBranch: (String) -> Unit,
    onDeleteBranch: (String) -> Unit
) {
    var showCreateForm by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }
    var checkoutImmediately by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("فروع المشروع (Branches)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { showCreateForm = !showCreateForm }) {
                Icon(if (showCreateForm) Icons.Default.Close else Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(if (showCreateForm) "إلغاء" else "فرع جديد")
            }
        }

        if (showCreateForm) {
            Spacer(Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إنشاء فرع جديد لحماية العمل الرئيسي", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = newBranchName,
                        onValueChange = { newBranchName = it },
                        label = { Text("اسم الفرع (مثل feature-auth)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checkoutImmediately, onCheckedChange = { checkoutImmediately = it })
                        Text("التبديل إلى هذا الفرع فور إنشائه", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            if (newBranchName.isNotBlank()) {
                                onCreateBranch(newBranchName, checkoutImmediately)
                                newBranchName = ""
                                showCreateForm = false
                            }
                        },
                        enabled = newBranchName.isNotBlank()
                    ) {
                        Text("إنشاء الفرع")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(branches) { branch ->
                val isCurrent = branch.name == currentBranch
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(branch.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                if (branch.isDefault) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0x302196F3)) {
                                        Text("الافتراضي", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1976D2), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                                if (isCurrent) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0x304CAF50)) {
                                        Text("الفرع النشط", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            if (branch.headCommitSha.isNotBlank()) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        "HEAD: ${branch.headCommitSha.take(8)}",
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!isCurrent) {
                                Button(
                                    onClick = { onCheckoutBranch(branch.name) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("تبديل", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            if (!branch.isDefault && !isCurrent) {
                                IconButton(
                                    onClick = { onDeleteBranch(branch.name) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
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
private fun WorkingTreeTab(
    diffs: List<FileDiff>,
    secrets: List<SecretAuditWarning>,
    onCommit: (String, String, Boolean) -> Unit
) {
    var commitMessage by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf("Developer") }
    var overrideSecrets by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Security Warnings Banner
        if (secrets.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFEF5350), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFC62828))
                            Text("تنبيه أمني: حماية بيانات المشروع", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                        }
                        Text(
                            "تم اكتشاف ${secrets.size} أسرار أو ملفات بيئة حساسة داخل مساحة العمل. لا يُنصح بتثبيت هذه الملفات لحماية حساباتك وبيانات اعتمادك.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB71C1C)
                        )
                        secrets.forEach { warn ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("• ${warn.filePath}:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                Text(warn.secretType, style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = overrideSecrets, onCheckedChange = { overrideSecrets = it })
                            Text("أنا أدرك المخاطر وأريد تأكيد الالتزام رغم التنبيه الأمني", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
                        }
                    }
                }
            }
        }

        // Commit Form
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تثبيت التغييرات (Commit Changes)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("رسالة الالتزام (Commit message)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = authorName,
                        onValueChange = { authorName = it },
                        label = { Text("اسم الكاتب (Author)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (commitMessage.isNotBlank()) {
                                onCommit(commitMessage, authorName, overrideSecrets)
                                commitMessage = ""
                            }
                        },
                        enabled = commitMessage.isNotBlank() && (secrets.isEmpty() || overrideSecrets),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("تثبيت التغييرات (${diffs.size} ملف)")
                    }
                }
            }
        }

        // Changed Files List
        item {
            Text("الملفات المتغيرة (${diffs.size}):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }

        if (diffs.isEmpty()) {
            item {
                Text("لا توجد ملفات متغيرة. مساحة العمل مطابقة لآخر التزام.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            items(diffs) { diff ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(diff.path, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("+${diff.additions}", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                Text("-${diff.deletions}", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubSyncTab(
    commits: List<WorkspaceGitCommit>,
    currentBranch: String,
    isPushPulling: Boolean,
    onPush: (owner: String, repo: String) -> Unit,
    onPull: (owner: String, repo: String) -> Unit,
    onCreatePR: (title: String, head: String, base: String, body: String) -> Unit
) {
    var owner by remember { mutableStateOf("my-org") }
    var repo by remember { mutableStateOf("my-project") }
    var showPrDialog by remember { mutableStateOf(false) }
    var prTitle by remember { mutableStateOf("") }
    var prBody by remember { mutableStateOf("") }

    val unpushed = commits.filter { !it.isPushedToRemote }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إعدادات مستودع GitHub المستهدف", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = owner,
                            onValueChange = { owner = it },
                            label = { Text("المستخدم / المنظمة") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = repo,
                            onValueChange = { repo = it },
                            label = { Text("اسم المستودع") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("عمليات المزامنة السحابية بموافقة المستخدم", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "الالتزامات غير المرفوعة: ${unpushed.size} التزام في الفرع '$currentBranch'",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onPush(owner, repo) },
                            enabled = !isPushPulling,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudUpload, null)
                            Spacer(Modifier.width(6.dp))
                            Text("رفع التغييرات (Push)")
                        }

                        OutlinedButton(
                            onClick = { onPull(owner, repo) },
                            enabled = !isPushPulling,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudDownload, null)
                            Spacer(Modifier.width(6.dp))
                            Text("سحب التغييرات (Pull)")
                        }
                    }

                    Button(
                        onClick = { showPrDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F52FF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CallMerge, null)
                        Spacer(Modifier.width(6.dp))
                        Text("إنشاء Pull Request على GitHub")
                    }
                }
            }
        }

        if (showPrDialog) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF7F52FF), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("طلب سحب جديد (Pull Request)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("دمج الفرع '$currentBranch' في الفرع الرئيسي 'main'", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                        OutlinedTextField(
                            value = prTitle,
                            onValueChange = { prTitle = it },
                            label = { Text("عنوان الطلب") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = prBody,
                            onValueChange = { prBody = it },
                            label = { Text("وصف التغييرات والاختبارات") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showPrDialog = false }) { Text("إلغاء") }
                            Button(
                                onClick = {
                                    if (prTitle.isNotBlank()) {
                                        onCreatePR(prTitle, currentBranch, "main", prBody)
                                        showPrDialog = false
                                    }
                                },
                                enabled = prTitle.isNotBlank()
                            ) {
                                Text("إرسال طلب السحب")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckpointsTab(
    checkpoints: List<WorkspaceCheckpoint>,
    dateFormat: SimpleDateFormat,
    onCreateCheckpoint: (String, String) -> Unit,
    onRevert: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var showCreateForm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("نقاط الاستعادة السريعة (Checkpoints)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { showCreateForm = !showCreateForm }) {
                Icon(if (showCreateForm) Icons.Default.Close else Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(if (showCreateForm) "إلغاء" else "نقطة جديدة")
            }
        }

        if (showCreateForm) {
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان النقطة") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("الوصف") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onCreateCheckpoint(title, description)
                                title = ""
                                description = ""
                                showCreateForm = false
                            }
                        },
                        enabled = title.isNotBlank()
                    ) {
                        Text("حفظ نقطة الاستعادة")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(checkpoints) { cp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cp.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(dateFormat.format(Date(cp.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        if (cp.description.isNotBlank()) {
                            Text(cp.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${cp.totalFiles} ملف محفوظ", style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { onRevert(cp.id) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("استعادة", style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { onDelete(cp.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
