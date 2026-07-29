package com.example.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageType {
    TEXT,
    IMAGE,
    FILE,
    AUDIO
}

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ
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
    val replyToContent: String? = null
)
