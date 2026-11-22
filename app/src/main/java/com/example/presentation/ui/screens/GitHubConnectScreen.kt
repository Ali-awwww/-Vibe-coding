package com.example.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.GitHubConnection
import com.example.domain.model.GitHubRepo
import com.example.domain.model.RepositoryMapping
import com.example.presentation.viewmodel.GitHubViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubConnectScreen(projectId: String, navController: NavController, viewModel: GitHubViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showLinkDialog by remember { mutableStateOf<GitHubRepo?>(null) }

    LaunchedEffect(projectId) { viewModel.loadMappings(projectId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.github_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.github_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.connection == null) {
                GitHubLoginCard(uiState.isLoading, uiState.errorResId, viewModel::connectWithToken)
            } else {
                GitHubStatusCard(uiState.connection, uiState.isLoading, viewModel::disconnect)
                uiState.errorResId?.let { errorResId ->
                    GitHubErrorNotice(errorResId) {
                        if (errorResId == R.string.github_error_load_mappings) viewModel.loadMappings(projectId)
                        else viewModel.fetchRepositories()
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    item {
                        Text(stringResource(R.string.github_repositories_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (uiState.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                    if (uiState.repositories.isEmpty() && !uiState.isLoading && uiState.errorResId == null) {
                        item { GitHubEmptyRepositories() }
                    }
                    items(uiState.repositories, key = { it.id }) { repo ->
                        RepoItem(repo) { showLinkDialog = repo }
                    }
                    if (uiState.mappings.isNotEmpty()) {
                        item { Text(stringResource(R.string.github_linked_repositories), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        items(uiState.mappings, key = { "${it.applicationId}:${it.repoOwner}/${it.repoName}" }) {
                            LinkedRepositoryItem(it)
                        }
                    }
                }
            }
        }
    }

    showLinkDialog?.let { repo ->
        LinkRepoDialog(
            repo = repo,
            projectId = projectId,
            isSaving = uiState.isLinkingRepository,
            errorResId = uiState.errorResId,
            onDismiss = { showLinkDialog = null },
            onConfirm = { applicationId, branch ->
                viewModel.linkRepo(applicationId, projectId, repo, branch) { saved ->
                    if (saved) showLinkDialog = null
                }
            }
        )
    }
}

@Composable
fun GitHubLoginCard(isLoading: Boolean, errorResId: Int?, onConnectToken: (String) -> Unit) {
    var personalToken by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val submitToken = {
        val token = personalToken.trim()
        if (token.isNotEmpty() && !isLoading) onConnectToken(token)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.github_connect_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.github_connect_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = personalToken,
            onValueChange = { personalToken = it },
            label = { Text(stringResource(R.string.github_token_label)) },
            placeholder = { Text(stringResource(R.string.github_token_placeholder)) },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submitToken() }),
            trailingIcon = {
                IconButton(
                    onClick = { tokenVisible = !tokenVisible },
                    enabled = !isLoading,
                    modifier = Modifier.testTag("github_token_visibility")
                ) {
                    Icon(
                        if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(if (tokenVisible) R.string.github_token_hide else R.string.github_token_show)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("github_token_field")
        )
        Text(stringResource(R.string.github_token_permissions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.github_token_storage_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(
            onClick = { uriHandler.openUri("https://github.com/settings/personal-access-tokens/new") },
            modifier = Modifier.testTag("github_token_settings_link")
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.github_token_settings))
        }
        Button(
            onClick = submitToken,
            enabled = personalToken.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("github_token_submit")
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.github_token_submit))
            }
        }
        errorResId?.let { GitHubErrorNotice(it) }
    }
}

@Composable
fun GitHubStatusCard(connection: GitHubConnection, isLoading: Boolean, onDisconnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = connection.avatarUrl, contentDescription = null, modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(connection.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.github_connected_as, connection.username), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDisconnect, enabled = !isLoading, modifier = Modifier.testTag("github_disconnect_button")) {
                Icon(Icons.Default.Logout, contentDescription = stringResource(R.string.github_disconnect), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun GitHubErrorNotice(errorResId: Int, onRetry: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(errorResId), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.github_retry)) }
        }
    }
}

@Composable
fun GitHubEmptyRepositories() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
        Text(stringResource(R.string.github_repositories_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun RepoItem(repo: GitHubRepo, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(repo.fullName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
            repo.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(repo.owner.login, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(repo.defaultBranch, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun LinkedRepositoryItem(mapping: RepositoryMapping) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${mapping.repoOwner}/${mapping.repoName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.github_link_repo_project, mapping.projectId), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(mapping.defaultBranch, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun LinkRepoDialog(
    repo: GitHubRepo,
    projectId: String,
    isSaving: Boolean,
    errorResId: Int?,
    onDismiss: () -> Unit,
    onConfirm: (applicationId: String, branch: String) -> Unit
) {
    var branch by remember(repo.id) { mutableStateOf(repo.defaultBranch) }
    var selectedAppId by remember(repo.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.github_link_repo_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(repo.fullName, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.github_link_repo_project, projectId))
                OutlinedTextField(
                    value = selectedAppId,
                    onValueChange = { selectedAppId = it },
                    label = { Text(stringResource(R.string.github_link_repo_app_id)) },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text(stringResource(R.string.github_link_repo_branch)) },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.github_link_repo_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                errorResId?.let { GitHubErrorNotice(it) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedAppId.trim(), branch.trim()) }, enabled = selectedAppId.isNotBlank() && branch.isNotBlank() && !isSaving) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.github_link_repo_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text(stringResource(R.string.github_link_repo_cancel)) }
        }
    )
}
