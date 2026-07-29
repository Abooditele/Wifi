package com.example.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.data.repository.ChatRepository
import com.example.notification.NotificationHelper

/**
 * Foreground service that keeps the LAN Messenger listener alive in the
 * background so incoming TCP/UDP packets are received even when the app UI
 * is closed.
 *
 * The service shows a low-importance persistent notification. When an actual
 * message arrives, a separate high-importance notification is posted by
 * NotificationHelper.showMessageNotification with a deep-link PendingIntent
 * that opens the source conversation directly.
 *
 * The ChatRepository is a singleton so it is shared between the service and
 * the running Activity/ViewModel.
 */
class LanMessengerService : Service() {

    companion object {
        private const val TAG = "LanMessengerService"
        private const val ONGOING_NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.action.START_SERVICE"
        const val ACTION_STOP = "com.example.action.STOP_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        NotificationHelper.createNotificationChannels(this)
        val notification: Notification = NotificationHelper.buildServiceNotification(this)
            .build()
        startForeground(ONGOING_NOTIFICATION_ID, notification)

        // Make sure the repository is initialized (which starts TCP/UDP listeners)
        try {
            ChatRepository.getSingleton(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ChatRepository from service", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // START_STICKY so the service is restarted by the system if killed.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        super.onDestroy()
    }
}
