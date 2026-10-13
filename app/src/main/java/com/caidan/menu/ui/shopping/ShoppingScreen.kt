@file:OptIn(ExperimentalMaterial3Api::class)

package com.caidan.menu.ui.shopping

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caidan.menu.AppContainer
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.ui.AppViewModelProvider
import com.caidan.menu.ui.components.EmptyState
import com.caidan.menu.ui.components.Tag

/**
 * 购物页：先勾选要做的菜，生成合并后的购买清单，再按条目勾选「已买」。
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
            CenterAlignedTopAppBar(
                title = { Text(if (state.generated) "购买清单" else "选菜") },
                navigationIcon = {
                    if (state.generated) {
                        IconButton(onClick = viewModel::backToSelection) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "重新选菜")
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
    onGenerate: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (recipes.isEmpty()) {
            EmptyState(text = "还没有菜谱，先去添加几道菜吧")
            return@Column
        }

        Text(
            "勾选这次想做的菜，自动合并食材生成清单",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(recipes, key = { it.id }) { recipe ->
                val checked = recipe.id in selectedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(recipe.id) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = { onToggle(recipe.id) })
                    Column(Modifier.weight(1f)) {
                        Text(recipe.name, style = MaterialTheme.typography.bodyLarge)
                    }
                    Tag(if (recipe.type == Recipe.TYPE_DESSERT) "甜点" else "家常菜")
                    Spacer(Modifier.width(8.dp))
                }
                HorizontalDivider()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
