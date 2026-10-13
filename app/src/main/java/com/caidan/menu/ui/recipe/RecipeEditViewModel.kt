package com.caidan.menu.ui.recipe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caidan.menu.data.ai.AiException
import com.caidan.menu.data.ai.IngredientExtractor
import com.caidan.menu.data.ai.ParsedRecipe
import com.caidan.menu.data.ai.RecipeParser
import com.caidan.menu.data.ai.ScreenshotParser
import com.caidan.menu.data.local.entity.Ingredient
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.data.local.entity.VideoLink
import com.caidan.menu.data.repo.RecipeRepository
import com.caidan.menu.data.repo.SettingsRepository
import com.caidan.menu.util.ImageStore
import com.caidan.menu.util.PendingImport
import com.caidan.menu.util.formatQuantity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 编辑中的食材草稿（用量用字符串保存，便于输入中途的「2.」等状态）。 */
data class IngredientDraft(
    val localId: Long,
    val name: String = "",
    val quantity: String = "",
    val unit: String = "",
)

data class VideoDraft(
    val localId: Long,
    val title: String = "",
    val url: String = "",
)

data class RecipeFormState(
    val recipeId: Long = 0L,
    val name: String = "",
    val description: String = "",
    val steps: String = "",
    val category: String = "",
    val type: String = Recipe.TYPE_DISH,
    val coverPhotoPath: String? = null,
    val bakingTemp: String = "",
    val bakingTime: String = "",
    val servings: String = "",
    val ingredients: List<IngredientDraft> = emptyList(),
    val videos: List<VideoDraft> = emptyList(),
    val pasteText: String = "",
    /** 「从链接导入」输入框；分享进来时也会把剥出来的链接填在这里给用户看 */
    val linkText: String = "",
    val loading: Boolean = false,
    val loadingMessage: String = "",
    val initialized: Boolean = false,
) {
    val isDessert: Boolean get() = type == Recipe.TYPE_DESSERT
    val canSave: Boolean get() = name.isNotBlank()
}

class RecipeEditViewModel(
    private val repository: RecipeRepository,
    private val extractor: IngredientExtractor,
    private val screenshotParser: ScreenshotParser,
    private val recipeParser: RecipeParser,
    private val settings: SettingsRepository,
    private val pendingImport: PendingImport,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val recipeId: Long = savedStateHandle.get<Long>("recipeId") ?: 0L
    private val typeArg: String = savedStateHandle.get<String>("type") ?: Recipe.TYPE_DISH

    private val _state = MutableStateFlow(RecipeFormState(recipeId = recipeId, type = typeArg))
    val state: StateFlow<RecipeFormState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** 保存成功后发出菜谱 id，UI 据此返回详情页。 */
    private val _saved = Channel<Long>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    private var nextLocalId = 1L

    init {
        if (recipeId > 0L) loadExisting(recipeId)
        else {
            addIngredient()
            _state.value = _state.value.copy(initialized = true)
            // 只有新建时吃一次分享内容；编辑已有菜谱时不能拿分享的东西覆盖人家
            pendingImport.takeNow()?.let(::importShared)
        }
    }

    private fun loadExisting(id: Long) {
        viewModelScope.launch {
            val recipe = repository.getRecipe(id) ?: return@launch
            val ingredients = repository.ingredientsOnce(id)
            val videos = repository.videosOnce(id)
            _state.value = RecipeFormState(
                recipeId = recipe.id,
                name = recipe.name,
                description = recipe.description,
                steps = recipe.steps,
                category = recipe.category,
                type = recipe.type,
                coverPhotoPath = recipe.coverPhotoPath,
                bakingTemp = recipe.bakingTempC?.toString().orEmpty(),
                bakingTime = recipe.bakingTimeMin?.toString().orEmpty(),
                servings = recipe.servings?.toString().orEmpty(),
                ingredients = ingredients.map {
                    IngredientDraft(
                        localId = nextLocalId++,
                        name = it.name,
                        quantity = formatQuantity(it.quantity),
                        unit = it.unit,
                    )
                }.ifEmpty { listOf(newIngredientDraft()) },
                videos = videos.map { VideoDraft(nextLocalId++, it.title, it.url) },
                initialized = true,
            )
        }
    }

    // ---- 基本字段 ----

    fun onNameChange(v: String) = update { it.copy(name = v) }
    fun onDescriptionChange(v: String) = update { it.copy(description = v) }
    fun onStepsChange(v: String) = update { it.copy(steps = v) }
    fun onCategoryChange(v: String) = update { it.copy(category = v) }
    fun onPasteTextChange(v: String) = update { it.copy(pasteText = v) }

    fun onTypeChange(type: String) = update { it.copy(type = type) }

    fun onCoverSelected(path: String) = update { it.copy(coverPhotoPath = path) }

    fun onBakingTempChange(v: String) = update { it.copy(bakingTemp = v.filterDigits()) }
    fun onBakingTimeChange(v: String) = update { it.copy(bakingTime = v.filterDigits()) }
    fun onServingsChange(v: String) = update { it.copy(servings = v.filterDigits()) }

    // ---- 食材 ----

    fun addIngredient() = update { it.copy(ingredients = it.ingredients + newIngredientDraft()) }

    fun removeIngredient(localId: Long) = update { s ->
        val remaining = s.ingredients.filterNot { it.localId == localId }
        s.copy(ingredients = remaining.ifEmpty { listOf(newIngredientDraft()) })
    }

    fun updateIngredient(localId: Long, name: String, quantity: String, unit: String) = update { s ->
        s.copy(ingredients = s.ingredients.map {
            if (it.localId == localId) it.copy(name = name, quantity = quantity, unit = unit) else it
        })
    }

    // ---- 视频链接 ----

    fun addVideo() = update { it.copy(videos = it.videos + VideoDraft(nextLocalId++)) }

    fun removeVideo(localId: Long) = update { s ->
        s.copy(videos = s.videos.filterNot { it.localId == localId })
    }

    fun updateVideo(localId: Long, title: String, url: String) = update { s ->
        s.copy(videos = s.videos.map {
            if (it.localId == localId) it.copy(title = title, url = url) else it
        })
    }

    // ---- AI ----

    /** 解析粘贴的文字，把识别出的食材合并进列表。 */
    fun extractFromText() {
        val text = _state.value.pasteText
        if (text.isBlank()) {
            send("请先粘贴菜谱文字或做法描述")
            return
        }
        viewModelScope.launch {
            setLoading(true, "AI 正在识别食材…")
            try {
                val apiKey = settings.apiKey.first()
                val model = settings.textModel.first()
                val parsed = extractor.extract(apiKey, model, text)
                mergeIngredients(parsed.map { Triple(it.name, it.quantity, it.unit) })
                send("已识别 ${parsed.size} 种食材，请核对")
            } catch (e: AiException) {
                send(e.message ?: "解析失败，请重试")
            } catch (e: Exception) {
                send("解析失败：${e.message ?: "未知错误"}")
            } finally {
                setLoading(false)
            }
        }
    }

    /** 解析菜谱截图，回填整份菜谱表单。 */
    fun parseScreenshot(path: String) {
        viewModelScope.launch {
            setLoading(true, "AI 正在识别截图…")
            try {
                val apiKey = settings.apiKey.first()
                val model = settings.visionModel.first()
                val dataUrl = ImageStore.toDataUrl(path)
                val parsed = screenshotParser.parse(apiKey, model, dataUrl)
                applyParsedRecipe(parsed)
                if (_state.value.coverPhotoPath.isNullOrBlank()) {
                    update { it.copy(coverPhotoPath = path) }
                }
                send("已从截图生成菜谱，请核对后保存")
            } catch (e: AiException) {
                send(e.message ?: "识别失败，请重试")
            } catch (e: Exception) {
                send("识别失败：${e.message ?: "未知错误"}")
            } finally {
                setLoading(false)
            }
        }
    }

    // ---- 链接导入 / 分享导入 ----

    fun onLinkChange(v: String) = update { it.copy(linkText = v) }

    /** 「从链接导入」按钮：抓网页 → 整理成菜谱 → 回填表单。 */
    fun importFromUrl() {
        val url = _state.value.linkText.trim()
        if (url.isBlank()) {
            send("请先粘贴菜谱网页链接")
            return
        }
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            send("链接要以 http:// 或 https:// 开头")
            return
        }
        parseFromUrl(url)
    }

    /**
     * 处理分享进来的一段内容。可能是整条链接，也可能是
     * 「标题 + 链接 + 复制此链接打开App」这种混合体。
     *
     * 先把链接剥出来填进链接框（用户能看见、也能改），
     * 剩下的正文够长就按文字解析 —— 分享出来的往往本来就是正文，
     * 再去抓那个链接反而可能撞上登录墙或空壳页面。
     */
    private fun importShared(raw: String) {
        val text = raw.trim()
        if (text.isBlank()) return
        val url = extractUrl(text)
        val rest = if (url == null) text else text.replace(url, " ").trim()

        // 整段原样留在粘贴框里：自动解析失败时用户还能手动点「AI 解析食材」重试
        update { it.copy(pasteText = text, linkText = url.orEmpty()) }

        when {
            rest.length >= SHARED_TEXT_MIN_CHARS -> parseTextContent(text)
            url != null -> parseFromUrl(url)
            else -> send("分享过来的内容里没有链接，也不像菜谱正文。可以自己复制菜谱文字再粘贴。")
        }
    }

    /** 抓网页并整理成菜谱。 */
    private fun parseFromUrl(url: String) {
        viewModelScope.launch {
            setLoading(true, "正在抓取网页…")
            try {
                val apiKey = settings.apiKey.first()
                val model = settings.textModel.first()
                val parsed = recipeParser.parseUrl(apiKey, model, url)
                applyParsedRecipe(parsed)
                rememberSourceLink(url)
                send("已从网页整理出菜谱，请核对后保存")
            } catch (e: AiException) {
                send(e.message ?: "导入失败，请重试")
            } catch (e: Exception) {
                send("导入失败：${e.message ?: "未知错误"}")
            } finally {
                setLoading(false)
            }
        }
    }

    /** 按正文整理成菜谱（分享进来的内容）。 */
    private fun parseTextContent(text: String) {
        viewModelScope.launch {
            setLoading(true, "AI 正在整理菜谱…")
            try {
                val apiKey = settings.apiKey.first()
                val model = settings.textModel.first()
                val parsed = recipeParser.parseContent(apiKey, model, text)
                applyParsedRecipe(parsed)
                send("已整理出菜谱，请核对后保存")
            } catch (e: AiException) {
                send(e.message ?: "整理失败，请重试")
            } catch (e: Exception) {
                send("整理失败：${e.message ?: "未知错误"}")
            } finally {
                setLoading(false)
            }
        }
    }

    /** 把来源链接存进「视频教学」区，以后想回原站看看还在。 */
    private fun rememberSourceLink(url: String) = update { s ->
        if (s.videos.any { it.url == url }) s
        else s.copy(videos = s.videos + VideoDraft(nextLocalId++, "原菜谱链接", url))
    }

    private fun applyParsedRecipe(parsed: ParsedRecipe) = update { s ->
        s.copy(
            name = parsed.name.ifBlank { s.name },
            description = parsed.description.ifBlank { s.description },
            steps = parsed.steps.ifBlank { s.steps },
            type = if (parsed.isDessert) Recipe.TYPE_DESSERT else s.type,
            bakingTemp = parsed.bakingTempC?.toString() ?: s.bakingTemp,
            bakingTime = parsed.bakingTimeMin?.toString() ?: s.bakingTime,
            servings = parsed.servings?.toString() ?: s.servings,
            ingredients = if (parsed.ingredients.isEmpty()) {
                s.ingredients
            } else {
                parsed.ingredients.map {
                    IngredientDraft(
                        localId = nextLocalId++,
                        name = it.name,
                        quantity = formatQuantity(it.quantity),
                        unit = it.unit,
                    )
                }
            },
        )
    }

    /** 把识别出的食材合并进当前列表：跳过空行与重名项。 */
    private fun mergeIngredients(items: List<Triple<String, Double, String>>) {
        val existingNames = _state.value.ingredients
            .filter { it.name.isNotBlank() }
            .map { it.name.trim() }
            .toMutableSet()

        val added = items
            .filter { it.first.isNotBlank() && existingNames.add(it.first.trim()) }
            .map {
                IngredientDraft(
                    localId = nextLocalId++,
                    name = it.first.trim(),
                    quantity = formatQuantity(it.second),
                    unit = it.third,
                )
            }

        update { s ->
            val kept = s.ingredients.filter { it.name.isNotBlank() }
            s.copy(ingredients = kept + added)
        }
    }

    // ---- 保存 ----

    fun save() {
        val s = _state.value
        if (!s.canSave) {
            send("请先填写菜名")
            return
        }
        viewModelScope.launch {
            setLoading(true, "正在保存…")
            try {
                val existing = if (s.recipeId > 0) repository.getRecipe(s.recipeId) else null
                val recipe = Recipe(
                    id = s.recipeId,
                    name = s.name.trim(),
                    description = s.description.trim(),
                    steps = s.steps.trim(),
                    category = s.category.trim(),
                    type = s.type,
                    coverPhotoPath = s.coverPhotoPath,
                    cookCount = existing?.cookCount ?: 0,
                    lastCookedAt = existing?.lastCookedAt,
                    bakingTempC = s.bakingTemp.takeIf { s.isDessert }?.toIntOrNull(),
                    bakingTimeMin = s.bakingTime.takeIf { s.isDessert }?.toIntOrNull(),
                    servings = s.servings.takeIf { s.isDessert }?.toIntOrNull(),
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                )
                val ingredients = s.ingredients.map {
                    Ingredient(
                        recipeId = s.recipeId,
                        name = it.name.trim(),
                        quantity = it.quantity.toDoubleOrNull() ?: 0.0,
                        unit = it.unit.trim(),
                    )
                }
                val videos = s.videos.map {
                    VideoLink(recipeId = s.recipeId, title = it.title.trim(), url = it.url.trim())
                }
                val id = repository.saveRecipe(recipe, ingredients, videos)
                _saved.send(id)
            } catch (e: Exception) {
                send("保存失败：${e.message ?: "未知错误"}")
            } finally {
                setLoading(false)
            }
        }
    }

    // ---- 内部工具 ----

    private fun newIngredientDraft() = IngredientDraft(localId = nextLocalId++)

    private fun update(block: (RecipeFormState) -> RecipeFormState) {
        _state.value = block(_state.value)
    }

    private fun setLoading(value: Boolean, message: String = "") = update {
        it.copy(loading = value, loadingMessage = message)
    }

    private fun send(message: String) {
        viewModelScope.launch { _messages.send(message) }
    }

    private fun String.filterDigits(): String = filter { it.isDigit() }

    private companion object {
        /** 正文短于这个长度就当它不是菜谱正文，转而试着抓链接。 */
        const val SHARED_TEXT_MIN_CHARS = 30

        /**
         * 保守取第一个 http(s) 链接：遇到空白或汉字就停 ——
         * 分享文案常写成「红烧肉 https://xxx.com/a 快来看看」，
         * 不排掉汉字会把后面一整句都吞进链接里。
         */
        val URL_REGEX = Regex("https?://[^\\s\\u4e00-\\u9fff\"'<>]+", RegexOption.IGNORE_CASE)

        /** 链接尾巴上常粘着中文标点或句号，剥掉。 */
        val TRAILING_JUNK = charArrayOf(
            '.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'',
            '。', '，', '、', '；', '：', '！', '？', '）', '】', '》', '」', '』',
        )

        fun extractUrl(text: String): String? = URL_REGEX.find(text)
            ?.value
            ?.trimEnd(*TRAILING_JUNK)
            // "https://" 之后至少得有点东西，否则不算链接
            ?.takeIf { it.length > "https://".length + 2 }
    }
}
