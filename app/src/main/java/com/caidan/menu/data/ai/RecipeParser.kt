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
    private val bilibili: Bilibili = Bilibili,
) {

    /**
     * 纯文字 → 菜谱。
     *
     * @param minChars 短于这个长度直接挡掉，省一次调用。默认按「一整篇正文」要求；
     *   用户在粘贴框里手动点按钮时传 0 —— 那时的短内容往往就是一张食材清单，
     *   该照样识别出食材，而不是被长度门槛挡回去。
     */
    suspend fun parseContent(
        apiKey: String,
        model: String,
        text: String,
        minChars: Int = MIN_CONTENT_CHARS,
        /** 只拿到标题（没有做法正文）时置 true，见 [TITLE_ONLY_RULE]。 */
        titleOnly: Boolean = false,
    ): ParsedRecipe {
        val content = text.trim()
        if (content.length < minChars) {
            throw AiException("内容太短，看不出是菜谱。多粘贴一些正文再试。")
        }
        val raw = client.chat(
            apiKey = apiKey,
            model = model,
            jsonMode = false,
            messages = listOf(
                ChatMessage("system", systemPrompt(titleOnly)),
                ChatMessage("user", content.take(MAX_CONTENT_CHARS)),
            ),
        )
        val parsed = parseRecipeJson(raw, EMPTY_HINT)
        if (!titleOnly) return parsed

        // 提示词管不住「番茄炒蛋」这种知名菜：模型会顺手把家常做法默写出来，
        // 步骤、克数、火候一样不缺，看着比真的还像真的，可跟原文毫无关系。
        // 所以这里再兜一道 —— 只认标题时，认不出菜名就报错，认得出就只留菜名。
        if (parsed.name.isBlank()) throw AiException(TITLE_ONLY_HINT)
        return parsed.copy(
            description = "",
            steps = "",
            ingredients = emptyList(),
            isDessert = false,
            bakingTempC = null,
            bakingTimeMin = null,
            servings = null,
        )
    }

    /**
     * 网页链接 → 菜谱。
     *
     * B站视频先走 [Bilibili] 的官方接口拿标题和简介；不是 B站（或它拿不到）
     * 再按普通网页抓。两条路抓不到正文时都会抛出可读的 [AiException]。
     *
     * `minChars = 0`：长度门槛是给「用户粘贴的正文」设的，防的是随手粘几个字。
     * 这里是机器抓回来的，短不等于没用 —— B站视频往往就一个标题能拿，
     * 「番茄炒蛋保姆级教程」九个字照样够模型认出菜名。
     */
    suspend fun parseUrl(apiKey: String, model: String, url: String): ParsedRecipe {
        bilibili.fetch(url)?.let { video ->
            return parseContent(
                apiKey = apiKey,
                model = model,
                text = video.text,
                minChars = 0,
                titleOnly = video.titleOnly,
            )
        }
        return parseContent(
            apiKey = apiKey,
            model = model,
            text = webPage.fetchText(url),
            minChars = 0,
        )
    }

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

        /**
         * 只拿到标题时追加的一条。接在 [SYSTEM_PROMPT] 的规则 5 后面。
         *
         * 单靠「不要编造」那句不够 —— 碰到「番茄炒蛋」这种人人会做的菜，
         * 模型会默认「这我熟」，然后把家常做法整篇默写出来。
         */
        private val TITLE_ONLY_RULE = """
6. 这次给你的**只有一个标题**，没有任何做法正文。只填 name，description、steps、ingredients 一律留空。
   不要因为「这道菜我熟」就把常见做法写出来 —— 用户要的是他手上那份原文，不是一道同名菜的通用做法。
   认不出菜名就把三个字段都留空。
""".trim()

        private fun systemPrompt(titleOnly: Boolean): String =
            if (titleOnly) "$SYSTEM_PROMPT\n$TITLE_ONLY_RULE" else SYSTEM_PROMPT

        private const val TITLE_ONLY_HINT =
            "只拿到了标题，没认出菜名。做法请手动补充，或截图用「从截图生成」。"
    }
}
