package com.example.presentation.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.model.CrashReport
import com.example.domain.model.DiagnosisCertainty
import com.example.domain.model.ErrorCategory
import com.example.domain.model.SymbolicatedFrame
import com.example.domain.model.SymbolicationReport
import com.example.domain.model.SymbolicationStatus
import com.example.R
import com.example.ui.theme.CrashIntelligenceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config
class CrashDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingReportOffersRetryAndBackActions() {
        var retried = false
        var navigatedBack = false

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                CrashDetailUnavailableState(
                    isLoadError = false,
                    onRetry = { retried = true },
                    onBack = { navigatedBack = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("crash_detail_unavailable_state").assertIsDisplayed()
        composeRule.onNodeWithTag("crash_detail_retry_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("crash_detail_back_button").assertIsDisplayed().performClick()

        assertTrue(retried)
        assertTrue(navigatedBack)
    }

    @Test
    fun loadFailureUsesTheConnectionRecoveryState() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                CrashDetailUnavailableState(
                    isLoadError = true,
                    onRetry = {},
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("crash_detail_unavailable_state").assertIsDisplayed()
        composeRule.onNodeWithTag("crash_detail_retry_button").assertIsDisplayed()
        composeRule.onNodeWithTag("crash_detail_back_button").assertIsDisplayed()
    }
    @Test
    fun thrownSymbolicationFailureKeepsRetryActionVisibleWithoutAReport() {
        var retried = false
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                SymbolicationStatusBanner(
                    report = null,
                    isLoading = false,
                    errorMessage = "Symbolication service unavailable",
                    onRetry = { retried = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("crash_symbolication_retry").assertIsDisplayed().performClick()
        assertTrue(retried)
    }

    @Test
    fun symbolicationLoadingShowsProgressWithoutOfferingRetry() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                SymbolicationStatusBanner(
                    report = null,
                    isLoading = true,
                    errorMessage = null,
                    onRetry = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(appContext().getString(com.example.R.string.crash_detail_symbolication_loading))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("crash_symbolication_retry").assertDoesNotExist()
    }


    @Test
    fun teamInvestigationActionUsesLocalizedResourceAndInvokesCallback() {
        var started = false
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                CrashInvestigationTeamSection(
                    isInvestigating = false,
                    teamExecution = null,
                    onStartInvestigation = { started = true },
                    onViewReport = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(appContext().getString(R.string.crash_team_start))
            .assertIsDisplayed()
            .performClick()
        assertTrue(started)
    }

    @Test
    fun resolvedStatusIsInformationalAndMetadataAdaptsToCompactWidth() {
        val crash = CrashReport(
            id = "crash-compact",
            projectId = "project-1",
            exceptionType = "IllegalStateException",
            message = "The operation could not be completed.",
            deviceModel = "Pixel 9 Pro",
            osVersion = "Android 16",
            appVersion = "2.4.0",
            isResolved = true
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                Box(Modifier.width(320.dp)) {
                    CrashHeader(crash)
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("crash_detail_resolved_status")
            .assertIsDisplayed()
            .assert(!hasClickAction())
        composeRule.onNodeWithTag("crash_header_metadata_compact").assertIsDisplayed()
        composeRule.onNodeWithTag("crash_header_metadata_wide").assertDoesNotExist()
    }

    @Test
    fun missingStackTraceShowsUnavailableStateWithoutCopyAction() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                StackTraceCard("  ")
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("crash_detail_stack_trace_unavailable").assertIsDisplayed()
        composeRule.onNodeWithText(
            appContext().getString(R.string.crash_detail_stack_trace_unavailable)
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            appContext().getString(R.string.crash_detail_copy_stack_trace)
        ).assertDoesNotExist()
    }

    @Test
    fun copyConfirmationResetsWhenStackTraceEvidenceChanges() {
        val stackTrace = mutableStateOf("at Example.first(Example.kt:1)")
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                StackTraceCard(stackTrace.value)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(
            appContext().getString(R.string.crash_detail_copy_stack_trace)
        ).performClick()
        composeRule.onNodeWithContentDescription(
            appContext().getString(R.string.crash_detail_stack_trace_copied)
        ).assertExists()

        composeRule.runOnIdle {
            stackTrace.value = "at Example.second(Example.kt:2)"
        }
        composeRule.onNodeWithContentDescription(
            appContext().getString(R.string.crash_detail_copy_stack_trace)
        ).assertExists()
    }

    @Test
    fun diagnosisBadgesUseLocalizedEnglishLabels() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                Column {
                    CertaintyBadge(DiagnosisCertainty.HIGHLY_LIKELY)
                    CategoryBadge(ErrorCategory.NULL_POINTER_EXCEPTION)
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Highly likely").assertIsDisplayed()
        composeRule.onNodeWithText("Null pointer exception").assertIsDisplayed()
    }

    @Test
    fun symbolicatedMethodIsRenderedWhenSourceFileIsUnavailable() {
        val frame = SymbolicatedFrame(
            originalLine = "at a.b(SourceFile:1)",
            fileName = null,
            methodName = "com.example.Repository.load",
            lineNumber = 42,
            libraryName = "com.example.Repository",
            isSystem = false
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                SymbolicatedFrameItem(frame)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("com.example.Repository.load").assertIsDisplayed()
        composeRule.onNodeWithText("com.example.Repository").assertIsDisplayed()
        composeRule.onNodeWithText("at a.b(SourceFile:1)").assertDoesNotExist()
    }

    @Test
    fun partialSymbolicatedFrameNeverRendersNullTokens() {
        val frame = SymbolicatedFrame(
            originalLine = "at a.b(SourceFile:1)",
            fileName = "Repository.kt",
            methodName = null,
            lineNumber = 42,
            libraryName = null,
            isSystem = false
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                SymbolicatedFrameItem(frame)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("(Repository.kt:42)").assertIsDisplayed()
        composeRule.onNodeWithText("null").assertDoesNotExist()
    }

    @Test
    fun symbolicatedTraceShowsHeaderAndExplicitEmptyState() {
        val report = SymbolicationReport(
            status = SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED,
            frames = emptyList()
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                SymbolicatedStackTrace(report)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("SYMBOLICATED TRACE // SYSTEM").assertIsDisplayed()
        composeRule.onNodeWithText("No stack frames were returned by symbolication.").assertIsDisplayed()
        composeRule.onNodeWithTag("crash_detail_copy_symbolicated_stack_trace_button").assertDoesNotExist()
    }

    @Test
    fun symbolicatedFrameDetailsStayWithinCompactPanelWidth() {
        val method = "com.example.crash.repository.CrashRepositoryImpl.loadCrashDetails"
        val library = "com.example.crash.repository"
        val report = SymbolicationReport(
            status = SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED,
            frames = listOf(
                SymbolicatedFrame(
                    originalLine = "at a.b(SourceFile:1)",
                    fileName = "CrashRepositoryImpl.kt",
                    methodName = method,
                    lineNumber = 128,
                    libraryName = library,
                    isSystem = false
                )
            )
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                Box(Modifier.width(220.dp)) {
                    SymbolicatedStackTrace(report)
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(method).assertIsDisplayed()
        composeRule.onNodeWithText("(CrashRepositoryImpl.kt:128)").assertIsDisplayed()
        composeRule.onNodeWithText(library).assertIsDisplayed()

        val panelBounds = composeRule.onNodeWithTag("symbolicated_frame_content")
            .fetchSemanticsNode().boundsInRoot
        val methodBounds = composeRule.onNodeWithText(method).fetchSemanticsNode().boundsInRoot
        val libraryBounds = composeRule.onNodeWithText(library).fetchSemanticsNode().boundsInRoot
        assertTrue(methodBounds.left >= panelBounds.left - 1f)
        assertTrue(methodBounds.right <= panelBounds.right + 1f)
        assertTrue(libraryBounds.left >= panelBounds.left - 1f)
        assertTrue(libraryBounds.right <= panelBounds.right + 1f)
    }

    @Test
    fun libraryEvidenceIsKeptWhenResolvedMethodAndFileAreMissing() {
        val frame = SymbolicatedFrame(
            originalLine = "at a.b(Unknown Source)",
            fileName = null,
            methodName = null,
            lineNumber = null,
            libraryName = "com.example.crash",
            isSystem = false
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                SymbolicatedFrameItem(frame)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("at a.b(Unknown Source)").assertIsDisplayed()
        composeRule.onNodeWithText("com.example.crash").assertIsDisplayed()
    }

    @Test
    fun partialSymbolicationShowsResolvedAndUnresolvedFrames() {
        val report = SymbolicationReport(
            status = SymbolicationStatus.PARTIALLY_SYMBOLICATED,
            frames = listOf(
                SymbolicatedFrame(
                    originalLine = "at a.b(SourceFile:1)",
                    fileName = "CrashRepositoryImpl.kt",
                    methodName = "com.example.crash.CrashRepositoryImpl.load",
                    lineNumber = 42,
                    libraryName = "com.example.crash",
                    isSystem = false
                ),
                SymbolicatedFrame(
                    originalLine = "at x.y(Unknown Source)",
                    fileName = null,
                    methodName = null,
                    lineNumber = null,
                    libraryName = null,
                    isSystem = false
                )
            ),
            error = "Some application frames could not be resolved."
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                CrashTraceContent(
                    report = report,
                    rawStackTrace = "RAW_TRACE_FALLBACK_SHOULD_NOT_REPLACE_PARTIAL_FRAMES"
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("com.example.crash.CrashRepositoryImpl.load").assertIsDisplayed()
        composeRule.onNodeWithText("at x.y(Unknown Source)").assertIsDisplayed()
        composeRule.onNodeWithText("RAW_TRACE_FALLBACK_SHOULD_NOT_REPLACE_PARTIAL_FRAMES").assertDoesNotExist()
    }

    @Test
    fun failedSymbolicationKeepsOriginalTraceAndCopyAction() {
        val report = SymbolicationReport(
            status = SymbolicationStatus.PROCESSING_FAILED,
            frames = emptyList(),
            error = "No application frames were resolved."
        )
        val originalTrace = "at com.example.crash.Repository.load(Repository.kt:42)"

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                CrashTraceContent(report = report, rawStackTrace = originalTrace)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(originalTrace).assertIsDisplayed()
        composeRule.onNodeWithTag("crash_detail_copy_stack_trace_button").assertIsDisplayed()
        composeRule.onNodeWithText("SYMBOLICATED TRACE // SYSTEM").assertDoesNotExist()
    }

    @Test
    fun symbolicatedTraceCopyConfirmationResetsWhenFramesChange() {
        val report = mutableStateOf(
            SymbolicationReport(
                status = SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED,
                frames = listOf(
                    SymbolicatedFrame(
                        originalLine = "at a.b(SourceFile:1)",
                        fileName = "Repository.kt",
                        methodName = "com.example.Repository.first",
                        lineNumber = 1,
                        libraryName = "com.example",
                        isSystem = false
                    )
                )
            )
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                SymbolicatedStackTrace(report.value)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(
            appContext().getString(R.string.crash_detail_copy_stack_trace)
        ).performClick()
        composeRule.onNodeWithContentDescription(
            appContext().getString(R.string.crash_detail_stack_trace_copied)
        ).assertExists()

        composeRule.runOnIdle {
            report.value = SymbolicationReport(
                status = SymbolicationStatus.PARTIALLY_SYMBOLICATED,
                frames = listOf(
                    SymbolicatedFrame(
                        originalLine = "at a.b(SourceFile:2)",
                        fileName = "Repository.kt",
                        methodName = "com.example.Repository.second",
                        lineNumber = 2,
                        libraryName = "com.example",
                        isSystem = false
                    )
                )
            )
        }

        composeRule.onNodeWithContentDescription(
            appContext().getString(R.string.crash_detail_copy_stack_trace)
        ).assertExists()
        composeRule.onNodeWithTag("crash_detail_copy_symbolicated_stack_trace_button").assertIsDisplayed()
    }

    @Test
    fun partialSymbolicationBannerExplainsUnresolvedFramesWithoutRawEnglishError() {
        val report = SymbolicationReport(
            status = SymbolicationStatus.PARTIALLY_SYMBOLICATED,
            frames = emptyList(),
            error = "Some application frames could not be resolved."
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                SymbolicationStatusBanner(
                    report = report,
                    isLoading = false,
                    errorMessage = null,
                    onRetry = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Partially symbolicated").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Some frames remain unresolved; the original trace is preserved below."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Some application frames could not be resolved.").assertDoesNotExist()
    }

    @Test
    fun symbolicatedTraceCopiesResolvedAndOriginalFrameEvidence() {
        val clipboard = RecordingClipboardManager()
        val report = SymbolicationReport(
            status = SymbolicationStatus.PARTIALLY_SYMBOLICATED,
            frames = listOf(
                SymbolicatedFrame(
                    originalLine = "at a.b(SourceFile:1)",
                    fileName = "Repository.kt",
                    methodName = "com.example.Repository.load",
                    lineNumber = 42,
                    libraryName = "com.example",
                    isSystem = false
                ),
                SymbolicatedFrame(
                    originalLine = "at x.y(Unknown Source)",
                    fileName = null,
                    methodName = null,
                    lineNumber = null,
                    libraryName = null,
                    isSystem = false
                ),
                SymbolicatedFrame(
                    originalLine = "at m.n(Unknown Source)",
                    fileName = null,
                    methodName = null,
                    lineNumber = null,
                    libraryName = "com.example.unresolved",
                    isSystem = false
                )
            )
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                CrashIntelligenceTheme(darkTheme = true) {
                    SymbolicatedStackTrace(report)
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("crash_detail_copy_symbolicated_stack_trace_button").performClick()

        assertEquals(
            "com.example.Repository.load (Repository.kt:42) com.example\n" +
                "at x.y(Unknown Source)\n" +
                "at m.n(Unknown Source) com.example.unresolved",
            clipboard.copiedText?.text
        )
    }

    private fun appContext(): android.content.Context =
        androidx.test.core.app.ApplicationProvider.getApplicationContext()

}

private class RecordingClipboardManager : ClipboardManager {
    var copiedText: AnnotatedString? = null

    override fun getText(): AnnotatedString? = copiedText

    override fun setText(annotatedString: AnnotatedString) {
        copiedText = annotatedString
    }
}
