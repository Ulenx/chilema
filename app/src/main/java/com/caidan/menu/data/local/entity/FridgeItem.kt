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
    /**
     * 数量扣到 0 的时刻；数量大于 0 时为 null。
     *
     * 「已用完」是个过渡状态而不是归档：留够时间让用户反悔（还想买、还想看看照片），
     * 之后由 [FridgeRepository.purgeUsedUp] 自动清掉。
     */
    val usedUpAt: Long? = null,
) {
    companion object {
        /** 大类：食材 —— 只有这一类再细分小类（肉类/蔬菜…） */
        const val BIG_INGREDIENT = "INGREDIENT"

        /** 大类：调料 */
        const val BIG_SEASONING = "SEASONING"

        /** 大类：零食 */
        const val BIG_SNACK = "SNACK"

        /** 大类：酒柜 */
        const val BIG_DRINK = "DRINK"

        /**
         * 全部分区，按展示顺序。
         *
         * 要加一个分区只改这里：页面上的分段按钮、编辑页的「归类」都从它派生。
         * [bigCategory] 是个自由字符串列，加新值**不需要**数据库迁移。
         */
        val BIG_CATEGORIES = listOf(
            BIG_INGREDIENT to "食材",
            BIG_SEASONING to "调料",
            BIG_SNACK to "零食",
            BIG_DRINK to "酒柜",
        )

        // 录入来源
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_RECEIPT = "RECEIPT"
        const val SOURCE_SCREENSHOT = "SCREENSHOT"

        /** 食材的子分类（其余分区不细分） */
        val CATEGORIES = listOf("肉类", "蔬菜", "水产", "蛋奶", "水果", "主食", "其他")

        /** 认不出来的值一律当食材，免得历史数据里的脏值让标题变成空字符串。 */
        fun bigCategoryLabel(value: String): String =
            BIG_CATEGORIES.firstOrNull { it.first == value }?.second ?: "食材"
    }
}
