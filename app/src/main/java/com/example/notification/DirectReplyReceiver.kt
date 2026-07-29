package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles inline replies from message notifications.
 *
 * When the user types a reply in the notification and taps "Send", the Android
 * system delivers the text to this receiver via RemoteInput. We forward the
 * text to the ChatRepository which sends it over the network to the peer.
 */
class DirectReplyReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationHelper.ACTION_REMOTE_REPLY) return

        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = remoteInput.getCharSequence(NotificationHelper.REMOTE_INPUT_REPLY_KEY)
            ?.toString()
            ?.trim()
            ?: return

        if (replyText.isBlank()) return

        val targetDeviceId =
            intent.getStringExtra(NotificationHelper.EXTRA_TARGET_DEVICE_ID) ?: return
        val notificationTag = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_TAG)
        val notificationId = notificationTag?.removePrefix("msg_")?.toIntOrNull()
            ?: targetDeviceId.hashCode()

        android.util.Log.d("DirectReply", "Sending reply to $targetDeviceId: $replyText")

        // Send the reply from a coroutine since sendTextMessageByDeviceId is suspend.
        scope.launch {
            try {
                val repo = ChatRepository.getSingleton(context.applicationContext)
                repo.sendTextMessageByDeviceId(targetDeviceId, replyText)
            } catch (e: Exception) {
                android.util.Log.e("DirectReply", "Failed to send reply", e)
            }
        }
    }
}
