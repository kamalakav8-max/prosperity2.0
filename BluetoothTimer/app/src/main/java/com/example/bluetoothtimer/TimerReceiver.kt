package com.example.bluetoothtimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.provider.Settings

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        context.getSharedPreferences("timer", Context.MODE_PRIVATE)
            .edit().remove("endAt").apply()

        NotificationHelper.createChannel(context)

        val settingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val settingsPending = PendingIntent.getActivity(
            context, 3001, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Bluetooth timer finished")
            .setContentText("Tap to open Bluetooth settings and switch Bluetooth off.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(settingsPending)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(3001, notification)
    }
}
