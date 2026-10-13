@file:OptIn(ExperimentalMaterial3Api::class)

package com.caidan.menu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.ui.theme.StarGold
import com.caidan.menu.ui.theme.StarGoldDark
import com.caidan.menu.util.formatRating
import java.io.File

/** 空列表占位。 */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = {
        Icon(
            Icons.Filled.Restaurant,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 封面图；没有图时显示占位图标。 */
@Composable
fun CoverImage(
    path: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (path.isNullOrBlank() || !File(path).exists()) {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        } else {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 一行可横向滚动的筛选 chip（分类 / 排序）。 */
@Composable
fun ChipRow(
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options.size) { index ->
            val option = options[index]
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected && allowClear) null else option) },
                label = { Text(option) },
            )
        }
    }
}

/** 菜谱/甜点卡片：封面 + 名称 + 分类/烘烤信息 + 做菜次数。 */
@Composable
fun RecipeCard(
    recipe: Recipe,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            CoverImage(
                path = recipe.coverPhotoPath,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitleFor(recipe),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(text = if (recipe.cookCount > 0) "做过 ${recipe.cookCount} 次" else "还没做过")
                    if (recipe.category.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Tag(text = recipe.category)
                    }
                }
            }
        }
    }
}

private fun subtitleFor(recipe: Recipe): String {
    val parts = mutableListOf<String>()
    if (recipe.description.isNotBlank()) parts += recipe.description
    if (recipe.type == Recipe.TYPE_DESSERT) {
        recipe.bakingTempC?.let { parts += "${it}°C" }
        recipe.bakingTimeMin?.let { parts += "${it} 分钟" }
    }
    return parts.joinToString(" · ").ifBlank {
        recipe.steps.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    }
}

/**
 * 两列网格用的菜谱卡片：上图下文。
 *
 * 没有封面时把菜名叠在占位块上 —— 否则一屏灰方块，认不出哪张是哪道菜；
 * 这时下面就不再重复菜名，只留次数/分类那一行。
 */
@Composable
fun RecipeGridCard(
    recipe: Recipe,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 历次平均星级；没打过分的传 null。 */
    rating: Double? = null,
) {
    val hasCover = !recipe.coverPhotoPath.isNullOrBlank() &&
        File(recipe.coverPhotoPath).exists()

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            if (hasCover) {
                CoverImage(path = recipe.coverPhotoPath, modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
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
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (hasCover) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = gridCaption(recipe, rating),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 网格卡片下面那行小字：做过几次 + 星级 + 分类。
 *
 * 这里用「★ 4.3」而不是排五颗星：卡片宽度只有一两百 dp，五颗星会挤成一团糊。
 */
private fun gridCaption(recipe: Recipe, rating: Double?): String {
    val parts = mutableListOf(
        if (recipe.cookCount > 0) "做过 ${recipe.cookCount} 次" else "还没做过"
    )
    if (rating != null && rating > 0.0) parts += "★ ${formatRating(rating)}"
    if (recipe.category.isNotBlank()) parts += recipe.category
    return parts.joinToString(" · ")
}

/** 星级上限，5 星制。 */
const val MAX_RATING = 5

/**
 * 只读星级。支持半星 —— 菜谱总分是历次平均，3.5 分得看得出来。
 */
@Composable
fun StarRating(
    rating: Double,
    modifier: Modifier = Modifier,
    starSize: Dp = 16.dp,
) {
    val gold = starGold()
    val empty = starEmptyColor()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..MAX_RATING) {
            val half = rating >= i - 0.5
            Icon(
                imageVector = when {
                    rating >= i -> Icons.Filled.Star
                    half -> Icons.AutoMirrored.Filled.StarHalf
                    else -> Icons.Filled.StarBorder
                },
                contentDescription = null,
                tint = if (half) gold else empty,
                modifier = Modifier.size(starSize),
            )
        }
    }
}

/**
 * 可点星级：点第 N 颗给 N 星，再点当前这一颗清零。
 *
 * 清零这个交互不是装饰：星星默认就是 0（没打分），没有清零就再也回不到「不打分」了。
 */
@Composable
fun StarRatingInput(
    rating: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    starSize: Dp = 32.dp,
) {
    val gold = starGold()
    val empty = starEmptyColor()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..MAX_RATING) {
            val filled = i <= rating
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "打 $i 星",
                tint = if (filled) gold else empty,
                modifier = Modifier
                    .size(starSize)
                    .clip(CircleShape)
                    .clickable { onChange(if (rating == i) 0 else i) },
            )
        }
    }
}

/** 浅色主题金色偏深、深色主题提亮，和保质期语义色一个套路。 */
@Composable
private fun starGold(): Color = if (isSystemInDarkTheme()) StarGoldDark else StarGold

/** 空心星不用金色，淡化到背景里，免得和实心星抢注意力。 */
@Composable
private fun starEmptyColor(): Color =
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

/** 小标签。 */
@Composable
fun Tag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** 详情页里的信息分区标题。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

/** 覆盖整页的半透明加载层，用于 AI 识别这类需要等待的操作。 */
@Composable
fun LoadingOverlay(message: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            if (message.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
