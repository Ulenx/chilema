package com.caidan.menu.data.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 从「购物小票」或「买菜 App 订单截图」里识别出买了哪些食材。
 *
 * 只识别**名称 + 数量 + 单位**三样：分类和保质期由用户自己填
 * （小票上没有保质期，模型猜出来的日期反而会误导临期提醒）。
 * 复用 [QwenClient] 与 [ParsedIngredient]，返回结果与「粘贴文字解析食材」同构。
 */
class FridgeParser(private val client: QwenClient = QwenClient()) {

    /**
     * @param imageDataUrl `ImageStore.toDataUrl()` 生成的 `data:image/jpeg;base64,...`
     * @return 识别出的条目；什么都没识别出来时抛 [AiException]
     */
    suspend fun parse(
        apiKey: String,
        model: String,
        imageDataUrl: String,
    ): List<ParsedIngredient> {
        val userContent = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", USER_PROMPT)
            })
            add(JsonObject().apply {
                addProperty("type", "image_url")
                add("image_url", JsonObject().apply {
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
        return parse(raw)
    }

    private fun parse(raw: String): List<ParsedIngredient> {
        val root = runCatching { JsonParser.parseString(extractJson(raw)) }.getOrNull()
            ?: throw AiException("AI 返回的内容不是有效 JSON，请重试。")

        // 兼容三种形态：{items:[...]}、顶层数组、单个 {"name":...}
        val array = when {
            root.isJsonObject && root.asJsonObject.has("items") ->
                root.asJsonObject.getAsJsonArray("items")
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> JsonArray().apply { add(root) }
            else -> throw AiException("AI 未返回食材清单，请重试。")
        }

        val items = array.mapNotNull { el ->
            val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = obj.get("name")?.let { if (it.isJsonNull) "" else it.asString }
                ?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val quantity = obj.get("quantity")?.let {
                if (it.isJsonNull) 0.0 else runCatching { it.asDouble }.getOrDefault(0.0)
            } ?: 0.0
            val unit = obj.get("unit")?.let { if (it.isJsonNull) "" else it.asString }
                ?.trim().orEmpty()
            ParsedIngredient(name = name, quantity = quantity, unit = unit)
        }

        if (items.isEmpty()) {
            throw AiException("没能从这张图里识别出食材，可以截图更清晰些，或直接手动添加。")
        }
        return items
    }

    companion object {
        private const val USER_PROMPT = "请识别这张购物小票或买菜订单截图，按要求的 JSON 格式列出买到的食材。"

        private const val SYSTEM_PROMPT = """
你是一个购物清单识别助手。用户会给你一张**超市购物小票**或**生鲜买菜 App 的订单截图**，
请提取其中**可以放进冰箱的食材与调料**。

只输出一个 JSON 对象，不要任何解释文字，格式如下：
{"items":[{"name":"牛肉","quantity":500,"unit":"克"},{"name":"生抽","quantity":1,"unit":"瓶"}]}

要求：
1. 只列食材/生鲜/调料。塑料袋、纸巾、洗衣液等日用品不要列。
2. name 用常见叫法（如小票上的「精品牛腩 500g」写成「牛腩」），去掉促销、规格、品牌前缀。
3. quantity 为数字，无法确定时填 0；unit 为计量单位（克/千克/斤/个/袋/瓶/盒 等），没有就填空字符串。
4. 同一种食材出现多次就合并成一条，数量相加。
5. 看不清的条目直接跳过，不要编造。
"""
    }
}
