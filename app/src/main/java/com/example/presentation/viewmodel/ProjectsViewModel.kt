package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val isAuthenticated: Boolean = false,
    val isCloudConfigured: Boolean = true,
    val projects: List<Project> = emptyList(),
    val currentUserId: String = "",
    val currentUserEmail: String = "",
    val selectedProject: Project? = null,
    val applications: List<Application> = emptyList(),
    val members: List<ProjectMember> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val userRoleInSelectedProject: Role? = null,
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModel(
    private val repository: CrashRepository,
    private val authRepository: AuthRepository,
    private val isCloudConfigured: Boolean = true
) : ViewModel() {

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    private val _refreshTrigger = MutableStateFlow(0)
    private val _isMutating = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProjectsUiState> = authRepository.currentUser
        .map { user -> user?.takeIf { it.id.isNotBlank() } }
        .distinctUntilChangedBy { it?.id }
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(
                    ProjectsUiState(
                        isAuthenticated = false,
                        isCloudConfigured = isCloudConfigured,
                        isLoading = false
                    )
                )
            } else {
                val userId = user.id
                val userEmail = user.email
                _refreshTrigger.flatMapLatest {
                    val projectsFlow = projectsForAuthenticatedUser(userId, repository::getProjectsForUser)

                    combine(
                        projectsFlow,
                        _selectedProjectId,
                        _isMutating,
                        _message,
                        _error
                    ) { projects, selectedId, mutating, msg, err ->
                        val selected = projects.find { it.id == selectedId } ?: projects.firstOrNull()
                        ProjectsUiState(
                            isAuthenticated = true,
                            isCloudConfigured = isCloudConfigured,
                            projects = projects,
                            currentUserId = userId,
                            currentUserEmail = userEmail,
                            selectedProject = selected,
                            isMutating = mutating,
                            message = msg,
                            error = err,
                            isLoading = false
                        )
                    }.flatMapLatest { baseState ->
                        val selectedId = baseState.selectedProject?.id
                        if (selectedId != null) {
                            combine(
                                repository.getApplications(selectedId),
                                repository.getProjectMembers(selectedId),
                                repository.getAuditLogsForProject(selectedId),
                                _isMutating,
                                _message,
                                _error
                            ) { values ->
                                @Suppress("UNCHECKED_CAST")
                                val apps = values[0] as List<Application>
                                @Suppress("UNCHECKED_CAST")
                                val members = values[1] as List<ProjectMember>
                                @Suppress("UNCHECKED_CAST")
                                val logs = values[2] as List<AuditLog>
                                val mutating = values[3] as Boolean
                                val msg = values[4] as String?
                                val err = values[5] as String?
                                val role = if (baseState.selectedProject.ownerId == baseState.currentUserId) {
                                    Role.OWNER
                                } else {
                                    members.find { it.userId == baseState.currentUserId }?.role
                                }
                                baseState.copy(
                                    applications = apps,
                                    members = members,
                                    auditLogs = logs,
                                    userRoleInSelectedProject = role,
                                    isMutating = mutating,
                                    message = msg,
                                    error = err
                                )
                            }
                        } else {
                            flowOf(baseState)
                        }
                    }
                        .onStart {
                            emit(
                                ProjectsUiState(
                                    isAuthenticated = true,
                                    isCloudConfigured = isCloudConfigured,
                                    currentUserId = userId,
                                    currentUserEmail = userEmail,
                                    isLoading = true
                                )
                            )
                        }
                        .catch { failure ->
                            if (failure is CancellationException) throw failure
                            emit(
                                ProjectsUiState(
                                    isAuthenticated = true,
                                    isCloudConfigured = isCloudConfigured,
                                    currentUserId = userId,
                                    currentUserEmail = userEmail,
                                    isLoading = false,
                                    error = safeError(failure, "تعذر تحميل المشاريع والتطبيقات.")
                                )
                            )
                        }
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ProjectsUiState(isCloudConfigured = isCloudConfigured, isLoading = true)
        )

    fun selectProject(projectId: String) {
        _selectedProjectId.value = projectId.takeIf { it.isNotBlank() }
    }

    fun refresh() {
        _error.value = null
        _refreshTrigger.update { it + 1 }
    }

    fun addProject(name: String, description: String, retentionDays: Int = 90, piiScrubbing: Boolean = true) {
        val user = requireAuthenticatedUser() ?: return
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            _error.value = "أدخل اسم المشروع أولاً."
            return
        }
        runMutation(
            successMessage = "تم إنشاء المشروع بنجاح",
            fallbackError = "تعذر إنشاء المشروع."
        ) {
            val newProject = Project(
                name = normalizedName,
                description = description.trim(),
                ownerId = user.id,
                dataRetentionDays = retentionDays,
                piiScrubbingEnabled = piiScrubbing
            )
            repository.addProject(newProject)
        }
    }

    fun deleteProject(projectId: String) {
        val user = requireAuthenticatedUser() ?: return
        runMutation(
            successMessage = "تم حذف المشروع وجميع بياناته بنجاح",
            fallbackError = "فشل حذف المشروع"
        ) {
            val result = repository.deleteProject(projectId, user.id)
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: IllegalStateException("فشل حذف المشروع")
            }
            if (_selectedProjectId.value == projectId) {
                _selectedProjectId.value = null
            }
        }
    }

    fun addMember(projectId: String, email: String, role: Role) {
        val user = requireAuthenticatedUser() ?: return
        val normalizedEmail = email.trim()
        if (normalizedEmail.isEmpty() || !normalizedEmail.contains("@")) {
            _error.value = "أدخل بريدًا إلكترونيًا صالحًا للعضو."
            return
        }
        runMutation(
            successMessage = "تمت إضافة العضو بنجاح",
            fallbackError = "فشل إضافة العضو"
        ) {
            val result = repository.addProjectMember(projectId, normalizedEmail, role, user.id)
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: IllegalStateException("فشل إضافة العضو")
            }
        }
    }

    fun updateMemberRole(projectId: String, memberUserId: String, newRole: Role) {
        val user = requireAuthenticatedUser() ?: return
        runMutation(
            successMessage = "تم تحديث صلاحية العضو بنجاح",
            fallbackError = "فشل تحديث الصلاحية"
        ) {
            val result = repository.updateMemberRole(projectId, memberUserId, newRole, user.id)
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: IllegalStateException("فشل تحديث الصلاحية")
            }
        }
    }

    fun removeMember(projectId: String, memberUserId: String) {
        val user = requireAuthenticatedUser() ?: return
        runMutation(
            successMessage = "تمت إزالة العضو من المشروع",
            fallbackError = "فشل إزالة العضو"
        ) {
            val result = repository.removeProjectMember(projectId, memberUserId, user.id)
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: IllegalStateException("فشل إزالة العضو")
            }
        }
    }

    fun updateSecurityPolicy(projectId: String, retentionDays: Int, piiScrubbing: Boolean) {
        val user = requireAuthenticatedUser() ?: return
        runMutation(
            successMessage = "تم تحديث سياسة الأمان والاحتفاظ بالبيانات",
            fallbackError = "فشل تحديث السياسة"
        ) {
            val result = repository.updateProjectSecurityPolicy(projectId, retentionDays, piiScrubbing, user.id)
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: IllegalStateException("فشل تحديث السياسة")
            }
        }
    }

    fun purgeOldCrashes(projectId: String) {
        val user = requireAuthenticatedUser() ?: return
        viewModelScope.launch {
            _isMutating.value = true
            try {
                val result = repository.purgeOldCrashes(projectId, user.id)
                if (result.isSuccess) {
                    val count = result.getOrDefault(0)
                    _error.value = null
                    _message.value = "تم حذف $count تقرير عطل أقدم من فترة الاحتفاظ المحددة"
                    _refreshTrigger.update { it + 1 }
                } else {
                    _message.value = null
                    _error.value = safeError(result.exceptionOrNull(), "فشل تطهير البيانات")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _message.value = null
                _error.value = safeError(failure, "فشل تطهير البيانات")
            } finally {
                _isMutating.value = false
            }
        }
    }

    fun addApplication(projectId: String, name: String, platform: String, packageName: String) {
        val user = requireAuthenticatedUser() ?: return
        val normalizedName = name.trim()
        val normalizedPackage = packageName.trim()
        if (normalizedName.isEmpty() || normalizedPackage.isEmpty()) {
            _error.value = "أدخل اسم التطبيق ومعرّف الحزمة أولاً."
            return
        }
        runMutation(
            successMessage = "تم ربط التطبيق '$normalizedName' بنجاح",
            fallbackError = "تعذر ربط التطبيق بالمشروع."
        ) {
            repository.addApplication(
                Application(
                    id = java.util.UUID.randomUUID().toString(),
                    projectId = projectId,
                    name = normalizedName,
                    platform = platform.trim(),
                    packageName = normalizedPackage,
                    ownerId = user.id,
                    sdkSetupStatus = SdkSetupStatus.NOT_CONFIGURED,
                    agentConnectionStatus = AgentConnectionStatus.OFFLINE
                )
            )
        }
    }

    fun deleteApplication(applicationId: String) {
        requireAuthenticatedUser() ?: return
        runMutation(
            successMessage = "تم حذف التطبيق بنجاح",
            fallbackError = "تعذر حذف التطبيق."
        ) {
            repository.deleteApplication(applicationId)
        }
    }

    fun clearNotifications() {
        _message.value = null
        _error.value = null
    }

    private fun requireAuthenticatedUser(): UserAccount? {
        val user = authRepository.getCurrentUserSync()?.takeIf { it.id.isNotBlank() }
        if (user == null) {
            _message.value = null
            _error.value = AUTH_REQUIRED_ERROR
        }
        return user
    }

    private fun runMutation(
        successMessage: String,
        fallbackError: String,
        action: suspend () -> Unit
    ) {
        viewModelScope.launch {
            _isMutating.value = true
            try {
                action()
                _error.value = null
                _message.value = successMessage
                _refreshTrigger.update { it + 1 }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _message.value = null
                _error.value = safeError(failure, fallbackError)
            } finally {
                _isMutating.value = false
            }
        }
    }

    private fun safeError(failure: Throwable?, fallback: String): String {
        val raw = failure?.message?.trim().orEmpty()
        return when {
            raw.contains("expired", ignoreCase = true) ->
                "انتهت صلاحية الجلسة السحابية؛ يرجى تسجيل الدخول مجددًا."
            raw.isNotEmpty() -> raw
            else -> fallback
        }
    }

    private companion object {
        const val AUTH_REQUIRED_ERROR = "يرجى تسجيل الدخول لإدارة المشاريع والتطبيقات."
    }
}
