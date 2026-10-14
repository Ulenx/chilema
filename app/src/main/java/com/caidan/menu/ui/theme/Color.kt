package com.caidan.menu.ui.theme

import androidx.compose.ui.graphics.Color

// ===========================================================================
// 主题色板。七个基准色：
//
//   #F28C28  暖橙      主色（按钮、悬浮按钮、选中态、链接）
//   #FFB74D  浅琥珀    暖色重音
//   #D35400  深橙      暖色里最重的一档
//   #FFF3E0  米色      底色
//   #4A90E2  中蓝      冷色点缀
//   #2C7BE5  亮蓝      冷色里更亮的一档（次要动作）
//   #1E3A5F  深海军蓝  正文文字 + 深色主题的底
//
// 色板只给了基准色，没有给浅色容器。下面几个带 Tint / Cream 后缀的是拿
// 基准色和米色兑出来的 —— 直接拿 #FFB74D 铺整块卡片太冲，压不住正文。
// ===========================================================================

val Orange = Color(0xFFF28C28)
val Amber = Color(0xFFFFB74D)
val DeepOrange = Color(0xFFD35400)
val Cream = Color(0xFFFFF3E0)
val Blue = Color(0xFF4A90E2)
val BlueStrong = Color(0xFF2C7BE5)
val Navy = Color(0xFF1E3A5F)

/** 浅琥珀兑米色：大块卡片的底（AI 录入区、底部提示条）。 */
val AmberTint = Color(0xFFFFE3BC)

/** 中蓝兑米色：次要选中态（分类栏选中项）。 */
val BlueTint = Color(0xFFD9E8FB)

/** 更淡一档的暖米色：输入框、占位块的底。 */
val CreamAmber = Color(0xFFFFEBD2)

/** 深海军蓝兑米色：次要文字（说明、用量、日期）。 */
val NavyMuted = Color(0xFF6B7C93)

/** 暖调描边，别用 Material 默认那个灰紫色。 */
val OutlineWarm = Color(0xFFB9A38C)
val OutlineWarmVariant = Color(0xFFE3D2BC)

// ---- 语义色：不吃色板 ----
//
// 保质期的两档是「提醒」而不是「品牌」，得让人一眼分得出轻重。
// 色板里没有红，硬把「过期」套成橙色就把危险的含义丢掉了，所以保留红色。

val ExpiryNear = DeepOrange
val ExpiryNearDark = Amber
val ExpiryExpired = Color(0xFFB3261E)
val ExpiryExpiredDark = Color(0xFFFF8A80)

/** 星级金色：和语义色一个做法，浅色主题用深一号、深色主题提亮。 */
val StarGold = Orange
val StarGoldDark = Amber

// ---- 深色主题 ----

val DarkSurface = Color(0xFF14263B)
val DarkSurfaceHigh = Color(0xFF2A4A73)
val DarkOnSurfaceVariant = Color(0xFFB9C7DA)
