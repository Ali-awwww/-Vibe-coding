package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CrashReport
import com.example.domain.repository.CrashRepository
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

    val uiState: StateFlow<CrashesUiState> = combine(
        repository.getAllCrashes(),
        _filter
    ) { allCrashes, filter ->
        val filtered = allCrashes.filter { crash ->
            val matchProject = filter.projectId == null || crash.projectId == filter.projectId
            val matchApp = filter.applicationId == null || crash.applicationId == filter.applicationId
            val matchException = filter.exceptionType == null || crash.exceptionType.equals(filter.exceptionType, ignoreCase = true)
            val matchVersion = filter.appVersion == null || crash.appVersion.equals(filter.appVersion, ignoreCase = true)
            val matchSeverity = filter.severity == null || crash.severity.name.equals(filter.severity, ignoreCase = true)
            val matchOs = filter.osName == null || crash.osVersion.contains(filter.osName, ignoreCase = true)
            val matchQuery = filter.searchQuery.isBlank() ||
                    crash.exceptionType.contains(filter.searchQuery, ignoreCase = true) ||
                    crash.message.contains(filter.searchQuery, ignoreCase = true) ||
                    crash.deviceModel.contains(filter.searchQuery, ignoreCase = true) ||
                    crash.stackTrace.contains(filter.searchQuery, ignoreCase = true)

            matchProject && matchApp && matchException && matchVersion && matchSeverity && matchOs && matchQuery
        }

        CrashesUiState(
            crashes = allCrashes,
            filteredCrashes = filtered,
            filter = filter,
            isLoading = false
        )
    }.catch { e ->
        emit(CrashesUiState(isLoading = false, error = e.message))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CrashesUiState())

    fun updateSearchQuery(query: String) {
        _filter.update { it.copy(searchQuery = query) }
    }

    fun clearFilters() {
        _filter.update {
            CrashesFilterState()
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
