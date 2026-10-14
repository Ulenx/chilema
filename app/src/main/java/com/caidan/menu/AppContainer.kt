package com.caidan.menu

import android.content.Context
import com.caidan.menu.data.ai.FridgeParser
import com.caidan.menu.data.ai.QwenClient
import com.caidan.menu.data.ai.RecipeParser
import com.caidan.menu.data.ai.ScreenshotParser
import com.caidan.menu.data.local.AppDatabase
import com.caidan.menu.data.repo.FridgeRepository
import com.caidan.menu.data.repo.RecipeRepository
import com.caidan.menu.data.repo.SettingsRepository
import com.caidan.menu.util.PendingImport

/**
 * 极简手动依赖注入容器，挂在 Application 上。
 * 对个人 App 而言足够，且不引入 Hilt 等额外构建复杂度。
 */
class AppContainer(context: Context) {

    private val db = AppDatabase.get(context)

    val recipeRepository = RecipeRepository(
        recipeDao = db.recipeDao(),
        ingredientDao = db.ingredientDao(),
        videoLinkDao = db.videoLinkDao(),
        cookRecordDao = db.cookRecordDao(),
    )

    val fridgeRepository = FridgeRepository(db.fridgeDao())

    val settingsRepository = SettingsRepository(context)

    /** 系统分享面板送进来的内容，等新建菜谱页取走 */
    val pendingImport = PendingImport()

    private val qwenClient = QwenClient()
    val screenshotParser = ScreenshotParser(qwenClient)
    val fridgeParser = FridgeParser(qwenClient)
    val recipeParser = RecipeParser(qwenClient)
}
