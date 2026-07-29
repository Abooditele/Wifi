package com.example.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageType {
    TEXT,
    IMAGE,
    FILE,
    AUDIO,
    VIDEO,
    LOCATION,
    SYSTEM,
    CALL_SIGNAL
}

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val conversationDeviceId: String,
    val senderDeviceId: String,
    val content: String,
    val messageType: MessageType = MessageType.TEXT,
    val mediaPath: String? = null,
    val mediaName: String? = null,
    val mediaSize: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENDING,
    val isOutgoing: Boolean = true,
    val replyToId: String? = null,
    val replyToContent: String? = null,
    // v2.0 fields
    val isEdited: Boolean = false,
    val editedAt: Long? = null,
    val isDeletedForEveryone: Boolean = false,
    val isForwarded: Boolean = false,
    val isStarred: Boolean = false,
    val isPinned: Boolean = false,
    val pinnedAt: Long? = null,
    val reactions: String = "", // JSON map of deviceId -> emoji
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val callDurationMs: Long = 0L,
    val callStatus: String? = null // OUTGOING/MISSED/DECLINED/COMPLETED
)
