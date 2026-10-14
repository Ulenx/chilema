package com.caidan.menu.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.data.repo.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortMode(val label: String) {
    RECENT("最近添加"),
    MOST_COOKED("最常做"),
    LAST_COOKED("最近做过"),
    NAME("按名称"),
}

/**
 * 左栏选中的那一项。外卖点单式的分类栏把「类型」和「分类」两个维度压成了一维，
 * 用户不必再在两行 chip 之间来回点。
 *
 * 「甜点」走 type 而不是分类：type 才是决定烘焙参数那块 UI 出不出、
 * 以及新建菜谱时默认类型的东西。
 */
sealed interface RailSelection {
    /**
     * LazyColumn 的稳定 key。
     *
     * 不能拿标签当 key —— 分类名是用户随手填的，真有人把分类就叫「甜点」，
     * 那就跟特殊项撞上了。
     */
    val key: String

    /** 全部菜谱，不筛。 */
    data object All : RailSelection {
        override val key = "all"
    }

    /** 只看甜点。 */
    data object Dessert : RailSelection {
        override val key = "dessert"
    }

    /** 只看某个分类。 */
    data class Category(val name: String) : RailSelection {
        override val key get() = "category:$name"
    }
}

/** 左栏的一项：能选的范围 + 这一项下的菜谱数。 */
data class RailEntry(
    val selection: RailSelection,
    val label: String,
    val count: Int,
)

/**
 * 列表的筛选条件。分类筛选已经并进左栏（[RailSelection]），不在这里。
 *
 * 打包成一个状态不是为了好看：`combine` 只有 2–5 个流的重载，而这些筛选加上
 * 菜谱流和评分流会超过重载上限。打包后正好降到 3 个。
 */
private data class RecipeFilters(
    val query: String = "",
    val sort: SortMode = SortMode.RECENT,
    val rail: RailSelection = RailSelection.All,
)

data class RecipeListUiState(
    val recipes: List<Recipe> = emptyList(),
    /** 菜谱 id → 平均星级。没有打过分的不在表里。 */
    val ratings: Map<Long, Double> = emptyMap(),
    /** 左栏的条目，带计数。 */
    val railEntries: List<RailEntry> = emptyList(),
    val selectedRail: RailSelection = RailSelection.All,
    val query: String = "",
    val sortMode: SortMode = SortMode.RECENT,
    val totalCount: Int = 0,
    /**
     * 菜单里一共攒了几道。
     *
     * 不受左边分类栏和搜索影响 —— 底部那条提示说的是「我挑了多少」，
     * 跟当前在看哪一栏没关系。
     */
    val menuCount: Int = 0,
)

class RecipeListViewModel(private val repository: RecipeRepository) : ViewModel() {

    private val _filters = MutableStateFlow(RecipeFilters())

    // 菜谱和甜点合并成一个列表，靠左栏筛，不再各开一个 Tab
    private val recipes = repository.observeAll()
    private val ratings = repository.observeRatings()

    val uiState: StateFlow<RecipeListUiState> =
        combine(recipes, ratings, _filters) { list, ratingRows, f ->
            val entries = list.toRailEntries()
            // 选中的项可能已经不存在了（最后一道甜点被删掉、分类名被改掉），
            // 回落到「全部」，免得停在「左栏没这项、右边也空着」的死状态
            val rail = f.rail.takeIf { sel -> entries.any { it.selection == sel } }
                ?: RailSelection.All
            val filtered = list
                .filterByRail(rail)
                .filter { f.query.isBlank() || it.matches(f.query) }
            RecipeListUiState(
                recipes = filtered.sortedWith(f.sort.comparator()),
                ratings = ratingRows.associate { it.recipeId to (it.avgRating ?: 0.0) },
                railEntries = entries,
                selectedRail = rail,
                query = f.query,
                sortMode = f.sort,
                totalCount = list.size,
                menuCount = list.count { it.inMenu },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeListUiState())

    fun onQueryChange(value: String) = _filters.update { it.copy(query = value) }

    fun onSortChange(mode: SortMode) = _filters.update { it.copy(sort = mode) }

    fun onRailSelect(value: RailSelection) = _filters.update { it.copy(rail = value) }

    /**
     * 把一道菜加进 / 移出「菜单」。
     *
     * 只动名单，菜谱本身不受影响 —— 菜单是一份精选，不是「删食谱」。
     */
    fun toggleMenu(recipe: Recipe) {
        viewModelScope.launch { repository.setInMenu(recipe.id, !recipe.inMenu) }
    }

    /**
     * 左栏的条目：全部 + 甜点 + 各个分类，各带菜谱数。
     *
     * 计数按全部菜谱算，不受搜索词影响 —— 它是分类本身的规模，不是当前筛选的结果。
     * 计数为 0 的不列：分类本来就从现有菜谱里长出来，只有「甜点」可能是 0。
     */
    private fun List<Recipe>.toRailEntries(): List<RailEntry> {
        val entries = mutableListOf(RailEntry(RailSelection.All, "全部", size))
        val desserts = count { it.type == Recipe.TYPE_DESSERT }
        if (desserts > 0) entries += RailEntry(RailSelection.Dessert, "甜点", desserts)
        map { it.category.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedBy { (name, _) -> name }
            .forEach { (name, count) ->
                entries += RailEntry(RailSelection.Category(name), name, count)
            }
        return entries
    }

    private fun List<Recipe>.filterByRail(rail: RailSelection): List<Recipe> = when (rail) {
        RailSelection.All -> this
        RailSelection.Dessert -> filter { it.type == Recipe.TYPE_DESSERT }
        is RailSelection.Category -> filter { it.category.trim() == rail.name }
    }

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
