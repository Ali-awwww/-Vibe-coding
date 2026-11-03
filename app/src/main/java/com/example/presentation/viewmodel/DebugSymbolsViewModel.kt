package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.SymbolicationRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class DebugSymbolsUiState(
    val files: List<DebugSymbolFile> = emptyList(),
    val applications: List<Application> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class DebugSymbolsViewModel(
    private val repository: SymbolicationRepository,
    private val crashRepository: com.example.domain.repository.CrashRepository,
    private val projectId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugSymbolsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            combine(
                repository.getDebugSymbols(projectId),
                crashRepository.getApplications(projectId)
            ) { files, apps ->
                _uiState.value = _uiState.value.copy(
                    files = files,
                    applications = apps,
                    isLoading = false
                )
            }.collect()
        }
    }

    fun uploadFile(
        applicationId: String,
        type: SymbolFileType,
        version: String,
        buildId: String?,
        uuid: String?,
        fileName: String,
        content: String
    ) {
        viewModelScope.launch {
            val file = DebugSymbolFile(
                id = UUID.randomUUID().toString(),
                applicationId = applicationId,
                projectId = projectId,
                type = type,
                appVersion = version,
                buildId = buildId,
                uuid = uuid,
                fileName = fileName,
                fileSize = content.length.toLong(),
                fileContent = content,
                status = SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED
            )
            repository.uploadDebugSymbol(file)
        }
    }

    fun deleteFile(id: String) {
        viewModelScope.launch {
            repository.deleteDebugSymbol(id)
        }
    }
}
