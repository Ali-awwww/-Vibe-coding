package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.model.*
import com.example.domain.repository.GitHubRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class GitHubUiState(
    val connection: GitHubConnection? = null,
    val repositories: List<GitHubRepo> = emptyList(),
    val branches: List<GitHubBranch> = emptyList(),
    val mappings: List<RepositoryMapping> = emptyList(),
    val isLoading: Boolean = false,
    val isLinkingRepository: Boolean = false,
    val errorResId: Int? = null
)

class GitHubViewModel(private val repository: GitHubRepository, private val userId: String) : ViewModel() {
    private val _uiState = MutableStateFlow(GitHubUiState())
    val uiState = _uiState.asStateFlow()

    init { loadConnection() }

    fun loadConnection() {
        if (userId.isBlank()) {
            _uiState.value = GitHubUiState(errorResId = R.string.github_sign_in_required)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorResId = null)
            val result = repository.getGitHubConnection(userId)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    connection = null,
                    isLoading = false,
                    errorResId = requestError(result.exceptionOrNull(), R.string.github_error_load_connection)
                )
                return@launch
            }
            val connection = result.getOrNull()
            _uiState.value = _uiState.value.copy(connection = connection, isLoading = false, errorResId = null)
            if (connection != null) fetchRepositories()
        }
    }

    fun connectWithToken(token: String) {
        if (userId.isBlank()) {
            _uiState.value = _uiState.value.copy(errorResId = R.string.github_sign_in_required)
            return
        }
        val normalizedToken = token.trim()
        if (normalizedToken.isBlank()) {
            _uiState.value = _uiState.value.copy(errorResId = R.string.github_token_required)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorResId = null)
            val result = repository.connectGitHubWithToken(userId, normalizedToken)
            if (result.isSuccess) loadConnection()
            else _uiState.value = _uiState.value.copy(isLoading = false, errorResId = requestError(result.exceptionOrNull(), R.string.github_error_connect))
        }
    }

    fun disconnect() {
        if (userId.isBlank()) {
            _uiState.value = _uiState.value.copy(errorResId = R.string.github_sign_in_required)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorResId = null)
            val result = repository.disconnectGitHub(userId)
            if (result.isSuccess) _uiState.value = GitHubUiState()
            else _uiState.value = _uiState.value.copy(isLoading = false, errorResId = requestError(result.exceptionOrNull(), R.string.github_error_disconnect))
        }
    }

    fun fetchRepositories() {
        if (userId.isBlank()) {
            _uiState.value = _uiState.value.copy(errorResId = R.string.github_sign_in_required)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, repositories = emptyList(), errorResId = null)
            val result = repository.fetchRepositories(userId)
            val failure = result.exceptionOrNull()
            if ((failure as? HttpException)?.code() == 401) {
                val removal = runCatching { repository.disconnectGitHub(userId) }.getOrNull()
                _uiState.value = _uiState.value.copy(
                    connection = null,
                    repositories = emptyList(),
                    isLoading = false,
                    errorResId = if (removal?.isSuccess == true) R.string.github_error_invalid_token else R.string.github_error_disconnect
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    repositories = result.getOrNull().orEmpty(),
                    isLoading = false,
                    errorResId = failure?.let { requestError(it, R.string.github_error_load_repositories) }
                )
            }
        }
    }

    fun fetchBranches(owner: String, repo: String) {
        if (userId.isBlank()) {
            _uiState.value = _uiState.value.copy(errorResId = R.string.github_sign_in_required)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorResId = null)
            val result = repository.fetchBranches(userId, owner, repo)
            _uiState.value = _uiState.value.copy(
                branches = result.getOrNull().orEmpty(),
                isLoading = false,
                errorResId = result.exceptionOrNull()?.let { requestError(it, R.string.github_error_load_branches) }
            )
        }
    }

    fun linkRepo(applicationId: String, projectId: String, repo: GitHubRepo, branch: String, onComplete: (Boolean) -> Unit = {}) {
        if (userId.isBlank()) {
            _uiState.value = _uiState.value.copy(errorResId = R.string.github_sign_in_required)
            onComplete(false)
            return
        }
        if (applicationId.isBlank() || projectId.isBlank() || branch.isBlank()) {
            _uiState.value = _uiState.value.copy(errorResId = R.string.github_error_invalid_mapping)
            onComplete(false)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLinkingRepository = true, errorResId = null)
            val mapping = RepositoryMapping(applicationId, projectId, repo.owner.login, repo.name, branch)
            val result = repository.linkRepositoryToApp(mapping)
            _uiState.value = _uiState.value.copy(
                isLinkingRepository = false,
                errorResId = result.exceptionOrNull()?.let { requestError(it, R.string.github_error_link_repository) }
            )
            if (result.isSuccess) {
                loadMappings(projectId)
                onComplete(true)
            } else onComplete(false)
        }
    }

    fun loadMappings(projectId: String) {
        if (projectId.isBlank()) return
        viewModelScope.launch {
            repository.getMappingsForProject(projectId)
                .catch { _uiState.value = _uiState.value.copy(errorResId = R.string.github_error_load_mappings) }
                .collect { _uiState.value = _uiState.value.copy(mappings = it) }
        }
    }

    private fun requestError(error: Throwable?, fallbackResId: Int): Int =
        when ((error as? HttpException)?.code()) {
            401 -> R.string.github_error_invalid_token
            403 -> R.string.github_error_permission_denied
            else -> fallbackResId
        }
}
