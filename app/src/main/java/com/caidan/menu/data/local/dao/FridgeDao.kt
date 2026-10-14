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
     * 扣减库存到 0 为止，扣到 0 的话顺手记下时间（[FridgeItem.usedUpAt]）。
     *
     * 走 SQL 而不是「读出来改完再整行写回」：用户在扣减确认框开着的时候
     * 改过这条库存的话，快照回写会把他的修改覆盖掉。这里让数据库自己算。
     *
     * `CASE` 里的 `quantity` 取的是**更新前**的值（SQLite 的 SET 表达式一律按原行求值），
     * 所以能直接判断这一扣有没有见底，不必再读回来一次。
     */
    @Query(
        "UPDATE fridge_items SET " +
            "quantity = MAX(quantity - :amount, 0), " +
            "usedUpAt = CASE WHEN quantity - :amount <= 0 THEN :now ELSE usedUpAt END " +
            "WHERE id = :id"
    )
    suspend fun decrementQuantity(id: Long, amount: Double, now: Long)

    /**
     * 清掉用完超过时限的条目，返回清掉的条数。
     *
     * 只认 `usedUpAt` 明确的：`quantity <= 0` 但没记时间的（理论上不该有）宁可留着，
     * 也不能凭数量猜它是什么时候用完的。
     */
    @Query(
        "DELETE FROM fridge_items " +
            "WHERE quantity <= 0 AND usedUpAt IS NOT NULL AND usedUpAt < :cutoff"
    )
    suspend fun purgeUsedUpBefore(cutoff: Long): Int

    @Delete
    suspend fun delete(item: FridgeItem)

    @Query("DELETE FROM fridge_items")
    suspend fun deleteAll()
}
