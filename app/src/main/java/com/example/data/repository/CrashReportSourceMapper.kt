package com.example.data.repository

import com.example.data.remote.BugsnagError
import com.example.data.remote.BugsnagEvent
import com.example.data.remote.SentryEvent
import com.example.data.remote.SentryIssue
import com.example.domain.model.CrashReport
import com.example.domain.model.IntegrationType
import com.example.domain.model.Severity
import com.example.util.SecurityUtils

/**
 * Converts provider payloads into crash records without inventing evidence for missing fields.
 * Keep source provenance and PII scrubbing at this boundary.
 */
internal object CrashReportSourceMapper {
    fun fromSentry(
        projectId: String,
        issue: SentryIssue,
        event: SentryEvent,
        timestamp: Long,
        severity: Severity
    ): CrashReport {
        val frames = event.entries
            ?.firstOrNull { it.type == "stacktrace" }
            ?.data
            ?.values
            ?.firstOrNull()
            ?.stacktrace
            ?.frames
            .orEmpty()
        val stackTrace = frames
            .mapNotNull { frame -> formatFrame(frame.function, frame.filename, frame.lineNo) }
            .joinToString("\n")

        return CrashReport(
            id = event.id,
            projectId = projectId,
            exceptionType = issue.metadata?.type.normalized(),
            message = SecurityUtils.PiiScrubber.scrub(event.message.normalized()),
            stackTrace = SecurityUtils.PiiScrubber.scrub(stackTrace),
            timestamp = timestamp,
            severity = severity,
            source = IntegrationType.SENTRY,
            appVersion = event.release.normalized(),
            osVersion = event.contexts?.os?.version.normalized(),
            deviceModel = event.contexts?.device?.model.normalized()
        )
    }

    fun fromBugsnag(
        projectId: String,
        error: BugsnagError,
        event: BugsnagEvent,
        timestamp: Long,
        severity: Severity
    ): CrashReport {
        val exception = event.exceptions?.firstOrNull()
        val stackTrace = exception?.stacktrace
            .orEmpty()
            .mapNotNull { frame -> formatFrame(frame.method, frame.file, frame.lineNumber) }
            .joinToString("\n")

        return CrashReport(
            id = event.id,
            projectId = projectId,
            exceptionType = exception?.errorClass.normalized().ifEmpty { error.error_class.normalized() },
            message = SecurityUtils.PiiScrubber.scrub(
                exception?.message.normalized().ifEmpty { error.message.normalized() }
            ),
            stackTrace = SecurityUtils.PiiScrubber.scrub(stackTrace),
            timestamp = timestamp,
            severity = severity,
            source = IntegrationType.BUGSNAG,
            appVersion = event.app?.version.normalized(),
            osVersion = event.device?.osVersion.normalized(),
            deviceModel = event.device?.model.normalized()
        )
    }

    private fun formatFrame(methodName: String?, fileName: String?, lineNumber: Int?): String? {
        val method = methodName.normalizedOrNull()
        val file = fileName.normalizedOrNull()
        val location = file?.let { path ->
            lineNumber?.takeIf { it > 0 }?.let { "$path:$it" } ?: path
        }

        return when {
            method != null && location != null -> "$method ($location)"
            method != null -> method
            location != null -> location
            else -> null
        }
    }

    private fun String?.normalized(): String = this?.trim().orEmpty()

    private fun String?.normalizedOrNull(): String? =
        normalized().takeIf { it.isNotEmpty() }
}
