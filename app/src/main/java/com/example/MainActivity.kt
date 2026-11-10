package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.presentation.navigation.AppNavigation
import com.example.ui.theme.CrashIntelligenceTheme

class MainActivity : ComponentActivity() {
  private val passwordRecoveryLink = MutableStateFlow<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    com.example.util.configureAppCheck(this, intent)
    passwordRecoveryLink.value = intent?.dataString

    setContent {
      CrashIntelligenceTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          AppNavigation(
            passwordRecoveryLink = passwordRecoveryLink,
            onPasswordRecoveryLinkConsumed = { passwordRecoveryLink.value = null }
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    com.example.util.configureAppCheck(this, intent)
    passwordRecoveryLink.value = intent.dataString
  }
}
