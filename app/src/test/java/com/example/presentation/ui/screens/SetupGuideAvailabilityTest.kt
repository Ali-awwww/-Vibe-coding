package com.example.presentation.ui.screens

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.repository.SdkIntegrationRepositoryImpl
import com.example.domain.model.IntegrationType
import com.example.domain.model.ProjectScanResult
import com.example.domain.model.ProjectTechnology
import com.example.domain.model.SdkSetupGuide
import com.example.domain.repository.SdkIntegrationRepository
import com.example.domain.repository.SetupGuideProgress
import com.example.domain.repository.SetupGuideProgressStore
import com.example.presentation.viewmodel.SetupGuideUiState
import com.example.presentation.viewmodel.SetupGuideViewModel
import com.example.ui.theme.CrashIntelligenceTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config
class SetupGuideAvailabilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun repositoryExposesTransportButFailsWithoutDaoAndKeepsCrashlyticsUnsupported() = runBlocking {
        val repository = SdkIntegrationRepositoryImpl()

        assertTrue(repository.isVerificationTransportAvailable)
        assertTrue(repository.verifyIntegration("application-1").isFailure)

        val firebaseGuide = repository.getSetupGuide(
            tech = ProjectTechnology.ANDROID_KOTLIN,
            service = IntegrationType.FIREBASE_CRASHLYTICS,
            projectId = "project-1"
        )
        assertTrue(firebaseGuide.steps.isEmpty())
    }

    @Test
    fun viewModelCannotReportVerifiedWithoutAnEventReceiver() {
        val viewModel = SetupGuideViewModel(UnavailableVerificationRepository())

        viewModel.verifyIntegration("application-1")

        assertFalse(viewModel.uiState.value.isVerifying)
        assertEquals(false, viewModel.uiState.value.verificationResult)
        assertEquals(
            R.string.setup_guide_verification_unavailable,
            viewModel.uiState.value.errorResId
        )
    }

    @Test
    fun verificationButtonIsDisabledWithoutARealReceiver() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                VerificationSection(
                    isVerifying = false,
                    result = null,
                    isVerificationAvailable = false,
                    error = null,
                    errorResId = R.string.setup_guide_verification_unavailable,
                    onVerify = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setup_guide_verify_button").assertIsNotEnabled()
        composeRule.onNodeWithText(appContext().getString(R.string.setup_guide_verification_unavailable))
            .assertIsDisplayed()
    }

    @Test
    fun verificationButtonIsEnabledWhenTransportIsAvailableAndShowsWaitingEvent() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                VerificationSection(
                    isVerifying = false,
                    result = false,
                    isVerificationAvailable = true,
                    error = null,
                    errorResId = R.string.setup_guide_verification_waiting_event,
                    onVerify = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setup_guide_verify_button").assertIsEnabled()
        composeRule.onNodeWithText(appContext().getString(R.string.setup_guide_verification_waiting_event))
            .assertIsDisplayed()
    }

    @Test
    fun automaticChangesAreNeverReportedAsSentToAnAgent() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                AutomatedChangesSection()
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(appContext().getString(R.string.setup_guide_automated_changes_unavailable_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(appContext().getString(R.string.setup_guide_automated_changes_unavailable_body))
            .assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("تم إرسال التعديلات المقترحة إلى Local Agent").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun technologySelectionDoesNotPretendToScanSampleFileNames() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                TechnologySelection(onTechSelected = {})
            }
        }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText("اكتشاف تلقائي (Auto Detect)").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText(appContext().getString(R.string.setup_guide_auto_detect_unavailable))
            .assertIsDisplayed()
    }

    @Test
    fun firebaseCrashlyticsCannotBeSelectedAsASupportedIntegration() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                GuideContent(
                    uiState = SetupGuideUiState(tech = ProjectTechnology.ANDROID_KOTLIN),
                    selectedService = IntegrationType.SENTRY,
                    onServiceSelected = {},
                    isVerificationAvailable = false,
                    onVerify = {},
                    onResetTech = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setup_guide_firebase_chip").assertIsNotEnabled()
        composeRule.onNodeWithText(appContext().getString(R.string.setup_guide_firebase_unavailable_body))
            .assertIsDisplayed()
    }

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()

    private class UnavailableVerificationRepository : SdkIntegrationRepository {
        override val isVerificationTransportAvailable: Boolean = false

        override suspend fun detectTechnology(fileNames: List<String>): ProjectScanResult =
            throw UnsupportedOperationException("Not used in this test")

        override suspend fun getSetupGuide(
            tech: ProjectTechnology,
            service: IntegrationType,
            projectId: String
        ): SdkSetupGuide = throw UnsupportedOperationException("Not used in this test")

        override suspend fun verifyIntegration(applicationId: String): Result<Boolean> =
            Result.failure(UnsupportedOperationException("No authenticated event receiver"))
    }

    @Test
    fun sentryGuideUsesAndroidWizardAndRejectsUnsupportedPlatforms() = runBlocking {
        val repository = SdkIntegrationRepositoryImpl()
        val androidGuide = repository.getSetupGuide(ProjectTechnology.ANDROID_KOTLIN, IntegrationType.SENTRY, "project-1")
        assertEquals("https://docs.sentry.io/platforms/android/", androidGuide.documentationUrl)
        assertTrue(androidGuide.steps.any {
            it.id == "sentry_install" && it.codeSnippet == "npx @sentry/wizard@latest -i android"
        })

        val unsupported = repository.getSetupGuide(ProjectTechnology.FLUTTER, IntegrationType.SENTRY, "project-1")
        assertTrue(unsupported.steps.isEmpty())
        assertEquals("setup_guide_platform_unsupported", unsupported.unavailableReason)
    }

    @Test
    fun bugsnagGuideUsesDocumentedAndroidDependencyAndLifecycle() = runBlocking {
        val guide = SdkIntegrationRepositoryImpl().getSetupGuide(ProjectTechnology.ANDROID_JAVA, IntegrationType.BUGSNAG, "project-1")
        assertEquals("https://docs.bugsnag.com/platforms/android/", guide.documentationUrl)
        assertTrue(guide.steps.any {
            it.codeSnippet == "implementation(\"com.bugsnag:bugsnag-android:6.27.1\")"
        })
        assertTrue(guide.steps.any {
            it.id == "bugsnag_initialize" && it.codeSnippet?.contains("Bugsnag.start(this)") == true
        })
    }

    @Test
    fun selectedPlatformAndProviderAreRestoredForTheSameApplication() {
        val store = InMemoryProgressStore()
        val viewModel = SetupGuideViewModel(UnavailableVerificationRepository(), store, "project-1", "application-1")
        viewModel.selectManualTech(ProjectTechnology.ANDROID_KOTLIN)
        viewModel.generateGuide(IntegrationType.BUGSNAG, "project-1")

        val restored = SetupGuideViewModel(UnavailableVerificationRepository(), store, "project-1", "application-1")
        assertEquals(ProjectTechnology.ANDROID_KOTLIN, restored.uiState.value.tech)
        assertEquals(IntegrationType.BUGSNAG, restored.uiState.value.selectedService)
    }

    @Test
    fun technologySelectionRendersEnglishAndArabicInOneComposition() {
        val base = appContext()
        val english = base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        )
        val arabic = base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(Locale("ar")) }
        )
        var showArabic by mutableStateOf(false)

        composeRule.setContent {
            val context = if (showArabic) arabic else english
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
                LocalLayoutDirection provides if (showArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                CrashIntelligenceTheme(darkTheme = true) {
                    TechnologySelection(onTechSelected = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(english.getString(R.string.setup_guide_technology_title)).assertIsDisplayed()
        composeRule.runOnIdle { showArabic = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(arabic.getString(R.string.setup_guide_technology_title)).assertIsDisplayed()
    }

    private class InMemoryProgressStore : SetupGuideProgressStore {
        private var saved: SetupGuideProgress? = null

        override fun load(projectId: String, applicationId: String): SetupGuideProgress? = saved

        override fun save(
            projectId: String,
            applicationId: String,
            tech: ProjectTechnology?,
            service: IntegrationType
        ) {
            saved = SetupGuideProgress(tech, service)
        }
    }

}
