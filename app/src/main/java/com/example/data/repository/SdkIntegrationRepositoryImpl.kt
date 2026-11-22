package com.example.data.repository

import com.example.data.local.CrashDao
import com.example.domain.model.*
import com.example.domain.repository.SdkIntegrationRepository

class SdkIntegrationRepositoryImpl(
    private val crashDao: CrashDao? = null
) : SdkIntegrationRepository {

    // No authenticated diagnostic-event receiver is configured in this build.
    override val isVerificationTransportAvailable: Boolean = false

    override suspend fun detectTechnology(fileNames: List<String>): ProjectScanResult {
        val files = fileNames.map { it.lowercase() }
        
        val tech = when {
            files.any { it.contains("pubspec.yaml") } -> ProjectTechnology.FLUTTER
            files.any { it.contains("package.json") } && files.any { it.contains("android") || it.contains("ios") } -> ProjectTechnology.REACT_NATIVE
            files.any { it.contains("assets/pacakge") || it.contains("projectsettings") } -> ProjectTechnology.UNITY
            files.any { it.endsWith(".swift") } -> ProjectTechnology.IOS_SWIFT
            files.any { it.endsWith(".m") || it.endsWith(".h") } -> ProjectTechnology.IOS_OBJC
            files.any { it.endsWith(".kt") } || files.any { it.contains("build.gradle.kts") } -> ProjectTechnology.ANDROID_KOTLIN
            files.any { it.endsWith(".java") } || files.any { it.contains("build.gradle") } -> ProjectTechnology.ANDROID_JAVA
            else -> ProjectTechnology.UNKNOWN
        }

        return ProjectScanResult(
            tech = tech,
            detectedFiles = fileNames.filter { f -> 
                listOf("gradle", "yaml", "json", "swift", "kt", "java", "plist", "xml").any { f.endsWith(it) }
            }.take(5)
        )
    }

    override suspend fun getSetupGuide(
        tech: ProjectTechnology,
        service: IntegrationType,
        projectId: String
    ): SdkSetupGuide {
        return when (service) {
            IntegrationType.FIREBASE_CRASHLYTICS -> getFirebaseGuide(tech)
            IntegrationType.SENTRY -> getSentryGuide(tech)
            IntegrationType.BUGSNAG -> getBugsnagGuide(tech)
            else -> getWebhookGuide(tech)
        }
    }

    private fun getFirebaseGuide(tech: ProjectTechnology): SdkSetupGuide =
        SdkSetupGuide(
            tech = tech,
            service = IntegrationType.FIREBASE_CRASHLYTICS,
            steps = emptyList(),
            troubleshooting = emptyList()
        )

    private fun getSentryGuide(tech: ProjectTechnology): SdkSetupGuide {
        val steps = mutableListOf<IntegrationStep>()
        steps.add(IntegrationStep(
            "تثبيت Sentry SDK",
            "أضف الاعتمادية إلى مشروعك",
            if (tech == ProjectTechnology.ANDROID_KOTLIN) "implementation(\"io.sentry:sentry-android:7.3.0\")" else "npm install @sentry/react-native",
            "build.gradle / package.json"
        ))
        steps.add(IntegrationStep(
            "تهيئة Sentry",
            "أضف كود التهيئة في نقطة انطلاق التطبيق",
            "Sentry.init { options -> \n  options.dsn = \"YOUR_SENTRY_DSN\"\n  options.tracesSampleRate = 1.0\n}",
            "MainActivity.kt / App.js"
        ))
        
        return SdkSetupGuide(
            tech = tech,
            service = IntegrationType.SENTRY,
            steps = steps,
            troubleshooting = listOf("تأكد من صحة الـ DSN الخاص بمشروعك في Sentry.")
        )
    }

    private fun getBugsnagGuide(tech: ProjectTechnology): SdkSetupGuide {
        return SdkSetupGuide(
            tech = tech,
            service = IntegrationType.BUGSNAG,
            steps = listOf(
                IntegrationStep("تثبيت Bugsnag", "أضف Bugsnag إلى اعتماديات المشروع", "implementation 'com.bugsnag:bugsnag-android:6.0.0'", "build.gradle"),
                IntegrationStep("بدء Bugsnag", "قم باستدعاء Bugsnag.start()", "Bugsnag.start(this)", "Application class")
            ),
            troubleshooting = listOf("تأكد من إضافة الـ API Key في AndroidManifest.xml.")
        )
    }

    private fun getWebhookGuide(tech: ProjectTechnology): SdkSetupGuide {
        return SdkSetupGuide(
            tech = tech,
            service = IntegrationType.WEBHOOK,
            steps = listOf(
                IntegrationStep("إرسال طلب POST", "أرسل تقرير الانهيار بصيغة JSON إلى رابط الـ Webhook", "curl -X POST https://api.crash-intelligence.ai/webhook/your-id -d '{\"error\": \"NullPointer\"}'", "Network Client")
            ),
            troubleshooting = listOf("تأكد من تضمين التوقيع (Signature) إذا تم تفعيل الأمان.")
        )
    }

    override suspend fun verifyIntegration(applicationId: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException(
            "SDK verification is unavailable because no authenticated diagnostic-event receiver is configured."
        ))
}
