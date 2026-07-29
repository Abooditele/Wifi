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
    FILE_TRANSFER_CHUNK,
    // v2.0 packet types
    MESSAGE_EDIT,
    MESSAGE_DELETE,
    MESSAGE_REACTION,
    MESSAGE_FORWARD,
    MESSAGE_PIN,
    MESSAGE_STAR,
    PRESENCE_HEARTBEAT,
    PRESENCE_REQUEST,
    CALL_INVITE,
    CALL_ACCEPT,
    CALL_REJECT,
    CALL_END,
    CALL_SDP,
    CALL_ICE,
    GROUP_CREATE,
    GROUP_UPDATE,
    GROUP_MESSAGE
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
    val messageTypeStr: String? = null,   // TEXT, IMAGE, FILE, AUDIO, VIDEO, LOCATION, SYSTEM, CALL_SIGNAL
    val mediaName: String? = null,
    val mediaSize: Long = 0L,
    val replyToId: String? = null,
    val replyToContent: String? = null,
    val fileChunkIndex: Int = 0,
    val totalFileChunks: Int = 0,
    val fileChunkBase64: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    // v2.0 fields
    val editedContent: String? = null,
    val deleteForEveryone: Boolean = false,
    val reactionEmoji: String? = null,
    val reactionAdd: Boolean = true,
    val isForwarded: Boolean = false,
    val pin: Boolean = true,
    val star: Boolean = true,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val callType: String? = null, // AUDIO / VIDEO
    val callSdp: String? = null,
    val callIce: String? = null,
    val presenceOnline: Boolean = true,
    val groupId: String? = null,
    val groupName: String? = null,
    val groupMembersCsv: String? = null
)
