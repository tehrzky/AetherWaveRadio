package com.aetherwave.radio.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.Equalizer
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
                .apply {
                    if (!station.logoUrl.isNullOrEmpty()) {
                        setDisplayTitle(station.name)
                    }
                }
                .build()

            return MediaItem.Builder()
                .setUri(cleanUrl)
                .setMediaId(station.id)
                .setMediaMetadata(metadata)
                .build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("AetherWaveRadio/1.0 (Android; Linux; ExoPlayer)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true) // Handles Audio Focus automatically!
            .setHandleAudioBecomingNoisy(true) // Pauses when Bluetooth headphones disconnect!
            .setWakeMode(C.WAKE_MODE_NETWORK) // Keeps CPU/Wi-Fi active while screen is off
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("RadioPlaybackService", "ExoPlayer error [${error.errorCodeName}]: ${error.message}", error)
                    }

                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        initEqualizer(audioSessionId)
                    }
                })
            }

        mediaSession = MediaSession.Builder(this, player!!)
            .build()
    }

    private fun initEqualizer(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return
        try {
            androidEqualizer?.release()
            androidEqualizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            Log.d("RadioPlaybackService", "Android Equalizer initialized for session $audioSessionId")
        } catch (e: Exception) {
            Log.e("RadioPlaybackService", "Equalizer init error: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
