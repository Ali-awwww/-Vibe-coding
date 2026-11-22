package com.example.presentation.ui.screens

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.ui.theme.CrashIntelligenceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config
class GitHubConnectScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun connectionCardUsesFineGrainedPermissionsAndDoesNotOfferFakeOAuth() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) { GitHubLoginCard(false, null, {}) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(appContext().getString(R.string.github_connect_title)).assertIsDisplayed()
        composeRule.onNodeWithText(appContext().getString(R.string.github_token_permissions)).assertIsDisplayed()
        composeRule.onNodeWithText(appContext().getString(R.string.github_token_storage_notice)).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("OAuth Login").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("repo, workflow").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun tokenVisibilityControlHasLocalizedAccessibleLabels() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) { GitHubLoginCard(false, null, {}) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(appContext().getString(R.string.github_token_show)).performClick()
        composeRule.onNodeWithContentDescription(appContext().getString(R.string.github_token_hide)).assertIsDisplayed()
    }

    @Test
    fun tokenSubmitTrimsWhitespaceBeforeInvokingConnection() {
        var submittedToken: String? = null
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) { GitHubLoginCard(isLoading = false, errorResId = null, onConnectToken = { submittedToken = it }) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("github_token_field").performTextInput("  github_pat_test_token  ")
        composeRule.onNodeWithTag("github_token_submit").performClick()
        composeRule.waitForIdle()
        assertEquals("github_pat_test_token", submittedToken)
    }

    @Test
    fun connectionCardRendersEnglishAndArabicResourcesInOneComposition() {
        val base = appContext()
        val english = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) })
        val arabic = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale("ar")) })
        var showArabic by mutableStateOf(false)
        composeRule.setContent {
            val context = if (showArabic) arabic else english
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
                LocalLayoutDirection provides if (showArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                CrashIntelligenceTheme(darkTheme = true) { GitHubLoginCard(false, null, {}) }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(english.getString(R.string.github_connect_title)).assertIsDisplayed()
        composeRule.runOnIdle { showArabic = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(arabic.getString(R.string.github_connect_title)).assertIsDisplayed()
    }

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()
}
