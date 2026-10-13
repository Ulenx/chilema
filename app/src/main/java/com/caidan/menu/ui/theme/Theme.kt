package com.caidan.menu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = OrangePrimary,
    onPrimary = Cream,
    primaryContainer = OrangeContainer,
    onPrimaryContainer = OnOrangeContainer,
    secondary = WarmRed,
    secondaryContainer = CreamContainer,
    surface = Cream,
    onSurface = Brown,
    surfaceVariant = CreamContainer,
    onSurfaceVariant = BrownGrey,
    background = Cream,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59D),
    onPrimary = OnOrangeContainer,
    primaryContainer = Color(0xFF7A2E0A),
    onPrimaryContainer = OrangeContainer,
    secondary = Color(0xFFFFB3A6),
    surface = DarkSurface,
    onSurface = Color(0xFFEDE0DA),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFD7C0B8),
    background = DarkSurface,
)

@Composable
fun MenuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
