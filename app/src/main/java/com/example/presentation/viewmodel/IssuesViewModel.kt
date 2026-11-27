package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Issue
import com.example.domain.model.Project
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class IssuesUiState(
    val projects: List<Project> = emptyList(),
    val selectedProject: Project? = null,
    val issues: List<Issue> = emptyList(),
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val error: String? = null
)

class IssuesViewModel(
    private val repository: CrashRepository,
    private val initialProjectId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(IssuesUiState())
    val uiState: StateFlow<IssuesUiState> = _uiState.asStateFlow()

    private var projectsJob: Job? = null
    private var issuesJob: Job? = null
    private var initialSelectionResolved = false

    init {
        loadProjects()
    }

    private fun loadProjects() {
        projectsJob?.cancel()
        projectsJob = viewModelScope.launch {
            repository.getProjects()
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    _uiState.update { it.copy(isLoading = false, error = ERROR_LOAD) }
                }
                .collect { projects ->
                    _uiState.update { it.copy(projects = projects) }
                    if (!initialSelectionResolved) {
                        if (initialProjectId == null) {
                            initialSelectionResolved = true
                            selectAllProjects()
                        } else {
                            val requestedProject = projects.firstOrNull { it.id == initialProjectId }
                            if (requestedProject != null) {
                                initialSelectionResolved = true
                                selectProject(requestedProject)
                            } else {
                                _uiState.update { it.copy(isLoading = false, error = ERROR_PROJECT_UNAVAILABLE) }
                            }
                        }
                    } else {
                        val selectedId = _uiState.value.selectedProject?.id
                        if (selectedId != null && projects.none { it.id == selectedId }) {
                            issuesJob?.cancel()
                            _uiState.update {
                                it.copy(
                                    selectedProject = null,
                                    issues = emptyList(),
                                    isLoading = false,
                                    error = ERROR_PROJECT_UNAVAILABLE
                                )
                            }
                        }
                    }
                }
        }
    }

    fun selectAllProjects() {
        initialSelectionResolved = true
        issuesJob?.cancel()
        _uiState.update { it.copy(selectedProject = null, isLoading = true, error = null) }
        issuesJob = viewModelScope.launch {
            repository.getAllIssues()
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    _uiState.update { it.copy(isLoading = false, error = ERROR_LOAD) }
                }
                .collect { issues ->
                    _uiState.update { it.copy(issues = issues, isLoading = false, error = null) }
                }
        }
    }

    fun selectProject(project: Project) {
        initialSelectionResolved = true
        issuesJob?.cancel()
        _uiState.update { it.copy(selectedProject = project, isLoading = true, error = null) }
        issuesJob = viewModelScope.launch {
            repository.getIssues(project.id)
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    _uiState.update { it.copy(isLoading = false, error = ERROR_LOAD) }
                }
                .collect { issues ->
                    _uiState.update { it.copy(issues = issues, isLoading = false, error = null) }
                }
        }
    }

    fun retry() {
        val selected = _uiState.value.selectedProject
        if (selected == null) {
            if (initialProjectId != null && !initialSelectionResolved) loadProjects()
            else selectAllProjects()
        } else {
            selectProject(selected)
        }
    }

    fun syncIssues() {
        val state = _uiState.value
        val projectsToSync = state.selectedProject?.let(::listOf) ?: state.projects
        if (projectsToSync.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, error = null) }
            try {
                var failed = false
                for (project in projectsToSync) {
                    if (repository.syncCrashes(project.id).isFailure) failed = true
                }
                if (failed) _uiState.update { it.copy(error = ERROR_SYNC) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(error = ERROR_SYNC) }
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    companion object {
        const val ERROR_PROJECT_UNAVAILABLE = "PROJECT_UNAVAILABLE"
        const val ERROR_LOAD = "LOAD_FAILED"
        const val ERROR_SYNC = "SYNC_FAILED"
    }
}
