package com.caidan.menu.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 冰箱里的一件库存：可以是食材（[BIG_INGREDIENT]）或调料（[BIG_SEASONING]）。
 *
 * [category] 子分类只在食材下使用（肉类/蔬菜/水产…），调料不再细分。
 * [expiryDate] 由用户手动填写，是临期提醒的唯一依据。
 * [recipeId] 可选关联到一道菜谱；菜谱被删除时置空而不是连带删除库存。
 */
@Entity(
    tableName = "fridge_items",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("recipeId")],
)
data class FridgeItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bigCategory: String = BIG_INGREDIENT,
    val category: String = "",
    val quantity: Double = 0.0,
    val unit: String = "",
    val purchaseDate: Long? = null,
    val expiryDate: Long? = null,
    val source: String = SOURCE_MANUAL,
    val recipeId: Long? = null,
    val photoPath: String? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        /** 大类：食材 */
        const val BIG_INGREDIENT = "INGREDIENT"

        /** 大类：调料 */
        const val BIG_SEASONING = "SEASONING"

        // 录入来源
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_RECEIPT = "RECEIPT"
        const val SOURCE_SCREENSHOT = "SCREENSHOT"

        /** 食材的子分类（调料不细分） */
        val CATEGORIES = listOf("肉类", "蔬菜", "水产", "蛋奶", "水果", "主食", "其他")

        fun bigCategoryLabel(value: String): String =
            if (value == BIG_SEASONING) "调料" else "食材"
    }
}
