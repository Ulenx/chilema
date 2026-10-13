package com.caidan.menu.data.ai

import com.google.gson.JsonParser

/** 把用户粘贴的菜谱文字解析成结构化食材清单。 */
class IngredientExtractor(private val client: QwenClient = QwenClient()) {

    suspend fun extract(apiKey: String, model: String, text: String): List<ParsedIngredient> {
        if (text.isBlank()) throw AiException("请先粘贴菜谱文字或做法描述。")

        val raw = client.chat(
            apiKey = apiKey,
            model = model,
            jsonMode = true,
            messages = listOf(
                ChatMessage("system", SYSTEM_PROMPT),
                ChatMessage("user", text.take(6000)),
            ),
        )
        return parse(raw)
    }

    private fun parse(raw: String): List<ParsedIngredient> {
        val root = runCatching { JsonParser.parseString(extractJson(raw)) }.getOrNull()
            ?: throw AiException("AI 返回的内容不是有效 JSON，请重试。")

        // 兼容三种形态：顶层数组、{ingredients:[...]}、单个 {"name":...}
        val array = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject && root.asJsonObject.has("ingredients") ->
                root.asJsonObject.getAsJsonArray("ingredients")
            root.isJsonObject -> com.google.gson.JsonArray().apply { add(root) }
            else -> throw AiException("AI 未返回食材清单，请重试。")
        }

        val result = array.mapNotNull { el ->
            val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = obj.get("name")?.asString?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val qty = obj.get("quantity")?.let {
                if (it.isJsonNull) 0.0 else runCatching { it.asDouble }.getOrDefault(0.0)
            } ?: 0.0
            val unit = obj.get("unit")?.let { if (it.isJsonNull) "" else it.asString }
                ?.trim().orEmpty()
            ParsedIngredient(name = name, quantity = qty, unit = unit)
        }

        if (result.isEmpty()) throw AiException("没能从这段文字里识别出食材，可以手动添加。")
        return result
    }

    companion object {
        private const val SYSTEM_PROMPT = """
你是一个菜谱助手。用户会给你一段菜谱或做法描述，请提取其中的全部食材。

要求：
1. 只输出 JSON，不要任何解释文字。
2. 输出格式固定为：{"ingredients":[{"name":"鸡蛋","quantity":2,"unit":"个"}]}
3. name 用常见食材名（如「生抽」不要写成「酱油生抽」）。
4. quantity 为数字，无法确定时填 0；unit 为计量单位（克/毫升/个/勺/适量 等），没有就填空字符串。
5. 调味料如「盐适量」「少许胡椒」也要列出，quantity 填 0、unit 填「适量」或「少许」。
"""
    }
}
