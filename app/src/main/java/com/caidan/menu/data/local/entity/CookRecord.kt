package com.caidan.menu.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 做菜记录：每「做一次」产生一条，承载频率统计、成品照片与这一次的评分。
 *
 * [rating] 为 null 表示还没打分（老记录迁移过来也是 null），取值 1–5。
 */
@Entity(
    tableName = "cook_records",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recipeId")],
)
data class CookRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val cookedAt: Long = System.currentTimeMillis(),
    val note: String = "",
    val photoPath: String? = null,
    val rating: Int? = null,
)
