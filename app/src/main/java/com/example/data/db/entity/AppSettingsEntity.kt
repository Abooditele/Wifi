package com.example.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Key-value store for app-level preferences (theme, font size, lock pin, etc.)
 * Persisted in the same Room DB so it is included in backups.
 */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)
