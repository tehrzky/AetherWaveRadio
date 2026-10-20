package com.aetherwave.radio.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
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

    companion object {
        const val CHANNEL_ID = "aetherwave_radio_channel"
        const val ACTION_PLAY_ALARM = "com.aetherwave.radio.ACTION_PLAY_ALARM"

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
        createNotificationChannel()

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
                })
            }

        mediaSession = MediaSession.Builder(this, player!!)
            .build()
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AetherWave Radio Playback"
            val descriptionText = "Live radio audio stream playback controls and status"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
