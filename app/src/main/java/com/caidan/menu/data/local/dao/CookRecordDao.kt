package com.caidan.menu.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.caidan.menu.data.local.entity.CookRecord
import kotlinx.coroutines.flow.Flow

/**
 * 某道菜的平均星级。
 *
 * 这不是实体，只是一段聚合查询的投影。
 * [avgRating] 声明成可空：`WHERE rating > 0` 保证了每个分组至少有一个非空值，
 * 但 Room 的判空分析证明不了这一点，写成非空会过不了编译期检查。
 */
data class RecipeRatingSummary(
    val recipeId: Long,
    val avgRating: Double?,
    val ratedCount: Int,
)

@Dao
interface CookRecordDao {

    @Query("SELECT * FROM cook_records WHERE recipeId = :recipeId ORDER BY cookedAt DESC")
    fun observeByRecipe(recipeId: Long): Flow<List<CookRecord>>

    @Insert
    suspend fun insert(record: CookRecord): Long

    @Delete
    suspend fun delete(record: CookRecord)

    /** 编辑已有记录。备注、星级、照片一起改，省得为字段各开一个方法。 */
    @Query("UPDATE cook_records SET note = :note, rating = :rating, photoPath = :photoPath WHERE id = :id")
    suspend fun updateRecord(id: Long, note: String, rating: Int?, photoPath: String?)

    /**
     * 每道菜的平均星级，供列表卡片显示。
     *
     * 详情页不用这个 —— 那边本来就订阅了这道菜的全部记录，在 Kotlin 里 average() 更省事。
     */
    @Query(
        "SELECT recipeId, AVG(rating) AS avgRating, COUNT(rating) AS ratedCount " +
            "FROM cook_records WHERE rating > 0 GROUP BY recipeId"
    )
    fun observeRatings(): Flow<List<RecipeRatingSummary>>

    @Query("SELECT COUNT(*) FROM cook_records")
    fun observeTotalCount(): Flow<Int>
}
