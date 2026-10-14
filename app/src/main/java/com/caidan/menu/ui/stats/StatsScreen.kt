@file:OptIn(ExperimentalMaterial3Api::class)

package com.caidan.menu.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caidan.menu.AppContainer
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.ui.AppViewModelProvider
import com.caidan.menu.ui.components.PageHeader
import com.caidan.menu.ui.components.SectionTitle
import com.caidan.menu.ui.components.Tag
import com.caidan.menu.util.formatDate

@Composable
fun StatsScreen(
    container: AppContainer,
    onOpenRecipe: (Long) -> Unit,
) {
    val viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.stats(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { PageHeader(title = "统计", subtitle = "一共做过多少道") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("菜谱总数", state.totalRecipes.toString(), Modifier.weight(1f))
                    StatCard("做菜总次数", state.totalCooks.toString(), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("家常菜", state.dishCount.toString(), Modifier.weight(1f))
                    StatCard("甜点", state.dessertCount.toString(), Modifier.weight(1f))
                }
            }

            item {
                Text(
                    "还有 ${state.neverCooked} 道菜没做过",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (state.topCooked.isNotEmpty()) {
                item { SectionTitle("做得最多") }
                items(state.topCooked, key = { "top-${it.id}" }) { recipe ->
                    RankRow(
                        rank = state.topCooked.indexOf(recipe) + 1,
                        recipe = recipe,
                        trailing = "做过 ${recipe.cookCount} 次",
                        onClick = { onOpenRecipe(recipe.id) },
                    )
                }
            }

            if (state.recentlyCooked.isNotEmpty()) {
                item { SectionTitle("最近做过") }
                items(state.recentlyCooked, key = { "recent-${it.id}" }) { recipe ->
                    RankRow(
                        rank = 0,
                        recipe = recipe,
                        trailing = recipe.lastCookedAt?.let { formatDate(it) }.orEmpty(),
                        onClick = { onOpenRecipe(recipe.id) },
                    )
                }
            }

            if (state.totalRecipes == 0) {
                item {
                    Text(
                        "还没有数据，先添加几道菜吧。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun RankRow(
    rank: Int,
    recipe: Recipe,
    trailing: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank > 0) {
            Text(
                rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(recipe.name, style = MaterialTheme.typography.bodyLarge)
        }
        if (recipe.type == Recipe.TYPE_DESSERT) {
            Tag("甜点")
            Spacer(Modifier.width(6.dp))
        }
        Text(
            trailing,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
