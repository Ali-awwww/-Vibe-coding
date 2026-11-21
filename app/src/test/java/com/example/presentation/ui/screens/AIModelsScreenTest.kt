package com.example.presentation.ui.screens

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.R
import com.example.domain.model.*
import com.example.presentation.viewmodel.AIModelsUiState
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
class AIModelsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dashboardDisclosesUnpricedUsageInsteadOfShowingAnUnqualifiedTotal() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                AIModelsScreenContent(
                    uiState = AIModelsUiState(isAuthenticated = true, hasUnpricedUsage = true),
                    onNavigateBack = {},
                    onAddProvider = { _, _, _, _ -> },
                    onDeleteProvider = {},
                    onSyncProvider = {},
                    onTestProvider = {},
                    onSelectDefaultModel = {},
                    onToggleFreeOnly = {},
                    onToggleAutoSelect = {},
                    onRefresh = {},
                    onClearFeedback = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_models_unpriced_usage_warning").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_models_unpriced_usage_warning_message")
            .assertTextContains(
                appContext().getString(R.string.ai_models_unpriced_usage_warning),
                substring = true
            )
    }

    @Test
    fun unauthenticatedStateDisplaysAuthRequiredGateAndDisablesAddButton() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                AIModelsScreenContent(
                    uiState = AIModelsUiState(isAuthenticated = false, isLoading = false),
                    onNavigateBack = {},
                    onAddProvider = { _, _, _, _ -> },
                    onDeleteProvider = {},
                    onSyncProvider = {},
                    onTestProvider = {},
                    onSelectDefaultModel = {},
                    onToggleFreeOnly = {},
                    onToggleAutoSelect = {},
                    onRefresh = {},
                    onClearFeedback = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_models_auth_required_card").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_models_add_provider_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("ai_models_dashboard_card").assertDoesNotExist()
    }

    @Test
    fun authenticatedStateDisplaysProvidersTruthfulBadgesAndDisablesUnsupportedActions() {
        val supportedProvider = AIProvider(
            id = "or-1",
            userId = "user-1",
            name = "Primary OpenRouter",
            type = AIProviderType.OPEN_ROUTER,
            apiKey = "sk-or-v1-1234567890abcdef",
            status = AIProviderStatus.CONNECTED
        )
        val unsupportedProvider = AIProvider(
            id = "hf-1",
            userId = "user-1",
            name = "Legacy HF",
            type = AIProviderType.HUGGING_FACE,
            apiKey = "hf_1234567890abcdef",
            status = AIProviderStatus.CONNECTED // Stale DB state must render UNSUPPORTED
        )
        val freeModel = AIModel(
            id = "or-1:openrouter/free",
            providerId = "or-1",
            apiId = "openrouter/free",
            name = "OpenRouter Auto Free",
            isFree = true,
            contextWindow = 128_000,
            inputPricePer1M = 0.0,
            outputPricePer1M = 0.0
        )

        var deletedId: String? = null
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                AIModelsScreenContent(
                    uiState = AIModelsUiState(
                        isAuthenticated = true,
                        providers = listOf(supportedProvider, unsupportedProvider),
                        models = listOf(freeModel),
                        defaultModelId = freeModel.id,
                        freeOnly = true,
                        autoSelectFree = false,
                        successCount = 4,
                        failedCount = 1,
                        totalCost = 0.0125
                    ),
                    onNavigateBack = {},
                    onAddProvider = { _, _, _, _ -> },
                    onDeleteProvider = { deletedId = it },
                    onSyncProvider = {},
                    onTestProvider = {},
                    onSelectDefaultModel = {},
                    onToggleFreeOnly = {},
                    onToggleAutoSelect = {},
                    onRefresh = {},
                    onClearFeedback = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_models_dashboard_card").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_models_add_provider_button").assertIsEnabled()

        // Scroll LazyColumn items into composition and assert supported vs unsupported statuses and controls
        composeRule.onNodeWithTag("ai_models_lazy_column")
            .performScrollToNode(hasTestTag("ai_provider_card_or-1"))
        composeRule.onNodeWithTag("ai_provider_card_or-1").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_provider_status_or-1").assertTextContains(appContext().getString(R.string.ai_models_status_connected))
        composeRule.onNodeWithTag("ai_provider_test_or-1").assertIsEnabled()
        composeRule.onNodeWithTag("ai_provider_sync_or-1").assertIsEnabled()

        composeRule.onNodeWithTag("ai_models_lazy_column")
            .performScrollToNode(hasTestTag("ai_provider_card_hf-1"))
        composeRule.onNodeWithTag("ai_provider_card_hf-1").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_provider_status_hf-1").assertTextContains(appContext().getString(R.string.ai_models_status_unsupported))
        composeRule.onNodeWithTag("ai_provider_test_hf-1").assertIsNotEnabled()
        composeRule.onNodeWithTag("ai_provider_sync_hf-1").assertIsNotEnabled()

        // Delete button remains enabled and asks for confirmation
        composeRule.onNodeWithTag("ai_models_lazy_column")
            .performScrollToNode(hasTestTag("ai_provider_delete_hf-1"))
        composeRule.onNodeWithTag("ai_provider_delete_hf-1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("delete_provider_confirm_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals("hf-1", deletedId)
    }

    @Test
    fun addProviderDialogValidatesAndSubmitsTrimmedCredentials() {
        var submittedName = ""
        var submittedType: AIProviderType? = null
        var submittedKey = ""

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                AIModelsScreenContent(
                    uiState = AIModelsUiState(isAuthenticated = true),
                    onNavigateBack = {},
                    onAddProvider = { name, type, key, _ ->
                        submittedName = name
                        submittedType = type
                        submittedKey = key
                    },
                    onDeleteProvider = {},
                    onSyncProvider = {},
                    onTestProvider = {},
                    onSelectDefaultModel = {},
                    onToggleFreeOnly = {},
                    onToggleAutoSelect = {},
                    onRefresh = {},
                    onClearFeedback = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_models_add_provider_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("add_provider_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("add_provider_save_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("add_provider_name_input").performTextInput("  My OpenRouter  ")
        composeRule.onNodeWithTag("add_provider_key_input").performTextInput("  sk-or-test-key  ")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("add_provider_save_button").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        assertEquals("My OpenRouter", submittedName)
        assertTrue(submittedType == AIProviderType.OPEN_ROUTER)
        assertEquals("sk-or-test-key", submittedKey)
    }

    @Test
    fun disconnectedProviderShowsLiveVerificationRequirementInsteadOfSelectableModels() {
        val disconnectedProvider = AIProvider(
            id = "provider-offline",
            userId = "user-1",
            name = "Offline provider",
            type = AIProviderType.OPEN_ROUTER,
            status = AIProviderStatus.DISCONNECTED
        )
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                AIModelsScreenContent(
                    uiState = AIModelsUiState(
                        isAuthenticated = true,
                        providers = listOf(disconnectedProvider),
                        models = emptyList(),
                        freeOnly = true
                    ),
                    onNavigateBack = {},
                    onAddProvider = { _, _, _, _ -> },
                    onDeleteProvider = {},
                    onSyncProvider = {},
                    onTestProvider = {},
                    onSelectDefaultModel = {},
                    onToggleFreeOnly = {},
                    onToggleAutoSelect = {},
                    onRefresh = {},
                    onClearFeedback = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_models_lazy_column")
            .performScrollToNode(hasTestTag("ai_models_empty_state"))
        composeRule.onNodeWithTag("ai_models_empty_state").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_models_empty_state_message")
            .assertTextContains(
                appContext().getString(R.string.ai_models_empty_no_connected_providers),
                substring = true
            )
    }


    @Test
    fun screenUsesEnglishResourcesWhenEnglishLocaleIsSelected() {
        renderUnauthenticatedScreen(Locale.ENGLISH)

        composeRule.onNodeWithText("AI provider settings").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in required").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_models_add_provider_button").assertContentDescriptionEquals("Add provider")
    }

    @Test
    fun screenUsesArabicResourcesWhenArabicLocaleIsSelected() {
        renderUnauthenticatedScreen(Locale("ar"))

        composeRule.onNodeWithText("إعدادات مزودي الذكاء الاصطناعي").assertIsDisplayed()
        composeRule.onNodeWithText("تسجيل الدخول مطلوب").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_models_add_provider_button").assertContentDescriptionEquals("إضافة مزود")
    }

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()

    private fun renderUnauthenticatedScreen(locale: Locale) {
        val baseContext = appContext()
        val configuration = Configuration(baseContext.resources.configuration).apply { setLocale(locale) }
        val localizedContext = baseContext.createConfigurationContext(configuration)
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides localizedContext) {
                CrashIntelligenceTheme(darkTheme = true) {
                    AIModelsScreenContent(
                        uiState = AIModelsUiState(isAuthenticated = false),
                        onNavigateBack = {},
                        onAddProvider = { _, _, _, _ -> },
                        onDeleteProvider = {},
                        onSyncProvider = {},
                        onTestProvider = {},
                        onSelectDefaultModel = {},
                        onToggleFreeOnly = {},
                        onToggleAutoSelect = {},
                        onRefresh = {},
                        onClearFeedback = {}
                    )
                }
            }
        }
    }

}
