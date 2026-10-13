package com.caidan.menu.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.caidan.menu.data.local.entity.FridgeItem
import kotlinx.coroutines.flow.Flow

@Dao
interface FridgeDao {

    @Query("SELECT * FROM fridge_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FridgeItem>>

    @Query("SELECT * FROM fridge_items WHERE id = :id")
    suspend fun getById(id: Long): FridgeItem?

    @Insert
    suspend fun insert(item: FridgeItem): Long

    @Insert
    suspend fun insertAll(items: List<FridgeItem>)

    @Update
    suspend fun update(item: FridgeItem)

    /**
     * 扣减库存到 0 为止。
     * 走 SQL 而不是「读出来改完再整行写回」：用户在扣减确认框开着的时候
     * 改过这条库存的话，快照回写会把他的修改覆盖掉。这里让数据库自己算。
     */
    @Query("UPDATE fridge_items SET quantity = MAX(quantity - :amount, 0) WHERE id = :id")
    suspend fun decrementQuantity(id: Long, amount: Double)

    @Delete
    suspend fun delete(item: FridgeItem)

    @Query("DELETE FROM fridge_items")
    suspend fun deleteAll()
}
