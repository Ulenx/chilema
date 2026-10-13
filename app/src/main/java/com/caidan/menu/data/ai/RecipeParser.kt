package com.caidan.menu.data.ai

/**
 * 从一段文字或一个网页链接整理出菜谱。
 *
 * 和 [ScreenshotParser]（看图）互补：[parseContent] 处理用户粘贴的文字或
 * 从别的 App 分享过来的菜谱内容，[parseUrl] 先抓网页再走同一条路。
 */
class RecipeParser(
    private val client: QwenClient = QwenClient(),
    private val webPage: WebPage = WebPage,
) {

    /** 纯文字 → 菜谱。内容太短直接挡掉，省一次调用。 */
    suspend fun parseContent(apiKey: String, model: String, text: String): ParsedRecipe {
        val content = text.trim()
        if (content.length < MIN_CONTENT_CHARS) {
            throw AiException("内容太短，看不出是菜谱。多粘贴一些正文再试。")
        }
        val raw = client.chat(
            apiKey = apiKey,
            model = model,
            jsonMode = false,
            messages = listOf(
                ChatMessage("system", SYSTEM_PROMPT),
                ChatMessage("user", content.take(MAX_CONTENT_CHARS)),
            ),
        )
        return parseRecipeJson(raw, EMPTY_HINT)
    }

    /** 网页链接 → 菜谱。抓不到正文时 [WebPage] 会抛出可读的 [AiException]。 */
    suspend fun parseUrl(apiKey: String, model: String, url: String): ParsedRecipe =
        parseContent(apiKey, model, webPage.fetchText(url))

    companion object {
        /** 短于这个长度的文本不值得花一次调用。 */
        private const val MIN_CONTENT_CHARS = 30

        /** 分享过来的内容可能带着一整屏评论，截断省 token。 */
        private const val MAX_CONTENT_CHARS = 12_000

        private const val EMPTY_HINT = "没能从这段内容里整理出菜谱，换一段更完整的正文试试。"

        private val SYSTEM_PROMPT = """
你是一个菜谱整理助手。用户会给你一段来路不一的文字：可能是菜谱网页的正文、别的 App 分享过来的菜谱，也可能是随手复制的几段话。里面常常混着广告、评论、"打开App查看完整做法"之类的噪音。

请从中整理出菜谱，忽略与做法无关的内容。

$RECIPE_JSON_FORMAT

$RECIPE_JSON_RULES
5. 原文是菜谱就照实整理；如果通篇没有可辨认的菜谱信息（菜名、食材、步骤都找不到），把三个字段都留空，不要编一个菜谱出来。
""".trim()
    }
}
