package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A1B66),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = CobaltBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF142B66),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = NeonCyan,
    onTertiary = StudioObsidian,
    tertiaryContainer = Color(0xFF003845),
    onTertiaryContainer = Color(0xFFB4F6FF),
    background = StudioObsidian,
    onBackground = TextPrimaryDark,
    surface = StudioNavyDeep,
    onSurface = TextPrimaryDark,
    surfaceVariant = StudioNavySurface,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = StudioNavySurface,
    surfaceContainerHigh = StudioNavyElevated,
    outline = StudioBorderGlow,
    error = CrimsonError,
    onError = Color.White
)

private val StudioLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = LightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF0C4A6E),
    background = LightCanvas,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    outline = Color(0xFFCBD5E1),
    error = CrimsonError,
    onError = Color.White
)

private val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MunzirVoiceTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (isDark) StudioDarkColorScheme else StudioLightColorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content
    )
}
