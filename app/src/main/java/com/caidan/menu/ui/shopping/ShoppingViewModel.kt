package com.caidan.menu.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.data.repo.FridgeRepository
import com.caidan.menu.data.repo.RecipeRepository
import com.caidan.menu.util.ExpiryStatus
import com.caidan.menu.util.expiryStatusOf
import com.caidan.menu.util.formatAmount
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 合并后的一条待买条目。 */
data class ShoppingItem(
    val key: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val sources: List<String>,
    /** 冰箱里同名、同单位、且没过去的库存合计 */
    val stockQuantity: Double = 0.0,
    /** 冰箱里有没有同名且没过去的（单位不一致也算有） */
    val inStock: Boolean = false,
) {
    /** 合并用量后的展示文本，如「3 个」「500 克」。 */
    val amountText: String get() = formatAmount(quantity, unit)

    /** 买之前先翻一下冰箱：够用、还差多少、还是根本没有。 */
    val stockText: String get() = when {
        !inStock -> "冰箱没有，需补买"
        stockQuantity <= 0.0 -> "冰箱已有"
        quantity <= 0.0 -> "冰箱已有 ${formatAmount(stockQuantity, unit)}"
        stockQuantity >= quantity -> "冰箱已有 ${formatAmount(stockQuantity, unit)}，够了"
        else -> "冰箱已有 ${formatAmount(stockQuantity, unit)}，还差 ${formatAmount(quantity - stockQuantity, unit)}"
    }
}

data class ShoppingUiState(
    val selectedIds: Set<Long> = emptySet(),
    val items: List<ShoppingItem> = emptyList(),
    val boughtKeys: Set<String> = emptySet(),
    val generated: Boolean = false,
    val generating: Boolean = false,
)

class ShoppingViewModel(
    private val repository: RecipeRepository,
    private val fridgeRepository: FridgeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ShoppingUiState())
    val state: StateFlow<ShoppingUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** 全部菜谱与甜点，供勾选。 */
    val recipes: StateFlow<List<Recipe>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleSelection(id: Long) {
        _state.value = _state.value.let { s ->
            val next = if (id in s.selectedIds) s.selectedIds - id else s.selectedIds + id
            s.copy(selectedIds = next)
        }
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedIds = emptySet())
    }

    /** 按「名称+单位」合并同名食材，用量求和，并对照冰箱库存标出还需不需要买。 */
    fun generateList() {
        val selected = _state.value.selectedIds
        if (selected.isEmpty()) {
            viewModelScope.launch { _messages.send("请先勾选至少一道菜") }
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(generating = true)
            try {
                // 已经过期的库存不算「家里还有」，不能拿它抵掉购物项
                val stockByName = fridgeRepository.observeAll().first()
                    .filter { expiryStatusOf(it.expiryDate) != ExpiryStatus.EXPIRED }
                    .groupBy { it.name.trim() }

                val buckets = linkedMapOf<String, ShoppingItem>()
                for (recipeId in selected) {
                    val recipe = repository.getRecipe(recipeId) ?: continue
                    repository.ingredientsOnce(recipeId).forEach { ing ->
                        val name = ing.name.trim()
                        if (name.isEmpty()) return@forEach
                        val unit = ing.unit.trim()
                        // 同名同单位才合并；单位不同（如「适量」vs「克」）分开列，避免错误相加
                        val key = "$name|$unit"
                        val existing = buckets[key]
                        buckets[key] = if (existing == null) {
                            // 同名就算冰箱里有；但只有单位也一样的才敢把数量加起来
                            val stock = stockByName[name].orEmpty()
                            ShoppingItem(
                                key = key,
                                name = name,
                                quantity = ing.quantity,
                                unit = unit,
                                sources = listOf(recipe.name),
                                stockQuantity = stock
                                    .filter { it.unit.trim().equals(unit, ignoreCase = true) }
                                    .sumOf { it.quantity },
                                inStock = stock.isNotEmpty(),
                            )
                        } else {
                            existing.copy(
                                quantity = existing.quantity + ing.quantity,
                                sources = (existing.sources + recipe.name).distinct(),
                            )
                        }
                    }
                }

                // 被多道菜共用的食材排前面，方便优先采购
                val items = buckets.values.sortedWith(
                    compareByDescending<ShoppingItem> { it.sources.size }.thenBy { it.name }
                )
                if (items.isEmpty()) {
                    _messages.send("选中的菜还没有录入食材")
                    _state.value = _state.value.copy(generating = false)
                    return@launch
                }
                _state.value = _state.value.copy(
                    items = items,
                    boughtKeys = emptySet(),
                    generated = true,
                    generating = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(generating = false)
                _messages.send("生成失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun toggleBought(key: String) {
        _state.value = _state.value.let { s ->
            val next = if (key in s.boughtKeys) s.boughtKeys - key else s.boughtKeys + key
            s.copy(boughtKeys = next)
        }
    }

    /** 清空所有勾选，便于下一轮采买。 */
    fun resetBought() {
        _state.value = _state.value.copy(boughtKeys = emptySet())
    }

    /** 回到选菜步骤。 */
    fun backToSelection() {
        _state.value = _state.value.copy(generated = false, items = emptyList(), boughtKeys = emptySet())
    }
}
