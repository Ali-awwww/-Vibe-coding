package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CrashReport
import com.example.domain.model.IntegrationStatus
import com.example.domain.model.isSimulatedQa
import com.example.domain.repository.CrashPageCursor
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CrashesFilterState(
    val projectId: String? = null,
    val applicationId: String? = null,
    val exceptionType: String? = null,
    val appVersion: String? = null,
    val severity: String? = null,
    val osName: String? = null,
    val searchQuery: String = "",
    val filterTitle: String? = null
)

data class CrashesUiState(
    val crashes: List<CrashReport> = emptyList(),
    val filteredCrashes: List<CrashReport> = emptyList(),
    val visibleCrashes: List<CrashReport> = emptyList(),
    val simulatedQaReports: List<CrashReport> = emptyList(),
    val filter: CrashesFilterState = CrashesFilterState(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null,
    val canLoadMore: Boolean = false,
    val hasConnectedCrashSource: Boolean = true
)

private data class CrashPagingState(
    val isLoading: Boolean,
    val error: String?,
    val isLoadingMore: Boolean,
    val loadMoreError: String?,
    val nextCursor: CrashPageCursor?
)

class CrashesViewModel(
    private val repository: CrashRepository,
    private val initialProjectId: String? = null,
    private val initialAppId: String? = null,
    initialExceptionType: String? = null,
    initialAppVersion: String? = null,
    initialSeverity: String? = null,
    initialOsName: String? = null,
    initialFilterTitle: String? = null
) : ViewModel() {

    private val _filter = MutableStateFlow(
        CrashesFilterState(
            projectId = initialProjectId,
            applicationId = initialAppId,
            exceptionType = initialExceptionType,
            appVersion = initialAppVersion,
            severity = initialSeverity,
            osName = initialOsName,
            filterTitle = initialFilterTitle
        )
    )
    private val _crashes = MutableStateFlow<List<CrashReport>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _loadMoreError = MutableStateFlow<String?>(null)
    private val _nextCursor = MutableStateFlow<CrashPageCursor?>(null)
    private val _hasConnectedCrashSource = MutableStateFlow(true)

    private var loadJob: Job? = null
    private var loadGeneration = 0

    private val pagingState = combine(
        _isLoading,
        _error,
        _isLoadingMore,
        _loadMoreError,
        _nextCursor
    ) { isLoading, error, isLoadingMore, loadMoreError, nextCursor ->
        CrashPagingState(isLoading, error, isLoadingMore, loadMoreError, nextCursor)
    }

    private val _simulatedQaReports: Flow<List<CrashReport>> = repository
        .getSimulatedQaReports(initialProjectId, initialAppId)
        .map { reports -> reports.filter { it.isSimulatedQa() } }
        .catch { emit(emptyList()) }

    val uiState: StateFlow<CrashesUiState> = combine(
        _crashes,
        _simulatedQaReports,
        _filter,
        pagingState,
        _hasConnectedCrashSource
    ) { crashes, simulatedQa, filter, paging, hasConnectedSource ->
        val realCrashes = crashes.filterNot { it.isSimulatedQa() }
        val filtered = if (paging.error != null && realCrashes.isEmpty()) {
            emptyList()
        } else {
            realCrashes.filter { crash ->
                val matchProject = filter.projectId == null || crash.projectId == filter.projectId
                val matchApp = filter.applicationId == null || crash.applicationId == filter.applicationId
                val matchException = filter.exceptionType == null ||
                    crash.exceptionType.equals(filter.exceptionType, ignoreCase = true)
                val matchVersion = filter.appVersion == null ||
                    crash.appVersion.equals(filter.appVersion, ignoreCase = true)
                val matchSeverity = filter.severity == null ||
                    crash.severity.name.equals(filter.severity, ignoreCase = true)
                val matchOs = filter.osName == null ||
                    crash.osVersion.contains(filter.osName, ignoreCase = true)
                val matchQuery = filter.searchQuery.isBlank() ||
                    crash.exceptionType.contains(filter.searchQuery, ignoreCase = true) ||
                    crash.message.contains(filter.searchQuery, ignoreCase = true) ||
                    crash.deviceModel.contains(filter.searchQuery, ignoreCase = true) ||
                    crash.stackTrace.contains(filter.searchQuery, ignoreCase = true)

                matchProject && matchApp && matchException && matchVersion &&
                    matchSeverity && matchOs && matchQuery
            }.sortedWith(compareByDescending<CrashReport> { it.timestamp }.thenByDescending { it.id })
        }

        val filteredSimulatedQa = simulatedQa.filter { report ->
            val matchQuery = filter.searchQuery.isBlank() ||
                report.exceptionType.contains(filter.searchQuery, ignoreCase = true) ||
                report.message.contains(filter.searchQuery, ignoreCase = true) ||
                report.stackTrace.contains(filter.searchQuery, ignoreCase = true)
            matchQuery
        }.sortedWith(compareByDescending<CrashReport> { it.timestamp }.thenByDescending { it.id })

        CrashesUiState(
            crashes = realCrashes,
            filteredCrashes = filtered,
            visibleCrashes = filtered,
            simulatedQaReports = filteredSimulatedQa,
            filter = filter,
            isLoading = paging.isLoading,
            error = paging.error,
            isLoadingMore = paging.isLoadingMore,
            loadMoreError = paging.loadMoreError,
            canLoadMore = paging.nextCursor != null,
            hasConnectedCrashSource = hasConnectedSource
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CrashesUiState(filter = _filter.value)
    )

    init {
        refresh()
    }

    fun updateSearchQuery(query: String) {
        _filter.update { it.copy(searchQuery = query) }
    }

    fun applyFilters(
        exceptionType: String,
        appVersion: String,
        severity: String?,
        osName: String
    ) {
        _filter.update {
            it.copy(
                exceptionType = exceptionType.trim().takeIf { value -> value.isNotEmpty() },
                appVersion = appVersion.trim().takeIf { value -> value.isNotEmpty() },
                severity = severity,
                osName = osName.trim().takeIf { value -> value.isNotEmpty() },
                filterTitle = null
            )
        }
    }

    fun refresh() {
        loadPage(cursor = null, replace = true)
    }

    fun loadMore() {
        val cursor = _nextCursor.value ?: return
        if (_isLoading.value || _isLoadingMore.value) return
        loadPage(cursor = cursor, replace = false)
    }

    private fun loadPage(cursor: CrashPageCursor?, replace: Boolean) {
        val generation = ++loadGeneration
        loadJob?.cancel()
        if (replace) {
            _crashes.value = emptyList()
            _nextCursor.value = null
            _isLoading.value = true
            _isLoadingMore.value = false
            _error.value = null
            _loadMoreError.value = null
        } else {
            _isLoadingMore.value = true
            _loadMoreError.value = null
        }

        loadJob = viewModelScope.launch {
            try {
                val page = repository.getCrashPage(
                    projectId = initialProjectId,
                    applicationId = initialAppId,
                    cursor = cursor,
                    pageSize = PAGE_SIZE
                )
                if (generation != loadGeneration) return@launch

                val realPageCrashes = page.crashes.filterNot { it.isSimulatedQa() }
                if (replace) {
                    _crashes.value = realPageCrashes
                    val integrations = runCatching {
                        if (initialProjectId != null) {
                            repository.getIntegrations(initialProjectId).firstOrNull().orEmpty()
                        } else {
                            repository.getProjects().firstOrNull().orEmpty().flatMap { project ->
                                repository.getIntegrations(project.id).firstOrNull().orEmpty()
                            }
                        }
                    }.getOrElse { emptyList() }
                    _hasConnectedCrashSource.value =
                        realPageCrashes.isNotEmpty() || integrations.any { it.status == IntegrationStatus.CONNECTED }
                } else {
                    _crashes.update { existing ->
                        (existing + realPageCrashes)
                            .distinctBy { it.id }
                            .sortedWith(compareByDescending<CrashReport> { it.timestamp }.thenByDescending { it.id })
                    }
                }
                _nextCursor.value = page.nextCursor
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == loadGeneration) {
                    if (replace) {
                        _error.value = error.message ?: "Crash reports could not be loaded."
                    } else {
                        _loadMoreError.value = error.message ?: "More crash reports could not be loaded."
                    }
                }
            } finally {
                if (generation == loadGeneration) {
                    _isLoading.value = false
                    _isLoadingMore.value = false
                }
            }
        }
    }

    fun clearFilters() {
        _filter.update {
            it.copy(
                exceptionType = null,
                appVersion = null,
                severity = null,
                osName = null,
                searchQuery = "",
                filterTitle = null
            )
        }
    }

    fun clearSpecificFilter(key: String) {
        _filter.update { current ->
            when (key) {
                "exception" -> current.copy(exceptionType = null, filterTitle = null)
                "version" -> current.copy(appVersion = null, filterTitle = null)
                "severity" -> current.copy(severity = null, filterTitle = null)
                "os" -> current.copy(osName = null, filterTitle = null)
                "search" -> current.copy(searchQuery = "")
                else -> current
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 25
    }
}
