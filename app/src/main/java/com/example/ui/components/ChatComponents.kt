package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.db.entity.MessageEntity
import com.example.data.db.entity.MessageStatus
import com.example.data.db.entity.MessageType
import com.example.media.AudioPlayerState
import com.example.ui.theme.DarkIncomingBubble
import com.example.ui.theme.DarkOutgoingBubble
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LightIncomingBubble
import com.example.ui.theme.LightOutgoingBubble
import com.example.ui.theme.StatusReadBlue
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isDarkTheme: Boolean,
    isSelected: Boolean,
    audioPlayerState: AudioPlayerState,
    onToggleSelection: (String) -> Unit,
    onPlayAudio: (String, String) -> Unit,
    onImageClick: (String) -> Unit,
    onSetReaction: (String, String, Boolean) -> Unit,
    showReactionPickerFor: String?,
    setShowReactionPickerFor: (String?) -> Unit,
    onReply: (MessageEntity) -> Unit,
    onForward: (MessageEntity) -> Unit,
    onEdit: (String, String) -> Unit,
    onCopy: (String) -> Unit
) {
    val isOutgoing = message.isOutgoing
    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart

    val bubbleColor = if (isOutgoing) {
        if (isDarkTheme) DarkOutgoingBubble else LightOutgoingBubble
    } else {
        if (isDarkTheme) DarkIncomingBubble else LightIncomingBubble
    }

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 8.dp),
        contentAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            Column(horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start) {
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else bubbleColor,
                    shape = bubbleShape,
                    tonalElevation = 2.dp,
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                if (showReactionPickerFor == message.messageId) {
                                    setShowReactionPickerFor(null)
                                } else if (isSelected) {
                                    onToggleSelection(message.messageId)
                                }
                            },
                            onLongClick = {
                                setShowReactionPickerFor(message.messageId)
                            }
                        )
                        .testTag("message_bubble_${message.messageId}")
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        // Forwarded tag
                        if (message.isForwarded) {
                            Text(
                                text = "Forwarded",
                                fontSize = 11.sp,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        // Edited tag
                        if (message.isEdited) {
                            Text(
                                text = "Edited",
                                fontSize = 10.sp,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        // Pinned indicator
                        if (message.isPinned) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                                Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pinned", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // Starred indicator
                        if (message.isStarred) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(12.dp).padding(bottom = 2.dp), tint = Color(0xFFFFC107))
                        }

                        // Reply quote
                        if (!message.replyToContent.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = message.replyToContent,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        when (message.messageType) {
                            MessageType.TEXT -> {
                                Text(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            MessageType.IMAGE -> {
                                val imagePath = message.mediaPath
                                if (!imagePath.isNullOrBlank()) {
                                    AsyncImage(
                                        model = File(imagePath),
                                        contentDescription = "Image attachment",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(200.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onImageClick(imagePath) }
                                    )
                                }
                            }

                            MessageType.VIDEO -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(16.dp)
                                ) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(text = message.mediaName ?: "Video", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(text = formatFileSize(message.mediaSize), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                    }
                                }
                            }

                            MessageType.AUDIO -> {
                                val isThisPlaying = (audioPlayerState.playingMessageId == message.messageId && audioPlayerState.isPlaying)
                                val path = message.mediaPath ?: ""
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    IconButton(
                                        onClick = { onPlayAudio(message.messageId, path) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause Voice",
                                            tint = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "Voice Note", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        if (isThisPlaying && audioPlayerState.durationMs > 0) {
                                            val progress = audioPlayerState.currentPositionMs.toFloat() / audioPlayerState.durationMs.toFloat()
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier
                                                    .width(120.dp)
                                                    .height(4.dp)
                                            )
                                        } else {
                                            Text(formatFileSize(message.mediaSize), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            MessageType.FILE -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = "File", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(text = message.mediaName ?: message.content, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(text = formatFileSize(message.mediaSize), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            MessageType.LOCATION -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(12.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Location", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${message.locationLat ?: 0.0}, ${message.locationLng ?: 0.0}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            MessageType.CALL_SIGNAL -> {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                                    Icon(Icons.Default.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(message.content, fontSize = 13.sp, fontStyle = FontStyle.Italic)
                                }
                            }

                            MessageType.SYSTEM -> {
                                Text(message.content, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Reactions row (if any)
                        val reactions = parseReactions(message.reactions)
                        if (reactions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(reactions.entries.toList()) { entry ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(entry.value, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Timestamp and Status Ticks
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(message.timestamp),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            if (isOutgoing) {
                                Spacer(modifier = Modifier.width(4.dp))
                                StatusTickIcon(status = message.status)
                            }
                        }
                    }
                }

                // Reaction / action picker popover
                if (showReactionPickerFor == message.messageId) {
                    ReactionActionPicker(
                        onReaction = { emoji ->
                            onSetReaction(message.messageId, emoji, true)
                            setShowReactionPickerFor(null)
                        },
                        onReply = {
                            onReply(message)
                            setShowReactionPickerFor(null)
                        },
                        onForward = {
                            onForward(message)
                            setShowReactionPickerFor(null)
                        },
                        onCopy = {
                            onCopy(message.content)
                            setShowReactionPickerFor(null)
                        },
                        onEdit = if (message.messageType == MessageType.TEXT && isOutgoing) {
                            { onEdit(message.messageId, message.content); setShowReactionPickerFor(null) }
                        } else null,
                        onDismiss = { setShowReactionPickerFor(null) }
                    )
                }
            }
        }
    }
}

@Composable
fun ReactionActionPicker(
    onReaction: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("👍", "❤️", "😂", "😮", "😢", "🙏").forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onReaction(emoji) }
                            .padding(4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onReply) { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply") }
                IconButton(onClick = onForward) { Icon(Icons.Default.Forward, contentDescription = "Forward") }
                IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy") }
                if (onEdit != null) {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                }
            }
        }
    }
}

private fun parseReactions(json: String): Map<String, String> {
    if (json.isBlank()) return emptyMap()
    return try {
        val obj = JSONObject(json)
        val map = mutableMapOf<String, String>()
        obj.keys().forEach { k -> map[k] = obj.getString(k) }
        map
    } catch (e: Exception) { emptyMap() }
}

@Composable
fun StatusTickIcon(status: MessageStatus) {
    when (status) {
        MessageStatus.SENDING -> Icon(Icons.Default.Schedule, contentDescription = "Sending", modifier = Modifier.size(12.dp), tint = Color.Gray)
        MessageStatus.FAILED -> Icon(Icons.Default.Close, contentDescription = "Failed", modifier = Modifier.size(12.dp), tint = Color.Red)
        MessageStatus.SENT -> Icon(Icons.Default.Done, contentDescription = "Sent", modifier = Modifier.size(14.dp), tint = Color.Gray)
        MessageStatus.DELIVERED -> Icon(Icons.Default.DoneAll, contentDescription = "Delivered", modifier = Modifier.size(14.dp), tint = Color.Gray)
        MessageStatus.READ -> Icon(Icons.Default.DoneAll, contentDescription = "Read", modifier = Modifier.size(14.dp), tint = StatusReadBlue)
    }
}

@Composable
fun TypingRecordingIndicator(isTyping: Boolean, isRecording: Boolean) {
    val text = when {
        isTyping -> "typing…"
        isRecording -> "recording voice note…"
        else -> null
    }
    if (text != null) {
        val infiniteTransition = rememberInfiniteTransition()
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(600), repeatMode = RepeatMode.Reverse)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(text = text, fontSize = 12.sp, color = ElectricCyan.copy(alpha = alpha), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ReplyPreviewBar(replyMessage: MessageEntity, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Replying to message", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(text = replyMessage.content, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel reply") }
    }
}

@Composable
fun FullScreenImageViewer(imagePath: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = "Full Screen Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", size.toDouble() / (1024 * 1024))
    }
}
