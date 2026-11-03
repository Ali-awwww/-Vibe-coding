package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.GitHubRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GitHubUiState(
    val connection: GitHubConnection? = null,
    val repositories: List<GitHubRepo> = emptyList(),
    val branches: List<GitHubBranch> = emptyList(),
    val mappings: List<RepositoryMapping> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class GitHubViewModel(
    private val repository: GitHubRepository,
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitHubUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadConnection()
    }

    fun loadConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.getGitHubConnection(userId)
            _uiState.value = _uiState.value.copy(
                connection = result.getOrNull(),
                isLoading = false
            )
            if (result.getOrNull() != null) {
                fetchRepositories()
            }
        }
    }

    fun connect(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.connectGitHub(userId, code)
            if (result.isSuccess) {
                loadConnection()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun connectWithToken(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.connectGitHubWithToken(userId, token)
            if (result.isSuccess) {
                loadConnection()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = result.exceptionOrNull()?.message ?: "فشل الاتصال بـ GitHub")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnectGitHub(userId)
            _uiState.value = GitHubUiState()
        }
    }

    fun fetchRepositories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.fetchRepositories(userId)
            _uiState.value = _uiState.value.copy(
                repositories = result.getOrNull() ?: emptyList(),
                isLoading = false
            )
        }
    }

    fun fetchBranches(owner: String, repo: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.fetchBranches(userId, owner, repo)
            _uiState.value = _uiState.value.copy(
                branches = result.getOrNull() ?: emptyList(),
                isLoading = false
            )
        }
    }

    fun linkRepo(applicationId: String, projectId: String, repo: GitHubRepo, branch: String) {
        viewModelScope.launch {
            val mapping = RepositoryMapping(
                applicationId = applicationId,
                projectId = projectId,
                repoOwner = repo.owner.login,
                repoName = repo.name,
                defaultBranch = branch
            )
            repository.linkRepositoryToApp(mapping)
            loadMappings(projectId)
        }
    }

    fun loadMappings(projectId: String) {
        viewModelScope.launch {
            repository.getMappingsForProject(projectId).collect {
                _uiState.value = _uiState.value.copy(mappings = it)
            }
        }
    }
}
