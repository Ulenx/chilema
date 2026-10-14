package com.caidan.menu.data.repo

import com.caidan.menu.data.local.dao.FridgeDao
import com.caidan.menu.data.local.entity.FridgeItem
import kotlinx.coroutines.flow.Flow

/** 冰箱库存仓库。 */
class FridgeRepository(private val dao: FridgeDao) {

    fun observeAll(): Flow<List<FridgeItem>> = dao.observeAll()

    suspend fun get(id: Long): FridgeItem? = dao.getById(id)

    suspend fun save(item: FridgeItem): Long {
        val stamped = item.stampUsedUp(System.currentTimeMillis())
        return if (stamped.id == 0L) {
            dao.insert(stamped)
        } else {
            dao.update(stamped)
            stamped.id
        }
    }

    /**
     * 清掉用完超过 [USED_UP_RETENTION_DAYS] 天的条目，返回清掉的条数。
     *
     * 到期自动清是为了不让「已用完」越堆越多 —— 它只是个缓冲，不是归档。
     */
    suspend fun purgeUsedUp(): Int = dao.purgeUsedUpBefore(
        System.currentTimeMillis() - USED_UP_RETENTION_DAYS * 24L * 60 * 60 * 1000
    )

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
        val now = System.currentTimeMillis()
        amounts.forEach { (id, amount) ->
            if (amount <= 0.0) return@forEach
            val before = dao.getById(id) ?: return@forEach
            if (before.quantity <= 0.0) return@forEach
            dao.decrementQuantity(id, amount, now)
            changed++
        }
        return changed
    }

    suspend fun delete(item: FridgeItem) = dao.delete(item)

    suspend fun deleteAll() = dao.deleteAll()

    /**
     * 数量归零就记下时间（[purgeUsedUp] 拿它当起点），又添回来就把时间抹掉。
     *
     * 已经记过时间的不覆盖：那是「第一次用完」的时刻，反复改数量不该无限续命。
     */
    private fun FridgeItem.stampUsedUp(now: Long): FridgeItem = when {
        quantity > 0.0 -> copy(usedUpAt = null)
        usedUpAt == null -> copy(usedUpAt = now)
        else -> this
    }

    companion object {
        /** 「已用完」保留多少天后自动清掉。 */
        const val USED_UP_RETENTION_DAYS = 14L
    }
}
