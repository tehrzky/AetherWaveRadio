package com.aetherwave.radio.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Wake-up alarm received! Action: ${intent.action}")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AetherWaveRadio:AlarmWakeLock"
        )
        wakeLock?.acquire(30000L) // Hold wake lock for 30s to allow network connection and audio buffer

        val stationId = intent.getStringExtra("station_id")

        // 1. Directly start RadioPlaybackService as foreground service to start music immediately
        val serviceIntent = Intent(context, RadioPlaybackService::class.java).apply {
            action = RadioPlaybackService.ACTION_PLAY_ALARM
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
    }
}
