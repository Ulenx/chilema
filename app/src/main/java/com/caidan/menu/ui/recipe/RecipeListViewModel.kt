package com.caidan.menu.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.data.repo.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class SortMode(val label: String) {
    RECENT("最近添加"),
    MOST_COOKED("最常做"),
    LAST_COOKED("最近做过"),
    NAME("按名称"),
}

/** 类型筛选 chip 的选项。「全部」= 不筛类型。 */
val TYPE_LABELS = listOf("全部", "家常菜", "甜点")

/** chip 标签 → type 值。认不出来的（含「全部」）一律当不筛。 */
fun typeOfLabel(label: String): String? = when (label) {
    "家常菜" -> Recipe.TYPE_DISH
    "甜点" -> Recipe.TYPE_DESSERT
    else -> null
}

/** type 值 → chip 标签，给 ChipRow 的 selected 用。 */
fun labelOfType(type: String?): String = when (type) {
    Recipe.TYPE_DISH -> "家常菜"
    Recipe.TYPE_DESSERT -> "甜点"
    else -> "全部"
}

/**
 * 列表的四个筛选条件。
 *
 * 打包成一个状态不是为了好看：`combine` 只有 2–5 个流的重载，而这四个筛选加上
 * 菜谱流和评分流就是 6 个，超过重载上限。打包后正好降到 3 个。
 */
private data class RecipeFilters(
    val query: String = "",
    val sort: SortMode = SortMode.RECENT,
    val category: String? = null,
    val type: String? = null,
)

data class RecipeListUiState(
    val recipes: List<Recipe> = emptyList(),
    /** 菜谱 id → 平均星级。没有打过分的不在表里。 */
    val ratings: Map<Long, Double> = emptyMap(),
    val categories: List<String> = emptyList(),
    val query: String = "",
    val sortMode: SortMode = SortMode.RECENT,
    val selectedCategory: String? = null,
    val selectedType: String? = null,
    val totalCount: Int = 0,
)

class RecipeListViewModel(repository: RecipeRepository) : ViewModel() {

    private val _filters = MutableStateFlow(RecipeFilters())

    // 菜谱和甜点合并成一个列表，靠上面的类型 chip 筛，不再各开一个 Tab
    private val recipes = repository.observeAll()
    private val ratings = repository.observeRatings()

    /** 分类 chip 跟着类型筛选走：只看甜点时不该把家常菜的分类也列出来。 */
    val categories: StateFlow<List<String>> =
        combine(recipes, _filters) { list, f -> list.filterByType(f.type) }
            .map { list -> list.map { it.category }.filter { it.isNotBlank() }.distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<RecipeListUiState> =
        combine(recipes, ratings, _filters) { list, ratingRows, f ->
            val scoped = list.filterByType(f.type)
            val filtered = scoped
                .filter { f.category == null || it.category == f.category }
                .filter { f.query.isBlank() || it.matches(f.query) }
            RecipeListUiState(
                recipes = filtered.sortedWith(f.sort.comparator()),
                ratings = ratingRows.associate { it.recipeId to (it.avgRating ?: 0.0) },
                query = f.query,
                sortMode = f.sort,
                selectedCategory = f.category,
                selectedType = f.type,
                // 用类型筛选后的数量，空态才分得清「真的一道菜都没有」和「这个筛选下没有」
                totalCount = scoped.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeListUiState())

    fun onQueryChange(value: String) = _filters.update { it.copy(query = value) }

    fun onSortChange(mode: SortMode) = _filters.update { it.copy(sort = mode) }

    fun onCategoryChange(value: String?) = _filters.update { it.copy(category = value) }

    /**
     * 切类型时顺手清掉分类筛选：分类是跟着类型走的，
     * 留着「川菜」再切到甜点会变成空列表，而分类 chip 里根本没有「川菜」可选，人就卡死了。
     */
    fun onTypeChange(value: String?) = _filters.update { it.copy(type = value, category = null) }

    private fun List<Recipe>.filterByType(type: String?): List<Recipe> =
        if (type == null) this else filter { it.type == type }

    private fun Recipe.matches(query: String): Boolean {
        val q = query.trim()
        return name.contains(q, ignoreCase = true) ||
            description.contains(q, ignoreCase = true) ||
            steps.contains(q, ignoreCase = true)
    }

    private fun SortMode.comparator(): Comparator<Recipe> = when (this) {
        SortMode.RECENT -> compareByDescending { it.createdAt }
        SortMode.MOST_COOKED -> compareByDescending<Recipe> { it.cookCount }
            .thenByDescending { it.lastCookedAt ?: 0L }
        SortMode.LAST_COOKED -> compareByDescending { it.lastCookedAt ?: 0L }
        SortMode.NAME -> compareBy { it.name }
    }
}
