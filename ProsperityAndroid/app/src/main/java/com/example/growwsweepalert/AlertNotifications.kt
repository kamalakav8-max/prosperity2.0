package com.example.growwsweepalert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AlertNotifications(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "sweep_alerts"
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Spot and option sweep alerts",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Previous-day/week high/low sweep notifications"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun show(alert: SweepAlert) {
        val kind = when (alert.detectionKind) {
            DetectionKind.LIVE_PRICE_CROSS -> "LIVE PRICE CROSS"
            DetectionKind.CANDLE_WICK_SWEEP -> "CANDLE WICK SWEEP"
        }
        val direction = if (alert.direction == SweepDirection.UP) "crossed above" else "crossed below"
        val group = if (alert.instrumentType == InstrumentType.SPOT_INDEX) "SPOT INDEX" else "OPTION"
        val title = "$group: ${alert.symbol} — ${alert.levelName}"
        val body = "$kind: price $direction ${alert.level}; observed ${alert.observedPrice}"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(alert.hashCode() and 0x7fffffff, notification)
        } catch (_: SecurityException) { }
    }
}