package com.example.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Group chat entity. A group has an admin (the creator) and a list of member device IDs.
 * For simplicity, member device IDs are stored as a comma-separated string.
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val description: String = "",
    val avatarColorHex: String = "#0088CC",
    val adminDeviceId: String,
    val memberIdsCsv: String, // comma-separated device IDs
    val createdAt: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0
)
