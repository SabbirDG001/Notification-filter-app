package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FilterRule::class, NotificationLog::class], version = 2, exportSchema = false)
abstract class FilterDatabase : RoomDatabase() {
    abstract fun filterDao(): FilterDao

    companion object {
        @Volatile
        private var INSTANCE: FilterDatabase? = null

        fun getDatabase(context: Context): FilterDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FilterDatabase::class.java,
                    "notification_filter_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
