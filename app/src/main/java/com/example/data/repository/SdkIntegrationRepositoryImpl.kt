package com.example.data.repository

import com.example.data.local.CrashDao
import com.example.data.remote.BugsnagApi
import com.example.data.remote.SentryApi
import com.example.domain.model.*
import com.example.domain.repository.SdkIntegrationRepository
import com.example.service.CrashWatcherState
import com.example.util.SecurityUtils
import com.example.util.shizuku.ShizukuManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.HttpException

interface DiagnosticVerificationTransport {
    val isAvailable: Boolean
    suspend fun isWatcherMonitoring(packageName: String): Boolean
    suspend fun hasRecentLogcatActivity(packageName: String): Boolean
}

class ShizukuDiagnosticVerificationTransport : DiagnosticVerificationTransport {
    override val isAvailable: Boolean = true

    override suspend fun isWatcherMonitoring(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return CrashWatcherState.isMonitoring.value &&
            packageName in CrashWatcherState.monitoredPackages.value
    }

    override suspend fun hasRecentLogcatActivity(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (packageName.isBlank() || !ShizukuManager.checkPermission()) return@withContext false
        val process = ShizukuManager.executeProcess(
            arrayOf("logcat", "-d", "-t", "80", "-b", "crash", "-b", "main")
        ) ?: return@withContext false
        try {
            val lines = process.inputStream.bufferedReader().use { it.readLines() }
            val exitCode = process.waitFor()
            exitCode == 0 && lines.any { it.contains(packageName, ignoreCase = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        } finally {
            runCatching { process.destroy() }
        }
    }
}

class SdkIntegrationRepositoryImpl(
    private val crashDao: CrashDao? = null,
    private val sentryApi: SentryApi? = null,
    private val bugsnagApi: BugsnagApi? = null,
    private val transport: DiagnosticVerificationTransport = ShizukuDiagnosticVerificationTransport()
) : SdkIntegrationRepository {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    override val isVerificationTransportAvailable: Boolean
        get() = transport.isAvailable

    override suspend fun detectTechnology(fileNames: List<String>): ProjectScanResult {
        val files = fileNames.map { it.lowercase() }
        val tech = when {
            files.any { it.contains("pubspec.yaml") } -> ProjectTechnology.FLUTTER
            files.any { it.contains("package.json") } && files.any { it.contains("android") || it.contains("ios") } -> ProjectTechnology.REACT_NATIVE
            files.any { it.contains("projectsettings/") || it.contains("assets/") } -> ProjectTechnology.UNITY
            files.any { it.endsWith(".swift") } -> ProjectTechnology.IOS_SWIFT
            files.any { it.endsWith(".m") || it.endsWith(".h") } -> ProjectTechnology.IOS_OBJC
            files.any { it.endsWith(".kt") } || files.any { it.contains("build.gradle.kts") } -> ProjectTechnology.ANDROID_KOTLIN
            files.any { it.endsWith(".java") } || files.any { it.contains("build.gradle") } -> ProjectTechnology.ANDROID_JAVA
            else -> ProjectTechnology.UNKNOWN
        }

        return ProjectScanResult(
            tech = tech,
            detectedFiles = fileNames.filter { file ->
                listOf("gradle", "yaml", "json", "swift", "kt", "java", "plist", "xml")
                    .any { file.endsWith(it, ignoreCase = true) }
            }.take(5)
        )
    }

    @Suppress("UNUSED_PARAMETER")
    override suspend fun getSetupGuide(
        tech: ProjectTechnology,
        service: IntegrationType,
        projectId: String
    ): SdkSetupGuide {
        val isAndroid = tech == ProjectTechnology.ANDROID_KOTLIN || tech == ProjectTechnology.ANDROID_JAVA
        return when {
            service == IntegrationType.FIREBASE_CRASHLYTICS -> unavailableGuide(
                tech,
                service,
                "setup_guide_firebase_unavailable_body"
            )
            service == IntegrationType.SENTRY && isAndroid -> getSentryGuide(tech)
            service == IntegrationType.BUGSNAG && isAndroid -> getBugsnagGuide(tech)
            service == IntegrationType.SENTRY || service == IntegrationType.BUGSNAG -> unavailableGuide(
                tech,
                service,
                "setup_guide_platform_unsupported"
            )
            else -> unavailableGuide(tech, service, "setup_guide_service_unavailable")
        }
    }

    private fun getSentryGuide(tech: ProjectTechnology): SdkSetupGuide {
        val verificationSnippet = if (tech == ProjectTechnology.ANDROID_JAVA) {
            "import io.sentry.Sentry;\nSentry.captureException(new RuntimeException(\"Vibe setup verification\"));"
        } else {
            "import io.sentry.Sentry\nSentry.captureException(RuntimeException(\"Vibe setup verification\"))"
        }
        return SdkSetupGuide(
            tech = tech,
            service = IntegrationType.SENTRY,
            documentationUrl = "https://docs.sentry.io/platforms/android/",
            prerequisites = listOf(
                "setup_guide_sentry_prerequisite_account",
                "setup_guide_sentry_prerequisite_platform"
            ),
            steps = listOf(
                IntegrationStep(
                    id = "sentry_install",
                    title = "Install Sentry",
                    description = "Run the official Android wizard from the project root. It configures the SDK and Gradle integration; review its diff before accepting the changes.",
                    codeSnippet = "npx @sentry/wizard@latest -i android",
                    fileToModify = "Project root terminal"
                ),
                IntegrationStep(
                    id = "sentry_credentials",
                    title = "Protect the Sentry upload token",
                    description = "Review the generated sentry.properties file. Keep its upload token out of source control and provide it to CI through a protected environment secret.",
                    codeSnippet = "sentry.properties",
                    fileToModify = "sentry.properties / .gitignore"
                ),
                IntegrationStep(
                    id = "sentry_verify",
                    title = "Send a test event",
                    description = "Call this from a debug-only action or test. Do not leave a forced test event in the production user flow.",
                    codeSnippet = verificationSnippet,
                    fileToModify = if (tech == ProjectTechnology.ANDROID_JAVA) "Debug-only Java test" else "Debug-only Kotlin test"
                ),
                IntegrationStep(
                    id = "sentry_dashboard",
                    title = "Confirm the event in Sentry",
                    description = "Open the linked Sentry project and confirm that the test event appears, then tap Verify Integration below.",
                    fileToModify = "Sentry Issues dashboard"
                )
            ),
            troubleshooting = listOf(
                "setup_guide_troubleshooting_sentry_dsn",
                "setup_guide_troubleshooting_sentry_token"
            )
        )
    }

    private fun getBugsnagGuide(tech: ProjectTechnology): SdkSetupGuide {
        val applicationSnippet = if (tech == ProjectTechnology.ANDROID_JAVA) {
            "public class MyApp extends Application {\n    @Override\n    public void onCreate() {\n        super.onCreate();\n        Bugsnag.start(this);\n    }\n}"
        } else {
            "class MyApp : Application() {\n    override fun onCreate() {\n        super.onCreate()\n        Bugsnag.start(this)\n    }\n}"
        }
        val verificationSnippet = if (tech == ProjectTechnology.ANDROID_JAVA) {
            "Bugsnag.notify(new IllegalStateException(\"Vibe setup verification\"));"
        } else {
            "Bugsnag.notify(IllegalStateException(\"Vibe setup verification\"))"
        }
        return SdkSetupGuide(
            tech = tech,
            service = IntegrationType.BUGSNAG,
            documentationUrl = "https://docs.bugsnag.com/platforms/android/",
            prerequisites = listOf(
                "setup_guide_bugsnag_prerequisite_account",
                "setup_guide_bugsnag_prerequisite_platform"
            ),
            steps = listOf(
                IntegrationStep(
                    id = "bugsnag_dependency",
                    title = "Install the BugSnag Android SDK",
                    description = "Add the current Android notifier dependency to the app module.",
                    codeSnippet = "implementation(\"com.bugsnag:bugsnag-android:6.27.1\")",
                    fileToModify = "app/build.gradle.kts"
                ),
                IntegrationStep(
                    id = "bugsnag_manifest",
                    title = "Configure the project API key",
                    description = "Use the API key for this BugSnag project, not a personal or organization management token.",
                    codeSnippet = "<meta-data android:name=\"com.bugsnag.android.API_KEY\" android:value=\"YOUR_PROJECT_API_KEY\" />",
                    fileToModify = "AndroidManifest.xml"
                ),
                IntegrationStep(
                    id = "bugsnag_initialize",
                    title = "Initialize BugSnag",
                    description = "Create or update the Application subclass and reference it from the application element in AndroidManifest.xml.",
                    codeSnippet = applicationSnippet,
                    fileToModify = if (tech == ProjectTechnology.ANDROID_JAVA) "MyApp.java" else "MyApp.kt"
                ),
                IntegrationStep(
                    id = "bugsnag_verify",
                    title = "Send a test notification",
                    description = "Run this only from a debug-only action or test, then confirm the event in the BugSnag dashboard.",
                    codeSnippet = verificationSnippet,
                    fileToModify = "Debug-only test"
                )
            ),
            troubleshooting = listOf(
                "setup_guide_troubleshooting_bugsnag_key",
                "setup_guide_troubleshooting_bugsnag_application"
            )
        )
    }

    private fun unavailableGuide(
        tech: ProjectTechnology,
        service: IntegrationType,
        reason: String
    ): SdkSetupGuide = SdkSetupGuide(
        tech = tech,
        service = service,
        steps = emptyList(),
        troubleshooting = emptyList(),
        unavailableReason = reason
    )

    override suspend fun verifyIntegration(applicationId: String): Result<Boolean> {
        if (applicationId.isBlank()) {
            return Result.failure(IllegalArgumentException("معرّف التطبيق مطلوب للتحقق من التكامل."))
        }
        val dao = crashDao
            ?: return Result.failure(IllegalStateException("قاعدة البيانات غير متاحة للتحقق من تكامل التطبيق."))

        val app = dao.getApplicationById(applicationId)
            ?: return Result.failure(NoSuchElementException("لم يتم العثور على التطبيق المطلوب."))

        val integrations = dao.getIntegrations(app.projectId).first()
        val realCrashes = dao.getCrashesByProject(app.projectId).first()
            .filter { !it.isSimulatedQa() && (it.applicationId == app.id || it.applicationId.isBlank()) }

        // 1. Check Sentry integration if configured for this project
        val sentryIntegration = integrations.firstOrNull { it.type == IntegrationType.SENTRY }
        if (sentryIntegration != null) {
            val config = parseConfig<SentryConfig>(sentryIntegration.configJson)
            if (config == null || config.token.isBlank() || config.org.isBlank() || config.project.isBlank()) {
                dao.insertIntegration(sentryIntegration.copy(status = IntegrationStatus.ERROR))
                dao.updateApplicationSdkStatus(app.id, SdkSetupStatus.ERROR)
                return Result.failure(IllegalStateException("بيانات ربط Sentry غير مكتملة أو غير صالحة."))
            }
            val api = sentryApi
            if (api != null) {
                try {
                    val issues = api.getIssues(
                        authToken = "Bearer ${config.token}",
                        orgSlug = config.org,
                        projectSlug = config.project
                    )
                    dao.insertIntegration(
                        sentryIntegration.copy(
                            status = IntegrationStatus.CONNECTED,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                    val hasRealSignal = issues.isNotEmpty() || realCrashes.any { it.source == IntegrationType.SENTRY }
                    return if (hasRealSignal) {
                        dao.updateApplicationSdkStatus(app.id, SdkSetupStatus.CONFIGURED)
                        Result.success(true)
                    } else {
                        dao.updateApplicationSdkStatus(app.id, SdkSetupStatus.PENDING)
                        Result.success(false)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (http: HttpException) {
                    val status = if (http.code() == 401 || http.code() == 403) {
                        IntegrationStatus.UNAUTHORIZED
                    } else {
                        IntegrationStatus.ERROR
                    }
                    dao.insertIntegration(sentryIntegration.copy(status = status))
                    dao.updateApplicationSdkStatus(app.id, SdkSetupStatus.ERROR)
                    return Result.failure(IllegalStateException("فشل التحقق عبر Sentry API (HTTP ${http.code()})."))
                } catch (error: Exception) {
                    dao.insertIntegration(sentryIntegration.copy(status = IntegrationStatus.ERROR))
                    dao.updateApplicationSdkStatus(app.id, SdkSetupStatus.ERROR)
                    return Result.failure(IllegalStateException("تعذر الاتصال بخادم Sentry: ${error.message ?: "خطأ شبكة"}"))
                }
            }
        }

        // 2. Check Bugsnag integration if configured for this project
        val bugsnagIntegration = integrations.firstOrNull { it.type == IntegrationType.BUGSNAG }
        if (bugsnagIntegration != null) {
            val config = parseConfig<BugsnagConfig>(bugsnagIntegration.configJson)
            if (config == null || config.token.isBlank() || config.projectId.isBlank()) {
                dao.insertIntegration(bugsnagIntegration.copy(status = IntegrationStatus.ERROR))
                dao.updateApplicationSdkStatus(app.id, SdkSetupStatus.ERROR)
                return Result.failure(IllegalStateException("بيانات ربط Bugsnag غير مكتملة أو غير صالحة."))
            }
            val api = bugsnagApi
            if (api != null) {
                try {
                    val errors = api.getErrors(
                        authToken = "token ${config.token}",
                        projectId = config.projectId
                    )
                    dao.insertIntegration(
                        bugsnagIntegration.copy(
                            status = IntegrationStatus.CONNECTED,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                    val hasRealSignal = errors.isNotEmpty() || realCrashes.any { it.source == IntegrationType.BUGSNAG }
                    return if (hasRealSignal) {
                        dao.updateApplicationSdkStatus(app.id, SdkSetupStatus.CONFIGURED)
                        Result.success(true)
                    } else {
                        dao.updateApplicationSdkStatus(app.id, SdkSetupStatus.PENDING)
                        Result.success(false)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (http: HttpException) {
                    val status = if (http.code() == 401 || http.code() == 403) {
                        IntegrationStatus.UNAUTHORIZED
                    } else {
                        IntegrationStatus.ERROR
                    }
                    dao.insertIntegration(bugsnagIntegration.copy(status = status))
                    dao.updateApplicationSdkStatus(app.id, SdkSetupStatus.ERROR)
                    return Result.failure(IllegalStateException("فشل التحقق عبر Bugsnag API (HTTP ${http.code()})."))
                } catch (error: Exception) {
                    dao.insertIntegration(bugsnagIntegration.copy(status = IntegrationStatus.ERROR))
                    dao.updateApplicationSdkStatus(app.id, SdkSetupStatus.ERROR)
                    return Result.failure(IllegalStateException("تعذر الاتصال بخادم Bugsnag: ${error.message ?: "خطأ شبكة"}"))
                }
            }
        }

        // 3. Check real stored diagnostic events from Webhook / On-Device Watcher / Custom
        val hasRecordedEvent = realCrashes.any {
            it.source == IntegrationType.WEBHOOK ||
                it.source == IntegrationType.ON_DEVICE_WATCHER ||
                it.source == IntegrationType.CUSTOM ||
                it.source == IntegrationType.SENTRY ||
                it.source == IntegrationType.BUGSNAG
        }
        if (hasRecordedEvent) {
            dao.updateApplicationSdkStatus(app.id, SdkSetupStatus.CONFIGURED)
            return Result.success(true)
        }

        // 4. Check on-device Shizuku watcher or active logcat telemetry for this package
        if (app.packageName.isNotBlank() &&
            (transport.isWatcherMonitoring(app.packageName) || transport.hasRecentLogcatActivity(app.packageName))
        ) {
            dao.updateApplicationSdkStatus(app.id, SdkSetupStatus.CONFIGURED)
            return Result.success(true)
        }

        // 5. No configured provider or diagnostic signal detected -> honest failure/pending
        dao.updateApplicationSdkStatus(app.id, SdkSetupStatus.PENDING)
        return Result.failure(
            IllegalStateException(
                "لم يُرصد أي مصدر تشخيص نشط أو حدث حقيقي للتطبيق ${app.name}. اربط Sentry أو Bugsnag أو فعّل مراقب الجهاز عبر Shizuku ثم أعد المحاولة."
            )
        )
    }

    private inline fun <reified T> parseConfig(encryptedOrRawJson: String): T? {
        return try {
            val decrypted = SecurityUtils.decryptSecret(encryptedOrRawJson)
            moshi.adapter(T::class.java).fromJson(decrypted)
        } catch (_: Exception) {
            null
        }
    }
}
