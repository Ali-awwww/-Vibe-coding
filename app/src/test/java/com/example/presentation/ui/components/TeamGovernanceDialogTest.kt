package com.example.presentation.ui.components

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.domain.model.TeamGovernanceSettings
import com.example.ui.theme.CrashIntelligenceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TeamGovernanceDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun invalidLimitsAreRejectedWithoutCallingSave() {
        var saved: TeamGovernanceSettings? = null
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                TeamGovernanceDialog(
                    currentSettings = TeamGovernanceSettings(),
                    onSave = { saved = it },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("team_governance_max_tokens")
            .performScrollTo()
            .performTextClearance()
            .performTextInput("0")
        composeRule.onNodeWithText("حفظ السياسات").performClick()

        composeRule.onNodeWithTag("team_governance_validation_error").assertIsDisplayed()
        assertNull(saved)
    }

    @Test
    fun savingStateDisablesConfirmationAndDismissal() {
        var dismissed = false
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                TeamGovernanceDialog(
                    currentSettings = TeamGovernanceSettings(),
                    onSave = {},
                    onDismiss = { dismissed = true },
                    isSaving = true
                )
            }
        }

        composeRule.onNodeWithTag("team_governance_save_button").assertIsNotEnabled()
        composeRule.onNodeWithText("إلغاء").assertIsNotEnabled()
        assertFalse(dismissed)
    }

    @Test
    fun validLimitsArePassedToSave() {
        var saved: TeamGovernanceSettings? = null
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                TeamGovernanceDialog(
                    currentSettings = TeamGovernanceSettings(),
                    onSave = { saved = it },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("team_governance_max_tokens")
            .performScrollTo()
            .performTextClearance()
            .performTextInput("200000")
        composeRule.onNodeWithText("حفظ السياسات").performClick()

        assertEquals(200000, saved?.maxTotalTokens)
    }
}
