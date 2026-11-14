package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val projects: List<Project> = emptyList(),
    val currentUserId: String = "",
    val currentUserEmail: String = "",
    val selectedProject: Project? = null,
    val applications: List<Application> = emptyList(),
    val members: List<ProjectMember> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val userRoleInSelectedProject: Role? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModel(
    private val repository: CrashRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    private val _message = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProjectsUiState> = authRepository.currentUser.flatMapLatest { user ->
        val userId = user?.id ?: ""
        val userEmail = user?.email ?: ""

        val projectsFlow = projectsForAuthenticatedUser(user?.id, repository::getProjectsForUser)

        combine(
            projectsFlow,
            _selectedProjectId,
            _message,
            _error
        ) { projects, selectedId, msg, err ->
            val selected = projects.find { it.id == selectedId } ?: projects.firstOrNull()
            ProjectsUiState(
                projects = projects,
                currentUserId = userId,
                currentUserEmail = userEmail,
                selectedProject = selected,
                message = msg,
                error = err,
                isLoading = false
            )
        }
    }.flatMapLatest { state ->
        val selectedId = state.selectedProject?.id
        if (selectedId != null) {
            combine(
                repository.getApplications(selectedId),
                repository.getProjectMembers(selectedId),
                repository.getAuditLogsForProject(selectedId)
            ) { apps, members, logs ->
                val role = if (state.selectedProject.ownerId == state.currentUserId) {
                    Role.OWNER
                } else {
                    members.find { it.userId == state.currentUserId }?.role
                }
                state.copy(
                    applications = apps,
                    members = members,
                    auditLogs = logs,
                    userRoleInSelectedProject = role
                )
            }
        } else {
            flowOf(state)
        }
    }.catch { e ->
        emit(ProjectsUiState(error = e.message))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectsUiState(isLoading = true))

    fun selectProject(projectId: String) {
        _selectedProjectId.value = projectId
    }

    fun addProject(name: String, description: String, retentionDays: Int = 90, piiScrubbing: Boolean = true) {
        val user = authRepository.getCurrentUserSync()
        viewModelScope.launch {
            repository.addProject(
                Project(
                    name = name,
                    description = description,
                    ownerId = user?.id ?: "",
                    dataRetentionDays = retentionDays,
                    piiScrubbingEnabled = piiScrubbing
                )
            )
            _message.value = "تم إنشاء المشروع بنجاح"
        }
    }

    fun deleteProject(projectId: String) {
        val user = authRepository.getCurrentUserSync() ?: return
        viewModelScope.launch {
            val result = repository.deleteProject(projectId, user.id)
            if (result.isSuccess) {
                _message.value = "تم حذف المشروع وجميع بياناته بنجاح"
                _selectedProjectId.value = null
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "فشل حذف المشروع"
            }
        }
    }

    fun addMember(projectId: String, email: String, role: Role) {
        val user = authRepository.getCurrentUserSync() ?: return
        viewModelScope.launch {
            val result = repository.addProjectMember(projectId, email, role, user.id)
            if (result.isSuccess) {
                _message.value = "تمت إضافة العضو بنجاح"
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "فشل إضافة العضو"
            }
        }
    }

    fun updateMemberRole(projectId: String, memberUserId: String, newRole: Role) {
        val user = authRepository.getCurrentUserSync() ?: return
        viewModelScope.launch {
            val result = repository.updateMemberRole(projectId, memberUserId, newRole, user.id)
            if (result.isSuccess) {
                _message.value = "تم تحديث صلاحية العضو بنجاح"
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "فشل تحديث الصلاحية"
            }
        }
    }

    fun removeMember(projectId: String, memberUserId: String) {
        val user = authRepository.getCurrentUserSync() ?: return
        viewModelScope.launch {
            val result = repository.removeProjectMember(projectId, memberUserId, user.id)
            if (result.isSuccess) {
                _message.value = "تمت إزالة العضو من المشروع"
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "فشل إزالة العضو"
            }
        }
    }

    fun updateSecurityPolicy(projectId: String, retentionDays: Int, piiScrubbing: Boolean) {
        val user = authRepository.getCurrentUserSync() ?: return
        viewModelScope.launch {
            val result = repository.updateProjectSecurityPolicy(projectId, retentionDays, piiScrubbing, user.id)
            if (result.isSuccess) {
                _message.value = "تم تحديث سياسة الأمان والاحتفاظ بالبيانات"
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "فشل تحديث السياسة"
            }
        }
    }

    fun purgeOldCrashes(projectId: String) {
        val user = authRepository.getCurrentUserSync() ?: return
        viewModelScope.launch {
            val result = repository.purgeOldCrashes(projectId, user.id)
            if (result.isSuccess) {
                val count = result.getOrDefault(0)
                _message.value = "تم حذف $count تقرير عطل أقدم من فترة الاحتفاظ المحددة"
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "فشل تطهير البيانات"
            }
        }
    }

    fun addApplication(projectId: String, name: String, platform: String, packageName: String) {
        val user = authRepository.getCurrentUserSync() ?: return
        viewModelScope.launch {
            repository.addApplication(
                Application(
                    id = java.util.UUID.randomUUID().toString(),
                    projectId = projectId,
                    name = name,
                    platform = platform,
                    packageName = packageName,
                    ownerId = user.id,
                    sdkSetupStatus = SdkSetupStatus.NOT_CONFIGURED,
                    agentConnectionStatus = AgentConnectionStatus.OFFLINE
                )
            )
            _message.value = "تم ربط التطبيق '$name' بنجاح"
        }
    }

    fun deleteApplication(applicationId: String) {
        viewModelScope.launch {
            repository.deleteApplication(applicationId)
            _message.value = "تم حذف التطبيق بنجاح"
        }
    }

    fun clearNotifications() {
        _message.value = null
        _error.value = null
    }
}
