package com.example.presentation.ui.screens

import android.content.Context
import androidx.compose.ui.test.*
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.presentation.viewmodel.MainViewModel
import com.example.ui.theme.CrashIntelligenceTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signedOutProfileDoesNotShowSyntheticDeveloperIdentityOrProvider() {
        val context = appContext()
        setContent(user = null)

        composeRule.onNodeWithTag("settings_profile_name")
            .assertTextEquals(context.getString(R.string.settings_profile_not_signed_in))
        composeRule.onNodeWithTag("settings_profile_email")
            .assertTextEquals(context.getString(R.string.settings_profile_email_unavailable))
        composeRule.onNodeWithTag("settings_last_login")
            .assertTextEquals(
                context.getString(
                    R.string.settings_last_login,
                    context.getString(R.string.settings_last_login_unavailable)
                )
            )
        composeRule.onNodeWithTag("settings_session_status")
            .assertTextEquals(context.getString(R.string.settings_no_active_session))
        composeRule.onNodeWithText("dev@crashintelligence.local").assertDoesNotExist()
        composeRule.onNodeWithText("Google / Firebase").assertDoesNotExist()
    }

    @Test
    fun authenticatedProfileShowsRealAccountAndProviderNeutralSessionStatus() {
        val user = UserAccount(
            id = "user-1",
            email = "amina@example.com",
            displayName = "Amina",
            lastLoginAt = 1_700_000_000_000L
        )
        val context = appContext()
        setContent(user)

        composeRule.onNodeWithTag("settings_profile_name").assertTextEquals("Amina")
        composeRule.onNodeWithTag("settings_profile_email").assertTextEquals("amina@example.com")
        composeRule.onNodeWithTag("settings_session_status")
            .assertTextEquals(context.getString(R.string.settings_authenticated_session))
        val formattedLogin = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(user.lastLoginAt))
        composeRule.onNodeWithTag("settings_last_login")
            .assertTextEquals(context.getString(R.string.settings_last_login, formattedLogin))
        composeRule.onNodeWithText("Google / Firebase").assertDoesNotExist()
    }

    private fun setContent(user: UserAccount?) {
        val viewModel = MainViewModel(SettingsAuthRepository(user))
        composeRule.setContent {
            CrashIntelligenceTheme(darkTheme = true) {
                SettingsScreen(
                    viewModel = viewModel,
                    onSignOut = {},
                    navController = rememberNavController()
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()
}

private class SettingsAuthRepository(
    private val user: UserAccount?
) : AuthRepository {
    override val currentUser: Flow<UserAccount?> = flowOf(user)

    override fun getCurrentUserSync(): UserAccount? = user

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        organization: String
    ): Result<UserAccount> = Result.failure(UnsupportedOperationException())

    override suspend fun signIn(email: String, password: String): Result<UserAccount> =
        Result.failure(UnsupportedOperationException())

    override suspend fun signOut() = Unit

    override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())
}
