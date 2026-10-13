package com.caidan.menu.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * 抓一个网页，粗提正文文本，喂给大模型。
 *
 * 只用 OkHttp，不引入 Jsoup —— 抓下来的东西是给模型看的，不需要精确的 DOM，
 * 粗糙的文本对模型够用（和 [extractJson] 的「容错优先」一个思路）。
 */
object WebPage {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** 正文超过这个长度就截断，免得把 token 烧光。 */
    private const val MAX_CHARS = 12_000

    /** 提取后短于这个长度，就当成没抓到正文（多半是前端渲染的站点）。 */
    private const val MIN_CHARS = 120

    /**
     * 抓取并返回网页正文文本。
     * @throws AiException 网址无效、网络失败、HTTP 错误或抓不到正文时抛出，message 面向用户可读。
     */
    suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        val httpUrl = normalizeUrl(url)

        val (bytes, contentType) = try {
            http.newCall(
                Request.Builder()
                    .url(httpUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw AiException(
                        when (resp.code) {
                            404 -> "这个链接打不开（404），确认一下网址。"
                            403 -> "这个网页不让抓取（403），改用「截图识别」吧。"
                            else -> "网页抓取失败（HTTP ${resp.code}），可以改用「截图识别」。"
                        }
                    )
                }
                val body = resp.body ?: throw AiException(EMPTY_HINT)
                body.bytes() to resp.header("Content-Type").orEmpty()
            }
        } catch (e: AiException) {
            throw e
        } catch (e: IOException) {
            throw AiException("网络连接失败，请检查网络后重试。")
        }

        val html = String(bytes, charsetOf(contentType, bytes))
        val text = htmlToText(html)
        if (text.length < MIN_CHARS) {
            throw AiException(
                "这个网页没抓到正文，多半是内容由脚本加载的站点。" +
                    "可以改用「截图识别」，或把菜谱文字复制出来粘贴。"
            )
        }
        if (text.length > MAX_CHARS) text.take(MAX_CHARS) else text
    }

    private fun normalizeUrl(raw: String): HttpUrl {
        val trimmed = raw.trim()
        val withScheme = if (trimmed.startsWith("http://", true) ||
            trimmed.startsWith("https://", true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return withScheme.toHttpUrlOrNull() ?: throw AiException("这不是一个有效的网址。")
    }

    /**
     * 网页声明的编码优先，没声明就在前 2KB 里找 meta 标签，都没有按 UTF-8 算。
     * 中文菜谱站里有不少老站还是 GBK，直接按 UTF-8 解会全是乱码。
     */
    private fun charsetOf(contentType: String, bytes: ByteArray): Charset {
        Regex("charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE)
            .find(contentType)
            ?.groupValues?.get(1)
            ?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
            ?.let { return it }

        val sniff = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        Regex("charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE)
            .find(sniff)
            ?.groupValues?.get(1)
            ?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
            ?.let { return it }

        return Charsets.UTF_8
    }

    private const val EMPTY_HINT = "网页内容为空，可以改用「截图识别」。"

    /** 多数站点会拒绝默认的 OkHttp UA，这里装成浏览器。 */
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
}

/** 块级标签换成换行，其余标签直接去掉（中文不靠空格分词，去掉比换空格好）。 */
private val BLOCK_TAG = Regex(
    "</?(p|div|br|li|ul|ol|h[1-6]|tr|td|th|table|tbody|thead|section|article|" +
        "aside|header|footer|nav|blockquote|pre|hr|dl|dt|dd|figure|figcaption)" +
        "[^>]*>",
    RegexOption.IGNORE_CASE,
)

private val NOISE_ELEMENT = Regex(
    "<(script|style|noscript|svg|iframe|template)\\b[\\s\\S]*?</\\1>",
    RegexOption.IGNORE_CASE,
)

private val COMMENT = Regex("<!--[\\s\\S]*?-->")

private val ANY_TAG = Regex("<[^>]*>")

/** 粗提取 HTML 正文：去脚本样式、块级转换行、剥标签、解实体、压空白。 */
internal fun htmlToText(html: String): String {
    val s = html
        .replace(COMMENT, " ")
        .replace(NOISE_ELEMENT, " ")
        .replace(BLOCK_TAG, "\n")
        .replace(ANY_TAG, "")
    return decodeEntities(s)
        .lines()
        .joinToString("\n") { it.replace(Regex("[ \\t\\u3000]+"), " ").trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private val NAMED_ENTITIES = mapOf(
    "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"",
    "apos" to "'", "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’",
    "mdash" to "—", "ndash" to "–", "hellip" to "…", "middot" to "·",
    "deg" to "°", "times" to "×", "divide" to "÷", "laquo" to "«", "raquo" to "»",
)

private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]{1,6});")
private val NAMED_ENTITY = Regex("&([a-zA-Z]{2,8});")

private fun decodeEntities(s: String): String {
    if ('&' !in s) return s
    val numeric = NUMERIC_ENTITY.replace(s) { m ->
        val radix = if (m.groupValues[1].isEmpty()) 10 else 16
        val code = m.groupValues[2].toIntOrNull(radix)
        if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else m.value
    }
    return NAMED_ENTITY.replace(numeric) { m ->
        NAMED_ENTITIES[m.groupValues[1].lowercase()] ?: m.value
    }
}
