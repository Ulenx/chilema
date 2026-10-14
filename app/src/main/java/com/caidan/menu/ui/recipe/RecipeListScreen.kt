@file:OptIn(ExperimentalMaterial3Api::class)

package com.caidan.menu.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caidan.menu.AppContainer
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.ui.AppViewModelProvider
import com.caidan.menu.ui.components.CompactSearchField
import com.caidan.menu.ui.components.EmptyState
import com.caidan.menu.ui.components.PageHeader
import com.caidan.menu.ui.components.RecipeGridCard
import com.caidan.menu.util.greetingAt
import java.time.LocalTime

/** 左栏宽度。窄一点，把宽度留给右边网格。 */
private val RAIL_WIDTH = 84.dp

/**
 * 菜谱列表页，外卖点单式的左右分栏：
 * 左边一竖栏分类（带计数），右边搜索 + 排序 + 菜品网格，右下角新建按钮。
 *
 * 分类栏把「类型」和「分类」两个维度压成了一维 —— 详见 [RailSelection]。
 */
@Composable
fun RecipeListScreen(
    container: AppContainer,
    onOpenRecipe: (Long) -> Unit,
    /** 参数是新建菜谱的类型，跟着左栏当前选中项走。 */
    onNewRecipe: (String) -> Unit,
    /** 点底部那条「菜单里已有 N 道」时跳去菜单页。 */
    onOpenMenu: () -> Unit,
) {
    val viewModel: RecipeListViewModel =
        viewModel(factory = AppViewModelProvider.recipeList(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 只在进这一页时算一次：用户要的是「打开时」的问候，
    // 挂着不动跨过饭点再自己变，反而像在莫名其妙地改字
    val greeting = remember { greetingAt(LocalTime.now().hour) }

    Scaffold(
        topBar = { PageHeader(title = "今天也要好好吃饭", subtitle = greeting) },
        // 放进 bottomBar 而不是塞在内容列里：Scaffold 会把悬浮按钮自动抬到它上面，
        // 两者就不会打架，这条也能占满整个宽度。
        // 塞在内容里的话，左边分类栏先吃掉 84dp，再给按钮让出 72dp，剩下的
        // 宽度连一句「菜单里已有 4 道」都放不下（试过，被截成省略号）
        bottomBar = {
            if (state.menuCount > 0) {
                MenuSummaryPill(
                    count = state.menuCount,
                    onClick = onOpenMenu,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                // 在「甜点」那一栏点 + 就是新建甜点，省得进去再改一次类型
                onClick = {
                    onNewRecipe(
                        if (state.selectedRail == RailSelection.Dessert) {
                            Recipe.TYPE_DESSERT
                        } else {
                            Recipe.TYPE_DISH
                        }
                    )
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新建菜谱")
            }
        },
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            CategoryRail(
                entries = state.railEntries,
                selected = state.selectedRail,
                onSelect = viewModel::onRailSelect,
            )
            VerticalDivider()

            Column(modifier = Modifier.weight(1f)) {
                CompactSearchField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = "搜索菜名或食材",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )

                SortBar(
                    count = state.recipes.size,
                    sortMode = state.sortMode,
                    onSortChange = viewModel::onSortChange,
                )

                if (state.recipes.isEmpty()) {
                    EmptyState(
                        text = when {
                            state.totalCount > 0 -> "没有符合条件的记录"
                            else -> "还没有菜谱，点右下角 + 添加一个吧"
                        },
                    )
                } else {
                    LazyVerticalGrid(
                        // 自适应而不是写死 2 列：手机竖屏约 2 列，横屏/平板自动排更多，
                        // 免得每张卡被撑成明信片那么大。
                        // 下限定 120dp（比别处小）是因为左边分类栏吃掉了 84dp 宽度，
                        // 还按 150dp 算的话手机竖屏会掉成一列。
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        modifier = Modifier.fillMaxSize(),
                        // 底部留够：悬浮按钮是 56dp + 16dp 边距，还要再加上一个
                        // 卡片标题那么高，最后一行才能整个滑出按钮的遮挡
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.recipes, key = { it.id }) { recipe ->
                            RecipeGridCard(
                                recipe = recipe,
                                onClick = { onOpenRecipe(recipe.id) },
                                rating = state.ratings[recipe.id],
                                onToggleMenu = { viewModel.toggleMenu(recipe) },
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 底部那条「菜单里已有 N 道」。
 *
 * 做得像外卖 App 的购物车条：整条可点，点了直接跳去菜单页。
 * 数字只算「加进菜单的」，跟当前在看哪一栏、搜了什么无关。
 */
@Composable
private fun MenuSummaryPill(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.RestaurantMenu,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "菜单里已有 $count 道",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "去生成清单",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 左边一竖栏：全部 / 甜点 / 各个分类，各带菜谱数。
 *
 * 选中项用「浅底 + 左侧色带 + 主色文字」标出来，未选中的压在灰底上 ——
 * 跟外卖 App 的分类栏一个套路，扫一眼就知道自己在哪一栏。
 */
@Composable
private fun CategoryRail(
    entries: List<RailEntry>,
    selected: RailSelection,
    onSelect: (RailSelection) -> Unit,
) {
    // 不铺满高的灰底：分类通常就三四个，铺满的话下面一大片空白看着像没加载完。
    // 改成只给选中项一块浅色，靠分隔线把栏和内容分开
    LazyColumn(
        modifier = Modifier
            .width(RAIL_WIDTH)
            .fillMaxHeight(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(entries, key = { it.selection.key }) { entry ->
            val isSelected = entry.selection == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(entry.selection) }
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        }
                    )
                    .padding(end = 8.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            }
                        ),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = entry.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 网格上方那条：左边这一栏有几道，右边排序方式。 */
@Composable
private fun SortBar(
    count: Int,
    sortMode: SortMode,
    onSortChange: (SortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$count 道",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(sortMode.label, style = MaterialTheme.typography.labelMedium)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        onClick = {
                            onSortChange(mode)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
