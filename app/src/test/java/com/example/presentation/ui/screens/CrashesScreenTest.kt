package com.example.presentation.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.domain.model.CrashReport
import com.example.domain.model.IntegrationType
import com.example.domain.model.Severity
import com.example.ui.theme.CrashIntelligenceTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config
class CrashesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingSourceUsesOneExplicitUnavailableLabel() {
        val crash = CrashReport(
            id = "missing-source",
            projectId = "project-1",
            exceptionType = "IllegalStateException",
            source = null
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                DetailedCrashCard(crash) {}
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Source unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Source: Source unavailable").assertDoesNotExist()
    }

    @Test
    fun detailedCrashCardWrapsMetadataInCompactLtrAndRtlAndRemainsClickable() {
        val layoutDirection = mutableStateOf(LayoutDirection.Ltr)
        var clicked = false
        val crash = CrashReport(
            id = "compact-crash",
            projectId = "project-1",
            applicationId = "app-1",
            exceptionType = "com.example.very.long.exception.IllegalStateException",
            message = "The repository operation could not be completed because the remote endpoint did not respond.",
            deviceModel = "Pixel 9 Pro with a long hardware revision identifier 2026",
            osVersion = "Android 16 with the November security patch",
            appVersion = "2.4.0-beta+20261126.123456",
            severity = Severity.HIGH,
            source = IntegrationType.SENTRY
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection.value) {
                CrashIntelligenceTheme(darkTheme = true) {
                    Box(Modifier.width(260.dp)) {
                        DetailedCrashCard(crash) { clicked = true }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        fun assertMetadataWithinCard() {
            val cardBounds = composeRule.onNodeWithTag("crashes_detailed_card")
                .fetchSemanticsNode().boundsInRoot
            val metadataBounds = composeRule.onNodeWithTag("crashes_card_metadata")
                .fetchSemanticsNode().boundsInRoot
            assertTrue(metadataBounds.left >= cardBounds.left - 1f)
            assertTrue(metadataBounds.right <= cardBounds.right + 1f)
        }

        composeRule.onNodeWithTag("crashes_detailed_card").assertIsDisplayed()
        assertMetadataWithinCard()

        composeRule.runOnIdle { layoutDirection.value = LayoutDirection.Rtl }
        composeRule.waitForIdle()
        assertMetadataWithinCard()

        composeRule.onNodeWithTag("crashes_detailed_card").performClick()
        assertTrue(clicked)
    }
}
