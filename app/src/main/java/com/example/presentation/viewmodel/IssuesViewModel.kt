package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Issue
import com.example.domain.model.Project
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class IssuesUiState(
    val projects: List<Project> = emptyList(),
    val selectedProject: Project? = null,
    val issues: List<Issue> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null
)

class IssuesViewModel(
    private val repository: CrashRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IssuesUiState())
    val uiState: StateFlow<IssuesUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            repository.getProjects().collect { projects ->
                _uiState.update { it.copy(projects = projects) }
                if (projects.isNotEmpty() && _uiState.value.selectedProject == null) {
                    selectProject(projects.first())
                }
            }
        }
    }

    fun selectProject(project: Project) {
        _uiState.update { it.copy(selectedProject = project, isLoading = true) }
        viewModelScope.launch {
            repository.getIssues(project.id).collect { issues ->
                _uiState.update { it.copy(issues = issues, isLoading = false) }
            }
        }
    }

    fun syncIssues() {
        val project = _uiState.value.selectedProject ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            repository.syncCrashes(project.id).onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update { it.copy(isSyncing = false) }
        }
    }
}
