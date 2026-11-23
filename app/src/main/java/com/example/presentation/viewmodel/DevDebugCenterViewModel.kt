package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.VibeStudioRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal sealed interface AuthenticatedWorkspaceFeed {
    data object SignedOut : AuthenticatedWorkspaceFeed
    data class Loading(val userId: String) : AuthenticatedWorkspaceFeed
    data class Loaded(val userId: String, val workspaces: List<Workspace>) : AuthenticatedWorkspaceFeed
    data class Failed(val userId: String) : AuthenticatedWorkspaceFeed
}

@OptIn(ExperimentalCoroutinesApi::class)
/**
 * Switches workspace subscriptions with the authenticated account. flatMapLatest cancels
 * the previous user's subscription before exposing data for the next account.
 */
internal fun observeAuthenticatedWorkspaceFeed(
    currentUser: Flow<UserAccount?>,
    getWorkspaces: (String) -> Flow<List<Workspace>>
): Flow<AuthenticatedWorkspaceFeed> = currentUser
    .map { user -> user?.id?.takeIf(String::isNotBlank) }
    .distinctUntilChanged()
    .flatMapLatest { userId ->
        if (userId == null) {
            flowOf(AuthenticatedWorkspaceFeed.SignedOut)
        } else {
            flow {
                emit(AuthenticatedWorkspaceFeed.Loading(userId))
                emitAll(getWorkspaces(userId).map { workspaces ->
                    AuthenticatedWorkspaceFeed.Loaded(userId, workspaces)
                })
            }.catch {
                emit(AuthenticatedWorkspaceFeed.Failed(userId))
            }
        }
    }

class DevDebugCenterViewModel(
    private val vibeStudioRepository: VibeStudioRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DevDebugCenterState(isLoading = true))
    val state = _state.asStateFlow()

    private val _workspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val workspaces = _workspaces.asStateFlow()

    private var workspaceFeedJob: Job? = null
    private var selectedWorkspaceJob: Job? = null
    private var requestedWorkspaceId: String? = null

    init {
        loadWorkspaces()
    }

    private fun loadWorkspaces() {
        workspaceFeedJob?.cancel()
        workspaceFeedJob = viewModelScope.launch {
            observeAuthenticatedWorkspaceFeed(
                currentUser = authRepository.currentUser,
                getWorkspaces = vibeStudioRepository::getWorkspaces
            ).collect { feed ->
                when (feed) {
                    AuthenticatedWorkspaceFeed.SignedOut -> {
                        selectedWorkspaceJob?.cancel()
                        selectedWorkspaceJob = null
                        _workspaces.value = emptyList()
                        _state.value = DevDebugCenterState(
                            isLoading = false,
                            error = "سجّل الدخول بحساب صالح لعرض مساحات العمل."
                        )
                    }

                    is AuthenticatedWorkspaceFeed.Loading -> {
                        selectedWorkspaceJob?.cancel()
                        selectedWorkspaceJob = null
                        _workspaces.value = emptyList()
                        _state.value = DevDebugCenterState(isLoading = true)
                    }

                    is AuthenticatedWorkspaceFeed.Failed -> {
                        selectedWorkspaceJob?.cancel()
                        selectedWorkspaceJob = null
                        _workspaces.value = emptyList()
                        _state.value = DevDebugCenterState(
                            isLoading = false,
                            error = "تعذر تحميل مساحات العمل للحساب الحالي."
                        )
                    }

                    is AuthenticatedWorkspaceFeed.Loaded -> {
                        _workspaces.value = feed.workspaces
                        val requestedId = requestedWorkspaceId
                        if (requestedId != null) {
                            val requested = feed.workspaces.firstOrNull { it.id == requestedId }
                            if (requested == null) {
                                selectedWorkspaceJob?.cancel()
                                selectedWorkspaceJob = null
                                _state.value = DevDebugCenterState(
                                    isLoading = false,
                                    error = "مساحة العمل المطلوبة غير متاحة للحساب الحالي."
                                )
                            } else if (_state.value.selectedWorkspace?.id != requested.id) {
                                loadAuthorizedWorkspace(requested.id)
                            }
                        } else {
                            val selectedId = _state.value.selectedWorkspace?.id
                            val selected = feed.workspaces.firstOrNull { it.id == selectedId }
                            when {
                                selected != null && selected != _state.value.selectedWorkspace ->
                                    loadAuthorizedWorkspace(selected.id)
                                selected == null && feed.workspaces.isNotEmpty() ->
                                    loadAuthorizedWorkspace(feed.workspaces.first().id)
                                feed.workspaces.isEmpty() -> {
                                    selectedWorkspaceJob?.cancel()
                                    selectedWorkspaceJob = null
                                    _state.value = DevDebugCenterState(isLoading = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun initForWorkspace(workspaceId: String?) {
        if (workspaceId.isNullOrBlank()) return
        requestedWorkspaceId = workspaceId
        if (_workspaces.value.any { it.id == workspaceId }) {
            loadAuthorizedWorkspace(workspaceId)
        }
    }

    fun selectWorkspace(workspaceId: String) {
        if (workspaceId.isBlank()) return
        requestedWorkspaceId = workspaceId
        if (_workspaces.value.none { it.id == workspaceId }) {
            selectedWorkspaceJob?.cancel()
            selectedWorkspaceJob = null
            _state.value = DevDebugCenterState(
                isLoading = false,
                error = "مساحة العمل المطلوبة غير متاحة للحساب الحالي."
            )
            return
        }
        loadAuthorizedWorkspace(workspaceId)
    }

    private fun loadAuthorizedWorkspace(workspaceId: String) {
        // Workspace IDs are accepted only from the authenticated user's live workspace feed.
        if (_workspaces.value.none { it.id == workspaceId }) return

        selectedWorkspaceJob?.cancel()
        selectedWorkspaceJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = vibeStudioRepository.getDevDebugState(workspaceId)
            if (!isActive) return@launch
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
            result.onSuccess { (_, fix) ->
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
