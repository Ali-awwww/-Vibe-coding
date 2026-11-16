package com.example.presentation.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.domain.model.AICapability
import com.example.ui.theme.CrashIntelligenceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TeamDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unavailableTeamStateShowsRetryAndInvokesIt() {
        var retryCount = 0
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                TeamDetailStatePanel(
                    title = "الفريق غير متاح",
                    message = "تعذر تحميل بيانات الفريق.",
                    onRetry = { retryCount++ }
                )
            }
        }

        composeRule.onNodeWithTag("team_detail_unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("تعذر تحميل بيانات الفريق.").assertIsDisplayed()
        composeRule.onNodeWithText("إعادة المحاولة").performClick()

        assertEquals(1, retryCount)
    }

    @Test
    fun signedOutTeamStateDoesNotOfferRetryAction() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                TeamDetailStatePanel(
                    title = "يلزم تسجيل الدخول",
                    message = "يجب تسجيل الدخول بحساب صالح لعرض الفريق أو تعديله.",
                    onRetry = null
                )
            }
        }

        composeRule.onNodeWithTag("team_detail_unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("يلزم تسجيل الدخول").assertIsDisplayed()
        composeRule.onNodeWithText("إعادة المحاولة").assertDoesNotExist()
    }

    @Test
    fun memberCardShowsConfiguredModelInstructionsAndRemovalAction() {
        var removeRequested = false
        val member = com.example.domain.model.TeamMember(
            id = "member-1",
            name = "Reviewer",
            modelId = "openrouter/free-model",
            providerId = "openrouter",
            instructions = "Review every proposed diff."
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                MemberStatusCard(
                    member = member,
                    capabilities = listOf(AICapability.CODING, AICapability.VISION),
                    onRemove = { removeRequested = true }
                )
            }
        }

        composeRule.onNodeWithText("النموذج: openrouter/free-model").assertIsDisplayed()
        composeRule.onNodeWithText("المزود: openrouter").assertIsDisplayed()
        composeRule.onNodeWithText("التعليمات: Review every proposed diff.").assertIsDisplayed()
        composeRule.onNodeWithText("القدرات: برمجة • رؤية").assertIsDisplayed()
        composeRule.onNodeWithTag("team_detail_remove_member_member-1").performClick()
        assertTrue(removeRequested)
    }

}
