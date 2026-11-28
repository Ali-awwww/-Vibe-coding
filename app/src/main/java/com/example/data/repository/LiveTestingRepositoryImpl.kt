package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.data.local.CrashDao
import com.example.domain.model.*
import com.example.domain.repository.LiveTestingRepository
import com.example.util.logcat.LogcatCrashParser
import com.example.util.shizuku.ShizukuManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class DeviceAgentInfo(
    val deviceId: String,
    val deviceName: String,
    val os: String
)

interface LiveAgentTransport {
    val isTransportAvailable: Boolean
    suspend fun checkReadiness(packageName: String): Result<DeviceAgentInfo>
    suspend fun dumpLogcat(packageName: String): Result<List<String>>
    suspend fun clearLogcat(): Result<Unit>
    suspend fun launchPackage(packageName: String): Result<String>
    suspend fun forceStopPackage(packageName: String): Result<String>
}

class ShizukuLiveAgentTransport(
    private val context: Context? = null
) : LiveAgentTransport {

    override val isTransportAvailable: Boolean = true

    private fun validatePackageName(packageName: String): String {
        val clean = packageName.trim()
        require(clean.isNotBlank() && PACKAGE_NAME_REGEX.matches(clean)) {
            "اسم حزمة التطبيق غير صالح: '$packageName'"
        }
        return clean
    }

    override suspend fun checkReadiness(packageName: String): Result<DeviceAgentInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!ShizukuManager.isShizukuRunning()) {
                    throw IllegalStateException("خدمة Shizuku غير نشطة على الجهاز. شغّل Shizuku أولاً لبدء الاختبار الحي.")
                }
                if (!ShizukuManager.checkPermission()) {
                    throw SecurityException("يلزم منح إذن Shizuku للتطبيق قبل بدء الاختبار الحي.")
                }
                if (packageName.isNotBlank() && context != null) {
                    val cleanPkg = validatePackageName(packageName)
                    runCatching {
                        context.packageManager.getPackageInfo(cleanPkg, 0)
                    }.getOrElse {
                        throw IllegalStateException("التطبيق المستهدف ($cleanPkg) غير مثبت على هذا الجهاز.")
                    }
                }
                val manufacturer = Build.MANUFACTURER.orEmpty().trim()
                val model = Build.MODEL.orEmpty().trim()
                val deviceName = listOf(manufacturer, model)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { "Android Device" }
                val osLabel = "Android ${Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString()}"
                DeviceAgentInfo(
                    deviceId = deviceName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "local-device" },
                    deviceName = deviceName,
                    os = osLabel
                )
            }
        }

    override suspend fun dumpLogcat(packageName: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val process = ShizukuManager.executeProcess(
                    arrayOf("logcat", "-d", "-t", "150", "-v", "threadtime", "-b", "crash", "-b", "main")
                ) ?: throw IllegalStateException("تعذر الوصول إلى logcat عبر Shizuku.")
                try {
                    val lines = process.inputStream.bufferedReader().use { it.readLines() }
                    if (packageName.isNotBlank()) {
                        lines
                    } else {
                        lines
                    }
                } finally {
                    process.destroy()
                }
            }
        }

    override suspend fun clearLogcat(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val process = ShizukuManager.executeProcess(arrayOf("logcat", "-c"))
                    ?: throw IllegalStateException("تعذر مسح مخزن logcat عبر Shizuku.")
                try {
                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        throw IllegalStateException("أعاد أمر logcat -c رمز خروج غير متوقع ($exitCode).")
                    }
                } finally {
                    process.destroy()
                }
            }
        }

    override suspend fun launchPackage(packageName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanPkg = validatePackageName(packageName)
                val process = ShizukuManager.executeProcess(
                    arrayOf("monkey", "-p", cleanPkg, "-c", "android.intent.category.LAUNCHER", "1")
                ) ?: throw IllegalStateException("تعذر تشغيل الحزمة $cleanPkg عبر Shizuku.")
                try {
                    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        throw IllegalStateException("تعذر تشغيل $cleanPkg (رمز الخروج $exitCode).")
                    }
                    output.ifBlank { "تم إرسال طلب تشغيل الحزمة $cleanPkg بنجاح." }
                } finally {
                    process.destroy()
                }
            }
        }

    override suspend fun forceStopPackage(packageName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanPkg = validatePackageName(packageName)
                val process = ShizukuManager.executeProcess(arrayOf("am", "force-stop", cleanPkg))
                    ?: throw IllegalStateException("تعذر إيقاف الحزمة $cleanPkg عبر Shizuku.")
                try {
                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        throw IllegalStateException("فشل إيقاف $cleanPkg قسرياً (رمز الخروج $exitCode).")
                    }
                    "تم إيقاف الحزمة $cleanPkg قسرياً عبر Shizuku."
                } finally {
                    process.destroy()
                }
            }
        }

    companion object {
        private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z0-9_.]+$")
    }
}

class LiveTestingRepositoryImpl(
    private val crashDao: CrashDao,
    private val transport: LiveAgentTransport = ShizukuLiveAgentTransport(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : LiveTestingRepository {

    override val isAgentTransportAvailable: Boolean
        get() = transport.isTransportAvailable

    private val watchJobs = ConcurrentHashMap<String, Job>()
    private val recordMutex = Mutex()

    override fun getTestSessions(projectId: String): Flow<List<TestSession>> =
        crashDao.getTestSessions(projectId)

    override fun getTestSessionById(sessionId: String): Flow<TestSession?> =
        crashDao.getTestSessionById(sessionId)

    override suspend fun connectToAgent(applicationId: String): Result<Boolean> {
        if (!isAgentTransportAvailable) {
            return Result.failure(UnsupportedOperationException("Agent transport is unavailable."))
        }
        val app = crashDao.getApplicationById(applicationId)
            ?: return Result.failure(IllegalArgumentException("التطبيق المطلوب غير موجود."))
        val readiness = transport.checkReadiness(app.packageName)
        return readiness.fold(
            onSuccess = {
                crashDao.updateApplicationAgentStatus(applicationId, AgentConnectionStatus.ONLINE)
                Result.success(true)
            },
            onFailure = { error ->
                crashDao.updateApplicationAgentStatus(applicationId, AgentConnectionStatus.OFFLINE)
                Result.failure(error)
            }
        )
    }

    override suspend fun startTestSession(session: TestSession): Result<Unit> {
        if (!isAgentTransportAvailable) {
            return Result.failure(UnsupportedOperationException("Agent transport is unavailable."))
        }
        val app = crashDao.getApplicationById(session.applicationId)
            ?: return Result.failure(IllegalArgumentException("التطبيق المستهدف غير موجود."))
        val readiness = transport.checkReadiness(app.packageName)
        val deviceInfo = readiness.getOrElse { error ->
            crashDao.updateApplicationAgentStatus(app.id, AgentConnectionStatus.OFFLINE)
            return Result.failure(error)
        }

        val runningSession = session.copy(
            deviceId = deviceInfo.deviceId,
            deviceName = deviceInfo.deviceName,
            os = deviceInfo.os,
            status = TestSessionStatus.APP_RUNNING
        )
        crashDao.insertTestSession(runningSession)
        crashDao.updateApplicationAgentStatus(app.id, AgentConnectionStatus.BUSY)
        crashDao.insertTestEvent(
            TestEvent(
                id = UUID.randomUUID().toString(),
                sessionId = runningSession.id,
                type = TestEventType.APP_START,
                details = "بدأت جلسة الاختبار الحي للحزمة ${app.packageName.ifBlank { app.name }} على ${deviceInfo.deviceName} (${deviceInfo.os})"
            )
        )

        if (app.packageName.isNotBlank()) {
            startSessionMonitoringJob(runningSession, app)
        }
        return Result.success(Unit)
    }

    private fun startSessionMonitoringJob(session: TestSession, app: Application) {
        watchJobs.remove(session.id)?.cancel()
        val monitoredSet = setOf(app.packageName)
        val seenFingerprints = mutableSetOf<String>()
        val job = scope.launch {
            while (isActive) {
                val currentSession = crashDao.getTestSessionById(session.id).first()
                if (currentSession == null ||
                    currentSession.status == TestSessionStatus.COMPLETED ||
                    currentSession.status == TestSessionStatus.FAILED ||
                    currentSession.status == TestSessionStatus.CRASH_DETECTED
                ) {
                    break
                }
                val dumpResult = transport.dumpLogcat(app.packageName)
                val lines = dumpResult.getOrNull().orEmpty()
                val parsedCrashes = LogcatCrashParser.parseLines(lines, monitoredSet)
                val firstNew = parsedCrashes.firstOrNull { parsed ->
                    val fp = "${parsed.packageName}:${parsed.exceptionType}:${parsed.message.take(64)}"
                    seenFingerprints.add(fp)
                }
                if (firstNew != null) {
                    recordCrashForSession(currentSession, app, firstNew)
                    break
                }
                delay(1_500L)
            }
        }
        watchJobs[session.id] = job
    }

    private suspend fun recordCrashForSession(
        session: TestSession,
        app: Application,
        parsed: com.example.util.logcat.ParsedCrash
    ) = recordMutex.withLock {
        val currentSession = crashDao.getTestSessionById(session.id).first() ?: session
        if (currentSession.status == TestSessionStatus.CRASH_DETECTED && !currentSession.crashId.isNullOrBlank()) {
            return@withLock
        }
        val crashId = UUID.randomUUID().toString()
        val crashReport = CrashReport(
            id = crashId,
            projectId = session.projectId,
            applicationId = session.applicationId,
            exceptionType = parsed.exceptionType,
            message = parsed.message,
            stackTrace = parsed.stackTrace,
            deviceModel = session.deviceName,
            osVersion = session.os,
            appVersion = session.version,
            timestamp = System.currentTimeMillis(),
            severity = parsed.severity,
            source = IntegrationType.ON_DEVICE_WATCHER,
            isSimulatedQaReport = false
        )
        crashDao.insertCrash(crashReport)
        crashDao.insertTestEvent(
            TestEvent(
                id = UUID.randomUUID().toString(),
                sessionId = session.id,
                type = if (parsed.category == ErrorCategory.ANR) TestEventType.ANR else TestEventType.CRASH,
                details = "${parsed.exceptionType}: ${parsed.message}\n${parsed.stackTrace.lines().take(5).joinToString("\n")}"
            )
        )
        crashDao.updateTestSessionStatus(
            sessionId = session.id,
            status = TestSessionStatus.CRASH_DETECTED,
            endTime = System.currentTimeMillis(),
            crashId = crashId
        )
        crashDao.updateApplicationAgentStatus(app.id, AgentConnectionStatus.ONLINE)
    }

    override suspend fun endTestSession(sessionId: String, status: TestSessionStatus): Result<Unit> {
        if (!isAgentTransportAvailable) {
            return Result.failure(UnsupportedOperationException("Agent transport is unavailable."))
        }
        watchJobs.remove(sessionId)?.cancel()
        val session = crashDao.getTestSessionById(sessionId).first()
            ?: return Result.failure(IllegalArgumentException("جلسة الاختبار غير موجودة."))
        val finalStatus = if (status == TestSessionStatus.APP_RUNNING || status == TestSessionStatus.PREPARING) {
            TestSessionStatus.COMPLETED
        } else {
            status
        }
        crashDao.updateTestSessionStatus(
            sessionId = sessionId,
            status = finalStatus,
            endTime = System.currentTimeMillis(),
            crashId = session.crashId
        )
        crashDao.insertTestEvent(
            TestEvent(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                type = TestEventType.APP_STATE_CHANGE,
                details = "تم إنهاء جلسة الاختبار الحي بالحالة: ${finalStatus.name}"
            )
        )
        if (session.applicationId.isNotBlank()) {
            crashDao.updateApplicationAgentStatus(session.applicationId, AgentConnectionStatus.ONLINE)
        }
        return Result.success(Unit)
    }

    override fun getTestEvents(sessionId: String): Flow<List<TestEvent>> =
        crashDao.getTestEvents(sessionId)

    override suspend fun addTestEvent(event: TestEvent): Result<Unit> {
        if (!isAgentTransportAvailable) {
            return Result.failure(UnsupportedOperationException("Agent transport is unavailable."))
        }
        if (event.sessionId.isBlank()) {
            return Result.failure(IllegalArgumentException("معرّف الجلسة مطلوب لإضافة الحدث."))
        }
        val session = crashDao.getTestSessionById(event.sessionId).first()
            ?: return Result.failure(IllegalArgumentException("الجلسة المرتبطة غير موجودة."))
        crashDao.insertTestEvent(event)
        if (event.type == TestEventType.CRASH && session.status != TestSessionStatus.CRASH_DETECTED) {
            crashDao.updateTestSessionStatus(session.id, TestSessionStatus.CRASH_DETECTED, System.currentTimeMillis(), session.crashId)
        }
        return Result.success(Unit)
    }

    override suspend fun updateApplicationSdkStatus(applicationId: String, status: SdkSetupStatus) {
        crashDao.updateApplicationSdkStatus(applicationId, status)
    }

    override suspend fun updateApplicationAgentStatus(applicationId: String, status: AgentConnectionStatus) {
        if (!isAgentTransportAvailable && status != AgentConnectionStatus.OFFLINE) {
            throw UnsupportedOperationException("Agent status cannot be marked online or busy without a verified transport.")
        }
        crashDao.updateApplicationAgentStatus(applicationId, status)
    }

    override suspend fun sendCommandToAgent(sessionId: String, command: String): Result<Unit> {
        if (!isAgentTransportAvailable) {
            return Result.failure(UnsupportedOperationException("Agent transport is unavailable."))
        }
        val session = crashDao.getTestSessionById(sessionId).first()
            ?: return Result.failure(IllegalArgumentException("جلسة الاختبار غير موجودة."))
        val app = crashDao.getApplicationById(session.applicationId)
        val packageName = app?.packageName.orEmpty()
        val normalized = command.trim().lowercase()

        return when (normalized) {
            "ping", "status" -> {
                transport.checkReadiness(packageName).map { info ->
                    crashDao.insertTestEvent(
                        TestEvent(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            type = TestEventType.SDK_EVENT,
                            details = "فحص الاتصال (ping): متصل بـ ${info.deviceName} (${info.os})"
                        )
                    )
                }
            }
            "dump_logs", "logcat" -> {
                transport.dumpLogcat(packageName).map { lines ->
                    val parsed = if (packageName.isNotBlank()) {
                        LogcatCrashParser.parseLines(lines, setOf(packageName))
                    } else {
                        emptyList()
                    }
                    if (parsed.isNotEmpty() && app != null) {
                        recordCrashForSession(session, app, parsed.first())
                    } else {
                        val relevant = lines.filter { packageName.isBlank() || it.contains(packageName, ignoreCase = true) }
                            .takeLast(8)
                        val summary = if (relevant.isEmpty()) {
                            "فحص logcat مكتمل (${lines.size} سطراً): لم تُرصد أعطال أو أخطاء جديدة."
                        } else {
                            "آخر مقتطفات logcat (${relevant.size}/${lines.size}):\n${relevant.joinToString("\n")}"
                        }
                        crashDao.insertTestEvent(
                            TestEvent(
                                id = UUID.randomUUID().toString(),
                                sessionId = sessionId,
                                type = TestEventType.SDK_EVENT,
                                details = summary
                            )
                        )
                    }
                }
            }
            "clear_logs" -> {
                transport.clearLogcat().map {
                    crashDao.insertTestEvent(
                        TestEvent(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            type = TestEventType.SDK_EVENT,
                            details = "تم مسح مخزن logcat عبر Shizuku بنجاح."
                        )
                    )
                }
            }
            "launch" -> {
                if (packageName.isBlank()) {
                    Result.failure(IllegalArgumentException("حزمة التطبيق غير محددة للتشغيل."))
                } else {
                    transport.launchPackage(packageName).map { msg ->
                        crashDao.insertTestEvent(
                            TestEvent(
                                id = UUID.randomUUID().toString(),
                                sessionId = sessionId,
                                type = TestEventType.APP_START,
                                details = msg
                            )
                        )
                    }
                }
            }
            "force_stop" -> {
                if (packageName.isBlank()) {
                    Result.failure(IllegalArgumentException("حزمة التطبيق غير محددة للإيقاف."))
                } else {
                    transport.forceStopPackage(packageName).map { msg ->
                        crashDao.insertTestEvent(
                            TestEvent(
                                id = UUID.randomUUID().toString(),
                                sessionId = sessionId,
                                type = TestEventType.APP_STATE_CHANGE,
                                details = msg
                            )
                        )
                    }
                }
            }
            else -> Result.failure(
                IllegalArgumentException(
                    "أمر غير مدعوم ('$command'). الأوامر المسموحة: ping, dump_logs, clear_logs, launch, force_stop"
                )
            )
        }
    }
}
