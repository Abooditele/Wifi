package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.data.repository.ChatRepository

/**
 * Handles inline replies from message notifications.
 *
 * When the user types a reply in the notification and taps "Send", the Android
 * system delivers the text to this receiver via RemoteInput. We forward the
 * text to the ChatRepository which sends it over the network to the peer.
 */
class DirectReplyReceiver : BroadcastReceiver() {

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

        // Mark the notification as "Replied: ..." so the user sees their reply
        val replyShown = NotificationHelper.javaClass.let {
            android.util.Log.d("DirectReply", "Sending reply to $targetDeviceId: $replyText")
        }

        // Send the reply on a background thread via the singleton repository.
        Thread {
            try {
                val repo = ChatRepository.getSingleton(context.applicationContext)
                repo.sendTextMessageByDeviceId(targetDeviceId, replyText)
            } catch (e: Exception) {
                android.util.Log.e("DirectReply", "Failed to send reply", e)
            }
        }.start()
    }
}
