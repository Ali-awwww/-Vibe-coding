package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CrashReport
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.CrashRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val currentUser: UserAccount? = null,
    val recentCrashes: List<CrashReport> = emptyList(),
    val notifications: List<com.example.domain.model.SystemNotification> = emptyList(),
    val unreadNotificationCount: Int = 0,
    val projectCount: Int = 0,
    val applicationCount: Int = 0,
    val issueCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val repository: CrashRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = authRepository.currentUser.flatMapLatest { user ->
        val userId = user?.id ?: ""
        val projectsFlow = if (userId.isNotBlank()) repository.getProjectsForUser(userId) else repository.getProjects()
        val notifsFlow = combine(repository.getNotifications(), repository.getUnreadNotificationCount()) { notifs, unread ->
            Pair(notifs, unread)
        }

        combine(
            projectsFlow,
            repository.getAllApplications(),
            repository.getAllCrashes(),
            repository.getAllIssues(),
            notifsFlow
        ) { userProjects, allApps, allCrashes, allIssues, notifData ->
            val userProjectIds = userProjects.map { it.id }.toSet()
            val userApps = allApps.filter { it.projectId in userProjectIds }
            val userCrashes = allCrashes.filter { it.projectId in userProjectIds }
            val userIssues = allIssues.filter { it.projectId in userProjectIds }

            DashboardUiState(
                currentUser = user,
                recentCrashes = userCrashes.take(10),
                notifications = notifData.first,
                unreadNotificationCount = notifData.second,
                projectCount = userProjects.size,
                applicationCount = userApps.size,
                issueCount = userIssues.size,
                isLoading = false
            )
        }
    }
    .onStart { emit(DashboardUiState(isLoading = true)) }
    .catch { e -> emit(DashboardUiState(error = e.message)) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun markNotificationAsRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationAsRead(notificationId)
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearNotifications()
        }
    }
}

