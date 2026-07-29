package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.MainViewModel
import com.example.ui.components.ProfileSettingsDialog
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DeviceListScreen
import com.example.ui.theme.LANMessengerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            val devices by viewModel.devices.collectAsState()
            val selectedDevice by viewModel.selectedDevice.collectAsState()
            val searchQuery by viewModel.searchQuery.collectAsState()
            val messages by viewModel.currentMessages.collectAsState()
            val peerActivityMap by viewModel.peerActivityMap.collectAsState()
            val audioPlayerState by viewModel.audioPlayerState.collectAsState()
            val selectedMessageIds by viewModel.selectedMessageIds.collectAsState()
            val replyToMessage by viewModel.replyToMessage.collectAsState()
            val isRecordingAudio by viewModel.isRecordingAudio.collectAsState()

            var showSettingsDialog by remember { mutableStateOf(false) }

            // Runtime Permission Launcher for Record Audio and Notifications
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { _ -> }

            LaunchedEffect(Unit) {
                val permissionsToRequest = mutableListOf<String>()
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (permissionsToRequest.isNotEmpty()) {
                    permissionLauncher.launch(permissionsToRequest.toTypedArray())
                }
            }

            LANMessengerTheme(darkTheme = isDarkTheme) {
                val currentTarget = selectedDevice

                if (currentTarget == null) {
                    DeviceListScreen(
                        devices = devices,
                        searchQuery = searchQuery,
                        myDeviceName = viewModel.repository.myDeviceName,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onDeviceSelected = { viewModel.selectDevice(it) },
                        onOpenSettings = { showSettingsDialog = true }
                    )
                } else {
                    val peerActivity = peerActivityMap[currentTarget.deviceId]
                    ChatScreen(
                        device = currentTarget,
                        messages = messages,
                        peerActivity = peerActivity,
                        isDarkTheme = isDarkTheme,
                        audioPlayerState = audioPlayerState,
                        selectedMessageIds = selectedMessageIds,
                        replyToMessage = replyToMessage,
                        isRecordingAudio = isRecordingAudio,
                        onBack = { viewModel.selectDevice(null) },
                        onSendText = { viewModel.sendTextMessage(it) },
                        onSendImage = { viewModel.sendImageMessage(it) },
                        onSendFile = { uri, name, size -> viewModel.sendFileMessage(uri, name, size) },
                        onStartRecordingAudio = { viewModel.startRecordingAudio() },
                        onStopAndSendAudioRecording = { viewModel.stopAndSendAudioRecording() },
                        onCancelAudioRecording = { viewModel.cancelAudioRecording() },
                        onTextInputChanged = { viewModel.onTextInputChanged(it) },
                        onPlayAudio = { msgId, path -> viewModel.playAudio(msgId, path) },
                        onToggleMessageSelection = { viewModel.toggleMessageSelection(it) },
                        onClearMessageSelection = { viewModel.clearMessageSelection() },
                        onReplyToMessage = { viewModel.setReplyToMessage(it) },
                        onClearReply = { viewModel.clearReplyToMessage() },
                        onDeleteSelectedMessages = { viewModel.deleteSelectedMessages() }
                    )
                }

                if (showSettingsDialog) {
                    ProfileSettingsDialog(
                        currentName = viewModel.repository.myDeviceName,
                        currentStatus = viewModel.repository.myStatusMessage,
                        myDeviceId = viewModel.repository.myDeviceId,
                        isDarkTheme = isDarkTheme,
                        onToggleDarkTheme = { viewModel.toggleDarkTheme() },
                        onSaveProfile = { name, status ->
                            viewModel.updateMyProfile(name, status)
                        },
                        onDismiss = { showSettingsDialog = false }
                    )
                }
            }
        }
    }
}

