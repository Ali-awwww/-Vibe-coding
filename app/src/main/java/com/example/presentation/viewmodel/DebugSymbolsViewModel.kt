package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.model.*
import com.example.domain.repository.SymbolicationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class DebugSymbolsUiState(
    val files: List<DebugSymbolFile> = emptyList(),
    val applications: List<Application> = emptyList(),
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val errorResId: Int? = null
)

class DebugSymbolsViewModel(
    private val repository: SymbolicationRepository,
    private val crashRepository: com.example.domain.repository.CrashRepository,
    private val projectId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugSymbolsUiState())
    val uiState = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init { loadData() }

    fun refresh() = loadData()

    private fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorResId = null) }
            combine(
                repository.getDebugSymbols(projectId),
                crashRepository.getApplications(projectId)
            ) { files, apps -> files to apps }
                .catch {
                    _uiState.update { it.copy(isLoading = false, errorResId = R.string.debug_symbols_load_failed) }
                }
                .collect { (files, apps) ->
                    _uiState.update { it.copy(files = files, applications = apps, isLoading = false) }
                }
        }
    }

    fun uploadFile(
        applicationId: String,
        type: SymbolFileType,
        version: String,
        buildId: String?,
        uuid: String?,
        fileName: String,
        content: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (applicationId.isBlank() || projectId.isBlank() || version.isBlank() || fileName.isBlank() || content.isBlank()) {
            _uiState.update { it.copy(errorResId = R.string.debug_symbols_invalid_input) }
            onComplete(false)
            return
        }

        val file = DebugSymbolFile(
            id = UUID.randomUUID().toString(),
            applicationId = applicationId,
            projectId = projectId,
            type = type,
            appVersion = version.trim(),
            buildId = buildId?.trim()?.ifBlank { null },
            uuid = uuid,
            fileName = fileName.trim(),
            fileSize = content.toByteArray(Charsets.UTF_8).size.toLong(),
            fileContent = content,
            status = SymbolicationStatus.PENDING
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, errorResId = null) }
            val result = repository.uploadDebugSymbol(file)
            _uiState.update {
                it.copy(
                    isUploading = false,
                    errorResId = if (result.isSuccess) null else R.string.debug_symbols_upload_failed
                )
            }
            onComplete(result.isSuccess)
        }
    }

    fun deleteFile(id: String) {
        viewModelScope.launch {
            val result = repository.deleteDebugSymbol(id)
            if (result.isFailure) {
                _uiState.update { it.copy(errorResId = R.string.debug_symbols_delete_failed) }
            }
        }
    }
}
