package com.example.presentation.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.model.CrashReport
import com.example.domain.model.DiagnosisCertainty
import com.example.domain.model.ErrorCategory
import com.example.R
import com.example.ui.theme.CrashIntelligenceTheme
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

    private fun appContext(): android.content.Context =
        androidx.test.core.app.ApplicationProvider.getApplicationContext()

}
