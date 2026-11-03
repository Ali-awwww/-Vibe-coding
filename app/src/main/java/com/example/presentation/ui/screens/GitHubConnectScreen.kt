package com.example.presentation.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.domain.model.*
import com.example.presentation.viewmodel.GitHubViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubConnectScreen(
    projectId: String,
    navController: NavController,
    viewModel: GitHubViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLinkDialog by remember { mutableStateOf<GitHubRepo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تكامل GitHub") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (uiState.connection == null) {
                GitHubLoginCard(
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.error,
                    onConnectOAuth = { viewModel.connect("simulated_github_code") },
                    onConnectToken = { viewModel.connectWithToken(it) }
                )
            } else {
                GitHubStatusCard(
                    connection = uiState.connection!!,
                    onDisconnect = { viewModel.disconnect() }
                )

                Spacer(Modifier.height(24.dp))

                Text("المستودعات المتاحة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(uiState.repositories) { repo ->
                            RepoItem(repo = repo, onClick = { showLinkDialog = repo })
                        }
                    }
                }
            }
        }
    }

    if (showLinkDialog != null) {
        LinkRepoDialog(
            repo = showLinkDialog!!,
            projectId = projectId,
            onDismiss = { showLinkDialog = null },
            onConfirm = { appId, branch ->
                viewModel.linkRepo(appId, projectId, showLinkDialog!!, branch)
                showLinkDialog = null
            }
        )
    }
}

@Composable
fun GitHubLoginCard(
    isLoading: Boolean,
    errorMessage: String?,
    onConnectOAuth: () -> Unit,
    onConnectToken: (String) -> Unit
) {
    var personalToken by remember { mutableStateOf("") }
    var useTokenMode by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Code, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("ربط مستودع GitHub", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "يتيح ربط GitHub لمحرك الذكاء الاصطناعي فحص الكود المصدري الحقيقي عند السطر المسبب للعطل واقتراح كود بديل (Diff) وإنشاء Pull Request بموافقتك.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = useTokenMode,
                onClick = { useTokenMode = true },
                label = { Text("رمز الوصول (Personal Token)") }
            )
            FilterChip(
                selected = !useTokenMode,
                onClick = { useTokenMode = false },
                label = { Text("OAuth Login") }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (useTokenMode) {
            OutlinedTextField(
                value = personalToken,
                onValueChange = { personalToken = it },
                label = { Text("GitHub Personal Access Token (ghp_...)") },
                placeholder = { Text("ghp_xxxxxxxxxxxxxxxxxxxx") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "الصلاحيات المطلوبة: repo, workflow. يتم تشفير الرمز محلياً بأعلى معايير الأمان.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { if (personalToken.isNotBlank()) onConnectToken(personalToken.trim()) },
                enabled = personalToken.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Link, null)
                    Spacer(Modifier.width(8.dp))
                    Text("ربط الحساب عبر الرمز")
                }
            }
        } else {
            Button(
                onClick = onConnectOAuth,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.Login, null)
                    Spacer(Modifier.width(8.dp))
                    Text("تسجيل الدخول عبر GitHub OAuth")
                }
            }
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun GitHubStatusCard(connection: GitHubConnection, onDisconnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = connection.avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small)
            )
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(connection.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("متصل - للقراءة فقط", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
            }
            
            IconButton(onClick = onDisconnect) {
                Icon(Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun RepoItem(repo: GitHubRepo, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(repo.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
            
            repo.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                Spacer(Modifier.width(4.dp))
                Text(repo.owner.login, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                
                Spacer(Modifier.width(16.dp))
                
                Icon(Icons.Default.CallSplit, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                Spacer(Modifier.width(4.dp))
                Text(repo.defaultBranch, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun LinkRepoDialog(
    repo: GitHubRepo,
    projectId: String,
    onDismiss: () -> Unit,
    onConfirm: (appId: String, branch: String) -> Unit
) {
    var branch by remember { mutableStateOf(repo.defaultBranch) }
    var selectedAppId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ربط المستودع بتطبيق") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("المستودع: ${repo.fullName}")
                
                OutlinedTextField(
                    value = selectedAppId,
                    onValueChange = { selectedAppId = it },
                    label = { Text("معرف التطبيق المرتبط (App ID)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("الفرع الافتراضي (Branch)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    "سيقوم الذكاء الاصطناعي بمقارنة الـ Stack Trace بالكود في هذا الفرع.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedAppId, branch) }, enabled = selectedAppId.isNotBlank()) {
                Text("ربط")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
