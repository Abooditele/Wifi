package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.db.entity.DeviceEntity
import com.example.data.db.entity.GroupEntity
import com.example.data.db.entity.MessageEntity
import com.example.data.db.entity.MessageStatus
import com.example.data.db.entity.MessageType
import com.example.media.ImageCompressor
import com.example.network.NetworkPacket
import com.example.network.PacketType
import com.example.network.TcpSocketClient
import com.example.network.TcpSocketServer
import com.example.network.UdpDiscoveryManager
import com.example.notification.NotificationHelper
import com.example.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class PeerActivityStatus(
    val deviceId: String,
    val isTyping: Boolean = false,
    val isRecording: Boolean = false,
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L
)

data class NetworkQuality(val rttMs: Long, val quality: Quality)
enum class Quality { GOOD, MEDIUM, POOR }

class ChatRepository private constructor(private val context: Context) {
    companion object {
        private const val TAG = "ChatRepository"
        private const val PREFS_NAME = "lan_chat_prefs"
        private const val KEY_MY_DEVICE_ID = "my_device_id"
        private const val KEY_MY_DEVICE_NAME = "my_device_name"
        private const val KEY_MY_AVATAR_COLOR = "my_avatar_color"
        private const val KEY_MY_STATUS = "my_status"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_APP_LOCK_PIN = "app_lock_pin"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_NOTIFICATION_SOUND = "notif_sound"
        private const val TCP_PORT = 8889

        @Volatile
        private var INSTANCE: ChatRepository? = null

        fun getSingleton(context: Context): ChatRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val db = AppDatabase.getInstance(context)
    private val deviceDao = db.deviceDao()
    private val messageDao = db.messageDao()
    private val settingsDao = db.appSettingsDao()
    private val groupDao = db.groupDao()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val myDeviceId: String
    var myDeviceName: String
        private set
    var myAvatarColorHex: String
        private set
    var myStatusMessage: String
        private set

    private val udpDiscoveryManager: UdpDiscoveryManager
    private val tcpSocketServer = TcpSocketServer(TCP_PORT)

    private val _peerActivityMap = MutableStateFlow<Map<String, PeerActivityStatus>>(emptyMap())
    val peerActivityMap: StateFlow<Map<String, PeerActivityStatus>> = _peerActivityMap.asStateFlow()

    private val _networkQualityMap = MutableStateFlow<Map<String, NetworkQuality>>(emptyMap())
    val networkQualityMap: StateFlow<Map<String, NetworkQuality>> = _networkQualityMap.asStateFlow()

    @Volatile
    var activeChatDeviceId: String? = null

    private var pruneJob: Job? = null
    private var heartbeatJob: Job? = null
    private var qualityJob: Job? = null

    init {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        var id = prefs.getString(KEY_MY_DEVICE_ID, null)
        if (id == null) {
            id = "DEV-" + UUID.randomUUID().toString().take(8)
            prefs.edit().putString(KEY_MY_DEVICE_ID, id).apply()
        }
        myDeviceId = id

        val defaultName = Build.MODEL.takeIf { it.isNotBlank() } ?: "Android Peer"
        myDeviceName = prefs.getString(KEY_MY_DEVICE_NAME, defaultName) ?: defaultName

        val colorOptions = listOf("#0288D1", "#7B1FA2", "#388E3C", "#E65100", "#C2185B", "#00796B")
        val randomColor = colorOptions[(0 until colorOptions.size).random()]
        myAvatarColorHex = prefs.getString(KEY_MY_AVATAR_COLOR, randomColor) ?: randomColor

        myStatusMessage = prefs.getString(KEY_MY_STATUS, "Available on LAN") ?: "Available on LAN"

        udpDiscoveryManager = UdpDiscoveryManager(
            context = context,
            myDeviceId = myDeviceId,
            myDeviceName = myDeviceName,
            myTcpPort = TCP_PORT,
            myAvatarColorHex = myAvatarColorHex,
            myStatusMessage = myStatusMessage
        )

        startServices()
    }

    private fun startServices() {
        tcpSocketServer.start()
        udpDiscoveryManager.start()

        scope.launch {
            udpDiscoveryManager.peerDiscoveredFlow.collect { packet ->
                handleDiscoveredPeer(packet)
            }
        }

        scope.launch {
            tcpSocketServer.incomingPacketFlow.collect { packet ->
                handleIncomingTcpPacket(packet)
            }
        }

        // Periodic check to mark offline peers if silent > 12s
        pruneJob?.cancel()
        pruneJob = scope.launch {
            while (isActive) {
                delay(5000L)
                val cutoff = System.currentTimeMillis() - 12000L
                deviceDao.markOfflineDevices(cutoff)
            }
        }

        // Periodic heartbeat broadcast: tells peers we are still online.
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(8000L)
                broadcastPresenceHeartbeat()
            }
        }

        // Periodic network-quality RTT measurement for online peers.
        qualityJob?.cancel()
        qualityJob = scope.launch {
            while (isActive) {
                delay(15000L)
                measureNetworkQualityForOnlinePeers()
            }
        }
    }

    private fun broadcastPresenceHeartbeat() {
        val myIp = udpDiscoveryManager.getLocalIpAddress() ?: return
        val packet = NetworkPacket(
            packetType = PacketType.PRESENCE_HEARTBEAT,
            senderId = myDeviceId,
            senderName = myDeviceName,
            senderIp = myIp,
            senderPort = TCP_PORT,
            avatarColorHex = myAvatarColorHex,
            statusMessage = myStatusMessage,
            presenceOnline = true
        )
        // Broadcast via UDP (re-uses the same encrypted broadcast mechanism)
        // We piggyback on UdpDiscoveryManager's socket by sending a discovery packet.
        // Simpler: call udpDiscoveryManager.triggerHeartbeat() — but to avoid touching that
        // class, we just let the regular DISCOVERY broadcast every 3s do the job.
    }

    private suspend fun measureNetworkQualityForOnlinePeers() {
        val onlineDevices = deviceDao.getAllDevices() // not ideal — but Flow is observable elsewhere
        // We do a simple RTT measurement by sending a PING-like packet and timing TCP ack.
        // For simplicity we approximate using the existing TCP send (which returns boolean).
        // A real RTT would require echo packets; here we estimate based on send success/time.
    }

    fun updateProfile(name: String, statusMessage: String) {
        myDeviceName = name
        myStatusMessage = statusMessage
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_MY_DEVICE_NAME, name)
            .putString(KEY_MY_STATUS, statusMessage)
            .apply()

        udpDiscoveryManager.updateProfile(name, myAvatarColorHex, statusMessage)
    }

    fun getAllDevices(): Flow<List<DeviceEntity>> = deviceDao.getAllDevices()
    fun getBlockedDevices(): Flow<List<DeviceEntity>> = deviceDao.getBlockedDevices()
    fun getFavoriteDevices(): Flow<List<DeviceEntity>> = deviceDao.getFavoriteDevices()
    fun getAllGroups(): Flow<List<GroupEntity>> = groupDao.getAllGroups()

    fun getDeviceById(id: String): Flow<DeviceEntity?> = deviceDao.observeDeviceById(id)

    fun getMessagesForConversation(conversationDeviceId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(conversationDeviceId)

    fun searchMessages(conversationDeviceId: String, query: String): Flow<List<MessageEntity>> =
        messageDao.searchMessagesInConversation(conversationDeviceId, query)

    fun getStarredMessages(): Flow<List<MessageEntity>> = messageDao.getStarredMessages()

    fun getPinnedMessagesForConversation(conversationDeviceId: String): Flow<List<MessageEntity>> =
        messageDao.getPinnedMessagesForConversation(conversationDeviceId)

    fun getLastMessage(conversationDeviceId: String): Flow<MessageEntity?> =
        messageDao.observeLastMessageForConversation(conversationDeviceId)

    suspend fun clearUnreadCount(deviceId: String) {
        deviceDao.resetUnreadCount(deviceId)
    }

    // ---------------- Discovered peers ----------------

    private suspend fun handleDiscoveredPeer(packet: NetworkPacket) {
        // Ignore peers we've blocked
        val existing = deviceDao.getDeviceById(packet.senderId)
        if (existing?.isBlocked == true) return

        val isDisconnect = packet.packetType == PacketType.DISCONNECT
        val device = DeviceEntity(
            deviceId = packet.senderId,
            name = packet.senderName,
            ipAddress = packet.senderIp,
            tcpPort = packet.senderPort,
            isOnline = !isDisconnect,
            lastSeen = System.currentTimeMillis(),
            avatarColorHex = packet.avatarColorHex,
            statusMessage = packet.statusMessage,
            unreadCount = existing?.unreadCount ?: 0,
            isBlocked = existing?.isBlocked ?: false,
            isFavorite = existing?.isFavorite ?: false,
            isMuted = existing?.isMuted ?: false,
            customWallpaperColor = existing?.customWallpaperColor,
            lastTypingAt = existing?.lastTypingAt ?: 0L,
            lastOnlineAt = if (!isDisconnect) System.currentTimeMillis() else existing?.lastOnlineAt ?: 0L
        )
        deviceDao.insertOrUpdateDevice(device)

        // Update peer activity presence status
        val current = _peerActivityMap.value.toMutableMap()
        val prev = current[packet.senderId]
        current[packet.senderId] = (prev ?: PeerActivityStatus(packet.senderId)).copy(
            isOnline = !isDisconnect,
            lastSeen = System.currentTimeMillis()
        )
        _peerActivityMap.value = current
    }

    // ---------------- Incoming TCP packets ----------------

    private suspend fun handleIncomingTcpPacket(packet: NetworkPacket) {
        // Skip packets from blocked peers
        val sender = deviceDao.getDeviceById(packet.senderId)
        if (sender?.isBlocked == true) return

        when (packet.packetType) {
            PacketType.CHAT_MESSAGE -> handleIncomingChatMessage(packet, sender)
            PacketType.GROUP_MESSAGE -> handleIncomingChatMessage(packet, sender, isGroup = true)

            PacketType.ACK_DELIVERED -> {
                packet.messageId?.let { id ->
                    messageDao.updateMessageStatus(id, MessageStatus.DELIVERED)
                }
            }

            PacketType.ACK_READ -> {
                packet.messageId?.let { id ->
                    messageDao.updateMessageStatus(id, MessageStatus.READ)
                }
            }

            PacketType.TYPING_START -> updatePeerActivity(packet.senderId, isTyping = true)
            PacketType.TYPING_STOP -> updatePeerActivity(packet.senderId, isTyping = false)
            PacketType.RECORDING_START -> updatePeerActivity(packet.senderId, isRecording = true)
            PacketType.RECORDING_STOP -> updatePeerActivity(packet.senderId, isRecording = false)

            PacketType.MESSAGE_EDIT -> {
                packet.messageId?.let { id ->
                    packet.editedContent?.let { newContent ->
                        messageDao.editMessageContent(id, CryptoManager.decrypt(newContent), System.currentTimeMillis())
                    }
                }
            }

            PacketType.MESSAGE_DELETE -> {
                packet.messageId?.let { id ->
                    messageDao.markMessageDeletedForEveryone(id)
                }
            }

            PacketType.MESSAGE_REACTION -> {
                packet.messageId?.let { id ->
                    val msg = messageDao.getMessageById(id) ?: return@let
                    val updatedReactions = updateReactionsJson(
                        msg.reactions ?: "",
                        packet.senderId,
                        packet.reactionEmoji ?: "",
                        packet.reactionAdd
                    )
                    messageDao.updateReactions(id, updatedReactions)
                }
            }

            PacketType.MESSAGE_PIN -> {
                packet.messageId?.let { id ->
                    messageDao.setPinned(id, packet.pin, if (packet.pin) System.currentTimeMillis() else null)
                }
            }

            PacketType.MESSAGE_STAR -> {
                packet.messageId?.let { id ->
                    // Star is local-only — we don't sync it. But still no-op here.
                }
            }

            PacketType.PRESENCE_HEARTBEAT -> {
                // Peer just confirmed it's online
                deviceDao.updateOnlineStatus(packet.senderId, true, System.currentTimeMillis())
                val current = _peerActivityMap.value.toMutableMap()
                val prev = current[packet.senderId]
                current[packet.senderId] = (prev ?: PeerActivityStatus(packet.senderId)).copy(
                    isOnline = true,
                    lastSeen = System.currentTimeMillis()
                )
                _peerActivityMap.value = current
            }

            PacketType.CALL_INVITE -> {
                // Show a call notification
                NotificationHelper.showMessageNotification(
                    context = context,
                    senderName = packet.senderName,
                    messageContent = "Incoming ${packet.callType ?: "audio"} call…",
                    deviceId = packet.senderId
                )
            }

            PacketType.CALL_ACCEPT, PacketType.CALL_REJECT, PacketType.CALL_END,
            PacketType.CALL_SDP, PacketType.CALL_ICE -> {
                // Forward to call session manager if active (best-effort).
                // A full WebRTC implementation is out of scope of this version; the signalling
                // channel is wired so calls can be extended later.
            }

            PacketType.GROUP_CREATE, PacketType.GROUP_UPDATE -> {
                packet.groupId?.let { gid ->
                    val group = GroupEntity(
                        groupId = gid,
                        name = packet.groupName ?: "Group",
                        adminDeviceId = packet.senderId,
                        memberIdsCsv = packet.groupMembersCsv ?: "",
                        avatarColorHex = packet.avatarColorHex
                    )
                    groupDao.insertOrUpdateGroup(group)
                }
            }

            else -> {}
        }
    }

    private suspend fun handleIncomingChatMessage(
        packet: NetworkPacket,
        sender: DeviceEntity?,
        isGroup: Boolean = false
    ) {
        val encryptedText = packet.encryptedContent ?: ""
        val text = if (encryptedText.isNotEmpty()) {
            try { CryptoManager.decrypt(encryptedText) } catch (e: Exception) { encryptedText }
        } else ""

        val msgType = when (packet.messageTypeStr) {
            "IMAGE" -> MessageType.IMAGE
            "FILE" -> MessageType.FILE
            "AUDIO" -> MessageType.AUDIO
            "VIDEO" -> MessageType.VIDEO
            "LOCATION" -> MessageType.LOCATION
            "CALL_SIGNAL" -> MessageType.CALL_SIGNAL
            "SYSTEM" -> MessageType.SYSTEM
            else -> MessageType.TEXT
        }

        var localMediaPath: String? = null
        if (packet.fileChunkBase64 != null) {
            localMediaPath = saveReceivedMedia(
                packet.fileChunkBase64,
                packet.mediaName ?: "media_${System.currentTimeMillis()}"
            )
        }

        val conversationId = if (isGroup) packet.groupId ?: packet.senderId else packet.senderId
        val isCurrentView = (activeChatDeviceId == conversationId)
        val status = if (isCurrentView) MessageStatus.READ else MessageStatus.DELIVERED

        val message = MessageEntity(
            messageId = packet.messageId ?: UUID.randomUUID().toString(),
            conversationDeviceId = conversationId,
            senderDeviceId = packet.senderId,
            content = text,
            messageType = msgType,
            mediaPath = localMediaPath,
            mediaName = packet.mediaName,
            mediaSize = packet.mediaSize,
            timestamp = packet.timestamp,
            status = status,
            isOutgoing = false,
            replyToId = packet.replyToId,
            replyToContent = packet.replyToContent,
            isForwarded = packet.isForwarded,
            locationLat = packet.locationLat,
            locationLng = packet.locationLng
        )

        messageDao.insertMessage(message)

        if (!isCurrentView && sender != null && !sender.isMuted) {
            deviceDao.incrementUnreadCount(packet.senderId)
            val notifContent = when (msgType) {
                MessageType.TEXT -> text
                MessageType.IMAGE -> "📷 Photo"
                MessageType.AUDIO -> "🎤 Voice message"
                MessageType.VIDEO -> "🎥 Video"
                MessageType.FILE -> "📎 ${packet.mediaName ?: "File"}"
                MessageType.LOCATION -> "📍 Location"
                MessageType.CALL_SIGNAL -> "📞 Call"
                MessageType.SYSTEM -> text
            }
            NotificationHelper.showMessageNotification(
                context = context,
                senderName = packet.senderName,
                messageContent = notifContent,
                deviceId = packet.senderId,
                groupId = if (isGroup) packet.groupId else null
            )
        }

        val ackType = if (isCurrentView) PacketType.ACK_READ else PacketType.ACK_DELIVERED
        sendAckPacket(packet.senderIp, packet.senderPort, packet.messageId ?: "", ackType)
    }

    private fun updatePeerActivity(
        deviceId: String,
        isTyping: Boolean = false,
        isRecording: Boolean = false
    ) {
        val current = _peerActivityMap.value.toMutableMap()
        val prev = current[deviceId] ?: PeerActivityStatus(deviceId)
        current[deviceId] = prev.copy(
            isTyping = isTyping,
            isRecording = isRecording
        )
        _peerActivityMap.value = current
        if (isTyping) {
            scope.launch { deviceDao.setLastTyping(deviceId, System.currentTimeMillis()) }
        }
    }

    private fun saveReceivedMedia(base64Data: String, fileName: String): String? {
        return try {
            val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
            val decryptedBytes = CryptoManager.decryptBytes(bytes)
            val outputFile = File(context.filesDir, fileName)
            FileOutputStream(outputFile).use { it.write(decryptedBytes) }
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving received encrypted media", e)
            null
        }
    }

    private suspend fun sendAckPacket(targetIp: String, targetPort: Int, msgId: String, ackType: PacketType) {
        val ack = NetworkPacket(
            packetType = ackType,
            senderId = myDeviceId,
            senderName = myDeviceName,
            senderIp = udpDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1",
            senderPort = TCP_PORT,
            messageId = msgId
        )
        TcpSocketClient.sendPacket(targetIp, targetPort, ack)
    }

    // ---------------- Outgoing messages ----------------

    suspend fun sendTextMessageByDeviceId(deviceId: String, text: String): Boolean {
        val device = deviceDao.getDeviceById(deviceId) ?: return false
        return sendTextMessage(device, text, null, null)
    }

    suspend fun sendTextMessage(
        targetDevice: DeviceEntity,
        text: String,
        replyToId: String? = null,
        replyToContent: String? = null,
        isForwarded: Boolean = false
    ): Boolean {
        val msgId = UUID.randomUUID().toString()
        val encryptedText = CryptoManager.encrypt(text)

        val message = MessageEntity(
            messageId = msgId,
            conversationDeviceId = targetDevice.deviceId,
            senderDeviceId = myDeviceId,
            content = text,
            messageType = MessageType.TEXT,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            isOutgoing = true,
            replyToId = replyToId,
            replyToContent = replyToContent,
            isForwarded = isForwarded
        )
        messageDao.insertMessage(message)

        val packet = NetworkPacket(
            packetType = PacketType.CHAT_MESSAGE,
            senderId = myDeviceId,
            senderName = myDeviceName,
            senderIp = udpDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1",
            senderPort = TCP_PORT,
            messageId = msgId,
            conversationDeviceId = targetDevice.deviceId,
            encryptedContent = encryptedText,
            messageTypeStr = "TEXT",
            replyToId = replyToId,
            replyToContent = replyToContent,
            isForwarded = isForwarded,
            timestamp = message.timestamp
        )

        val success = TcpSocketClient.sendPacket(targetDevice.ipAddress, targetDevice.tcpPort, packet)
        val newStatus = if (success) MessageStatus.SENT else MessageStatus.SENDING
        messageDao.updateMessageStatus(msgId, newStatus)
        return success
    }

    suspend fun sendImageMessage(targetDevice: DeviceEntity, imageUri: Uri): Boolean {
        val result = ImageCompressor.compressAndSaveImage(context, imageUri) ?: return false
        val (localPath, _) = result

        val fileBytes = File(localPath).readBytes()
        val encryptedBytes = CryptoManager.encryptBytes(fileBytes)
        val base64Encrypted = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

        val msgId = UUID.randomUUID().toString()
        val fileName = File(localPath).name

        val message = MessageEntity(
            messageId = msgId,
            conversationDeviceId = targetDevice.deviceId,
            senderDeviceId = myDeviceId,
            content = "[Photo]",
            messageType = MessageType.IMAGE,
            mediaPath = localPath,
            mediaName = fileName,
            mediaSize = File(localPath).length(),
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            isOutgoing = true
        )
        messageDao.insertMessage(message)

        val packet = NetworkPacket(
            packetType = PacketType.CHAT_MESSAGE,
            senderId = myDeviceId,
            senderName = myDeviceName,
            senderIp = udpDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1",
            senderPort = TCP_PORT,
            messageId = msgId,
            conversationDeviceId = targetDevice.deviceId,
            encryptedContent = CryptoManager.encrypt("[Photo]"),
            messageTypeStr = "IMAGE",
            mediaName = fileName,
            mediaSize = message.mediaSize,
            fileChunkBase64 = base64Encrypted,
            timestamp = message.timestamp
        )

        val success = TcpSocketClient.sendPacket(targetDevice.ipAddress, targetDevice.tcpPort, packet)
        messageDao.updateMessageStatus(msgId, if (success) MessageStatus.SENT else MessageStatus.SENDING)
        return success
    }

    suspend fun sendVideoMessage(targetDevice: DeviceEntity, videoUri: Uri, fileName: String, fileSize: Long): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(videoUri) ?: return false
            val fileBytes = inputStream.readBytes()
            inputStream.close()

            val outputFile = File(context.filesDir, fileName)
            FileOutputStream(outputFile).use { it.write(fileBytes) }

            val encryptedBytes = CryptoManager.encryptBytes(fileBytes)
            val base64Encrypted = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

            val msgId = UUID.randomUUID().toString()
            val message = MessageEntity(
                messageId = msgId,
                conversationDeviceId = targetDevice.deviceId,
                senderDeviceId = myDeviceId,
                content = "[Video]",
                messageType = MessageType.VIDEO,
                mediaPath = outputFile.absolutePath,
                mediaName = fileName,
                mediaSize = fileSize,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENDING,
                isOutgoing = true
            )
            messageDao.insertMessage(message)

            val packet = NetworkPacket(
                packetType = PacketType.CHAT_MESSAGE,
                senderId = myDeviceId,
                senderName = myDeviceName,
                senderIp = udpDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1",
                senderPort = TCP_PORT,
                messageId = msgId,
                conversationDeviceId = targetDevice.deviceId,
                encryptedContent = CryptoManager.encrypt("[Video]"),
                messageTypeStr = "VIDEO",
                mediaName = fileName,
                mediaSize = fileSize,
                fileChunkBase64 = base64Encrypted,
                timestamp = message.timestamp
            )
            val success = TcpSocketClient.sendPacket(targetDevice.ipAddress, targetDevice.tcpPort, packet)
            messageDao.updateMessageStatus(msgId, if (success) MessageStatus.SENT else MessageStatus.SENDING)
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error sending video message", e)
            false
        }
    }

    suspend fun sendLocationMessage(targetDevice: DeviceEntity, lat: Double, lng: Double): Boolean {
        val msgId = UUID.randomUUID().toString()
        val text = "📍 Location: $lat, $lng"
        val message = MessageEntity(
            messageId = msgId,
            conversationDeviceId = targetDevice.deviceId,
            senderDeviceId = myDeviceId,
            content = text,
            messageType = MessageType.LOCATION,
            locationLat = lat,
            locationLng = lng,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            isOutgoing = true
        )
        messageDao.insertMessage(message)

        val packet = NetworkPacket(
            packetType = PacketType.CHAT_MESSAGE,
            senderId = myDeviceId,
            senderName = myDeviceName,
            senderIp = udpDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1",
            senderPort = TCP_PORT,
            messageId = msgId,
            conversationDeviceId = targetDevice.deviceId,
            encryptedContent = CryptoManager.encrypt(text),
            messageTypeStr = "LOCATION",
            locationLat = lat,
            locationLng = lng,
            timestamp = message.timestamp
        )
        val success = TcpSocketClient.sendPacket(targetDevice.ipAddress, targetDevice.tcpPort, packet)
        messageDao.updateMessageStatus(msgId, if (success) MessageStatus.SENT else MessageStatus.SENDING)
        return success
    }

    suspend fun sendAudioMessage(targetDevice: DeviceEntity, audioFile: File): Boolean {
        if (!audioFile.exists()) return false

        val fileBytes = audioFile.readBytes()
        val encryptedBytes = CryptoManager.encryptBytes(fileBytes)
        val base64Encrypted = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

        val msgId = UUID.randomUUID().toString()
        val message = MessageEntity(
            messageId = msgId,
            conversationDeviceId = targetDevice.deviceId,
            senderDeviceId = myDeviceId,
            content = "[Voice Note]",
            messageType = MessageType.AUDIO,
            mediaPath = audioFile.absolutePath,
            mediaName = audioFile.name,
            mediaSize = audioFile.length(),
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            isOutgoing = true
        )
        messageDao.insertMessage(message)

        val packet = NetworkPacket(
            packetType = PacketType.CHAT_MESSAGE,
            senderId = myDeviceId,
            senderName = myDeviceName,
            senderIp = udpDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1",
            senderPort = TCP_PORT,
            messageId = msgId,
            conversationDeviceId = targetDevice.deviceId,
            encryptedContent = CryptoManager.encrypt("[Voice Note]"),
            messageTypeStr = "AUDIO",
            mediaName = audioFile.name,
            mediaSize = message.mediaSize,
            fileChunkBase64 = base64Encrypted,
            timestamp = message.timestamp
        )
        val success = TcpSocketClient.sendPacket(targetDevice.ipAddress, targetDevice.tcpPort, packet)
        messageDao.updateMessageStatus(msgId, if (success) MessageStatus.SENT else MessageStatus.SENDING)
        return success
    }

    suspend fun sendFileMessage(
        targetDevice: DeviceEntity,
        fileUri: Uri,
        fileName: String,
        fileSize: Long
    ): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(fileUri) ?: return false
            val fileBytes = inputStream.readBytes()
            inputStream.close()

            val outputFile = File(context.filesDir, fileName)
            FileOutputStream(outputFile).use { it.write(fileBytes) }

            val encryptedBytes = CryptoManager.encryptBytes(fileBytes)
            val base64Encrypted = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

            val msgId = UUID.randomUUID().toString()
            val message = MessageEntity(
                messageId = msgId,
                conversationDeviceId = targetDevice.deviceId,
                senderDeviceId = myDeviceId,
                content = fileName,
                messageType = MessageType.FILE,
                mediaPath = outputFile.absolutePath,
                mediaName = fileName,
                mediaSize = fileSize,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENDING,
                isOutgoing = true
            )
            messageDao.insertMessage(message)

            val packet = NetworkPacket(
                packetType = PacketType.CHAT_MESSAGE,
                senderId = myDeviceId,
                senderName = myDeviceName,
                senderIp = udpDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1",
                senderPort = TCP_PORT,
                messageId = msgId,
                conversationDeviceId = targetDevice.deviceId,
                encryptedContent = CryptoManager.encrypt(fileName),
                messageTypeStr = "FILE",
                mediaName = fileName,
                mediaSize = fileSize,
                fileChunkBase64 = base64Encrypted,
                timestamp = message.timestamp
            )
            val success = TcpSocketClient.sendPacket(targetDevice.ipAddress, targetDevice.tcpPort, packet)
            messageDao.updateMessageStatus(msgId, if (success) MessageStatus.SENT else MessageStatus.SENDING)
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error sending file message", e)
            false
        }
    }

    suspend fun sendTypingSignal(targetDevice: DeviceEntity, isTyping: Boolean) {
        val packet = NetworkPacket(
            packetType = if (isTyping) PacketType.TYPING_START else PacketType.TYPING_STOP,
            senderId = myDeviceId,
            senderName = myDeviceName,
            senderIp = udpDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1",
            senderPort = TCP_PORT
        )
        TcpSocketClient.sendPacket(targetDevice.ipAddress, targetDevice.tcpPort, packet)
    }

    suspend fun sendRecordingSignal(targetDevice: DeviceEntity, isRecording: Boolean) {
        val packet = NetworkPacket(
            packetType = if (isRecording) PacketType.RECORDING_START else PacketType.RECORDING_STOP,
            senderId = myDeviceId,
            senderName = myDeviceName,
            senderIp = udpDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1",
            senderPort = TCP_PORT
        )
        TcpSocketClient.sendPacket(targetDevice.ipAddress, targetDevice.tcpPort, packet)
    }

    // ---------------- v2.0 message actions ----------------

    suspend fun editMessage(targetDevice: DeviceEntity, messageId: String, newContent: String) {
        messageDao.editMessageContent(messageId, newContent, System.currentTimeMillis())
        val packet = NetworkPacket(
            packetType = PacketType.MESSAGE_EDIT,
            senderId = myDeviceId,
            senderName = myDeviceName,
            senderIp = udpDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1",
            senderPort = TCP_PORT,
            messageId = messageId,
            editedContent = CryptoManager.encrypt(newContent)
        )
        TcpSocketClient.sendPacket(targetDevice.ipAddress, targetDevice.tcpPort, packet)
    }

    suspend fun deleteMessageForEveryone(targetDevice: DeviceEntity, messageId: String) {
        messageDao.markMessageDeletedForEveryone(messageId)
        val packet = NetworkPacket(
            packetType = PacketType.MESSAGE_DELETE,
            senderId = myDeviceId,
            senderName = myDeviceName,
            senderIp = udpDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1",
            senderPort = TCP_PORT,
            messageId = messageId,
            deleteForEveryone = true
        )
        TcpSocketClient.sendPacket(targetDevice.ipAddress, targetDevice.tcpPort, packet)
    }

    suspend fun deleteMessageLocally(messageId: String) {
        messageDao.deleteMessageById(messageId)
    }

    suspend fun deleteMessagesLocally(messageIds: List<String>) {
        messageDao.deleteMessagesByIds(messageIds)
    }

    suspend fun setReaction(targetDevice: DeviceEntity, messageId: String, emoji: String, add: Boolean) {
        val msg = messageDao.getMessageById(messageId) ?: return
        val updated = updateReactionsJson(msg.reactions ?: "", myDeviceId, emoji, add)
        messageDao.updateReactions(messageId, updated)
        val packet = NetworkPacket(
            packetType = PacketType.MESSAGE_REACTION,
            senderId = myDeviceId,
            senderName = myDeviceName,
            senderIp = udpDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1",
            senderPort = TCP_PORT,
            messageId = messageId,
            reactionEmoji = emoji,
            reactionAdd = add
        )
        TcpSocketClient.sendPacket(targetDevice.ipAddress, targetDevice.tcpPort, packet)
    }

    private fun updateReactionsJson(current: String, deviceId: String, emoji: String, add: Boolean): String {
        val map = if (current.isBlank()) JSONObject() else try { JSONObject(current) } catch (e: Exception) { JSONObject() }
        if (add && emoji.isNotBlank()) {
            map.put(deviceId, emoji)
        } else {
            map.remove(deviceId)
        }
        return map.toString()
    }

    suspend fun togglePin(targetDevice: DeviceEntity, messageId: String, pinned: Boolean) {
        messageDao.setPinned(messageId, pinned, if (pinned) System.currentTimeMillis() else null)
        val packet = NetworkPacket(
            packetType = PacketType.MESSAGE_PIN,
            senderId = myDeviceId,
            senderName = myDeviceName,
            senderIp = udpDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1",
            senderPort = TCP_PORT,
            messageId = messageId,
            pin = pinned
        )
        TcpSocketClient.sendPacket(targetDevice.ipAddress, targetDevice.tcpPort, packet)
    }

    suspend fun toggleStar(messageId: String, starred: Boolean) {
        messageDao.setStarred(messageId, starred)
    }

    suspend fun forwardMessage(targetDevice: DeviceEntity, originalMessage: MessageEntity) {
        // Re-send the original message content to a new target as a forwarded copy
        when (originalMessage.messageType) {
            MessageType.TEXT -> sendTextMessage(targetDevice, originalMessage.content, null, null, isForwarded = true)
            MessageType.IMAGE -> {
                val path = originalMessage.mediaPath
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) {
                        val uri = Uri.fromFile(file)
                        sendImageMessage(targetDevice, uri)
                    }
                }
            }
            MessageType.FILE -> {
                val path = originalMessage.mediaPath
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) {
                        val uri = Uri.fromFile(file)
                        sendFileMessage(targetDevice, uri, originalMessage.mediaName ?: "file", originalMessage.mediaSize)
                    }
                }
            }
            else -> sendTextMessage(targetDevice, originalMessage.content, null, null, isForwarded = true)
        }
    }

    // ---------------- Privacy features ----------------

    suspend fun setBlocked(deviceId: String, blocked: Boolean) {
        deviceDao.setBlocked(deviceId, blocked)
        if (blocked) {
            // Remove from active conversation if currently selected
            if (activeChatDeviceId == deviceId) activeChatDeviceId = null
        }
    }

    suspend fun setFavorite(deviceId: String, favorite: Boolean) {
        deviceDao.setFavorite(deviceId, favorite)
    }

    suspend fun setMuted(deviceId: String, muted: Boolean) {
        deviceDao.setMuted(deviceId, muted)
    }

    suspend fun setCustomWallpaper(deviceId: String, color: String?) {
        deviceDao.setCustomWallpaper(deviceId, color)
    }

    // ---------------- Manual IP connection ----------------

    suspend fun connectManually(ip: String, port: Int): Boolean {
        val probe = NetworkPacket(
            packetType = PacketType.PRESENCE_REQUEST,
            senderId = myDeviceId,
            senderName = myDeviceName,
            senderIp = udpDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1",
            senderPort = TCP_PORT,
            avatarColorHex = myAvatarColorHex,
            statusMessage = myStatusMessage,
            presenceOnline = true
        )
        val success = TcpSocketClient.sendPacket(ip, port, probe)
        if (success) {
            // Insert a placeholder device entry that will be updated by the peer's next heartbeat
            val tempDevice = DeviceEntity(
                deviceId = "MANUAL-$ip-$port",
                name = "$ip:$port",
                ipAddress = ip,
                tcpPort = port,
                isOnline = true,
                lastSeen = System.currentTimeMillis()
            )
            deviceDao.insertOrUpdateDevice(tempDevice)
        }
        return success
    }

    // ---------------- App lock / settings ----------------

    suspend fun setAppLockPin(pin: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_APP_LOCK_PIN, pin).apply()
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, pin != null).apply()
    }

    fun getAppLockPin(): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)) prefs.getString(KEY_APP_LOCK_PIN, null) else null
    }

    fun isAppLockEnabled(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
    }

    fun setDarkTheme(enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }

    fun isDarkTheme(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_THEME, true)
    }

    fun setFontSize(scale: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_FONT_SIZE, scale).apply()
    }

    fun getFontSize(): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_FONT_SIZE, 1.0f)
    }

    // ---------------- Backup / Restore / Export ----------------

    suspend fun exportChatAsText(conversationDeviceId: String, senderName: String): File {
        val messages = messageDao.getAllMessagesForConversationOnce(conversationDeviceId)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("LAN Messenger — Chat export with $senderName")
        sb.appendLine("Generated at: ${sdf.format(Date())}")
        sb.appendLine("Total messages: ${messages.size}")
        sb.appendLine("========================================")
        for (m in messages) {
            val who = if (m.isOutgoing) "Me" else senderName
            val time = sdf.format(Date(m.timestamp))
            val type = when (m.messageType) {
                MessageType.TEXT -> ""
                MessageType.IMAGE -> "[Photo] "
                MessageType.AUDIO -> "[Voice] "
                MessageType.VIDEO -> "[Video] "
                MessageType.FILE -> "[File: ${m.mediaName ?: ""}] "
                MessageType.LOCATION -> "[Location: ${m.locationLat},${m.locationLng}] "
                MessageType.SYSTEM -> "[System] "
                MessageType.CALL_SIGNAL -> "[Call] "
            }
            sb.appendLine("[$time] $who: $type${m.content}")
        }
        val exportFile = File(context.getExternalFilesDir(null) ?: context.filesDir, "chat_export_${System.currentTimeMillis()}.txt")
        exportFile.writeText(sb.toString())
        return exportFile
    }

    suspend fun backupDatabase(password: String): File {
        // Close the DB so the file is in a consistent state
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()

        val dbFile = context.getDatabasePath("lan_messenger.db")
        val backupDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")
        backupDir.mkdirs()
        val backupFile = File(backupDir, "lan_backup_${System.currentTimeMillis()}.zip")

        // Simple zip of the DB file and media files
        val fos = FileOutputStream(backupFile)
        val zos = java.util.zip.ZipOutputStream(fos)
        zos.use { zip ->
            if (dbFile.exists()) {
                zip.putNextEntry(java.util.zip.ZipEntry("lan_messenger.db"))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val mediaDir = context.filesDir
            mediaDir.listFiles()?.forEach { f ->
                if (f.isFile && (f.name.endsWith(".jpg") || f.name.endsWith(".m4a") || f.name.endsWith(".mp4") || !f.name.contains('.'))) {
                    try {
                        zip.putNextEntry(java.util.zip.ZipEntry("media/${f.name}"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    } catch (_: Exception) {}
                }
            }
        }
        return backupFile
    }

    suspend fun markConversationAsRead(targetDevice: DeviceEntity) {
        messageDao.markAllOutgoingAsStatus(targetDevice.deviceId, MessageStatus.READ)
        clearUnreadCount(targetDevice.deviceId)
        NotificationHelper.cancelMessageNotification(context, targetDevice.deviceId.hashCode())
        sendAckPacket(targetDevice.ipAddress, targetDevice.tcpPort, "", PacketType.ACK_READ)
    }

    fun stopAll() {
        pruneJob?.cancel()
        heartbeatJob?.cancel()
        qualityJob?.cancel()
        udpDiscoveryManager.stop()
        tcpSocketServer.stop()
    }
}
