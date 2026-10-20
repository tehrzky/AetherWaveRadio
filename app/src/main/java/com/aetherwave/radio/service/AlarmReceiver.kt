package com.aetherwave.radio.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.aetherwave.radio.MainActivity
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("AlarmReceiver", "AlarmReceiver invoked with action: $action")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AetherWaveRadio:AlarmWakeLock"
        )
        wakeLock?.acquire(60000L) // Hold wake lock for 60s to allow network connection and audio buffer

        val prefs = context.getSharedPreferences("aetherwave_prefs", Context.MODE_PRIVATE)

        if (action == Intent.ACTION_BOOT_COMPLETED) {
            val enabled = prefs.getBoolean("alarm_enabled", false)
            if (enabled) {
                val hour = prefs.getInt("alarm_hour", 7)
                val minute = prefs.getInt("alarm_minute", 30)
                val stationId = prefs.getString("alarm_station_id", null)
                rescheduleAlarm(context, hour, minute, stationId)
            }
            return
        }

        val stationId = intent.getStringExtra("station_id") ?: prefs.getString("alarm_station_id", null)

        // 1. Start RadioPlaybackService as foreground service to start live radio music immediately
        val serviceIntent = Intent(context, RadioPlaybackService::class.java).apply {
            this.action = RadioPlaybackService.ACTION_PLAY_ALARM
            putExtra("station_id", stationId)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d("AlarmReceiver", "Successfully launched RadioPlaybackService for alarm")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to start RadioPlaybackService: ${e.message}", e)
        }

        // 2. Launch MainActivity to foreground so user sees wake-up alarm interface
        try {
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                this.action = "com.aetherwave.radio.ACTION_ALARM_FIRE"
                putExtra("station_id", stationId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            Log.w("AlarmReceiver", "Activity launch deferred: ${e.message}")
        }
    }

    private fun rescheduleAlarm(context: Context, hour: Int, minute: Int, stationId: String?) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.aetherwave.radio.ACTION_ALARM_TRIGGER"
            putExtra("station_id", stationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            val showIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val clockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, showIntent)
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
            Log.d("AlarmReceiver", "Rescheduled alarm after boot for: ${calendar.time}")
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            } catch (ex: Exception) {
                Log.e("AlarmReceiver", "Failed to reschedule alarm after boot", ex)
            }
        }
    }
}
