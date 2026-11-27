package com.example.presentation.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.Issue
import com.example.domain.model.Project
import com.example.domain.repository.CrashRepository
import com.example.presentation.viewmodel.FakeCrashRepository
import com.example.presentation.viewmodel.IssuesViewModel
import com.example.ui.theme.CrashIntelligenceTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config
class IssuesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadFailureShowsRetryInsteadOfAHealthyEmptyState() {
        val backing = FakeCrashRepository().apply {
            projects.value = listOf(Project(id = "p1", name = "Alpha", ownerId = "u1"))
        }
        val failingRepository = object : CrashRepository by backing {
            override fun getAllIssues(): Flow<List<Issue>> = flow {
                throw IllegalStateException("offline")
            }
        }
        val viewModel = IssuesViewModel(failingRepository)

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                IssuesScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Filter issues by project").assertExists()
        composeRule.onNodeWithContentDescription("Refresh issues").assertExists()
        composeRule.onNodeWithText("Could not load issues. Check the connection and retry.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("No issues found").assertDoesNotExist()
    }
}
