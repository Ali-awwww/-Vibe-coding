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
    fun missingCrashWithoutErrorShowsMissingState() {
        assertEquals(
            FixAssistantContentState.MissingCrash,
            deriveFixAssistantContentState(FixAssistantUiState())
        )
    }

    @Test
    fun missingCrashWithErrorShowsLoadError() {
        assertEquals(
            FixAssistantContentState.LoadError("network unavailable"),
            deriveFixAssistantContentState(FixAssistantUiState(error = "network unavailable"))
        )
    }

    @Test
    fun crashWithoutDiagnosisDoesNotOfferFixGeneration() {
        assertEquals(
            FixAssistantContentState.DiagnosisMissing,
            deriveFixAssistantContentState(FixAssistantUiState(crash = CrashReport(id = "crash-1")))
        )
    }

    @Test
    fun diagnosisWithoutSuggestionsShowsActionableEmptyState() {
        assertEquals(
            FixAssistantContentState.ReadyWithoutFix,
            deriveFixAssistantContentState(
                FixAssistantUiState(
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
                    crash = CrashReport(id = "crash-1"),
                    diagnosis = AIDiagnosis(crashId = "crash-1"),
                    suggestions = listOf(FixSuggestion(id = "fix-1")),
                    error = "pull request creation failed"
                )
            )
        )
    }
}
