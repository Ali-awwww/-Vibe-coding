package com.example.presentation.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.EmailConfirmationRequiredException
import com.example.domain.repository.PasswordRecoveryCredentials
import com.example.presentation.viewmodel.AuthUiMessage
import com.example.presentation.viewmodel.MainViewModel
import com.example.ui.theme.CrashIntelligenceTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config
class LoginScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun authDiagnosticsStartCollapsedAndCanBeExpandedOnDemand() {
        val viewModel = MainViewModel(LoginScreenAuthRepository())
        viewModel.handleGoogleSignInFailure(
            GetCredentialCancellationException("credential provider detail")
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                LoginScreen(viewModel = viewModel, onAuthSuccess = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("auth_diagnostic_panel")
            .assertExists()
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("credential provider detail").assertDoesNotExist()

        composeRule.onNodeWithTag("toggle_auth_diagnostics_button")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("credential provider detail")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun switchingAuthModeClearsPasswordAndResetsVisibility() {
        val viewModel = MainViewModel(LoginScreenAuthRepository())
        val context = appContext()

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                LoginScreen(viewModel = viewModel, onAuthSuccess = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("toggle_auth_mode_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("display_name_input").assertExists()
        composeRule.onNodeWithTag("password_input").performScrollTo().performTextInput("sensitive-password")
        composeRule.onNodeWithTag("auth_password_visibility_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("back_to_sign_in_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("toggle_auth_mode_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("password_input").performScrollTo()
        assertEditableTextEquals("password_input", "")
        composeRule.onNodeWithContentDescription(context.getString(R.string.auth_show_password))
            .assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.auth_hide_password))
            .assertDoesNotExist()
    }

    @Test
    fun emailConfirmationRequiredSignupClearsEnteredPassword() {
        val repository = LoginScreenAuthRepository(
            signUpOutcome = Result.failure(
                EmailConfirmationRequiredException("email confirmation required")
            )
        )
        val viewModel = MainViewModel(repository)
        val context = appContext()

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                LoginScreen(viewModel = viewModel, onAuthSuccess = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("toggle_auth_mode_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("display_name_input").performScrollTo().performTextInput("Developer")
        composeRule.onNodeWithTag("email_input").performScrollTo().performTextInput("developer@example.com")
        composeRule.onNodeWithTag("password_input").performScrollTo().performTextInput("sensitive-password")
        composeRule.onNodeWithTag("confirm_password_input").performScrollTo().performTextInput("sensitive-password")
        composeRule.onNodeWithTag("auth_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(AuthUiMessage.EMAIL_CONFIRMATION_REQUIRED, viewModel.actionState.value.successMessage)
        composeRule.onNodeWithText(context.getString(R.string.auth_success_email_confirmation))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("password_input").performScrollTo()
        assertEditableTextEquals("password_input", "")
        composeRule.onNodeWithTag("confirm_password_input").performScrollTo()
        assertEditableTextEquals("confirm_password_input", "")
    }

    @Test
    fun successfulPasswordRecoveryReturnsToSignInWithoutNavigatingToDashboard() {
        val repository = LoginScreenAuthRepository(
            passwordRecoveryOutcome = Result.success(Unit)
        )
        val viewModel = MainViewModel(repository)
        var authSuccessCalls = 0
        val context = appContext()
        viewModel.acceptPasswordRecoveryLink(
            "com.aistudio.crashintelligence.vyfrip://auth/recovery?mode=resetPassword&oobCode=valid-oob"
        )

        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                LoginScreen(viewModel = viewModel, onAuthSuccess = { authSuccessCalls++ })
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("password_input")
            .performScrollTo()
            .performTextInput("new-secure-password")
        composeRule.onNodeWithTag("confirm_password_input")
            .performScrollTo()
            .performTextInput("new-secure-password")
        composeRule.onNodeWithTag("auth_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(AuthUiMessage.PASSWORD_UPDATED, viewModel.actionState.value.successMessage)
        assertEquals(0, authSuccessCalls)
        composeRule.onNodeWithText(context.getString(R.string.auth_success_password_updated))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("display_name_input").assertDoesNotExist()
        composeRule.onNodeWithTag("password_input").performScrollTo()
        assertEditableTextEquals("password_input", "")
    }

    @Test
    fun authFormRemainsWithinCompactViewportInLtrAndRtl() {
        val layoutDirection = mutableStateOf(LayoutDirection.Ltr)
        val viewModel = MainViewModel(LoginScreenAuthRepository())

        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection.value) {
                CrashIntelligenceTheme(darkTheme = true) {
                    Box(Modifier.width(320.dp).height(760.dp)) {
                        LoginScreen(viewModel = viewModel, onAuthSuccess = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()

        fun assertCardContained() {
            val rootBounds = composeRule.onNodeWithTag("login_root")
                .fetchSemanticsNode().boundsInRoot
            val cardBounds = composeRule.onNodeWithTag("auth_form_card")
                .fetchSemanticsNode().boundsInRoot
            assertTrue(cardBounds.left >= rootBounds.left - 1f)
            assertTrue(cardBounds.right <= rootBounds.right + 1f)
        }

        assertCardContained()
        composeRule.runOnIdle { layoutDirection.value = LayoutDirection.Rtl }
        composeRule.waitForIdle()
        assertCardContained()
        composeRule.onNodeWithTag("email_input").assertExists()
        composeRule.onNodeWithTag("auth_submit_button").assertExists()
    }

    private fun assertEditableTextEquals(tag: String, expected: String) {
        val actual = composeRule.onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text
        assertEquals(expected, actual)
    }

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()
}

private class LoginScreenAuthRepository(
    private val signUpOutcome: Result<UserAccount> =
        Result.failure(UnsupportedOperationException("No sign-up response configured.")),
    private val passwordRecoveryOutcome: Result<Unit> =
        Result.failure(UnsupportedOperationException("No password recovery response configured."))
) : AuthRepository {
    override val currentUser: Flow<UserAccount?> = flowOf(null)

    override fun getCurrentUserSync(): UserAccount? = null

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        organization: String
    ): Result<UserAccount> = signUpOutcome

    override suspend fun signIn(email: String, password: String): Result<UserAccount> =
        Result.failure(UnsupportedOperationException("Sign-in is not part of this UI test."))

    override suspend fun signOut() = Unit

    override suspend fun completePasswordRecovery(
        credentials: PasswordRecoveryCredentials,
        newPassword: String
    ): Result<Unit> = passwordRecoveryOutcome

    override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Password reset is not part of this UI test."))

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Google sign-in is not part of this UI test."))
}
