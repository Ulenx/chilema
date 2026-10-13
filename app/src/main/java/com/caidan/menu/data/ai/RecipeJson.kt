package com.caidan.menu.data.ai

import com.google.gson.JsonParser

/**
 * 大模型按下面 [RECIPE_JSON_FORMAT] 的格式返回菜谱 —— 截图识别、网页抓取、
 * 分享内容解析三条路都归到这里，容错解析只有一份实现。
 *
 * @param emptyMessage 什么都没解出来时给用户看的话，各入口提示的下一步动作不同。
 */
internal fun parseRecipeJson(raw: String, emptyMessage: String): ParsedRecipe {
    val root = runCatching { JsonParser.parseString(extractJson(raw)) }.getOrNull()
        ?: throw AiException("AI 返回的内容不是有效 JSON，请重试。")

    val obj = if (root.isJsonArray) {
        root.asJsonArray.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
    } else {
        root.takeIf { it.isJsonObject }?.asJsonObject
    } ?: throw AiException(emptyMessage)

    fun str(key: String): String =
        obj.get(key)?.let { if (it.isJsonNull) "" else it.asString }?.trim().orEmpty()

    // 模型可能返回 180，也可能返回 "180"，两种都要能接住
    fun intOrNull(key: String): Int? = obj.get(key)?.let { el ->
        if (el.isJsonNull) null
        else runCatching { el.asInt }
            .getOrElse { runCatching { el.asString.trim().toInt() }.getOrNull() }
    }

    val ingredients = obj.get("ingredients")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = o.get("name")?.asString?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            ParsedIngredient(
                name = name,
                quantity = o.get("quantity")?.let {
                    if (it.isJsonNull) 0.0 else runCatching { it.asDouble }.getOrDefault(0.0)
                } ?: 0.0,
                unit = o.get("unit")?.let { if (it.isJsonNull) "" else it.asString }.orEmpty(),
            )
        }.orEmpty()

    val recipe = ParsedRecipe(
        name = str("name"),
        description = str("description"),
        steps = str("steps"),
        ingredients = ingredients,
        isDessert = obj.get("isDessert")?.let { !it.isJsonNull && it.asBoolean } ?: false,
        bakingTempC = intOrNull("bakingTempC"),
        bakingTimeMin = intOrNull("bakingTimeMin"),
        servings = intOrNull("servings"),
    )

    if (recipe.name.isEmpty() && recipe.ingredients.isEmpty() && recipe.steps.isEmpty()) {
        throw AiException(emptyMessage)
    }
    return recipe
}

/** 各入口提示词共用的输出格式说明；改字段只改这里。 */
internal val RECIPE_JSON_FORMAT = """
只输出一个 JSON 对象，不要任何解释文字，格式如下：
{
  "name": "菜名",
  "description": "一句话简介或特色说明，没有就留空",
  "steps": "完整做法步骤，多步之间用换行分隔，每步前面加序号",
  "ingredients": [{"name": "鸡蛋", "quantity": 2, "unit": "个"}],
  "isDessert": false,
  "bakingTempC": null,
  "bakingTimeMin": null,
  "servings": null
}
""".trim()

/** 各入口共用的字段要求。 */
internal val RECIPE_JSON_RULES = """
要求：
1. steps 要尽量完整还原原始做法的步骤，不要省略。
2. ingredients 必须包含出现的全部食材，quantity 为数字（无法确定填 0），unit 为计量单位（没有则空字符串）。
3. 判断这是否是甜点/烘焙类（蛋糕、饼干、面包、布丁、慕斯等），是则 isDessert 为 true，并尽量填写烘焙温度（摄氏度）、烘焙时间（分钟）和份量；不是甜点则三者都填 null。
4. 看不清或没有的内容不要编造，对应字段留空。
""".trim()
