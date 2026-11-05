package com.example.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.repository.AuthRepository
import com.example.domain.validation.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class ValidationTab(val labelAr: String, val labelEn: String) {
    MATRIX_20("مصفوفة الـ 20 فحصًا", "20-Point Matrix"),
    VIBE_SANDBOX("فريق Vibe Coding المعزول", "Vibe Sandbox"),
    CRASH_SANDBOX("فريق Crash Investigation", "Crash Sandbox"),
    REPORT("تقرير الاعتماد الشامل", "Success Report")
}

data class TeamValidationUiState(
    val selectedTab: ValidationTab = ValidationTab.MATRIX_20,
    val selectedCategory: ValidationCategory? = null,
    val matrixProgress: TeamValidationProgress = TeamValidationProgress(),
    val vibeProgress: VibeCodingSandboxProgress = VibeCodingSandboxProgress(),
    val crashProgress: CrashInvestigationSandboxProgress = CrashInvestigationSandboxProgress(),
    val summaryReport: TeamValidationSummaryReport? = null,
    val expandedTestIds: Set<Int> = emptySet(),
    val isExporting: Boolean = false,
    val exportSuccessMessage: String? = null,
    val errorMessage: String? = null
)

class TeamValidationViewModel(
    private val validationEngine: TeamValidationEngine,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TeamValidationUiState(
            matrixProgress = TeamValidationProgress(items = validationEngine.getInitialTests())
        )
    )
    val uiState: StateFlow<TeamValidationUiState> = _uiState.asStateFlow()

    init {
        // Pre-initialize initial report preview
        viewModelScope.launch {
            val initialReport = validationEngine.generateValidationReport(
                testItems = validationEngine.getInitialTests(),
                vibeProgress = null,
                crashProgress = null
            )
            _uiState.update { it.copy(summaryReport = initialReport) }
        }
    }

    private fun getUserId(): String? = authRepository.getCurrentUserSync()?.id?.takeIf { it.isNotBlank() }

    private fun requireAuthenticatedUserId(): String? = getUserId().also { userId ->
        if (userId == null) _uiState.update { it.copy(errorMessage = "سجّل الدخول بحساب صالح لتشغيل اختبارات التحقق.") }
    }

    fun selectTab(tab: ValidationTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun filterCategory(category: ValidationCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun toggleExpandTest(testId: Int) {
        _uiState.update { current ->
            val set = current.expandedTestIds.toMutableSet()
            if (set.contains(testId)) set.remove(testId) else set.add(testId)
            current.copy(expandedTestIds = set)
        }
    }

    fun runAllMatrixTests() {
        if (_uiState.value.matrixProgress.isRunning) return
        val userId = requireAuthenticatedUserId() ?: return
        viewModelScope.launch {
            validationEngine.runFullValidationSuite(userId).collect { progress ->
                _uiState.update { current ->
                    current.copy(
                        matrixProgress = progress,
                        summaryReport = progress.summaryReport ?: current.summaryReport
                    )
                }
            }
        }
    }

    fun runSingleTest(testId: Int) {
        val userId = getUserId()
        viewModelScope.launch {
            validationEngine.runSingleTest(userId, testId).collect { resultItem ->
                _uiState.update { current ->
                    val updatedItems = current.matrixProgress.items.map {
                        if (it.id == testId) resultItem else it
                    }
                    current.copy(
                        matrixProgress = current.matrixProgress.copy(items = updatedItems)
                    )
                }
            }
            // Refresh summary report
            val newReport = validationEngine.generateValidationReport(
                testItems = _uiState.value.matrixProgress.items,
                vibeProgress = _uiState.value.vibeProgress,
                crashProgress = _uiState.value.crashProgress
            )
            _uiState.update { it.copy(summaryReport = newReport) }
        }
    }

    fun runVibeSandbox() {
        if (_uiState.value.vibeProgress.isRunning) return
        val userId = getUserId()
        viewModelScope.launch {
            validationEngine.runVibeCodingSandbox(userId).collect { progress ->
                _uiState.update { it.copy(vibeProgress = progress) }
            }
        }
    }

    fun runCrashSandbox() {
        if (_uiState.value.crashProgress.isRunning) return
        val userId = getUserId()
        viewModelScope.launch {
            validationEngine.runCrashInvestigationSandbox(userId).collect { progress ->
                _uiState.update { it.copy(crashProgress = progress) }
            }
        }
    }

    fun exportMarkdownReport(context: Context) {
        val report = _uiState.value.summaryReport ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                // Save locally to files dir
                val exportFile = File(context.filesDir, "ai_model_teams_validation_report.md")
                exportFile.writeText(report.markdownReport)

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportSuccessMessage = "تم تصدير التقرير الفني بنجاح (${exportFile.name})"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = "فشل تصدير التقرير: ${e.message}"
                    )
                }
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(exportSuccessMessage = null, errorMessage = null) }
    }
}
