package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.entity.DeviceEntity
import com.example.data.db.entity.GroupEntity
import com.example.data.db.entity.MessageEntity
import com.example.data.repository.ChatRepository
import com.example.data.repository.NetworkQuality
import com.example.data.repository.PeerActivityStatus
import com.example.media.AudioPlayer
import com.example.media.AudioPlayerState
import com.example.media.AudioRecorder
import com.example.notification.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = ChatRepository.getSingleton(application)
    private val audioRecorder = AudioRecorder(application)
    private val audioPlayer = AudioPlayer(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDevice = MutableStateFlow<DeviceEntity?>(null)
    val selectedDevice: StateFlow<DeviceEntity?> = _selectedDevice.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(repository.isDarkTheme())
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _fontSizeScale = MutableStateFlow(repository.getFontSize())
    val fontSizeScale: StateFlow<Float> = _fontSizeScale.asStateFlow()

    val devices: StateFlow<List<DeviceEntity>> = combine(
        repository.getAllDevices(),
        _searchQuery
    ) { deviceList, query ->
        if (query.isBlank()) {
            deviceList
        } else {
            deviceList.filter { it.name.contains(query, ignoreCase = true) || it.ipAddress.contains(query) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedDevices: StateFlow<List<DeviceEntity>> =
        repository.getBlockedDevices().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDevices: StateFlow<List<DeviceEntity>> =
        repository.getFavoriteDevices().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<GroupEntity>> =
        repository.getAllGroups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentMessages: StateFlow<List<MessageEntity>> = _selectedDevice.flatMapLatest { dev ->
        if (dev == null) flowOf(emptyList()) else repository.getMessagesForConversation(dev.deviceId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pinnedMessages: StateFlow<List<MessageEntity>> = _selectedDevice.flatMapLatest { dev ->
        if (dev == null) flowOf(emptyList()) else repository.getPinnedMessagesForConversation(dev.deviceId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val peerActivityMap: StateFlow<Map<String, PeerActivityStatus>> = repository.peerActivityMap
    val networkQualityMap: StateFlow<Map<String, NetworkQuality>> = repository.networkQualityMap

    val audioPlayerState: StateFlow<AudioPlayerState> = audioPlayer.playerState

    private val _selectedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMessageIds: StateFlow<Set<String>> = _selectedMessageIds.asStateFlow()

    private val _replyToMessage = MutableStateFlow<MessageEntity?>(null)
    val replyToMessage: StateFlow<MessageEntity?> = _replyToMessage.asStateFlow()

    private val _isRecordingAudio = MutableStateFlow(false)
    val isRecordingAudio: StateFlow<Boolean> = _isRecordingAudio.asStateFlow()

    private val _showAppLock = MutableStateFlow(repository.isAppLockEnabled())
    val showAppLock: StateFlow<Boolean> = _showAppLock.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private var currentAudioRecordingFile: File? = null
    private var typingJob: Job? = null

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun toggleDarkTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
        repository.setDarkTheme(_isDarkTheme.value)
    }

    fun setDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled
        repository.setDarkTheme(enabled)
    }

    fun setFontSizeScale(scale: Float) {
        _fontSizeScale.value = scale
        repository.setFontSize(scale)
    }

    fun selectDevice(device: DeviceEntity?) {
        _selectedDevice.value = device
        repository.activeChatDeviceId = device?.deviceId
        _selectedMessageIds.value = emptySet()
        _replyToMessage.value = null

        if (device != null) {
            viewModelScope.launch {
                repository.markConversationAsRead(device)
            }
        }
    }

    /**
     * Called by MainActivity when the user taps a notification. The intent
     * carries EXTRA_TARGET_DEVICE_ID — we look up the corresponding device
     * and select it so the UI deep-links into the source conversation.
     */
    fun handleDeepLinkIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action != NotificationHelper.ACTION_VIEW_CONVERSATION) return
        val targetDeviceId = intent.getStringExtra(NotificationHelper.EXTRA_TARGET_DEVICE_ID) ?: return
        viewModelScope.launch {
            // Devices are observed via Flow, but we can also fetch synchronously via a one-shot query.
            // Use a small polling approach for up to ~2s waiting for the device to appear.
            val device = findDeviceById(targetDeviceId)
            if (device != null) selectDevice(device)
        }
    }

    private suspend fun findDeviceById(deviceId: String): DeviceEntity? {
        // Try a few times because the device may not yet be in the DB when a notification is tapped
        // from a brand-new peer.
        repeat(10) {
            val list = devices.value
            val match = list.firstOrNull { it.deviceId == deviceId }
            if (match != null) return match
            delay(200)
        }
        return devices.value.firstOrNull { it.deviceId == deviceId }
    }

    fun updateMyProfile(name: String, statusMessage: String) {
        repository.updateProfile(name, statusMessage)
    }

    fun onTextInputChanged(text: String) {
        val target = _selectedDevice.value ?: return
        if (text.isNotBlank()) {
            typingJob?.cancel()
            typingJob = viewModelScope.launch {
                repository.sendTypingSignal(target, true)
                delay(2000L)
                repository.sendTypingSignal(target, false)
            }
        } else {
            typingJob?.cancel()
            viewModelScope.launch { repository.sendTypingSignal(target, false) }
        }
    }

    fun sendTextMessage(text: String) {
        val target = _selectedDevice.value ?: return
        if (text.isBlank()) return
        val reply = _replyToMessage.value
        _replyToMessage.value = null
        viewModelScope.launch {
            repository.sendTypingSignal(target, false)
            repository.sendTextMessage(
                targetDevice = target,
                text = text.trim(),
                replyToId = reply?.messageId,
                replyToContent = reply?.content
            )
        }
    }

    fun sendImageMessage(uri: Uri) {
        val target = _selectedDevice.value ?: return
        viewModelScope.launch { repository.sendImageMessage(target, uri) }
    }

    fun sendVideoMessage(uri: Uri, name: String, size: Long) {
        val target = _selectedDevice.value ?: return
        viewModelScope.launch { repository.sendVideoMessage(target, uri, name, size) }
    }

    fun sendFileMessage(uri: Uri, fileName: String, fileSize: Long) {
        val target = _selectedDevice.value ?: return
        viewModelScope.launch { repository.sendFileMessage(target, uri, fileName, fileSize) }
    }

    fun sendLocationMessage(lat: Double, lng: Double) {
        val target = _selectedDevice.value ?: return
        viewModelScope.launch { repository.sendLocationMessage(target, lat, lng) }
    }

    fun startRecordingAudio() {
        val target = _selectedDevice.value ?: return
        currentAudioRecordingFile = audioRecorder.startRecording()
        if (currentAudioRecordingFile != null) {
            _isRecordingAudio.value = true
            viewModelScope.launch { repository.sendRecordingSignal(target, true) }
        }
    }

    fun stopAndSendAudioRecording() {
        val target = _selectedDevice.value ?: return
        val recordedFile = audioRecorder.stopRecording()
        _isRecordingAudio.value = false
        viewModelScope.launch {
            repository.sendRecordingSignal(target, false)
            if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                repository.sendAudioMessage(target, recordedFile)
            }
        }
    }

    fun cancelAudioRecording() {
        val target = _selectedDevice.value
        audioRecorder.cancelRecording()
        _isRecordingAudio.value = false
        if (target != null) {
            viewModelScope.launch { repository.sendRecordingSignal(target, false) }
        }
    }

    fun playAudio(messageId: String, path: String) {
        audioPlayer.playAudio(messageId, path)
    }

    fun toggleMessageSelection(messageId: String) {
        val current = _selectedMessageIds.value.toMutableSet()
        if (current.contains(messageId)) current.remove(messageId) else current.add(messageId)
        _selectedMessageIds.value = current
    }

    fun clearMessageSelection() { _selectedMessageIds.value = emptySet() }

    fun setReplyToMessage(message: MessageEntity) { _replyToMessage.value = message }
    fun clearReplyToMessage() { _replyToMessage.value = null }

    fun deleteSelectedMessages(deleteForEveryone: Boolean) {
        val target = _selectedDevice.value ?: return
        val ids = _selectedMessageIds.value.toList()
        viewModelScope.launch {
            if (deleteForEveryone) {
                ids.forEach { repository.deleteMessageForEveryone(target, it) }
            } else {
                repository.deleteMessagesLocally(ids)
            }
            _selectedMessageIds.value = emptySet()
        }
    }

    fun editMessage(messageId: String, newContent: String) {
        val target = _selectedDevice.value ?: return
        viewModelScope.launch { repository.editMessage(target, messageId, newContent) }
    }

    fun setReaction(messageId: String, emoji: String, add: Boolean) {
        val target = _selectedDevice.value ?: return
        viewModelScope.launch { repository.setReaction(target, messageId, emoji, add) }
    }

    fun togglePinMessage(messageId: String, pinned: Boolean) {
        val target = _selectedDevice.value ?: return
        viewModelScope.launch { repository.togglePin(target, messageId, pinned) }
    }

    fun toggleStarMessage(messageId: String, starred: Boolean) {
        viewModelScope.launch { repository.toggleStar(messageId, starred) }
    }

    fun forwardMessage(targetDevice: DeviceEntity, originalMessage: MessageEntity) {
        viewModelScope.launch { repository.forwardMessage(targetDevice, originalMessage) }
    }

    fun setBlocked(deviceId: String, blocked: Boolean) {
        viewModelScope.launch { repository.setBlocked(deviceId, blocked) }
    }

    fun setFavorite(deviceId: String, favorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(deviceId, favorite) }
    }

    fun setMuted(deviceId: String, muted: Boolean) {
        viewModelScope.launch { repository.setMuted(deviceId, muted) }
    }

    fun setCustomWallpaper(deviceId: String, color: String?) {
        viewModelScope.launch { repository.setCustomWallpaper(deviceId, color) }
    }

    fun connectManually(ip: String, port: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.connectManually(ip, port)
            onResult(ok)
        }
    }

    fun setAppLockPin(pin: String?) {
        viewModelScope.launch { repository.setAppLockPin(pin) }
        _showAppLock.value = (pin != null)
    }

    fun unlockApp() { _showAppLock.value = false }

    fun verifyPin(enteredPin: String): Boolean {
        val stored = repository.getAppLockPin()
        return stored != null && stored == enteredPin
    }

    fun exportCurrentChat(onResult: (File) -> Unit) {
        val target = _selectedDevice.value ?: return
        viewModelScope.launch {
            val f = repository.exportChatAsText(target.deviceId, target.name)
            onResult(f)
        }
    }

    fun backupData(onResult: (File) -> Unit) {
        viewModelScope.launch {
            val f = repository.backupDatabase("lanmessenger")
            onResult(f)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stopAudio()
        // Don't call repository.stopAll() — the singleton lives on with the foreground service
    }
}

enum class ConnectionStatus { CONNECTED, RECONNECTING, OFFLINE }
