package com.caidan.menu.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.round

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd")

private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateFormatter)

fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateTimeFormatter)

/** 1.0 -> "1"，2.5 -> "2.5"，0.0 -> ""（表示未填用量）。 */
fun formatQuantity(value: Double): String =
    if (value <= 0.0) "" else if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/**
 * 平均星级：4.0 -> "4"，4.25 -> "4.3"。
 *
 * 保留一位小数就够了 —— 分母是做菜次数，本来就到不了更细的精度，
 * 但整数不带 ".0" 尾巴，卡片上能省一格。
 */
fun formatRating(value: Double): String {
    val rounded = round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/**
 * 按打开的时间打个招呼。
 *
 * 只收小时数而不是自己取当前时间：取值那一侧留在 UI 里，这个函数就是纯的，好测。
 *
 * 分了四档而不是「早/中/晚」三档 —— 三档的话 11:00 到 18:00 全得算「中午好」，
 * 下午三点跟人说「中午好」很怪。18 点后到凌晨都算晚上，半夜打开也别太较真。
 */
fun greetingAt(hour: Int): String = when (hour) {
    in 5..10 -> "早上好"
    in 11..13 -> "中午好"
    in 14..17 -> "下午好"
    else -> "晚上好"
}

/** 拼出「2 个」「500 克」这样的展示文本。 */
fun formatAmount(quantity: Double, unit: String): String {
    val q = formatQuantity(quantity)
    return when {
        q.isEmpty() && unit.isEmpty() -> ""
        q.isEmpty() -> unit
        unit.isEmpty() -> q
        else -> "$q $unit"
    }
}
