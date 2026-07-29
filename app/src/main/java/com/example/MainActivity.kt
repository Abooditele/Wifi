package com.example

import android.Manifest
import android.content.Intent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.FontScaling
import com.example.service.LanMessengerService
import com.example.ui.MainViewModel
import com.example.ui.components.AppLockScreen
import com.example.ui.components.ProfileSettingsDialog
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DeviceListScreen
import com.example.ui.theme.LANMessengerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start foreground service so the listener survives in the background
        startLanService()

        // Handle any deep-link from a notification tap
        viewModel.handleDeepLinkIntent(intent)

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            val fontSizeScale by viewModel.fontSizeScale.collectAsState()
            val devices by viewModel.devices.collectAsState()
            val selectedDevice by viewModel.selectedDevice.collectAsState()
            val searchQuery by viewModel.searchQuery.collectAsState()
            val messages by viewModel.currentMessages.collectAsState()
            val pinnedMessages by viewModel.pinnedMessages.collectAsState()
            val peerActivityMap by viewModel.peerActivityMap.collectAsState()
            val audioPlayerState by viewModel.audioPlayerState.collectAsState()
            val selectedMessageIds by viewModel.selectedMessageIds.collectAsState()
            val replyToMessage by viewModel.replyToMessage.collectAsState()
            val isRecordingAudio by viewModel.isRecordingAudio.collectAsState()
            val showAppLock by viewModel.showAppLock.collectAsState()

            var showSettingsDialog by remember { mutableStateOf(false) }

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

            // Apply font-size scaling via LocalDensity override.
            // Density takes (density, fontScale) where fontScale multiplies the
            // system font scale; we multiply by the user-selected scale (default 1.0).
            val baseDensity = LocalDensity.current
            val scaledDensity = Density(
                density = baseDensity.density,
                fontScale = baseDensity.fontScale * fontSizeScale
            )

            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensity provides scaledDensity
            ) {
                LANMessengerTheme(darkTheme = isDarkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when {
                            showAppLock -> AppLockScreen(
                                onUnlock = { pin ->
                                    if (viewModel.verifyPin(pin)) {
                                        viewModel.unlockApp()
                                    }
                                }
                            )
                            else -> {
                                val currentTarget = selectedDevice
                                if (currentTarget == null) {
                                    DeviceListScreen(
                                        devices = devices,
                                        searchQuery = searchQuery,
                                        myDeviceName = viewModel.repository.myDeviceName,
                                        myDeviceId = viewModel.repository.myDeviceId,
                                        blockedDevices = viewModel.blockedDevices.collectAsState().value,
                                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                        onDeviceSelected = { viewModel.selectDevice(it) },
                                        onOpenSettings = { showSettingsDialog = true },
                                        onConnectManually = { ip, port, cb -> viewModel.connectManually(ip, port, cb) },
                                        onSetBlocked = { id, blocked -> viewModel.setBlocked(id, blocked) }
                                    )
                                } else {
                                    val peerActivity = peerActivityMap[currentTarget.deviceId]
                                    ChatScreen(
                                        device = currentTarget,
                                        messages = messages,
                                        pinnedMessages = pinnedMessages,
                                        peerActivity = peerActivity,
                                        isDarkTheme = isDarkTheme,
                                        audioPlayerState = audioPlayerState,
                                        selectedMessageIds = selectedMessageIds,
                                        replyToMessage = replyToMessage,
                                        isRecordingAudio = isRecordingAudio,
                                        onBack = { viewModel.selectDevice(null) },
                                        onSendText = { viewModel.sendTextMessage(it) },
                                        onSendImage = { viewModel.sendImageMessage(it) },
                                        onSendVideo = { uri, name, size -> viewModel.sendVideoMessage(uri, name, size) },
                                        onSendFile = { uri, name, size -> viewModel.sendFileMessage(uri, name, size) },
                                        onSendLocation = { lat, lng -> viewModel.sendLocationMessage(lat, lng) },
                                        onStartRecordingAudio = { viewModel.startRecordingAudio() },
                                        onStopAndSendAudioRecording = { viewModel.stopAndSendAudioRecording() },
                                        onCancelAudioRecording = { viewModel.cancelAudioRecording() },
                                        onTextInputChanged = { viewModel.onTextInputChanged(it) },
                                        onPlayAudio = { msgId, path -> viewModel.playAudio(msgId, path) },
                                        onToggleMessageSelection = { viewModel.toggleMessageSelection(it) },
                                        onClearMessageSelection = { viewModel.clearMessageSelection() },
                                        onReplyToMessage = { viewModel.setReplyToMessage(it) },
                                        onClearReply = { viewModel.clearReplyToMessage() },
                                        onDeleteSelectedMessages = { forEveryone -> viewModel.deleteSelectedMessages(forEveryone) },
                                        onEditMessage = { id, content -> viewModel.editMessage(id, content) },
                                        onSetReaction = { id, emoji, add -> viewModel.setReaction(id, emoji, add) },
                                        onTogglePin = { id, pinned -> viewModel.togglePinMessage(id, pinned) },
                                        onToggleStar = { id, starred -> viewModel.toggleStarMessage(id, starred) },
                                        onExportChat = { cb -> viewModel.exportCurrentChat(cb) },
                                        onSetMuted = { muted -> viewModel.setMuted(currentTarget.deviceId, muted) },
                                        onSetWallpaper = { color -> viewModel.setCustomWallpaper(currentTarget.deviceId, color) }
                                    )
                                }

                                if (showSettingsDialog) {
                                    ProfileSettingsDialog(
                                        currentName = viewModel.repository.myDeviceName,
                                        currentStatus = viewModel.repository.myStatusMessage,
                                        myDeviceId = viewModel.repository.myDeviceId,
                                        isDarkTheme = isDarkTheme,
                                        fontSizeScale = fontSizeScale,
                                        appLockPin = viewModel.repository.getAppLockPin(),
                                        onToggleDarkTheme = { viewModel.toggleDarkTheme() },
                                        onFontSizeChange = { viewModel.setFontSizeScale(it) },
                                        onSetAppLockPin = { viewModel.setAppLockPin(it) },
                                        onBackupData = { viewModel.backupData { } },
                                        onSaveProfile = { name, status -> viewModel.updateMyProfile(name, status) },
                                        onDismiss = { showSettingsDialog = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Called when the activity is already running and a new intent is delivered
        // (e.g. user taps a notification while the app is open).
        setIntent(intent)
        viewModel.handleDeepLinkIntent(intent)
    }

    private fun startLanService() {
        val serviceIntent = Intent(this, LanMessengerService::class.java).apply {
            action = LanMessengerService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
