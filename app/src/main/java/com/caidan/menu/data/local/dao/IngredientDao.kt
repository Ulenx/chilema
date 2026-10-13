package com.caidan.menu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.caidan.menu.data.local.entity.Ingredient
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {

    @Query("SELECT * FROM ingredients WHERE recipeId = :recipeId ORDER BY sortOrder, id")
    fun observeByRecipe(recipeId: Long): Flow<List<Ingredient>>

    @Query("SELECT * FROM ingredients WHERE recipeId = :recipeId ORDER BY sortOrder, id")
    suspend fun getByRecipe(recipeId: Long): List<Ingredient>

    @Insert
    suspend fun insertAll(ingredients: List<Ingredient>)

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    suspend fun deleteByRecipe(recipeId: Long)
}
