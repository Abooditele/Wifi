package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.entity.DeviceEntity
import com.example.data.db.entity.MessageEntity
import com.example.data.repository.ChatRepository
import com.example.data.repository.PeerActivityStatus
import com.example.media.AudioPlayer
import com.example.media.AudioPlayerState
import com.example.media.AudioRecorder
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

    val repository = ChatRepository(application)
    private val audioRecorder = AudioRecorder(application)
    private val audioPlayer = AudioPlayer(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDevice = MutableStateFlow<DeviceEntity?>(null)
    val selectedDevice: StateFlow<DeviceEntity?> = _selectedDevice.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    // Filtered devices based on search query
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

    // Messages for selected device
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentMessages: StateFlow<List<MessageEntity>> = _selectedDevice.flatMapLatest { dev ->
        if (dev == null) flowOf(emptyList()) else repository.getMessagesForConversation(dev.deviceId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val peerActivityMap: StateFlow<Map<String, PeerActivityStatus>> = repository.peerActivityMap

    val audioPlayerState: StateFlow<AudioPlayerState> = audioPlayer.playerState

    private val _selectedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMessageIds: StateFlow<Set<String>> = _selectedMessageIds.asStateFlow()

    private val _replyToMessage = MutableStateFlow<MessageEntity?>(null)
    val replyToMessage: StateFlow<MessageEntity?> = _replyToMessage.asStateFlow()

    private val _isRecordingAudio = MutableStateFlow(false)
    val isRecordingAudio: StateFlow<Boolean> = _isRecordingAudio.asStateFlow()

    private var currentAudioRecordingFile: File? = null
    private var typingJob: Job? = null

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleDarkTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
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
        viewModelScope.launch {
            repository.sendImageMessage(target, uri)
        }
    }

    fun sendFileMessage(uri: Uri, fileName: String, fileSize: Long) {
        val target = _selectedDevice.value ?: return
        viewModelScope.launch {
            repository.sendFileMessage(target, uri, fileName, fileSize)
        }
    }

    fun startRecordingAudio() {
        val target = _selectedDevice.value ?: return
        currentAudioRecordingFile = audioRecorder.startRecording()
        if (currentAudioRecordingFile != null) {
            _isRecordingAudio.value = true
            viewModelScope.launch {
                repository.sendRecordingSignal(target, true)
            }
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
            viewModelScope.launch {
                repository.sendRecordingSignal(target, false)
            }
        }
    }

    fun playAudio(messageId: String, path: String) {
        audioPlayer.playAudio(messageId, path)
    }

    fun toggleMessageSelection(messageId: String) {
        val current = _selectedMessageIds.value.toMutableSet()
        if (current.contains(messageId)) {
            current.remove(messageId)
        } else {
            current.add(messageId)
        }
        _selectedMessageIds.value = current
    }

    fun clearMessageSelection() {
        _selectedMessageIds.value = emptySet()
    }

    fun setReplyToMessage(message: MessageEntity) {
        _replyToMessage.value = message
    }

    fun clearReplyToMessage() {
        _replyToMessage.value = null
    }

    fun deleteSelectedMessages() {
        val ids = _selectedMessageIds.value.toList()
        viewModelScope.launch {
            repository.deleteMessages(ids)
            _selectedMessageIds.value = emptySet()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stopAudio()
        repository.stopAll()
    }
}
