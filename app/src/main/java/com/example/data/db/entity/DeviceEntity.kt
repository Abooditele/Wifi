package com.example.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val deviceId: String,
    val name: String,
    val ipAddress: String,
    val tcpPort: Int,
    val isOnline: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis(),
    val avatarColorHex: String = "#0088CC",
    val statusMessage: String = "Available on LAN",
    val unreadCount: Int = 0
)
