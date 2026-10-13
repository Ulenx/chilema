package com.caidan.menu.ui.recipe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caidan.menu.data.local.entity.CookRecord
import com.caidan.menu.data.local.entity.FridgeItem
import com.caidan.menu.data.local.entity.Ingredient
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.data.local.entity.VideoLink
import com.caidan.menu.data.repo.FridgeRepository
import com.caidan.menu.data.repo.RecipeRepository
import com.caidan.menu.util.ExpiryStatus
import com.caidan.menu.util.expiryStatusOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 扣减确认框里的一行库存。 */
data class DeductLine(
    val item: FridgeItem,
    /** 计划从这条扣掉的量；不可扣时为 0 */
    val deduct: Double = 0.0,
    /** 不可扣的原因，可扣时为 null */
    val blockReason: String? = null,
    val checked: Boolean = true,
) {
    val deductible: Boolean get() = blockReason == null && deduct > 0.0
}

/**
 * 非 null 时显示扣减确认框。
 *
 * 备注、照片、星级、是否设封面是上一步 CookDialog 填的，那时对话框已经关掉了，
 * 只能由 ViewModel 先接着，等扣减确认完再一起落库。
 */
data class DeductPrompt(
    val note: String,
    val photoPath: String?,
    val setAsCover: Boolean,
    val rating: Int,
    val lines: List<DeductLine>,
)

data class RecipeDetailUiState(
    val recipe: Recipe? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val videos: List<VideoLink> = emptyList(),
    val cookRecords: List<CookRecord> = emptyList(),
    /** 历次平均星级；一次都没打过是 null。 */
    val rating: Double? = null,
    /** 参与平均的记录条数，用来显示「3 次评分」。 */
    val ratedCount: Int = 0,
    val deductPrompt: DeductPrompt? = null,
    val loaded: Boolean = false,
)

class RecipeDetailViewModel(
    private val repository: RecipeRepository,
    private val fridgeRepository: FridgeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val recipeId: Long = savedStateHandle.get<Long>("recipeId") ?: 0L

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** 菜谱被删除后发出，UI 据此退出详情页。 */
    private val _deleted = Channel<Unit>(Channel.BUFFERED)
    val deleted = _deleted.receiveAsFlow()

    /** 扣减确认框的状态；数据本身来自数据库，这个只放流程中的临时状态。 */
    private val _deductPrompt = MutableStateFlow<DeductPrompt?>(null)

    val uiState: StateFlow<RecipeDetailUiState> = combine(
        repository.observeRecipe(recipeId),
        repository.observeIngredients(recipeId),
        repository.observeVideos(recipeId),
        repository.observeCookRecords(recipeId),
        _deductPrompt,
    ) { recipe, ingredients, videos, records, prompt ->
        // 平均分直接从这道菜的记录里算，不用再加一个聚合查询
        val rated = records.mapNotNull { it.rating }.filter { it > 0 }
        RecipeDetailUiState(
            recipe = recipe,
            ingredients = ingredients,
            videos = videos,
            cookRecords = records,
            rating = rated.takeIf { it.isNotEmpty() }?.average(),
            ratedCount = rated.size,
            deductPrompt = prompt,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    /**
     * 记录一次做菜；[photoPath] 为成品照片在私有目录中的路径。
     * 先算一遍冰箱里能扣什么：有得扣就弹确认框，没有就直接记一笔。
     */
    fun recordCook(
        note: String,
        photoPath: String?,
        setAsCover: Boolean = false,
        rating: Int = 0,
    ) {
        viewModelScope.launch {
            // 算不出来（比如查询出错）就当没得扣，别把「记录一次」这件事整个卡住
            val lines = runCatching { buildDeductLines() }.getOrDefault(emptyList())
            if (lines.none { it.deductible }) {
                val why = if (lines.isEmpty()) {
                    "冰箱里没有对应食材，未扣库存"
                } else {
                    "冰箱里的对不上（过期或单位不一致），未扣库存"
                }
                cook(note, photoPath, setAsCover, rating, emptyMap(), why)
                return@launch
            }
            _deductPrompt.value = DeductPrompt(note, photoPath, setAsCover, rating, lines)
        }
    }

    /** 扣减确认框里勾选/取消一项。不可扣的行不允许被勾上。 */
    fun toggleDeductLine(id: Long) {
        _deductPrompt.value = _deductPrompt.value?.let { p ->
            p.copy(
                lines = p.lines.map {
                    if (it.item.id == id && it.deductible) it.copy(checked = !it.checked) else it
                }
            )
        }
    }

    /** 确认扣减并记录。 */
    fun confirmCook() {
        val prompt = _deductPrompt.value ?: return
        _deductPrompt.value = null
        // 一个库存 id 只会出现一次：同名同单位的用量在 buildDeductLines 里已经归并过
        val deductions = prompt.lines
            .filter { it.deductible && it.checked }
            .associate { it.item.id to it.deduct }
        viewModelScope.launch {
            cook(
                prompt.note,
                prompt.photoPath,
                prompt.setAsCover,
                prompt.rating,
                deductions,
                "没有勾选任何食材，未扣库存",
            )
        }
    }

    /** 放弃这次记录：备注、照片、扣减都不落库。 */
    fun cancelCook() {
        _deductPrompt.value = null
    }

    /**
     * 写入一次做菜记录，并扣掉 [deductions]（库存 id → 扣掉的量）。
     *
     * 顺序是「先记账，再扣库存」：记了没扣还能自己补一刀，
     * 反过来扣了没记，库存就凭空少了，查都没处查。
     */
    private suspend fun cook(
        note: String,
        photoPath: String?,
        setAsCover: Boolean,
        rating: Int,
        deductions: Map<Long, Double>,
        emptyDeductionNote: String,
    ) {
        try {
            // 0 星就是没打分，存成 null，和「老记录没打过分」是一个状态
            repository.recordCook(
                recipeId = recipeId,
                note = note.trim(),
                photoPath = photoPath,
                setAsCover = setAsCover,
                rating = rating.takeIf { it > 0 },
            )
        } catch (e: Exception) {
            _messages.send("记录失败：${e.message ?: "未知错误"}")
            return
        }

        if (deductions.isEmpty()) {
            _messages.send("已记录一次（$emptyDeductionNote）")
            return
        }

        val changed = try {
            fridgeRepository.applyDeductions(deductions)
        } catch (e: Exception) {
            _messages.send("已记录，但库存扣减失败：${e.message ?: "未知错误"}")
            return
        }
        val cover = if (setAsCover && !photoPath.isNullOrBlank()) "，封面也换成这张了" else ""
        _messages.send("已记录一次，扣了 $changed 项库存$cover")
    }

    /**
     * 列出这次做菜能从冰箱里扣掉的库存。
     *
     * 匹配规则和「购物清单对照库存」保持一套口径：同名、单位一致、没过期。
     * 同名但不可扣的行也会列出来（标上原因），否则用户看到「只扣了 2 项」会犯嘀咕。
     */
    private suspend fun buildDeductLines(): List<DeductLine> {
        val ingredients = repository.ingredientsOnce(recipeId).filter { it.name.isNotBlank() }
        if (ingredients.isEmpty()) return emptyList()

        val stockByName = fridgeRepository.observeAll().first().groupBy { it.name.trim() }
        val lines = mutableListOf<DeductLine>()

        // 先按「名称 + 单位」把菜谱用量归并：同一道菜可能写了两行「鸡蛋」，
        // 分两行各扣一次会生成两条同一条库存的扣减，后一条把前一条覆盖掉，少扣
        val needByKey = linkedMapOf<String, Double>()
        ingredients.forEach { ing ->
            val key = "${ing.name.trim()}|${ing.unit.trim().lowercase()}"
            needByKey[key] = (needByKey[key] ?: 0.0) + ing.quantity
        }

        needByKey.forEach { (key, totalNeed) ->
            val name = key.substringBefore('|')
            val unit = key.substringAfter('|')
            val stock = stockByName[name].orEmpty()
            if (stock.isEmpty()) return@forEach

            var left = totalNeed
            // 先扣快到期的：符合做饭习惯，也免得临期的东西放坏
            stock.sortedBy { it.expiryDate ?: Long.MAX_VALUE }.forEach { item ->
                val reason = when {
                    item.quantity <= 0.0 -> "已经用完了"
                    // 单位对不上宁可少扣，不能把「个」当「克」扣
                    !item.unit.trim().equals(unit, ignoreCase = true) ->
                        "单位不一致（冰箱记的是${item.unit.ifBlank { "无单位" }}）"
                    expiryStatusOf(item.expiryDate) == ExpiryStatus.EXPIRED -> "已过期，不扣"
                    totalNeed <= 0.0 -> "菜谱写的是「适量」，没法定量"
                    left <= 0.0 -> "这条够用了"
                    else -> null
                }
                val deduct = if (reason == null) minOf(left, item.quantity) else 0.0
                left -= deduct
                lines += DeductLine(item = item, deduct = deduct, blockReason = reason)
            }
        }
        return lines
    }

    /** 把某条成品记录里的照片设为封面。 */
    fun setCoverFromRecord(record: CookRecord) {
        val path = record.photoPath
        if (path.isNullOrBlank()) return
        viewModelScope.launch {
            try {
                repository.setCoverPhoto(recipeId, path)
                _messages.send("封面已更新")
            } catch (e: Exception) {
                _messages.send("设置失败：${e.message ?: "未知错误"}")
            }
        }
    }

    /**
     * 事后编辑某条成品记录：补/换照片、补打分、改备注。
     *
     * 不动做菜次数和「最近一次」时间 —— 改的是内容，不是「又做了一次」。
     */
    fun updateRecord(
        record: CookRecord,
        note: String,
        rating: Int,
        photoPath: String?,
        setAsCover: Boolean,
    ) {
        viewModelScope.launch {
            try {
                repository.updateCookRecord(
                    id = record.id,
                    note = note.trim(),
                    rating = rating.takeIf { it > 0 },
                    photoPath = photoPath,
                )
                // 只做加法：没勾就不碰现有封面。清掉照片也不会连累封面，
                // 封面指向的是文件本身，文件还在就照常显示。
                if (setAsCover && !photoPath.isNullOrBlank()) {
                    repository.setCoverPhoto(recipeId, photoPath)
                }
                _messages.send("记录已更新")
            } catch (e: Exception) {
                _messages.send("保存失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun deleteRecord(record: CookRecord) {
        viewModelScope.launch {
            try {
                repository.deleteCookRecord(record)
            } catch (e: Exception) {
                _messages.send("删除失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun deleteRecipe() {
        viewModelScope.launch {
            val recipe = repository.getRecipe(recipeId) ?: return@launch
            try {
                repository.deleteRecipe(recipe)
                _deleted.send(Unit)
            } catch (e: Exception) {
                _messages.send("删除失败：${e.message ?: "未知错误"}")
            }
        }
    }
}
