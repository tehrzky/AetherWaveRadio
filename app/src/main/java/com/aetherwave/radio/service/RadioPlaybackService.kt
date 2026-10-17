package com.aetherwave.radio.service

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aetherwave.radio.data.Station

class RadioPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // Handles Audio Focus automatically!
            .setHandleAudioBecomingNoisy(true) // Pauses when Bluetooth headphones disconnect!
            .setWakeMode(C.WAKE_MODE_NETWORK) // Keeps CPU/Wi-Fi active while screen is off
            .build()

        mediaSession = MediaSession.Builder(this, player!!)
            .build()
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

    companion object {
        fun buildMediaItem(station: Station): MediaItem {
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
                .setUri(station.streamUrl)
                .setMediaId(station.id)
                .setMediaMetadata(metadata)
                .build()
        }
    }
}
