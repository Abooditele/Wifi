package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity

/**
 * Centralised notification helper.
 *
 * Two channels:
 *  - SERVICE_CHANNEL_ID   : low-importance, ongoing foreground service notification.
 *  - MESSAGES_CHANNEL_ID  : high-importance, pop-on-top notifications for new messages.
 *
 * Message notifications include a PendingIntent with deep-link extras so that
 * tapping the notification opens MainActivity and automatically navigates to
 * the source conversation.
 */
object NotificationHelper {
    const val SERVICE_CHANNEL_ID = "lan_service_channel"
    const val MESSAGES_CHANNEL_ID = "lan_messages_channel"
    const val EXTRA_TARGET_DEVICE_ID = "EXTRA_TARGET_DEVICE_ID"
    const val EXTRA_TARGET_GROUP_ID = "EXTRA_TARGET_GROUP_ID"
    const val EXTRA_NOTIFICATION_TAG = "EXTRA_NOTIFICATION_TAG"
    const val ACTION_VIEW_CONVERSATION = "com.example.action.VIEW_CONVERSATION"
    const val REMOTE_INPUT_REPLY_KEY = "remote_input_reply_key"
    const val ACTION_REMOTE_REPLY = "com.example.action.REMOTE_REPLY"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Foreground service channel (low importance, no sound)
        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            "LAN Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps LAN Messenger alive in the background to receive messages"
            setShowBadge(false)
        }

        // Messages channel (high importance, sound + vibrate)
        val messagesChannel = NotificationChannel(
            MESSAGES_CHANNEL_ID,
            "LAN Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for incoming LAN messages"
            enableVibration(true)
            enableLights(true)
            lightColor = Color.BLUE
            vibrationPattern = longArrayOf(0, 200, 100, 200)
        }

        notificationManager.createNotificationChannels(listOf(serviceChannel, messagesChannel))
    }

    /**
     * Builds the persistent foreground-service notification shown while the
     * LAN listener is running in the background.
     */
    fun buildServiceNotification(context: Context): NotificationCompat.Builder {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("LAN Messenger")
            .setContentText("Listening for messages on local network")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setShowWhen(false)
    }

    /**
     * Shows (or updates) a high-importance notification for a new incoming message.
     *
     * Tapping the notification launches MainActivity with extras
     * [EXTRA_TARGET_DEVICE_ID] (or [EXTRA_TARGET_GROUP_ID]) so the app can
     * deep-link directly into the source conversation.
     */
    fun showMessageNotification(
        context: Context,
        senderName: String,
        messageContent: String,
        deviceId: String,
        groupId: String? = null,
        notificationId: Int = deviceId.hashCode()
    ) {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_VIEW_CONVERSATION
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_TARGET_DEVICE_ID, deviceId)
            groupId?.let { putExtra(EXTRA_TARGET_GROUP_ID, it) }
            putExtra(EXTRA_NOTIFICATION_TAG, "msg_$notificationId")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build a MessagingStyle notification with the sender as a Person
        val senderPerson = Person.Builder()
            .setName(senderName)
            .setImportant(true)
            .build()

        val style = NotificationCompat.MessagingStyle(senderPerson)
            .setConversationTitle(senderName)
            .addMessage(
                messageContent,
                System.currentTimeMillis(),
                senderPerson
            )

        // Optional inline reply action (RemoteInput)
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_REPLY_KEY)
            .setLabel("Reply")
            .build()

        val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
            action = ACTION_REMOTE_REPLY
            putExtra(EXTRA_TARGET_DEVICE_ID, deviceId)
            groupId?.let { putExtra(EXTRA_TARGET_GROUP_ID, it) }
            putExtra(EXTRA_NOTIFICATION_TAG, "msg_$notificationId")
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, android.R.drawable.ic_menu_send),
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, MESSAGES_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(senderName)
            .setContentText(messageContent)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .addAction(replyAction)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Cancels a previously shown message notification (e.g. when the user opens
     * the corresponding conversation).
     */
    fun cancelMessageNotification(context: Context, notificationId: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }
}
