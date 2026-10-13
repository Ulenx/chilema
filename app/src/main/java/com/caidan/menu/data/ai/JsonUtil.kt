package com.caidan.menu.data.ai

/**
 * 大模型有时会把 JSON 包在 ```json 代码块里，或前后带解释文字。
 * 这里做一次宽松提取：先剥代码块，再取第一个 { 或 [ 到最后一个 } 或 ] 的子串。
 */
internal fun extractJson(raw: String): String {
    val trimmed = raw.trim()
    val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(trimmed)
    val candidate = fence?.groupValues?.get(1)?.trim() ?: trimmed

    val firstBrace = candidate.indexOfFirst { it == '{' || it == '[' }
    val lastBrace = candidate.indexOfLast { it == '}' || it == ']' }
    if (firstBrace >= 0 && lastBrace > firstBrace) {
        return candidate.substring(firstBrace, lastBrace + 1)
    }
    return candidate
}
