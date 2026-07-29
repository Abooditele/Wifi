package com.example.network

import com.squareup.moshi.JsonClass

enum class PacketType {
    DISCOVERY,
    DISCOVERY_RESPONSE,
    DISCONNECT,
    CHAT_MESSAGE,
    ACK_DELIVERED,
    ACK_READ,
    TYPING_START,
    TYPING_STOP,
    RECORDING_START,
    RECORDING_STOP,
    FILE_TRANSFER_HEADER,
    FILE_TRANSFER_CHUNK
}

@JsonClass(generateAdapter = true)
data class NetworkPacket(
    val packetType: PacketType,
    val senderId: String,
    val senderName: String,
    val senderIp: String,
    val senderPort: Int,
    val avatarColorHex: String = "#0088CC",
    val statusMessage: String = "Available on LAN",
    val messageId: String? = null,
    val conversationDeviceId: String? = null,
    val encryptedContent: String? = null, // Base64 encrypted text or metadata
    val messageTypeStr: String? = null,   // TEXT, IMAGE, FILE, AUDIO
    val mediaName: String? = null,
    val mediaSize: Long = 0L,
    val replyToId: String? = null,
    val replyToContent: String? = null,
    val fileChunkIndex: Int = 0,
    val totalFileChunks: Int = 0,
    val fileChunkBase64: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
