package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = EngDarkPrimary,
    onPrimary = EngDarkBackground,
    primaryContainer = EngDarkTertiary,
    onPrimaryContainer = EngLightBackground,
    secondary = EngDarkSecondary,
    onSecondary = EngDarkBackground,
    background = EngDarkBackground,
    onBackground = EngLightBackground,
    surface = EngDarkSurface,
    onSurface = EngLightBackground,
    error = EngDarkError,
    onError = EngLightBackground
)

private val LightColorScheme = lightColorScheme(
    primary = EngLightPrimary,
    onPrimary = EngLightSurface,
    primaryContainer = EngLightTertiary,
    onPrimaryContainer = EngLightSurface,
    secondary = EngLightSecondary,
    onSecondary = EngLightSurface,
    background = EngLightBackground,
    onBackground = EngDarkBackground,
    surface = EngLightSurface,
    onSurface = EngDarkBackground,
    error = EngLightError,
    onError = EngLightSurface
)

@Composable
fun CrashIntelligenceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled to maintain specific engineering aesthetic
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
