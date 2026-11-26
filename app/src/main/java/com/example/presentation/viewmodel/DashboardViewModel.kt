package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CrashReport
import com.example.domain.model.Project
import com.example.domain.model.SystemNotification
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isAuthenticated: Boolean = false,
    val currentUser: UserAccount? = null,
    val projects: List<Project> = emptyList(),
    val selectedProjectId: String? = null,
    val recentCrashes: List<CrashReport> = emptyList(),
    val notifications: List<SystemNotification> = emptyList(),
    val unreadNotificationCount: Int = 0,
    val projectCount: Int = 0,
    val applicationCount: Int = 0,
    val issueCount: Int = 0,
    val isCloudConfigured: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val repository: CrashRepository,
    private val authRepository: AuthRepository,
    private val isCloudConfigured: Boolean = true
) : ViewModel() {

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    private val _refreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<DashboardUiState> = authRepository.currentUser
        .map { user -> user?.takeIf { it.id.isNotBlank() } }
        .distinctUntilChangedBy { it?.id }
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(
                    DashboardUiState(
                        isAuthenticated = false,
                        currentUser = null,
                        isCloudConfigured = isCloudConfigured,
                        isLoading = false
                    )
                )
            } else {
                _refreshTrigger.flatMapLatest {
                    val projectsFlow = projectsForAuthenticatedUser(user.id, repository::getProjectsForUser)
                    val notifsFlow = combine(
                        repository.getNotifications(),
                        repository.getUnreadNotificationCount()
                    ) { notifs, unread ->
                        notifs to unread
                    }

                    combine(
                        projectsFlow,
                        repository.getAllApplications(),
                        repository.getAllCrashes(),
                        repository.getAllIssues(),
                        notifsFlow,
                        _selectedProjectId
                    ) { values ->
                        @Suppress("UNCHECKED_CAST")
                        val userProjects = values[0] as List<Project>
                        @Suppress("UNCHECKED_CAST")
                        val allApps = values[1] as List<com.example.domain.model.Application>
                        @Suppress("UNCHECKED_CAST")
                        val allCrashes = values[2] as List<CrashReport>
                        @Suppress("UNCHECKED_CAST")
                        val allIssues = values[3] as List<com.example.domain.model.Issue>
                        @Suppress("UNCHECKED_CAST")
                        val notifData = values[4] as Pair<List<SystemNotification>, Int>
                        val requestedProjectId = values[5] as String?

                        val activeProjectId = requestedProjectId?.takeIf { id ->
                            userProjects.any { it.id == id }
                        }
                        val scopedProjectIds = if (activeProjectId != null) {
                            setOf(activeProjectId)
                        } else {
                            userProjects.map { it.id }.toSet()
                        }

                        val userApps = allApps.filter { it.projectId in scopedProjectIds }
                        val userCrashes = allCrashes.filter { it.projectId in scopedProjectIds }
                        val userIssues = allIssues.filter { it.projectId in scopedProjectIds }

                        DashboardUiState(
                            isAuthenticated = true,
                            currentUser = user,
                            projects = userProjects,
                            selectedProjectId = activeProjectId,
                            recentCrashes = userCrashes.take(10),
                            notifications = notifData.first,
                            unreadNotificationCount = notifData.second,
                            projectCount = userProjects.size,
                            applicationCount = userApps.size,
                            issueCount = userIssues.size,
                            isCloudConfigured = isCloudConfigured,
                            isLoading = false,
                            error = null
                        )
                    }
                        .onStart {
                            emit(
                                DashboardUiState(
                                    isAuthenticated = true,
                                    currentUser = user,
                                    isCloudConfigured = isCloudConfigured,
                                    isLoading = true
                                )
                            )
                        }
                        .catch { failure ->
                            if (failure is CancellationException) throw failure
                            emit(
                                DashboardUiState(
                                    isAuthenticated = true,
                                    currentUser = user,
                                    isCloudConfigured = isCloudConfigured,
                                    isLoading = false,
                                    error = safeLoadError(failure)
                                )
                            )
                        }
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DashboardUiState(isCloudConfigured = isCloudConfigured, isLoading = true)
        )

    fun selectProject(projectId: String?) {
        _selectedProjectId.value = projectId?.takeIf { it.isNotBlank() }
    }

    fun refresh() {
        _refreshTrigger.update { it + 1 }
    }

    fun markNotificationAsRead(notificationId: String) {
        viewModelScope.launch {
            runCatching { repository.markNotificationAsRead(notificationId) }
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            runCatching { repository.markAllNotificationsAsRead() }
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            runCatching { repository.clearNotifications() }
        }
    }

    private fun safeLoadError(failure: Throwable): String {
        val raw = failure.message.orEmpty()
        return when {
            raw.contains("expired", ignoreCase = true) ->
                "انتهت صلاحية الجلسة السحابية؛ سجّل الدخول مجددًا لتحديث المشاريع."
            raw.contains("connect", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) ->
                "تعذر الاتصال بالخادم السحابي. تحقق من الشبكة ثم أعد المحاولة."
            else -> "تعذر تحميل بيانات لوحة التحكم. تحقق من الاتصال ثم أعد المحاولة."
        }
    }
}
