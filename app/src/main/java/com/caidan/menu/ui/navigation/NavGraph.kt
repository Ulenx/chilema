package com.caidan.menu.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.caidan.menu.AppContainer
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.ui.fridge.FridgeEditScreen
import com.caidan.menu.ui.fridge.FridgeScreen
import com.caidan.menu.ui.recipe.RecipeDetailScreen
import com.caidan.menu.ui.recipe.RecipeEditScreen
import com.caidan.menu.ui.recipe.RecipeListScreen
import com.caidan.menu.ui.settings.SettingsScreen
import com.caidan.menu.ui.shopping.ShoppingScreen
import com.caidan.menu.ui.stats.StatsScreen

object Routes {
    const val RECIPES = "recipes"
    const val FRIDGE = "fridge"
    const val SHOPPING = "shopping"
    const val STATS = "stats"
    const val SETTINGS = "settings"

    const val EDIT = "edit?recipeId={recipeId}&type={type}"
    fun edit(recipeId: Long = 0L, type: String = Recipe.TYPE_DISH) =
        "edit?recipeId=$recipeId&type=$type"

    const val DETAIL = "recipe/{recipeId}"
    fun detail(recipeId: Long) = "recipe/$recipeId"

    const val FRIDGE_EDIT = "fridgeEdit?itemId={itemId}"
    fun fridgeEdit(itemId: Long = 0L) = "fridgeEdit?itemId=$itemId"
}

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    TabItem(Routes.RECIPES, "菜谱", Icons.Filled.Restaurant),
    TabItem(Routes.FRIDGE, "食材", Icons.Filled.Kitchen),
    TabItem(Routes.SHOPPING, "购物", Icons.Filled.ShoppingCart),
    TabItem(Routes.STATS, "统计", Icons.Filled.BarChart),
    TabItem(Routes.SETTINGS, "设置", Icons.Filled.Settings),
)

@Composable
fun MenuNavGraph(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // currentRoute 可空，用 == 比较避免 String? 参与集合 contains 的类型不匹配
    val showBottomBar = tabs.any { it.route == currentRoute }

    fun openRecipe(id: Long) = navController.navigate(Routes.detail(id))
    fun openEditor(recipeId: Long, type: String) =
        navController.navigate(Routes.edit(recipeId, type))

    // 从别的 App 分享内容进来：开一个新建菜谱页，内容由 RecipeEditViewModel 取走
    LaunchedEffect(Unit) {
        container.pendingImport.arrivalFlow.collect {
            // 必须在 collect 里现读路由：闭包捕获的是首次组合时的旧值
            val route = navController.currentBackStackEntry?.destination?.route
            navController.navigate(Routes.edit(0L, Recipe.TYPE_DISH)) {
                if (route == Routes.EDIT) {
                    // 已经停在编辑页时替换掉这一层，别把编辑页越堆越深
                    popUpTo(Routes.EDIT) { inclusive = true }
                }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    // 切换 Tab 时保留各自状态，且不堆叠返回栈
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.RECIPES,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.RECIPES) {
                RecipeListScreen(
                    container = container,
                    onOpenRecipe = ::openRecipe,
                    onNewRecipe = { openEditor(0L, it) },
                )
            }

            composable(Routes.FRIDGE) {
                FridgeScreen(
                    container = container,
                    onNewItem = { navController.navigate(Routes.fridgeEdit()) },
                    onOpenItem = { navController.navigate(Routes.fridgeEdit(it)) },
                )
            }

            composable(
                route = Routes.FRIDGE_EDIT,
                arguments = listOf(
                    navArgument("itemId") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) {
                // itemId 由 FridgeEditViewModel 从导航参数中读取
                FridgeEditScreen(
                    container = container,
                    onClose = { navController.popBackStack() },
                )
            }

            composable(Routes.SHOPPING) { ShoppingScreen(container) }

            composable(Routes.STATS) {
                StatsScreen(container = container, onOpenRecipe = ::openRecipe)
            }

            composable(Routes.SETTINGS) { SettingsScreen(container) }

            composable(
                route = Routes.EDIT,
                arguments = listOf(
                    navArgument("recipeId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = Recipe.TYPE_DISH
                    },
                ),
            ) {
                RecipeEditScreen(
                    container = container,
                    onDone = { id ->
                        navController.navigate(Routes.detail(id)) {
                            popUpTo(Routes.EDIT) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("recipeId") { type = NavType.LongType }),
            ) {
                // recipeId 由 RecipeDetailViewModel 从导航参数中读取，这里无需再取
                RecipeDetailScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    // 已有菜谱的类型以数据库里的为准，这里的 type 参数只是占位
                    onEdit = { openEditor(it, Recipe.TYPE_DISH) },
                )
            }
        }
    }
}
