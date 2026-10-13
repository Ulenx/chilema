package com.caidan.menu.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.caidan.menu.data.local.dao.CookRecordDao
import com.caidan.menu.data.local.dao.FridgeDao
import com.caidan.menu.data.local.dao.IngredientDao
import com.caidan.menu.data.local.dao.RecipeDao
import com.caidan.menu.data.local.dao.VideoLinkDao
import com.caidan.menu.data.local.entity.CookRecord
import com.caidan.menu.data.local.entity.FridgeItem
import com.caidan.menu.data.local.entity.Ingredient
import com.caidan.menu.data.local.entity.Recipe
import com.caidan.menu.data.local.entity.VideoLink

@Database(
    entities = [
        Recipe::class,
        Ingredient::class,
        VideoLink::class,
        CookRecord::class,
        FridgeItem::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun videoLinkDao(): VideoLinkDao
    abstract fun cookRecordDao(): CookRecordDao
    abstract fun fridgeDao(): FridgeDao

    companion object {
        /** v1 → v2：新增冰箱库存表。纯新增，既有菜谱数据原样保留。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `fridge_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `bigCategory` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `quantity` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `purchaseDate` INTEGER,
                        `expiryDate` INTEGER,
                        `source` TEXT NOT NULL,
                        `recipeId` INTEGER,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`)
                            ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_fridge_items_recipeId` " +
                        "ON `fridge_items` (`recipeId`)"
                )
            }
        }

        /** v2 → v3：冰箱条目加一张图片。纯加列，既有数据保留。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `fridge_items` ADD COLUMN `photoPath` TEXT")
            }
        }

        /**
         * v3 → v4：做菜记录加星级。纯加列，老记录 rating 为 NULL（表示没打过分）。
         *
         * 这里必须建**可空、不带 DEFAULT** 的列：Room 迁移后会拿实际表结构和实体声明做比对，
         * 一条 `NOT NULL DEFAULT 0` 的列对不上没写 `@ColumnInfo(defaultValue = "0")` 的实体，
         * 校验会直接抛异常让 App 起不来。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cook_records` ADD COLUMN `rating` INTEGER")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "menu.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
