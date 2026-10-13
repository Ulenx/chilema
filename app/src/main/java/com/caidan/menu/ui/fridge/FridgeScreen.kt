@file:OptIn(ExperimentalMaterial3Api::class)

package com.caidan.menu.ui.fridge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caidan.menu.AppContainer
import com.caidan.menu.data.local.entity.FridgeItem
import com.caidan.menu.ui.AppViewModelProvider
import com.caidan.menu.ui.components.ChipRow
import com.caidan.menu.ui.components.CoverImage
import com.caidan.menu.ui.components.EmptyState
import com.caidan.menu.ui.components.Tag
import com.caidan.menu.ui.theme.ExpiryExpired
import com.caidan.menu.ui.theme.ExpiryExpiredDark
import com.caidan.menu.ui.theme.ExpiryNear
import com.caidan.menu.ui.theme.ExpiryNearDark
import com.caidan.menu.util.ExpiryStatus
import com.caidan.menu.util.expiryText
import com.caidan.menu.util.formatAmount
import com.caidan.menu.util.formatDate
import kotlinx.coroutines.launch

/**
 * 冰箱库存页：先分「食材 / 调料」大类，食材下再按肉类/蔬菜/水产…筛。
 * 顶部提示卡汇总临期与过期数量，点一下即可只看那一档。
 */
@Composable
fun FridgeScreen(
    container: AppContainer,
    onNewItem: () -> Unit,
    onOpenItem: (Long) -> Unit,
) {
    val viewModel: FridgeViewModel = viewModel(factory = AppViewModelProvider.fridge(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun delete(row: FridgeRow) {
        viewModel.delete(row.item)
        scope.launch {
            val result = snackbar.showSnackbar(
                message = "已删除「${row.item.name}」",
                actionLabel = "撤销",
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(row.item)
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("我的冰箱") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewItem) {
                Icon(Icons.Filled.Add, contentDescription = "添加食材")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val options = listOf(
                    FridgeItem.BIG_INGREDIENT to "食材",
                    FridgeItem.BIG_SEASONING to "调料",
                )
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = state.bigCategory == value,
                        onClick = { viewModel.onBigCategoryChange(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(label) }
                }
            }

            ExpiryBanner(
                nearCount = state.nearCount,
                expiredCount = state.expiredCount,
                onNearClick = { viewModel.onExpiryFilterChange(ExpiryFilter.NEAR) },
                onExpiredClick = { viewModel.onExpiryFilterChange(ExpiryFilter.EXPIRED) },
            )

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索食材或备注") },
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

            // 调料不再细分，所以只有食材大类才出子分类
            if (state.isIngredient && state.categories.isNotEmpty()) {
                ChipRow(
                    options = state.categories,
                    selected = state.category,
                    onSelect = viewModel::onCategoryChange,
                )
                Spacer(Modifier.height(8.dp))
            }

            ChipRow(
                options = ExpiryFilter.entries.map { it.label },
                selected = state.expiryFilter.label,
                onSelect = { label ->
                    ExpiryFilter.entries.firstOrNull { it.label == label }
                        ?.let(viewModel::onExpiryFilterChange)
                },
                allowClear = false,
            )

            if (state.rows.isEmpty()) {
                EmptyState(
                    text = when {
                        state.totalInBigCategory > 0 -> "没有符合条件的条目"
                        state.isIngredient -> "还没有食材，点右下角 + 添加，或用收据/截图识别"
                        else -> "还没有调料，点右下角 + 添加"
                    },
                    icon = {
                        Icon(
                            Icons.Filled.Kitchen,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.rows, key = { it.item.id }) { row ->
                        FridgeItemCard(
                            row = row,
                            onClick = { onOpenItem(row.item.id) },
                            onDelete = { delete(row) },
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

/** 临期/过期汇总卡，两个 chip 分别点进对应筛选；都没有时不显示。 */
@Composable
private fun ExpiryBanner(
    nearCount: Int,
    expiredCount: Int,
    onNearClick: () -> Unit,
    onExpiredClick: () -> Unit,
) {
    if (nearCount == 0 && expiredCount == 0) return

    val hasExpired = expiredCount > 0
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasExpired) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = expiryColor(
                    if (hasExpired) ExpiryStatus.EXPIRED else ExpiryStatus.NEAR
                ) ?: MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text("快过期了", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            if (nearCount > 0) {
                AssistChip(onClick = onNearClick, label = { Text("临期 $nearCount") })
            }
            if (expiredCount > 0) {
                Spacer(Modifier.width(8.dp))
                AssistChip(onClick = onExpiredClick, label = { Text("过期 $expiredCount") })
            }
        }
    }
}

@Composable
private fun FridgeItemCard(
    row: FridgeRow,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val item = row.item
    val accent = expiryColor(row.status)
    // 扣到 0 的条目不能再走 formatAmount：它会把 0 显示成空串，只剩一个孤零零的单位
    val amount = if (item.quantity <= 0.0) "已用完" else formatAmount(item.quantity, item.unit)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                path = item.photoPath,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (amount.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = amount,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                val tags = listOfNotNull(
                    item.category.takeIf { it.isNotBlank() },
                    row.recipeName?.let { "关联：$it" },
                )
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { Tag(it) }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (accent != null) {
                        Icon(
                            Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = expiryLine(item),
                        style = MaterialTheme.typography.bodyMedium,
                        color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除")
            }
        }
    }
}

private fun expiryLine(item: FridgeItem): String {
    val expiry = item.expiryDate ?: return "未填保质期"
    return "保质期至 ${formatDate(expiry)} · ${expiryText(expiry)}"
}

/** 临期橙色、过期红色；没填保质期返回 null，用默认文字色。 */
@Composable
private fun expiryColor(status: ExpiryStatus): Color? {
    val dark = isSystemInDarkTheme()
    return when (status) {
        ExpiryStatus.EXPIRED -> if (dark) ExpiryExpiredDark else ExpiryExpired
        ExpiryStatus.NEAR -> if (dark) ExpiryNearDark else ExpiryNear
        else -> null
    }
}
