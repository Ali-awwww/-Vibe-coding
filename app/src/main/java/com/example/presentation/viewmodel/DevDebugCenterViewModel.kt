package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.VibeStudioRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DevDebugCenterViewModel(
    private val vibeStudioRepository: VibeStudioRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DevDebugCenterState(isLoading = true))
    val state = _state.asStateFlow()

    private val _workspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val workspaces = _workspaces.asStateFlow()

    init {
        loadWorkspaces()
    }

    private fun loadWorkspaces() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                val userId = user?.id ?: "usr_dev_master"
                vibeStudioRepository.getWorkspaces(userId).collect { wsList ->
                    _workspaces.value = wsList
                    val currentSelected = _state.value.selectedWorkspace
                    if (currentSelected == null && wsList.isNotEmpty()) {
                        selectWorkspace(wsList.first().id)
                    } else if (wsList.isEmpty()) {
                        _state.update { it.copy(isLoading = false) }
                    }
                }
            }
        }
    }

    fun initForWorkspace(workspaceId: String?) {
        if (!workspaceId.isNullOrBlank()) {
            selectWorkspace(workspaceId)
        }
    }

    fun selectWorkspace(workspaceId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = vibeStudioRepository.getDevDebugState(workspaceId)
            result.onSuccess { debugState ->
                _state.value = debugState.copy(isLoading = false)
            }.onFailure { err ->
                _state.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun setupMonitoring() {
        val workspace = _state.value.selectedWorkspace ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = vibeStudioRepository.setupWorkspaceMonitoring(workspace.id)
            result.onSuccess { profile ->
                refreshCurrentWorkspace(feedback = "تم إعداد ملف المراقبة بنجاح وربط SDK للمشروع: ${profile.targetPlatform}")
            }.onFailure { err ->
                _state.update { it.copy(isLoading = false, error = "فشل إعداد المراقبة: ${err.message}") }
            }
        }
    }

    fun startTestRun(customBuildId: String? = null) {
        val workspace = _state.value.selectedWorkspace ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = vibeStudioRepository.startTrackedTestSession(workspace.id, customBuildId)
            result.onSuccess { session ->
                refreshCurrentWorkspace(feedback = "بدأت جلسة الاختبار التتبعية (${session.id.take(8)}) للبناء ${session.buildId}")
            }.onFailure { err ->
                _state.update { it.copy(isLoading = false, error = "فشل بدء جلسة الاختبار: ${err.message}") }
            }
        }
    }

    fun simulateOrReportCrash(
        exceptionType: String,
        message: String,
        stackTrace: String
    ) {
        val workspace = _state.value.selectedWorkspace ?: return
        val activeSessionId = _state.value.activeRunContext?.testSessionId
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = vibeStudioRepository.ingestSimulatedOrRuntimeCrash(
                workspaceId = workspace.id,
                testSessionId = activeSessionId,
                exceptionType = exceptionType,
                message = message,
                stackTrace = stackTrace
            )
            result.onSuccess { crash ->
                refreshCurrentWorkspace(feedback = "تم التقاط عطل حرج بنجاح: ${crash.exceptionType} (${crash.id.take(8)})")
            }.onFailure { err ->
                _state.update { it.copy(isLoading = false, error = "فشل تسجيل العطل: ${err.message}") }
            }
        }
    }

    fun diagnoseCrash(crashId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = vibeStudioRepository.diagnoseCrashAndGenerateFix(crashId)
            result.onSuccess { (diag, fix) ->
                refreshCurrentWorkspace(feedback = "تم تحليل العطل جذرياً وتوليد اقتراح إصلاح في ${fix.filePath}")
            }.onFailure { err ->
                _state.update { it.copy(isLoading = false, error = "فشل التشخيص: ${err.message}") }
            }
        }
    }

    fun selectIncident(incident: ClosedLoopIncident) {
        _state.update { it.copy(activeIncident = incident) }
    }

    fun applyFix(fixId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isFixing = true, error = null) }
            val result = vibeStudioRepository.applyClosedLoopFix(fixId)
            result.onSuccess { commit ->
                refreshCurrentWorkspace(feedback = "تم تطبيق الإصلاح في الملفات وإنشاء الالتزام ${commit.id.take(7)} بنجاح")
            }.onFailure { err ->
                _state.update { it.copy(isFixing = false, error = "فشل تطبيق الإصلاح: ${err.message}") }
            }
        }
    }

    fun retestAndVerify(crashId: String, fixId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isVerifying = true, error = null) }
            val result = vibeStudioRepository.retestAndVerifyClosedLoop(crashId, fixId)
            result.onSuccess { outcome ->
                val fb = if (outcome.isFixed) {
                    "تم التحقق بنجاح! اجتازت الاختبارات في البناء الجديد (${outcome.newBuildId}) وأُغلِق العطل"
                } else {
                    "فشل التحقق: الاختبارات لم تجتز أو أعيد إنتاج العطل"
                }
                refreshCurrentWorkspace(feedback = fb)
            }.onFailure { err ->
                _state.update { it.copy(isVerifying = false, error = "فشل التحقق: ${err.message}") }
            }
        }
    }

    fun refresh() {
        val currentWorkspaceId = _state.value.selectedWorkspace?.id ?: return
        selectWorkspace(currentWorkspaceId)
    }

    fun clearFeedbackMessage() {
        _state.update { it.copy(actionFeedbackMessage = null, error = null) }
    }

    private suspend fun refreshCurrentWorkspace(feedback: String? = null) {
        val currentWorkspaceId = _state.value.selectedWorkspace?.id ?: return
        val result = vibeStudioRepository.getDevDebugState(currentWorkspaceId)
        result.onSuccess { debugState ->
            _state.value = debugState.copy(
                isLoading = false,
                isFixing = false,
                isVerifying = false,
                actionFeedbackMessage = feedback
            )
        }.onFailure { err ->
            _state.update {
                it.copy(
                    isLoading = false,
                    isFixing = false,
                    isVerifying = false,
                    actionFeedbackMessage = feedback,
                    error = err.message
                )
            }
        }
    }
}
