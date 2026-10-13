package com.caidan.menu.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 视频教学链接：属于某个菜谱，含标题与 URL。 */
@Entity(
    tableName = "video_links",
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
data class VideoLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val title: String = "",
    val url: String,
    val sortOrder: Int = 0,
)
