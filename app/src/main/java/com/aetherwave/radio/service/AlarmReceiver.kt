package com.aetherwave.radio.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.aetherwave.radio.ACTION_ALARM_TRIGGER" || 
            intent.action == "android.intent.action.BOOT_COMPLETED") {
            // In a full app, this would start the RadioPlaybackService
            // For now, this satisfies the compiler and manifest requirements.
        }
    }
}
