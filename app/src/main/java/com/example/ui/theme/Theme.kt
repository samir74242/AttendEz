package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val CampusBlueColorScheme =
  lightColorScheme(
    primary = CampusBluePrimary,
    secondary = CampusBlueAccent,
    tertiary = CampusBlueAccent,
    background = CampusBlueBackground,
    surface = CampusBlueSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = CampusBlueTextPrimary,
    onSurface = CampusBlueTextPrimary,
    onSurfaceVariant = CampusBlueTextSecondary,
    outline = CampusBlueTextSecondary,
    outlineVariant = Color(0xFFE2E8F0)
  )

private val MidnightFocusColorScheme =
  darkColorScheme(
    primary = MidnightFocusPrimary,
    secondary = MidnightFocusAccent,
    tertiary = MidnightFocusAccent,
    background = MidnightFocusBackground,
    surface = MidnightFocusSurface,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = MidnightFocusTextPrimary,
    onSurface = MidnightFocusTextPrimary,
    onSurfaceVariant = MidnightFocusTextSecondary,
    outline = MidnightFocusTextSecondary,
    outlineVariant = MidnightFocusSurface
  )

private val MinimalWhiteColorScheme =
  lightColorScheme(
    primary = MinimalWhitePrimary,
    secondary = MinimalWhiteAccent,
    tertiary = MinimalWhiteAccent,
    background = MinimalWhiteBackground,
    surface = MinimalWhiteSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = MinimalWhiteTextPrimary,
    onSurface = MinimalWhiteTextPrimary,
    onSurfaceVariant = MinimalWhiteTextSecondary,
    outline = MinimalWhiteTextSecondary,
    outlineVariant = Color(0xFFECEFF1)
  )

private val EcoGreenColorScheme =
  lightColorScheme(
    primary = EcoGreenPrimary,
    secondary = EcoGreenAccent,
    tertiary = EcoGreenAccent,
    background = EcoGreenBackground,
    surface = EcoGreenSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = EcoGreenTextPrimary,
    onSurface = EcoGreenTextPrimary,
    onSurfaceVariant = EcoGreenTextSecondary,
    outline = EcoGreenTextSecondary,
    outlineVariant = Color(0xFFD1FAE5)
  )

private val TechGradientColorScheme =
  lightColorScheme(
    primary = TechGradientPrimary,
    secondary = TechGradientAccent,
    tertiary = TechGradientAccent,
    background = TechGradientBackground,
    surface = TechGradientSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TechGradientTextPrimary,
    onSurface = TechGradientTextPrimary,
    onSurfaceVariant = TechGradientTextSecondary,
    outline = TechGradientTextSecondary,
    outlineVariant = Color(0xFFF1F5F9)
  )

private val LavenderCalmColorScheme =
  lightColorScheme(
    primary = LavenderCalmPrimary,
    secondary = LavenderCalmAccent,
    tertiary = LavenderCalmAccent,
    background = LavenderCalmBackground,
    surface = LavenderCalmSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = LavenderCalmTextPrimary,
    onSurface = LavenderCalmTextPrimary,
    onSurfaceVariant = LavenderCalmTextSecondary,
    outline = LavenderCalmTextSecondary,
    outlineVariant = Color(0xFFEDE9FE)
  )

@Composable
fun MyApplicationTheme(
  themeMode: String = "SYSTEM",
  content: @Composable () -> Unit,
) {
  val colorScheme = when (themeMode) {
    "CAMPUS_BLUE", "LIGHT", "ACADEMIC" -> CampusBlueColorScheme
    "MIDNIGHT_FOCUS", "DARK", "MIDNIGHT" -> MidnightFocusColorScheme
    "MINIMAL_WHITE" -> MinimalWhiteColorScheme
    "ECO_GREEN", "FOREST" -> EcoGreenColorScheme
    "TECH_GRADIENT" -> TechGradientColorScheme
    "LAVENDER_CALM", "PURPLE" -> LavenderCalmColorScheme
    else -> if (isSystemInDarkTheme()) MidnightFocusColorScheme else CampusBlueColorScheme
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
