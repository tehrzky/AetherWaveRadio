package com.aetherwave.radio.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aetherwave.radio.data.DefaultStations
import com.aetherwave.radio.data.Station
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RadioPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var androidEqualizer: Equalizer? = null
    private var androidBassBoost: BassBoost? = null
    private var androidVirtualizer: Virtualizer? = null

    companion object {
        const val CHANNEL_ID = "aetherwave_radio_channel"
        const val ALARM_CHANNEL_ID = "aetherwave_alarm_channel"
        const val ACTION_PLAY_ALARM = "com.aetherwave.radio.ACTION_PLAY_ALARM"
        const val ACTION_SET_EQ = "com.aetherwave.radio.ACTION_SET_EQ"

        fun buildMediaItem(station: Station): MediaItem {
            val cleanUrl = station.streamUrl.trim()
            val metadata = MediaMetadata.Builder()
                .setTitle(station.name)
                .setArtist(station.group)
                .setAlbumTitle("AetherWave Studio Radio")
                .setDisplayTitle(station.name)
                .build()

            return MediaItem.Builder()
                .setMediaId(station.id)
                .setUri(cleanUrl)
                .setMediaMetadata(metadata)
                .build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setUserAgent("AetherWaveRadio/1.0 (Linux; Android)")

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("RadioPlaybackService", "ExoPlayer error [${error.errorCodeName}]: ${error.message}", error)
                    }

                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        initAudioEffects(audioSessionId)
                    }
                })
            }

        mediaSession = MediaSession.Builder(this, player!!)
            .build()
    }

    private fun initAudioEffects(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return
        try {
            androidEqualizer?.release()
            androidEqualizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }

            androidBassBoost?.release()
            androidBassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
            }

            androidVirtualizer?.release()
            androidVirtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
            }

            // Load saved settings from preferences and apply
            val prefs = getSharedPreferences("aetherwave_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("eq_bands_json", null)
            val bands: FloatArray = if (!json.isNullOrEmpty()) {
                try {
                    Gson().fromJson(json, FloatArray::class.java)
                } catch (_: Exception) {
                    floatArrayOf(0f, 0f, 0f, 0f, 0f)
                }
            } else {
                floatArrayOf(0f, 0f, 0f, 0f, 0f)
            }
            applyEqBands(bands)
            applyBassBoost(prefs.getFloat("bass_boost", 0f))
            applyVirtualizer(prefs.getFloat("virtualizer", 0f))
            Log.d("RadioPlaybackService", "Android Equalizer & AudioFX initialized for session $audioSessionId")
        } catch (e: Exception) {
            Log.e("RadioPlaybackService", "Equalizer init error: ${e.message}")
        }
    }

    private fun applyEqBands(bands: FloatArray) {
        val eq = androidEqualizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val minBandLevel = eq.bandLevelRange?.get(0) ?: -1200
            val maxBandLevel = eq.bandLevelRange?.get(1) ?: 1200
            for (i in 0 until minOf(bands.size, numBands)) {
                // gain is in dB (-12 to 12). 1 dB = 100 mB
                val millibels = (bands[i] * 100).toInt().coerceIn(minBandLevel.toInt(), maxBandLevel.toInt()).toShort()
                eq.setBandLevel(i.toShort(), millibels)
            }
        } catch (e: Exception) {
            Log.e("RadioPlaybackService", "Error applying EQ bands: ${e.message}")
        }
    }

    private fun applyBassBoost(strength: Float) {
        try {
            androidBassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    val s = (strength * 1000).toInt().coerceIn(0, 1000).toShort()
                    bb.setStrength(s)
                    bb.enabled = strength > 0.01f
                }
            }
        } catch (e: Exception) {
            Log.e("RadioPlaybackService", "Error setting bass boost: ${e.message}")
        }
    }

    private fun applyVirtualizer(strength: Float) {
        try {
            androidVirtualizer?.let { virt ->
                if (virt.strengthSupported) {
                    val s = (strength * 1000).toInt().coerceIn(0, 1000).toShort()
                    virt.setStrength(s)
                    virt.enabled = strength > 0.01f
                }
            }
        } catch (e: Exception) {
            Log.e("RadioPlaybackService", "Error setting virtualizer: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SET_EQ) {
            val bands = intent.getFloatArrayExtra("eq_bands")
            if (bands != null) {
                applyEqBands(bands)
            }
            if (intent.hasExtra("bass_boost")) {
                applyBassBoost(intent.getFloatExtra("bass_boost", 0f))
            }
            if (intent.hasExtra("virtualizer")) {
                applyVirtualizer(intent.getFloatExtra("virtualizer", 0f))
            }
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_PLAY_ALARM) {
            val stationId = intent.getStringExtra("station_id")
            val prefs = getSharedPreferences("aetherwave_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("saved_stations_json", null)
            val stations: List<Station> = try {
                if (!json.isNullOrEmpty()) {
                    Gson().fromJson(json, object : TypeToken<List<Station>>() {}.type)
                } else {
                    DefaultStations.STATIONS
                }
            } catch (e: Exception) {
                DefaultStations.STATIONS
            }
            val target = stations.firstOrNull { it.id == stationId } ?: stations.firstOrNull()

            // Post ongoing Alarm Notification immediately to satisfy foreground service contract
            val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
                .setContentTitle("AetherWave Alarm: ${target?.name ?: "Wake Up Radio"}")
                .setContentText("Playing live wake-up radio broadcast")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .build()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(1001, notification)
                }
            } catch (e: Exception) {
                Log.e("RadioPlaybackService", "startForeground error: ${e.message}")
            }

            if (target != null && player != null) {
                try {
                    val mediaItem = buildMediaItem(target)
                    player?.setMediaItem(mediaItem)
                    player?.prepare()
                    player?.play()
                    Log.d("RadioPlaybackService", "Started alarm playback for station: ${target.name}")
                } catch (e: Exception) {
                    Log.e("RadioPlaybackService", "Error starting alarm stream: ${e.message}", e)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val playChannel = NotificationChannel(
                CHANNEL_ID,
                "AetherWave Radio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live radio audio stream playback controls and status"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(playChannel)

            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "AetherWave Radio Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Wake-up radio alarm sound and status"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            androidEqualizer?.release()
        } catch (_: Exception) {}
        androidEqualizer = null

        try {
            androidBassBoost?.release()
        } catch (_: Exception) {}
        androidBassBoost = null

        try {
            androidVirtualizer?.release()
        } catch (_: Exception) {}
        androidVirtualizer = null

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
