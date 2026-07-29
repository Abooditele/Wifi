package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.db.dao.AppSettingsDao
import com.example.data.db.dao.DeviceDao
import com.example.data.db.dao.GroupDao
import com.example.data.db.dao.MessageDao
import com.example.data.db.entity.AppSettingsEntity
import com.example.data.db.entity.DeviceEntity
import com.example.data.db.entity.GroupEntity
import com.example.data.db.entity.MessageEntity

@Database(
    entities = [
        DeviceEntity::class,
        MessageEntity::class,
        AppSettingsEntity::class,
        GroupEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun messageDao(): MessageDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lan_messenger.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
