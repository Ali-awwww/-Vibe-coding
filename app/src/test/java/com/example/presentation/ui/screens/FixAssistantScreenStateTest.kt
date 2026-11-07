package com.example.presentation.ui.screens

import com.example.domain.model.AIDiagnosis
import com.example.domain.model.CrashReport
import com.example.domain.model.FixSuggestion
import com.example.presentation.viewmodel.FixAssistantUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class FixAssistantScreenStateTest {

    @Test
    fun loadingStateTakesPrecedence() {
        assertEquals(
            FixAssistantContentState.Loading,
            deriveFixAssistantContentState(FixAssistantUiState(isLoading = true))
        )
    }

    @Test
    fun initialUiStateShowsLoadingUntilTheLoadRequestStarts() {
        assertEquals(
            FixAssistantContentState.Loading,
            deriveFixAssistantContentState(FixAssistantUiState())
        )
    }

    @Test
    fun missingCrashWithoutErrorShowsMissingState() {
        assertEquals(
            FixAssistantContentState.MissingCrash,
            deriveFixAssistantContentState(FixAssistantUiState(isLoading = false))
        )
    }

    @Test
    fun missingCrashWithErrorShowsLoadError() {
        assertEquals(
            FixAssistantContentState.LoadError("network unavailable"),
            deriveFixAssistantContentState(FixAssistantUiState(isLoading = false, loadError = "network unavailable"))
        )
    }

    @Test
    fun diagnosisLoadFailureWithExistingCrashShowsLoadError() {
        assertEquals(
            FixAssistantContentState.LoadError("diagnosis unavailable"),
            deriveFixAssistantContentState(
                FixAssistantUiState(
                    isLoading = false,
                    crash = CrashReport(id = "crash-1"),
                    loadError = "diagnosis unavailable"
                )
            )
        )
    }

    @Test
    fun suggestionsStreamFailureWithoutSuggestionsShowsLoadError() {
        assertEquals(
            FixAssistantContentState.LoadError("suggestions offline"),
            deriveFixAssistantContentState(
                FixAssistantUiState(
                    isLoading = false,
                    crash = CrashReport(id = "crash-1"),
                    diagnosis = AIDiagnosis(crashId = "crash-1"),
                    suggestionsLoadError = "suggestions offline"
                )
            )
        )
    }

    @Test
    fun existingSuggestionsRemainReadyWhenSuggestionStreamFails() {
        assertEquals(
            FixAssistantContentState.ReadyWithFixes(1),
            deriveFixAssistantContentState(
                FixAssistantUiState(
                    isLoading = false,
                    crash = CrashReport(id = "crash-1"),
                    diagnosis = AIDiagnosis(crashId = "crash-1"),
                    suggestions = listOf(FixSuggestion(id = "fix-1")),
                    suggestionsLoadError = "stream stopped"
                )
            )
        )
    }

    @Test
    fun verificationHistoryLoadFailureDoesNotBlockFixGeneration() {
        assertEquals(
            FixAssistantContentState.ReadyWithoutFix,
            deriveFixAssistantContentState(
                FixAssistantUiState(
                    isLoading = false,
                    crash = CrashReport(id = "crash-1"),
                    diagnosis = AIDiagnosis(crashId = "crash-1"),
                    verificationsLoadError = "history offline"
                )
            )
        )
    }

    @Test
    fun crashWithoutDiagnosisDoesNotOfferFixGeneration() {
        assertEquals(
            FixAssistantContentState.DiagnosisMissing,
            deriveFixAssistantContentState(FixAssistantUiState(isLoading = false, crash = CrashReport(id = "crash-1")))
        )
    }

    @Test
    fun diagnosisWithoutSuggestionsShowsActionableEmptyState() {
        assertEquals(
            FixAssistantContentState.ReadyWithoutFix,
            deriveFixAssistantContentState(
                FixAssistantUiState(
                    isLoading = false,
                    crash = CrashReport(id = "crash-1"),
                    diagnosis = AIDiagnosis(crashId = "crash-1")
                )
            )
        )
    }

    @Test
    fun generationErrorIsNotPresentedAsAnEmptyState() {
        assertEquals(
            FixAssistantContentState.GenerationError("model request failed"),
            deriveFixAssistantContentState(
                FixAssistantUiState(
                    isLoading = false,
                    crash = CrashReport(id = "crash-1"),
                    diagnosis = AIDiagnosis(crashId = "crash-1"),
                    error = "model request failed"
                )
            )
        )
    }

    @Test
    fun existingSuggestionsRemainVisibleWhenAnotherActionFails() {
        assertEquals(
            FixAssistantContentState.ReadyWithFixes(1),
            deriveFixAssistantContentState(
                FixAssistantUiState(
                    isLoading = false,
                    crash = CrashReport(id = "crash-1"),
                    diagnosis = AIDiagnosis(crashId = "crash-1"),
                    suggestions = listOf(FixSuggestion(id = "fix-1")),
                    error = "pull request creation failed"
                )
            )
        )
    }
}
