package com.example.presentation.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
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

@RunWith(RobolectricTestRunner::class)
@Config
class SetupGuideAvailabilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun repositoryDoesNotVerifyOrMarkUnsupportedCrashlyticsAsConfigured() = runBlocking {
        val repository = SdkIntegrationRepositoryImpl()

        assertFalse(repository.isVerificationTransportAvailable)
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
        composeRule.onNodeWithText("تم إرسال التعديلات المقترحة إلى Local Agent")
            .assertDoesNotExist()
    }

    @Test
    fun technologySelectionDoesNotPretendToScanSampleFileNames() {
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                TechnologySelection(onTechSelected = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("اكتشاف تلقائي (Auto Detect)").assertDoesNotExist()
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
}
