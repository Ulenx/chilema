package com.caidan.menu.data.ai

/**
 * 把菜谱截图交给多模态模型，直接解析出完整菜谱
 * （菜名、做法步骤、食材清单、是否甜点及烘烤参数）。
 *
 * @param encodeImage 由调用方提供「本地图片路径 -> data URL 字符串」的转换，
 *   便于把 Android 的图片处理逻辑留在 UI/util 层。
 */
class ScreenshotParser(private val client: QwenClient = QwenClient()) {

    suspend fun parse(
        apiKey: String,
        model: String,
        imageDataUrl: String,
    ): ParsedRecipe {
        val userContent = com.google.gson.JsonArray().apply {
            add(com.google.gson.JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", USER_PROMPT)
            })
            add(com.google.gson.JsonObject().apply {
                addProperty("type", "image_url")
                add("image_url", com.google.gson.JsonObject().apply {
                    addProperty("url", imageDataUrl)
                })
            })
        }

        val raw = client.chat(
            apiKey = apiKey,
            model = model,
            jsonMode = false, // 通义 VL 系列对 json_object 支持不稳，改为提示词约束 + 宽松提取
            messages = listOf(
                ChatMessage("system", SYSTEM_PROMPT),
                ChatMessage("user", userContent),
            ),
        )
        return parseRecipeJson(raw, "AI 没有识别出菜谱内容，请换一张更清晰的截图。")
    }

    companion object {
        private const val USER_PROMPT = "请识别这张菜谱截图，按要求的 JSON 格式输出。"

        private val SYSTEM_PROMPT = """
你是一个菜谱识别助手。请识别用户提供的菜谱图片，提取完整菜谱信息。

$RECIPE_JSON_FORMAT

$RECIPE_JSON_RULES
""".trim()
    }
}
