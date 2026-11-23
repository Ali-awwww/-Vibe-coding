package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CrashReport
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

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
    val filter: CrashesFilterState = CrashesFilterState(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentPage: Int = 0,
    val canLoadMore: Boolean = false
)

private data class CrashSourceState(
    val crashes: List<CrashReport> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class CrashesViewModel(
    private val repository: CrashRepository,
    initialProjectId: String? = null,
    initialAppId: String? = null,
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
    private val _refreshTrigger = MutableStateFlow(0)
    private val _page = MutableStateFlow(0)

    // Project/application context is applied at the repository boundary before
    // the remaining interactive filters are evaluated in memory.
    private val crashSource: Flow<CrashSourceState> = _refreshTrigger.flatMapLatest {
        repository.getLatestCrashes(initialProjectId, initialAppId)
            .map { crashes -> CrashSourceState(crashes = crashes) }
            .onStart { emit(CrashSourceState(isLoading = true)) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(
                    CrashSourceState(
                        error = error.message ?: "Crash reports could not be loaded."
                    )
                )
            }
    }

    val uiState: StateFlow<CrashesUiState> = combine(crashSource, _filter, _page) { source, filter, page ->
        val filtered = if (source.isLoading || source.error != null) {
            emptyList()
        } else {
            source.crashes.filter { crash ->
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
            }.sortedByDescending { it.timestamp }
        }
        val visible = filtered.take((page + 1) * PAGE_SIZE)

        CrashesUiState(
            crashes = source.crashes,
            filteredCrashes = filtered,
            visibleCrashes = visible,
            filter = filter,
            isLoading = source.isLoading,
            error = source.error,
            currentPage = page,
            canLoadMore = visible.size < filtered.size
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CrashesUiState(filter = _filter.value)
    )

    fun updateSearchQuery(query: String) {
        updateFilter { it.copy(searchQuery = query) }
    }

    fun applyFilters(
        exceptionType: String,
        appVersion: String,
        severity: String?,
        osName: String
    ) {
        updateFilter {
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
        _page.value = 0
        _refreshTrigger.update { it + 1 }
    }

    fun loadMore() {
        if (uiState.value.canLoadMore) {
            _page.update { it + 1 }
        }
    }

    fun clearFilters() {
        updateFilter {
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
        updateFilter { current ->
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

    private fun updateFilter(transform: (CrashesFilterState) -> CrashesFilterState) {
        _page.value = 0
        _filter.update(transform)
    }

    private companion object {
        const val PAGE_SIZE = 25
    }
}
