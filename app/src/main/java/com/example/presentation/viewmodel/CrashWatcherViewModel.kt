package com.example.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CrashReport
import com.example.service.CrashWatcherService
import com.example.service.CrashWatcherState
import com.example.util.shizuku.ShizukuManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.Locale

data class MonitoredAppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val isSelected: Boolean = false
)

enum class AppFilter { ALL, USER_ONLY, SYSTEM_ONLY }

data class CrashWatcherUiState(
    val isShizukuInstalled: Boolean = false,
    val isShizukuRunning: Boolean = false,
    val isShizukuAuthorized: Boolean = false,
    val installedApps: List<MonitoredAppItem> = emptyList(),
    val filteredApps: List<MonitoredAppItem> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val searchQuery: String = "",
    val activeFilter: AppFilter = AppFilter.USER_ONLY,
    val isMonitoring: Boolean = false,
    val monitoringStartTime: Long? = null,
    val elapsedDurationFormatted: String = "00:00",
    val capturedCrashesCount: Int = 0,
    val lastCapturedCrash: CrashReport? = null,
    val isLoadingApps: Boolean = false,
    val appsLoadError: Boolean = false,
    val userMessage: String? = null
)

internal fun CrashWatcherUiState.asAppsLoadFailure(): CrashWatcherUiState =
    copy(isLoadingApps = false, appsLoadError = true)

class CrashWatcherViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CrashWatcherUiState())
    val uiState = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        _uiState.update { it.copy(isShizukuAuthorized = granted) }
    }

    init {
        ShizukuManager.addPermissionListener(permissionListener)
        observeWatcherServiceState()
    }

    override fun onCleared() {
        super.onCleared()
        ShizukuManager.removePermissionListener(permissionListener)
        timerJob?.cancel()
    }

    private fun observeWatcherServiceState() {
        viewModelScope.launch {
            CrashWatcherState.isMonitoring.collect { active ->
                _uiState.update { it.copy(isMonitoring = active) }
                if (active) {
                    startTimer()
                } else {
                    timerJob?.cancel()
                    _uiState.update { it.copy(elapsedDurationFormatted = "00:00") }
                }
            }
        }
        viewModelScope.launch {
            CrashWatcherState.startTime.collect { time ->
                _uiState.update { it.copy(monitoringStartTime = time) }
            }
        }
        viewModelScope.launch {
            CrashWatcherState.capturedCount.collect { count ->
                _uiState.update { it.copy(capturedCrashesCount = count) }
            }
        }
        viewModelScope.launch {
            CrashWatcherState.lastCapturedCrash.collect { crash ->
                _uiState.update { it.copy(lastCapturedCrash = crash) }
            }
        }
        viewModelScope.launch {
            CrashWatcherState.errorMessage.collect { message ->
                if (!message.isNullOrBlank()) {
                    _uiState.update { it.copy(userMessage = message) }
                }
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val start = _uiState.value.monitoringStartTime ?: System.currentTimeMillis()
                val elapsedSec = maxOf(0L, (System.currentTimeMillis() - start) / 1000L)
                val mins = elapsedSec / 60L
                val secs = elapsedSec % 60L
                val formatted = String.format(Locale.US, "%02d:%02d", mins, secs)
                _uiState.update { it.copy(elapsedDurationFormatted = formatted) }
                delay(1000L)
            }
        }
    }

    fun refreshShizukuStatus(context: Context) {
        val installed = ShizukuManager.isShizukuInstalled(context)
        val running = ShizukuManager.isShizukuRunning()
        val authorized = ShizukuManager.checkPermission()
        _uiState.update {
            it.copy(
                isShizukuInstalled = installed,
                isShizukuRunning = running,
                isShizukuAuthorized = authorized
            )
        }
    }

    fun requestShizukuPermission() {
        ShizukuManager.requestPermission(9988)
    }

    fun loadInstalledApps(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true, appsLoadError = false) }
            try {
                refreshShizukuStatus(context)

                val apps = withContext(Dispatchers.IO) {
                    val pm = context.packageManager
                    val selfPackage = context.packageName

                    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }
                    val resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
                    val launcherPackages = resolveInfos.mapTo(HashSet()) { it.activityInfo.packageName }

                    // Do not turn package-manager failures into a false "no apps found" state.
                    val allPackages = pm.getInstalledPackages(0)

                    allPackages.mapNotNull { packageInfo ->
                        val pkgName = packageInfo.packageName
                        if (pkgName == selfPackage) return@mapNotNull null

                        val isLauncher = launcherPackages.contains(pkgName)
                        val appInfo = packageInfo.applicationInfo
                        val isSystem = (appInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM)) != 0

                        if (!isLauncher && isSystem) return@mapNotNull null

                        val appName = appInfo?.loadLabel(pm)?.toString() ?: pkgName
                        val versionName = packageInfo.versionName ?: "1.0"

                        MonitoredAppItem(
                            packageName = pkgName,
                            appName = appName,
                            versionName = versionName,
                            isSystemApp = isSystem,
                            isSelected = _uiState.value.selectedPackages.contains(pkgName)
                        )
                    }.sortedBy { it.appName.lowercase(Locale.ROOT) }
                }

                _uiState.update { current ->
                    val filtered = applyFilter(apps, current.searchQuery, current.activeFilter)
                    current.copy(
                        installedApps = apps,
                        filteredApps = filtered,
                        isLoadingApps = false,
                        appsLoadError = false
                    )
                }
            } catch (error: CancellationException) {
                _uiState.update { it.copy(isLoadingApps = false) }
                throw error
            } catch (error: Exception) {
                android.util.Log.w("CrashWatcherViewModel", "Failed to load installed apps", error)
                _uiState.update { it.asAppsLoadFailure() }
            }
        }
    }

    fun toggleAppSelection(packageName: String) {
        _uiState.update { current ->
            val newSelected = current.selectedPackages.toMutableSet()
            if (newSelected.contains(packageName)) {
                newSelected.remove(packageName)
            } else {
                newSelected.add(packageName)
            }
            val updatedApps = current.installedApps.map {
                it.copy(isSelected = newSelected.contains(it.packageName))
            }
            val updatedFiltered = applyFilter(updatedApps, current.searchQuery, current.activeFilter)
            current.copy(
                selectedPackages = newSelected,
                installedApps = updatedApps,
                filteredApps = updatedFiltered
            )
        }
    }

    fun selectAll(select: Boolean) {
        _uiState.update { current ->
            val newSelected = if (select) {
                current.filteredApps.mapTo(HashSet()) { it.packageName }
            } else {
                emptySet()
            }
            val updatedApps = current.installedApps.map {
                it.copy(isSelected = newSelected.contains(it.packageName))
            }
            val updatedFiltered = applyFilter(updatedApps, current.searchQuery, current.activeFilter)
            current.copy(
                selectedPackages = newSelected,
                installedApps = updatedApps,
                filteredApps = updatedFiltered
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            val filtered = applyFilter(current.installedApps, query, current.activeFilter)
            current.copy(searchQuery = query, filteredApps = filtered)
        }
    }

    fun setFilter(filter: AppFilter) {
        _uiState.update { current ->
            val filtered = applyFilter(current.installedApps, current.searchQuery, filter)
            current.copy(activeFilter = filter, filteredApps = filtered)
        }
    }

    fun startMonitoring(context: Context) {
        val selected = _uiState.value.selectedPackages
        if (selected.isEmpty()) {
            _uiState.update { it.copy(userMessage = "يرجى اختيار تطبيق واحد على الأقل للمراقبة.") }
            return
        }
        if (!ShizukuManager.checkPermission()) {
            _uiState.update { it.copy(userMessage = "يلزم منح إذن Shizuku أولاً لبدء المراقبة.") }
            return
        }
        CrashWatcherService.start(context, selected)
    }

    fun stopMonitoring(context: Context) {
        CrashWatcherService.stop(context)
    }

    fun dismissUserMessage() {
        CrashWatcherState.setError(null)
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun applyFilter(
        apps: List<MonitoredAppItem>,
        query: String,
        filter: AppFilter
    ): List<MonitoredAppItem> {
        return apps.filter { app ->
            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER_ONLY -> !app.isSystemApp
                AppFilter.SYSTEM_ONLY -> app.isSystemApp
            }
            val matchesQuery = query.isBlank() ||
                app.appName.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }
}
