package com.example.data.repository

import com.example.data.remote.BugsnagApp
import com.example.data.remote.BugsnagDevice
import com.example.data.remote.BugsnagError
import com.example.data.remote.BugsnagEvent
import com.example.data.remote.BugsnagException
import com.example.data.remote.BugsnagStackframe
import com.example.data.remote.SentryContexts
import com.example.data.remote.SentryDevice
import com.example.data.remote.SentryEntry
import com.example.data.remote.SentryEntryData
import com.example.data.remote.SentryEvent
import com.example.data.remote.SentryFrame
import com.example.data.remote.SentryIssue
import com.example.data.remote.SentryMetadata
import com.example.data.remote.SentryOs
import com.example.data.remote.SentryStacktrace
import com.example.data.remote.SentryStacktraceValue
import com.example.domain.model.IntegrationType
import com.example.domain.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Test

class CrashReportSourceMapperTest {
    @Test
    fun sentryMissingMetadataAndFramesStayEmptyInsteadOfPlaceholderText() {
        val report = CrashReportSourceMapper.fromSentry(
            projectId = "project-1",
            issue = sentryIssue(metadata = null),
            event = sentryEvent(),
            timestamp = 123L,
            severity = Severity.HIGH
        )

        assertEquals("project-1", report.projectId)
        assertEquals("", report.exceptionType)
        assertEquals("", report.message)
        assertEquals("", report.stackTrace)
        assertEquals("", report.appVersion)
        assertEquals("", report.osVersion)
        assertEquals("", report.deviceModel)
        assertEquals(IntegrationType.SENTRY, report.source)
        assertEquals(123L, report.timestamp)
    }

    @Test
    fun sentryMapsOnlyAvailableFrameFieldsAndScrubsProviderMessage() {
        val frames = listOf(
            SentryFrame("load", "MainActivity.kt", 42, null, null),
            SentryFrame(null, "Worker.kt", null, null, null),
            SentryFrame(null, null, 99, null, null)
        )
        val report = CrashReportSourceMapper.fromSentry(
            projectId = "project-1",
            issue = sentryIssue(SentryMetadata(type = "IllegalStateException", value = null)),
            event = sentryEvent(
                message = "Failure for alice@example.com",
                release = "2.4.0",
                contexts = SentryContexts(
                    os = SentryOs(name = "Android", version = "16"),
                    device = SentryDevice(model = "Pixel 9", arch = "arm64")
                ),
                entries = listOf(
                    SentryEntry(
                        type = "stacktrace",
                        data = SentryEntryData(
                            values = listOf(SentryStacktraceValue(SentryStacktrace(frames)))
                        )
                    )
                )
            ),
            timestamp = 456L,
            severity = Severity.CRITICAL
        )

        assertEquals("IllegalStateException", report.exceptionType)
        assertEquals("Failure for [REDACTED_EMAIL]", report.message)
        assertEquals("load (MainActivity.kt:42)\nWorker.kt", report.stackTrace)
        assertEquals("2.4.0", report.appVersion)
        assertEquals("16", report.osVersion)
        assertEquals("Pixel 9", report.deviceModel)
        assertEquals(IntegrationType.SENTRY, report.source)
    }

    @Test
    fun bugsnagMissingEventMetadataAndFramesStayEmpty() {
        val report = CrashReportSourceMapper.fromBugsnag(
            projectId = "project-2",
            error = bugsnagError(),
            event = bugsnagEvent(),
            timestamp = 789L,
            severity = Severity.MEDIUM
        )

        assertEquals("", report.exceptionType)
        assertEquals("", report.message)
        assertEquals("", report.stackTrace)
        assertEquals("", report.appVersion)
        assertEquals("", report.osVersion)
        assertEquals("", report.deviceModel)
        assertEquals(IntegrationType.BUGSNAG, report.source)
    }

    @Test
    fun bugsnagUsesErrorFallbackAndFormatsPartialFramesWithoutNullTokens() {
        val report = CrashReportSourceMapper.fromBugsnag(
            projectId = "project-2",
            error = bugsnagError(
                errorClass = "NetworkError",
                message = "Request failed for bob@example.com"
            ),
            event = bugsnagEvent(
                exceptions = listOf(
                    BugsnagException(
                        errorClass = "",
                        message = null,
                        stacktrace = listOf(
                            BugsnagStackframe("Repository.kt", 18, "load", true),
                            BugsnagStackframe(null, null, "dispatch", false)
                        )
                    )
                ),
                app = BugsnagApp(version = "3.1.0"),
                device = BugsnagDevice(model = "Galaxy S", osVersion = "Android 15")
            ),
            timestamp = 987L,
            severity = Severity.HIGH
        )

        assertEquals("NetworkError", report.exceptionType)
        assertEquals("Request failed for [REDACTED_EMAIL]", report.message)
        assertEquals("load (Repository.kt:18)\ndispatch", report.stackTrace)
        assertEquals("3.1.0", report.appVersion)
        assertEquals("Android 15", report.osVersion)
        assertEquals("Galaxy S", report.deviceModel)
        assertEquals(IntegrationType.BUGSNAG, report.source)
    }

    private fun sentryIssue(metadata: SentryMetadata?) = SentryIssue(
        id = "sentry-issue",
        title = "Issue title",
        culprit = "MainActivity",
        level = "error",
        status = "unresolved",
        firstSeen = "2026-11-25T00:00:00Z",
        lastSeen = "2026-11-25T00:01:00Z",
        count = "2",
        userCount = 1,
        permalink = null,
        metadata = metadata
    )

    private fun sentryEvent(
        message: String = "",
        release: String? = null,
        contexts: SentryContexts? = null,
        entries: List<SentryEntry>? = null
    ) = SentryEvent(
        id = "sentry-event",
        eventID = "sentry-event",
        title = "Event title",
        message = message,
        dateCreated = "2026-11-25T00:01:00Z",
        release = release,
        contexts = contexts,
        entries = entries
    )

    private fun bugsnagError(
        errorClass: String = "",
        message: String = ""
    ) = BugsnagError(
        id = "bugsnag-error",
        error_class = errorClass,
        message = message,
        context = null,
        last_message = null,
        first_seen = "2026-11-25T00:00:00Z",
        last_seen = "2026-11-25T00:01:00Z",
        events = 1,
        users = 1,
        status = "open",
        severity = "error"
    )

    private fun bugsnagEvent(
        exceptions: List<BugsnagException>? = null,
        app: BugsnagApp? = null,
        device: BugsnagDevice? = null
    ) = BugsnagEvent(
        id = "bugsnag-event",
        received_at = "2026-11-25T00:01:00Z",
        exceptions = exceptions,
        app = app,
        device = device
    )
}
