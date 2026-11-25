package com.example.presentation.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.domain.model.SymbolicationReport
import com.example.domain.model.SymbolicationStatus
import com.example.ui.theme.CrashIntelligenceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ar")
class CrashDetailArabicLocalizationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun symbolicatedTraceHeaderAndEmptyStateUseArabicLabels() {
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

        composeRule.onNodeWithText("التتبع بعد فك الترميز // النظام").assertIsDisplayed()
        composeRule.onNodeWithText("لم تُرجع عملية فك الترميز أي إطارات للمكدس.").assertIsDisplayed()
    }

    @Test
    fun rawStackTraceHeaderUsesArabicLabel() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                StackTraceCard("at com.example.Repository.load(Repository.kt:42)")
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("تتبع المكدس // النظام").assertIsDisplayed()
    }

    @Test
    fun partialSymbolicationBannerUsesArabicDescription() {
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

        composeRule.onNodeWithText("تم تحليل بعض الرموز فقط").assertIsDisplayed()
        composeRule.onNodeWithText(
            "لا تزال بعض الإطارات دون فك رموز؛ وقد احتُفظ بتتبعها الأصلي أدناه."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Some application frames could not be resolved.").assertDoesNotExist()
    }

    @Test
    fun diagnosisBadgesUseArabicLabels() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                Column {
                    CertaintyBadge(com.example.domain.model.DiagnosisCertainty.HIGHLY_LIKELY)
                    CategoryBadge(com.example.domain.model.ErrorCategory.NULL_POINTER_EXCEPTION)
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("مرجّح بدرجة عالية").assertIsDisplayed()
        composeRule.onNodeWithText("استثناء مؤشر فارغ").assertIsDisplayed()
    }
}
