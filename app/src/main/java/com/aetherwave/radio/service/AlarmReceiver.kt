package com.aetherwave.radio.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.aetherwave.radio.MainActivity

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Wake-up alarm received! Action: ${intent.action}")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AetherWaveRadio:AlarmWakeLock"
        )
        wakeLock?.acquire(15000L) // Hold wake lock for 15s to allow audio to start

        val stationId = intent.getStringExtra("station_id")

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.aetherwave.radio.ACTION_ALARM_FIRE"
            putExtra("station_id", stationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        try {
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to launch MainActivity on alarm: ${e.message}", e)
        }
    }
}
