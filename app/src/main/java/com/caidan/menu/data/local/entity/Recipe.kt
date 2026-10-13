package com.caidan.menu.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 菜谱/甜点共用一张表，用 [type] 区分：
 * - [TYPE_DISH] 家常菜
 * - [TYPE_DESSERT] 甜点/烘焙
 * 甜点专属字段 [bakingTempC]/[bakingTimeMin]/[servings] 仅 type=DESSERT 时使用。
 */
@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val steps: String = "",
    val category: String = "",
    val type: String = TYPE_DISH,
    val coverPhotoPath: String? = null,
    val cookCount: Int = 0,
    val lastCookedAt: Long? = null,
    val bakingTempC: Int? = null,
    val bakingTimeMin: Int? = null,
    val servings: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TYPE_DISH = "DISH"
        const val TYPE_DESSERT = "DESSERT"
    }
}
