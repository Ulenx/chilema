package com.caidan.menu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.caidan.menu.data.local.entity.VideoLink
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoLinkDao {

    @Query("SELECT * FROM video_links WHERE recipeId = :recipeId ORDER BY sortOrder, id")
    fun observeByRecipe(recipeId: Long): Flow<List<VideoLink>>

    @Query("SELECT * FROM video_links WHERE recipeId = :recipeId ORDER BY sortOrder, id")
    suspend fun getByRecipe(recipeId: Long): List<VideoLink>

    @Insert
    suspend fun insertAll(links: List<VideoLink>)

    @Query("DELETE FROM video_links WHERE recipeId = :recipeId")
    suspend fun deleteByRecipe(recipeId: Long)
}
