package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.db.entity.DeviceEntity
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
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class PeerActivityStatus(
    val deviceId: String,
    val isTyping: Boolean = false,
    val isRecording: Boolean = false
)

class ChatRepository(private val context: Context) {
    companion object {
        private const val TAG = "ChatRepository"
        private const val PREFS_NAME = "lan_chat_prefs"
        private const val KEY_MY_DEVICE_ID = "my_device_id"
        private const val KEY_MY_DEVICE_NAME = "my_device_name"
        private const val KEY_MY_AVATAR_COLOR = "my_avatar_color"
        private const val KEY_MY_STATUS = "my_status"
        private const val TCP_PORT = 8889
    }

    private val db = AppDatabase.getInstance(context)
    private val deviceDao = db.deviceDao()
    private val messageDao = db.messageDao()

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

    // Tracks peer activity (Typing... / Recording...)
    private val _peerActivityMap = MutableStateFlow<Map<String, PeerActivityStatus>>(emptyMap())
    val peerActivityMap: StateFlow<Map<String, PeerActivityStatus>> = _peerActivityMap.asStateFlow()

    var activeChatDeviceId: String? = null

    private var pruneJob: Job? = null

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

        // Observe discovered UDP peers
        scope.launch {
            udpDiscoveryManager.peerDiscoveredFlow.collect { packet ->
                handleDiscoveredPeer(packet)
            }
        }

        // Observe incoming TCP packets
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

    fun getDeviceById(id: String): Flow<DeviceEntity?> = deviceDao.observeDeviceById(id)

    fun getMessagesForConversation(conversationDeviceId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(conversationDeviceId)

    fun searchMessages(conversationDeviceId: String, query: String): Flow<List<MessageEntity>> =
        messageDao.searchMessagesInConversation(conversationDeviceId, query)

    fun getLastMessage(conversationDeviceId: String): Flow<MessageEntity?> =
        messageDao.observeLastMessageForConversation(conversationDeviceId)

    suspend fun clearUnreadCount(deviceId: String) {
        deviceDao.resetUnreadCount(deviceId)
    }

    private suspend fun handleDiscoveredPeer(packet: NetworkPacket) {
        val isDisconnect = packet.packetType == PacketType.DISCONNECT
        val device = DeviceEntity(
            deviceId = packet.senderId,
            name = packet.senderName,
            ipAddress = packet.senderIp,
            tcpPort = packet.senderPort,
            isOnline = !isDisconnect,
            lastSeen = System.currentTimeMillis(),
            avatarColorHex = packet.avatarColorHex,
            statusMessage = packet.statusMessage
        )
        deviceDao.insertOrUpdateDevice(device)
    }

    private suspend fun handleIncomingTcpPacket(packet: NetworkPacket) {
        when (packet.packetType) {
            PacketType.CHAT_MESSAGE -> {
                val encryptedText = packet.encryptedContent ?: ""
                val text = if (encryptedText.isNotEmpty()) {
                    try { CryptoManager.decrypt(encryptedText) } catch (e: Exception) { encryptedText }
                } else ""

                val msgType = when (packet.messageTypeStr) {
                    "IMAGE" -> MessageType.IMAGE
                    "FILE" -> MessageType.FILE
                    "AUDIO" -> MessageType.AUDIO
                    else -> MessageType.TEXT
                }

                // If media was included in chunk Base64
                var localMediaPath: String? = null
                if (packet.fileChunkBase64 != null) {
                    localMediaPath = saveReceivedMedia(packet.fileChunkBase64, packet.mediaName ?: "media_${System.currentTimeMillis()}")
                }

                val isCurrentView = (activeChatDeviceId == packet.senderId)
                val status = if (isCurrentView) MessageStatus.READ else MessageStatus.DELIVERED

                val message = MessageEntity(
                    messageId = packet.messageId ?: UUID.randomUUID().toString(),
                    conversationDeviceId = packet.senderId,
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
                    replyToContent = packet.replyToContent
                )

                messageDao.insertMessage(message)

                if (!isCurrentView) {
                    deviceDao.incrementUnreadCount(packet.senderId)
                    NotificationHelper.showMessageNotification(
                        context = context,
                        senderName = packet.senderName,
                        messageContent = if (msgType == MessageType.TEXT) text else "[${msgType.name}]",
                        deviceId = packet.senderId
                    )
                }

                // Send back ACK DELIVERED or ACK READ
                val ackType = if (isCurrentView) PacketType.ACK_READ else PacketType.ACK_DELIVERED
                sendAckPacket(packet.senderIp, packet.senderPort, packet.messageId ?: "", ackType)
            }

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

            PacketType.TYPING_START -> {
                updatePeerActivity(packet.senderId, isTyping = true, isRecording = false)
            }

            PacketType.TYPING_STOP -> {
                updatePeerActivity(packet.senderId, isTyping = false, isRecording = false)
            }

            PacketType.RECORDING_START -> {
                updatePeerActivity(packet.senderId, isTyping = false, isRecording = true)
            }

            PacketType.RECORDING_STOP -> {
                updatePeerActivity(packet.senderId, isTyping = false, isRecording = false)
            }

            else -> {}
        }
    }

    private fun updatePeerActivity(deviceId: String, isTyping: Boolean, isRecording: Boolean) {
        val current = _peerActivityMap.value.toMutableMap()
        current[deviceId] = PeerActivityStatus(deviceId, isTyping, isRecording)
        _peerActivityMap.value = current
    }

    private fun saveReceivedMedia(base64Data: String, fileName: String): String? {
        return try {
            val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
            val decryptedBytes = CryptoManager.decryptBytes(bytes)
            val outputFile = File(context.filesDir, fileName)
            val fos = FileOutputStream(outputFile)
            fos.write(decryptedBytes)
            fos.flush()
            fos.close()
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

    suspend fun sendTextMessage(
        targetDevice: DeviceEntity,
        text: String,
        replyToId: String? = null,
        replyToContent: String? = null
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
            replyToContent = replyToContent
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
            timestamp = message.timestamp
        )

        val success = TcpSocketClient.sendPacket(targetDevice.ipAddress, targetDevice.tcpPort, packet)
        val newStatus = if (success) MessageStatus.SENT else MessageStatus.SENDING
        messageDao.updateMessageStatus(msgId, newStatus)

        return success
    }

    suspend fun sendImageMessage(targetDevice: DeviceEntity, imageUri: Uri): Boolean {
        val result = ImageCompressor.compressAndSaveImage(context, imageUri) ?: return false
        val (localPath, base64Raw) = result

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
        val newStatus = if (success) MessageStatus.SENT else MessageStatus.SENDING
        messageDao.updateMessageStatus(msgId, newStatus)

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
        val newStatus = if (success) MessageStatus.SENT else MessageStatus.SENDING
        messageDao.updateMessageStatus(msgId, newStatus)

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
            val newStatus = if (success) MessageStatus.SENT else MessageStatus.SENDING
            messageDao.updateMessageStatus(msgId, newStatus)

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

    suspend fun markConversationAsRead(targetDevice: DeviceEntity) {
        messageDao.markAllOutgoingAsStatus(targetDevice.deviceId, MessageStatus.READ)
        clearUnreadCount(targetDevice.deviceId)
        sendAckPacket(targetDevice.ipAddress, targetDevice.tcpPort, "", PacketType.ACK_READ)
    }

    suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessageById(messageId)
    }

    suspend fun deleteMessages(messageIds: List<String>) {
        messageDao.deleteMessagesByIds(messageIds)
    }

    fun stopAll() {
        pruneJob?.cancel()
        udpDiscoveryManager.stop()
        tcpSocketServer.stop()
    }
}
