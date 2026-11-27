package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Integration
import com.example.domain.model.IntegrationType
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
    val error: String? = null,
    val isAllProjectsScope: Boolean = false,
    val lastSyncAt: Long? = null
)

class IssuesViewModel(
    private val repository: CrashRepository,
    private val initialProjectId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(IssuesUiState())
    val uiState: StateFlow<IssuesUiState> = _uiState.asStateFlow()

    private var projectsJob: Job? = null
    private var issuesJob: Job? = null
    private var syncTimeJob: Job? = null
    private var observedAllProjectIds: Set<String>? = null
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
                        val state = _uiState.value
                        val selectedId = state.selectedProject?.id
                        if (selectedId != null && projects.none { it.id == selectedId }) {
                            issuesJob?.cancel()
                            syncTimeJob?.cancel()
                            observedAllProjectIds = null
                            _uiState.update {
                                it.copy(
                                    selectedProject = null,
                                    isAllProjectsScope = false,
                                    issues = emptyList(),
                                    lastSyncAt = null,
                                    isLoading = false,
                                    error = ERROR_PROJECT_UNAVAILABLE
                                )
                            }
                        } else if (state.isAllProjectsScope) {
                            observeSyncTime(projects, selectedProjectId = null)
                            observeAllProjectsIssues(projects)
                        } else if (selectedId != null) {
                            val currentProject = projects.firstOrNull { it.id == selectedId }
                            if (currentProject != null && currentProject != state.selectedProject) {
                                _uiState.update { it.copy(selectedProject = currentProject) }
                            }
                            observeSyncTime(projects, selectedProjectId = selectedId)
                        }
                    }
                }
        }
    }

    fun selectAllProjects() {
        initialSelectionResolved = true
        observedAllProjectIds = null
        issuesJob?.cancel()
        val projects = _uiState.value.projects
        _uiState.update {
            it.copy(
                selectedProject = null,
                isAllProjectsScope = true,
                lastSyncAt = null,
                isLoading = true,
                error = null
            )
        }
        observeSyncTime(projects, selectedProjectId = null)
        observeAllProjectsIssues(projects)
    }

    fun selectProject(project: Project) {
        initialSelectionResolved = true
        val authorizedProject = _uiState.value.projects.firstOrNull { it.id == project.id }
        if (authorizedProject == null) {
            issuesJob?.cancel()
            syncTimeJob?.cancel()
            observedAllProjectIds = null
            _uiState.update {
                it.copy(
                    selectedProject = null,
                    isAllProjectsScope = false,
                    issues = emptyList(),
                    lastSyncAt = null,
                    isLoading = false,
                    error = ERROR_PROJECT_UNAVAILABLE
                )
            }
            return
        }
        observedAllProjectIds = null
        issuesJob?.cancel()
        val projects = _uiState.value.projects
        _uiState.update {
            it.copy(
                selectedProject = authorizedProject,
                isAllProjectsScope = false,
                lastSyncAt = null,
                isLoading = true,
                error = null
            )
        }
        observeSyncTime(projects, selectedProjectId = project.id)
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
        if (_uiState.value.error == ERROR_SYNC) {
            syncIssues()
            return
        }
        val selected = _uiState.value.selectedProject
        if (selected == null) {
            if (initialProjectId != null && !_uiState.value.isAllProjectsScope) loadProjects()
            else selectAllProjects()
        } else {
            selectProject(selected)
        }
    }


    private fun observeAllProjectsIssues(projects: List<Project>) {
        val projectIds = projects.map { it.id }.filter { it.isNotBlank() }.toSet()
        if (observedAllProjectIds == projectIds) return

        observedAllProjectIds = projectIds
        issuesJob?.cancel()
        _uiState.update {
            it.copy(
                issues = emptyList(),
                isLoading = projectIds.isNotEmpty(),
                error = null
            )
        }
        if (projectIds.isEmpty()) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        issuesJob = viewModelScope.launch {
            repository.getAllIssues()
                .map { issues -> issues.filter { it.projectId in projectIds } }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    _uiState.update { it.copy(isLoading = false, error = ERROR_LOAD) }
                }
                .collect { issues ->
                    _uiState.update { it.copy(issues = issues, isLoading = false, error = null) }
                }
        }
    }

    private fun observeSyncTime(projects: List<Project>, selectedProjectId: String?) {
        syncTimeJob?.cancel()
        val scopedProjects = selectedProjectId?.let { id ->
            projects.filter { it.id == id }
        } ?: projects
        if (scopedProjects.isEmpty()) {
            _uiState.update { it.copy(lastSyncAt = null) }
            return
        }
        val integrationFlows = scopedProjects.map { repository.getIntegrations(it.id) }
        syncTimeJob = viewModelScope.launch {
            combine(integrationFlows) { snapshots ->
                snapshots.asList()
                    .flatten()
                    .filterNot { it.type == IntegrationType.INTERNAL_QA }
                    .mapNotNull(Integration::lastSyncedAt)
                    .maxOrNull()
            }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    _uiState.update { it.copy(lastSyncAt = null) }
                }
                .collect { timestamp ->
                    _uiState.update { it.copy(lastSyncAt = timestamp) }
                }
        }
    }

    fun syncIssues() {
        val state = _uiState.value
        if (state.isSyncing) return
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
