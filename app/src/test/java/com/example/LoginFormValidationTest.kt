package com.example

import com.example.presentation.ui.screens.AuthFormField
import com.example.presentation.ui.screens.AuthMode
import com.example.presentation.ui.screens.validateAuthForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoginFormValidationTest {

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
    fun recoveryModeReturnsAnExplicitUnavailableError() {
        val error = validateAuthForm(
            mode = AuthMode.RECOVER_PASSWORD,
            email = "dev@example.com",
            password = "",
            displayName = "",
            confirmPassword = ""
        )

        assertEquals(AuthFormField.FORM, error?.field)
        assertEquals(
            "استعادة كلمة المرور غير مفعّلة حتى اكتمال التحقق من رابط البريد.",
            error?.message
        )
    }
}
