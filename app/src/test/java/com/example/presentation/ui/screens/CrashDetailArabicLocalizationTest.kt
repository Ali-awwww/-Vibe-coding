package com.example.presentation.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
