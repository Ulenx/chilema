package com.caidan.menu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 浅色：米色底 + 海军蓝字 + 暖橙主色，冷色只用来点次要动作。
 *
 * surfaceContainer 那几个也得写 —— 不写的话 Material 会用它自带的灰紫色
 * 默认值，底部导航栏就会变成一块跟整个暖色主题不搭的淡紫。
 */
private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Cream,
    primaryContainer = AmberTint,
    onPrimaryContainer = Navy,

    secondary = BlueStrong,
    onSecondary = Cream,
    secondaryContainer = BlueTint,
    onSecondaryContainer = Navy,

    tertiary = DeepOrange,
    onTertiary = Cream,

    background = Cream,
    onBackground = Navy,
    surface = Cream,
    onSurface = Navy,
    surfaceVariant = CreamAmber,
    onSurfaceVariant = NavyMuted,

    surfaceContainerLowest = Color(0xFFFFFBF5),
    surfaceContainerLow = Color(0xFFFFF7EA),
    surfaceContainer = CreamAmber,
    surfaceContainerHigh = AmberTint,
    surfaceContainerHighest = Color(0xFFFFDCB0),

    outline = OutlineWarm,
    outlineVariant = OutlineWarmVariant,
    error = Color(0xFFB3261E),
    onError = Cream,
)

/**
 * 深色：把海军蓝当底，主色换成浅琥珀 —— 橙色压在深底上要提亮才看得清。
 */
private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF4A2600),
    primaryContainer = Color(0xFF8A4A00),
    onPrimaryContainer = AmberTint,

    secondary = Blue,
    onSecondary = Color(0xFF0A2A4A),
    secondaryContainer = Navy,
    onSecondaryContainer = BlueTint,

    tertiary = Amber,
    onTertiary = Color(0xFF4A2600),

    background = DarkSurface,
    onBackground = Cream,
    surface = DarkSurface,
    onSurface = Cream,
    surfaceVariant = Navy,
    onSurfaceVariant = DarkOnSurfaceVariant,

    surfaceContainerLowest = Color(0xFF0E1C2C),
    surfaceContainerLow = DarkSurface,
    surfaceContainer = Navy,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = Color(0xFF35608F),

    outline = Color(0xFF8896A8),
    outlineVariant = Color(0xFF3C4E64),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0A06),
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
