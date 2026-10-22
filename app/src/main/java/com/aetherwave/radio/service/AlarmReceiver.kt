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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
        wakeLock?.acquire(60000L)

        // --- BOOT COMPLETED: reschedule everything ---
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("aetherwave_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("saved_alarms_json", null) ?: return
            val alarms: List<SavedAlarmData> = try {
                Gson().fromJson(json, object : TypeToken<List<SavedAlarmData>>() {}.type) ?: emptyList()
            } catch (_: Exception) { emptyList() }
            alarms.forEachIndexed { index, alarm ->
                if (alarm.enabled) rescheduleAlarm(context, alarm, 1100 + index)
            }
            return
        }

        // --- NORMAL ALARM TRIGGER ---
        val alarmId = intent.getStringExtra("alarm_id")
        val stationIdFromIntent = intent.getStringExtra("station_id")

        val prefs = context.getSharedPreferences("aetherwave_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("saved_alarms_json", null)
        val alarms: List<SavedAlarmData> = try {
            if (!json.isNullOrEmpty())
                Gson().fromJson(json, object : TypeToken<List<SavedAlarmData>>() {}.type) ?: emptyList()
            else emptyList()
        } catch (_: Exception) { emptyList() }

        // Find the alarm by id. If not found (e.g. deleted), skip.
        val targetAlarm = if (alarmId != null) alarms.firstOrNull { it.id == alarmId } else null
        if (alarmId != null && targetAlarm == null) {
            Log.d("AlarmReceiver", "Alarm $alarmId not found — ignoring")
            return
        }
        val stationId = targetAlarm?.stationId ?: stationIdFromIntent

        // Start the radio service
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
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to start RadioPlaybackService: ${e.message}", e)
        }

        // Launch the app full-screen
        try {
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                this.action = "com.aetherwave.radio.ACTION_ALARM_FIRE"
                putExtra("station_id", stationId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            Log.w("AlarmReceiver", "Activity launch deferred: ${e.message}")
        }

        // Reschedule this specific alarm for tomorrow (one-shot setAlarmClock
        // needs re-arming for repeat).
        targetAlarm?.let {
            val idx = alarms.indexOf(it)
            if (idx >= 0) rescheduleAlarm(context, it, 1100 + idx)
        }
    }

    private fun rescheduleAlarm(context: Context, alarm: SavedAlarmData, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.aetherwave.radio.ACTION_ALARM_TRIGGER"
            putExtra("alarm_id", alarm.id)
            putExtra("station_id", alarm.stationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour24)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(calendar.timeInMillis, null),
                pendingIntent
            )
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            else
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }

    // Mirror data class so we can deserialize SavedAlarm here without
    // importing the ViewModel package (avoids a circular dependency).
    private data class SavedAlarmData(
        val id: String,
        val hour24: Int,
        val minute: Int,
        val stationId: String? = null,
        val enabled: Boolean = true
    )
}
