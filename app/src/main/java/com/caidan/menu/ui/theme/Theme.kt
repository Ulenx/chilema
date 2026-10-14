package com.caidan.menu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 浅色：米色底 + 深棕字 + 焦橙主色，选中态用珊瑚。
 *
 * 三个暖色分工别混：焦橙只给「主要动作」，珊瑚只给「选中了哪个」，
 * 琥珀只当大块卡片的底。要是主按钮和选中底都用橙色，两者会互相打架。
 *
 * surfaceContainer 那几个必须写 —— 不写 Material 会用它自带的灰紫默认值，
 * 底部导航栏就会变成一块跟暖色主题不搭的淡紫。
 */
private val LightColors = lightColorScheme(
    primary = Burnt,
    onPrimary = Cream,
    primaryContainer = Sand,
    onPrimaryContainer = DarkBrown,

    secondary = Coral,
    onSecondary = DarkBrown,
    secondaryContainer = CoralTint,
    onSecondaryContainer = DarkBrown,

    tertiary = Amber,
    onTertiary = DarkBrown,

    background = Cream,
    onBackground = DarkBrown,
    surface = Cream,
    onSurface = DarkBrown,
    surfaceVariant = SandCream,
    onSurfaceVariant = WarmBrown,

    surfaceContainerLowest = Color(0xFFFFFBF5),
    surfaceContainerLow = Color(0xFFFFF7EA),
    surfaceContainer = SandCream,
    surfaceContainerHigh = Sand,
    surfaceContainerHighest = Amber,

    outline = OutlineWarm,
    outlineVariant = OutlineWarmVariant,
    error = ExpiryExpired,
    onError = Cream,
)

/**
 * 深色：把深棕当底，主色提亮成珊瑚 —— 焦橙压在深底上沉下去看不清。
 */
private val DarkColors = darkColorScheme(
    primary = Coral,
    onPrimary = DarkBrown,
    primaryContainer = Color(0xFF8A3A1B),
    onPrimaryContainer = Sand,

    secondary = Amber,
    onSecondary = DarkBrown,
    secondaryContainer = Color(0xFF6D4C41),
    onSecondaryContainer = Sand,

    tertiary = Amber,
    onTertiary = DarkBrown,

    background = DarkSurface,
    onBackground = Cream,
    surface = DarkSurface,
    onSurface = Cream,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,

    surfaceContainerLowest = DarkSurfaceLow,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceHigh,
    surfaceContainerHigh = Color(0xFF7A5548),
    surfaceContainerHighest = Color(0xFF9A6B5C),

    outline = Color(0xFFA98C82),
    outlineVariant = Color(0xFF5A423A),
    error = ExpiryExpiredDark,
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
