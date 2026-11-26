package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.di.DependencyProvider
import com.example.domain.model.Application
import com.example.domain.model.CrashReport
import com.example.domain.model.IntegrationType
import com.example.domain.model.Project
import com.example.domain.model.Severity
import com.example.util.logcat.LogcatCrashParser
import com.example.util.logcat.ParsedCrash
import com.example.util.shizuku.ShizukuManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.ArrayDeque
import java.util.UUID

object CrashWatcherState {
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring = _isMonitoring.asStateFlow()

    private val _monitoredPackages = MutableStateFlow<Set<String>>(emptySet())
    val monitoredPackages = _monitoredPackages.asStateFlow()

    private val _startTime = MutableStateFlow<Long?>(null)
    val startTime = _startTime.asStateFlow()

    private val _capturedCount = MutableStateFlow(0)
    val capturedCount = _capturedCount.asStateFlow()

    private val _lastCapturedCrash = MutableStateFlow<CrashReport?>(null)
    val lastCapturedCrash = _lastCapturedCrash.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    internal fun setMonitoring(active: Boolean, packages: Set<String> = emptySet(), time: Long? = null) {
        _isMonitoring.value = active
        _monitoredPackages.value = packages
        _startTime.value = time
    }

    internal fun onProcessStartResult(
        processStarted: Boolean,
        packages: Set<String> = emptySet(),
        startedAt: Long? = null
    ) {
        if (processStarted && packages.isNotEmpty()) {
            setMonitoring(true, packages, startedAt ?: System.currentTimeMillis())
        } else {
            setMonitoring(false)
        }
    }

    internal fun setError(message: String?) {
        _errorMessage.value = message
    }

    internal fun incrementCaptured(crash: CrashReport) {
        _capturedCount.value += 1
        _lastCapturedCrash.value = crash
    }
}

class CrashWatcherService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamJob: Job? = null
    @Volatile
    private var activeProcess: Process? = null

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_STOP -> {
                CrashWatcherState.setError(null)
                stopMonitoring()
            }
            ACTION_START -> {
                val packages = intent?.getStringArrayListExtra(EXTRA_PACKAGES)?.toSet().orEmpty()
                if (packages.isNotEmpty()) {
                    startMonitoring(packages)
                } else {
                    CrashWatcherState.onProcessStartResult(processStarted = false)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING_ID,
                getString(R.string.crash_watcher_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.crash_watcher_service_channel_desc)
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                getString(R.string.crash_watcher_alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.crash_watcher_alert_channel_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(ongoingChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    private fun startMonitoring(packages: Set<String>) {
        streamJob?.cancel()
        runCatching { activeProcess?.destroy() }
        activeProcess = null
        CrashWatcherState.onProcessStartResult(processStarted = false)
        CrashWatcherState.setError(null)

        // A foreground notification is required immediately, but it must not claim that
        // monitoring is active until the privileged logcat process has actually started.
        startForeground(
            ONGOING_NOTIFICATION_ID,
            buildServiceNotification(packages, isStarting = true)
        )

        streamJob = serviceScope.launch {
            if (!inspectRecentLogcatBuffer(packages)) {
                failMonitoring(getString(R.string.crash_watcher_process_start_failed))
                return@launch
            }

            val process = ShizukuManager.executeProcess(
                arrayOf("logcat", "-v", "threadtime", "-b", "crash", "-b", "main")
            )
            if (process == null) {
                failMonitoring(getString(R.string.crash_watcher_process_start_failed))
                return@launch
            }

            activeProcess = process
            CrashWatcherState.onProcessStartResult(
                processStarted = true,
                packages = packages,
                startedAt = System.currentTimeMillis()
            )
            withContext(Dispatchers.Main) {
                notificationManager.notify(
                    ONGOING_NOTIFICATION_ID,
                    buildServiceNotification(packages, isStarting = false)
                )
            }

            streamLogcatContinuously(packages, process)
        }
    }

    private suspend fun failMonitoring(message: String) {
        CrashWatcherState.onProcessStartResult(processStarted = false)
        CrashWatcherState.setError(message)
        withContext(Dispatchers.Main) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildServiceNotification(
        packages: Set<String>,
        isStarting: Boolean
    ): android.app.Notification {
        val stopIntent = Intent(this, CrashWatcherService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            102,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appCount = packages.size
        return NotificationCompat.Builder(this, CHANNEL_ONGOING_ID)
            .setContentTitle(
                getString(
                    if (isStarting) R.string.crash_watcher_service_starting_title
                    else R.string.crash_watcher_service_notification_title
                )
            )
            .setContentText(
                if (isStarting) getString(R.string.crash_watcher_service_starting_text)
                else getString(R.string.crash_watcher_service_notification_text, appCount)
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.crash_watcher_service_stop_action),
                stopPendingIntent
            )
            .build()
    }

    private suspend fun inspectRecentLogcatBuffer(packages: Set<String>): Boolean = withContext(Dispatchers.IO) {
        val cmd = arrayOf("logcat", "-d", "-v", "threadtime", "-b", "crash", "-b", "main")
        val process = ShizukuManager.executeProcess(cmd) ?: return@withContext false
        try {
            val lines = process.inputStream.bufferedReader().use { it.readLines() }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.w(TAG, "Recent logcat inspection failed with exit code $exitCode")
                return@withContext false
            }

            LogcatCrashParser.parseLines(lines, packages).forEach { crash ->
                recordAndNotifyCrash(crash)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Error checking recent logcat buffer: ${e.message}")
            false
        } finally {
            runCatching { process.destroy() }
        }
    }

    private suspend fun streamLogcatContinuously(
        packages: Set<String>,
        process: Process
    ) = withContext(Dispatchers.IO) {
        activeProcess = process

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val window = ArrayDeque<String>(80)
        var line: String? = null

        try {
            while (isActive && reader.readLine().also { line = it } != null) {
                val currentLine = line ?: break
                window.addLast(currentLine)
                if (window.size > 80) window.removeFirst()

                if (currentLine.contains("FATAL EXCEPTION", ignoreCase = true) ||
                    currentLine.contains("Fatal signal", ignoreCase = true) ||
                    currentLine.contains("ANR in ", ignoreCase = true)
                ) {
                    // Accumulate following 40 lines for the crash block
                    val crashLines = mutableListOf<String>()
                    crashLines.addAll(window)
                    for (i in 0 until 40) {
                        val next = reader.readLine() ?: break
                        crashLines.add(next)
                        window.addLast(next)
                        if (window.size > 80) window.removeFirst()
                    }
                    val detected = LogcatCrashParser.parseLines(crashLines, packages)
                    for (crash in detected) {
                        recordAndNotifyCrash(crash)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Logcat streaming ended: ${e.message}")
        } finally {
            runCatching { process.destroy() }
            if (activeProcess === process) activeProcess = null
            if (isActive) {
                CrashWatcherState.onProcessStartResult(processStarted = false)
                CrashWatcherState.setError(getString(R.string.crash_watcher_stream_ended))
                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private suspend fun recordAndNotifyCrash(parsed: ParsedCrash) = withContext(Dispatchers.IO) {
        try {
            val crashRepo = DependencyProvider.provideCrashRepository(applicationContext)
            val authRepo = DependencyProvider.provideAuthRepository(applicationContext)
            val currentUserId = authRepo.getCurrentUserSync()?.id?.takeIf { it.isNotBlank() }
                ?: run {
                    Log.w(TAG, "Skipping captured crash because no authenticated user is available")
                    return@withContext
                }

            // 1. Resolve or establish active project and registered application
            val projects = crashRepo.getProjects().firstOrNull().orEmpty()
            val targetProject = projects.firstOrNull() ?: run {
                val newProj = Project(
                    id = "prj_on_device",
                    name = "مراقبة الجهاز (On-Device)",
                    description = "مشروع الأعطال المباشرة عبر مراقب Shizuku",
                    ownerId = currentUserId
                )
                runCatching { crashRepo.addProject(newProj) }
                newProj
            }

            val appEntityId = "app_" + parsed.packageName.replace('.', '_').take(100)
            val apps = crashRepo.getApplications(targetProject.id).firstOrNull().orEmpty()
            if (apps.none { it.id == appEntityId }) {
                val appName = try {
                    val pm = packageManager
                    val pi = pm.getPackageInfo(parsed.packageName, 0)
                    pi.applicationInfo?.loadLabel(pm)?.toString() ?: parsed.packageName
                } catch (_: Exception) {
                    parsed.packageName
                }
                val newApp = Application(
                    id = appEntityId,
                    projectId = targetProject.id,
                    name = appName,
                    packageName = parsed.packageName,
                    ownerId = currentUserId
                )
                runCatching { crashRepo.addApplication(newApp) }
            }

            val appVersion = try {
                packageManager.getPackageInfo(parsed.packageName, 0).versionName ?: "1.0"
            } catch (_: Exception) {
                "1.0"
            }

            val crashId = UUID.randomUUID().toString()
            val crashReport = CrashReport(
                id = crashId,
                applicationId = appEntityId,
                projectId = targetProject.id,
                exceptionType = parsed.exceptionType,
                message = parsed.message,
                stackTrace = parsed.stackTrace,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                appVersion = appVersion,
                timestamp = parsed.timestamp,
                isResolved = false,
                severity = parsed.severity,
                userId = currentUserId,
                source = IntegrationType.ON_DEVICE_WATCHER
            )

            crashRepo.addCrash(crashReport)
            CrashWatcherState.incrementCaptured(crashReport)

            // Post system alert notification
            sendCrashAlertNotification(crashReport, parsed.packageName)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to record on-device crash: ${e.message}", e)
        }
    }

    private fun sendCrashAlertNotification(crash: CrashReport, packageName: String) {
        val appLabel = try {
            val pm = packageManager
            val pi = pm.getPackageInfo(packageName, 0)
            pi.applicationInfo?.loadLabel(pm)?.toString() ?: packageName
        } catch (_: Exception) {
            packageName
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_CRASH_ID", crash.id)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            crash.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alertNotification = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setContentTitle(getString(R.string.crash_watcher_alert_title, appLabel))
            .setContentText(getString(R.string.crash_watcher_alert_text, crash.exceptionType, crash.message.take(80)))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(crash.id.hashCode(), alertNotification)
    }

    private fun stopMonitoring() {
        CrashWatcherState.onProcessStartResult(processStarted = false)
        streamJob?.cancel()
        streamJob = null
        runCatching { activeProcess?.destroy() }
        activeProcess = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "CrashWatcherService"
        private const val ONGOING_NOTIFICATION_ID = 9001
        private const val CHANNEL_ONGOING_ID = "crash_watcher_ongoing"
        private const val CHANNEL_ALERTS_ID = "crash_watcher_alerts"

        const val ACTION_START = "com.example.service.action.START_WATCHER"
        const val ACTION_STOP = "com.example.service.action.STOP_WATCHER"
        const val EXTRA_PACKAGES = "extra_monitored_packages"

        fun start(context: Context, packages: Set<String>) {
            val intent = Intent(context, CrashWatcherService::class.java).apply {
                action = ACTION_START
                putStringArrayListExtra(EXTRA_PACKAGES, ArrayList(packages))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CrashWatcherService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
