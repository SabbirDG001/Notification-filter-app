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

private val DarkColorScheme =
  darkColorScheme(
    primary = SophisticatedPrimary,
    onPrimary = SophisticatedOnPrimary,
    secondary = SophisticatedSecondaryContainer,
    onSecondary = SophisticatedOnSecondaryContainer,
    secondaryContainer = SophisticatedSecondaryContainer,
    onSecondaryContainer = SophisticatedOnSecondaryContainer,
    background = SophisticatedBackground,
    onBackground = SophisticatedOnBackground,
    surface = SophisticatedSurface,
    onSurface = SophisticatedOnSurface,
    surfaceVariant = SophisticatedSurface,
    onSurfaceVariant = SophisticatedOnSurfaceVariant,
    outline = SophisticatedOutline,
    outlineVariant = SophisticatedOutlineVariant,
    error = SophisticatedError,
    onError = SophisticatedOnError,
    errorContainer = SophisticatedErrorContainer,
    onErrorContainer = SophisticatedOnErrorContainer
  )

private val LightColorScheme = DarkColorScheme // Always use Sophisticated Dark as requested

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to dark theme
  dynamicColor: Boolean = false, // Set to false to avoid overriding with user's system wallpaper palette
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
