package com.example.presentation.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider as Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.domain.model.*
import com.example.presentation.navigation.Screen
import com.example.presentation.ui.screens.vibe.*
import com.example.presentation.viewmodel.VibeStudioViewModel
import com.example.ui.components.*
import com.example.ui.theme.EngGridLine
import com.example.ui.theme.JetBrainsMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeStudioScreen(
    navController: NavController,
    viewModel: VibeStudioViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val workspaces by viewModel.workspaces.collectAsState()
    var showNewProjectDialog by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, "Vibe", tint = MaterialTheme.colorScheme.primary)
                        Text("AI Vibe Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (uiState.activeWorkspace != null) {
                            Text(" / ${uiState.activeWorkspace?.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.activeWorkspace != null) {
                            viewModel.selectWorkspace(null)
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState.activeWorkspace != null) {
                        IconButton(onClick = { viewModel.toggleSearch(true) }) {
                            Icon(Icons.Default.Search, "Search Files")
                        }
                        IconButton(onClick = { viewModel.togglePanel(StudioPanel.TERMINAL) }) {
                            Icon(
                                Icons.Default.Terminal,
                                "Terminal",
                                tint = if (uiState.isTerminalOpen) Color(0xFF3FB950) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { viewModel.toggleResourceModal(true) }) {
                            Icon(Icons.Default.Memory, "Resource Quotas", tint = Color(0xFFD29922))
                        }
                        IconButton(onClick = {
                            navController.navigate(Screen.DevDebugCenter(uiState.activeWorkspace?.id))
                        }) {
                            Icon(
                                Icons.Default.Healing,
                                "Dev & Debug Center",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (uiState.runtimeState?.status == ServerStatus.RUNNING) {
                            IconButton(onClick = { viewModel.stopDevServer() }) {
                                Icon(Icons.Default.Stop, "Stop Server", tint = Color(0xFFF85149))
                            }
                        } else {
                            IconButton(onClick = { viewModel.startDevServer() }) {
                                Icon(Icons.Default.PlayArrow, "Start Server", tint = Color(0xFF3FB950))
                            }
                        }
                        IconButton(onClick = { viewModel.togglePanel(StudioPanel.PREVIEW) }) {
                            Icon(
                                Icons.Default.Visibility,
                                "Preview",
                                tint = if (uiState.isPreviewOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { viewModel.togglePanel(StudioPanel.CHAT) }) {
                            Icon(Icons.Default.AutoAwesome, "AI Agent", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.activeWorkspace != null) {
            androidx.activity.compose.BackHandler {
                viewModel.selectWorkspace(null)
            }
        }
        if (uiState.activeWorkspace == null) {
            WorkspaceSelectionView(
                workspaces = workspaces,
                onSelect = { viewModel.selectWorkspace(it) },
                onNew = { showNewProjectDialog = true },
                onBuildWithAi = { viewModel.openAppBuilder() },
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                // Side Bar (Explorer): on tablet 240.dp docked, on mobile 170.dp
                if (uiState.isExplorerOpen) {
                    Surface(
                        modifier = Modifier
                            .width(if (isTablet) 240.dp else 170.dp)
                            .fillMaxHeight()
                            .drawBehind {
                                drawLine(
                                    color = EngGridLine,
                                    start = Offset(size.width, 0f),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            },
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        FileExplorer(
                            tree = uiState.fileTree,
                            selectedId = uiState.selectedFileId,
                            onFileClick = {
                                viewModel.openFile(it)
                                if (!isTablet) viewModel.togglePanel(StudioPanel.EXPLORER)
                            },
                            onSearchClick = { viewModel.toggleSearch(true) }
                        )
                    }
                }

                // Main Content (Editor Area)
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Mobile Adaptive Switcher Bar (only when on mobile phone)
                    if (!isTablet) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = uiState.isExplorerOpen,
                                        onClick = { viewModel.togglePanel(StudioPanel.EXPLORER) },
                                        label = { Text("الملفات", fontSize = 11.sp) },
                                        leadingIcon = { Icon(Icons.Default.Folder, null, modifier = Modifier.size(14.dp)) }
                                    )
                                    FilterChip(
                                        selected = !uiState.isExplorerOpen && !uiState.isPreviewOpen,
                                        onClick = {
                                            if (uiState.isExplorerOpen) viewModel.togglePanel(StudioPanel.EXPLORER)
                                            if (uiState.isPreviewOpen) viewModel.togglePanel(StudioPanel.PREVIEW)
                                        },
                                        label = { Text("المحرر", fontSize = 11.sp) },
                                        leadingIcon = { Icon(Icons.Default.Code, null, modifier = Modifier.size(14.dp)) }
                                    )
                                    FilterChip(
                                        selected = uiState.isPreviewOpen,
                                        onClick = { viewModel.togglePanel(StudioPanel.PREVIEW) },
                                        label = { Text("المعاينة", fontSize = 11.sp) },
                                        leadingIcon = { Icon(Icons.Default.Visibility, null, modifier = Modifier.size(14.dp)) }
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.togglePanel(StudioPanel.TERMINAL) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Terminal,
                                        "Terminal",
                                        tint = if (uiState.isTerminalOpen) Color(0xFF3FB950) else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Open Tabs
                    if (uiState.openFiles.isNotEmpty() && (isTablet || !uiState.isPreviewOpen)) {
                        ScrollableTabRow(
                            selectedTabIndex = uiState.openFiles.indexOfFirst { it.id == uiState.selectedFileId }.coerceAtLeast(0),
                            edgePadding = 0.dp,
                            containerColor = MaterialTheme.colorScheme.surface,
                            divider = { Divider(color = EngGridLine) }
                        ) {
                            uiState.openFiles.forEach { file ->
                                Tab(
                                    selected = uiState.selectedFileId == file.id,
                                    onClick = { viewModel.openFile(file) },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(getFileIcon(file.extension), null, modifier = Modifier.size(16.dp), tint = getFileIconColor(file.extension))
                                            Text(file.name, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono)
                                            IconButton(onClick = { viewModel.closeFile(file.id) }, modifier = Modifier.size(16.dp)) {
                                                Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Editor / Workspace Center
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val currentFile = uiState.openFiles.find { it.id == uiState.selectedFileId }
                        if (currentFile != null) {
                            CodeEditor(
                                content = currentFile.content,
                                onContentChange = { viewModel.updateFileContent(currentFile.id, it) }
                            )
                        } else {
                            WelcomeToVibe(
                                workspace = uiState.activeWorkspace!!,
                                onOpenChat = { viewModel.togglePanel(StudioPanel.CHAT) },
                                onRunBuild = { viewModel.startDevServer() }
                            )
                        }

                        // Floating / Docked Live Preview (Full-width on phone, 420.dp on tablet)
                        if (uiState.isPreviewOpen) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .then(if (isTablet) Modifier.width(420.dp) else Modifier.fillMaxWidth())
                                    .background(MaterialTheme.colorScheme.background)
                                    .drawBehind {
                                        if (isTablet) {
                                            drawLine(
                                                color = EngGridLine,
                                                start = Offset(0f, 0f),
                                                end = Offset(0f, size.height),
                                                strokeWidth = 1.dp.toPx()
                                            )
                                        }
                                    }
                            ) {
                                VibeLivePreviewPanel(
                                    workspace = uiState.activeWorkspace!!,
                                    files = uiState.openFiles.ifEmpty { listOf(WorkspaceFile(workspaceId = uiState.activeWorkspace!!.id, name = "index.html", path = "index.html", content = "<h1>${uiState.activeWorkspace!!.name}</h1>")) },
                                    runtimeState = uiState.runtimeState,
                                    previewViewport = uiState.previewViewport,
                                    previewOrientation = uiState.previewOrientation,
                                    previewKey = uiState.previewKey,
                                    browserLogs = uiState.browserLogs,
                                    onSetViewport = { viewModel.setPreviewViewport(it) },
                                    onSetOrientation = { viewModel.setPreviewOrientation(it) },
                                    onReload = { viewModel.reloadPreview() },
                                    onStartServer = { viewModel.startDevServer() },
                                    onStopServer = { viewModel.stopDevServer() },
                                    onRestartServer = { viewModel.restartDevServer() },
                                    onAddConsoleLog = { lvl, msg, src -> viewModel.addBrowserConsoleLog(lvl, msg, src) },
                                    onClearConsoleLogs = { viewModel.clearBrowserConsoleLogs() },
                                    onClose = { viewModel.togglePanel(StudioPanel.PREVIEW) }
                                )
                            }
                        }
                    }

                    // Bottom Panel (Terminal / Logs)
                    if (uiState.isTerminalOpen) {
                        TerminalPanel(
                            title = "VIBE_TERMINAL",
                            logs = uiState.logs.map { it.message },
                            modifier = Modifier.height(if (isTablet) 200.dp else 150.dp)
                        )
                    }

                    // Bottom Status Bar
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .drawBehind {
                                drawLine(
                                    color = EngGridLine,
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 1.dp.toPx()
                                )
                            },
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val isRunning = uiState.runtimeState?.status == ServerStatus.RUNNING
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (isRunning) Color(0xFF3FB950) else Color.Gray, CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (isRunning) "SERVER: RUNNING ON :${uiState.runtimeState?.port ?: 3000}" else "SERVER: STOPPED",
                                    color = if (isRunning) Color(0xFF3FB950) else Color.Gray,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMono,
                                    fontSize = 10.sp
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { viewModel.toggleVersionControl() }
                            ) {
                                Icon(Icons.Default.AccountTree, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    uiState.currentBranchName.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMono,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(Modifier.weight(1f))
                            Text(
                                "RAM: ${uiState.runtimeState?.memoryUsageMb ?: 128}MB | STACK: ${uiState.activeWorkspace?.techStack?.uppercase() ?: "UTF-8"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMono,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // AI Autonomous Agent Panel
                if (uiState.isChatOpen) {
                    VibeAgentChatPanel(
                        mode = uiState.executionMode,
                        onModeChange = { viewModel.setExecutionMode(it) },
                        messages = uiState.chatMessages,
                        activePlan = uiState.activePlan,
                        isLoading = uiState.isLoading,
                        onSendMessage = { viewModel.sendMessage(it) },
                        onExecutePlan = { viewModel.executeActivePlan() },
                        onOpenDiffViewer = { viewModel.toggleDiffViewer(true) },
                        onOpenReviewIssues = { viewModel.toggleReviewIssues(true) },
                        workspace = uiState.activeWorkspace,
                        activeAgentModel = uiState.activeAgentModel,
                        agentIsFree = uiState.agentIsFree,
                        activeTeam = uiState.activeTeam,
                        availableTeams = uiState.availableTeams,
                        selectedMemberId = uiState.selectedMemberId,
                        onSelectTeam = { viewModel.selectTeam(it) },
                        onSelectMember = { viewModel.selectTeamMember(it) },
                        modifier = Modifier
                            .width(if (isTablet) 360.dp else 300.dp)
                            .fillMaxHeight()
                            .drawBehind {
                                drawLine(
                                    color = EngGridLine,
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                    )
                }
            }
        }
    }

    // Dialogs & Modals
    if (showNewProjectDialog) {
        NewProjectDialog(
            onDismiss = { showNewProjectDialog = false },
            onConfirm = { name, desc, type, stack ->
                viewModel.createNewProject(name, desc, type, stack)
                showNewProjectDialog = false
            }
        )
    }

    if (uiState.isDiffViewerOpen) {
        VibeDiffViewerDialog(
            diffs = uiState.currentDiffs,
            onDismiss = { viewModel.toggleDiffViewer(false) }
        )
    }

    if (uiState.isCheckpointsOpen) {
        VibeCheckpointsDialog(
            checkpoints = uiState.checkpoints,
            onCreateCheckpoint = { title, desc -> viewModel.createManualCheckpoint(title, desc) },
            onRevertToCheckpoint = { checkpointId -> viewModel.revertToCheckpoint(checkpointId) },
            onDeleteCheckpoint = { checkpointId -> viewModel.deleteCheckpoint(checkpointId) },
            onDismiss = { viewModel.toggleCheckpoints(false) }
        )
    }

    if (uiState.isReviewIssuesOpen) {
        VibeReviewIssuesDialog(
            issues = uiState.reviewIssues,
            onDismiss = { viewModel.toggleReviewIssues(false) }
        )
    }

    if (uiState.isSearchOpen) {
        VibeSearchDialog(
            searchQuery = uiState.searchQuery,
            searchResults = uiState.searchResults,
            onQueryChange = { viewModel.searchWorkspace(it) },
            onFileSelected = { file -> viewModel.openFile(file) },
            onDismiss = { viewModel.toggleSearch(false) }
        )
    }

    if (uiState.pendingSensitiveAction != null) {
        VibeSensitiveActionDialog(
            action = uiState.pendingSensitiveAction!!,
            onConfirm = { approved -> viewModel.confirmSensitiveAction(approved) }
        )
    }

    if (uiState.isResourceModalOpen) {
        VibeResourceLimitsDialog(
            runtimeState = uiState.runtimeState,
            onKillStuckProcesses = { viewModel.killStuckProcesses() },
            onDismiss = { viewModel.toggleResourceModal(false) }
        )
    }

    if (uiState.isLocalAgentModalOpen) {
        VibeLocalAgentDialog(
            config = uiState.localAgentConfig,
            onConnect = { endpoint, token -> viewModel.connectLocalAgent(endpoint, token) },
            onDisconnect = { viewModel.disconnectLocalAgent() },
            onDismiss = { viewModel.toggleLocalAgentModal(false) }
        )
    }

    if (uiState.isAppBuilderOpen) {
        VibeAppBuilderDialog(
            prompt = uiState.appBuilderPrompt,
            preferredPlatform = uiState.appBuilderPreferredPlatform,
            blueprint = uiState.appBuilderBlueprint,
            isAnalyzing = uiState.isAnalyzingRequirements,
            buildProgress = uiState.appBuildProgress,
            onPromptChange = { viewModel.updateAppBuilderPrompt(it) },
            onPlatformChange = { viewModel.updateAppBuilderPlatform(it) },
            onAnalyze = { p, plat -> viewModel.analyzeAppRequirements(p, plat) },
            onUpdateBlueprint = { viewModel.updateBlueprint(it) },
            onBuildApp = { viewModel.buildApplication(it) },
            onDismiss = { viewModel.closeAppBuilder() }
        )
    }

    if (uiState.isTestingPanelOpen) {
        VibeTestingPanelDialog(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { viewModel.toggleTestingPanel(false) }
        )
    }

    if (uiState.isVersionControlOpen) {
        VibeVersionControlDialog(
            uiState = uiState,
            onTabSelected = { viewModel.setGitTab(it) },
            onInitRepo = { viewModel.initGitRepository() },
            onCommit = { msg, author, override -> viewModel.commitChanges(msg, author, override) },
            onCreateBranch = { name, checkout -> viewModel.createBranch(name, checkout) },
            onCheckoutBranch = { name -> viewModel.checkoutBranch(name) },
            onDeleteBranch = { name -> viewModel.deleteBranch(name) },
            onRestoreCommit = { sha -> viewModel.restoreToCommit(sha) },
            onPushToGitHub = { owner, repo -> viewModel.pushToGitHub(owner, repo) },
            onPullFromGitHub = { owner, repo -> viewModel.pullFromGitHub(owner, repo) },
            onCreatePullRequest = { title, head, base, body -> viewModel.createGitHubPullRequest(title, head, base, body) },
            onCreateCheckpoint = { title, desc -> viewModel.createManualCheckpoint(title, desc) },
            onRevertCheckpoint = { id -> viewModel.revertToCheckpoint(id) },
            onDeleteCheckpoint = { id -> viewModel.deleteCheckpoint(id) },
            onClearMessage = { viewModel.clearGitOperationMessage() },
            onDismiss = { viewModel.toggleVersionControl(false) }
        )
    }

    if (uiState.isTaskReviewOpen && uiState.activeTaskReview != null) {
        VibeTaskReviewDialog(
            review = uiState.activeTaskReview!!,
            onApprove = { reviewId, commitMsg -> viewModel.approveTaskReview(reviewId, commitMsg) },
            onRevert = { reviewId -> viewModel.revertTaskReview(reviewId) },
            onBranch = { reviewId, branchName -> viewModel.branchTaskReview(reviewId, branchName) },
            onDismiss = { viewModel.dismissTaskReview() }
        )
    }

    if (uiState.isGovernanceOpen) {
        VibeGovernanceDialog(
            workspace = uiState.activeWorkspace,
            logs = uiState.agentActivityLogs,
            onPermissionChange = { viewModel.updateWorkspacePermission(it) },
            onClose = { viewModel.toggleGovernance(false) }
        )
    }

    if (uiState.isApprovalQueueOpen) {
        VibeApprovalQueueDialog(
            approvals = uiState.pendingApprovals,
            onAction = { id, status -> viewModel.handleApproval(id, status) },
            onClose = { viewModel.toggleApprovalQueue(false) }
        )
    }
}

@Composable
fun WorkspaceSelectionView(
    workspaces: List<Workspace>,
    onSelect: (Workspace) -> Unit,
    onNew: () -> Unit,
    onBuildWithAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        EngCard(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "AI App Builder",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "حوّل فكرتك بنص طبيعي إلى مشروع كامل: استخراج المتطلبات، بناء الشاشات ونظام التصميم، وهيكلة قاعدة البيانات، مع تكامل فوري لـ Crash Intelligence.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EngButton(
                        onClick = onBuildWithAi,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Build with AI")
                    }

                    OutlinedButton(
                        onClick = onNew,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("مشروع يدوي")
                    }
                }
            }
        }

        SectionHeader(title = "المساحات المتاحة (Workspaces)")
        Spacer(Modifier.height(12.dp))

        if (workspaces.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = EngGridLine)
                    Spacer(Modifier.height(8.dp))
                    Text("لا توجد مشاريع حالية. ابدأ واحداً الآن!", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(workspaces) { workspace ->
                    EngCard(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(workspace) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                                Icon(getTypeIcon(workspace.type), null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(workspace.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(workspace.description, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(workspace.techStack.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontFamily = JetBrainsMono)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileExplorer(
    tree: List<VibeFileNode>,
    selectedId: String?,
    onFileClick: (WorkspaceFile) -> Unit,
    onSearchClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("EXPLORER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontFamily = JetBrainsMono)
            IconButton(onClick = onSearchClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Search, "Search", modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(tree) { node ->
                FileNodeItem(node, selectedId, onFileClick, depth = 0)
            }
        }
    }
}

@Composable
fun FileNodeItem(
    node: VibeFileNode,
    selectedId: String?,
    onFileClick: (WorkspaceFile) -> Unit,
    depth: Int
) {
    var isExpanded by remember { mutableStateOf(true) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (node.file.isFolder) isExpanded = !isExpanded
                    else onFileClick(node.file)
                }
                .background(if (selectedId == node.file.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                .padding(vertical = 4.dp, horizontal = (depth * 8).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (node.file.isFolder) {
                    if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight
                } else getFileIcon(node.file.extension),
                null,
                modifier = Modifier.size(14.dp),
                tint = if (node.file.isFolder) MaterialTheme.colorScheme.secondary else getFileIconColor(node.file.extension)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                node.file.name, 
                style = MaterialTheme.typography.bodySmall, 
                maxLines = 1, 
                fontFamily = if (!node.file.isFolder) JetBrainsMono else null,
                color = if (selectedId == node.file.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }

        if (node.file.isFolder && isExpanded) {
            node.children.forEach { child ->
                FileNodeItem(child, selectedId, onFileClick, depth + 1)
            }
        }
    }
}

@Composable
fun CodeEditor(
    content: String,
    onContentChange: (String) -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        TextField(
            value = content,
            onValueChange = onContentChange,
            modifier = Modifier.fillMaxSize(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
fun TerminalPanel(logs: List<BuildLog>) {
    Surface(modifier = Modifier.fillMaxWidth().height(150.dp), color = Color(0xFF1E1E1E)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                items(logs) { log ->
                    Text(
                        text = log.message,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = when (log.type) {
                            LogType.ERROR -> Color(0xFFF44336)
                            LogType.SUCCESS -> Color(0xFF4CAF50)
                            LogType.TERMINAL_INPUT -> Color(0xFF2196F3)
                            LogType.WARNING -> Color(0xFFFFB74D)
                            else -> Color.White
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PreviewPanel(workspace: Workspace, onClose: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Live Preview", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
            }

            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Web, null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                    Text("Preview for ${workspace.type}", color = Color.Gray)
                    Text("Local Server Running: http://localhost:3000", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun WelcomeToVibe(
    workspace: Workspace,
    onOpenChat: () -> Unit,
    onRunBuild: () -> Unit
) {
    Box(Modifier.fillMaxSize().drawBehind {
        val step = 40.dp.toPx()
        for (x in 0 until (size.width / step).toInt()) {
            drawLine(EngGridLine, Offset(x * step, 0f), Offset(x * step, size.height), 1f)
        }
        for (y in 0 until (size.height / step).toInt()) {
            drawLine(EngGridLine, Offset(0f, y * step), Offset(size.width, y * step), 1f)
        }
    }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.Terminal, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            Spacer(Modifier.height(16.dp))
            Text(workspace.name.uppercase(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMono)
            Text(workspace.techStack.uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontFamily = JetBrainsMono)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EngButton(onClick = onOpenChat) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("START VIBE SESSION")
                }
                OutlinedButton(onClick = onRunBuild, shape = RoundedCornerShape(4.dp), modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("BUILD & DEPLOY")
                }
            }
        }
    }
}

@Composable
fun NewProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, WorkspaceType, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(WorkspaceType.WEB_APP) }
    var stack by remember { mutableStateOf("React + Tailwind") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء مشروع Vibe جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المشروع") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("وصف المشروع") }, modifier = Modifier.fillMaxWidth())

                Text("نوع المشروع", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WorkspaceType.entries.take(4).forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.name.lowercase(), fontSize = 10.sp) }
                        )
                    }
                }

                OutlinedTextField(value = stack, onValueChange = { stack = it }, label = { Text("التقنيات (Tech Stack)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, desc, type, stack) }, enabled = name.isNotBlank()) {
                Text("إنشاء وابدأ البرمجة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

fun getTypeIcon(type: WorkspaceType): ImageVector = when (type) {
    WorkspaceType.WEB_SITE, WorkspaceType.WEB_APP -> Icons.Default.Language
    WorkspaceType.ANDROID -> Icons.Default.Android
    WorkspaceType.IOS -> Icons.Default.PhoneIphone
    WorkspaceType.FLUTTER -> Icons.Default.Bolt
    WorkspaceType.BACKEND_API -> Icons.Default.Dns
    else -> Icons.Default.Terminal
}

fun getFileIcon(ext: String): ImageVector = when (ext) {
    "kt" -> Icons.Default.Android
    "js", "ts", "tsx", "jsx" -> Icons.Default.Javascript
    "html" -> Icons.Default.Html
    "css" -> Icons.Default.Css
    "json" -> Icons.Default.Settings
    "md" -> Icons.Default.Description
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

fun getFileIconColor(ext: String): Color = when (ext) {
    "kt" -> Color(0xFF7F52FF)
    "js" -> Color(0xFFF7DF1E)
    "ts", "tsx" -> Color(0xFF3178C6)
    "html" -> Color(0xFFE34F26)
    "css" -> Color(0xFF1572B6)
    else -> Color.Gray
}
