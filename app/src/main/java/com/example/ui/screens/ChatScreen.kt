package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
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
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    device: DeviceEntity,
    messages: List<MessageEntity>,
    pinnedMessages: List<MessageEntity>,
    peerActivity: PeerActivityStatus?,
    isDarkTheme: Boolean,
    audioPlayerState: AudioPlayerState,
    selectedMessageIds: Set<String>,
    replyToMessage: MessageEntity?,
    isRecordingAudio: Boolean,
    onBack: () -> Unit,
    onSendText: (String) -> Unit,
    onSendImage: (Uri) -> Unit,
    onSendVideo: (Uri, String, Long) -> Unit,
    onSendFile: (Uri, String, Long) -> Unit,
    onSendLocation: (Double, Double) -> Unit,
    onStartRecordingAudio: () -> Unit,
    onStopAndSendAudioRecording: () -> Unit,
    onCancelAudioRecording: () -> Unit,
    onTextInputChanged: (String) -> Unit,
    onPlayAudio: (String, String) -> Unit,
    onToggleMessageSelection: (String) -> Unit,
    onClearMessageSelection: () -> Unit,
    onReplyToMessage: (MessageEntity) -> Unit,
    onClearReply: () -> Unit,
    onDeleteSelectedMessages: (Boolean) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onSetReaction: (String, String, Boolean) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onToggleStar: (String, Boolean) -> Unit,
    onExportChat: ((java.io.File) -> Unit) -> Unit,
    onSetMuted: (Boolean) -> Unit,
    onSetWallpaper: (String?) -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var fullScreenImagePath by remember { mutableStateOf<String?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showReactionPickerFor by remember { mutableStateOf<String?>(null) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }
    val wallpaperColor = device.customWallpaperColor?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
    }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { onSendImage(it) } }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            val (name, size) = getFileInfo(context, fileUri)
            onSendVideo(fileUri, name, size)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            val (name, size) = getFileInfo(context, fileUri)
            onSendFile(fileUri, name, size)
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { _ -> /* placeholder: real impl uses FusedLocationProviderClient */ }

    // ===== The KEY keyboard fix: apply imePadding() + navigationBarsPadding()
    // to the WHOLE Scaffold so that whenever the soft keyboard opens, every
    // part of the screen (including the input bar) is pushed up accordingly.
    Scaffold(
        modifier = Modifier
            .imePadding()
            .navigationBarsPadding(),
        topBar = {
            if (selectedMessageIds.isNotEmpty()) {
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
                                }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy") }
                                IconButton(onClick = {
                                    onReplyToMessage(selectedMsg)
                                    onClearMessageSelection()
                                }) { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply") }
                                IconButton(onClick = {
                                    onToggleStar(selectedMsg.messageId, !selectedMsg.isStarred)
                                    onClearMessageSelection()
                                }) {
                                    Icon(
                                        if (selectedMsg.isStarred) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "Star"
                                    )
                                }
                                IconButton(onClick = {
                                    onTogglePin(selectedMsg.messageId, !selectedMsg.isPinned)
                                    onClearMessageSelection()
                                }) { Icon(Icons.Default.PushPin, contentDescription = "Pin") }
                                if (selectedMsg.messageType.name == "TEXT") {
                                    IconButton(onClick = {
                                        editingMessageId = selectedMsg.messageId
                                        editingText = selectedMsg.content
                                        onClearMessageSelection()
                                    }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                                }
                            }
                        }
                        IconButton(onClick = { onDeleteSelectedMessages(false) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete for me")
                        }
                        if (selectedMessageIds.size == 1) {
                            IconButton(onClick = { onDeleteSelectedMessages(true) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete for everyone", tint = MaterialTheme.colorScheme.error)
                            }
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
                                    peerActivity?.isTyping == true -> "typing…"
                                    peerActivity?.isRecording == true -> "recording voice note…"
                                    device.isOnline -> "Online • ${device.ipAddress}"
                                    else -> "Last seen ${formatLastSeen(device.lastOnlineAt)}"
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
                    actions = {
                        IconButton(onClick = {
                            Toast.makeText(context, "Call signalling sent", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.Call, contentDescription = "Voice call") }
                        IconButton(onClick = {
                            Toast.makeText(context, "Video call signalling sent", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.Videocam, contentDescription = "Video call") }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export chat as text") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        Toast.makeText(context, "Exporting chat…", Toast.LENGTH_SHORT).show()
                                        onExportChat { f ->
                                            Toast.makeText(context, "Saved: ${f.absolutePath}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (device.isMuted) "Unmute notifications" else "Mute notifications") },
                                    leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onSetMuted(!device.isMuted)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Set wallpaper (random)") },
                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        val colors = listOf("#FFC107", "#E91E63", "#9C27B0", "#3F51B5", "#009688", "#FF5722", "#795548", "#607D8B")
                                        onSetWallpaper(colors.random())
                                    }
                                )
                            }
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
                .background(wallpaperColor ?: MaterialTheme.colorScheme.background)
        ) {
            // Pinned messages bar (if any)
            if (pinnedMessages.isNotEmpty() && selectedMessageIds.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pinned: ${pinnedMessages.first().content}",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (pinnedMessages.size > 1) {
                            Text("+${pinnedMessages.size - 1}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

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
                        onImageClick = { fullScreenImagePath = it },
                        onSetReaction = onSetReaction,
                        showReactionPickerFor = showReactionPickerFor,
                        setShowReactionPickerFor = { showReactionPickerFor = it },
                        onReply = onReplyToMessage,
                        onForward = { /* forwarding target picker would open here */ },
                        onEdit = { id, newContent -> onEditMessage(id, newContent) },
                        onCopy = { copyToClipboard(context, it) }
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
                ReplyPreviewBar(replyMessage = replyToMessage, onCancel = onClearReply)
            }

            // Edit preview bar
            if (editingMessageId != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Editing message", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            editingMessageId = null
                            editingText = ""
                        }) { Icon(Icons.Default.Close, contentDescription = "Cancel edit") }
                        IconButton(onClick = {
                            editingMessageId?.let { onEditMessage(it, editingText) }
                            editingMessageId = null
                            editingText = ""
                        }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Save edit") }
                    }
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = { editingText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            // Bottom Input Bar — wrapped with imePadding() AGAIN as belt-and-braces,
            // in case the outer scaffold inset doesn't catch some OEM keyboards.
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRecordingAudio) {
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
                                    text = "Recording audio voice note…",
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
                        Box {
                            IconButton(
                                onClick = { showAttachmentMenu = true },
                                modifier = Modifier.testTag("attachment_button")
                            ) { Icon(Icons.Default.AttachFile, contentDescription = "Attach media") }
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
                                    text = { Text("Video") },
                                    leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null) },
                                    onClick = {
                                        showAttachmentMenu = false
                                        videoPickerLauncher.launch("video/*")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Audio / Music") },
                                    leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                                    onClick = {
                                        showAttachmentMenu = false
                                        filePickerLauncher.launch("audio/*")
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
                                DropdownMenuItem(
                                    text = { Text("Share Location") },
                                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                    onClick = {
                                        showAttachmentMenu = false
                                        // Send a fixed placeholder location. A real impl would use
                                        // FusedLocationProviderClient to fetch the GPS fix.
                                        Toast.makeText(context, "Location sending is a placeholder", Toast.LENGTH_SHORT).show()
                                        onSendLocation(33.3152, 44.3661) // Baghdad coords as placeholder
                                    }
                                )
                            }
                        }

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

    fullScreenImagePath?.let { path ->
        FullScreenImageViewer(imagePath = path, onDismiss = { fullScreenImagePath = null })
    }
}

private fun parseHexColor(hex: String): Color {
    return try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color(0xFF0088CC) }
}

private fun formatLastSeen(ts: Long): String {
    if (ts <= 0L) return "offline"
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
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
