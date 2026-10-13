package com.caidan.menu.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.caidan.menu.data.local.entity.Recipe
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeById(id: Long): Flow<Recipe?>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: Long): Recipe?

    @Insert
    suspend fun insert(recipe: Recipe): Long

    @Update
    suspend fun update(recipe: Recipe)

    @Delete
    suspend fun delete(recipe: Recipe)

    @Query("UPDATE recipes SET cookCount = cookCount + 1, lastCookedAt = :time, updatedAt = :time WHERE id = :recipeId")
    suspend fun incrementCookCount(recipeId: Long, time: Long)

    /** 把某次成品照片设为封面（详情页「成品记录」里挑一张）。 */
    @Query("UPDATE recipes SET coverPhotoPath = :path, updatedAt = :time WHERE id = :recipeId")
    suspend fun setCoverPhoto(recipeId: Long, path: String, time: Long)

    @Query("UPDATE recipes SET cookCount = MAX(cookCount - 1, 0), lastCookedAt = (SELECT MAX(cookedAt) FROM cook_records WHERE recipeId = :recipeId) WHERE id = :recipeId")
    suspend fun decrementCookCount(recipeId: Long)

    @Query("SELECT COUNT(*) FROM recipes")
    fun observeTotalCount(): Flow<Int>

    /** 清空全部菜谱；食材/视频/记录由外键级联删除。 */
    @Query("DELETE FROM recipes")
    suspend fun deleteAll()
}
