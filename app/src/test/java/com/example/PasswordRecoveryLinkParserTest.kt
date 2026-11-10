package com.example

import com.example.presentation.auth.PasswordRecoveryLinkResult
import com.example.presentation.auth.parsePasswordRecoveryLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRecoveryLinkParserTest {

    @Test
    fun parsesRecoveryTokenHashFromQueryParameters() {
        val result = parsePasswordRecoveryLink(
            "com.aistudio.crashintelligence.vyfrip://auth/recovery?type=recovery&token_hash=one-time-hash",
            nowEpochSeconds = 1_000L
        )

        assertTrue(result is PasswordRecoveryLinkResult.Ready)
        val credentials = (result as PasswordRecoveryLinkResult.Ready).credentials
        assertEquals("one-time-hash", credentials.tokenHash)
    }

    @Test
    fun parsesVerifiedSessionFromFragmentWithoutLosingEncodedTokenCharacters() {
        val result = parsePasswordRecoveryLink(
            "com.aistudio.crashintelligence.vyfrip://auth/recovery#access_token=access%2Btoken&refresh_token=refresh-token&expires_in=3600&type=recovery",
            nowEpochSeconds = 1_000L
        )

        assertTrue(result is PasswordRecoveryLinkResult.Ready)
        val credentials = (result as PasswordRecoveryLinkResult.Ready).credentials
        assertEquals("access+token", credentials.accessToken)
        assertEquals("refresh-token", credentials.refreshToken)
        assertEquals(4_600L, credentials.expiresAtEpochSeconds)
    }

    @Test
    fun rejectsWrongCallbackAndExpiredRecoverySession() {
        val wrongRoute = parsePasswordRecoveryLink(
            "https://example.com/auth/recovery?type=recovery&token_hash=hash",
            nowEpochSeconds = 1_000L
        )
        val expired = parsePasswordRecoveryLink(
            "com.aistudio.crashintelligence.vyfrip://auth/recovery#access_token=access&refresh_token=refresh&expires_at=999&type=recovery",
            nowEpochSeconds = 1_000L
        )

        assertTrue(wrongRoute is PasswordRecoveryLinkResult.Rejected)
        assertTrue(expired is PasswordRecoveryLinkResult.Rejected)
    }
}
