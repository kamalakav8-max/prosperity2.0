package com.example.growwsweepalert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * Foreground-service shell for future authorized streaming connection.
 * A real provider must be injected before this service can claim to monitor contracts.
 */
class MonitoringService : Service() {
    override fun onCreate() {
        super.onCreate()
        val channelId = "monitoring_status"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Monitoring status", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Groww Sweep Alert")
                .setContentText("Monitoring service shell. Connect an authorized data provider to start.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Groww Sweep Alert")
                .setContentText("Monitoring service shell. Data provider not connected.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
        startForeground(1001, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null
}