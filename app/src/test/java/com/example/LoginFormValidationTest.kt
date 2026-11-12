package com.example

import com.example.presentation.ui.screens.AuthFormField
import com.example.presentation.ui.screens.AuthMode
import com.example.presentation.ui.screens.resolvePasswordRecoveryMode
import com.example.presentation.ui.screens.shouldSubmitOnEmailIme
import com.example.presentation.ui.screens.validateAuthForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoginFormValidationTest {

    @Test
    fun recoveryEmailImeSubmitsTheRecoveryRequest() {
        assertTrue(shouldSubmitOnEmailIme(AuthMode.RECOVER_PASSWORD))
    }

    @Test
    fun signInEmailImeMovesFocusInsteadOfSubmitting() {
        assertFalse(shouldSubmitOnEmailIme(AuthMode.SIGN_IN))
    }

    @Test
    fun signUpEmailImeMovesFocusInsteadOfSubmitting() {
        assertFalse(shouldSubmitOnEmailIme(AuthMode.SIGN_UP))
    }

    @Test
    fun newPasswordModeDoesNotUseTheEmailImeSubmitBehavior() {
        assertFalse(shouldSubmitOnEmailIme(AuthMode.SET_NEW_PASSWORD))
    }

    @Test
    fun acceptedRecoveryLinkOpensNewPasswordMode() {
        assertEquals(
            AuthMode.SET_NEW_PASSWORD,
            resolvePasswordRecoveryMode(AuthMode.RECOVER_PASSWORD, isReadyForNewPassword = true)
        )
    }

    @Test
    fun rejectedRecoveryLinkReturnsToSignInAndDiscardsPasswordMode() {
        assertEquals(
            AuthMode.SIGN_IN,
            resolvePasswordRecoveryMode(AuthMode.SET_NEW_PASSWORD, isReadyForNewPassword = false)
        )
    }

    @Test
    fun rejectedRecoveryLinkKeepsTheRecoveryRequestFormOpen() {
        assertEquals(
            AuthMode.RECOVER_PASSWORD,
            resolvePasswordRecoveryMode(AuthMode.RECOVER_PASSWORD, isReadyForNewPassword = false)
        )
    }

    @Test
    fun inactiveRecoveryStateDoesNotChangeSignInMode() {
        assertEquals(
            AuthMode.SIGN_IN,
            resolvePasswordRecoveryMode(AuthMode.SIGN_IN, isReadyForNewPassword = false)
        )
    }

    @Test
    fun invalidEmailTargetsEmailField() {
        val error = validateAuthForm(
            mode = AuthMode.SIGN_IN,
            email = "not-an-email",
            password = "password",
            displayName = "",
            confirmPassword = ""
        )

        assertEquals(AuthFormField.EMAIL, error?.field)
    }

    @Test
    fun missingPasswordTargetsPasswordField() {
        val error = validateAuthForm(
            mode = AuthMode.SIGN_IN,
            email = "dev@example.com",
            password = " ",
            displayName = "",
            confirmPassword = ""
        )

        assertEquals(AuthFormField.PASSWORD, error?.field)
    }

    @Test
    fun signupRequiresDeveloperNameBeforeLaterFields() {
        val error = validateAuthForm(
            mode = AuthMode.SIGN_UP,
            email = "dev@example.com",
            password = "strongpass",
            displayName = " ",
            confirmPassword = "strongpass"
        )

        assertEquals(AuthFormField.DISPLAY_NAME, error?.field)
    }

    @Test
    fun signupRejectsPasswordShorterThanEightCharacters() {
        val error = validateAuthForm(
            mode = AuthMode.SIGN_UP,
            email = "dev@example.com",
            password = "short",
            displayName = "Developer",
            confirmPassword = "short"
        )

        assertEquals(AuthFormField.PASSWORD, error?.field)
    }

    @Test
    fun signupRequiresMatchingPasswordConfirmation() {
        val error = validateAuthForm(
            mode = AuthMode.SIGN_UP,
            email = "dev@example.com",
            password = "strongpass",
            displayName = "Developer",
            confirmPassword = "differentpass"
        )

        assertEquals(AuthFormField.CONFIRM_PASSWORD, error?.field)
    }

    @Test
    fun validSigninAcceptsTrimmedEmailAndNonblankPassword() {
        val error = validateAuthForm(
            mode = AuthMode.SIGN_IN,
            email = " dev@example.com ",
            password = "password",
            displayName = "",
            confirmPassword = ""
        )

        assertNull(error)
    }

    @Test
    fun validSignupPassesAllRequiredFields() {
        val error = validateAuthForm(
            mode = AuthMode.SIGN_UP,
            email = "dev@example.com",
            password = "strongpass",
            displayName = "Developer",
            confirmPassword = "strongpass"
        )

        assertNull(error)
    }

    @Test
    fun recoveryRequestRequiresOnlyAValidEmail() {
        val error = validateAuthForm(
            mode = AuthMode.RECOVER_PASSWORD,
            email = "dev@example.com",
            password = "",
            displayName = "",
            confirmPassword = ""
        )

        assertNull(error)
    }

    @Test
    fun newPasswordModeRejectsPasswordsShorterThanEightCharacters() {
        val error = validateAuthForm(
            mode = AuthMode.SET_NEW_PASSWORD,
            email = "",
            password = "short",
            displayName = "",
            confirmPassword = "short"
        )

        assertEquals(AuthFormField.PASSWORD, error?.field)
    }

    @Test
    fun newPasswordModeRequiresMatchingConfirmation() {
        val error = validateAuthForm(
            mode = AuthMode.SET_NEW_PASSWORD,
            email = "",
            password = "strong-password",
            displayName = "",
            confirmPassword = "different-password"
        )

        assertEquals(AuthFormField.CONFIRM_PASSWORD, error?.field)
    }

    @Test
    fun validNewPasswordModeDoesNotRequireAnEmailField() {
        val error = validateAuthForm(
            mode = AuthMode.SET_NEW_PASSWORD,
            email = "",
            password = "strong-password",
            displayName = "",
            confirmPassword = "strong-password"
        )

        assertNull(error)
    }
}
