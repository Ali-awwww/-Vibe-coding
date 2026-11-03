package com.example.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.quality.ProductQualityGateEngine
import com.example.domain.model.quality.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class QualityCenterTab(val labelEn: String, val labelAr: String) {
    OVERVIEW_GATE("Release Gate", "بوابة الإصدار والجاهزية"),
    SCREENS_MATRIX("Screens Matrix", "مصفوفة الشاشات والشروط"),
    BLOCKERS_INTEGRATIONS("Blockers & APIs", "الموانع والتكاملات"),
    AUDIT_REPORT("Acceptance Report", "تقرير الاعتماد النهائي")
}

data class QualityCenterUiState(
    val assessment: QualityGateAssessment? = null,
    val isScanning: Boolean = false,
    val activeTab: QualityCenterTab = QualityCenterTab.OVERVIEW_GATE,
    val selectedModuleFilter: ScreenModule? = null,
    val selectedStatusFilter: ScreenCompletionStatus? = null,
    val searchQuery: String = "",
    val selectedScreenForInspection: ScreenAuditRecord? = null,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

class ProductQualityCenterViewModel(
    private val engine: ProductQualityGateEngine = ProductQualityGateEngine()
) : ViewModel() {

    private val _uiState = MutableStateFlow(QualityCenterUiState())
    val uiState: StateFlow<QualityCenterUiState> = _uiState.asStateFlow()

    init {
        runAuditScan()
    }

    fun selectTab(tab: QualityCenterTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setModuleFilter(module: ScreenModule?) {
        _uiState.update { it.copy(selectedModuleFilter = module) }
    }

    fun setStatusFilter(status: ScreenCompletionStatus?) {
        _uiState.update { it.copy(selectedStatusFilter = status) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun inspectScreen(screen: ScreenAuditRecord?) {
        _uiState.update { it.copy(selectedScreenForInspection = screen) }
    }

    fun dismissFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null, errorMessage = null) }
    }

    fun runAuditScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            try {
                val assessment = engine.performFullAssessment()
                _uiState.update {
                    it.copy(
                        assessment = assessment,
                        isScanning = false,
                        feedbackMessage = "تم تحديث فحص معايير الجودة وبوابة الاعتماد بنجاح"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        errorMessage = "فشل تشغيل فحص الجودة: ${e.message}"
                    )
                }
            }
        }
    }

    fun exportAcceptanceReport(context: Context) {
        val currentAssessment = _uiState.value.assessment ?: return
        viewModelScope.launch {
            try {
                val reportsDir = File(context.cacheDir, "quality_reports").apply {
                    if (!exists()) mkdirs()
                }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val reportFile = File(reportsDir, "acceptance_report_$timestamp.md")

                reportFile.writeText(currentAssessment.auditSummaryMarkdown)

                _uiState.update {
                    it.copy(
                        feedbackMessage = "تم تصدير تقرير الاعتماد بنجاح: ${reportFile.name}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = "فشل تصدير التقرير: ${e.message}"
                    )
                }
            }
        }
    }
}
