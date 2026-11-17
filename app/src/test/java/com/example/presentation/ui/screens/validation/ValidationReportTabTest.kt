package com.example.presentation.ui.screens.validation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.example.domain.validation.ContactedModelSummary
import com.example.domain.validation.TeamValidationSummaryReport
import com.example.presentation.viewmodel.TeamValidationUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ValidationReportTabTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reportDistinguishesSuccessPartialFailureAndUntestedModels() {
        val report = TeamValidationSummaryReport(
            totalTests = 20,
            passedCount = 3,
            failedCount = 2,
            skippedCount = 14,
            pendingCount = 1,
            actualCostMeasured = false,
            modelsContacted = listOf(
                model("success", "Successful model", success = 2, failed = 0),
                model("partial", "Partially successful model", success = 1, failed = 1),
                model("failed", "Failed model", success = 0, failed = 2),
                model("untested", "Untested model", success = 0, failed = 0)
            )
        )

        composeRule.setContent {
            MaterialTheme {
                ValidationReportTab(
                    uiState = TeamValidationUiState(summaryReport = report),
                    onExportReport = {}
                )
            }
        }

        composeRule.onNodeWithTag("validation_non_pass_counts")
            .assertTextEquals("فاشلة: 2 • متجاوزة: 14 • معلّقة: 1")
        composeRule.onNodeWithTag("validation_report_list")
            .performScrollToNode(hasTestTag("validation_model_status_partial"))
        composeRule.onNodeWithTag("validation_model_status_success")
            .assertTextEquals("ناجح (2/2)")
        composeRule.onNodeWithTag("validation_model_status_partial")
            .assertTextEquals("نجاح جزئي (1/2)")
        composeRule.onNodeWithTag("validation_model_status_failed")
            .assertTextEquals("فشل (0/2)")
        composeRule.onNodeWithTag("validation_model_status_untested")
            .assertTextEquals("لم يُختبر")
        composeRule.onNodeWithTag("validation_model_cost_partial")
            .assertTextEquals("12 توكن • التكلفة غير مقاسة")
    }

    @Test
    fun modelCostIsShownOnlyWhenTheReportHasMeasuredIt() {
        val report = TeamValidationSummaryReport(
            actualCostMeasured = true,
            modelsContacted = listOf(
                model("measured", "Measured model", success = 1, failed = 0, costUsd = 0.0123)
            )
        )

        composeRule.setContent {
            MaterialTheme {
                ValidationReportTab(
                    uiState = TeamValidationUiState(summaryReport = report),
                    onExportReport = {}
                )
            }
        }

        composeRule.onNodeWithTag("validation_report_list")
            .performScrollToNode(hasTestTag("validation_model_cost_measured"))
        composeRule.onNodeWithTag("validation_model_cost_measured")
            .assertTextEquals("12 توكن • 0.0123")
    }

    private fun model(
        id: String,
        name: String,
        success: Int,
        failed: Int,
        costUsd: Double = 0.0
    ) = ContactedModelSummary(
        modelId = id,
        modelName = name,
        providerId = "provider",
        providerName = "Test provider",
        tasksExecuted = listOf("validation"),
        successCount = success,
        failureCount = failed,
        totalTokens = 12,
        costUsd = costUsd,
        isFree = false,
        apiKeyMasked = "••••1234"
    )
}
