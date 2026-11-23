package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CrashReport
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.CancellationException
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
    val filter: CrashesFilterState = CrashesFilterState(),
    val isLoading: Boolean = true,
    val error: String? = null
)

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

    val uiState: StateFlow<CrashesUiState> = combine(_filter, _refreshTrigger) { filter, _ -> filter }
        .flatMapLatest { filter ->
            repository.getAllCrashes()
                .map { allCrashes ->
                    val filtered = allCrashes.filter { crash ->
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

                    CrashesUiState(
                        crashes = allCrashes,
                        filteredCrashes = filtered,
                        filter = filter,
                        isLoading = false
                    )
                }
                .onStart { emit(CrashesUiState(filter = filter, isLoading = true)) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(
                        CrashesUiState(
                            filter = filter,
                            isLoading = false,
                            error = error.message ?: "Crash reports could not be loaded."
                        )
                    )
                }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CrashesUiState(filter = _filter.value)
        )

    fun updateSearchQuery(query: String) {
        _filter.update { it.copy(searchQuery = query) }
    }

    fun refresh() {
        _refreshTrigger.update { it + 1 }
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
                "project" -> current.copy(projectId = null)
                "app" -> current.copy(applicationId = null)
                else -> current
            }
        }
    }
}
