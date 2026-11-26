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

            recordStartup("Preparing local Android data directory")
            val dataDirectory = filesDir.resolve("screenerbot")
            if (!dataDirectory.exists() && !dataDirectory.mkdirs()) {
                throw IllegalStateException("Δεν δημιουργήθηκε ο τοπικός φάκελος δεδομένων: ${dataDirectory.absolutePath}")
            }

            recordStartup("Creating Android Keystore identity")
            val machineIdentifier = AndroidIdentity.getOrCreateMachineIdentifier(this)
            if (machineIdentifier.isBlank()) {
                throw IllegalStateException("Το Android Keystore επέστρεψε κενό αναγνωριστικό συσκευής")
            }

            recordStartup("Calling local native Rust engine")
            val started = NativeEngine.start(dataDirectory.absolutePath, machineIdentifier)
            if (!started) {
                throw IllegalStateException("NativeEngine.start() επέστρεψε false. Η JNI δεν ξεκίνησε την τοπική μηχανή.")
            }
            recordStartup("Native engine accepted startup")
            updateNotification("Η τοπική μηχανή ScreenerBot ξεκίνησε")
        } catch (error: Throwable) {
            val detail = "${error.javaClass.simpleName}: ${error.message ?: "χωρίς λεπτομέρειες"}"
            recordStartup("FAILED", detail)
            updateNotification("Αποτυχία τοπικής μηχανής: $detail")
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
