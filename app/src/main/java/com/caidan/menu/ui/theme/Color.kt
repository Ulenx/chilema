package com.caidan.menu.ui.theme

import androidx.compose.ui.graphics.Color

// 暖色系，贴合「食物/做菜」主题
val OrangePrimary = Color(0xFFFF6D3B)
val OrangeContainer = Color(0xFFFFDBCF)
val OnOrangeContainer = Color(0xFF5A1A00)

val WarmRed = Color(0xFFC62828)
val Cream = Color(0xFFFFF8F0)
val CreamContainer = Color(0xFFFFEDE2)

// 冰箱保质期语义色：浅色主题用深一号，深色主题用浅一号，保证两种主题下都清晰
val ExpiryNear = Color(0xFFC26B00)
val ExpiryNearDark = Color(0xFFFFB74D)
val ExpiryExpired = Color(0xFFB3261E)
val ExpiryExpiredDark = Color(0xFFFF8A80)

// 星级金色：和保质期语义色同一套做法，浅色主题用深一号、深色主题提亮，
// 保证米色和深棕两种背景上都看得清。空心星不走这两个色，用 onSurfaceVariant 淡化。
val StarGold = Color(0xFFF59E00)
val StarGoldDark = Color(0xFFFFC107)

val Brown = Color(0xFF6D4C41)
val BrownGrey = Color(0xFF8D6E63)
val DarkSurface = Color(0xFF1C1614)
val DarkSurfaceVariant = Color(0xFF2A211E)
