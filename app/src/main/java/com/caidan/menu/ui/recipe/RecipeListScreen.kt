@file:OptIn(ExperimentalMaterial3Api::class)

package com.caidan.menu.ui.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caidan.menu.AppContainer
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.ui.AppViewModelProvider
import com.caidan.menu.ui.components.ChipRow
import com.caidan.menu.ui.components.EmptyState
import com.caidan.menu.ui.components.RecipeGridCard

/**
 * 菜谱列表页。菜谱和甜点在同一页里，靠顶部的类型 chip 切换。
 * 顶部为搜索框 + 类型 / 分类 / 排序筛选，下面是卡片网格，右下角新建按钮。
 */
@Composable
fun RecipeListScreen(
    container: AppContainer,
    onOpenRecipe: (Long) -> Unit,
    /** 参数是新建菜谱的类型，跟着当前类型筛选走。 */
    onNewRecipe: (String) -> Unit,
) {
    val viewModel: RecipeListViewModel =
        viewModel(factory = AppViewModelProvider.recipeList(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("我的菜谱") })
        },
        floatingActionButton = {
            FloatingActionButton(
                // 在「甜点」筛选下点 + 就是新建甜点，省得进去再改一次类型
                onClick = { onNewRecipe(state.selectedType ?: Recipe.TYPE_DISH) },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新建菜谱")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索菜名或食材") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )

            ChipRow(
                options = TYPE_LABELS,
                selected = labelOfType(state.selectedType),
                // 「全部」本身就是不筛选，所以不能让 chip 清成「一个都没选中」
                onSelect = { label -> viewModel.onTypeChange(typeOfLabel(label.orEmpty())) },
                allowClear = false,
            )
            Spacer(Modifier.height(8.dp))

            if (categories.isNotEmpty()) {
                ChipRow(
                    options = categories,
                    selected = state.selectedCategory,
                    onSelect = viewModel::onCategoryChange,
                )
                Spacer(Modifier.height(8.dp))
            }

            ChipRow(
                options = SortMode.entries.map { it.label },
                selected = state.sortMode.label,
                onSelect = { label ->
                    SortMode.entries.firstOrNull { it.label == label }
                        ?.let(viewModel::onSortChange)
                },
                allowClear = false,
            )

            if (state.recipes.isEmpty()) {
                EmptyState(
                    text = when {
                        state.totalCount > 0 -> "没有符合条件的记录"
                        state.selectedType == Recipe.TYPE_DESSERT ->
                            "还没有甜点，点右下角 + 添加一个吧"
                        state.selectedType == Recipe.TYPE_DISH ->
                            "还没有家常菜，点右下角 + 添加一个吧"
                        else -> "还没有菜谱，点右下角 + 添加一个吧"
                    },
                )
            } else {
                LazyVerticalGrid(
                    // 按宽度自适应而不是写死 2 列：手机竖屏正好 2 列，
                    // 横屏/平板上固定 2 列会让每张卡涨到明信片那么大，一屏看不到第二行
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.recipes, key = { it.id }) { recipe ->
                        RecipeGridCard(
                            recipe = recipe,
                            onClick = { onOpenRecipe(recipe.id) },
                            rating = state.ratings[recipe.id],
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(72.dp))
                    }
                }
            }
        }
    }
}
