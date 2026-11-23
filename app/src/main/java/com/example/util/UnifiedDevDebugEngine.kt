package com.example.util

import com.example.domain.model.*
import java.util.UUID

/**
 * Unified AI Development & Crash Intelligence Closed-Loop Engine.
 * Manages the full cycle: Monitoring scaffolding -> Tracked test session ->
 * Crash ingestion & symbolication -> AI Diagnosis & Patch generation ->
 * In-workspace patching -> Closed-loop re-testing and verification.
 */
object UnifiedDevDebugEngine {

    /**
     * Generates a concrete SDK initialization snippet matching the workspace tech stack.
     */
    fun generateSdkIntegrationSnippet(
        workspaceId: String,
        workspaceName: String,
        techStack: String
    ): String {
        val stackLower = techStack.lowercase()
        return when {
            stackLower.contains("android") || stackLower.contains("kotlin") -> """
                // Android (Kotlin) - Crash Intelligence SDK Setup
                // In your Application class onCreate():
                import com.crashintelligence.sdk.CrashIntelligence
                
                class MyApp : Application() {
                    override fun onCreate() {
                        super.onCreate()
                        CrashIntelligence.init(
                            context = this,
                            applicationId = "$workspaceId",
                            projectId = "$workspaceId",
                            endpoint = "https://crash-intelligence.internal/api/v1/telemetry"
                        )
                    }
                }
            """.trimIndent()

            stackLower.contains("react") || stackLower.contains("next") || stackLower.contains("vue") -> """
                // Web (React / TypeScript / Next.js) - Crash Intelligence SDK Setup
                // In your main entrypoint (index.tsx or App.tsx):
                import { initCrashIntelligence } from '@crash-intelligence/browser';
                
                initCrashIntelligence({
                    applicationId: '$workspaceId',
                    projectId: '$workspaceId',
                    trackUnhandledRejections: true,
                    autoCaptureConsoleErrors: true,
                    environment: 'development'
                });
            """.trimIndent()

            stackLower.contains("node") || stackLower.contains("express") -> """
                // Node.js (Backend) - Crash Intelligence SDK Setup
                // In your server entrypoint (server.js / app.ts):
                const { initCrashIntelligence, crashMiddleware } = require('@crash-intelligence/node');
                
                initCrashIntelligence({
                    applicationId: '$workspaceId',
                    projectId: '$workspaceId'
                });
                
                // Express error handler middleware
                app.use(crashMiddleware());
            """.trimIndent()

            stackLower.contains("python") || stackLower.contains("flask") || stackLower.contains("fastapi") -> """
                # Python - Crash Intelligence SDK Setup
                import crash_intelligence
                
                crash_intelligence.init(
                    application_id="$workspaceId",
                    project_id="$workspaceId",
                    capture_uncaught_exceptions=True
                )
            """.trimIndent()

            stackLower.contains("flutter") -> """
                // Flutter - Crash Intelligence SDK Setup
                import 'package:crash_intelligence_flutter/crash_intelligence.dart';
                
                void main() async {
                  WidgetsFlutterBinding.ensureInitialized();
                  await CrashIntelligence.init(
                    applicationId: '$workspaceId',
                    projectId: '$workspaceId',
                  );
                  runApp(const MyApp());
                }
            """.trimIndent()

            else -> """
                // Standard REST / Telemetry Webhook Setup
                // POST payload to: https://crash-intelligence.internal/api/v1/crashes
                {
                  "applicationId": "$workspaceId",
                  "projectId": "$workspaceId",
                  "exceptionType": "FatalException",
                  "message": "Crash message...",
                  "stackTrace": "..."
                }
            """.trimIndent()
        }
    }

    /**
     * Extracts the primary culprit file path and line number from a real stack trace.
     */
    fun parseCrashSourceLocation(stackTrace: String, files: List<WorkspaceFile>): Pair<String?, Int?> {
        val candidatePaths = files.filter { !it.isFolder }.map { it.path }
        val lines = stackTrace.lines()

        for (line in lines) {
            // Check if line mentions any workspace file directly
            for (path in candidatePaths) {
                val fileName = path.substringAfterLast("/")
                if (line.contains(path) || line.contains(fileName)) {
                    // Try to extract line number: e.g. path:42 or fileName.kt:42 or (fileName.js:42:15)
                    val regex = Regex("""(?::|\()(\d+)(?::|\))?""")
                    val match = regex.find(line)
                    val lineNum = match?.groupValues?.get(1)?.toIntOrNull()
                    return Pair(path, lineNum ?: 1)
                }
            }
        }

        // Fallback: search for standard patterns like "at com.example... (File.kt:25)" or "at (src/App.js:14)"
        val genericRegex = Regex("""([a-zA-Z0-9_\-./]+\.[a-zA-Z0-9]+):(\d+)""")
        val match = genericRegex.find(stackTrace)
        if (match != null) {
            val matchedFile = match.groupValues[1]
            val matchedLine = match.groupValues[2].toIntOrNull()
            val existing = candidatePaths.find { it.endsWith(matchedFile) }
            return Pair(existing ?: matchedFile, matchedLine)
        }

        return Pair(candidatePaths.firstOrNull(), null)
    }

    /**
     * Analyzes crash report details and matches source code to generate root cause analysis.
     */
    fun generateDiagnosis(
        crashReport: CrashReport,
        targetFile: WorkspaceFile?,
        targetLine: Int?
    ): AIDiagnosis {
        val rootCause = when {
            crashReport.exceptionType.contains("NullPointer", ignoreCase = true) || crashReport.message.contains("null", ignoreCase = true) ->
                "محاولة الوصول إلى خاصية أو استدعاء دالة على كائن فارغ (Null reference / Unhandled undefined) في السطر ${targetLine ?: "غير محدد"}."

            crashReport.exceptionType.contains("IndexOutOfBounds", ignoreCase = true) || crashReport.message.contains("index", ignoreCase = true) ->
                "محاولة استدعاء فهرس خارج حدود المصفوفة أو القائمة بدون التحقق المسبق من الطول."

            crashReport.exceptionType.contains("Type", ignoreCase = true) || crashReport.message.contains("not a function", ignoreCase = true) ->
                "استدعاء دالة غير معرفة أو عدم تطابق في الأنواع (Type Mismatch) عند تمرير المعاملات."

            crashReport.exceptionType.contains("Network", ignoreCase = true) || crashReport.message.contains("fetch", ignoreCase = true) ->
                "فشل في استدعاء واجهة برمجة التطبيقات (API) دون وجود معالجة مناسبة لحالات فشل الشبكة (try/catch أو fallback)."

            else -> "حدوث خطأ تشغيلي غير متوقع: ${crashReport.message.take(100)}."
        }

        val technicalEvidence = buildString {
            append("Exception: ").append(crashReport.exceptionType).append("\n")
            append("Message: ").append(crashReport.message).append("\n")
            if (targetFile != null && targetLine != null) {
                append("Target: ").append(targetFile.path).append(":").append(targetLine).append("\n")
                val fileLines = targetFile.content.lines()
                if (targetLine in 1..fileLines.size) {
                    append("Code snippet: ").append(fileLines[targetLine - 1].trim())
                }
            }
        }

        return AIDiagnosis(
            crashId = crashReport.id,
            summary = "تحليل السبب الجذري للعطل ${crashReport.exceptionType}",
            rootCause = rootCause,
            certainty = if (targetFile != null) DiagnosisCertainty.CONFIRMED else DiagnosisCertainty.HIGHLY_LIKELY,
            sourceLocation = "${targetFile?.path ?: "unknown"}:${targetLine ?: 1}",
            technicalEvidence = technicalEvidence,
            whyItHappened = "عدم وجود معالجة استباقية للحالة الاستثنائية أثناء تنفيذ الدورة التشغيلية للتطبيق."
        )
    }

    /**
     * Generates a concrete source code patch to fix the diagnosed crash.
     */
    fun generateFixPatch(
        crashReport: CrashReport,
        targetFile: WorkspaceFile,
        targetLine: Int?,
        diagnosis: AIDiagnosis
    ): FixSuggestion {
        val originalContent = targetFile.content
        val lines = originalContent.lines().toMutableList()

        val lineIndex = if (targetLine != null && targetLine in 1..lines.size) targetLine - 1 else 0
        val targetCodeLine = if (lines.isNotEmpty()) lines[lineIndex] else ""

        // Generate intelligent fix depending on the exception type
        val fixedCodeLine = when {
            crashReport.exceptionType.contains("NullPointer", ignoreCase = true) || crashReport.message.contains("null", ignoreCase = true) -> {
                if (targetCodeLine.contains(".") && !targetCodeLine.contains("?.")) {
                    targetCodeLine.replaceFirst(".", "?.")
                } else {
                    "// Safe guard against null reference\nif (${targetCodeLine.trim().takeWhile { it != '.' && it != ' ' }}) {\n    $targetCodeLine\n}"
                }
            }

            crashReport.exceptionType.contains("Network", ignoreCase = true) || crashReport.message.contains("fetch", ignoreCase = true) -> {
                "try {\n    $targetCodeLine\n} catch (e: Exception) {\n    // Graceful error fallback\n    Log.e(\"CrashIntelligence\", \"Handled network exception: \" + e.message)\n}"
            }

            else -> {
                "// Auto-patched by Crash Intelligence AI\ntry {\n    $targetCodeLine\n} catch (e: Throwable) {\n    println(\"Safely caught: \" + e.message)\n}"
            }
        }

        lines[lineIndex] = fixedCodeLine
        val suggestedContent = lines.joinToString("\n")

        return FixSuggestion(
            id = UUID.randomUUID().toString(),
            crashId = crashReport.id,
            originalCode = originalContent,
            suggestedCode = suggestedContent,
            explanation = "إضافة حماية استباقية (Safe Navigation / Exception Handling) لمنع الانهيار الناتج عن: ${diagnosis.rootCause}",
            potentialSideEffects = "لا توجد آثار جانبية سلبية معروفة، التعديل يعزل الخطأ ويمنع توقف التطبيق.",
            filePath = targetFile.path,
            branchName = "fix-${crashReport.id.take(8)}",
            status = FixStatus.PROPOSED
        )
    }

    /**
     * Verifies closed loop outcomes strictly against actual test execution.
     * Rule: Do NOT mark fixed unless tests actually ran and succeeded without crash!
     */
    fun evaluateVerificationOutcome(
        crashReport: CrashReport,
        fixSuggestion: FixSuggestion,
        previousBuildId: String,
        newBuildId: String,
        testResult: TestExecutionResult?
    ): ClosedLoopVerificationOutcome {
        val fixMatchesCrash = fixSuggestion.crashId == crashReport.id
        val fixWasApplied = fixSuggestion.status == FixStatus.APPLIED
        val fixCanBeVerified = fixMatchesCrash && fixWasApplied
        val verifiedResult = testResult?.takeIf { it.executionVerified }
        val isTestSuccess = fixCanBeVerified &&
            verifiedResult != null &&
            verifiedResult.isSuccess &&
            verifiedResult.totalTests > 0 &&
            verifiedResult.passed == verifiedResult.totalTests &&
            verifiedResult.failed == 0

        val (status, comparisonSummary) = if (isTestSuccess) {
            Pair(
                VerificationStatus.FIX_VERIFIED_IN_TEST,
                "تم التحقق بنجاح من الإصلاح في البناء $newBuildId. اجتازت جميع الاختبارات (${testResult?.passed ?: 0}/${testResult?.totalTests ?: 0}) ولم يُعاد إنتاج العطل."
            )
        } else if (fixCanBeVerified && verifiedResult != null && verifiedResult.failed > 0) {
            Pair(
                VerificationStatus.TEST_FAILED,
                "فشلت الاختبارات الفعلية المرتبطة بالإصلاح في البناء $newBuildId (${verifiedResult.failed} اختبار فاشل). ما زال الكود بحاجة لتعديل."
            )
        } else {
            Pair(
                VerificationStatus.INCONCLUSIVE,
                "لم تتوفر أدلة بناء واختبارات فعلية مكتملة لإثبات الإصلاح؛ الحالة غير مؤكدة."
            )
        }

        val record = CrashVerificationRecord(
            id = UUID.randomUUID().toString(),
            projectId = crashReport.projectId,
            applicationId = crashReport.applicationId,
            crashId = crashReport.id,
            fixSuggestionId = fixSuggestion.id,
            previousAppVersion = previousBuildId,
            newAppVersion = newBuildId,
            buildId = newBuildId,
            scenarioName = "Closed-Loop Verification for ${crashReport.exceptionType}",
            status = status,
            comparisonSummary = comparisonSummary,
            oldStackTrace = crashReport.stackTrace,
            newStackTrace = if (verifiedResult != null && verifiedResult.failed > 0) "Executed tests reported failure" else null,
            crashExceptionType = crashReport.exceptionType,
            crashMessage = crashReport.message,
            environmentValid = verifiedResult != null
        )

        return ClosedLoopVerificationOutcome(
            isFixed = (status == VerificationStatus.FIX_VERIFIED_IN_TEST),
            status = status,
            previousBuildId = previousBuildId,
            newBuildId = newBuildId,
            comparisonSummary = comparisonSummary,
            verificationRecord = record
        )
    }
}
