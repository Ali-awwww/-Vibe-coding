package com.example.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.repository.AuthRepository
import com.example.domain.validation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ValidationTab(val labelAr: String, val labelEn: String) {
    MATRIX_20("مصفوفة الـ 20 فحصًا", "20-Point Matrix"),
    VIBE_SANDBOX("فريق Vibe Coding المعزول", "Vibe Sandbox"),
    CRASH_SANDBOX("فريق Crash Investigation", "Crash Sandbox"),
    REPORT("تقرير الاعتماد الشامل", "Validation Report")
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

    private var activeUserId: String? = authRepository.getCurrentUserSync()?.id?.takeIf { it.isNotBlank() }
    private var matrixJob: Job? = null
    private var vibeJob: Job? = null
    private var crashJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.currentUser
                .map { account -> account?.id?.takeIf { it.isNotBlank() } }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId != activeUserId) {
                        cancelUserScopedJobs()
                        activeUserId = userId
                        _uiState.update { current ->
                            TeamValidationUiState(
                                selectedTab = current.selectedTab,
                                matrixProgress = TeamValidationProgress(
                                    items = validationEngine.getInitialTests()
                                )
                            )
                        }
                    }
                }
        }
    }

    private fun getUserId(): String? =
        authRepository.getCurrentUserSync()?.id?.takeIf { it.isNotBlank() }

    private fun requireAuthenticatedUserId(): String? = getUserId().also { userId ->
        if (userId == null) {
            _uiState.update {
                it.copy(errorMessage = "سجّل الدخول بحساب صالح لتشغيل اختبارات التحقق.")
            }
        }
    }

    fun selectTab(tab: ValidationTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun filterCategory(category: ValidationCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun toggleExpandTest(testId: Int) {
        _uiState.update { current ->
            val expanded = current.expandedTestIds.toMutableSet()
            if (!expanded.add(testId)) expanded.remove(testId)
            current.copy(expandedTestIds = expanded)
        }
    }

    fun runAllMatrixTests() {
        if (_uiState.value.matrixProgress.isRunning) return
        val userId = requireAuthenticatedUserId() ?: return
        matrixJob?.cancel()
        _uiState.update { it.copy(summaryReport = null) }

        matrixJob = viewModelScope.launch {
            try {
                validationEngine.runFullValidationSuite(userId).collect { progress ->
                    if (getUserId() != userId) return@collect
                    _uiState.update { current ->
                        current.copy(
                            matrixProgress = progress,
                            summaryReport = progress.summaryReport ?: current.summaryReport
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                publishMatrixFailure(userId, null)
            }
        }
    }

    fun cancelMatrixTests() {
        val current = _uiState.value.matrixProgress
        if (!current.isRunning) return

        val userId = getUserId()
        matrixJob?.cancel()
        matrixJob = null

        val items = current.items.toMutableList()
        val activeIndex = items.indexOfFirst { it.status == ValidationStatus.RUNNING }
            .takeIf { it >= 0 }
            ?: items.indexOfFirst { it.status == ValidationStatus.PENDING }
        if (activeIndex >= 0) {
            items[activeIndex] = items[activeIndex].copy(
                status = ValidationStatus.SKIPPED,
                errorMessage = "أوقف المستخدم التشغيل قبل اكتمال هذا الفحص."
            )
        }

        _uiState.update {
            it.copy(
                matrixProgress = current.copy(
                    items = items,
                    isRunning = false,
                    currentTestName = ""
                ),
                errorMessage = "تم إيقاف تشغيل مصفوفة التحقق."
            )
        }

        viewModelScope.launch {
            val report = validationEngine.generateValidationReport(
                testItems = items,
                vibeProgress = _uiState.value.vibeProgress,
                crashProgress = _uiState.value.crashProgress
            )
            if (getUserId() == userId) {
                _uiState.update { it.copy(summaryReport = report) }
            }
        }
    }

    fun runSingleTest(testId: Int) {
        if (_uiState.value.matrixProgress.isRunning) return
        val userId = requireAuthenticatedUserId() ?: return
        val initial = _uiState.value.matrixProgress.items.firstOrNull { it.id == testId } ?: return
        matrixJob?.cancel()
        _uiState.update { current ->
            current.copy(
                summaryReport = null,
                matrixProgress = current.matrixProgress.copy(
                    currentTestIndex = current.matrixProgress.items.indexOfFirst { it.id == testId } + 1,
                    currentTestName = initial.nameAr,
                    isRunning = true,
                    items = current.matrixProgress.items.map { item ->
                        if (item.id == testId) item.copy(
                            status = ValidationStatus.RUNNING,
                            errorMessage = null,
                            evidence = null
                        ) else item
                    }
                )
            )
        }

        matrixJob = viewModelScope.launch {
            try {
                validationEngine.runSingleTest(userId, testId).collect { resultItem ->
                    if (getUserId() != userId) return@collect
                    _uiState.update { current ->
                        current.copy(
                            matrixProgress = current.matrixProgress.copy(
                                items = current.matrixProgress.items.map { item ->
                                    if (item.id == testId) resultItem else item
                                }
                            )
                        )
                    }
                }
                if (getUserId() == userId) {
                    val report = validationEngine.generateValidationReport(
                        testItems = _uiState.value.matrixProgress.items,
                        vibeProgress = _uiState.value.vibeProgress,
                        crashProgress = _uiState.value.crashProgress
                    )
                    _uiState.update { current ->
                        current.copy(
                            matrixProgress = current.matrixProgress.copy(isRunning = false),
                            summaryReport = report
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                publishMatrixFailure(userId, testId)
            }
        }
    }

    private suspend fun publishMatrixFailure(userId: String, testId: Int?) {
        if (getUserId() != userId) return
        val items = _uiState.value.matrixProgress.items.map { item ->
            val affected = if (testId == null) {
                item.status == ValidationStatus.RUNNING
            } else {
                item.id == testId
            }
            if (affected) {
                item.copy(
                    status = ValidationStatus.FAILED,
                    errorMessage = "تعذر إكمال الفحص بسبب خطأ داخلي. حاول مرة أخرى."
                )
            } else {
                item
            }
        }
        val report = validationEngine.generateValidationReport(items, null, null)
        _uiState.update { current ->
            current.copy(
                matrixProgress = current.matrixProgress.copy(
                    items = items,
                    isRunning = false,
                    currentTestName = ""
                ),
                summaryReport = report,
                errorMessage = "توقف تشغيل التحقق قبل اكتماله."
            )
        }
    }

    fun runVibeSandbox() {
        if (_uiState.value.vibeProgress.isRunning) return
        val userId = requireAuthenticatedUserId() ?: return
        vibeJob?.cancel()
        vibeJob = viewModelScope.launch {
            try {
                validationEngine.runVibeCodingSandbox(userId).collect { progress ->
                    if (getUserId() == userId) {
                        _uiState.update { it.copy(vibeProgress = progress) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (getUserId() == userId) {
                    _uiState.update {
                        it.copy(
                            vibeProgress = VibeCodingSandboxProgress(
                                blockedReason = "تعذر الوصول إلى بيئة التنفيذ المعزولة."
                            )
                        )
                    }
                }
            }
        }
    }

    fun runCrashSandbox() {
        if (_uiState.value.crashProgress.isRunning) return
        val userId = requireAuthenticatedUserId() ?: return
        crashJob?.cancel()
        crashJob = viewModelScope.launch {
            try {
                validationEngine.runCrashInvestigationSandbox(userId).collect { progress ->
                    if (getUserId() == userId) {
                        _uiState.update { it.copy(crashProgress = progress) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (getUserId() == userId) {
                    _uiState.update {
                        it.copy(
                            crashProgress = CrashInvestigationSandboxProgress(
                                blockedReason = "تعذر الوصول إلى مصدر التحقيق المعزول."
                            )
                        )
                    }
                }
            }
        }
    }

    fun exportMarkdownReport(context: Context, destination: Uri) {
        val report = _uiState.value.summaryReport ?: return
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, errorMessage = null) }
            try {
                val output = context.contentResolver.openOutputStream(destination)
                    ?: throw IllegalStateException("تعذر فتح الملف المحدد للكتابة.")
                output.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(report.markdownReport)
                }
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportSuccessMessage = "تم حفظ التقرير في الموقع الذي اخترته."
                    )
                }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(isExporting = false) }
                throw cancelled
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = "تعذر حفظ التقرير في الموقع المحدد."
                    )
                }
            }
        }
    }

    private fun cancelUserScopedJobs() {
        matrixJob?.cancel()
        vibeJob?.cancel()
        crashJob?.cancel()
        matrixJob = null
        vibeJob = null
        crashJob = null
    }

    fun dismissMessage() {
        _uiState.update { it.copy(exportSuccessMessage = null, errorMessage = null) }
    }

    protected override fun onCleared() {
        cancelUserScopedJobs()
        super.onCleared()
    }
}
