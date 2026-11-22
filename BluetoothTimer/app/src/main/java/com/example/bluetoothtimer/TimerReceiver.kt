package com.example.bluetoothtimer

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        context.getSharedPreferences("timer", Context.MODE_PRIVATE)
            .edit().remove("endAt").apply()

        NotificationHelper.createChannel(context)

        val outcome = turnBluetoothOff(context)
        val title: String
        val message: String
        when (outcome) {
            TurnOffResult.TURNING_OFF -> {
                title = "Bluetooth is turning OFF"
                message = "Bluetooth shutdown was requested successfully."
            }
            TurnOffResult.ALREADY_OFF -> {
                title = "Bluetooth is already OFF"
                message = "The timer finished. Bluetooth was already disabled."
            }
            TurnOffResult.NO_ADAPTER -> {
                title = "Bluetooth unavailable"
                message = "This device does not expose a Bluetooth adapter."
            }
            TurnOffResult.PERMISSION_MISSING -> {
                title = "Bluetooth permission required"
                message = "Open Bluetooth Timer and grant Nearby devices permission."
            }
            else -> {
                title = "Could not turn Bluetooth OFF"
                message = "Android did not accept the Bluetooth shutdown request."
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val launchPending = PendingIntent.getActivity(
            context, 3001, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(message)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(launchPending)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(3001, notification)
    }

    private fun turnBluetoothOff(context: Context): TurnOffResult {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return TurnOffResult.NO_ADAPTER

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return TurnOffResult.PERMISSION_MISSING
        }

        return try {
            if (!adapter.isEnabled) {
                TurnOffResult.ALREADY_OFF
            } else if (adapter.disable()) {
                TurnOffResult.TURNING_OFF
            } else {
                TurnOffResult.FAILED
            }
        } catch (_: SecurityException) {
            TurnOffResult.PERMISSION_MISSING
        }
    }

    private enum class TurnOffResult {
        TURNING_OFF, ALREADY_OFF, NO_ADAPTER, PERMISSION_MISSING, FAILED
    }
}
