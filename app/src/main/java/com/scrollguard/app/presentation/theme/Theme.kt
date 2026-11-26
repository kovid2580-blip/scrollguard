package com.scrollguard.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.scrollguard.app.data.preferences.ThemeMode

private val LightColors = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoPrimaryContainer,
    onPrimaryContainer = IndigoPrimary,
    secondary = SoftBlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = SoftBlueContainer,
    onSecondaryContainer = IndigoPrimary,
    tertiary = EmeraldSuccess,
    onTertiary = Color.White,
    background = NeutralLightBg,
    onBackground = NeutralLightText,
    surface = NeutralLightSurface,
    onSurface = NeutralLightText,
    surfaceVariant = NeutralLightSurfaceVariant,
    onSurfaceVariant = NeutralLightSubtext,
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonError
)

private val DarkColors = darkColorScheme(
    primary = IndigoDarkPrimary,
    onPrimary = Color(0xFF0D1540),
    primaryContainer = IndigoDarkContainer,
    onPrimaryContainer = IndigoPrimaryContainer,
    secondary = SoftBlueDark,
    onSecondary = Color(0xFF0A1D47),
    secondaryContainer = Color(0xFF1A2F5C),
    onSecondaryContainer = SoftBlueContainer,
    tertiary = EmeraldSuccess,
    onTertiary = Color(0xFF042F21),
    background = NeutralDarkBg,
    onBackground = NeutralDarkText,
    surface = NeutralDarkSurface,
    onSurface = NeutralDarkText,
    surfaceVariant = NeutralDarkSurfaceVariant,
    onSurfaceVariant = NeutralDarkSubtext,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

@Composable
fun ScrollGuardTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = ScrollGuardTypography,
        content = content
    )
}
