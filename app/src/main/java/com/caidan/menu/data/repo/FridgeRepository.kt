package com.caidan.menu.data.repo

import com.caidan.menu.data.local.dao.FridgeDao
import com.caidan.menu.data.local.entity.FridgeItem
import kotlinx.coroutines.flow.Flow

/** 冰箱库存仓库。 */
class FridgeRepository(private val dao: FridgeDao) {

    fun observeAll(): Flow<List<FridgeItem>> = dao.observeAll()

    suspend fun get(id: Long): FridgeItem? = dao.getById(id)

    suspend fun save(item: FridgeItem): Long =
        if (item.id == 0L) dao.insert(item) else {
            dao.update(item)
            item.id
        }

    /**
     * 批量保存识别出来的多条食材。
     * [drafts] 里 name 为空的条目会被跳过。
     */
    suspend fun saveAll(drafts: List<FridgeItem>): Int {
        val valid = drafts.filter { it.name.isNotBlank() }
        if (valid.isEmpty()) return 0
        dao.insertAll(valid)
        return valid.size
    }

    /**
     * 按 id 扣减若干条库存，返回真正动过的条数。
     *
     * [amounts] 的 key 是 [FridgeItem.id]，value 是要扣掉的量。
     * 调用方要先把同名食材的用量归并好（一个 id 只会出现一次），
     * 否则同一 id 传两个值会扣两次。
     */
    suspend fun applyDeductions(amounts: Map<Long, Double>): Int {
        var changed = 0
        amounts.forEach { (id, amount) ->
            if (amount <= 0.0) return@forEach
            val before = dao.getById(id) ?: return@forEach
            if (before.quantity <= 0.0) return@forEach
            dao.decrementQuantity(id, amount)
            changed++
        }
        return changed
    }

    suspend fun delete(item: FridgeItem) = dao.delete(item)

    suspend fun deleteAll() = dao.deleteAll()
}
