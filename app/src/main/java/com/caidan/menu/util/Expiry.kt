package com.caidan.menu.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 距离过期还剩几天以内算「临期」。 */
const val NEAR_EXPIRY_DAYS = 3

/** 一件库存的保质期状态。 */
enum class ExpiryStatus {
    /** 没填过期日期，不参与提醒 */
    NONE,

    /** 还早 */
    FRESH,

    /** 临期（[NEAR_EXPIRY_DAYS] 天内到期，含今天） */
    NEAR,

    /** 已经过期 */
    EXPIRED,
}

/** 过期日期距今天的天数：正数=还剩几天，0=今天到期，负数=已经过期几天。 */
fun daysUntilExpiry(expiryDate: Long, today: LocalDate = LocalDate.now()): Long =
    ChronoUnit.DAYS.between(
        today,
        Instant.ofEpochMilli(expiryDate).atZone(ZoneId.systemDefault()).toLocalDate(),
    )

fun expiryStatusOf(expiryDate: Long?, nearDays: Int = NEAR_EXPIRY_DAYS): ExpiryStatus {
    if (expiryDate == null) return ExpiryStatus.NONE
    val days = daysUntilExpiry(expiryDate)
    return when {
        days < 0L -> ExpiryStatus.EXPIRED
        days <= nearDays -> ExpiryStatus.NEAR
        else -> ExpiryStatus.FRESH
    }
}

/** 过期日期的可读描述：「还剩 2 天」/「今天到期」/「已过期 3 天」。 */
fun expiryText(expiryDate: Long?): String {
    if (expiryDate == null) return ""
    val days = daysUntilExpiry(expiryDate)
    return when {
        days > 0L -> "还剩 $days 天"
        days == 0L -> "今天到期"
        else -> "已过期 ${-days} 天"
    }
}
