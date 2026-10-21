package com.aetherwave.radio.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RadioPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private var androidEqualizer: Equalizer? = null
    private var androidBassBoost: BassBoost? = null
    private var androidVirtualizer: Virtualizer? = null
    private var androidVisualizer: Visualizer? = null
    private var currentAudioSessionId: Int = 0

    companion object {
        const val CHANNEL_ID = "aetherwave_radio_channel"
        const val ALARM_CHANNEL_ID = "aetherwave_alarm_channel"
        const val ACTION_PLAY_ALARM = "com.aetherwave.radio.ACTION_PLAY_ALARM"
        const val ACTION_SET_EQ = "com.aetherwave.radio.ACTION_SET_EQ"
        const val FOREGROUND_ID = 1001

        // ---- REAL FFT STATE EXPOSED TO THE UI ----
        private val _fftState = MutableStateFlow(FloatArray(64))
        val fftState: StateFlow<FloatArray> = _fftState.asStateFlow()

        // Whether real FFT is currently available (false = UI should fall back to fake animation)
        private val _hasRealFft = MutableStateFlow(false)
        val hasRealFft: StateFlow<Boolean> = _hasRealFft.asStateFlow()

        private const val FFT_BINS = 64

        fun buildMediaItem(station: Station): MediaItem {
            val metadata = MediaMetadata.Builder()
                .setTitle(station.name)
                .setArtist(station.group)
                .setAlbumTitle("AetherWave Studio Radio")
                .setDisplayTitle(station.name)
                .build()
            return MediaItem.Builder()
                .setMediaId(station.id)
                .setUri(station.streamUrl.trim())
                .setMediaMetadata(metadata)
                .build()
        }

        // Called by the ViewModel if the UI needs to force a fallback (e.g. real FFT silent for >3s)
        fun enableFallbackFft() {
            _hasRealFft.value = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initExoPlayer()
    }

    private fun initExoPlayer() {
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
                        currentAudioSessionId = audioSessionId
                        initAudioEffects(audioSessionId)
                    }
                })
            }

        mediaSession = MediaSession.Builder(this, player!!)
            .build()
    }

    // ---- AUDIO FX ----

    private fun initAudioEffects(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return

        releaseAudioFx()

        try {
            androidEqualizer = Equalizer(0, audioSessionId).apply { enabled = true }
            androidBassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
            androidVirtualizer = Virtualizer(0, audioSessionId).apply { enabled = true }
        } catch (e: Exception) {
            Log.e("RadioPlaybackService", "AudioFX init failed: ${e.message}")
        }

        // Apply saved state
        val prefs = getSharedPreferences("aetherwave_prefs", Context.MODE_PRIVATE)
        val bandsJson = prefs.getString("eq_bands_json", null)
        val bands: FloatArray = try {
            if (!bandsJson.isNullOrEmpty())
                Gson().fromJson(bandsJson, FloatArray::class.java) ?: FloatArray(5)
            else
                FloatArray(5)
        } catch (_: Exception) { FloatArray(5) }

        applyEqBands(bands)
        applyBassBoost(prefs.getFloat("bass_boost", 0f))
        applyVirtualizer(prefs.getFloat("virtualizer", 0f))

        initVisualizer(audioSessionId)
    }

    private fun applyEqBands(bands: FloatArray) {
        val eq = androidEqualizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange?.get(0) ?: -1200
            val maxLevel = eq.bandLevelRange?.get(1) ?: 1200
            for (i in 0 until minOf(bands.size, numBands)) {
                val millibels = (bands[i] * 100).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                eq.setBandLevel(i.toShort(), millibels)
            }
        } catch (e: Exception) {
            Log.e("RadioPlaybackService", "applyEqBands error: ${e.message}")
        }
    }

    private fun applyBassBoost(strength: Float) {
        try {
            androidBassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength((strength * 1000).toInt().coerceIn(0, 1000).toShort())
                    bb.enabled = strength > 0.01f
                }
            }
        } catch (e: Exception) { Log.e("RadioPlaybackService", "BassBoost error: ${e.message}") }
    }

    private fun applyVirtualizer(strength: Float) {
        try {
            androidVirtualizer?.let { v ->
                if (v.strengthSupported) {
                    v.setStrength((strength * 1000).toInt().coerceIn(0, 1000).toShort())
                    v.enabled = strength > 0.01f
                }
            }
        } catch (e: Exception) { Log.e("RadioPlaybackService", "Virtualizer error: ${e.message}") }
    }

    // ---- REAL FFT VISUALIZER ----

    private fun initVisualizer(audioSessionId: Int) {
        try {
            androidVisualizer?.release()
            androidVisualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1] // largest allowed
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, rate: Int) {}
                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                            fft ?: return
                            // fft is [Re, Im, Re, Im, ...]; we reduce to FFT_BINS magnitude bins
                            val bins = IntArray(FFT_BINS) { 0 }
                            val step = (fft.size / 2) / FFT_BINS
                            if (step < 1) return
                            var max = 0
                            for (i in 0 until FFT_BINS) {
                                val re = fft[(i * step) * 2].toInt()
                                val im = fft[(i * step) * 2 + 1].toInt()
                                val mag = kotlin.math.sqrt((re * re + im * im).toFloat()).toInt()
                                bins[i] = mag
                                if (mag > max) max = mag
                            }
                            // Normalize 0..1
                            val out = FloatArray(FFT_BINS) { i ->
                                if (max == 0) 0f else (bins[i].toFloat() / max).coerceIn(0f, 1f)
                            }
                            _fftState.value = out
                            _hasRealFft.value = out.any { it > 0.02f }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false, // don't care about waveform
                    true
                )
                enabled = true
            }
        } catch (e: Exception) {
            Log.e("RadioPlaybackService", "Visualizer init failed: ${e.message}")
            _hasRealFft.value = false
        }
    }

    // ---- LIFECYCLE / INTENTS ----

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SET_EQ) {
            intent.getFloatArrayExtra("eq_bands")?.let { applyEqBands(it) }
            if (intent.hasExtra("bass_boost")) applyBassBoost(intent.getFloatExtra("bass_boost", 0f))
            if (intent.hasExtra("virtualizer")) applyVirtualizer(intent.getFloatExtra("virtualizer", 0f))
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_PLAY_ALARM) {
            val stationId = intent.getStringExtra("station_id")
            val prefs = getSharedPreferences("aetherwave_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("saved_stations_json", null)
            val stations: List<Station> = try {
                if (!json.isNullOrEmpty())
                    Gson().fromJson(json, object : TypeToken<List<Station>>() {}.type)
                else DefaultStations.STATIONS
            } catch (_: Exception) { DefaultStations.STATIONS }

            val target = stations.firstOrNull { it.id == stationId } ?: stations.firstOrNull()

            val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
                .setContentTitle("AetherWave Alarm: ${target?.name ?: "Wake Up"}")
                .setContentText("Playing wake-up radio")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .build()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(FOREGROUND_ID, notification)
                }
            } catch (e: Exception) {
                Log.e("RadioPlaybackService", "startForeground error: ${e.message}")
            }

            if (target != null) {
                player?.apply {
                    setMediaItem(buildMediaItem(target))
                    prepare()
                    play()
                }
            }
            return START_STICKY
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Radio Playback", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Live radio playback"
                    setShowBadge(false)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(ALARM_CHANNEL_ID, "Radio Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Wake-up radio alarm"
                    enableVibration(true)
                }
            )
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val cur = player
        if (cur == null || !cur.playWhenReady || cur.mediaItemCount == 0) stopSelf()
    }

    private fun releaseAudioFx() {
        try { androidEqualizer?.release() } catch (_: Exception) {}
        try { androidBassBoost?.release() } catch (_: Exception) {}
        try { androidVirtualizer?.release() } catch (_: Exception) {}
        try { androidVisualizer?.release() } catch (_: Exception) {}
        androidEqualizer = null
        androidBassBoost = null
        androidVirtualizer = null
        androidVisualizer = null
        _hasRealFft.value = false
    }

    override fun onDestroy() {
        releaseAudioFx()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
