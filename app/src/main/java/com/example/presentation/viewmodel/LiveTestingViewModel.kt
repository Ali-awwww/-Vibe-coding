package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.CrashRepository
import com.example.domain.repository.LiveTestingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class LiveTestingViewModel(
    private val repository: LiveTestingRepository,
    private val crashRepository: CrashRepository
) : ViewModel() {

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects = _projects.asStateFlow()

    private val _applications = MutableStateFlow<List<Application>>(emptyList())
    val applications = _applications.asStateFlow()

    private val _sessions = MutableStateFlow<List<TestSession>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private val _activeSession = MutableStateFlow<TestSession?>(null)
    val activeSession = _activeSession.asStateFlow()

    private val _events = MutableStateFlow<List<TestEvent>>(emptyList())
    val events = _events.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError = _operationError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProjectId = _selectedProjectId.asStateFlow()

    init {
        loadProjects()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            _isLoading.value = true
            crashRepository.getProjects().collect {
                _projects.value = it
                if (it.isNotEmpty() && _selectedProjectId.value == null) {
                    selectProject(it.first().id)
                }
                _isLoading.value = false
            }
        }
    }

    fun selectProject(projectId: String) {
        _selectedProjectId.value = projectId
        loadApplications(projectId)
        loadSessions(projectId)
    }

    private fun loadApplications(projectId: String) {
        viewModelScope.launch {
            crashRepository.getApplications(projectId).collect {
                _applications.value = it
            }
        }
    }

    private fun loadSessions(projectId: String) {
        viewModelScope.launch {
            repository.getTestSessions(projectId).collect {
                _sessions.value = it
            }
        }
    }

    fun startSession(application: Application, version: String, buildId: String) {
        viewModelScope.launch {
            _operationError.value = null
            val session = TestSession(
                id = UUID.randomUUID().toString(),
                projectId = application.projectId,
                applicationId = application.id,
                buildId = buildId,
                version = version,
                deviceId = "",
                deviceName = "غير متصل",
                os = "",
                status = TestSessionStatus.PREPARING
            )
            val result = repository.startTestSession(session)
            result.onSuccess {
                _activeSession.value = session
                observeSession(session.id)
                observeEvents(session.id)
            }.onFailure { error ->
                _operationError.value = error.message ?: "تعذر بدء جلسة الاختبار."
            }
        }
    }
    fun observeSession(sessionId: String) {
        viewModelScope.launch {
            repository.getTestSessionById(sessionId).collect {
                _activeSession.value = it
            }
        }
    }

    fun observeEvents(sessionId: String) {
        viewModelScope.launch {
            repository.getTestEvents(sessionId).collect {
                _events.value = it
            }
        }
    }

    fun stopSession() {
        val session = _activeSession.value ?: return
        viewModelScope.launch {
            repository.endTestSession(session.id, TestSessionStatus.COMPLETED)
        }
    }

    fun connectAgent(applicationId: String) {
        viewModelScope.launch {
            _operationError.value = null
            repository.connectToAgent(applicationId).onFailure { error ->
                _operationError.value = error.message ?: "تعذر الاتصال بالعميل."
            }
        }
    }

    fun sendCommandToAgent(sessionId: String, command: String) {
        viewModelScope.launch {
            _operationError.value = null
            repository.sendCommandToAgent(sessionId, command).onFailure { error ->
                _operationError.value = error.message ?: "تعذر إرسال الأمر."
            }
        }
    }

    fun updateSdkStatus(applicationId: String, status: SdkSetupStatus) {
        viewModelScope.launch {
            repository.updateApplicationSdkStatus(applicationId, status)
        }
    }
}
