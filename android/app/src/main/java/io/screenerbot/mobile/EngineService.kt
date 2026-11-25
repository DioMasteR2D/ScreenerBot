package io.screenerbot.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class EngineService : Service() {
    companion object {
        private const val CHANNEL_ID = "screenerbot_engine"
        private const val NOTIFICATION_ID = 2601
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            val machineIdentifier = AndroidIdentity.getOrCreateMachineIdentifier(this)
            if (!NativeEngine.start(filesDir.resolve("screenerbot").absolutePath, machineIdentifier)) {
                updateNotification("Αποτυχία αρχικοποίησης της μηχανής. Έλεγξε τα αρχεία καταγραφής.")
            }
        } catch (error: UnsatisfiedLinkError) {
            updateNotification("Λείπει η βιβλιοθήκη της μηχανής. Χρειάζεται νέα έκδοση της εφαρμογής.")
        } catch (error: Exception) {
            updateNotification("Σφάλμα εκκίνησης μηχανής: ${error.message ?: "άγνωστο"}")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Μηχανή ScreenerBot",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String = "Η τοπική μηχανή ξεκινά"): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("ScreenerBot")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }
}
