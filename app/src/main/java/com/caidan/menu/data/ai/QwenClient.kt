package com.caidan.menu.data.ai

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 通义千问 DashScope「OpenAI 兼容」接口的最小客户端。
 * 只用 OkHttp + Gson，避免 Retrofit 注解在动态请求体上的额外复杂度。
 */
class QwenClient {

    private val gson = Gson()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)   // 图片理解较慢，读超时给足
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * 发起一次对话补全，返回助手消息的文本内容。
     * @throws AiException 网络异常、鉴权失败或返回体异常时抛出，message 面向用户可读。
     */
    suspend fun chat(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        jsonMode: Boolean,
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw AiException("还没有配置通义千问 API Key，请到「设置」页填写。")
        }

        val body = ChatRequest(
            model = model,
            messages = messages,
            responseFormat = if (jsonMode) ResponseFormat() else null,
        )
        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(body).toRequestBody(JSON_MEDIA))
            .build()

        val raw = try {
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    // 尽量提取服务端返回的错误信息，失败则回落到状态码
                    val msg = runCatching {
                        gson.fromJson(text, ChatResponse::class.java).error?.message
                    }.getOrNull()
                    throw AiException(
                        when (resp.code) {
                            401 -> "API Key 无效或已过期，请到「设置」页检查。"
                            429 -> "调用过于频繁或额度不足，请稍后再试。"
                            else -> msg ?: "请求失败（HTTP ${resp.code}）"
                        }
                    )
                }
                text
            }
        } catch (e: AiException) {
            throw e
        } catch (e: IOException) {
            throw AiException("网络连接失败，请检查网络后重试。")
        }

        val parsed = runCatching { gson.fromJson(raw, ChatResponse::class.java) }.getOrNull()
            ?: throw AiException("服务返回内容无法解析。")
        parsed.error?.message?.let { throw AiException(it) }
        parsed.choices?.firstOrNull()?.message?.content as? String
            ?: throw AiException("服务未返回有效结果。")
    }

    companion object {
        private const val ENDPOINT =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
