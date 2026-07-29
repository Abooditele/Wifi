package com.example.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.db.entity.AppSettingsEntity

@Dao
interface AppSettingsDao {
    @Query("SELECT value FROM app_settings WHERE key = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM app_settings")
    suspend fun getAll(): List<AppSettingsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: AppSettingsEntity)

    @Query("DELETE FROM app_settings WHERE key = :key")
    suspend fun delete(key: String)
}
