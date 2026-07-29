package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.entity.DeviceEntity
import com.example.data.db.entity.MessageEntity
import com.example.data.repository.PeerActivityStatus
import com.example.media.AudioPlayerState
import com.example.ui.components.FullScreenImageViewer
import com.example.ui.components.MessageBubble
import com.example.ui.components.ReplyPreviewBar
import com.example.ui.components.TypingRecordingIndicator
import com.example.ui.theme.OfflineGrey
import com.example.ui.theme.OnlineGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    device: DeviceEntity,
    messages: List<MessageEntity>,
    peerActivity: PeerActivityStatus?,
    isDarkTheme: Boolean,
    audioPlayerState: AudioPlayerState,
    selectedMessageIds: Set<String>,
    replyToMessage: MessageEntity?,
    isRecordingAudio: Boolean,
    onBack: () -> Unit,
    onSendText: (String) -> Unit,
    onSendImage: (Uri) -> Unit,
    onSendFile: (Uri, String, Long) -> Unit,
    onStartRecordingAudio: () -> Unit,
    onStopAndSendAudioRecording: () -> Unit,
    onCancelAudioRecording: () -> Unit,
    onTextInputChanged: (String) -> Unit,
    onPlayAudio: (String, String) -> Unit,
    onToggleMessageSelection: (String) -> Unit,
    onClearMessageSelection: () -> Unit,
    onReplyToMessage: (MessageEntity) -> Unit,
    onClearReply: () -> Unit,
    onDeleteSelectedMessages: () -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var fullScreenImagePath by remember { mutableStateOf<String?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Scroll to latest message on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onSendImage(it) }
    }

    // File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            val (name, size) = getFileInfo(context, fileUri)
            onSendFile(fileUri, name, size)
        }
    }

    Scaffold(
        topBar = {
            if (selectedMessageIds.isNotEmpty()) {
                // Multi-Selection App Bar
                TopAppBar(
                    title = { Text("${selectedMessageIds.size} Selected") },
                    navigationIcon = {
                        IconButton(onClick = onClearMessageSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        if (selectedMessageIds.size == 1) {
                            val selectedMsg = messages.find { selectedMessageIds.contains(it.messageId) }
                            if (selectedMsg != null) {
                                IconButton(onClick = {
                                    copyToClipboard(context, selectedMsg.content)
                                    onClearMessageSelection()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                }
                                IconButton(onClick = {
                                    onReplyToMessage(selectedMsg)
                                    onClearMessageSelection()
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply")
                                }
                            }
                        }
                        IconButton(onClick = onDeleteSelectedMessages) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Peer Avatar
                            Surface(
                                color = parseHexColor(device.avatarColorHex),
                                shape = CircleShape,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = device.name.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = device.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                val statusText = when {
                                    peerActivity?.isTyping == true -> "typing..."
                                    peerActivity?.isRecording == true -> "recording voice note..."
                                    device.isOnline -> "Online • ${device.ipAddress}"
                                    else -> "Offline"
                                }

                                Text(
                                    text = statusText,
                                    fontSize = 11.sp,
                                    color = if (device.isOnline) OnlineGreen else OfflineGrey
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("chat_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                items(messages, key = { it.messageId }) { msg ->
                    MessageBubble(
                        message = msg,
                        isDarkTheme = isDarkTheme,
                        isSelected = selectedMessageIds.contains(msg.messageId),
                        audioPlayerState = audioPlayerState,
                        onToggleSelection = onToggleMessageSelection,
                        onPlayAudio = onPlayAudio,
                        onImageClick = { fullScreenImagePath = it }
                    )
                }
            }

            // Typing or Recording Indicator
            TypingRecordingIndicator(
                isTyping = peerActivity?.isTyping == true,
                isRecording = peerActivity?.isRecording == true
            )

            // Reply Preview Bar
            if (replyToMessage != null) {
                ReplyPreviewBar(
                    replyMessage = replyToMessage,
                    onCancel = onClearReply
                )
            }

            // Bottom Input Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRecordingAudio) {
                        // Audio Recording Active Bar
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Recording audio voice note...",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = onCancelAudioRecording) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Recording", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        IconButton(
                            onClick = onStopAndSendAudioRecording,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Recording", tint = Color.White)
                        }
                    } else {
                        // Attachment Button & Dropdown Menu
                        Box {
                            IconButton(
                                onClick = { showAttachmentMenu = true },
                                modifier = Modifier.testTag("attachment_button")
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Attach media")
                            }

                            DropdownMenu(
                                expanded = showAttachmentMenu,
                                onDismissRequest = { showAttachmentMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Photo Gallery") },
                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                    onClick = {
                                        showAttachmentMenu = false
                                        imagePickerLauncher.launch("image/*")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Send File / Document") },
                                    leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                                    onClick = {
                                        showAttachmentMenu = false
                                        filePickerLauncher.launch("*/*")
                                    }
                                )
                            }
                        }

                        // Text Field Input
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                                onTextInputChanged(it)
                            },
                            placeholder = { Text("Message") },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .testTag("chat_input_text_field")
                        )

                        // Send Text or Record Voice Note
                        if (inputText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    onSendText(inputText)
                                    inputText = ""
                                },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .testTag("send_message_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Message",
                                    tint = Color.White
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onStartRecordingAudio,
                                modifier = Modifier.testTag("record_audio_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Record Voice Note",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Full Screen Image Dialog if clicked
    fullScreenImagePath?.let { path ->
        FullScreenImageViewer(
            imagePath = path,
            onDismiss = { fullScreenImagePath = null }
        )
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF0088CC)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Copied LAN Message", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun getFileInfo(context: Context, uri: Uri): Pair<String, Long> {
    var name = "file_${System.currentTimeMillis()}"
    var size = 0L

    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex != -1) name = cursor.getString(nameIndex)
            if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
        }
    }

    return Pair(name, size)
}
