package com.example.data.repository

import com.example.data.local.CrashDao
import com.example.domain.model.*
import com.example.domain.repository.SdkIntegrationRepository

class SdkIntegrationRepositoryImpl(
    @Suppress("unused") private val crashDao: CrashDao? = null
) : SdkIntegrationRepository {

    // A real authenticated diagnostic-event receiver is not configured in this build.
    override val isVerificationTransportAvailable: Boolean = false

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
                    description = "Open the linked Sentry project and confirm that the test event appears. This external check is not a verified Vibe Coding integration until the authenticated receiver is available.",
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

    override suspend fun verifyIntegration(applicationId: String): Result<Boolean> =
        Result.failure(
            UnsupportedOperationException(
                "SDK verification is unavailable because no authenticated diagnostic-event receiver is configured."
            )
        )
}
