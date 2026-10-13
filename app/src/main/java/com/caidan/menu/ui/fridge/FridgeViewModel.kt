package com.caidan.menu.ui.fridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caidan.menu.data.local.entity.FridgeItem
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.data.repo.FridgeRepository
import com.caidan.menu.data.repo.RecipeRepository
import com.caidan.menu.util.ExpiryStatus
import com.caidan.menu.util.expiryStatusOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 保质期筛选。 */
enum class ExpiryFilter(val label: String) {
    ALL("全部"),
    NEAR("临期"),
    EXPIRED("过期"),
}

/** 列表里的一行：库存 + 算好的保质期状态 + 关联菜名。 */
data class FridgeRow(
    val item: FridgeItem,
    val status: ExpiryStatus,
    val recipeName: String?,
)

data class FridgeUiState(
    val bigCategory: String = FridgeItem.BIG_INGREDIENT,
    val category: String? = null,
    val query: String = "",
    val expiryFilter: ExpiryFilter = ExpiryFilter.ALL,
    /** 当前大类下实际出现过的子分类，用来出 chips */
    val categories: List<String> = emptyList(),
    val rows: List<FridgeRow> = emptyList(),
    val nearCount: Int = 0,
    val expiredCount: Int = 0,
    /** 当前大类的总条数，用来区分「一条都没有」和「筛没了」 */
    val totalInBigCategory: Int = 0,
) {
    val isIngredient: Boolean get() = bigCategory == FridgeItem.BIG_INGREDIENT
}

/** 只放筛选条件，数据本身来自数据库。 */
private data class Filters(
    val bigCategory: String = FridgeItem.BIG_INGREDIENT,
    val category: String? = null,
    val query: String = "",
    val expiryFilter: ExpiryFilter = ExpiryFilter.ALL,
)

class FridgeViewModel(
    private val fridgeRepository: FridgeRepository,
    recipeRepository: RecipeRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(Filters())

    val state: StateFlow<FridgeUiState> = combine(
        fridgeRepository.observeAll(),
        recipeRepository.observeAll(),
        filters,
    ) { items, recipes, f -> buildState(items, recipes, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FridgeUiState())

    // ---- 筛选 ----

    fun onBigCategoryChange(value: String) {
        // 切大类时清掉子分类筛选，否则「调料」会带着「肉类」把列表筛空
        filters.value = filters.value.copy(bigCategory = value, category = null)
    }

    fun onCategoryChange(value: String?) {
        filters.value = filters.value.copy(category = value)
    }

    fun onQueryChange(value: String) {
        filters.value = filters.value.copy(query = value)
    }

    fun onExpiryFilterChange(value: ExpiryFilter) {
        filters.value = filters.value.copy(expiryFilter = value)
    }

    // ---- 增删 ----

    /** 删除后可以用 [restore] 撤回；重建的条目会拿到新的 id，其余字段不变。 */
    fun delete(item: FridgeItem) {
        viewModelScope.launch { fridgeRepository.delete(item) }
    }

    fun restore(item: FridgeItem) {
        viewModelScope.launch { fridgeRepository.save(item.copy(id = 0L)) }
    }

    // ---- 内部 ----

    private fun buildState(
        all: List<FridgeItem>,
        recipes: List<Recipe>,
        f: Filters,
    ): FridgeUiState {
        val inBig = all.filter { it.bigCategory == f.bigCategory }
        val recipeNames = recipes.associate { it.id to it.name }

        // 提醒卡按整个大类统计，不受子分类/搜索影响，免得一筛选数字就变
        val near = inBig.count { rowStatus(it) == ExpiryStatus.NEAR }
        val expired = inBig.count { rowStatus(it) == ExpiryStatus.EXPIRED }

        val keyword = f.query.trim()
        val rows = inBig
            .filter { f.category == null || it.category == f.category }
            .filter {
                keyword.isEmpty() ||
                    it.name.contains(keyword, ignoreCase = true) ||
                    it.note.contains(keyword, ignoreCase = true)
            }
            .filter { matchesFilter(rowStatus(it), f.expiryFilter) }
            .map {
                FridgeRow(
                    item = it,
                    status = rowStatus(it),
                    recipeName = it.recipeId?.let(recipeNames::get),
                )
            }
            .sortedWith(rowOrder)

        return FridgeUiState(
            bigCategory = f.bigCategory,
            category = f.category,
            query = f.query,
            expiryFilter = f.expiryFilter,
            categories = FridgeItem.CATEGORIES.filter { c -> inBig.any { it.category == c } },
            rows = rows,
            nearCount = near,
            expiredCount = expired,
            totalInBigCategory = inBig.size,
        )
    }

    private fun matchesFilter(status: ExpiryStatus, filter: ExpiryFilter): Boolean = when (filter) {
        ExpiryFilter.ALL -> true
        ExpiryFilter.NEAR -> status == ExpiryStatus.NEAR
        ExpiryFilter.EXPIRED -> status == ExpiryStatus.EXPIRED
    }

    /**
     * 条目的保质期状态。数量已经扣到 0 的（做菜扣完了）一律当「无状态」：
     * 东西都吃完了，还标红提醒「快过期了」、还占着「临期 N」的名额是错的。
     * 条目本身仍留在列表里可见，只是不再报警。
     */
    private fun rowStatus(item: FridgeItem): ExpiryStatus =
        if (item.quantity <= 0.0) ExpiryStatus.NONE else expiryStatusOf(item.expiryDate)

    private companion object {
        /** 过期的最要紧，其次临期；同档内先到期的排前面。 */
        val rowOrder = compareBy<FridgeRow>(
            { expiryRank(it.status) },
            { it.item.expiryDate ?: Long.MAX_VALUE },
            { it.item.name },
        )

        fun expiryRank(status: ExpiryStatus): Int = when (status) {
            ExpiryStatus.EXPIRED -> 0
            ExpiryStatus.NEAR -> 1
            ExpiryStatus.FRESH -> 2
            ExpiryStatus.NONE -> 3
        }
    }
}
