package com.example

import com.example.presentation.auth.isVerifiedEmailConfirmationLink
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailConfirmationLinkParserTest {
    @Test
    fun acceptsSupabaseSignupConfirmationCallback() {
        assertTrue(
            isVerifiedEmailConfirmationLink(
                "com.aistudio.crashintelligence.vyfrip://auth/email-confirmation#access_token=opaque-token&type=signup"
            )
        )
    }

    @Test
    fun rejectsWrongRouteTypeErrorsAndMissingSessionToken() {
        assertFalse(
            isVerifiedEmailConfirmationLink(
                "https://example.com/auth/email-confirmation#access_token=token&type=signup"
            )
        )
        assertFalse(
            isVerifiedEmailConfirmationLink(
                "com.aistudio.crashintelligence.vyfrip://auth/email-confirmation#access_token=token&type=recovery"
            )
        )
        assertFalse(
            isVerifiedEmailConfirmationLink(
                "com.aistudio.crashintelligence.vyfrip://auth/email-confirmation#type=signup"
            )
        )
        assertFalse(
            isVerifiedEmailConfirmationLink(
                "com.aistudio.crashintelligence.vyfrip://auth/email-confirmation#error=access_denied&type=signup"
            )
        )
    }
}
