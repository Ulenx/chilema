package com.caidan.menu.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.data.repo.RecipeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val totalRecipes: Int = 0,
    val dishCount: Int = 0,
    val dessertCount: Int = 0,
    val totalCooks: Int = 0,
    val neverCooked: Int = 0,
    val topCooked: List<Recipe> = emptyList(),
    val recentlyCooked: List<Recipe> = emptyList(),
)

class StatsViewModel(repository: RecipeRepository) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = repository.observeAll()
        .map { all ->
            val cooked = all.filter { it.cookCount > 0 }
            StatsUiState(
                totalRecipes = all.size,
                dishCount = all.count { it.type == Recipe.TYPE_DISH },
                dessertCount = all.count { it.type == Recipe.TYPE_DESSERT },
                totalCooks = all.sumOf { it.cookCount },
                neverCooked = all.count { it.cookCount == 0 },
                topCooked = cooked.sortedByDescending { it.cookCount }.take(10),
                recentlyCooked = cooked
                    .filter { it.lastCookedAt != null }
                    .sortedByDescending { it.lastCookedAt }
                    .take(5),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}
