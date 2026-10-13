package com.caidan.menu.ui

import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.caidan.menu.AppContainer
import com.caidan.menu.ui.fridge.FridgeEditViewModel
import com.caidan.menu.ui.fridge.FridgeViewModel
import com.caidan.menu.ui.recipe.RecipeDetailViewModel
import com.caidan.menu.ui.recipe.RecipeEditViewModel
import com.caidan.menu.ui.recipe.RecipeListViewModel
import com.caidan.menu.ui.settings.SettingsViewModel
import com.caidan.menu.ui.shopping.ShoppingViewModel
import com.caidan.menu.ui.stats.StatsViewModel

/**
 * 集中管理各 ViewModel 的构造。
 * 导航目的地作为 ViewModelStoreOwner 时，createSavedStateHandle() 会自动
 * 拿到导航参数（recipeId / type），无需手动传递。
 */
object AppViewModelProvider {

    fun recipeList(container: AppContainer) = viewModelFactory {
        initializer { RecipeListViewModel(container.recipeRepository) }
    }

    fun recipeEdit(container: AppContainer) = viewModelFactory {
        initializer {
            RecipeEditViewModel(
                repository = container.recipeRepository,
                extractor = container.ingredientExtractor,
                screenshotParser = container.screenshotParser,
                recipeParser = container.recipeParser,
                settings = container.settingsRepository,
                pendingImport = container.pendingImport,
                savedStateHandle = createSavedStateHandle(),
            )
        }
    }

    fun recipeDetail(container: AppContainer) = viewModelFactory {
        initializer {
            RecipeDetailViewModel(
                repository = container.recipeRepository,
                fridgeRepository = container.fridgeRepository,
                savedStateHandle = createSavedStateHandle(),
            )
        }
    }

    fun fridge(container: AppContainer) = viewModelFactory {
        initializer {
            FridgeViewModel(container.fridgeRepository, container.recipeRepository)
        }
    }

    fun fridgeEdit(container: AppContainer) = viewModelFactory {
        initializer {
            FridgeEditViewModel(
                fridgeRepository = container.fridgeRepository,
                recipeRepository = container.recipeRepository,
                parser = container.fridgeParser,
                settings = container.settingsRepository,
                savedStateHandle = createSavedStateHandle(),
            )
        }
    }

    fun shopping(container: AppContainer) = viewModelFactory {
        initializer {
            ShoppingViewModel(container.recipeRepository, container.fridgeRepository)
        }
    }

    fun stats(container: AppContainer) = viewModelFactory {
        initializer { StatsViewModel(container.recipeRepository) }
    }

    fun settings(container: AppContainer) = viewModelFactory {
        initializer {
            SettingsViewModel(container.settingsRepository, container.recipeRepository)
        }
    }
}
