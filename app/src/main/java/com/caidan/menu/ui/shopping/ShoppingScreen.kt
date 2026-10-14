@file:OptIn(ExperimentalMaterial3Api::class)

package com.caidan.menu.ui.shopping

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caidan.menu.AppContainer
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.ui.AppViewModelProvider
import com.caidan.menu.ui.components.CoverImage
import com.caidan.menu.ui.components.EmptyState
import com.caidan.menu.ui.components.PageHeader
import java.io.File

/**
 * 菜单页：先勾选这次要做的菜，生成合并后的购买清单，再按条目勾选「已买」。
 */
@Composable
fun ShoppingScreen(container: AppContainer) {
    val viewModel: ShoppingViewModel = viewModel(factory = AppViewModelProvider.shopping(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            PageHeader(
                title = if (state.generated) "购买清单" else "菜单",
                subtitle = if (state.generated) "照着买就行" else "这次想做哪几道",
                navigationIcon = {
                    if (state.generated) {
                        IconButton(onClick = viewModel::backToSelection) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "回菜单")
                        }
                    }
                },
                actions = {
                    if (state.generated && state.items.isNotEmpty()) {
                        val remaining = state.items.filterNot { it.key in state.boughtKeys }
                        IconButton(
                            onClick = {
                                val text = buildShareText(state.items, state.boughtKeys)
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        },
                                        "分享购买清单",
                                    )
                                )
                            },
                            enabled = remaining.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "分享清单")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.generating) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (!state.generated) {
                SelectionList(
                    recipes = recipes,
                    selectedIds = state.selectedIds,
                    onToggle = viewModel::toggleSelection,
                    onRemoveFromMenu = viewModel::removeFromMenu,
                    onGenerate = viewModel::generateList,
                )
            } else {
                ShoppingList(
                    state = state,
                    onToggleBought = viewModel::toggleBought,
                    onReset = viewModel::resetBought,
                )
            }
        }
    }
}

@Composable
private fun SelectionList(
    recipes: List<Recipe>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onRemoveFromMenu: (Long) -> Unit,
    onGenerate: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (recipes.isEmpty()) {
            EmptyState(text = "菜单还空着，去「菜谱」页点卡片右上角的角标，把想做的菜加进来")
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(recipes, key = { it.id }) { recipe ->
                MenuCard(
                    recipe = recipe,
                    selected = recipe.id in selectedIds,
                    onToggle = { onToggle(recipe.id) },
                    onRemove = { onRemoveFromMenu(recipe.id) },
                )
            }

            // 「已选 N 道 / 生成购买清单」跟着卡片走，不再钉在屏幕最底下 ——
            // 菜单通常只有几道菜，钉底下会留出中间一大片空白。
            // 菜多的时候往下滑一下它就在最后。
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "已选 ${selectedIds.size} 道",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onGenerate, enabled = selectedIds.isNotEmpty()) {
                        Text("生成购买清单")
                    }
                }
            }
        }
    }
}

/**
 * 菜单里的一道菜：封面 + 菜名 + 类型。
 *
 * 点卡片切换勾选（这次做不做）；右侧的 ✕ 是**把它从菜单里去掉** ——
 * 卡片当场消失，菜谱本身不动，想加回来去菜谱页点角标。
 *
 * 没有封面时把菜名叠在占位块上，和菜谱页的网格卡一个处理：一屏灰方块认不出哪张是哪道菜。
 */
@Composable
private fun MenuCard(
    recipe: Recipe,
    selected: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    val hasCover = !recipe.coverPhotoPath.isNullOrBlank() &&
        File(recipe.coverPhotoPath).exists()

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                // 选中时整张卡点亮成琥珀；没选中就用近米色，
                // 跟上面那块琥珀封面拉开，卡片才有分段
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            // 没有封面时**不能**再摆一个 CoverImage：它自带一个灰色占位图标，
            // 正好压在下面那行菜名底下，糊出一块脏印子。和菜谱页的网格卡一样，
            // 没封面就自己铺底色
            if (hasCover) {
                CoverImage(path = recipe.coverPhotoPath, modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = recipe.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // 勾选态徽标。垫一层实心底才压得住深浅不一的封面图
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Filled.RadioButtonUnchecked
                    },
                    contentDescription = if (selected) "已加入菜单" else "未加入菜单",
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (hasCover) {
                    Text(
                        text = recipe.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = if (recipe.type == Recipe.TYPE_DESSERT) "甜点" else "家常菜",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 不管勾没勾都摆出来：它管的是「要不要留在菜单里」，
            // 跟这次做不做没关系，藏起来反而让人找不到
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = "从菜单移除",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ShoppingList(
    state: ShoppingUiState,
    onToggleBought: (String) -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        val bought = state.boughtKeys.size
        val total = state.items.size
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "共 $total 项，已买 $bought 项",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (bought > 0) {
                TextButton(onClick = onReset) { Text("清空勾选") }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.items, key = { it.key }) { item ->
                val isBought = item.key in state.boughtKeys
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleBought(item.key) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = isBought, onCheckedChange = { onToggleBought(item.key) })
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (isBought) TextDecoration.LineThrough else null,
                            color = if (isBought) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        if (item.sources.size > 1) {
                            Text(
                                "来自：${item.sources.joinToString("、")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 对照冰箱库存：够用就不必重复买
                        Text(
                            text = item.stockText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.inStock) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        item.amountText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

/** 把清单拼成可分享的纯文本。 */
private fun buildShareText(items: List<ShoppingItem>, boughtKeys: Set<String>): String = buildString {
    appendLine("购买清单")
    appendLine()
    items.forEach { item ->
        val mark = if (item.key in boughtKeys) "☑" else "☐"
        val amount = item.amountText
        val stock = if (item.inStock) "  （${item.stockText}）" else ""
        appendLine("$mark ${item.name}${if (amount.isBlank()) "" else "  $amount"}$stock")
    }
}
