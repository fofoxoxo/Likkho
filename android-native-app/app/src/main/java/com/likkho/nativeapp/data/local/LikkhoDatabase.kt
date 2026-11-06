package com.likkho.nativeapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.likkho.nativeapp.data.model.CustomFontItem
import com.likkho.nativeapp.data.model.DiaryLog

@Database(
    entities = [DiaryLog::class, CustomFontItem::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class LikkhoDatabase : RoomDatabase() {

    abstract fun diaryDao(): DiaryDao

    companion object {
        @Volatile
        private var INSTANCE: LikkhoDatabase? = null

        fun getDatabase(context: Context): LikkhoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LikkhoDatabase::class.java,
                    "likkho_native_room.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
