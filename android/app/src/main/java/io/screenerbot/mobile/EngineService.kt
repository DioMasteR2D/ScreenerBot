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
        try {
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

            // The Activity owns the JNI engine in its process. This foreground
            // service only keeps the Android process eligible to run in background.
            recordStartup("Foreground service active; engine owned by MainActivity process")
            updateNotification("Η τοπική μηχανή λειτουργεί στο Android")
        } catch (error: Throwable) {
            val detail = "${error.javaClass.simpleName}: ${error.message ?: "χωρίς λεπτομέρειες"}"
            recordStartup("FOREGROUND SERVICE FAILED", detail)
            updateNotification("Αποτυχία υπηρεσίας Android: $detail")
            stopSelf(startId)
        }
        return START_STICKY
    }

    private fun recordStartup(stage: String, error: String? = null) {
        getSharedPreferences("engine_startup", MODE_PRIVATE).edit()
            .putString("stage", stage)
            .putString("error", error.orEmpty())
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
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
