package com.aetherwave.radio.ui

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aetherwave.radio.data.DefaultStations
import com.aetherwave.radio.data.SortOption
import com.aetherwave.radio.data.Station
import com.aetherwave.radio.data.ViewMode
import com.aetherwave.radio.service.AlarmReceiver
import com.aetherwave.radio.service.RadioPlaybackService
import com.aetherwave.radio.util.BluetoothHelper
import com.aetherwave.radio.util.SleepTimerManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

enum class SettingsSubPage {
    PLAYER,
    APPEARANCE,
    EQUALIZER,
    SPECTRUM_VISUALIZER,
    GENRE_MANAGER,
    CLOUD_BACKUP,
    ALARM_CLOCK,
    BLUETOOTH,
    ABOUT
}

data class ScrapedRadioStation(
    val name: String,
    val url: String,
    val favicon: String?,
    val tags: String?,
    val country: String?,
    val codec: String?,
    val bitrate: Int?
)

data class RadioUiState(
    val currentStation: Station? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isDeckOpen: Boolean = false,
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOption: SortOption = SortOption.CUSTOM,
    val searchQuery: String = "",
    val selectedGroup: String = "All",
    val themeColorHex: String = "#F59E0B",
    val backgroundColorHex: String = "#090D16",
    val cardColorHex: String = "#111726",
    val themeMode: String = "dark", // "dark", "light", "system"
    val isMonetEnabled: Boolean = false,
    val isMuted: Boolean = false,
    val eqPreset: String = "Flat",
    val eqBands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val bassBoost: Float = 0.5f,
    val virtualizer: Float = 0.3f,
    // Spectrum visualizer settings
    val spectrumStyle: String = "Bars", // "Bars", "Mirrored", "NeonLine", "Columns"
    val spectrumHeight: Int = 85, // in dp (40 to 140)
    val spectrumSensitivity: Float = 1.2f, // 0.5 to 2.5
    val spectrumColorScheme: String = "Accent", // "Accent", "Rainbow", "CyanAmber", "NeonEmerald", "PurpleGlow"
    val spectrumSpeed: String = "Normal", // "Smooth", "Normal", "Energetic"
    val syncRoomCode: String = "RADIO-A89K",
    val isAutoSync: Boolean = false,
    val isArrangeMode: Boolean = false,
    val isSettingsPageOpen: Boolean = false,
    val currentSettingsSubPage: SettingsSubPage? = null,
    val isGenreSelectMode: Boolean = false,
    val isGenreArrangeMode: Boolean = false,
    val selectedGenresForDeletion: Set<String> = emptySet(),
    val alarmEnabled: Boolean = false,
    val alarmHour: Int = 7,
    val alarmMinute: Int = 30,
    val alarmStationId: String? = null,
    val bufferSize: String = "Medium",
    val stopOnHeadsetDisconnect: Boolean = true,
    val autoResumeOnBluetooth: Boolean = false,
    val highQualityAudio: Boolean = true,
    val openedSettingsFromDeck: Boolean = false
)

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("aetherwave_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    val bluetoothHelper = BluetoothHelper(application)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _stations = MutableStateFlow<List<Station>>(loadSavedStations())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    private val _genres = MutableStateFlow<List<String>>(loadSavedGenres())
    val genres: StateFlow<List<String>> = _genres.asStateFlow()

    private val savedStationId = prefs.getString("last_played_station_id", null)
    private val initialStation = _stations.value.firstOrNull { it.id == savedStationId }
    private var pendingPlayStation: Station? = null

    // Scraped Radio Browser autocomplete suggestions
    private val _searchSuggestions = MutableStateFlow<List<ScrapedRadioStation>>(emptyList())
    val searchSuggestions: StateFlow<List<ScrapedRadioStation>> = _searchSuggestions.asStateFlow()

    private val _isScraping = MutableStateFlow(false)
    val isScraping: StateFlow<Boolean> = _isScraping.asStateFlow()

    private var scrapeJob: Job? = null

    private val _uiState = MutableStateFlow(
        RadioUiState(
            currentStation = initialStation,
            themeColorHex = prefs.getString("theme_color", "#F59E0B") ?: "#F59E0B",
            backgroundColorHex = prefs.getString("bg_color", "#090D16") ?: "#090D16",
            cardColorHex = prefs.getString("card_color", "#111726") ?: "#111726",
            themeMode = prefs.getString("theme_mode", "dark") ?: "dark",
            isMonetEnabled = prefs.getBoolean("monet_enabled", false),
            selectedGroup = prefs.getString("selected_genre_group", "All") ?: "All",
            viewMode = ViewMode.valueOf(prefs.getString("view_mode", ViewMode.GRID.name) ?: ViewMode.GRID.name),
            sortOption = SortOption.valueOf(prefs.getString("sort_option", SortOption.CUSTOM.name) ?: SortOption.CUSTOM.name),
            spectrumStyle = prefs.getString("spec_style", "Bars") ?: "Bars",
            spectrumHeight = prefs.getInt("spec_height", 85),
            spectrumSensitivity = prefs.getFloat("spec_sens", 1.2f),
            spectrumColorScheme = prefs.getString("spec_color", "Accent") ?: "Accent",
            spectrumSpeed = prefs.getString("spec_speed", "Normal") ?: "Normal",
            syncRoomCode = prefs.getString("sync_room", "RADIO-A89K") ?: "RADIO-A89K",
            alarmEnabled = prefs.getBoolean("alarm_enabled", false),
            alarmHour = prefs.getInt("alarm_hour", 7),
            alarmMinute = prefs.getInt("alarm_minute", 30),
            alarmStationId = prefs.getString("alarm_station_id", null),
            bufferSize = prefs.getString("buffer_size", "Medium") ?: "Medium",
            stopOnHeadsetDisconnect = prefs.getBoolean("stop_on_disconnect", true),
            autoResumeOnBluetooth = prefs.getBoolean("auto_resume_bt", false),
            highQualityAudio = prefs.getBoolean("high_quality", true)
        )
    )
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    private var originalVolumeBeforeFade = 0.85f

    val sleepTimerManager = SleepTimerManager(
        onTickVolumeFade = { ratio ->
            val target = originalVolumeBeforeFade * ratio
            mediaController?.volume = target
        },
        onTimerFinished = {
            pausePlayback()
            mediaController?.volume = originalVolumeBeforeFade
        }
    )

    init {
        initMediaController(application)
    }

    fun syncPlayerState() {
        mediaController?.let { player ->
            val isPlaying = player.isPlaying
            val currentMediaId = player.currentMediaItem?.mediaId
            val matched = _stations.value.firstOrNull { it.id == currentMediaId }
                ?: _uiState.value.currentStation
                ?: _stations.value.firstOrNull { it.id == prefs.getString("last_played_station_id", null) }

            if (matched != null) {
                _uiState.value = _uiState.value.copy(
                    currentStation = matched,
                    isPlaying = isPlaying,
                    isLoading = player.playbackState == Player.STATE_BUFFERING
                )
            }
        }
    }

    private fun initMediaController(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, RadioPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _uiState.value = _uiState.value.copy(
                            isPlaying = isPlaying,
                            isLoading = false
                        )
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = playbackState == Player.STATE_BUFFERING
                        )
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("RadioViewModel", "ExoPlayer playback error: ${error.errorCodeName}", error)
                        _uiState.value = _uiState.value.copy(
                            isPlaying = false,
                            isLoading = false
                        )
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val mediaId = mediaItem?.mediaId
                        if (!mediaId.isNullOrEmpty()) {
                            val st = _stations.value.firstOrNull { it.id == mediaId }
                            if (st != null) {
                                _uiState.value = _uiState.value.copy(currentStation = st)
                                prefs.edit().putString("last_played_station_id", st.id).apply()
                            }
                        }
                    }
                })

                syncPlayerState()
                pendingPlayStation?.let { pending ->
                    playStation(pending)
                    pendingPlayStation = null
                }
            } catch (e: Exception) {
                Log.e("RadioViewModel", "Error connecting MediaController: ${e.message}", e)
            }
        }, MoreExecutors.directExecutor())
    }

    // Dynamic genre categories combining defaults and user custom genres
    val allFilterGroups: StateFlow<List<String>> = combine(
        _stations,
        _genres
    ) { stationList, genreList ->
        val result = linkedSetOf<String>()
        result.add("All")
        result.add("Favorites")
        result.addAll(genreList)
        stationList.forEach { s ->
            if (s.group.isNotBlank() && s.group != "All" && s.group != "Favorites") {
                result.add(s.group)
            }
        }
        result.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All", "Favorites") + DefaultStations.GROUPS.filter { it != "All" && it != "Favorites" })

    val filteredAndSortedStations: StateFlow<List<Station>> = combine(
        _stations,
        _uiState
    ) { stationsList, state ->
        val query = state.searchQuery.trim().lowercase()
        val group = state.selectedGroup

        var list = stationsList.filter { st ->
            val matchQuery = query.isEmpty() ||
                    st.name.lowercase().contains(query) ||
                    st.group.lowercase().contains(query) ||
                    (st.country?.lowercase()?.contains(query) ?: false)

            val matchGroup = when {
                group.equals("All", ignoreCase = true) -> true
                group.equals("Favorites", ignoreCase = true) -> st.isFavorite
                else -> st.group.equals(group, ignoreCase = true)
            }

            matchQuery && matchGroup
        }

        list = when (state.sortOption) {
            SortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            SortOption.FAVORITES_FIRST -> list.sortedByDescending { it.isFavorite }
            SortOption.RECENTLY_PLAYED -> list
            SortOption.CUSTOM -> list
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playStation(station: Station) {
        if (station.streamUrl.isBlank()) return
        _uiState.value = _uiState.value.copy(
            currentStation = station,
            isLoading = true
        )
        prefs.edit().putString("last_played_station_id", station.id).apply()

        try {
            val controller = mediaController
            if (controller != null) {
                val mediaItem = RadioPlaybackService.buildMediaItem(station)
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            } else {
                pendingPlayStation = station
            }
        } catch (e: Exception) {
            Log.e("RadioViewModel", "Error starting playback for ${station.name}: ${e.message}", e)
            _uiState.value = _uiState.value.copy(isLoading = false, isPlaying = false)
        }
    }

    fun pausePlayback() {
        try {
            mediaController?.pause()
            _uiState.value = _uiState.value.copy(isPlaying = false)
        } catch (e: Exception) {
            Log.e("RadioViewModel", "Error pausing playback: ${e.message}", e)
        }
    }

    fun resumePlayback() {
        try {
            if (mediaController?.currentMediaItem == null && _uiState.value.currentStation != null) {
                playStation(_uiState.value.currentStation!!)
            } else {
                mediaController?.play()
                _uiState.value = _uiState.value.copy(isPlaying = true)
            }
        } catch (e: Exception) {
            Log.e("RadioViewModel", "Error resuming playback: ${e.message}", e)
        }
    }

    fun playNext() {
        val currentList = filteredAndSortedStations.value
        if (currentList.isEmpty()) return
        val index = currentList.indexOfFirst { it.id == _uiState.value.currentStation?.id }
        val nextIndex = if (index == -1 || index >= currentList.size - 1) 0 else index + 1
        playStation(currentList[nextIndex])
    }

    fun playPrevious() {
        val currentList = filteredAndSortedStations.value
        if (currentList.isEmpty()) return
        val index = currentList.indexOfFirst { it.id == _uiState.value.currentStation?.id }
        val prevIndex = if (index <= 0) currentList.size - 1 else index - 1
        playStation(currentList[prevIndex])
    }

    fun toggleMute() {
        val nextMuted = !_uiState.value.isMuted
        if (nextMuted) {
            mediaController?.volume = 0f
            _uiState.value = _uiState.value.copy(isMuted = true)
        } else {
            mediaController?.volume = originalVolumeBeforeFade
            _uiState.value = _uiState.value.copy(isMuted = false)
        }
    }

    private fun pushEqToService() {
        try {
            val context = getApplication<Application>()
            val intent = Intent(context, RadioPlaybackService::class.java).apply {
                action = RadioPlaybackService.ACTION_SET_EQ
                putExtra("eq_bands", _uiState.value.eqBands.toFloatArray())
                putExtra("bass_boost", _uiState.value.bassBoost)
                putExtra("virtualizer", _uiState.value.virtualizer)
            }
            context.startService(intent)
        } catch (e: Exception) {
            Log.e("RadioViewModel", "Error pushing EQ to service: ${e.message}")
        }
    }

    fun setEqPreset(preset: String) {
        val bands = when (preset) {
            "Bass Boost" -> listOf(6f, 4f, 1f, 0f, -1f)
            "Vocal Clarity" -> listOf(-2f, 2f, 5f, 3f, 1f)
            "Club/Dance" -> listOf(5f, 3f, -1f, 3f, 4f)
            "Rock" -> listOf(4f, -1f, -2f, 2f, 5f)
            "Classical" -> listOf(4f, 2f, 0f, 3f, 4f)
            "Jazz" -> listOf(3f, 1f, 3f, 1f, 2f)
            else -> listOf(0f, 0f, 0f, 0f, 0f)
        }
        _uiState.value = _uiState.value.copy(eqPreset = preset, eqBands = bands)
        prefs.edit().putString("eq_bands_json", gson.toJson(bands.toFloatArray())).putString("eq_preset", preset).apply()
        pushEqToService()
    }

    fun setEqBand(bandIndex: Int, value: Float) {
        if (bandIndex in 0 until 5) {
            val updated = _uiState.value.eqBands.toMutableList()
            updated[bandIndex] = value.coerceIn(-12f, 12f)
            _uiState.value = _uiState.value.copy(eqBands = updated, eqPreset = "Custom")
            prefs.edit().putString("eq_bands_json", gson.toJson(updated.toFloatArray())).putString("eq_preset", "Custom").apply()
            pushEqToService()
        }
    }

    fun setBassBoost(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(bassBoost = clamped)
        prefs.edit().putFloat("bass_boost", clamped).apply()
        pushEqToService()
    }

    fun setVirtualizer(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(virtualizer = clamped)
        prefs.edit().putFloat("virtualizer", clamped).apply()
        pushEqToService()
    }

    fun toggleFavorite(stationId: String) {
        _stations.value = _stations.value.map {
            if (it.id == stationId) it.copy(isFavorite = !it.isFavorite) else it
        }
        saveStationsToPrefs(_stations.value)
        if (_uiState.value.currentStation?.id == stationId) {
            _uiState.value = _uiState.value.copy(
                currentStation = _uiState.value.currentStation?.copy(
                    isFavorite = !(_uiState.value.currentStation?.isFavorite ?: false)
                )
            )
        }
    }

    fun toggleArrangeMode() {
        _uiState.value = _uiState.value.copy(isArrangeMode = !_uiState.value.isArrangeMode)
    }

    fun openDeck() {
        _uiState.value = _uiState.value.copy(isDeckOpen = true)
    }

    fun closeDeck() {
        _uiState.value = _uiState.value.copy(isDeckOpen = false)
    }

    fun openSettingsPage(subPage: SettingsSubPage? = null, fromDeck: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            isSettingsPageOpen = true,
            currentSettingsSubPage = subPage,
            openedSettingsFromDeck = fromDeck
        )
    }

    fun closeSettingsPage() {
        if (_uiState.value.openedSettingsFromDeck) {
            _uiState.value = _uiState.value.copy(
                isSettingsPageOpen = false,
                currentSettingsSubPage = null,
                openedSettingsFromDeck = false,
                isDeckOpen = true
            )
        } else if (_uiState.value.currentSettingsSubPage != null) {
            _uiState.value = _uiState.value.copy(currentSettingsSubPage = null)
        } else {
            _uiState.value = _uiState.value.copy(
                isSettingsPageOpen = false,
                isGenreSelectMode = false,
                isGenreArrangeMode = false,
                selectedGenresForDeletion = emptySet()
            )
        }
    }

    fun navigateToSubPage(subPage: SettingsSubPage) {
        _uiState.value = _uiState.value.copy(currentSettingsSubPage = subPage)
    }

    // Spectrum Visualizer Settings
    fun setSpectrumSettings(
        style: String? = null,
        height: Int? = null,
        sensitivity: Float? = null,
        colorScheme: String? = null,
        speed: String? = null
    ) {
        val nextStyle = style ?: _uiState.value.spectrumStyle
        val nextHeight = height ?: _uiState.value.spectrumHeight
        val nextSens = sensitivity ?: _uiState.value.spectrumSensitivity
        val nextColor = colorScheme ?: _uiState.value.spectrumColorScheme
        val nextSpeed = speed ?: _uiState.value.spectrumSpeed

        _uiState.value = _uiState.value.copy(
            spectrumStyle = nextStyle,
            spectrumHeight = nextHeight,
            spectrumSensitivity = nextSens,
            spectrumColorScheme = nextColor,
            spectrumSpeed = nextSpeed
        )

        prefs.edit()
            .putString("spec_style", nextStyle)
            .putInt("spec_height", nextHeight)
            .putFloat("spec_sens", nextSens)
            .putString("spec_color", nextColor)
            .putString("spec_speed", nextSpeed)
            .apply()
    }

    // Theme Customization
    fun setThemeColor(colorHex: String) {
        _uiState.value = _uiState.value.copy(themeColorHex = colorHex)
        prefs.edit().putString("theme_color", colorHex).apply()
    }

    fun setBackgroundColor(bgHex: String) {
        _uiState.value = _uiState.value.copy(backgroundColorHex = bgHex)
        prefs.edit().putString("bg_color", bgHex).apply()
    }

    fun setThemeMode(mode: String) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun setCardColor(hex: String) {
        _uiState.value = _uiState.value.copy(cardColorHex = hex)
        prefs.edit().putString("card_color", hex).apply()
    }

    fun setBufferSize(size: String) {
        _uiState.value = _uiState.value.copy(bufferSize = size)
        prefs.edit().putString("buffer_size", size).apply()
    }

    fun setStopOnHeadsetDisconnect(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(stopOnHeadsetDisconnect = enabled)
        prefs.edit().putBoolean("stop_on_disconnect", enabled).apply()
    }

    fun setHighQualityAudio(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(highQualityAudio = enabled)
        prefs.edit().putBoolean("high_quality", enabled).apply()
    }

    fun reorderStations(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val current = _stations.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _stations.value = current
            saveStationsToPrefs(current)
        }
    }

    fun reorderGenres(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val current = _genres.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _genres.value = current
            saveGenresToPrefs(current)
        }
    }

    fun exportStationsJson(): String {
        return gson.toJson(_stations.value)
    }

    fun importStationsJson(jsonStr: String): Boolean {
        return try {
            val type = object : TypeToken<List<Station>>() {}.type
            val imported: List<Station> = gson.fromJson(jsonStr, type)
            if (imported.isNotEmpty()) {
                val merged = (_stations.value + imported).distinctBy { it.id }
                _stations.value = merged
                saveStationsToPrefs(merged)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun restoreDefaultStations() {
        val defaultList = DefaultStations.STATIONS
        _stations.value = defaultList
        saveStationsToPrefs(defaultList)
    }

    fun setAutoResumeOnBluetooth(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoResumeOnBluetooth = enabled)
        prefs.edit().putBoolean("auto_resume_bt", enabled).apply()
    }

    fun setSyncRoomCode(code: String) {
        _uiState.value = _uiState.value.copy(syncRoomCode = code)
        prefs.edit().putString("sync_room", code).apply()
    }

    fun generateSyncRoomCode() {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = "RADIO-" + (1..4).map { chars.random() }.joinToString("")
        setSyncRoomCode(code)
    }

    fun testAlarmInSeconds(seconds: Int = 5) {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val targetStationId = _uiState.value.alarmStationId ?: _stations.value.firstOrNull()?.id
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.aetherwave.radio.ACTION_ALARM_TRIGGER"
            putExtra("station_id", targetStationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = System.currentTimeMillis() + (seconds * 1000L)
        try {
            val showIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val clockInfo = AlarmManager.AlarmClockInfo(triggerTime, showIntent)
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
            Log.d("RadioViewModel", "Test alarm scheduled for $seconds seconds from now")
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } catch (ex: Exception) {
                Log.e("RadioViewModel", "Failed to schedule test alarm: ${ex.message}", ex)
            }
        }
    }

    fun setMonetEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isMonetEnabled = enabled)
        prefs.edit().putBoolean("monet_enabled", enabled).apply()
    }

    fun setSelectedGroup(group: String) {
        _uiState.value = _uiState.value.copy(selectedGroup = group)
        prefs.edit().putString("selected_genre_group", group).apply()
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
        prefs.edit().putString("view_mode", mode.name).apply()
    }

    fun setSortOption(option: SortOption) {
        _uiState.value = _uiState.value.copy(sortOption = option)
        prefs.edit().putString("sort_option", option.name).apply()
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    // Reliable Alarm Clock Manager (Uses system AlarmManager for background wake)
    fun setAlarm(enabled: Boolean, hour: Int, minute: Int, stationId: String?) {
        _uiState.value = _uiState.value.copy(
            alarmEnabled = enabled,
            alarmHour = hour,
            alarmMinute = minute,
            alarmStationId = stationId
        )

        prefs.edit()
            .putBoolean("alarm_enabled", enabled)
            .putInt("alarm_hour", hour)
            .putInt("alarm_minute", minute)
            .putString("alarm_station_id", stationId)
            .apply()

        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.aetherwave.radio.ACTION_ALARM_TRIGGER"
            putExtra("station_id", stationId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (enabled) {
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
                Log.d("RadioViewModel", "Scheduled background alarm with setAlarmClock for ${calendar.time}")
            } catch (e: Exception) {
                Log.w("RadioViewModel", "Alarm scheduling setAlarmClock fallback: ${e.message}")
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    }
                } catch (ex: Exception) {
                    Log.e("RadioViewModel", "Failed to schedule alarm: ${ex.message}", ex)
                }
            }
        } else {
            alarmManager.cancel(pendingIntent)
            Log.d("RadioViewModel", "Cancelled scheduled alarm")
        }
    }

    // Genre Management
    fun addGenre(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank() && !_genres.value.contains(trimmed)) {
            val updated = _genres.value + trimmed
            _genres.value = updated
            saveGenresToPrefs(updated)
        }
    }

    fun deleteGenre(name: String) {
        val updated = _genres.value.filter { it != name }
        _genres.value = updated
        saveGenresToPrefs(updated)
        if (_uiState.value.selectedGroup == name) {
            setSelectedGroup("All")
        }
    }

    fun deleteSelectedGenres() {
        val toDelete = _uiState.value.selectedGenresForDeletion
        val updated = _genres.value.filter { !toDelete.contains(it) }
        _genres.value = updated
        saveGenresToPrefs(updated)
        if (toDelete.contains(_uiState.value.selectedGroup)) {
            setSelectedGroup("All")
        }
        _uiState.value = _uiState.value.copy(
            selectedGenresForDeletion = emptySet(),
            isGenreSelectMode = false
        )
    }

    fun toggleGenreSelectMode() {
        val next = !_uiState.value.isGenreSelectMode
        _uiState.value = _uiState.value.copy(
            isGenreSelectMode = next,
            selectedGenresForDeletion = emptySet()
        )
    }

    fun toggleGenreArrangeMode() {
        _uiState.value = _uiState.value.copy(
            isGenreArrangeMode = !_uiState.value.isGenreArrangeMode
        )
    }

    fun toggleGenreSelection(genre: String) {
        val current = _uiState.value.selectedGenresForDeletion.toMutableSet()
        if (current.contains(genre)) {
            current.remove(genre)
        } else {
            current.add(genre)
        }
        _uiState.value = _uiState.value.copy(selectedGenresForDeletion = current)
    }

    fun moveGenre(genre: String, direction: String) {
        val list = _genres.value.toMutableList()
        val index = list.indexOf(genre)
        if (index == -1) return

        if (direction == "up" && index > 0) {
            list.removeAt(index)
            list.add(index - 1, genre)
        } else if (direction == "down" && index < list.size - 1) {
            list.removeAt(index)
            list.add(index + 1, genre)
        }
        _genres.value = list
        saveGenresToPrefs(list)
    }

    // Station CRUD
    fun saveStation(station: Station) {
        val current = _stations.value.toMutableList()
        val index = current.indexOfFirst { it.id == station.id }
        if (index != -1) {
            current[index] = station
        } else {
            current.add(0, station)
        }
        _stations.value = current
        saveStationsToPrefs(current)

        if (_uiState.value.currentStation?.id == station.id) {
            _uiState.value = _uiState.value.copy(currentStation = station)
        }
    }

    fun deleteStation(stationId: String) {
        val updated = _stations.value.filter { it.id != stationId }
        _stations.value = updated
        saveStationsToPrefs(updated)

        if (_uiState.value.currentStation?.id == stationId) {
            pausePlayback()
            _uiState.value = _uiState.value.copy(currentStation = updated.firstOrNull())
        }
    }

    fun moveStation(stationId: String, direction: String) {
        val current = _stations.value.toMutableList()
        val index = current.indexOfFirst { it.id == stationId }
        if (index == -1) return

        if (direction == "up" && index > 0) {
            val item = current.removeAt(index)
            current.add(index - 1, item)
        } else if (direction == "down" && index < current.size - 1) {
            val item = current.removeAt(index)
            current.add(index + 1, item)
        }
        _stations.value = current
        saveStationsToPrefs(current)
    }

    // Live Radio Browser Scraper for Autocomplete
    fun queryRadioBrowserSuggestions(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.length < 2) {
            _searchSuggestions.value = emptyList()
            return
        }

        scrapeJob?.cancel()
        scrapeJob = viewModelScope.launch(Dispatchers.IO) {
            delay(280) // debounce typing
            _isScraping.value = true
            try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val urlStr = "https://de1.api.radio-browser.info/json/stations/byname/$encoded?limit=10&order=votes&reverse=true"
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "AetherWaveRadioApp/1.0")

                if (conn.responseCode == 200) {
                    val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                    val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                    val rawList: List<Map<String, Any>> = gson.fromJson(jsonText, type)

                    val results = rawList.mapNotNull { item ->
                        val name = item["name"] as? String ?: return@mapNotNull null
                        val streamUrl = (item["url_resolved"] as? String) ?: (item["url"] as? String) ?: return@mapNotNull null
                        val favicon = item["favicon"] as? String
                        val tags = item["tags"] as? String
                        val country = item["country"] as? String
                        val codec = item["codec"] as? String
                        val bitrate = (item["bitrate"] as? Number)?.toInt()

                        ScrapedRadioStation(
                            name = name.trim(),
                            url = streamUrl.trim(),
                            favicon = favicon?.takeIf { it.isNotBlank() },
                            tags = tags?.takeIf { it.isNotBlank() },
                            country = country?.takeIf { it.isNotBlank() },
                            codec = codec?.takeIf { it.isNotBlank() },
                            bitrate = bitrate?.takeIf { it > 0 }
                        )
                    }

                    _searchSuggestions.value = results
                }
            } catch (e: Exception) {
                Log.e("RadioViewModel", "Error fetching radio suggestions: ${e.message}")
            } finally {
                _isScraping.value = false
            }
        }
    }

    fun clearSuggestions() {
        _searchSuggestions.value = emptyList()
    }

    private fun loadSavedStations(): List<Station> {
        val json = prefs.getString("stations_json", null)
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<Station>>() {}.type
                return gson.fromJson(json, type)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return DefaultStations.STATIONS
    }

    private fun saveStationsToPrefs(list: List<Station>) {
        val json = gson.toJson(list)
        prefs.edit().putString("stations_json", json).apply()
    }

    private fun loadSavedGenres(): List<String> {
        val json = prefs.getString("custom_genres_json", null)
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                return gson.fromJson(json, type)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return DefaultStations.GROUPS.filter { it != "All" && it != "Favorites" }
    }

    private fun saveGenresToPrefs(list: List<String>) {
        val json = gson.toJson(list)
        prefs.edit().putString("custom_genres_json", json).apply()
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
