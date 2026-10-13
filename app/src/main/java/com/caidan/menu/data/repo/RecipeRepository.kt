package com.caidan.menu.data.repo

import com.caidan.menu.data.local.dao.CookRecordDao
import com.caidan.menu.data.local.dao.IngredientDao
import com.caidan.menu.data.local.dao.RecipeDao
import com.caidan.menu.data.local.dao.RecipeRatingSummary
import com.caidan.menu.data.local.dao.VideoLinkDao
import com.caidan.menu.data.local.entity.CookRecord
import com.caidan.menu.data.local.entity.Ingredient
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.data.local.entity.VideoLink
import kotlinx.coroutines.flow.Flow

/** 菜谱聚合仓库：统一编排菜谱、食材、视频、做菜记录四张表。 */
class RecipeRepository(
    private val recipeDao: RecipeDao,
    private val ingredientDao: IngredientDao,
    private val videoLinkDao: VideoLinkDao,
    private val cookRecordDao: CookRecordDao,
) {

    fun observeAll(): Flow<List<Recipe>> = recipeDao.observeAll()

    fun observeRecipe(id: Long): Flow<Recipe?> = recipeDao.observeById(id)

    fun observeIngredients(recipeId: Long): Flow<List<Ingredient>> =
        ingredientDao.observeByRecipe(recipeId)

    fun observeVideos(recipeId: Long): Flow<List<VideoLink>> =
        videoLinkDao.observeByRecipe(recipeId)

    fun observeCookRecords(recipeId: Long): Flow<List<CookRecord>> =
        cookRecordDao.observeByRecipe(recipeId)

    /** 每道菜的平均星级，列表卡片用。只包含打过分（rating > 0）的记录。 */
    fun observeRatings(): Flow<List<RecipeRatingSummary>> = cookRecordDao.observeRatings()

    fun observeTotalRecipeCount(): Flow<Int> = recipeDao.observeTotalCount()

    fun observeTotalCookCount(): Flow<Int> = cookRecordDao.observeTotalCount()

    /**
     * 保存菜谱（含食材与视频链接）。
     * 新增时返回新 id；更新时先清空再重写子表，逻辑简单可靠。
     */
    suspend fun saveRecipe(
        recipe: Recipe,
        ingredients: List<Ingredient>,
        videos: List<VideoLink>,
    ): Long {
        val now = System.currentTimeMillis()
        val id = if (recipe.id == 0L) {
            recipeDao.insert(recipe.copy(createdAt = now, updatedAt = now))
        } else {
            recipeDao.update(recipe.copy(updatedAt = now))
            recipe.id
        }

        ingredientDao.deleteByRecipe(id)
        ingredientDao.insertAll(
            ingredients
                .filter { it.name.isNotBlank() }
                .mapIndexed { index, item -> item.copy(id = 0, recipeId = id, sortOrder = index) }
        )

        videoLinkDao.deleteByRecipe(id)
        videoLinkDao.insertAll(
            videos
                .filter { it.url.isNotBlank() }
                .mapIndexed { index, item -> item.copy(id = 0, recipeId = id, sortOrder = index) }
        )
        return id
    }

    suspend fun deleteRecipe(recipe: Recipe) = recipeDao.delete(recipe)

    /** 清空全部数据（设置页的「清除数据」）。 */
    suspend fun deleteAllRecipes() = recipeDao.deleteAll()

    /** 记录「做了一次」：写入记录并同步菜谱上的冗余计数与最近时间。 */
    suspend fun recordCook(
        recipeId: Long,
        note: String,
        photoPath: String?,
        setAsCover: Boolean = false,
        rating: Int? = null,
    ) {
        val now = System.currentTimeMillis()
        cookRecordDao.insert(
            CookRecord(
                recipeId = recipeId,
                cookedAt = now,
                note = note,
                photoPath = photoPath,
                rating = rating,
            )
        )
        recipeDao.incrementCookCount(recipeId, now)
        if (setAsCover && !photoPath.isNullOrBlank()) {
            recipeDao.setCoverPhoto(recipeId, photoPath, now)
        }
    }

    /**
     * 事后编辑某条做菜记录：补/换照片、补打分、改备注。
     *
     * 不改 cookCount 和 lastCookedAt —— 编辑的是内容，不是「又做了一次」。
     */
    suspend fun updateCookRecord(
        id: Long,
        note: String,
        rating: Int?,
        photoPath: String?,
    ) = cookRecordDao.updateRecord(id, note, rating, photoPath)

    /** 把已有的某张成品照片设为封面。 */
    suspend fun setCoverPhoto(recipeId: Long, path: String) =
        recipeDao.setCoverPhoto(recipeId, path, System.currentTimeMillis())

    suspend fun deleteCookRecord(record: CookRecord) {
        cookRecordDao.delete(record)
        recipeDao.decrementCookCount(record.recipeId)
    }

    suspend fun getRecipe(id: Long): Recipe? = recipeDao.getById(id)

    /** 编辑页初始化用的一次性读取（非观察）。 */
    suspend fun ingredientsOnce(recipeId: Long): List<Ingredient> =
        ingredientDao.getByRecipe(recipeId)

    suspend fun videosOnce(recipeId: Long): List<VideoLink> =
        videoLinkDao.getByRecipe(recipeId)
}
