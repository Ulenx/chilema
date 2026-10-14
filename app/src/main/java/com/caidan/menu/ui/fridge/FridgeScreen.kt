@file:OptIn(ExperimentalMaterial3Api::class)

package com.caidan.menu.ui.fridge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.caidan.menu.ui.components.CompactSearchField
import com.caidan.menu.ui.components.EmptyState
import com.caidan.menu.ui.components.PageHeader
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
        topBar = {
            PageHeader(
                title = "我的冰箱",
                subtitle = when (state.bigCategory) {
                    FridgeItem.BIG_SEASONING -> "调料还够不够用"
                    FridgeItem.BIG_SNACK -> "囤的零食还剩多少"
                    FridgeItem.BIG_DRINK -> "酒柜里都存了什么"
                    else -> "看看冰箱里还有什么"
                },
                actions = {
                    // 过期提醒挪到标题右边：原来它横在内容顶上白占 56dp，
                    // 手机竖屏上把网格挤得只剩一条缝
                    ExpiryChips(
                        nearCount = state.nearCount,
                        expiredCount = state.expiredCount,
                        usedUpCount = state.usedUpCount,
                        onNearClick = { viewModel.onExpiryFilterChange(ExpiryFilter.NEAR) },
                        onExpiredClick = { viewModel.onExpiryFilterChange(ExpiryFilter.EXPIRED) },
                        onUsedUpClick = { viewModel.onExpiryFilterChange(ExpiryFilter.USED_UP) },
                    )
                },
            )
        },
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
                val options = FridgeItem.BIG_CATEGORIES
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = state.bigCategory == value,
                        onClick = { viewModel.onBigCategoryChange(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(label) }
                }
            }

            LazyVerticalGrid(
                // 自适应而不是写死 3 列：手机竖屏正好 3 列，横屏/平板自动排更多，
                // 免得每张卡被撑成一大块。下限定 100dp 是按「360dp 手机排 3 列」倒推的。
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier.fillMaxSize(),
                // 底部留够：悬浮按钮 56dp + 16dp 边距，再加一个卡片标题的高度，
                // 最后一行才能整个滑出按钮的遮挡
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 提醒栏、搜索框、两行筛选项都当网格的表头，往下滑就跟着滑走。
                // 它们原先钉在顶上，640dp 的手机上要吃掉一半多，网格只剩不到
                // 150dp，卡片底部会被导航栏切掉。
                // 「食材 / 调料」留在网格外面：那是和底部 Tab 一个级别的大类切换，
                // 滑走了想切回来还得先滑回顶部。
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        CompactSearchField(
                            value = state.query,
                            onValueChange = viewModel::onQueryChange,
                            placeholder = "搜索食材或备注",
                            modifier = Modifier.padding(vertical = 6.dp),
                        )

                        // 调料不再细分，所以只有食材大类才出子分类。
                        // 两行 chip 的横向内边距交给网格的 contentPadding，别再叠一层
                        if (state.isIngredient && state.categories.isNotEmpty()) {
                            ChipRow(
                                options = state.categories,
                                selected = state.category,
                                onSelect = viewModel::onCategoryChange,
                                contentPadding = PaddingValues(horizontal = 0.dp),
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
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        )
                    }
                }

                if (state.rows.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            text = when {
                                state.expiryFilter == ExpiryFilter.USED_UP -> "还没有用完的条目"
                                state.totalInBigCategory > 0 -> "没有符合条件的条目"
                                state.isIngredient -> "还没有食材，点右下角 + 添加，或用收据/截图识别"
                                else -> "这个分区还空着，点右下角 + 添加"
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
                    }
                } else {
                    items(state.rows, key = { it.item.id }) { row ->
                        FridgeItemCard(
                            row = row,
                            onClick = { onOpenItem(row.item.id) },
                            onDelete = { delete(row) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 标题右边那排过期提醒：临期 / 过期 / 已用完各一个 chip，点进对应筛选。
 *
 * 三者都没有时一个都不出，标题右边干干净净。
 * 每个 chip 自带底色 —— 独立站着没有容器了，颜色得自己带，
 * 不然「过期」和「已用完」看着一样重。
 */
@Composable
private fun ExpiryChips(
    nearCount: Int,
    expiredCount: Int,
    usedUpCount: Int,
    onNearClick: () -> Unit,
    onExpiredClick: () -> Unit,
    onUsedUpClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (nearCount > 0) {
            ExpiryChip(
                label = "临期 $nearCount",
                container = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onNearClick,
            )
        }
        if (expiredCount > 0) {
            Spacer(Modifier.width(4.dp))
            ExpiryChip(
                label = "过期 $expiredCount",
                container = MaterialTheme.colorScheme.errorContainer,
                onClick = onExpiredClick,
            )
        }
        if (usedUpCount > 0) {
            Spacer(Modifier.width(4.dp))
            ExpiryChip(
                label = "已用完 $usedUpCount",
                container = MaterialTheme.colorScheme.surfaceVariant,
                onClick = onUsedUpClick,
            )
        }
    }
}

@Composable
private fun ExpiryChip(label: String, container: Color, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = AssistChipDefaults.assistChipColors(containerColor = container),
        border = null,
    )
}

/**
 * 网格里的一张库存卡：上图下文。
 *
 * 三列之后每张卡只有一百来 dp 宽，装不下原来那行的「分类 + 关联菜名 + 完整保质期」，
 * 所以只留最要紧的三样：菜名、用量、保质期状态。分类和关联菜名点开就能看到，
 * 封面右上角那个是删除（删完有撤销，误点也收得回）。
 */
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
        // 正文用近米色、封面那块是琥珀，两块深浅拉开才看得出分段
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            CoverImage(path = item.photoPath, modifier = Modifier.fillMaxSize())
            // 垫一层实心底才压得住深浅不一的封面图
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (amount.isNotEmpty()) {
                Text(
                    text = amount,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = compactExpiryLine(item),
                style = MaterialTheme.typography.bodySmall,
                color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 网格卡片窄，保质期只留最要紧的那半句。
 *
 * 原来那行「保质期至 2026-10-12 · 已过期 2 天」在 100dp 宽里会被截成
 * 「保质期至 202…」—— 具体哪天到期远不如「还剩几天」有用。
 */
private fun compactExpiryLine(item: FridgeItem): String =
    item.expiryDate?.let { expiryText(it) } ?: "未填保质期"

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
