package com.caidan.menu.ui.fridge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caidan.menu.data.ai.AiException
import com.caidan.menu.data.ai.FridgeParser
import com.caidan.menu.data.local.entity.FridgeItem
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.data.repo.FridgeRepository
import com.caidan.menu.data.repo.RecipeRepository
import com.caidan.menu.data.repo.SettingsRepository
import com.caidan.menu.util.ImageStore
import com.caidan.menu.util.formatQuantity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** 小票/截图识别回来、还没落库的一条食材。数量用字符串存，方便输入中途的「2.」。 */
data class DraftItem(
    val localId: Long,
    val name: String = "",
    val quantity: String = "",
    val unit: String = "",
)

data class FridgeFormState(
    val itemId: Long = 0L,
    val name: String = "",
    val quantity: String = "",
    val unit: String = "",
    val bigCategory: String = FridgeItem.BIG_INGREDIENT,
    val category: String = "",
    val purchaseDate: Long? = null,
    val expiryDate: Long? = null,
    val recipeId: Long? = null,
    val note: String = "",
    /** 条目照片在私有目录中的路径；批量整理模式下不单独选图 */
    val photoPath: String? = null,
    val source: String = FridgeItem.SOURCE_MANUAL,
    /** 非空时进入「批量整理」模式：忽略单条的名称/数量/单位，只保存草稿列表 */
    val drafts: List<DraftItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMessage: String = "",
    val initialized: Boolean = false,
) {
    val isIngredient: Boolean get() = bigCategory == FridgeItem.BIG_INGREDIENT
    val isEditing: Boolean get() = itemId > 0L
    val batching: Boolean get() = drafts.isNotEmpty()
    val canSave: Boolean get() = if (batching) true else name.isNotBlank()
}

class FridgeEditViewModel(
    private val fridgeRepository: FridgeRepository,
    recipeRepository: RecipeRepository,
    private val parser: FridgeParser,
    private val settings: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: Long = savedStateHandle.get<Long>("itemId") ?: 0L

    private val _state = MutableStateFlow(FridgeFormState(itemId = itemId))
    val state: StateFlow<FridgeFormState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** 保存完成，通知页面返回。 */
    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    /** 供「关联菜谱」下拉使用。 */
    val recipes: StateFlow<List<Recipe>> = recipeRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var nextLocalId = 1L

    init {
        if (itemId > 0L) loadExisting(itemId) else _state.value = _state.value.copy(initialized = true)
    }

    private fun loadExisting(id: Long) {
        viewModelScope.launch {
            val item = fridgeRepository.get(id) ?: return@launch
            _state.value = FridgeFormState(
                itemId = item.id,
                name = item.name,
                quantity = formatQuantity(item.quantity),
                unit = item.unit,
                bigCategory = item.bigCategory,
                category = item.category,
                purchaseDate = item.purchaseDate,
                expiryDate = item.expiryDate,
                recipeId = item.recipeId,
                note = item.note,
                photoPath = item.photoPath,
                source = item.source,
                initialized = true,
            )
        }
    }

    // ---- 单条字段 ----

    fun onNameChange(v: String) = update { it.copy(name = v) }
    fun onQuantityChange(v: String) = update { it.copy(quantity = v.filterAmount()) }
    fun onUnitChange(v: String) = update { it.copy(unit = v) }
    fun onNoteChange(v: String) = update { it.copy(note = v) }

    fun onBigCategoryChange(value: String) = update {
        // 调料不细分，切过去时清掉子分类
        it.copy(
            bigCategory = value,
            category = if (value == FridgeItem.BIG_INGREDIENT) it.category else "",
        )
    }

    fun onCategoryChange(value: String) = update { it.copy(category = value) }

    fun onPurchaseDateChange(millis: Long?) = update { it.copy(purchaseDate = millis) }

    fun onExpiryDateChange(millis: Long?) = update { it.copy(expiryDate = millis) }

    fun onRecipeChange(id: Long?) = update { it.copy(recipeId = id) }

    fun onPhotoSelected(path: String?) = update { it.copy(photoPath = path) }

    /**
     * 填了过期日期但比购买日期还早，多半是点错了，保存前挡一下。
     * 日期都取的是当天 0 点，所以同一天不算冲突。
     */
    private fun validateDates(s: FridgeFormState): String? {
        val purchase = s.purchaseDate
        val expiry = s.expiryDate
        if (purchase != null && expiry != null && expiry < purchase) {
            return "过期日期早于购买日期，请检查一下"
        }
        return null
    }

    // ---- 草稿（批量整理） ----

    fun addDraft() = update { it.copy(drafts = it.drafts + DraftItem(nextLocalId++)) }

    fun removeDraft(localId: Long) = update { s ->
        val remaining = s.drafts.filterNot { it.localId == localId }
        // 删光了就回到单条录入，且来源改回手动
        s.copy(
            drafts = remaining,
            source = if (remaining.isEmpty() && s.itemId == 0L) FridgeItem.SOURCE_MANUAL else s.source,
        )
    }

    fun updateDraft(localId: Long, name: String, quantity: String, unit: String) = update { s ->
        s.copy(drafts = s.drafts.map {
            if (it.localId == localId) {
                it.copy(name = name, quantity = quantity.filterAmount(), unit = unit)
            } else {
                it
            }
        })
    }

    /** 放弃批量整理，回到单条手动录入。 */
    fun clearDrafts() = update {
        it.copy(drafts = emptyList(), source = FridgeItem.SOURCE_MANUAL)
    }

    // ---- AI 识别 ----

    /**
     * 把小票/订单截图交给 AI，识别出的条目追加进草稿列表。
     * @param source [FridgeItem.SOURCE_RECEIPT] 或 [FridgeItem.SOURCE_SCREENSHOT]
     */
    fun importFromImage(path: String, source: String) {
        viewModelScope.launch {
            setLoading(true, "AI 正在识别小票…")
            try {
                val apiKey = settings.apiKey.first()
                val model = settings.visionModel.first()
                val dataUrl = ImageStore.toDataUrl(path)
                val parsed = parser.parse(apiKey, model, dataUrl)
                update { s ->
                    // 已经手打的单条内容先变成草稿，别被识别结果冲掉
                    val typed = if (s.name.isBlank()) {
                        emptyList()
                    } else {
                        listOf(DraftItem(nextLocalId++, s.name, s.quantity, s.unit))
                    }
                    s.copy(
                        name = "",
                        quantity = "",
                        unit = "",
                        drafts = typed +
                            s.drafts.filter { it.name.isNotBlank() } +
                            parsed.map {
                                DraftItem(
                                    localId = nextLocalId++,
                                    name = it.name,
                                    quantity = formatQuantity(it.quantity),
                                    unit = it.unit,
                                )
                            },
                        source = source,
                    )
                }
                send("识别到 ${parsed.size} 条，请核对后保存")
            } catch (e: AiException) {
                send(e.message ?: "识别失败，请重试")
            } catch (e: Exception) {
                send("识别失败：${e.message ?: "未知错误"}")
            } finally {
                setLoading(false)
                // 小票图只是识别用的，识别完就删，不占空间
                runCatching { File(path).delete() }
            }
        }
    }

    // ---- 保存 ----

    fun save() {
        val s = _state.value
        if (!s.canSave) {
            send("请填写名称")
            return
        }
        validateDates(s)?.let { send(it); return }

        viewModelScope.launch {
            setLoading(true, "正在保存…")
            try {
                if (s.batching) {
                    val count = fridgeRepository.saveAll(
                        s.drafts
                            .filter { it.name.isNotBlank() }
                            .map { draft -> s.toFridgeItem(draft.name, draft.quantity, draft.unit) }
                    )
                    send("已存入 $count 条")
                } else {
                    val existing = if (s.itemId > 0L) fridgeRepository.get(s.itemId) else null
                    fridgeRepository.save(
                        s.toFridgeItem(s.name, s.quantity, s.unit)
                            .copy(id = s.itemId, createdAt = existing?.createdAt ?: System.currentTimeMillis())
                    )
                }
                _saved.send(Unit)
            } catch (e: Exception) {
                send("保存失败：${e.message ?: "未知错误"}")
            } finally {
                setLoading(false)
            }
        }
    }

    /** 草稿批量保存时，大类/子分类/日期/菜谱/备注/图片 用当前表单里选的同一套。 */
    private fun FridgeFormState.toFridgeItem(
        name: String,
        quantity: String,
        unit: String,
    ) = FridgeItem(
        name = name.trim(),
        bigCategory = bigCategory,
        category = if (isIngredient) category else "",
        quantity = quantity.toDoubleOrNull() ?: 0.0,
        unit = unit.trim(),
        purchaseDate = purchaseDate,
        expiryDate = expiryDate,
        source = source,
        recipeId = recipeId,
        photoPath = photoPath,
        note = note.trim(),
    )

    // ---- 内部工具 ----

    private fun update(block: (FridgeFormState) -> FridgeFormState) {
        _state.value = block(_state.value)
    }

    private fun setLoading(value: Boolean, message: String = "") = update {
        it.copy(loading = value, loadingMessage = message)
    }

    private fun send(message: String) {
        viewModelScope.launch { _messages.send(message) }
    }

    /** 数量允许小数点和一位小数，但不允许出现字母或第二个小数点。 */
    private fun String.filterAmount(): String {
        val cleaned = filter { it.isDigit() || it == '.' }
        val firstDot = cleaned.indexOf('.')
        if (firstDot < 0) return cleaned
        return cleaned.substring(0, firstDot + 1) +
            cleaned.substring(firstDot + 1).replace(".", "")
    }
}
