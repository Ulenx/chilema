package com.caidan.menu.data.ai

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException

/**
 * B站视频能拿到的文案。
 *
 * @param title 视频标题，一定有
 * @param body 简介正文；UP 主没写时是空串（B站接口用 `-` 表示）
 */
data class VideoText(val title: String, val body: String) {

    /**
     * 只有标题、没有正文。
     *
     * 这个判断很要紧：光看标题，模型会把「番茄炒蛋」这种知名菜的家常做法
     * 整篇默写出来 —— 字数和步骤都齐全，却和用户真正想看的那份做法毫无关系。
     */
    val titleOnly: Boolean get() = body.isBlank()

    /** 拼成给模型看的文本。 */
    val text: String get() = listOf(title, body).filter { it.isNotBlank() }.joinToString("\n")
}

/**
 * B站视频 → 文案（标题 + 简介）。
 *
 * 直接问官方 JSON 接口，而不是去刮视频页：页面是纯前端渲染的，内容全塞在
 * `<script>` 里，而 [htmlToText] 恰恰会把 `<script>` 整段扔掉 —— 抠出来只剩
 * 两万多字的站点导航噪音，模型在垃圾堆里翻菜谱，还不如不给。
 *
 * **别对它期望太高**：抽查下来，菜谱视频的简介基本是宣传语、复读标题、
 * 或者 UP 主在聊别的事，做法本身在画面和口播里。所以这条路的实际产出
 * 通常就是一个菜名（从标题认出来），步骤还得靠手动或截图。
 *
 * 抖音、小红书没有这种公开接口（小红书连页面都先要登录），所以这里不做它们：
 * 那些链接仍然交给 [WebPage] 走抓网页那条路，抓不到就报错让用户改用截图。
 */
object Bilibili {

    /** 视频号，形如 BV1GJ411x7h7。 */
    private val BVID = Regex("BV[0-9A-Za-z]{10}")

    /** 短链域名。b23.tv 上没有视频号，得先跟一次跳转。 */
    private val SHORT_HOST = Regex("""b23\.tv""", RegexOption.IGNORE_CASE)

    private const val API = "https://api.bilibili.com/x/web-interface/view"

    private const val EMPTY_HINT =
        "这个视频的标题和简介里没有可整理的内容。做法多半在画面和口播里，" +
            "可以截几张图用「从截图生成」。"

    /**
     * 是 B站链接就返回它的文案；不是 B站（或跳转/接口没拿到）返回 null，
     * 交给 [WebPage] 去当普通网页抓。
     *
     * @throws AiException 确认是 B站视频、但接口明确失败时抛出，message 面向用户可读。
     */
    suspend fun fetch(url: String): VideoText? = withContext(Dispatchers.IO) {
        if (!isBilibili(url)) return@withContext null
        val bvid = BVID.find(url)?.value
            ?: resolveShortLink(url)?.let { BVID.find(it)?.value }
            ?: return@withContext null
        fetchByBvid(bvid)
    }

    private fun isBilibili(url: String): Boolean =
        url.contains("bilibili.com", ignoreCase = true) || SHORT_HOST.containsMatchIn(url)

    /**
     * 跟一次短链跳转，拿最终地址。
     *
     * 用 GET 而不是 HEAD：短链服务对 HEAD 不一定给跳转。
     * 拿到最终地址就把响应关掉，正文不读。
     */
    private fun resolveShortLink(url: String): String? = runCatching {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        sharedHttpClient.newCall(
            Request.Builder()
                .url(httpUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
        ).execute().use { it.request.url.toString() }
    }.getOrNull()

    private fun fetchByBvid(bvid: String): VideoText {
        val api = "$API?bvid=$bvid".toHttpUrlOrNull()
            ?: throw AiException("这个 B站链接解析不了，确认一下网址。")

        val payload = try {
            sharedHttpClient.newCall(
                Request.Builder()
                    .url(api)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    // 接口对没有 Referer 的请求偶尔会拒答，带上稳一些
                    .header("Referer", "https://www.bilibili.com")
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw AiException(
                        "B站接口请求失败（HTTP ${resp.code}），可以改用「截图识别」。"
                    )
                }
                resp.body?.string() ?: throw AiException(EMPTY_HINT)
            }
        } catch (e: AiException) {
            throw e
        } catch (e: IOException) {
            throw AiException("网络连接失败，请检查网络后重试。")
        }

        val obj = runCatching { JsonParser.parseString(payload) }.getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: throw AiException("B站接口返回的内容看不懂，可以改用「截图识别」。")

        val code = obj.get("code")?.let { runCatching { it.asInt }.getOrNull() } ?: -1
        if (code != 0) {
            // -404 最常见：视频被删了或视频号写错
            throw AiException(
                if (code == -404) {
                    "这个 B站视频不存在或已被删除。"
                } else {
                    "B站接口返回错误（code $code），可以改用「截图识别」。"
                }
            )
        }

        val data = obj.getAsJsonObject("data") ?: throw AiException(EMPTY_HINT)

        fun field(key: String): String =
            data.get(key)?.let { if (it.isJsonPrimitive) it.asString else null }?.trim().orEmpty()

        // 简介为空时 B站给的是一个 "-"，别把它当正文
        val title = field("title")
        val body = field("desc").takeIf { it != "-" }.orEmpty()

        // 不设长度门槛：这类视频的简介基本是宣传语或复读标题，指望不上，
        // 但光一个标题也够模型认出菜名了 —— 那已经比什么都不给强
        if (title.isBlank() && body.isBlank()) throw AiException(EMPTY_HINT)
        return VideoText(title = title, body = body)
    }
}
