package com.caidan.menu.ui.theme

import androidx.compose.ui.graphics.Color

// ===========================================================================
// 主题色板 —— 全暖色，没有冷色。食物 App 用暖色才有食欲。
//
//   #FFF3E0  米色      底色
//   #FFCC80  浅琥珀   大块卡片的底（封面、AI 录入区、提示条）
//   #FFB74D  琥珀     再深一档的暖色强调
//   #FF8A65  珊瑚     次要动作、选中态
//   #D84315  焦橙     主色（按钮、悬浮按钮、链接）
//   #8D6E63  暖棕     次要文字、描边
//   #4E342E  深棕     正文文字、深色主题的底
//
// 色板只给了基准色，没给浅色容器。带 Tint / Cream 后缀的是拿基准色和米色
// 兑出来的 —— 直接铺 #D84315 当整块底太冲，正文压不住。
// ===========================================================================

val Cream = Color(0xFFFFF3E0)
val Sand = Color(0xFFFFCC80)
val Amber = Color(0xFFFFB74D)
val Coral = Color(0xFFFF8A65)
val Burnt = Color(0xFFD84315)
val WarmBrown = Color(0xFF8D6E63)
val DarkBrown = Color(0xFF4E342E)

/** 珊瑚兑米色：选中态（分类栏、chip、分段按钮、导航 Tab）。 */
val CoralTint = Color(0xFFFFD9CE)

/** 更淡一档的暖米色：输入框、搜索框的底。 */
val SandCream = Color(0xFFFFEAD4)

/** 暖棕兑米色：描边。基准色 #8D6E63 当描边太重，会把 chip 压成实心。 */
val OutlineWarm = Color(0xFFC4A79B)
val OutlineWarmVariant = Color(0xFFE6D3C8)

// ---- 语义色：不吃色板 ----
//
// 保质期两档是「提醒」而不是「品牌」，得让人一眼分得出轻重。
// 色板全是暖色，硬把「过期」也套成橙色就跟「临期」分不出来了，
// 所以保留红色 —— 这是全App唯一的冷色，专门留给「危险」。

val ExpiryNear = Burnt
val ExpiryNearDark = Coral
val ExpiryExpired = Color(0xFFB3261E)
val ExpiryExpiredDark = Color(0xFFFF8A80)

/** 星级金色：和语义色一个做法，浅色主题用深一号、深色主题提亮。 */
val StarGold = Amber
val StarGoldDark = Sand

// ---- 深色主题 ----

val DarkSurface = DarkBrown
val DarkSurfaceLow = Color(0xFF3B2721)
val DarkSurfaceHigh = Color(0xFF6D4C41)
val DarkOnSurfaceVariant = Color(0xFFD3BDB4)
