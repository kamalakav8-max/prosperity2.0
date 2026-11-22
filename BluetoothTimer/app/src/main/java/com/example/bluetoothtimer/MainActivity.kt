package com.example.bluetoothtimer

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("timer", Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var countdown: TextView
    private lateinit var status: TextView
    private lateinit var customMinutes: EditText
    private var endAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            refreshCountdown()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)
        buildUi()
        endAt = prefs.getLong("endAt", 0L)
        refreshCountdown()
        handler.post(ticker)
        requestNotificationPermissionIfNeeded()
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 28, 24, 28)
            setBackgroundColor(0xFFF4F6FA.toInt())
        }

        val title = TextView(this).apply {
            text = "Bluetooth Timer"
            textSize = 28f
            setTextColor(0xFF17233B.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        page.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "Bluetooth will switch OFF when your countdown ends"
            textSize = 15f
            setTextColor(0xFF596579.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 24)
        }
        page.addView(subtitle, matchWrap())

        countdown = TextView(this).apply {
            text = "00:00:00"
            textSize = 42f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF1557B0.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 8)
        }
        page.addView(countdown, matchWrap())

        status = TextView(this).apply {
            text = "No timer running"
            textSize = 14f
            setTextColor(0xFF596579.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        page.addView(status, matchWrap())

        val presets = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf(5, 10, 15, 30, 60).forEach { mins ->
            val b = Button(this).apply {
                text = "$mins"
                textSize = 13f
                setOnClickListener { startTimer(mins) }
            }
            presets.addView(b, LinearLayout.LayoutParams(0, 52, 1f).apply {
                setMargins(2, 0, 2, 8)
            })
        }
        page.addView(presets, matchWrap())

        customMinutes = EditText(this).apply {
            hint = "Custom minutes (1–1440)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textSize = 16f
            setSingleLine(true)
        }
        page.addView(customMinutes, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 56
        ).apply { setMargins(0, 8, 0, 8) })

        val start = Button(this).apply {
            text = "START CUSTOM TIMER"
            setOnClickListener {
                val mins = customMinutes.text.toString().toLongOrNull()
                if (mins == null || mins !in 1..1440) {
                    Toast.makeText(this@MainActivity, "Enter 1 to 1440 minutes", Toast.LENGTH_SHORT).show()
                } else startTimer(mins.toInt())
            }
        }
        page.addView(start, matchWrap())

        val cancel = Button(this).apply {
            text = "CANCEL TIMER"
            setOnClickListener { cancelTimer() }
        }
        page.addView(cancel, matchWrap())

        val note = TextView(this).apply {
            text = "Grant Nearby devices permission when asked. At expiry, the app requests Bluetooth OFF directly."
            textSize = 13f
            setTextColor(0xFF596579.toInt())
            gravity = Gravity.CENTER
            setPadding(4, 22, 4, 0)
        }
        page.addView(note, matchWrap())
        setContentView(page)
    }

    private fun startTimer(minutes: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 201)
            Toast.makeText(this, "Allow Nearby devices, then tap Start again.", Toast.LENGTH_LONG).show()
            return
        }

        val alarm = getSystemService(ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
            Toast.makeText(this, "Allow Alarms & reminders, then tap Start again.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            return
        }

        endAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes.toLong())
        prefs.edit().putLong("endAt", endAt).apply()
        alarm.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, endAt, alarmPendingIntent()
        )
        refreshCountdown()
        Toast.makeText(this, "Bluetooth will turn OFF in $minutes minutes", Toast.LENGTH_SHORT).show()
    }

    private fun cancelTimer() {
        val alarm = getSystemService(ALARM_SERVICE) as AlarmManager
        alarm.cancel(alarmPendingIntent())
        prefs.edit().remove("endAt").apply()
        endAt = 0L
        refreshCountdown()
        Toast.makeText(this, "Timer cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun alarmPendingIntent(): PendingIntent {
        val intent = Intent(this, TimerReceiver::class.java)
        return PendingIntent.getBroadcast(
            this, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun refreshCountdown() {
        if (!::countdown.isInitialized) return
        val remaining = endAt - System.currentTimeMillis()
        if (endAt > 0L && remaining > 0L) {
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(remaining)
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            countdown.text = String.format("%02d:%02d:%02d", h, m, s)
            status.text = "Timer running"
        } else {
            countdown.text = "00:00:00"
            status.text = if (endAt > 0L) "Timer finished" else "No timer running"
            if (endAt > 0L) {
                endAt = 0L
                prefs.edit().remove("endAt").apply()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
