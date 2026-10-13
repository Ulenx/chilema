package com.caidan.menu.data.ai

import com.google.gson.annotations.SerializedName

/** DashScope OpenAI 兼容接口的请求体。 */
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.2,
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null,
)

data class ResponseFormat(val type: String = "json_object")

data class ChatMessage(
    val role: String,
    /** 文本内容；图片消息时可为 null。 */
    val content: Any?,
)

data class ChatResponse(
    val choices: List<Choice>? = null,
    val error: ApiError? = null,
) {
    data class Choice(val message: ChatMessage?)
}

data class ApiError(val message: String? = null, val code: String? = null)

// ---- 解析结果模型 ----

/** 单条食材。 */
data class ParsedIngredient(
    val name: String,
    val quantity: Double = 0.0,
    val unit: String = "",
)

/** 截图解析出的完整菜谱。 */
data class ParsedRecipe(
    val name: String = "",
    val description: String = "",
    val steps: String = "",
    val ingredients: List<ParsedIngredient> = emptyList(),
    val isDessert: Boolean = false,
    val bakingTempC: Int? = null,
    val bakingTimeMin: Int? = null,
    val servings: Int? = null,
)

/** 解析失败时抛出，message 可直接展示给用户。 */
class AiException(message: String) : Exception(message)
